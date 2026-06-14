package com.detailline.callfollowcrm.presentation.screen.sharedsite

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.detailline.callfollowcrm.ai.SharedSiteRepository
import com.detailline.callfollowcrm.data.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 협업 현장(B = 협업자) 화면 ViewModel.
 *   내(bizPhone)가 공유받은 현장 목록을 서버에서 가져오고, 수락/거절·진행(출발/도착/완료)을 보냄.
 *   완료 시 [AppPreferences] 의 입금 계좌를 같이 실어 보냄 → 주인 사장에게 계좌 전달.
 *
 * 서버 endpoint 미구현 동안엔 withMe 가 빈 목록 → 화면은 "공유받은 현장 없음" 안내.
 * (docs/SERVER_HANDOFF_collab_sites.md)
 */
class SharedSiteViewModel(private val container: AppContainer) : ViewModel() {

    private val repo: SharedSiteRepository = container.sharedSiteRepository
    private val myPhone: String = container.preferences.bizPhone.filter { it.isDigit() }

    private val _sites = MutableStateFlow<List<SharedSiteRepository.SharedSite>>(emptyList())
    val sites = _sites.asStateFlow()

    /** 업체별(§B) 서버 집계 — 비어 있으면 화면이 로컬 그룹핑으로 폴백. */
    private val _partners = MutableStateFlow<List<SharedSiteRepository.Partner>>(emptyList())
    val partners = _partners.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()

    /** 현재 연 협업 현장의 증거 사진(§F). 상세 열 때 loadPhotos. */
    private val _photos = MutableStateFlow<List<SharedSiteRepository.SharedPhoto>>(emptyList())
    val photos = _photos.asStateFlow()
    private val _photoBusy = MutableStateFlow(false)
    val photoBusy = _photoBusy.asStateFlow()

    /** 휴지통에 넣은 협업 현장 share_id 들(목록에서 제외, 휴지통에 보관·복구 가능). */
    private val _trashed = MutableStateFlow(container.preferences.trashedSharedSiteIds)
    val trashed = _trashed.asStateFlow()

    fun trash(shareId: String) {
        if (shareId.isBlank()) return
        container.preferences.trashedSharedSiteIds = container.preferences.trashedSharedSiteIds + shareId
        _trashed.value = container.preferences.trashedSharedSiteIds
    }

    fun restore(shareId: String) {
        container.preferences.trashedSharedSiteIds = container.preferences.trashedSharedSiteIds - shareId
        _trashed.value = container.preferences.trashedSharedSiteIds
    }

    /** B(협업자)가 협업 그만하기 — 서버 end(by partner) → A 에게 알림 + 기록 보존. 로컬에선 즉시 숨김(서버가 declined 처리). best-effort. */
    fun leaveCollab(site: SharedSiteRepository.SharedSite) {
        trash(site.shareId) // 즉시 목록에서 빠짐
        viewModelScope.launch {
            runCatching { repo.endCollab(site.shareId, myPhone, asOwner = false) }
            _toast.value = "협업을 그만뒀어요 — 사장님께 알려드렸어요"
        }
    }

    /** true = 사업자 전화 미등록 → 협업 받을 수 없음(안내). */
    val noBizPhone: Boolean get() = myPhone.length < 9

    private val _toast = MutableStateFlow<String?>(null)
    val toast = _toast.asStateFlow()
    fun consumeToast() { _toast.value = null }

    fun load() {
        if (noBizPhone) return
        _loading.value = true
        viewModelScope.launch {
            val list = repo.withMe(myPhone).getOrDefault(emptyList())
            _sites.value = list
            // 수락 유효시간(12h) 앵커: 화면을 직접 열어 본 시각도 기록(폴링 전이라도) — 서버 created_at_ms 폴백.
            container.preferences.syncCollabInviteFirstSeen(
                list.filter { it.status == "pending" }.map { it.shareId }.toSet(),
                System.currentTimeMillis()
            )
            // 업체별 집계(§B) — 서버 없거나 실패하면 빈 목록 → 화면이 로컬 그룹핑으로 폴백.
            _partners.value = repo.partners(myPhone).getOrDefault(emptyList())
            _loading.value = false
        }
    }

