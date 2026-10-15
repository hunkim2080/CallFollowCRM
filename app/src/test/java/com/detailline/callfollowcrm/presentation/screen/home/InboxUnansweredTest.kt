package com.detailline.callfollowcrm.presentation.screen.home

import com.detailline.callfollowcrm.data.local.entity.CallRecordEntity
import com.detailline.callfollowcrm.data.local.entity.CustomerEntity
import com.detailline.callfollowcrm.domain.model.HandledStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 📥 「답장 안 함」 — **칩 숫자 = 칩을 열었을 때 보이는 줄 수.** (2026-10-15 사장님 "왜 1이 떠 있을까?")
 *
 *   숫자와 목록이 각자 세면 한쪽만 고쳐져 「답장 안 함 1 인데 열면 빈 목록」이 난다(2026-10-14 실제).
 *   그래서 세는 곳은 [inboxUnanswered] 하나고, 이 테스트가 그 한 곳의 규칙을 지킨다.
 *   목록 쪽이 쓰는 광고 판정도 [isInboxAd] 같은 함수다.
 */
class InboxUnansweredTest {

    private val now = 1_760_500_000_000L

    private fun item(
        phone: String,
        unconfirmed: Boolean = true,
        customer: CustomerEntity? = null,
        lastSent: Boolean? = false,
        lastBody: String? = "줄눈 시공 문의드려요"
    ) = HomeItem(
        record = CallRecordEntity(
            id = phone.hashCode().toLong(),
            phoneNumber = phone,
            callType = HomeViewModel.CALL_TYPE_SMS_ONLY,
            duration = 0L,
            startedAt = null,
            endedAt = now,
            handledStatus = HandledStatus.SAVED.name,
            linkedCustomerId = customer?.id
        ),
        customer = customer,
        callCount = 0,
        isUnconfirmed = unconfirmed,
        lastSent = lastSent,
        lastBody = lastBody
    )

    private fun saved(phone: String, asPendingAt: Long? = null) =
        CustomerEntity(id = 7L, phoneNumber = phone, name = "손님", asPendingAt = asPendingAt, createdAt = now, updatedAt = now)

    @Test
    fun `답 안 한 저장 고객은 들어간다 — 숫자 1 = 줄 1`() {
        val all = listOf(item("01011112222", customer = saved("01011112222")))
        val out = inboxUnanswered(all, emptySet())
        assertEquals(1, out.size)
        assertEquals("01011112222", out.single().record.phoneNumber)
    }

    @Test
    fun `답 안 한 낯선 번호도 들어간다`() {
        assertEquals(1, inboxUnanswered(listOf(item("01033334444")), emptySet()).size)
    }

    @Test
    fun `답했으면(isUnconfirmed=false) 안 들어간다`() {
        val all = listOf(item("01011112222", unconfirmed = false, customer = saved("01011112222"), lastSent = true))
        assertTrue(inboxUnanswered(all, emptySet()).isEmpty())
    }

    @Test
    fun `A S 잡힌 손님도 답 안 했으면 들어간다 — 다른 일 칩과 달리 A S 를 빼지 않는다`() {
        val all = listOf(item("01011112222", customer = saved("01011112222", asPendingAt = now - 1000L)))
        assertEquals(1, inboxUnanswered(all, emptySet()).size)
    }

    @Test
    fun `답 안 한 광고는 숫자에서도 목록에서도 빠진다 — 10-14 사고`() {
        val ad = item("01055556666", lastBody = "[광고] 이번 주 특가 세일")
        assertTrue(isInboxAd(ad, emptySet()))
        assertTrue(inboxUnanswered(listOf(ad), emptySet()).isEmpty())
    }

    @Test
    fun `광고 아냐 예외로 둔 번호는 다시 들어간다`() {
        val ad = item("01055556666", lastBody = "[광고] 이번 주 특가 세일")
        val allow = setOf("55556666")
        assertFalse(isInboxAd(ad, allow))
        assertEquals(1, inboxUnanswered(listOf(ad), allow).size)
    }

    @Test
    fun `저장 고객이면 내용이 광고 같아도 광고로 안 본다`() {
        val c = item("01011112222", customer = saved("01011112222"), lastBody = "[광고] 이번 주 특가 세일")
        assertFalse(isInboxAd(c, emptySet()))
        assertEquals(1, inboxUnanswered(listOf(c), emptySet()).size)
    }

    @Test
    fun `내가 마지막에 보냈으면 광고로 안 본다`() {
        val c = item("01055556666", lastSent = true, lastBody = "[광고] 이번 주 특가 세일")
        assertFalse(isInboxAd(c, emptySet()))
    }

    @Test
    fun `섞인 목록 — 숫자는 보일 줄과 정확히 같다`() {
        val all = listOf(
            item("01011112222", customer = saved("01011112222")),            // 들어감
            item("01033334444", unconfirmed = false),                         // 답함
            item("01055556666", lastBody = "[광고] 이번 주 특가 세일"),       // 광고
            item("01077778888")                                               // 들어감
        )
        val out = inboxUnanswered(all, emptySet())
        assertEquals(2, out.size)
        assertEquals(listOf("01011112222", "01077778888"), out.map { it.record.phoneNumber })
    }
}
