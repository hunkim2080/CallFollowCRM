package com.detailline.callfollowcrm.presentation.screen.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.detailline.callfollowcrm.ai.TeamRepository
import com.detailline.callfollowcrm.data.AppContainer
import com.detailline.callfollowcrm.data.local.entity.CustomerEntity
import com.detailline.callfollowcrm.data.local.entity.TeamAssignmentEntity
import com.detailline.callfollowcrm.util.DateTimeUtils
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * 시공 예약 화면의 데이터 묶음.
 *
 * 분류:
 *  - upcoming: 오늘 포함 미래 예약 (가까운 순)
 *  - past:     지난 예약 (최근 지난 것부터)
 *
 * upcoming 은 다시 월별 그룹화해서 헤더 표시.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ScheduleViewModel(private val container: AppContainer) : ViewModel() {

    val state: StateFlow<ScheduleUiState> = container.customerRepository.observeScheduled()
        .map { list -> buildState(list) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScheduleUiState())

    private val ownerPhone: String get() = container.preferences.bizPhone.trim()

    /** 팀원 목록 (배정 시트용). 비즈니스 미설정/미가입이면 빈 리스트 → 배정 줄 숨김. */
    private val _teamMembers = MutableStateFlow<List<TeamRepository.TeamMember>>(emptyList())
    val teamMembers = _teamMembers.asStateFlow()

    /** 내가 수락한 협업 현장의 날짜(startOfDay) — 캘린더 보라점. (2026-06-08 #7) */
    private val _collabDayStarts = MutableStateFlow<Set<Long>>(emptySet())
    val collabDayStarts = _collabDayStarts.asStateFlow()

    /** 내가 수락한 협업 현장 목록 — 날짜 선택 시 카드로 보여줌. */
    private val _collabSites = MutableStateFlow<List<com.detailline.callfollowcrm.ai.SharedSiteRepository.SharedSite>>(emptyList())
    val collabSites = _collabSites.asStateFlow()

    /** 아직 응답(수락/거절) 안 한 협업 요청의 날짜(startOfDay) — 캘린더 주황 마커. 푸시 놓친 요청도 일정 보다 catch. (2026-07-08 사장님) */
    private val _pendingCollabDayStarts = MutableStateFlow<Set<Long>>(emptySet())
    val pendingCollabDayStarts = _pendingCollabDayStarts.asStateFlow()

    /** 응답 안 한 협업 요청 목록 — 날짜 선택 시 '확인하기' 카드로. */
    private val _pendingCollabSites = MutableStateFlow<List<com.detailline.callfollowcrm.ai.SharedSiteRepository.SharedSite>>(emptyList())
    val pendingCollabSites = _pendingCollabSites.asStateFlow()

    /** 고객(현장)별 배정된 팀원 — 일정 카드 배정 줄이 구독. */
    val assignmentsByCustomer: StateFlow<Map<Long, List<TeamAssignmentEntity>>> =
        container.teamAssignmentRepository.observeAll()
            .map { list -> list.groupBy { it.customerId } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** 인원 관리 "일당사장"(수첩 WORKER) 전체 — 전문가 배정 시트에서 탭해 [내가 부른 일당] 또는 [협업 요청]. (2026-06-13: "협업" 태그 필터 제거 — 등록한 일당사장 다 뜨게) */
    val collabPartners: StateFlow<List<com.detailline.callfollowcrm.data.local.entity.NotebookContactEntity>> =
        container.notebookRepository.observeWorkers()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * 고객(현장)별 **내가 부른 일당** 배정 — 일정 카드/배정 시트가 구독. (2026-07-16 사장님)
     *   협업 요청(collabAssignByCustomer)과 다른 것: 이건 로컬 기록이고 정산에 −지출로 잡히며
     *   "함께한 현장"에 쌓인다. 수락 같은 건 없다(내가 부른 사람이니까).
     */
    val jobCrewByCustomer: StateFlow<Map<Long, List<com.detailline.callfollowcrm.data.local.entity.JobCrewEntity>>> =
        container.jobCrewRepository.observeAll()
            .map { list -> list.groupBy { it.customerId } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /**
     * 이 시공(고객+시공일)의 일당 배정을 통째로 맞춘다 — 고른 사람은 배정(금액 갱신), 뺀 사람은 배정 해제.
     *   assign 이 멱등이라(JobCrewRepository) 연타/재배정해도 일당이 두 번 빠지지 않는다.
     *   wageByWorkerId 는 **원** 단위.
     */
    fun setJobCrew(customer: CustomerEntity, dayMs: Long, wageByWorkerId: Map<Long, Long>) {
        viewModelScope.launch {
            runCatching {
                val existing = container.jobCrewRepository.observeByJob(customer.id, dayMs).first()
                // 뺀 사람 정리
                existing.filter { it.workerId !in wageByWorkerId.keys }
                    .forEach { container.jobCrewRepository.unassign(it.workerId, customer.id, dayMs) }
                // 고른 사람 배정/갱신
                val byId = collabPartners.value.associateBy { it.id }
                wageByWorkerId.forEach { (wid, wage) ->
                    val nm = byId[wid]?.name ?: existing.firstOrNull { it.workerId == wid }?.workerName ?: "일당"
                    container.jobCrewRepository.assign(wid, nm, customer.id, dayMs, wage)
                }
            }.onFailure { _toast.value = "일당 배정을 저장하지 못했어요" }
        }
    }

    /** 협업 사장 배정(요청) 1건 — 일정 카드 "🤝 이름" + 중복요청 가드(phone).
     *   accepted = 상대가 수락했나(아니면 아직 "요청 중"). 수락 안 된 협업을 파트너처럼 표시하던 버그 fix용. (2026-07-09) */
    data class CollabAssign(val phone: String, val name: String, val accepted: Boolean = false)
    /** 협업 사장 배정 — customerId → 배정들. (로컬 기록, 서버 수락확정은 추후). (2026-06-13) */
    private val _collabAssignByCustomer = MutableStateFlow<Map<Long, List<CollabAssign>>>(emptyMap())
    val collabAssignByCustomer = _collabAssignByCustomer.asStateFlow()

    private val _toast = MutableStateFlow<String?>(null)
    val toast = _toast.asStateFlow()
    fun consumeToast() { _toast.value = null }

    /** 전문가 배정 협업 요청 시 출근시간 칩 기본 선택값(마지막 보낸 시간 기억). 시트 열 때 1회 읽음. */
    val lastCollabStartHour: Int get() = container.preferences.lastCollabStartHour

    /** by-me 로 받은 거절/종료/취소된 협업 shareId — 표시 단계에서 "🤝 이름" 배지를 즉시 거른다.
     *   (prefs 정리(reconcile)가 비동기로 못 따라가도, 캘린더/일정 카드엔 죽은 협업이 안 뜨게.) (2026-06-15 cowork 핸드오프)
     *   ⚠️ 반드시 init 블록보다 위에 선언해야 한다 — init → loadCollabAssignments() 가 이 값을 동기로 읽는데,
     *      init 아래에 두면 초기화 순서상 init 시점엔 null 이라 'Set.contains() on null' NPE 로 일정탭 진입 즉시 크래시. (2026-06-15 fix) */
    private var deadCollabShareIds: Set<String> = emptySet()
    /** by-me status 가 "pending"(상대 아직 미수락)인 shareId — 일정 배지에서 "요청 중"으로 표시(수락된 파트너처럼 X). (2026-07-09 사장님)
     *   ⚠️ deadCollabShareIds 와 같은 이유로 init 위에 선언(초기화 순서 NPE 방지). 초기값 emptySet 유지 =
     *      미로딩/네트워크 실패 시 accepted 취급(빈 status 를 서버가 accepted 로 기본처리하므로 뒤집힘 방지). */
    private var pendingCollabShareIds: Set<String> = emptySet()
    /** by-me pending 인데 수락유효시간(12h)이 지나 상대가 더는 수락 못 하는 = 만료된 요청 shareId.
     *   status 는 서버상 계속 "pending" 이라 구분 안 됨 → createdAtMs 로 시각 판정. 만료건은 배지에서 제거(요청 중 아님).
     *   (2026-07-10 사장님: 만료된 요청이 계속 "요청 중"으로 떠서.) ⚠️ init 위에 선언(초기화 순서 NPE 방지). */
    private var expiredPendingShareIds: Set<String> = emptySet()

    init { loadTeam(); loadCollab(); loadCollabAssignments() }  // loadCollab() 안에서 reconcile(거절 정리)도 같이 돈다

    /**
     * 거절/종료/취소된 협업은 일정 뱃지("🤝 이름")에서 자동 제거(self-heal).
     *   collab_assignments 는 요청 시 로컬에 박는 기록인데, 상대가 거절하거나 협업이 끝나도 서버 상태와 대조를 안 해
     *   "이미 끝난 협업"이 일정에 계속 떴음(2026-06-13 사장님: "16일 디테일라인 사장이랑 일한다고 체크돼있다, 다 삭제했는데").
     *   → by-me 로 내가 만든 협업들 status 받아 declined/ended/cancelled 인 shareId(4번째 토큰) 배정을 조용히 정리.
     */
    private fun reconcileCollabAssignments() {
        val owner = ownerPhone.filter { it.isDigit() }
        if (owner.length < 9) return
        viewModelScope.launch {
            val sites = container.sharedSiteRepository.byMe(owner).getOrNull() ?: return@launch
            val dead = sites.filter { it.status in DEAD_COLLAB_STATUS }.map { it.shareId }.toSet()
            val now = System.currentTimeMillis()
            // pending 이지만 수락유효시간(12h) 지난 = 만료 → 배지에서 제거(더는 "요청 중" 아님). createdAtMs 없으면(0) 만료판정 보류. (2026-07-10 사장님)
            expiredPendingShareIds = sites.filter {
                it.status == "pending" && it.createdAtMs > 0L && now - it.createdAtMs >= COLLAB_ACCEPT_MS
            }.map { it.shareId }.toSet()
            // 아직 살아있는(수락 가능) pending 만 "요청 중"으로 표시(수락된 파트너처럼 X, 만료건도 X). (2026-07-09 사장님)
            pendingCollabShareIds = sites.filter {
                it.status == "pending" && (it.createdAtMs <= 0L || now - it.createdAtMs < COLLAB_ACCEPT_MS)
            }.map { it.shareId }.toSet()
            // 표시용 dead set 갱신 + 즉시 재필터 (prefs 정리와 무관하게 배지에서 바로 빠지게).
            deadCollabShareIds = dead
            loadCollabAssignments()
            if (dead.isEmpty()) return@launch
            val before = container.preferences.collabAssignments
            val after = before.filterNot { e ->
                val sid = e.split('|').getOrNull(3)  // "customerId|phone|name|shareId"
                !sid.isNullOrBlank() && sid in dead
            }.toSet()
            if (after.size != before.size) {
                container.preferences.collabAssignments = after
                loadCollabAssignments()
            }
        }
    }

    /** 로컬 협업 배정 기록 로드 → customerId→배정들. ("customerId|phone|name|shareId" Set 파싱, 구버전 "id|name" 호환, 같은 번호 중복 제거)
     *   거절/종료된 shareId(deadCollabShareIds) 건은 표시에서 제외. */
    private fun loadCollabAssignments() {
        val map = HashMap<Long, MutableList<CollabAssign>>()
        for (e in container.preferences.collabAssignments) {
            val parts = e.split('|')
            val id = parts.getOrNull(0)?.toLongOrNull() ?: continue
            val shareId = parts.getOrNull(3)?.trim().orEmpty()
            if (shareId.isNotEmpty() && (shareId in deadCollabShareIds || shareId in expiredPendingShareIds)) continue  // 거절/종료/만료(12h) 협업은 배지에서 제외
            val phone = if (parts.size >= 3) parts[1].filter { it.isDigit() } else ""
            val name = if (parts.size >= 3) parts[2] else parts.getOrNull(1).orEmpty()
            if (name.isBlank()) continue
            val list = map.getOrPut(id) { mutableListOf() }
            val dup = list.any {
                if (phone.isNotBlank() && it.phone.isNotBlank()) it.phone.takeLast(8) == phone.takeLast(8)
                else it.name == name
            }
            // 구버전 기록(shareId 없음)=기존 표시 유지(accepted=true, 회귀 방지). 그 외엔 pending 인 것만 미수락. (2026-07-09)
            val accepted = shareId.isEmpty() || shareId !in pendingCollabShareIds
            if (!dup) list.add(CollabAssign(phone, name, accepted))
        }
        _collabAssignByCustomer.value = map
    }

    fun loadTeam() {
        if (ownerPhone.isBlank()) return
        viewModelScope.launch {
            container.teamRepository.members(ownerPhone).onSuccess { _teamMembers.value = it }
        }
    }

    /** 내가 수락한 협업 현장 → 캘린더 보라점용 날짜 set. 서버 with-me. (2026-06-08 #7) */
    /** 마지막으로 서버에서 받은 수락 협업 현장(숨김 필터 전) — hide/undo 시 재요청 없이 재필터하려 보관. */
    private var allAcceptedCollab: List<com.detailline.callfollowcrm.ai.SharedSiteRepository.SharedSite> = emptyList()

    fun loadCollab() {
        if (ownerPhone.isBlank()) return
        viewModelScope.launch {
            container.sharedSiteRepository.withMe(ownerPhone).onSuccess { sites ->
                allAcceptedCollab = sites.filter { it.status == "accepted" && it.scheduledAtMs > 0L }
                // 응답 안 한 요청(pending) — 캘린더 주황 마커 + 선택 시 확인 카드. (2026-07-08 사장님)
                val pending = sites.filter { it.status == "pending" && it.scheduledAtMs > 0L }
                _pendingCollabSites.value = pending
                _pendingCollabDayStarts.value = pending.map { DateTimeUtils.startOfDay(it.scheduledAtMs) }.toSet()
                applyCollabFilter()
            }
        }
        // 화면 진입(resume)마다 거절/종료된 협업 배지도 다시 정리 — 앱 켜둔 채 상대가 거절해도 다음 진입에 사라지게.
        reconcileCollabAssignments()
    }

    /** 숨김 set 을 빼고 _collabSites/_collabDayStarts 갱신. loadCollab 결과 + hide/undo 양쪽이 공유. */
    private fun applyCollabFilter() {
        val hidden = container.preferences.hiddenCollabShareIds
        val visible = allAcceptedCollab.filter { it.shareId !in hidden }
        _collabSites.value = visible
        _collabDayStarts.value = visible.map { DateTimeUtils.startOfDay(it.scheduledAtMs) }.toSet()
    }

    /** 협업 카드 밀어서 삭제 — 서버에서 지우는 게 아니라 내 일정 뷰에서만 숨김(되돌리기 가능). */
    fun hideCollab(shareId: String) {
        container.preferences.hiddenCollabShareIds = container.preferences.hiddenCollabShareIds + shareId
        applyCollabFilter()
        pushMirror()
    }

    /** 숨김 되돌리기(토스트의 "되돌리기"). */
    fun unhideCollab(shareId: String) {
        container.preferences.hiddenCollabShareIds = container.preferences.hiddenCollabShareIds - shareId
        applyCollabFilter()
        pushMirror()
    }

    /**
     * 본폰 미러 즉시 갱신 — 미러도 이 '숨김' 목록을 그대로 따르므로(MirrorSyncManager.mapCollabSites)
     *   여기서 안 찔러주면 일정 화면에선 사라졌는데 본폰엔 ~3시간 남아있게 된다(두 화면 불일치).
     *   미러 자동전송은 '내 고객/현금 변경'만 구독해서 협업 변경엔 안 걸림. 옵트인 꺼져있으면 내부에서 skip.
     */
    private fun pushMirror() {
        viewModelScope.launch { runCatching { container.mirrorSyncManager.pushNow(force = true) } }
    }

    /** 협업 카드 "그만두기"(밀어서) — 수락된 협업을 끝냄(서버 end → 상대=주인 A 에게 "해제" 알림) + 내 일정에서 숨김.
     *   캘린더 협업카드는 내가 '공유받은(B)' 현장이라 asOwner=false. (2026-06-20 사장님 — 양쪽 동기화) */
    fun leaveCollabSite(site: com.detailline.callfollowcrm.ai.SharedSiteRepository.SharedSite) {
        hideCollab(site.shareId)  // 즉시 일정에서 사라짐(서버 반영 전에도)
        val owner = ownerPhone.filter { it.isDigit() }
        if (owner.length < 9 || site.shareId.isBlank()) return
        viewModelScope.launch {
            val ok = container.sharedSiteRepository.endCollab(site.shareId, owner, asOwner = false).isSuccess
            _toast.value = if (ok) "협업을 그만뒀어요 — 사장님께 알려드렸어요"
                else "지금 연결이 안 돼 사장님께 알림을 못 보냈어요 — 잠시 후 다시 시도해주세요"
        }
    }

    /** 일정 카드 밀어서 삭제 — 이 현장을 "일정에서만" 뺌(고객·대화·정산 기록은 보존). 되돌리기 가능. (2026-06-13 사장님) */
    fun unschedule(customer: CustomerEntity) {
        viewModelScope.launch { container.customerRepository.updateScheduledWorkDate(customer.id, null) }
    }
    /** 되돌리기 — 뺀 일정을 원래 날짜로 복구. */
    fun restoreSchedule(customerId: Long, scheduledAtMs: Long) {
        viewModelScope.launch { container.customerRepository.updateScheduledWorkDate(customerId, scheduledAtMs) }
    }

    /** 협업 현장 표시 라벨 — 주소(지역+아파트/단독은 지역+동) "○○ 현장", 없으면 실제 고객 이름, 둘 다 없으면 "이 현장".
     *   전화번호는 제목으로 쓰지 않음(이름이 번호면 건너뜀). 번호/대화 절대 미포함. */
    private fun collabTitleOf(c: CustomerEntity): String = collabTitleOf(c.address, c.name)
    private fun collabTitleOf(address: String?, name: String?): String {
        com.detailline.callfollowcrm.util.AddressExtractor.siteLabel(address).takeIf { it.isNotBlank() }
            ?.let { return "$it 현장" }
        name?.takeIf { it.isNotBlank() && it.count { ch -> ch.isDigit() } < 9 } // 이름이 전화번호면 제외
            ?.let { return "$it 현장" }
        return "이 현장"
    }

    /**
     * 전문가 배정 시트의 "협업 사장님" 선택 → 이 현장을 그 사장님께 협업 요청(/api/shared/invite).
     *   고객 번호/대화는 안 보냄(customer_label = 안전 라벨만) — CustomerDetail 공유 흐름과 동일.
     *   link 라우트면 onLink(번호, 문자본문) 로 화면이 SMS 작성창을 열게 함. inapp/실패는 토스트.
     */
    fun inviteCollabToSite(
        customer: CustomerEntity,
        partnerPhone: String,
        force: Boolean = false,
        memo: String = "",
        dailyWage: Int? = null,
        startHour: Int = -1, // 출근시간(24h). -1=미선택
        addressOverride: String? = null, // 시트에서 입력받은 현장 주소(고객에 주소 없을 때). 비면 customer.address. (2026-06-20 사장님)
        onLink: (String, String) -> Unit
    ) {
        val owner = ownerPhone.filter { it.isDigit() }
        if (owner.length < 9) { _toast.value = "먼저 더보기 → 견적서·사업자 정보에서 내 전화번호를 등록해주세요"; return }
        val partner = partnerPhone.filter { it.isDigit() }
        if (partner.length < 9) { _toast.value = "협업 사장님 번호를 확인해주세요"; return }
        // 중복 요청 가드 — 이미 이 현장에 이 사장님께 요청했으면 막음(같은 사람 계속 신청·수락되던 버그).
        val already = _collabAssignByCustomer.value[customer.id].orEmpty()
            .any { it.phone.isNotBlank() && it.phone.takeLast(8) == partner.takeLast(8) }
        if (already && !force) { _toast.value = "이미 이 현장에 요청한 사장님이에요"; return }
        // 시트에서 주소를 받았으면 그걸로(고객에도 저장), 아니면 고객 주소 → 제목·addr 둘 다 주소 기반. (2026-06-20 사장님)
        val effectiveAddress = addressOverride?.trim()?.takeIf { it.isNotBlank() } ?: customer.address
        val title = collabTitleOf(effectiveAddress, customer.name)
        val partnerName = collabPartners.value
            .firstOrNull { it.phone.filter { ch -> ch.isDigit() }.takeLast(8) == partner.takeLast(8) }
            ?.name?.takeIf { it.isNotBlank() } ?: "협업 사장님"
        val addr = com.detailline.callfollowcrm.util.AddressExtractor.tidyAddress(effectiveAddress).takeIf { it.isNotBlank() }
        // 출근시간 선택 시: 일정 날짜에 그 정시 박아 scheduled_at_ms + time_label 도 함께.
        val baseMs = customer.scheduledWorkDate ?: 0L
        val effectiveMs = if (startHour in 0..23 && baseMs > 0L) {
            Calendar.getInstance().apply {
                timeInMillis = baseMs
                set(Calendar.HOUR_OF_DAY, startHour); set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        } else baseMs
        val timeLabel = startHour.takeIf { it in 0..23 }?.let {
            val ampm = if (it < 12) "오전" else "오후"; val h12 = if (it % 12 == 0) 12 else it % 12; "$ampm ${h12}시"
        }
        viewModelScope.launch {
            // 입력받은 주소는 고객에도 저장 — 다음부턴 자동, 데이터 보존(사장님을 버그리포터로 X). (2026-06-20)
            if (!addressOverride.isNullOrBlank() && addressOverride.trim() != customer.address) {
                runCatching { container.customerRepository.updateAddress(customer.id, addressOverride.trim()) }
            }
            container.sharedSiteRepository.invite(
                ownerPhone = owner, partnerPhone = partner, title = title,
                addr = addr, scheduledAtMs = effectiveMs,
                workSummary = null, memo = memo.takeIf { it.isNotBlank() }, customerLabel = title,
                dailyWage = dailyWage, timeLabel = timeLabel,
                ownerName = container.preferences.bizName
            ).onSuccess { r ->
                // 일정 카드 "🤝 이름" 표시용 로컬 배정 기록 (서버 수락 확정은 추후). 4번째=shareId(공유후카드 사진 조회용).
                container.preferences.collabAssignments = container.preferences.collabAssignments + "${customer.id}|$partner|$partnerName|${r.shareId}"
                if (startHour in 0..23) container.preferences.lastCollabStartHour = startHour  // 다음 요청 때 미리 선택
                loadCollabAssignments()
                if (r.deduped) {
                    _toast.value = "이미 이 현장으로 협업 중인 사장님이에요 (새 알림은 안 가요)"
                } else if (r.route == "link" && !r.url.isNullOrBlank()) {
                    onLink(partner, r.smsDraft ?: "협업 현장 공유 — ${r.url}")
                } else {
                    _toast.value = "협업 요청을 보냈어요 — 상대 사장님이 수락하면 시작돼요"
                }
            }.onFailure { _toast.value = "공유 실패 — 잠시 후 다시 시도해주세요" }
        }
    }

    /** 협업 요청/협업 취소 — 배정 시트에서 "요청함" 사장님 다시 탭 → 내 목록에서 빼고 + 서버에도 알려 B쪽에서도 빠지게.
     *   수락 전이면 요청 취소(조용), 수락 후면 해제(B 에게 "해제" 알림). (2026-06-20 사장님 — 양쪽 동기화) */
    fun removeCollabAssignment(customerId: Long, partnerPhone: String) {
        val ph = partnerPhone.filter { it.isDigit() }
        // shareId 찾기(서버 취소/해제용) — collabAssignments 4번째 칸 "customerId|phone|name|shareId".
        val shareId = container.preferences.collabAssignments.firstNotNullOfOrNull { e ->
            val p = e.split('|')
            if (p.getOrNull(0)?.toLongOrNull() == customerId &&
                (p.getOrNull(1)?.filter { it.isDigit() }?.takeLast(8) ?: "") == ph.takeLast(8))
                p.getOrNull(3)?.trim()?.takeIf { it.isNotBlank() } else null
        }
        container.preferences.collabAssignments = container.preferences.collabAssignments
            .filterNot { e ->
                val p = e.split('|')
                p.getOrNull(0)?.toLongOrNull() == customerId &&
                    (p.getOrNull(1)?.filter { it.isDigit() }?.takeLast(8) ?: "") == ph.takeLast(8)
            }.toSet()
        loadCollabAssignments()
        // 서버에도 — 수락 전이면 cancel(조용), 수락했으면(cancel 실패) end(B 에게 "해제" 알림). best-effort.
        val owner = ownerPhone.filter { it.isDigit() }
        if (shareId != null && owner.length >= 9) {
            viewModelScope.launch {
                // 서버 결과 확인 후 토스트 — 실패해도 "취소했어요"라 하던 거짓 안심 제거 + 취소/해제 구분. (2026-07-30)
                val cancelled = container.sharedSiteRepository.cancel(shareId, owner).isSuccess
                val ended = if (!cancelled) container.sharedSiteRepository.endCollab(shareId, owner, asOwner = true).isSuccess else false
                _toast.value = when {
                    cancelled -> "협업 요청을 취소했어요"
                    ended -> "협업을 해제했어요 — 상대 사장님께 알림이 가요"
                    else -> "지금 연결이 안 돼 처리를 못 했어요 — 잠시 후 다시 시도해주세요"
                }
            }
        } else {
            _toast.value = "협업 요청을 취소했어요"
        }
    }

    /** 전문가 배정 시트 안 "+추가" — 팀원 즉시 등록(서버 invite). 등록 후 칩에 바로 뜸. (2026-06-14) */
    fun addTeamMember(name: String, phone: String) {
        val owner = ownerPhone.filter { it.isDigit() }
        if (owner.length < 9) { _toast.value = "먼저 더보기 → 견적서·사업자 정보에서 내 전화번호를 등록해주세요"; return }
        val nm = name.trim(); val ph = phone.filter { it.isDigit() }
        if (nm.isBlank()) { _toast.value = "이름을 입력해주세요"; return }
        if (ph.length < 9) { _toast.value = "전화번호를 확인해주세요"; return }
        viewModelScope.launch {
            container.teamRepository.invite(owner, nm, ph)
                .onSuccess { loadTeam(); _toast.value = "팀원 ${nm}님을 추가했어요" }
                .onFailure { _toast.value = "팀원 추가 실패 — 잠시 후 다시 시도해주세요" }
        }
    }

    /** 전문가 배정 시트 안 "+추가" — 일당사장(수첩 WORKER) 즉시 등록. 일당(만원)은 ×10000 저장. 칩에 바로 뜸(Flow). (2026-06-14) */
    fun addCollabPartner(name: String, phone: String, wageManwon: Int?) {
        val nm = name.trim(); val ph = phone.filter { it.isDigit() }
        if (nm.isBlank()) { _toast.value = "이름을 입력해주세요"; return }
        if (ph.length < 9) { _toast.value = "전화번호를 확인해주세요"; return }
        viewModelScope.launch {
            container.notebookRepository.add(
                kind = com.detailline.callfollowcrm.data.local.entity.NotebookContactEntity.KIND_WORKER,
                name = nm, phone = ph, tag = "", memo = "",
                wage = wageManwon?.takeIf { it > 0 }?.let { it.toLong() * 10000 }
            )
            _toast.value = "일당사장 ${nm}님을 추가했어요"
        }
    }

    /**
     * 한 현장(고객)에 팀원 배정 저장 — 로컬 기록 교체 + 영향 받은 팀원들의 일정 snapshot 을 서버에 push.
     *   팀원 웹뷰(/team/member/{token})에 배정 일정이 바로 반영됨. 자동 SMS 발송 X.
     */
    fun assignTeam(customer: CustomerEntity, dayStartMs: Long, selectedMemberIds: List<String>, teamMemo: String? = null) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val members = _teamMembers.value
            val selected = members.filter { it.memberId in selectedMemberIds }
            val prev = container.teamAssignmentRepository.forCustomer(customer.id).map { it.memberId }.toSet()
            container.teamAssignmentRepository.replaceForCustomer(
                customer.id, dayStartMs, selected.map { it.memberId to it.name }, now, teamMemo
            )
            // 영향 받은 팀원 = 이전 배정 ∪ 새 배정 — 각자 snapshot 재구성 push.
            val affected = prev + selectedMemberIds
            var pushed = 0
            for (mid in affected) {
                if (pushSnapshotFor(mid)) pushed++
            }
            _toast.value = when {
                selected.isEmpty() -> "배정을 비웠어요"
                pushed > 0 -> "${selected.size}명 배정 · 현장 공유 📩"
                else -> "${selected.size}명 배정 (링크 발급 후 다시 시도하면 공유돼요)"
            }
        }
    }

    /** 한 팀원의 모든 배정을 모아 snapshot 으로 push. 활성 토큰 없으면(404) invite(reuse) 후 1회 재시도. */
    private suspend fun pushSnapshotFor(memberId: String): Boolean {
        val rows = container.teamAssignmentRepository.forMember(memberId)
        val all = state.value.all
        val todayStart = DateTimeUtils.startOfDay(System.currentTimeMillis())
        val items = rows.mapNotNull { a ->
            val c = all.find { it.id == a.customerId } ?: return@mapNotNull null
            val sched = c.scheduledWorkDate ?: return@mapNotNull null
            val sStart = DateTimeUtils.startOfDay(sched)
            val isToday = sStart == todayStart
            TeamRepository.SnapshotItem(
                whenLabel = if (isToday) "오늘" else SimpleDateFormat("M/d", Locale.KOREA).format(java.util.Date(sStart)),
                customerLabel = c.name?.takeIf { it.isNotBlank() } ?: "현장",
                customerPhone = c.phoneNumber.takeIf { it.isNotBlank() },
                time = c.scheduledWorkMinutes?.let { DateTimeUtils.formatWorkMinutes(it) },
                addr = c.address?.takeIf { it.isNotBlank() },
                workSummary = null,
                // 직원 전달 메모 = 배정 시 사장님이 적은 것(고객 메모 아님 — 사생활 보호).
                teamMemo = a.teamMemo?.takeIf { it.isNotBlank() },
                days = c.scheduledWorkDays.coerceAtLeast(1),
                isToday = isToday,
                scheduledAtMs = sStart
            )
        }.sortedBy { it.scheduledAtMs }

        var res = container.teamRepository.pushScheduleSnapshot(memberId, items)
        if (res.isFailure && (res.exceptionOrNull()?.message?.contains("404") == true)) {
            // 활성 토큰 없음 → 재초대(reuse)로 토큰 발급 후 재시도.
            val m = _teamMembers.value.find { it.memberId == memberId }
            if (m != null && ownerPhone.isNotBlank()) {
                container.teamRepository.invite(ownerPhone, m.name, m.phone, m.role, m.tint)
                res = container.teamRepository.pushScheduleSnapshot(memberId, items)
            }
        }
        return res.isSuccess
    }

    /**
     * 사장님 요청 (2026-05-24): 캘린더 셀 탭 시 시공 카드에 "어떤 내용으로 예약 확정인지" 한 줄.
     * → AiSummaryEntity.cardSummary 재사용 (HomeScreen 카드의 ✨ 한 줄 요약과 같은 데이터).
     * Map<phoneSuffix, summary>. 서버 미구현이면 빈 맵 → UI 가 silent 숨김.
     */
    val cardSummariesByPhoneSuffix: StateFlow<Map<String, String>> = state
        .flatMapLatest { st ->
            val suffixes = st.all.map { phoneSuffix(it.phoneNumber) }.distinct().filter { it.length >= 7 }
            if (suffixes.isEmpty()) flowOf(emptyMap())
            else container.conversationAiRepository.observeMany(suffixes).map { list ->
                list.mapNotNull { e -> e.cardSummary?.let { e.phoneSuffix to it } }.toMap()
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private fun phoneSuffix(phone: String): String =
        phone.filter { it.isDigit() }.takeLast(8)

    private fun buildState(list: List<CustomerEntity>): ScheduleUiState {
        val todayStart = DateTimeUtils.startOfDay(System.currentTimeMillis())
        val (upcoming, past) = list.partition { (it.scheduledWorkDate ?: 0L) >= todayStart }

        // 월별 그룹 — 키는 (year, month). 정렬 유지를 위해 LinkedHashMap.
        val byMonth = LinkedHashMap<String, MutableList<CustomerEntity>>()
        for (c in upcoming) {
            val date = c.scheduledWorkDate ?: continue
            val header = DateTimeUtils.formatMonthHeader(date)
            byMonth.getOrPut(header) { mutableListOf() }.add(c)
        }
        val groups = byMonth.map { (header, members) ->
            ScheduleGroup(monthHeader = header, customers = members)
        }
        return ScheduleUiState(
            upcomingByMonth = groups,
            upcomingCount = upcoming.size,
            past = past.sortedByDescending { it.scheduledWorkDate ?: 0L },
            all = list
        )
    }

    /** 오늘이 어느 월인지 — UI 가 "이번 달" 표기에 사용. */
    fun currentMonthHeader(): String =
        DateTimeUtils.formatMonthHeader(System.currentTimeMillis())

    companion object {
        /** 일정 뱃지에서 더는 안 띄울(=정리할) 협업 상태 — 거절/종료/취소. */
        private val DEAD_COLLAB_STATUS = setOf("declined", "ended", "cancelled", "canceled")
        /** 협업 요청 수락 유효시간 — 12h. 지나면 상대가 수락 못 함 = 만료 → 일정 배지에서 "요청 중" 제거. (SharedSiteViewModel.ACCEPT_VALID_MS 와 동일 값) */
        private const val COLLAB_ACCEPT_MS = 12L * 60 * 60 * 1000
    }
}

data class ScheduleUiState(
    val upcomingByMonth: List<ScheduleGroup> = emptyList(),
    val upcomingCount: Int = 0,
    val past: List<CustomerEntity> = emptyList(),
    /** 전체 (과거 + 미래) 시공 예약 — 캘린더 그리드 그릴 때 사용. */
    val all: List<CustomerEntity> = emptyList()
)

data class ScheduleGroup(
    val monthHeader: String,
    val customers: List<CustomerEntity>
)
