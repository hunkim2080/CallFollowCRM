package com.detailline.callfollowcrm.util

import android.content.Context
import android.content.Intent
import android.os.Build
import com.detailline.callfollowcrm.BuildConfig
import com.detailline.callfollowcrm.data.preferences.AppPreferences

/**
 * 문제 신고 / 진단 보내기 (2026-07-22 사장님) — 앱이 죽지 않는 "이상 동작"(예: 자동문자 인코딩 깨짐)을
 * 사용자가 버튼 하나로 우리한테 보낼 수 있게. Crashlytics(크래시 자동수집)의 짝 = 수동 진단.
 *
 * 원칙:
 *   - 개인 고객정보(이름/번호/주소/대화)는 담지 않는다. 앱 버전·기기·"자동문자 설정값"만.
 *   - 자동문자는 코드포인트 덤프(U+XXXX)도 함께 넣어, 이메일/카톡에서 다시 mojibake 로 렌더돼도
 *     우리가 원문(어느 바이트가 깨졌는지)을 복원할 수 있게 한다. (이번 D-1 깨짐 같은 케이스 진단용)
 *   - 전송은 공유 시트(이메일·카톡 등) — 사용자가 눈으로 보고 직접 보냄(동의형).
 */
object DiagnosticsReporter {

    /** 진단 리포트 수신처(사장님). 이메일 앱을 고르면 자동 채워짐. */
    private const val TARGET_EMAIL = "hugman2080@gmail.com"

    fun buildReport(prefs: AppPreferences, userNote: String): String {
        val sb = StringBuilder()
        sb.append("[시공막내 진단]\n")
        sb.append("버전: ${BuildConfig.VERSION_NAME} (code ${BuildConfig.VERSION_CODE})\n")
        sb.append("기기: ${Build.MANUFACTURER} ${Build.MODEL}\n")
        sb.append("안드로이드: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})\n")

        sb.append("\n[사용자 메모]\n")
        sb.append(userNote.trim().ifBlank { "(없음)" }).append("\n")

        sb.append("\n[자동문자 설정값] — 깨짐 진단용 (개인정보 아님)\n")
        val items = listOf(
            "D-1 안내" to prefs.d1AutoText,
            "도착 안내" to prefs.arrivalAutoText,
            "부재중(신규)" to prefs.autoMissedNewText,
            "부재중(재통화)" to prefs.autoMissedReturnText
        )
        for ((label, value) in items) {
            sb.append("· $label: \"$value\"\n")
            sb.append("  코드: ${codepointDump(value)}\n")
        }
        return sb.toString()
    }

    /** 문자열을 U+XXXX 나열로 (앞 80 코드포인트). 이메일/카톡이 다시 깨뜨려도 원문 복원 가능. */
    private fun codepointDump(s: String): String {
        if (s.isEmpty()) return "(빈 값)"
        val sb = StringBuilder()
        var i = 0
        var count = 0
        while (i < s.length && count < 80) {
            val cp = s.codePointAt(i)
            sb.append("U+%04X ".format(cp))
            i += Character.charCount(cp)
            count++
        }
        if (i < s.length) sb.append("…")
        return sb.toString().trim()
    }

    /** 공유 시트(이메일·카톡 등)로 진단 리포트 보내기. */
    fun share(context: Context, report: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_EMAIL, arrayOf(TARGET_EMAIL))
            putExtra(Intent.EXTRA_SUBJECT, "[시공막내] 문제 신고 / 진단")
            putExtra(Intent.EXTRA_TEXT, report)
        }
        context.startActivity(
            Intent.createChooser(send, "진단 보내기").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
