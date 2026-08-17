package com.detailline.callfollowcrm.ai

import com.detailline.callfollowcrm.data.preferences.AppPreferences
import com.detailline.callfollowcrm.data.repository.CustomerRepository
import com.detailline.callfollowcrm.data.repository.ManualCashRepository
import com.detailline.callfollowcrm.domain.settlement.CashFlowCalc
import com.detailline.callfollowcrm.domain.settlement.SettlementCalc
import com.detailline.callfollowcrm.util.DateTimeUtils
import com.detailline.callfollowcrm.util.PhoneNumberFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 본폰 미러 링크 — 일정/돈 스냅샷을 서버로 자동 전송(2026-07-13). [MirrorRepository]
 *   설계대로: observeAll 구독 → **30초 디바운스 + 해시 비교 시에만 push** + ReminderWorker(~3h) 백업.
 *   돈 요약(오늘 입금/미수금)은 정산과 같은 계산([CashFlowCalc]/[SettlementCalc]) 재사용 = 홈/정산과 항상 일치.
 *
 * 옵트인 — mirrorEnabled && mirrorToken 있을 때만 동작. 꺼져 있으면 조용히 skip(비용 0).
 */
class MirrorSyncManager(
    private val repo: MirrorRepository,
    private val prefs: AppPreferences,
    private val customerRepository: CustomerRepository,
    private val manualCashRepository: ManualCashRepository,
    private val sharedSiteRepository: SharedSiteRepository
) {
    private val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.KOREA)

    /**
     * 라이브 전송 — 일정/직접현금이 바뀌면 30초 잠잠해진 뒤 1회 push.
     *   collectLatest = 새 변경이 오면 대기 중이던 30초를 취소·재시작 → 자연스러운 디바운스.
     */
    fun start(scope: CoroutineScope) {
        scope.launch {
            combine(
                customerRepository.observeAll(),
                manualCashRepository.observeAll()
            ) { _, _ -> Unit }.collectLatest {
                if (!isActive()) return@collectLatest
                delay(30_000)               // 디바운스: 다음 변경이 오면 이 대기가 취소됨
                runCatching { pushNow(force = false) }
            }
        }
    }

    // v2: 옵트인 토글만으로 판정(token 개념 없음). 본폰이 수락하면 이 스냅샷을 봄.
    private fun isActive(): Boolean = prefs.mirrorEnabled

    /**
     * 스냅샷 1회 전송. force=false 면 직전 전송과 내용이 같을 때 skip(해시 비교).
     *   전송 성공했을 때만 해시/시각 저장 → 실패하면 다음 기회(변경·워커)에 재시도.
     *   @return 실제로 보냈으면 true.
     */
    suspend fun pushNow(force: Boolean): Boolean {
        if (!isActive()) return false
        val ownerPhone = prefs.bizPhone.trim()
        if (ownerPhone.filter { it.isDigit() }.length < 9) return false

        val now = System.currentTimeMillis()
        val todayStart = DateTimeUtils.startOfDay(now)
        // 고객 읽기가 '예외로' 실패하면(빈 결과가 진짜 0건이 아님) 빈 스냅샷을 밀지 않고 skip —
        //   안 그러면 본폰 뷰어의 일정이 순간 텅 비는 착시가 생김. 진짜 0건(신규 사장님)이면 성공이라 그대로 진행. (2026-08-11 감사)
        val customers = runCatching { customerRepository.allOnce() }.getOrNull() ?: return false
        val manual = runCatching { manualCashRepository.observeAll().first() }.getOrDefault(emptyList())

        // 일정 — 시공일이 잡힌 현장 전부(본폰은 사장님 본인 폰 → 전화·메모까지 포함, 처리방침 확정).
        val ownItems = customers
            .filter { (it.scheduledWorkDate ?: 0L) > 0L }
            .map { c ->
                val day = DateTimeUtils.startOfDay(c.scheduledWorkDate!!)
                MirrorRepository.MirrorItem(
                    date = dateFmt.format(Date(day)),
                    time = c.scheduledWorkMinutes?.let { "%02d:%02d".format(it / 60, it % 60) },
                    days = c.scheduledWorkDays.coerceAtLeast(1),
                    name = c.name?.takeIf { it.isNotBlank() } ?: "현장",
                    address = c.address?.takeIf { it.isNotBlank() },
                    // 고객 전화 = 하이픈 포함으로(사장님 요청). tel: 링크는 하이픈 있어도 동작.
                    phone = c.phoneNumber.takeIf { it.isNotBlank() }?.let { PhoneNumberFormatter.format(it) },
                    memo = c.memo.takeIf { it.isNotBlank() },
                    completed = c.isWorkDone,   // 잔금 받으면=완료 통일 (2026-08-18)
                    // 총금액 = 정산과 같은 계산(총액 없으면 계약금+잔금). 0=미입력 → 뷰어가 숨김.
                    total = SettlementCalc.rowOf(c).total
                )
            }

        val items = (ownItems + collabItems(ownerPhone)).sortedWith(
            compareBy({ it.date }, { it.time ?: "" })
        )

        // 돈 — 오늘 입금 / **지금까지 받은 돈 누적** / 미수금(합계+건수). 전부 정산과 동일 계산(숫자 어긋남 방지).
        val cashItems = CashFlowCalc.buildItems(customers, manual, emptyList(), todayStart)
        val todayIn = cashItems
            .filter { it.isIncome && it.isDone && it.dayStartMs == todayStart }
            .sumOf { it.amount }
        // 사장님: "오늘 입금이 아니라 지금까지 입금된 금액이 나와야 할 듯" → 날짜 제한 없이 '실제로 받은 것'만 합산.
        //   isDone=false(받을 예정)는 아직 안 받은 돈이라 제외 — 미수금 칸이 그걸 보여준다.
        val totalIn = cashItems.filter { it.isIncome && it.isDone }.sumOf { it.amount }
        val moneyRows = customers.filter { SettlementCalc.hasMoney(it) }.map { it to SettlementCalc.rowOf(it) }
        val unpaid = moneyRows.sumOf { it.second.outstanding }
        val unpaidCount = moneyRows.count { it.second.outstanding > 0L }
        // 미수 현장 목록 — 큰 금액순. 뷰어 "미수금 N건" 탭 시 어디서 얼마 못 받았나.
        val receivables = moneyRows
            .filter { it.second.outstanding > 0L }
            .map { (c, row) ->
                MirrorRepository.Receivable(
                    name = c.name?.takeIf { it.isNotBlank() } ?: "현장",
                    amount = row.outstanding,
                    address = c.address?.takeIf { it.isNotBlank() },
                    phone = c.phoneNumber.takeIf { it.isNotBlank() }?.let { PhoneNumberFormatter.format(it) },
                    overdueDays = SettlementCalc.overdueDays(c, todayStart),
                    // 미수가 걸린 날 = 완료일 우선, 없으면 시공 예약일 (overdueDays 와 같은 기준).
                    //   뷰어가 달력에 연한 빨강으로 칠하는 데 씀.
                    date = (c.workCompletedAt ?: c.scheduledWorkDate)
                        ?.takeIf { it > 0L }
                        ?.let { dateFmt.format(Date(DateTimeUtils.startOfDay(it))) }
                )
            }
            .sortedByDescending { it.amount }

        val label = prefs.mirrorLabel.ifBlank { prefs.bizName.ifBlank { "내 일정" } }
        val hash = buildHash(label, items, todayIn, totalIn, unpaid, unpaidCount, receivables)
        if (!force && hash == prefs.mirrorLastHash) return false

        val res = repo.pushSnapshot(ownerPhone, label, items, todayIn, totalIn, unpaid, unpaidCount, receivables)
        return if (res.isSuccess) {
            prefs.mirrorLastHash = hash
            prefs.mirrorLastPushMs = System.currentTimeMillis()
            true
        } else {
            false
        }
    }

    /**
     * 협업 현장(내가 수락한 것)도 일정이다 → 미러에 같이 실어 보낸다.
     *   (2026-07-15 사장님 신고: "협업현장으로 수락한 현장도 일정인데 노출이 안 되고 있어".
     *    원인 = 스냅샷을 customers 에서만 만들어서 — 협업 현장은 고객이 아니라 SharedSite 라 아예 안 실렸음.)
     *
     * 규칙:
     *  - 내가 **수락한**(accepted) + 날짜 잡힌 것만. 일정 화면(ScheduleViewModel)과 같은 필터.
     *  - 일정 화면에서 밀어서 숨긴 협업(hiddenCollabShareIds)은 미러에서도 숨김 → 두 화면이 항상 같게.
     *  - **금액 = 내 일당만**(사장님: "협업은 내가 받아야 할 돈(일당)만 보이면 되는거지").
     *    dailyWage 는 **만원 단위** → 원으로 환산(×10000). 남의 고객 시공비는 안 보냄.
     *  - **전화번호 없음** — 협업 현장엔 고객 번호 자체가 없다(벽, SPEC §1). phone=null 로 고정.
     *  - 미수금/오늘입금 계산엔 안 섞는다(정산 1단계 제외, SPEC §3) → 정산 화면 숫자와 어긋나지 않게.
     *  - 네트워크 실패해도 스냅샷 전체를 죽이지 않는다(내 현장만 나가고 다음 기회에 재시도).
     */
    private suspend fun collabItems(ownerPhone: String): List<MirrorRepository.MirrorItem> {
        val sites = runCatching { sharedSiteRepository.withMe(ownerPhone).getOrDefault(emptyList()) }
            .getOrDefault(emptyList())
        return mapCollabSites(sites, prefs.hiddenCollabShareIds)
    }

    companion object {
        /**
         * 협업 현장 → 미러 아이템 (순수 함수 — 네트워크/프레임워크 없음, 단위테스트 대상: [MirrorCollabMapTest]).
         *   금액 단위 환산(만원→원)이 있어서 반드시 테스트로 고정한다.
         *   (과거 사고: 접수서 계약금이 만원↔원 이중 환산돼 10억으로 저장됨 — 단위는 항상 양쪽을 맞춰 검증.)
         */
        fun mapCollabSites(
            sites: List<SharedSiteRepository.SharedSite>,
            hidden: Set<String>
        ): List<MirrorRepository.MirrorItem> {
            val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.KOREA)
            return sites
                .filter { it.status == "accepted" && it.scheduledAtMs > 0L && it.shareId !in hidden }
                .map { s ->
                    MirrorRepository.MirrorItem(
                        date = fmt.format(Date(DateTimeUtils.startOfDay(s.scheduledAtMs))),
                        time = collabTime(s),
                        days = 1,                       // 협업 현장엔 기간(일수) 개념이 아직 없음(서버 미구현)
                        name = siteDisplayName(s),      // 주소라벨 > 현장명 > 주소 — 일정 화면과 같은 규칙
                        address = s.addr?.takeIf { it.isNotBlank() },
                        phone = null,                   // 벽 — 협업 현장은 고객 번호를 갖지 않는다
                        memo = s.memo?.takeIf { it.isNotBlank() },
                        completed = s.progress == SharedSiteRepository.Progress.COMPLETED,
                        // 일당: dailyWage 는 **만원 단위**, MirrorItem.total 은 **원** → ×10000. 없으면 0(뷰어가 숨김).
                        total = s.dailyWage?.takeIf { it > 0 }?.let { it * 10_000L } ?: 0L,
                        collab = true
                    )
                }
        }

        /** 협업 현장 시각 "HH:mm" — scheduledAtMs 에 박힌 시각 우선, 자정(미설정)이면 서버 timeLabel. 없으면 null. */
        private fun collabTime(s: SharedSiteRepository.SharedSite): String? {
            val cal = java.util.Calendar.getInstance().apply { timeInMillis = s.scheduledAtMs }
            val h = cal.get(java.util.Calendar.HOUR_OF_DAY)
            val m = cal.get(java.util.Calendar.MINUTE)
            if (h != 0 || m != 0) return "%02d:%02d".format(h, m)
            return s.timeLabel?.takeIf { it.isNotBlank() && it != "00:00" && it != "0:00" }
        }
    }

    /**
     * 새 '일정 공유 신청' 폴 → 알림. 포그라운드 60초 루프 + ReminderWorker(~3h)에서 호출.
     *   이미 알림 띄운 share_id(mirrorSeenShareIds)는 건너뜀. 옵트인 꺼져 있으면 skip.
     */
    suspend fun pollShareRequests(context: android.content.Context) {
        if (!prefs.mirrorEnabled) return
        val owner = prefs.bizPhone.trim()
        if (owner.filter { it.isDigit() }.length < 9) return
        val shares = repo.shares(owner).getOrNull() ?: return
        val seen = prefs.mirrorSeenShareIds
        val fresh = shares.pending.filter { it.id.toString() !in seen }
        if (fresh.isEmpty()) return
        val label = PhoneNumberFormatter.format(fresh.first().homePhone)
        com.detailline.callfollowcrm.service.NotificationHelper.showMirrorShareRequest(context, label, fresh.size)
        prefs.mirrorSeenShareIds = seen + fresh.map { it.id.toString() }
    }

    /** 내용 지문 — 바뀐 게 없으면 재전송 안 하려고. items 순서·필드 + 돈 요약 전부 반영. */
    private fun buildHash(
        label: String,
        items: List<MirrorRepository.MirrorItem>,
        todayIn: Long,
        totalIn: Long,
        unpaid: Long,
        unpaidCount: Int,
        receivables: List<MirrorRepository.Receivable>
    ): String {
        val sb = StringBuilder()
        sb.append(label).append('|').append(todayIn).append('|').append(totalIn)
            .append('|').append(unpaid).append('|').append(unpaidCount)
        for (r in receivables) {
            sb.append('₩').append(r.name).append('~').append(r.amount).append('~').append(r.overdueDays ?: -1)
                .append('~').append(r.date ?: "")   // 날짜만 바뀌어도 재전송되게
        }
        for (it in items) {
            sb.append('¶')
                .append(it.date).append('~').append(it.time ?: "").append('~').append(it.days)
                .append('~').append(it.name).append('~').append(it.address ?: "")
                .append('~').append(it.phone ?: "").append('~').append(it.memo ?: "")
                .append('~').append(it.completed).append('~').append(it.total)
                .append('~').append(it.collab)   // 협업 여부도 지문에 — 빠지면 협업만 바뀌었을 때 전송이 스킵된다
        }
        return sb.toString().hashCode().toString()
    }
}
