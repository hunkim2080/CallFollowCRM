package com.detailline.callfollowcrm.domain.inbox

import com.detailline.callfollowcrm.util.SpamPrefix
import com.detailline.callfollowcrm.util.isLikelyAd
import com.detailline.callfollowcrm.util.isProtectedNotice

/**
 * 🔕 「광고·스팸은 알림 안 울리기」 — **확실한 광고만** 알림을 끈다. (2026-10-16 사장님 A안)
 *   사장님: "광고나 스팸은 푸시알람조차 안 오게 하고 싶다. 근데 거기 인증문자가 껴있을까 봐 걱정."
 *
 *   **판정은 여기 한 곳**(§12). 켜는 스위치는 AppPreferences.adSilentPush(기본 OFF — 옵트인),
 *   부르는 자리는 수신 알림 세 곳: SmsReceiver(SMS) · CallFollowCrmApplication.notifyNewInboxMms(MMS 폴링) ·
 *   MmsDownloadedReceiver.runMmsHook(MMS 즉시). 문자 저장·문자함 분류(classifyLocal)·목록은 **안 건드린다** — 알림만.
 *
 *   셈(순서가 곧 안전선):
 *   ① 저장 고객이면 **절대 아님** — 손님 문자는 무조건 울린다.
 *   ② 인증번호(OTP)·입금·택배([isProtectedNotice])면 **절대 아님** — 앞자리(02 등)·낱말이 걸려도 안 끈다.
 *      (인증문자를 잘못 끄면 로그인·결제를 못 한다 — 이게 제일 중요하다)
 *   ③ 그다음에야 — 앞자리([SpamPrefix]) · 뻔한 광고 본문([isLikelyAd]) · 사장님이 적은 낱말([SpamKeywordMatch]) 중 하나.
 *   빈 본문은 ③ 의 본문 신호가 전부 false → 앞자리만 남는다. 애매하면 울린다(안 끄는 쪽이 안전, §13②).
 */
object DefiniteAd {

    const val REASON_PREFIX = "앞자리"
    const val REASON_BODY = "광고 본문"
    const val REASON_KEYWORD = "단어"

    /** 확실한 광고면 true → 알림을 안 띄운다. 애매하면 false(=울린다). */
    fun isDefiniteAd(
        body: String,
        address: String,
        isSavedCustomer: Boolean,
        spamPrefixes: Set<String>,
        spamKeywords: Set<String>
    ): Boolean = reason(body, address, isSavedCustomer, spamPrefixes, spamKeywords) != null

    /** 왜 확실한 광고로 봤나 — 로그용. null = 광고 아님(울린다). */
    fun reason(
        body: String,
        address: String,
        isSavedCustomer: Boolean,
        spamPrefixes: Set<String>,
        spamKeywords: Set<String>
    ): String? {
        if (isSavedCustomer) return null
        if (isProtectedNotice(body)) return null
        return when {
            SpamPrefix.isSpam(address, spamPrefixes) -> REASON_PREFIX
            isLikelyAd(body, address) -> REASON_BODY
            SpamKeywordMatch.matches(body, spamKeywords) -> REASON_KEYWORD
            else -> null
        }
    }
}
