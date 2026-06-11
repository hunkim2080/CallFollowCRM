package com.detailline.callfollowcrm.util

import android.content.Context
import android.telephony.TelephonyManager

/**
 * 내 폰 번호 자동 채움 보조. (2026-06-12 사장님 요청 — 사업자 정보 번호 직접 입력 줄이기)
 *   ⚠️ 한국 통신사는 번호를 유심에 안 적어두는 경우가 많아 빈 값이 자주 옴 → 실패해도 안전(빈 문자열).
 *   읽히면 자동 채움, 안 읽히면 화면의 "내 번호 불러오기"(구글 번호 힌트 한 번 탭)로 보강.
 */
object DevicePhoneNumber {

    /** 유심에서 내 번호 읽기 시도(READ_PHONE_STATE 필요). 실패/빈값 = "". */
    fun readSimNumber(context: Context): String = runCatching {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        @Suppress("DEPRECATION", "MissingPermission")
        normalizeKorean(tm?.line1Number)
    }.getOrDefault("")

    /** +8210..., 82-10-..., 010... → 01012345678 (숫자만, 국가코드 82 → 0). 한국 로컬번호는 항상 0 으로 시작. */
    fun normalizeKorean(raw: String?): String {
        val d = raw?.filter { it.isDigit() } ?: return ""
        return if (d.startsWith("82")) "0" + d.removePrefix("82") else d
    }
}
