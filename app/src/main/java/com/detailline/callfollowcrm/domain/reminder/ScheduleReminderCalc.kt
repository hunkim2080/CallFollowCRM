package com.detailline.callfollowcrm.domain.reminder

import com.detailline.callfollowcrm.data.local.entity.CustomerEntity

/** 시공 전 안내 종류 — 전날(D1) / 당일 출발(ARRIVAL). */
enum class ReminderKind { D1, ARRIVAL }

/** "시공 안내 문자 보낼까요?" 한 건 (고객 × 종류). renderedBody 는 VM 이 상호/날짜로 채움. */
data class ReminderItem(
    val kind: ReminderKind,
    val customerId: Long,
    val customerName: String?,
    val phone: String,
    val scheduledDayStartMs: Long
)

/**
 * 시공 D-1 / 도착 안내 리마인드 계산 — 순수 함수 (android 비의존, 테스트 가능).
 *
 * 사장님 명시 요청(2026-05-28): 시공 전날 알림. 정책상 자동발송 X → "보낼까요?" 안내만,
 *   실제 발송은 사장님이 채팅에서 확인 후. 정기문자와 동일 패턴(포그라운드 계산 + 확인 후 발송).
 *
 * dedupe 는 recurring_message_log 재사용 — 음수 sentinel ruleId(RULE_D1/RULE_ARRIVAL)로 구분.
 *   양수 ruleId(정기문자)와 키가 겹치지 않으므로 한 테이블 공유 안전.
 */
object ScheduleReminderCalc {

    /** 로그 재사용용 sentinel ruleId. 정기문자(양수)와 구분. */
    const val RULE_D1 = -1L
    const val RULE_ARRIVAL = -2L

    private const val DAY_MS = 24L * 60 * 60 * 1000

    fun ruleIdOf(kind: ReminderKind): Long = if (kind == ReminderKind.D1) RULE_D1 else RULE_ARRIVAL

    /**
     * @param loggedKeys 이미 보냄/넘김 처리된 (ruleId, customerId, scheduledDayStartMs)
     * @param todayStartMs 오늘 자정 ms (호출자 정규화)
     * @param arrivalEnabled 도착 안내(위치 기반) 토글. OFF 면 ARRIVAL 카드 안 띄움.
     * @param arrivalEnteredCustomerIds 오늘 5km 지오펜스 진입한 고객 ID. ARRIVAL 은 진입한 현장만.
     *   → "오늘 시공일"이라고 무조건 뜨던 버그(위치·토글 무시) 방지. D1(전날 안내)은 위치 무관, 날짜 기준 유지.
     * @param d1TimeReached 설정한 D-1 안내 시각(d1SendHour, 예 오후 6시)이 됐는지. false 면 D1 카드 숨김.
     *   → "오후 6시로 설정했는데 자정(날짜 바뀔 때)에 갑자기 떴다"는 버그 방지(알림은 ReminderWorker 가 이미 시각 체크).
     *   (2026-06-18 사장님) 호출자가 현재 시각 vs d1SendHour 로 계산해 넘김.
     * 반환: 오늘 보낼 도착안내(ARRIVAL) + 내일 시공 전날안내(D1). 시공일 가까운 순.
     */
    fun compute(
        customers: List<CustomerEntity>,
        loggedKeys: Set<Triple<Long, Long, Long>>,
        todayStartMs: Long,
        arrivalEnabled: Boolean = false,
        arrivalEnteredCustomerIds: Set<Long> = emptySet(),
        d1Enabled: Boolean = true,
        d1TimeReached: Boolean = true
    ): List<ReminderItem> {
        val tomorrowStart = todayStartMs + DAY_MS
        val out = ArrayList<ReminderItem>()
        for (c in customers) {
            val day = c.scheduledWorkDate ?: continue
            val kind = when (day) {
                todayStartMs -> ReminderKind.ARRIVAL
                tomorrowStart -> ReminderKind.D1
                else -> continue
            }
            // ARRIVAL 은 토글 ON + 실제 5km 진입한 현장만 (그냥 시공일이라고 뜨면 안 됨).
            if (kind == ReminderKind.ARRIVAL && (!arrivalEnabled || c.id !in arrivalEnteredCustomerIds)) continue
            // D-1 도 토글 따라가게 — OFF 면 홈 "내일 시공 안내" 카드 안 띄움(알림은 ReminderWorker 가 이미 토글 체크). (2026-06-18 사장님)
            // + 설정한 시각(d1SendHour) 전엔 안 띄움 — "오후 6시 설정인데 자정에 갑툭" 방지(알림과 시각 일치). (2026-06-18 사장님)
            if (kind == ReminderKind.D1 && (!d1Enabled || !d1TimeReached)) continue
            val key = Triple(ruleIdOf(kind), c.id, day)
            if (key in loggedKeys) continue
            out += ReminderItem(kind, c.id, c.name, c.phoneNumber, day)
        }
        // ARRIVAL(오늘) 먼저, 그 다음 D1(내일).
        return out.sortedBy { it.scheduledDayStartMs }
    }
}
