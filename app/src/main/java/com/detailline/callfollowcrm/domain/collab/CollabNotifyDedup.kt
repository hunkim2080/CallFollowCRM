package com.detailline.callfollowcrm.domain.collab

/**
 * 🤝 협업 진행 알림 **한 번만** — 같은 이벤트가 두 길로 와도 한 번만 띄우는 셈. (2026-10-15 사장님 "출발이 왜 2번 찍히지")
 *
 * 왜 두 번 왔나 — 상대 사장이 「출발」을 누르면 서버가 **① FCM 즉시 푸시**를 쏘고, **같은 이벤트를
 *   ② owner-events 폴링**(60초·앱 복귀·ReminderWorker)도 가져온다. 둘 다 `showCollabEvent` 를 부르는데
 *   ①은 「본 이벤트」를 아무 데도 적지 않아서 ②가 **같은 걸 또 새것으로** 띄웠다(알림 센터에 두 줄).
 *   상태바는 같은 notifId 라 덮여서 하나 — 그래서 **센터에서만** 두 줄로 보였다.
 *
 * 열쇠 = shareId | 단계(step) | 서버 이벤트 id. 서버는 FCM 과 폴링에 **같은 event_id** 를 준다.
 *   본 열쇠는 24시간만 기억한다(그 뒤엔 같은 열쇠가 와도 새것으로 — 되돌리기·재수락 같은 정상 재발을 막지 않게).
 *   최대 [CAP] 개(오래된 것부터 버림).
 *
 * 순수 함수 — 기억(prefs)과 잠금은 부르는 쪽(NotificationHelper.showCollabEvent 게이트 한 곳)이 든다.
 */
object CollabNotifyDedup {

    /** 같은 열쇠를 기억하는 시간. */
    const val WINDOW_MS: Long = 24L * 60 * 60 * 1000

    /** 기억하는 열쇠 상한 — 넘치면 오래된 것부터 버린다. */
    const val CAP: Int = 300

    /** 열쇠 — FCM 과 폴링이 **같은 글자**를 만들게 한 곳에서만 만든다. */
    fun key(shareId: String, kind: String, eventId: String): String =
        "${shareId.trim()}|${kind.trim()}|${eventId.trim()}"

    /** @param admit 띄워도 되나. @param next 이번 판정 뒤의 기억(그대로 저장하면 된다). */
    data class Verdict(val admit: Boolean, val next: Set<String>)

    /**
     * 이 열쇠를 지금 띄워도 되나.
     *   · 24시간 안에 본 열쇠 → admit=false (기억은 만료분만 정리).
     *   · 처음 → admit=true, 기억에 「열쇠@시각」으로 적는다(상한 넘치면 오래된 것부터 버림).
     */
    fun admit(seen: Set<String>, key: String, nowMs: Long): Verdict {
        val fresh = seen.mapNotNull(::decode).filter { nowMs - it.second < WINDOW_MS }
        if (fresh.any { it.first == key }) return Verdict(false, fresh.map(::encode).toSet())
        val kept = (fresh + (key to nowMs)).sortedBy { it.second }.takeLast(CAP)
        return Verdict(true, kept.map(::encode).toSet())
    }

    private fun encode(p: Pair<String, Long>): String = "${p.first}@${p.second}"

    /** 「열쇠@시각」 → (열쇠, 시각). 모양이 아니면 null(= 버린다). 열쇠 안에 '@' 가 있어도 마지막 '@' 로 가른다. */
    private fun decode(s: String): Pair<String, Long>? {
        val i = s.lastIndexOf('@')
        if (i <= 0) return null
        val at = s.substring(i + 1).toLongOrNull() ?: return null
        return s.substring(0, i) to at
    }
}
