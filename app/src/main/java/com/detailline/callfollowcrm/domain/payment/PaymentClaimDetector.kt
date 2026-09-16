package com.detailline.callfollowcrm.domain.payment

/**
 * 고객이 **"돈 보냈다"고 말한 문자**를 알아본다. (2026-09-17 사장님)
 *
 *   "어 입금했습니다 감지 좋다. 근데 그 텍스트만 감지하나?"
 *
 * 실측(사장님 받은 문자 1,085통):
 *   입금 43 · 이체 7 · 송금 5 · 보냈 5 · 보내드렸 2 · 넣었 1  → 약 60통.
 *   "입금했습니다" 한 가지만 보면 **17통을 놓친다.**
 *
 * ⚠️ 틀리면 어느 쪽으로 틀리는가가 중요하다.
 *   못 잡으면 = 사장님이 예전처럼 직접 누른다(지금과 같다).
 *   **잘못 잡으면 = 안 받은 돈을 받았다고 기록한다 → 미수금이 조용히 사라진다.**
 *   그래서 애매하면 **안 잡는 쪽**으로 기운다. 그리고 절대 자동 처리하지 않는다(항상 물어본다).
 */
object PaymentClaimDetector {

    /** @param amountWon 문장에 적힌 금액(원). 없으면 null. */
    data class Claim(val amountWon: Long?)

    /** 돈 말고는 쓸 데가 없는 말 — 이것만으로 충분하다. */
    private const val HARD_VERB = "(?:입금|송금|이체)"

    /**
     * 돈에도 쓰지만 사진·주소·서류에도 쓰는 말 — 돈 단어가 같이 있어야 한다.
     * ⚠️ **이미 보낸 형태만** 넣는다. "보내"까지 열어두면 "잔금 보내 드리겠습니다"(아직 안 보냄)와
     *    "예약금 반환 계약조항 보내주세요"(돈 얘기가 아님)가 걸린다 — 실측에서 실제로 걸렸다.
     */
    private const val SOFT_VERB = "(?:보냈|보내\\s*드렸|넣었|쐈|쏘았)"

    /** 이미 한 일임을 나타내는 꼬리. */
    private const val DONE = "(?:드렸|하였|했|완료|해드렸|보냈|드립니다|했어|했네|했음|됐|되었|됩니다)"

    /**
     * 아직 **안 한** 일 — 앞으로 하겠다·물어본다·조건을 말한다.
     * 실제 문장: "네 제가 내일 입금할게요" / "10만원 입금이 혹시 언제쯤 가능할까요??" /
     *            "부가세 끊으려면 10프로입금해야죠"
     */
    private val NOT_YET = Regex(
        "(?:입금|송금|이체|보내|넣)[^.!?\\n]{0,12}" +
            // '부탁' 은 뺐다 — "입금했습니다. 확인 부탁드립니다" 처럼 **보낸 뒤에 덧붙이는 말**이라
            //   이걸로 거르면 진짜 입금을 놓친다(실측에서 2통 놓쳤다).
            "(?:할게|할께|하겠|할래|해야|가능|언제|할까|하면|해주|해 주|드릴게|드릴께|드리겠|드릴|주세요|주실|주시)"
    )

    private val HARD_DONE = Regex("$HARD_VERB\\s*(?:을|를|은|는|이|가)?\\s*(?:해\\s*)?$DONE")
    private val SOFT_DONE = Regex("$SOFT_VERB\\s*(?:$DONE)?")

    /** 돈 이야기임을 확인해주는 말. 소프트 동사는 이게 있어야 인정한다. */
    private val MONEY_WORD = Regex("(?:예약금|계약금|잔금|시공비|대금|금액|\\d[\\d,]*\\s*(?:만원|원))")

    /** 금액 — "100만원", "10만원", "100,000원", "십만원". */
    private val AMOUNT_MANWON = Regex("(\\d[\\d,]*)\\s*만\\s*원")
    private val AMOUNT_WON = Regex("(\\d[\\d,]{2,})\\s*원")
    private val KOR_MANWON = mapOf(
        "십만" to 100_000L, "이십만" to 200_000L, "삼십만" to 300_000L, "사십만" to 400_000L,
        "오십만" to 500_000L, "육십만" to 600_000L, "칠십만" to 700_000L, "팔십만" to 800_000L,
        "구십만" to 900_000L, "백만" to 1_000_000L
    )

    /**
     * @return 돈을 보냈다고 말한 것으로 보이면 [Claim], 아니면 null.
     */
    fun detect(body: String?): Claim? {
        val b = body?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        // ① 아직 안 한 일이면 무조건 제외 — 여기서 실수하면 안 받은 돈이 '받음'이 된다.
        if (NOT_YET.containsMatchIn(b)) return null
        // ② 확실한 말이면 그대로 인정.
        val hard = HARD_DONE.containsMatchIn(b)
        // ③ 애매한 말은 돈 단어가 같이 있을 때만.
        val soft = !hard && SOFT_DONE.containsMatchIn(b) && MONEY_WORD.containsMatchIn(b)
        if (!hard && !soft) return null
        return Claim(amountWon = amountOf(b))
    }

    /** 문장에서 금액 읽기. 못 읽으면 null — 그래도 감지는 유효하다(금액만 비워서 묻는다). */
    fun amountOf(body: String): Long? {
        AMOUNT_MANWON.find(body)?.let { m ->
            m.groupValues[1].replace(",", "").toLongOrNull()?.let { return it * 10_000L }
        }
        for ((k, v) in KOR_MANWON) if (body.contains(k + "원") || body.contains(k + " 원")) return v
        AMOUNT_WON.find(body)?.let { m ->
            m.groupValues[1].replace(",", "").toLongOrNull()?.let { if (it >= 1_000L) return it }
        }
        return null
    }
}
