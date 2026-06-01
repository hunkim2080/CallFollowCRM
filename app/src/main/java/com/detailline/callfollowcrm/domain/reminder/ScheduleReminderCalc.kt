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
     * 반환: 오늘 보낼 도착안내(ARRIVAL) + 내일 시공 전날안내(D1). 시공일 가까운 순.
     */
    fun compute(
        customers: List<CustomerEntity>,
        loggedKeys: Set<Triple<Long, Long, Long>>,
        todayStartMs: Long
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
            val key = Triple(ruleIdOf(kind), c.id, day)
            if (key in loggedKeys) continue
            out += ReminderItem(kind, c.id, c.name, c.phoneNumber, day)
        }
        // ARRIVAL(오늘) 먼저, 그 다음 D1(내일).
        return out.sortedBy { it.scheduledDayStartMs }
    }
}
