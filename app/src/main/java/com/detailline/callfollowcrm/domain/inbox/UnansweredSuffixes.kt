package com.detailline.callfollowcrm.domain.inbox

import com.detailline.callfollowcrm.data.local.entity.CallRecordEntity
import com.detailline.callfollowcrm.data.repository.SmsRepository
import com.detailline.callfollowcrm.util.PhoneKey
import com.detailline.callfollowcrm.util.SpamPrefix

/**
 * 📥 「답장 안 함」 번호 집합 — **셈은 여기 한 곳.** (2026-11-04 HomeViewModel 에서 추출)
 *
 *   전엔 `HomeViewModel.unconfirmedSuffixes` **안에만** 있어서 화면이 떠 있을 때만 돌았다.
 *   위젯·백그라운드가 같은 「답장 안 함」을 쓰려면 화면 밖에서도 돌아야 한다 → **순수 함수로 떼어**
 *   화면과 위젯이 **같은 셈**을 부른다(두 벌 금지 §12). 유일한 비순수 의존이던 `spamPrefixesFlow.value` 는
 *   인자(`spamPrefixes`)로 받는다 — 나머지 규칙은 그대로다. 동작 고정은 `UnansweredSuffixesTest`.
 *
 *   규칙(사장님 의도 그대로):
 *   · 문자: 마지막이 **고객 수신**(`!lastSent`)이고 7일 창 안이면 미답. 단 그 뒤 전화로 응대
 *     (`handledCallMs[suffix] >= lastDateMs`)했으면 제외. 이전에 답장한 적 있어도 **마지막이 수신이면** 미답.
 *   · 부재중: 7일 창 안 + 스팸/일정/전화응대 아님 + (그 번호 문자에 **사장님 답장 기록이 없을 때**) 미답.
 *   · 스팸 suffix(`spam`)·스팸 앞자리(`spamPrefixes`, 070 등)는 양쪽에서 뺀다.
 */
object UnansweredSuffixes {

    fun of(
        smsContacts: List<SmsRepository.SmsContact>,
        missed: List<CallRecordEntity>,
        spam: Set<String> = emptySet(),
        scheduled: Set<String> = emptySet(),
        handledCallMs: Map<String, Long> = emptyMap(),
        sevenStart: Long,
        spamPrefixes: Set<String> = emptySet()
    ): Set<String> {
        val bySuffix = smsContacts.associateBy { it.normalizedSuffix }
        val result = HashSet<String>()
        for (c in smsContacts) {
            if (c.normalizedSuffix in spam) continue
            if (SpamPrefix.isSpam(c.address, spamPrefixes)) continue   // 스팸 앞자리
            // 마지막 메시지가 고객 수신이면 미확인. 이전 답장 여부는 무관(사장님 의도).
            if (!c.lastSent && c.lastDateMs >= sevenStart) {
                // 그 문자 이후 전화로 응대(응답/콜백)했으면 처리된 것 → 제외.
                if ((handledCallMs[c.normalizedSuffix] ?: 0L) >= c.lastDateMs) continue
                result += c.normalizedSuffix
            }
        }
        for (m in missed) {
            if (m.endedAt < sevenStart) continue
            val suffix = PhoneKey.of(m.phoneNumber)
            if (suffix in spam) continue
            if (SpamPrefix.isSpam(m.phoneNumber, spamPrefixes)) continue   // 스팸 앞자리
            if (suffix in scheduled) continue
            // 부재중 이후 전화로 응대했으면 처리됨 → 제외.
            if ((handledCallMs[suffix] ?: 0L) >= m.endedAt) continue
            val sms = bySuffix[suffix]
            if (sms == null || !sms.hasOwnerReply) result += suffix
        }
        return result
    }
}
