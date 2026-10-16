package com.detailline.callfollowcrm.domain.inbox

import com.detailline.callfollowcrm.data.local.entity.ThreadBucketEntity
import com.detailline.callfollowcrm.domain.inbox.ServerClassifyGate.Decision
import com.detailline.callfollowcrm.domain.inbox.ServerClassifyGate.Msg
import org.junit.Assert.assertEquals
import org.junit.Test

/** 「서버에 물어볼까」 — 돈 드는 문이라 좁게 열리는지. */
class ServerClassifyGateTest {

    private val now = 1_000_000L
    private val cust = listOf(Msg(fromOwner = false, body = "안녕하세요 혹시 줄눈 시공 하시나요", ts = now))

    private fun decide(
        local: InboxClassifier.Verdict = InboxClassifier.Verdict.UNSURE,
        existing: ThreadBucketEntity? = null,
        saved: Boolean = false,
        replied: Boolean = false,
        nonCustomer: Boolean = false,
        messages: List<Msg> = cust,
        lastAskedAt: Long? = null,
        filterOn: Boolean = true
    ) = ServerClassifyGate.decide(local, existing, saved, replied, nonCustomer, messages, lastAskedAt, now, filterOn)

    @Test fun `애매하고 행 없고 신호 없으면 묻는다`() {
        assertEquals(Decision.ASK, decide())
    }

    @Test fun `AI 자동 거르기를 끄면 묻을 만한 문자여도 안 묻는다 — 0원`() {
        assertEquals(Decision.SKIP_FILTER_OFF, decide(filterOn = false))
    }

    @Test fun `끔이 다른 모든 이유보다 앞선다 — 로컬 확정이어도 FILTER_OFF 로 돌려보낸다`() {
        assertEquals(Decision.SKIP_FILTER_OFF, decide(local = InboxClassifier.Verdict.CONSULT, filterOn = false))
    }

    @Test fun `로컬이 CONSULT 또는 GENERAL 로 확정이면 안 묻는다 — 0원`() {
        assertEquals(Decision.SKIP_LOCAL_DECIDED, decide(local = InboxClassifier.Verdict.CONSULT))
        assertEquals(Decision.SKIP_LOCAL_DECIDED, decide(local = InboxClassifier.Verdict.GENERAL))
    }

    @Test fun `이미 바구니 행이 있으면 다시 안 묻는다 — 서버가 답했거나 사장님 고정`() {
        val haiku = ThreadBucketEntity("12345678", BucketPolicy.GENERAL, BucketPolicy.SRC_HAIKU, "택배", 1L, 1)
        val owner = ThreadBucketEntity("12345678", BucketPolicy.CONSULT, BucketPolicy.SRC_OWNER, "사장님 이동", 1L, null)
        assertEquals(Decision.SKIP_ALREADY_BUCKETED, decide(existing = haiku))
        assertEquals(Decision.SKIP_ALREADY_BUCKETED, decide(existing = owner))
    }

    @Test fun `저장 고객 · 답장 이력 · 고객 아님 찍음 은 답이 정해져 있다`() {
        assertEquals(Decision.SKIP_KNOWN_ANSWER, decide(saved = true))
        assertEquals(Decision.SKIP_KNOWN_ANSWER, decide(replied = true))
        assertEquals(Decision.SKIP_KNOWN_ANSWER, decide(nonCustomer = true))
    }

    @Test fun `호출부가 답장 이력을 몰라도 메시지에 사장님 줄이 있으면 안 묻는다`() {
        val withReply = cust + Msg(fromOwner = true, body = "네 가능합니다", ts = now + 1)
        assertEquals(Decision.SKIP_KNOWN_ANSWER, decide(messages = withReply))
    }

    @Test fun `본문이 비면 안 묻는다`() {
        assertEquals(Decision.SKIP_NO_BODY, decide(messages = emptyList()))
        assertEquals(Decision.SKIP_NO_BODY, decide(messages = listOf(Msg(false, "   ", now))))
    }

    @Test fun `같은 번호 연타는 쿨다운`() {
        assertEquals(Decision.SKIP_COOLDOWN, decide(lastAskedAt = now - 1_000L))
        assertEquals(Decision.ASK, decide(lastAskedAt = now - ServerClassifyGate.COOLDOWN_MS))
    }

    // ── payloadMessages ──

    @Test fun `방금 받은 문자가 provider 에 아직 없으면 붙인다`() {
        val current = Msg(false, "줄눈 견적 얼마예요", now)
        val out = ServerClassifyGate.payloadMessages(recent = emptyList(), current = current)
        assertEquals(listOf(current), out)
    }

    @Test fun `이미 들어가 있으면 두 번 안 넣는다`() {
        val current = Msg(false, "줄눈 견적 얼마예요", now)
        val recent = listOf(Msg(false, "줄눈 견적 얼마예요", now - 5))
        assertEquals(1, ServerClassifyGate.payloadMessages(recent, current).size)
    }

    @Test fun `시간순 정렬하고 마지막 5개만`() {
        val recent = (1..8).map { Msg(false, "m$it", now - (9 - it) * 1000L) }.shuffled()
        val current = Msg(false, "m9", now)
        val out = ServerClassifyGate.payloadMessages(recent, current)
        assertEquals(listOf("m5", "m6", "m7", "m8", "m9"), out.map { it.body })
    }

    @Test fun `빈 본문 줄은 뺀다`() {
        val recent = listOf(Msg(false, "", now - 10), Msg(true, " ", now - 9))
        val current = Msg(false, "문의요", now)
        assertEquals(listOf("문의요"), ServerClassifyGate.payloadMessages(recent, current).map { it.body })
    }
}
