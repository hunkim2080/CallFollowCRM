package com.detailline.callfollowcrm.domain.schedule

/**
 * 🖐 **연타냐, 하루 두 현장이냐.** (2026-09-28)
 *
 * 왜 갈라야 하나 —
 *   사장님이 2026-09-11 에 하신 말: **"인테리어 업체는 한 번호에 현장 여러 개"**.
 *   아침 한 집, 오후 한 집은 줄눈에선 흔하다. 그런데 전엔 「같은 고객 + 같은 날」이면
 *   무조건 안 만들었다. 둘째 현장을 넣으면 **주소도 돈도 통째로 사라지고**
 *   화면은 「일정 등록 완료」라고 했다.
 *
 *   뿌리는 **연타(같은 걸 두 번 누름)** 와 **다른 일**을 같은 자로 잰 것이다.
 *
 * 여기 규칙 —
 *   · 같은 날 + **같은 주소** → 같은 현장. 막는다.
 *   · 같은 날 + 몇 초 안에 또 → 손가락이 두 번 닿은 것. 막는다.
 *   · 주소가 다르거나 한쪽이 비었으면 → **다른 현장**. 만든다.
 *
 * Android 의존성 없음 → 폰 없이도 매 빌드가 검사한다([SameDayGuardTest]).
 */
object SameDayGuard {

    /** 손가락이 두 번 닿은 것으로 보는 시간. */
    const val DOUBLE_TAP_MS = 10_000L

    /** 이미 있는 그 날 건 하나의 정보 — 주소와 만들어진 시각만 본다. */
    data class Existing(val address: String?, val createdAt: Long)

    /**
     * 이 등록이 **연타인가**(= 만들지 말아야 하는가).
     * @param sameDay 같은 고객·같은 날에 이미 있는 건들
     * @param address 지금 넣으려는 주소 (비어 있을 수 있다)
     * @param now 지금 시각
     */
    fun isRetap(sameDay: List<Existing>, address: String?, now: Long): Boolean {
        val addr = address?.trim().orEmpty()
        return sameDay.any { old ->
            val oldAddr = old.address?.trim().orEmpty()
            // 주소가 둘 다 적혀 있고 같으면 같은 현장이다. 한쪽이라도 비었으면 판단 못 한다 —
            //   그땐 「다른 현장」 쪽으로 봐준다. 잘못 막으면 일정이 사라지고, 잘못 만들면 지우면 된다.
            val sameSite = addr.isNotEmpty() && oldAddr.isNotEmpty() && addr == oldAddr
            val justNow = now - old.createdAt in 0 until DOUBLE_TAP_MS
            sameSite || justNow
        }
    }
}
