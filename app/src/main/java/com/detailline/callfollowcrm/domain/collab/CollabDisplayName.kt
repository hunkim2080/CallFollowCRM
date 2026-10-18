package com.detailline.callfollowcrm.domain.collab

/**
 * 🤝 **협업 상대를 뭐라고 부를까 — 한 곳에서.** (2026-10-18 사장님 A안)
 *
 * 같은 사람이 **두 이름**으로 보였다 —
 *   · 일정 탭 「🤝 배지」엔 **내가 명부에 적은 이름**(「김포 김사장」, `collabAssignments` 의 name)
 *   · 협업 현장엔 **상대가 등록한 상호**(「대박줄눈」, 서버 by-me 의 `partner_name` — 상대가 수락할 때 채워진다)
 * 사장님이 보기에 헷갈린다. 그래서 **수락 전엔 내 이름표 그대로**, **수락해서 진짜 상호가 생기면 상호를 앞에,
 * 내 이름표는 괄호로** — 「대박줄눈(김포 김사장)」.
 *
 * 규칙(순수 — [CollabDisplayNameTest] 가 매 빌드 본다):
 *   · 미수락 · 상호 없음 → 내 이름표만.
 *   · 상호 == 내 이름표(공백 무시) → 하나만.
 *   · 내 이름표가 비었거나 자리표시(「협업 사장님」)뿐 → 상호만. (「대박줄눈(협업 사장님)」 은 뜻이 없다)
 *   · 그 외 → 「상호(내 이름표)」.
 *
 * ⚠️ **명부·`collabAssignments` 의 원본 이름은 안 건드린다** — 보여줄 때만 합친다. 꾹 눌러 바꾸는 rename 은 원본에 간다.
 */
object CollabDisplayName {

    /** 명부에 없는 번호로 요청할 때 로컬 줄에 적히는 자리표시 — 이건 「내가 지은 이름」이 아니다. */
    const val PLACEHOLDER = "협업 사장님"

    fun merge(nickname: String, partnerName: String?, accepted: Boolean): String {
        val nick = nickname.trim()
        val real = partnerName?.trim().orEmpty()
        if (!accepted || real.isEmpty()) return nick
        if (nick.isEmpty() || nick == PLACEHOLDER || nick == real) return real
        return "$real($nick)"
    }
}
