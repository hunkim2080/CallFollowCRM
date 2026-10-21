package com.detailline.callfollowcrm.domain.collab

/**
 * 🤝 협업 요청 **만료** — 공유 뒤 **24시간** 안에 상대가 수락 안 하면 만료. (2026-10-20 사장님)
 *   만료돼도 자동 취소는 안 한다 — 칩에 「만료됨 · 다시 부르기」로 남겨 사장님이 보고 결정(사장님 선택).
 *
 * ⚠️ **서버도 같은 24h 로 막아야 한다** — 앱의 「만료됨」은 **보여주는 쪽**일 뿐이고,
 *   진짜로 수락을 막는 건 **서버가 최종 결정권자**다(§13②). 안 그러면 「만료됨」인데 상대는 수락된다.
 *   짝: `server/main.py` 의 `/api/shared/respond` 수락 경로 (created_at + 24h 지나면 거절). **바꾸면 양쪽 같이.**
 *
 * 순수 함수(부작용 없음) → 단위테스트로 경계값 검증.
 */
object CollabInviteExpiry {

    /** 공유 후 **이 시간(시)** 이 지나면 만료 — 문구("N시간")도 이 숫자를 쓴다(한 곳). */
    const val TTL_HOURS: Int = 24

    /** 공유 후 이 시간이 지나면 만료. 서버(`_SHARE_TTL_MS`)와 **같은 값**이어야 한다.
     *   ⚠️ 협업 수락 유효시간은 **여기 하나**다 — 옛 `ACCEPT_VALID_MS`·`COLLAB_ACCEPT_MS` 는 이걸 가리키는 별칭일 뿐. */
    const val TTL_MS: Long = TTL_HOURS * 60L * 60 * 1000

    /** 아직 수락 전인 요청이 만료됐나 — 공유 시각(createdAtMs)으로 판단. createdAtMs 0/음수면 모름 → 안 만료. */
    fun isExpired(createdAtMs: Long, now: Long): Boolean =
        createdAtMs > 0L && now - createdAtMs >= TTL_MS

    /** 만료까지 남은 시간(시간 단위, **올림**). 지났으면 0. 1시간 미만이어도 1 로 올려 "1시간 남음"으로 보인다. */
    fun remainingHours(createdAtMs: Long, now: Long): Int {
        if (createdAtMs <= 0L) return 0
        val left = createdAtMs + TTL_MS - now
        return if (left <= 0L) 0 else ((left + 3_599_999L) / 3_600_000L).toInt()
    }
}
