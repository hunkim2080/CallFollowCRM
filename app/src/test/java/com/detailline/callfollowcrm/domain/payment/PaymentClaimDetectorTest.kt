package com.detailline.callfollowcrm.domain.payment

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * "돈 보냈다"는 문자 알아보기. (2026-09-17 사장님 "그 텍스트만 감지하나?")
 *
 * 아래 문장들은 **사장님이 실제로 받은 문자**에서 가져왔다(사람 이름·주소는 바꿈).
 * 지어낸 예문으로 맞춰보고 "잘 된다"고 보고했던 전례가 있어서, 진짜 문장으로 고정한다.
 *
 * ⚠️ 못 잡는 건 손해가 없다(지금처럼 직접 누르면 된다).
 *    **잘못 잡으면 안 받은 돈이 '받음'이 되고 미수금이 사라진다.** 아래 '아닌 것' 테스트가 그 그물이다.
 */
class PaymentClaimDetectorTest {

    private fun hit(s: String?) = PaymentClaimDetector.detect(s)

    // ── 잡아야 하는 것 (실제 문장) ──────────────────────────────

    @Test
    fun `입금했다는 말들`() {
        listOf(
            "입금했습니다",
            "입금 했습니다",
            "입금 드렸습니다",
            "입금 완료입니다-!",
            "입금했습니다.",
            "입금 완료입니다!",
            "9/4 동아1  로 방금 입금 했습니다.",
            "예금주 홍길동 입금했어요",
            "시공비 입금하였는데 확인하셨나요.",
            "10만원 입금 완료하였습니다."
        ).forEach { assertNotNull("놓침: $it", hit(it)) }
    }

    @Test
    fun `송금 이체도 잡는다`() {
        listOf(
            "십만원 송금 했습니다.",
            "송금했습니다.",
            "송금 했습니다 수고하셨습니다 번창하세요",
            "잔금 이체 드렸습니다",
            "이체 드렸습니다",
            "이체했습니다"
        ).forEach { assertNotNull("놓침: $it", hit(it)) }
    }

    @Test
    fun `보냈다는 돈 단어가 같이 있으면 잡는다`() {
        assertNotNull(hit("예약금 보냈습니다"))
        assertNotNull(hit("계약금 10만원 보냈습니다"))
    }

    @Test
    fun `은행이 보낸 알림도 잡는다`() {
        val c = hit("[하나은행] 홍길동님이 김상훈님의 카카오뱅크계좌로 100,000원을 이체하였습니다.")
        assertNotNull(c)
        assertEquals(100_000L, c!!.amountWon)
    }

    // ── 절대 잡으면 안 되는 것 (실제 문장) ──────────────────────

    @Test
    fun `아직 안 보낸 건 안 잡는다`() {
        // 이걸 '받음'으로 처리하면 안 받은 돈이 받은 돈이 된다
        listOf(
            "네 제가 내일 입금할게요",
            "900  먼저 입금할게요",
            "10만원 입금이 혹시 언제쯤 가능할까요??",
            "부가세 끊으려면 10프로입금해야죠 그럼 괜찮습니다",
            "입금 계좌 알려주세요",
            "잔금은 시공 끝나고 입금하면 될까요?"
        ).forEach { assertNull("잘못 잡음: $it", hit(it)) }
    }

    @Test
    fun `돈 얘기가 아닌 보냈다 넣었다는 안 잡는다`() {
        listOf(
            "사진 같이 보내드렸는데 안 갔나요?",
            "제가 이전에 복사했던걸 그대로 넣었네요 ㅜㅜ",
            "주소 보냈습니다",
            "접수서 보냈어요"
        ).forEach { assertNull("잘못 잡음: $it", hit(it)) }
    }

    @Test
    fun `빈 값도 안전하다`() {
        assertNull(hit(null))
        assertNull(hit(""))
        assertNull(hit("   "))
    }

    // ── 금액 읽기 ──────────────────────────────────────────────

    @Test
    fun `문장에서 금액을 읽는다`() {
        assertEquals(1_000_000L, hit("100만원 입금해드렸습니다. 확인부탁드립니다~!")!!.amountWon)
        assertEquals(100_000L, hit("계약금 10만원 입금하였습니다.")!!.amountWon)
        assertEquals(880_000L, hit("88만원 입금 했습니다")!!.amountWon)
        assertEquals(100_000L, hit("십만원 송금 했습니다.")!!.amountWon)
    }

    @Test
    fun `금액이 없어도 감지는 된다`() {
        val c = hit("입금했습니다")
        assertNotNull(c)
        assertNull("금액은 비워두고 물어본다", c!!.amountWon)
    }

    @Test
    fun `날짜나 주소의 숫자를 금액으로 읽지 않는다`() {
        // "9/4 동아1 로 방금 입금 했습니다" — 9, 4, 1 은 금액이 아니다
        assertNull(hit("9/4 동아1  로 방금 입금 했습니다.")!!.amountWon)
    }
    // ── 실측에서 **실제로 잘못 잡았던** 문장들 (2026-09-17) ─────────
    //   사장님 문자 1,085통에 처음 규칙을 돌렸을 때 5통을 잘못 잡았다. 전부 "보내 드리겠습니다" 계열.
    //   그래서 소프트 동사를 **이미 보낸 형태만**("보냈", "보내 드렸") 으로 좁혔다.

    @Test
    fun `보내 드리겠습니다는 아직 안 보낸 것이다`() {
        listOf(
            "잔금 90 보내 드리겠습니다",
            "안녕하세요 어제 퇴근이 늦어 현장을 밤 늦게 가게 되어 지금 잔금 보내드리겠습니당! 감사합니다!",
            "넵 저녁에 확인 하고 바로 잔금 보내 드리겠습니다!"
        ).forEach { assertNull("잘못 잡음: $it", hit(it)) }
    }

    @Test
    fun `보내달라는 요청은 돈 받은 게 아니다`() {
        listOf(
            "예약금 반환 어려운 부분에 대한거 계약조항 한번 보내주세요^^",
            "안녕하세요? 바쁘시겠지만 110만원 계산서 발행하려는데 링크좀 다시 보내주실 수 있으세요?"
        ).forEach { assertNull("잘못 잡음: $it", hit(it)) }
    }

    @Test
    fun `확인 부탁드립니다가 붙어도 놓치지 않는다`() {
        // '부탁'을 미래형으로 보고 거르면 진짜 입금을 놓친다 — 실측에서 2통 놓쳤다
        assertNotNull(hit("100만원 입금해드렸습니다. 확인부탁드립니다~!"))
        assertNotNull(hit("결재 했다고 하네요... 디자인 누 플러스로 입금됐을겁니다  확인 부탁드립니다"))
    }
}
