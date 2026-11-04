package com.detailline.callfollowcrm.domain.inbox

import com.detailline.callfollowcrm.data.local.entity.CallRecordEntity
import com.detailline.callfollowcrm.data.repository.SmsRepository
import com.detailline.callfollowcrm.util.PhoneKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「답장 안 함」 집합 동작 고정 — HomeViewModel 에서 공용으로 뺄 때 동작이 그대로인지 못 박는다. (2026-11-04)
 * 규칙이 바뀌면 여기부터 바뀌어야 한다(화면·위젯이 같은 셈을 쓰므로).
 */
class UnansweredSuffixesTest {

    private val sevenStart = 1_000L

    private fun sms(
        suffix: String,
        lastDateMs: Long,
        lastSent: Boolean,
        hasOwnerReply: Boolean = false,
        address: String = "0101234$suffix"
    ) = SmsRepository.SmsContact(
        address = address,
        normalizedSuffix = suffix,
        lastBody = "문의드려요",
        lastDateMs = lastDateMs,
        lastSent = lastSent,
        hasOwnerReply = hasOwnerReply,
        firstDateMsInScan = lastDateMs
    )

    private fun missed(phone: String, endedAt: Long) = CallRecordEntity(
        phoneNumber = phone,
        callType = "MISSED",
        duration = 0L,
        endedAt = endedAt,
        handledStatus = ""
    )

    @Test
    fun inbound_last_within_window_not_replied_is_unanswered() {
        val out = UnansweredSuffixes.of(
            smsContacts = listOf(sms("11112222", lastDateMs = 2_000L, lastSent = false)),
            missed = emptyList(),
            sevenStart = sevenStart
        )
        assertEquals(setOf("11112222"), out)
    }

    @Test
    fun last_sent_by_owner_is_not_unanswered() {
        val out = UnansweredSuffixes.of(
            smsContacts = listOf(sms("11112222", lastDateMs = 2_000L, lastSent = true)),
            missed = emptyList(),
            sevenStart = sevenStart
        )
        assertTrue(out.isEmpty())
    }

    @Test
    fun older_than_window_is_excluded() {
        val out = UnansweredSuffixes.of(
            smsContacts = listOf(sms("11112222", lastDateMs = 500L, lastSent = false)),
            missed = emptyList(),
            sevenStart = sevenStart
        )
        assertTrue(out.isEmpty())
    }

    @Test
    fun handled_by_call_after_message_is_excluded() {
        val out = UnansweredSuffixes.of(
            smsContacts = listOf(sms("11112222", lastDateMs = 2_000L, lastSent = false)),
            missed = emptyList(),
            handledCallMs = mapOf("11112222" to 2_500L), // 문자 이후 전화 응대
            sevenStart = sevenStart
        )
        assertTrue(out.isEmpty())
    }

    @Test
    fun spam_suffix_is_excluded() {
        val out = UnansweredSuffixes.of(
            smsContacts = listOf(sms("11112222", lastDateMs = 2_000L, lastSent = false)),
            missed = emptyList(),
            spam = setOf("11112222"),
            sevenStart = sevenStart
        )
        assertTrue(out.isEmpty())
    }

    @Test
    fun missed_call_within_window_without_sms_reply_is_unanswered() {
        val phone = "010-5555-6666"
        val suffix = PhoneKey.of(phone)
        val out = UnansweredSuffixes.of(
            smsContacts = emptyList(),
            missed = listOf(missed(phone, endedAt = 3_000L)),
            sevenStart = sevenStart
        )
        assertTrue(suffix in out)
    }

    @Test
    fun missed_call_excluded_when_scheduled_or_replied_or_handled() {
        val phone = "010-5555-6666"
        val suffix = PhoneKey.of(phone)

        // 일정 잡힌 번호 → 제외
        assertFalse(
            suffix in UnansweredSuffixes.of(
                smsContacts = emptyList(),
                missed = listOf(missed(phone, endedAt = 3_000L)),
                scheduled = setOf(suffix),
                sevenStart = sevenStart
            )
        )
        // 그 번호 문자에 사장님 답장 기록 있음 → 제외
        assertFalse(
            suffix in UnansweredSuffixes.of(
                smsContacts = listOf(sms(suffix, lastDateMs = 2_900L, lastSent = true, hasOwnerReply = true)),
                missed = listOf(missed(phone, endedAt = 3_000L)),
                sevenStart = sevenStart
            )
        )
        // 전화로 응대함 → 제외
        assertFalse(
            suffix in UnansweredSuffixes.of(
                smsContacts = emptyList(),
                missed = listOf(missed(phone, endedAt = 3_000L)),
                handledCallMs = mapOf(suffix to 3_500L),
                sevenStart = sevenStart
            )
        )
    }
}
