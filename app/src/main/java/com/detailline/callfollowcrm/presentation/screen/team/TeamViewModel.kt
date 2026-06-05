package com.detailline.callfollowcrm.presentation.screen.team

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.detailline.callfollowcrm.ai.TeamRepository
import com.detailline.callfollowcrm.data.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 팀 관리 화면 ViewModel (프로토 #s-team).  2026-06-05.
 *   owner_phone = 사업자 전화(AppPreferences.bizPhone). 비어 있으면 사업자정보 먼저 안내.
 *   팀원 목록/출발 이벤트는 서버에서 로드. 추가/제외/미리보기는 서버 호출.
 */
class TeamViewModel(private val container: AppContainer) : ViewModel() {

    val ownerPhone: String = container.preferences.bizPhone.trim()
    val ownerName: String = container.preferences.bizOwner.trim().ifBlank { "대표" }

    private val _members = MutableStateFlow<List<TeamRepository.TeamMember>>(emptyList())
    val members = _members.asStateFlow()

    /** 출발 이벤트만 (오늘 출발 알림 목록). */
    private val _departures = MutableStateFlow<List<TeamRepository.TeamEvent>>(emptyList())
    val departures = _departures.asStateFlow()

    /**
     * 최근 통화·문자 번호 (팀원 추가 시 "번호로 불러오기"용) — 주소록에 저장 안 된 번호도 보이게.
     *   통화기록 + 문자 연락처 합쳐 끝 8자리로 중복 제거, 최신순. 이름은 고객으로 등록돼 있으면 표시.
     */
    val recentNumbers: StateFlow<List<RecentNumber>> = combine(
        container.callRecordRepository.observeRecent(80),
        container.smsContactCacheRepository.observeAll(200),
        container.customerRepository.observeAll()
    ) { calls, sms, customers ->
        fun suf(p: String) = p.filter { it.isDigit() }.takeLast(8)
        val nameBySuffix = customers.mapNotNull { c ->
            c.name?.takeIf { it.isNotBlank() }?.let { suf(c.phoneNumber) to it }
        }.toMap()
        val map = LinkedHashMap<String, RecentNumber>()
        fun add(phone: String, ms: Long) {
            val s = suf(phone)
            if (s.length < 8) return
            val ex = map[s]
            if (ex == null || ms > ex.lastMs) map[s] = RecentNumber(nameBySuffix[s], phone, ms)
        }
        calls.forEach { add(it.phoneNumber, it.endedAt) }
        sms.forEach { add(it.address, it.lastDateMs) }
        map.values.sortedByDescending { it.lastMs }.take(40)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()

    private val _toast = MutableStateFlow<String?>(null)
    val toast = _toast.asStateFlow()
    fun consumeToast() { _toast.value = null }

    val ownerPhoneMissing: Boolean get() = ownerPhone.isBlank()

    fun load() {
        if (ownerPhoneMissing) return
        viewModelScope.launch {
            _loading.value = true
            container.teamRepository.members(ownerPhone).onSuccess { _members.value = it }
            container.teamRepository.events(ownerPhone).onSuccess { list ->
                _departures.value = list.filter { it.eventType == "departed" }
            }
            _loading.value = false
        }
    }

    /**
     * 팀원 추가. 성공 시 (member phone, sms_draft) 콜백 → 화면이 문자앱 prefill(자동발송 X).
     * 서버가 비즈니스 미가입이면 403 → 친절 메시지.
     */
    fun invite(name: String, phone: String, onResult: (phone: String, smsDraft: String) -> Unit) {
        val nm = name.trim()
        val ph = phone.trim()
        if (nm.isBlank()) { _toast.value = "이름을 입력해주세요"; return }
        if (ph.filter { it.isDigit() }.length < 9) { _toast.value = "전화번호를 확인해주세요"; return }
        viewModelScope.launch {
            _loading.value = true
            val tint = _members.value.size % 5
            container.teamRepository.invite(ownerPhone, nm, ph, tint = tint).fold(
                onSuccess = { r ->
                    load()
                    _toast.value = "${r.name} 추가 · 초대 링크를 문자에 넣었어요 📩"
                    onResult(ph, r.smsDraft)
                },
                onFailure = { _toast.value = inviteErrorMessage(it) }
            )
            _loading.value = false
        }
    }

    /** 팀원 제외 (링크 차단). undo = 같은 이름·번호로 재초대. */
    fun remove(member: TeamRepository.TeamMember, onRemoved: (undo: () -> Unit) -> Unit) {
        viewModelScope.launch {
            container.teamRepository.remove(member.memberId, ownerPhone).fold(
                onSuccess = {
                    load()
                    onRemoved {
                        // 되돌리기 — 같은 사람 재초대 (새 member_id 로 복구)
                        viewModelScope.launch {
                            container.teamRepository.invite(ownerPhone, member.name, member.phone, member.role, member.tint)
                                .onSuccess { load() }
                        }
                    }
                },
                onFailure = { _toast.value = "제외 실패 — 잠시 후 다시 시도해주세요" }
            )
        }
    }

    /** 팀원 화면 미리보기 — 그 팀원 링크를 받아 콜백(브라우저로 열기). reuse 라 안전. */
    fun previewLink(member: TeamRepository.TeamMember, onUrl: (String) -> Unit) {
        viewModelScope.launch {
            _loading.value = true
            container.teamRepository.invite(ownerPhone, member.name, member.phone, member.role, member.tint).fold(
                onSuccess = { onUrl(it.url) },
                onFailure = { _toast.value = inviteErrorMessage(it) }
            )
            _loading.value = false
        }
    }

    private fun inviteErrorMessage(t: Throwable): String {
        val msg = t.message.orEmpty()
        return if (msg.contains("403")) "팀 관리는 비즈니스 요금제 기능이에요."
        else "서버 연결 실패 — 잠시 후 다시 시도해주세요"
    }
}

/** 최근 통화·문자 번호 1건 (팀원 추가 picker용). */
data class RecentNumber(
    val name: String?,
    val phone: String,
    val lastMs: Long
)
