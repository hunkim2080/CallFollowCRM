package com.detailline.callfollowcrm.util

/**
 * ☎️ **이 번호는 어떤 번호인가** — 전화가 왔을 때 한눈에 알려주려고. (2026-09-27 사장님)
 *
 * 사장님: "070 번호는… 너무 대놓고 광고 전화야. **사용자들이 착각하고 반갑게 받을까 봐 겁나네.**"
 *         "그리고 02, 031 이런 일반 번호도. 알기 쉽게 표현할 방법 없을까?
 *          **고객이 핸드폰으로 하지 이런 집전화로 안 하거든**"
 *
 * 둘을 **같이 취급하면 안 된다.**
 *   · 070·050·0507·15xx 는 **거의 광고**다 — 빨갛게 세워 막는다.
 *   · 02·031 은 **손님이 잘 안 쓰는 번호**일 뿐이다 — 관공서·거래처·진짜 문의일 수도 있다.
 *     단정하지 않고 **사실만** 말한다: "손님은 보통 휴대폰으로 걸어요".
 *
 * ⚠️ 여기서 **아는 손님인지는 안 본다.** 그건 부르는 쪽(통화 카드)이 판단한다 —
 *   사무실 번호로 거는 진짜 고객이 있기 때문이다.
 */
object PhoneKind {

    enum class Kind {
        /** 010·011·016~019 — 사람이 들고 다니는 전화. */
        MOBILE,
        /** 070·050·0507·060 인터넷·안심번호, 15xx·16xx·18xx 대표번호 — 거의 광고. */
        AD,
        /** 02·031~064 지역번호 — 집·사무실 전화. 손님은 잘 안 쓴다. */
        LANDLINE,
        /** 모르겠다(번호가 짧거나 국제 등). */
        UNKNOWN
    }

    private val MOBILE_HEADS = listOf("010", "011", "016", "017", "018", "019")

    /** 인터넷전화·안심번호·060 — 광고로 보는 앞자리. */
    private val AD_HEADS = listOf("0507", "070", "050", "060")

    /** 지역번호 — 02 는 두 자리, 나머지는 세 자리. */
    private val AREA_HEADS = listOf(
        "031", "032", "033", "041", "042", "043", "044",
        "051", "052", "053", "054", "055", "061", "062", "063", "064", "02"
    )

    fun of(phone: String?): Kind {
        val d = normalize(phone)
        if (d.length < 8) return Kind.UNKNOWN
        if (MOBILE_HEADS.any { d.startsWith(it) }) return Kind.MOBILE
        // 8자리 대표번호 — 1588·1644·1899 … 카드사·보험·통신사 광고가 대부분.
        if (d.length == 8 && (d.startsWith("15") || d.startsWith("16") || d.startsWith("18"))) return Kind.AD
        if (AD_HEADS.any { d.startsWith(it) }) return Kind.AD
        if (AREA_HEADS.any { d.startsWith(it) }) return Kind.LANDLINE
        return Kind.UNKNOWN
    }

    /** 카드 머리에 쓸 한 줄. MOBILE·UNKNOWN 이면 null(평소대로). */
    fun headline(kind: Kind): String? = when (kind) {
        Kind.AD -> "광고 전화로 의심"
        Kind.LANDLINE -> "집·사무실 전화"
        else -> null
    }

    /** 왜 그렇게 보는지 — 단정하지 않고 사실만. */
    fun reason(kind: Kind): String? = when (kind) {
        Kind.AD -> "070 같은 번호예요"
        Kind.LANDLINE -> "손님은 보통 휴대폰으로 걸어요"
        else -> null
    }

    private fun normalize(phone: String?): String {
        var d = (phone ?: "").filter { it.isDigit() }
        if (d.startsWith("82")) d = "0" + d.removePrefix("82")
        return d
    }
}