    /**
     * 받은 협업 요청의 수락 유효시간(12h)이 지났는지. true 면 더는 수락 불가(화면이 "지났어요" 안내).
     *   앵커 = 서버 created_at_ms(>0) 우선, 없으면 로컬 첫 관측 시각. 둘 다 0이면 만료 처리 안 함(안전).
     */
    fun acceptExpired(site: SharedSiteRepository.SharedSite): Boolean {
        if (site.status != "pending") return false
        val anchor = site.createdAtMs.takeIf { it > 0L }
            ?: container.preferences.collabInviteFirstSeenMs(site.shareId)
        if (anchor <= 0L) return false
        return System.currentTimeMillis() - anchor >= ACCEPT_VALID_MS
    }

    fun respond(site: SharedSiteRepository.SharedSite, accept: Boolean) {
        viewModelScope.launch {
            repo.respond(site.shareId, myPhone, accept)
                .onSuccess {
                    _toast.value = if (accept) "협업 현장에 들어왔어요" else "거절했어요"
                    // 수락/거절 즉시 상담함 카드·뱃지·알림에서 제거(다음 폴 안 기다리게). (2026-06-14 버그)
                    container.collabEventCenter.markResponded(container.appContext, site.shareId)
                    load()
                }
                .onFailure { _toast.value = "처리 못했어요 — 잠시 후 다시" }
        }
    }

    fun updateProgress(site: SharedSiteRepository.SharedSite, step: SharedSiteRepository.Progress) {
        viewModelScope.launch {
            val withAccount = step == SharedSiteRepository.Progress.COMPLETED
            val res = repo.progress(
                shareId = site.shareId,
                partnerPhone = myPhone,
                step = step,
                bank = if (withAccount) container.preferences.bizBank.takeIf { it.isNotBlank() } else null,
                accountNo = if (withAccount) container.preferences.bizAccountNo.takeIf { it.isNotBlank() } else null,
                holder = if (withAccount) {
                    container.preferences.bizAccountHolder.takeIf { it.isNotBlank() }
                        ?: container.preferences.bizOwner.takeIf { it.isNotBlank() }
                } else null
            )
            res.onSuccess {
                _toast.value = when (step) {
                    SharedSiteRepository.Progress.DEPARTED -> "출발 알렸어요"
                    SharedSiteRepository.Progress.ARRIVED -> "도착 알렸어요"
                    SharedSiteRepository.Progress.COMPLETED -> "완료 알렸어요 — 주인 사장님께 계좌가 전달돼요"
                    else -> "알렸어요"
                }
                load()
            }.onFailure { _toast.value = "전송 실패 — 잠시 후 다시" }
        }
    }

    /** 완료 알리기 전, 입금 계좌가 등록돼 있는지. 없으면 화면이 등록 유도. */
    fun hasAccount(): Boolean = container.preferences.bizAccountNo.isNotBlank()

    /** 일당 지급(입금) 계좌 — 협업 화면 인라인 확인/등록·수정용. (2026-06-14 사장님) */
    val accountBank: String get() = container.preferences.bizBank
    val accountNo: String get() = container.preferences.bizAccountNo
    val accountHolder: String get() = container.preferences.bizAccountHolder
    fun saveAccount(bank: String, no: String, holder: String) {
        container.preferences.bizBank = bank.trim()
        container.preferences.bizAccountNo = no.trim()
        container.preferences.bizAccountHolder = holder.trim()
    }

    /** 상세 열 때 그 현장의 증거 사진 로드(§F). 다른 현장으로 바뀌면 비움. */
    fun loadPhotos(shareId: String) {
        if (shareId.isBlank() || noBizPhone) { _photos.value = emptyList(); return }
        viewModelScope.launch { _photos.value = repo.photos(shareId, myPhone).getOrDefault(emptyList()) }
    }

    /** 증거 사진 업로드(§F) — base64 는 화면에서 변환해 넘김(VM 은 Context 없음). */
    fun uploadPhotoBase64(shareId: String, base64: String) {
        if (shareId.isBlank() || noBizPhone || base64.isBlank()) return
        _photoBusy.value = true
        viewModelScope.launch {
            repo.uploadPhoto(shareId, myPhone, base64)
                .onSuccess {
                    _toast.value = "현장 사진을 올렸어요"
                    _photos.value = repo.photos(shareId, myPhone).getOrDefault(_photos.value)
                }
                .onFailure { _toast.value = "사진 업로드 실패 — 잠시 후 다시" }
            _photoBusy.value = false
        }
    }

    companion object {
        /** 협업 요청 수락 유효시간 — 12시간. 그 이후엔 수락 불가("수락 시간이 지났어요"). (2026-06-14 사장님) */
        const val ACCEPT_VALID_MS = 12L * 60 * 60 * 1000
    }
}
