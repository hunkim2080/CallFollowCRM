package com.detailline.callfollowcrm.util

/**
 * "이게 **어느 현장**인가" 를 한 줄로. (2026-09-23 사장님)
 *
 * 왜 생겼나 — 정산 화면에서 **번호가 제목**이었다. `010-8005-6674` 만 봐서는
 * 사장님이 그게 어느 현장인지 알 수가 없다. 사장님 말:
 * *"돈을 받았으면 번호가 아니라 어떤 현장인지. 번호는 작게 표현해도 되는데."*
 *
 * 순서: **이름 → 주소(줄여서) → 번호**. 번호는 마지막 수단이다.
 */
object SiteLabel {

    /** 앞에 붙은 시·도는 떼도 알아본다 — "경기도 화성시…" 의 '경기도'는 현장 구분에 도움이 안 된다. */
    private val PROVINCE = Regex("""^(서울|부산|대구|인천|광주|대전|울산|세종|경기|강원|충북|충남|전북|전남|경북|경남|제주)\S*$""")

    /** 동·호수 꼬리 — 제목에선 뺀다(주소 줄에 이미 있다). */
    private val UNIT_TAIL = Regex("""^\d+(동|호|층)$""")

    /**
     * 카드 제목. 이름이 있으면 이름, 없으면 주소를 줄여서, 그것도 없으면 번호.
     *
     * 주소는 **시·도를 떼고 앞 세 토막**만 — "경기 시흥시 가마길 4 ㅓㅁ머머" → "시흥시 가마길 4".
     */
    fun of(name: String?, address: String?, phone: String?): String {
        name?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
        shortAddress(address)?.let { return it }
        return PhoneNumberFormatter.format(phone.orEmpty())
    }

    /** 주소를 제목에 쓸 만큼 줄인다. 쓸 게 없으면 null. */
    fun shortAddress(address: String?): String? {
        val raw = address?.trim().orEmpty()
        if (raw.isBlank()) return null
        val parts = raw.split(Regex("""\s+""")).filter { it.isNotBlank() }
        if (parts.isEmpty()) return null
        val body = if (parts.size > 1 && PROVINCE.matches(parts[0])) parts.drop(1) else parts
        val picked = body.filterNot { UNIT_TAIL.matches(it) }.take(3)
        val out = (if (picked.isEmpty()) body.take(3) else picked).joinToString(" ")
        return out.takeIf { it.isNotBlank() }
    }

    /**
     * 제목 밑에 **작게** 붙일 곁줄. 제목이 이미 번호면 없음(같은 말 두 번 안 한다).
     */
    fun sub(name: String?, address: String?, phone: String?): String? {
        val tel = PhoneNumberFormatter.format(phone.orEmpty()).takeIf { it.isNotBlank() } ?: return null
        val title = of(name, address, phone)
        return if (title == tel) null else tel
    }
}
