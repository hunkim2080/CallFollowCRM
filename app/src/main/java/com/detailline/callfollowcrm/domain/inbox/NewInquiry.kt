package com.detailline.callfollowcrm.domain.inbox

/**
 * 💬 수신 문자·사진이 **「신규 문의」**인가 — 알림 소리(신규문의음 vs 일반 문자음)와 제목(「새 문의」 vs 「새 문자」)을 가른다.
 *
 * 세 수신 경로가 **같은 판정**을 쓰게 한 한 곳(§12). 전엔 셋이 제각각이라 「대화 중인 사람인데 사진 보내니 신규로 뜸」이 났다:
 *   - SmsReceiver(SMS)            : `customer == null && !seenBefore`  ✅
 *   - MmsDownloadedReceiver(사진 훅): isNewCustomer 안 넘김 → 늘 false  ❌ (진짜 신규 사진도 신규음 안 울림)
 *   - CallFollowCrmApplication(폴링): `customer == null`               ❌ (대화 중이어도 미저장이면 매번 신규음)
 *   → 2026-08-02 에 SMS 만 고치고 MMS 두 경로는 옛 판정으로 남았다. (2026-11-06 사장님 "대화 중인데 사진 보낼 때 신규로 뜸")
 *
 * ⚠️ **순서에 안전해야 한다.** MMS 폴링은 알림 **전에** `syncMmsContacts()` 가 돌아 방금 온 사진이 이미 캐시에 들어간다.
 *   그래서 「캐시에 있나(findBySuffix != null)」만 보면 진짜 신규 사진도 늘 「있음」이 되어 신규음이 안 울린다.
 *   대신 **가장 오래된 메시지 시각**(`firstDateMsInScan`, upsert 때 min 으로 보존)을 본다 — 이게 이 메시지보다 전이면
 *   「전에도 왕래」 = 신규 아님. 이 메시지가 가장 오래된 것(또는 캐시에 아예 없음)이면 = 이게 최초 = 신규.
 *   → 이 메시지가 캐시에 이미 upsert 됐든 안 됐든 같은 답이 나온다.
 */
object NewInquiry {

    /**
     * @param customerExists 저장된 고객인가 — 저장 고객이면 신규 문의가 아니다(일반 문자음).
     * @param cachedFirstMs  이 번호 캐시에 남은 **가장 오래된** 메시지 시각. 캐시에 없으면 null.
     * @param thisMs         방금 받은 이 메시지 시각.
     */
    fun isNew(customerExists: Boolean, cachedFirstMs: Long?, thisMs: Long): Boolean {
        if (customerExists) return false
        if (cachedFirstMs == null) return true   // 캐시에 처음 보는 번호 = 신규
        return cachedFirstMs >= thisMs           // 가장 오래된 게 이 메시지 = 이게 최초 = 신규 (전 메시지가 있으면 false)
    }
}
