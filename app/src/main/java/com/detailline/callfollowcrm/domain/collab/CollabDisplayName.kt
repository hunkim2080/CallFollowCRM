package com.detailline.callfollowcrm.domain.collab

/**
 * 🤝 **협업 상대를 뭐라고 부를까 — 한 곳에서.** (2026-10-27 사장님 규칙 확정)
 *
 * 우선순위(사장님):
 *   ① **내가 저장/지은 이름** — 명부에 그 번호로 저장한 상호(「탄성 파인」) 또는 꾹 눌러 지은 이름.
 *   ② 없으면 **상대가 자기 폰에 등록한 사업자명** — 서버 by-me 의 `partner_name`(수락할 때 채워진다).
 *   ③ 그것도 없으면 **핸드폰 번호**(010-0000-0000).
 *
 * 전엔(2026-10-18 A안) 「상호(내 이름표)」로 **② 상호를 ① 내 이름보다 먼저** 보였는데,
 * 「협업 사장(탄성 파인)」처럼 지저분하고 **일정(merge)·고객상세(원본 이름)가 달라** 헷갈렸다.
 * → 사장님 규칙으로 **내 이름 우선**, 깔끔하게 하나만.
 *
 * ⚠️ **명부·`collabAssignments` 의 원본 이름은 안 건드린다** — 보여줄 때만 고른다.
 *   (저장된 번호 → 명부 이름 교정은 `ScheduleViewModel.resolveCollabNamesFromContacts` 가 원본에 1회.)
 */
object CollabDisplayName {

    /** 명부에 없는 번호로 요청할 때 로컬 줄에 적히는 자리표시 — 「내가 지은 이름」이 아니다. */
    const val PLACEHOLDER = "협업 사장님"
    private const val PLACEHOLDER2 = "협업 사장"

    /** 진짜 사람 이름인가 — 비었거나·자리표시·숫자(번호)뿐이면 아니다. */
    private fun isRealName(s: String): Boolean =
        s.isNotEmpty() && s != PLACEHOLDER && s != PLACEHOLDER2 && !s.all { it.isDigit() }

    /** 숫자만 → 010-0000-0000. 11자리(휴대폰) 3-4-4, 10자리 3-3-4, 그 외는 그대로. */
    private fun formatPhone(digits: String): String = when (digits.length) {
        11 -> "${digits.substring(0, 3)}-${digits.substring(3, 7)}-${digits.substring(7)}"
        10 -> "${digits.substring(0, 3)}-${digits.substring(3, 6)}-${digits.substring(6)}"
        else -> digits
    }

    /**
     * @param nickname 내가 저장/지은 이름(없으면 번호·자리표시가 들어온다)
     * @param partnerName 상대가 등록한 사업자명(서버, 수락 시)
     */
    fun merge(nickname: String, partnerName: String?, accepted: Boolean): String {
        val nick = nickname.trim()
        val real = partnerName?.trim().orEmpty()
        if (isRealName(nick)) return nick                 // ① 내가 저장/지은 이름
        if (accepted && isRealName(real)) return real     // ② 상대가 등록한 사업자명
        val digits = nick.filter { it.isDigit() }.ifEmpty { real.filter { it.isDigit() } }
        return if (digits.isNotEmpty()) formatPhone(digits) else ""   // ③ 번호 (없으면 빈 글자 — 부르는 쪽이 거른다)
    }
}
