package com.detailline.callfollowcrm.domain.customer

/**
 * ⏸️ **보류 손님** — 계약금은 받았는데 **날짜를 고객이 미룬** 손님. (2026-10-16 사장님, 프로토 artifact/D4EwQSrXk4TFPzc9gjW6Cs)
 *
 *   사장님: *"시공 계약금까지 다 받았는데, 고객이 와이프가 아프다고 일정을 추후에 본인이 다시 연락하겠다고 함.
 *   그럼 이 사람을 어디다 둬야 하지?"*
 *   「시공 대기」는 **날짜가 있어야** 들어가고, 「잔금 대기」는 **시공이 끝나야** 들어간다.
 *   계약금 받고 날짜 없는 손님은 **어느 칸에도 없어 잊힌다** — 돈 낸 손님이 제일 아깝다.
 *
 *   판단은 **여기 한 곳**이다. 상담함 칩 숫자·칩 목록·고객 상세 줄·달력 미러가 전부 이걸 본다.
 *   (A/S 명단(`asPendingAt`)과 같은 모양 — 사장님이 손으로 찍는 예외 칸.)
 *
 *   규칙:
 *     · 보류 = `onHoldAt` 이 찍혀 있고 **아직 안 끝난** 손님. 완료·잔금 받음이면 보류가 아니다(끝난 일).
 *     · **날짜를 잡으면 보류가 풀린다** — [afterDateSet]. 앱이 자동으로 푼다(사장님이 또 누를 일 없게).
 *     · 3주([QUIET_DAYS]) 조용하면 안부 넛지 — 가장 오래 조용한 **1명만**([quietest]). 띠가 여러 장이면 어수선하다.
 */
object OnHold {

    /** 안부 넛지 기준 — 이만큼 조용하면 「안부 한번 물어볼까요?」. 바꿀 땐 여기만. */
    const val QUIET_DAYS: Int = 21

    private const val DAY_MS: Long = 24L * 60 * 60 * 1000

    /** 보류 손님인가 — 찍혀 있고 아직 안 끝난 손님. */
    fun isOnHold(onHoldAt: Long?, isWorkDone: Boolean): Boolean = onHoldAt != null && !isWorkDone

    /**
     * 날짜가 잡힌 뒤의 `onHoldAt` — **오늘 이후** 날짜가 생기면 보류를 푼다(null). 아니면 그대로.
     *   지난 날짜(옛 건이 대표로 잡힌 경우)로는 안 푼다 — 그건 "일정을 잡은 것"이 아니다.
     */
    fun afterDateSet(onHoldAt: Long?, scheduledWorkDate: Long?, todayStart: Long): Long? =
        if (onHoldAt != null && scheduledWorkDate != null && scheduledWorkDate >= todayStart) null else onHoldAt

    /**
     * 며칠째 조용한가 — 마지막 연락(또는 보류 시작) 이후 지난 날 수. 음수는 0.
     *   `quietSinceMs` 는 [quietSince] 로 뽑는다(마지막 연락과 보류 시작 중 **나중** 것).
     */
    fun quietDays(quietSinceMs: Long, nowMs: Long): Int =
        ((nowMs - quietSinceMs) / DAY_MS).toInt().coerceAtLeast(0)

    /** 「3주째」 — 주 단위. 21일 → 3. */
    fun quietWeeks(quietSinceMs: Long, nowMs: Long): Int = quietDays(quietSinceMs, nowMs) / 7

    /**
     * 조용해진 시점 — 마지막 연락과 보류 시작 중 **나중** 것.
     *   보류를 찍은 날이 곧 마지막으로 얘기한 날이다. 연락 기록이 없으면(0) 보류 시작으로 센다.
     */
    fun quietSince(lastActivityMs: Long, onHoldAt: Long): Long = maxOf(lastActivityMs, onHoldAt)

    /** 넛지 대상인가 — [QUIET_DAYS] 이상 조용. */
    fun needsNudge(quietSinceMs: Long, nowMs: Long): Boolean = quietDays(quietSinceMs, nowMs) >= QUIET_DAYS

    /**
     * 넛지 띠에 올릴 **딱 한 명** — 3주 이상 조용한 사람 중 가장 오래 조용한 사람. 없으면 null.
     *   @param quietSinceOf 각 항목의 조용해진 시점(ms). ([quietSince] 로 뽑은 값)
     */
    fun <T> quietest(items: List<T>, nowMs: Long, quietSinceOf: (T) -> Long): T? =
        items.filter { needsNudge(quietSinceOf(it), nowMs) }.minByOrNull { quietSinceOf(it) }
}
