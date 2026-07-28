package com.detailline.callfollowcrm.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Base64
import com.detailline.callfollowcrm.AppConfig
import com.detailline.callfollowcrm.BuildConfig
import com.detailline.callfollowcrm.data.preferences.AppPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 문제 신고 / 진단 보내기 (2026-07-22 사장님) — 앱이 죽지 않는 "이상 동작"(예: 자동문자 인코딩 깨짐)을
 * 사용자가 버튼 하나로 우리한테 보낼 수 있게. Crashlytics(크래시 자동수집)의 짝 = 수동 진단.
 *
 * 원칙:
 *   - 개인 고객정보(이름/번호/주소/대화)는 담지 않는다. 앱 버전·기기·"자동문자 설정값"만.
 *   - 자동문자는 코드포인트 덤프(U+XXXX)도 함께 넣어, 이메일/카톡에서 다시 mojibake 로 렌더돼도
 *     우리가 원문(어느 바이트가 깨졌는지)을 복원할 수 있게 한다. (이번 D-1 깨짐 같은 케이스 진단용)
 *   - 전송 = **버튼 한 번에 서버로 바로 전송(캐치)**. (2026-07-23 사장님: 공유 시트는 사용자가 어떻게
 *     보낼지 몰라 불편 → 직송으로 바꿈.) 서버 실패 시에만 공유 시트로 폴백.
 */
object DiagnosticsReporter {

    /** 서버 전송 실패 시 폴백용 이메일 수신처(사장님). */
    private const val TARGET_EMAIL = "hugman2080@gmail.com"

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

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

    /**
     * "막힌 자리에서" 자동 진단 직송 (2026-07-29 사장님) — 사용자가 더보기까지 안 가도,
     *   코딩한 흐름이 안 될 때(예: 녹음 켰는데 계속 0개) 그 화면에서 바로 보낸다.
     * @param tag  어디서 막혔나 (예: "온보딩-녹음연결", "가격표-자동생성").
     * @param extra 상황별 진단 텍스트(개인정보 배제). 예: [com.detailline.callfollowcrm.recording.AdotFolderScanner.recordingDiag].
     * @return 서버 전송 성공 여부. 실패 시 호출부가 [share] 폴백.
     */
    suspend fun sendAuto(context: Context, prefs: AppPreferences, tag: String, extra: String): Boolean =
        sendToServer(context, prefs, note = "[막힘 자동진단] $tag\n$extra", imageUri = null)

    /** 자동 진단 폴백(공유 시트)용 리포트 — 서버 직송 실패 시 [share] 에 넘길 본문. */
    fun autoReport(prefs: AppPreferences, tag: String, extra: String): String =
        buildReport(prefs, "[막힘 자동진단] $tag\n$extra")

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

    /**
     * 버튼 한 번에 진단 리포트를 **서버로 직접 전송**한다. 성공하면 true.
     * @param imageUri 스크린샷 등(선택) — 있으면 base64(dataURL)로 함께 전송(5MB 이하).
     * 서버: POST /api/diagnostics/report {phone, version, device, android, note, report, image?}
     */
    suspend fun sendToServer(context: Context, prefs: AppPreferences, note: String, imageUri: Uri?): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val body = JSONObject().apply {
                    put("phone", prefs.bizPhone.filter { it.isDigit() })
                    put("version", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                    put("device", "${Build.MANUFACTURER} ${Build.MODEL}")
                    put("android", "${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
                    put("note", note.trim())
                    put("report", buildReport(prefs, note))
                    if (imageUri != null) {
                        runCatching {
                            context.contentResolver.openInputStream(imageUri)?.use { ins ->
                                val bytes = ins.readBytes()
                                if (bytes.isNotEmpty() && bytes.size <= 5_000_000) {
                                    put("image", "data:image/*;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP))
                                }
                            }
                        }
                    }
                }
                val req = Request.Builder()
                    .url("${AppConfig.BASE_URL}/api/diagnostics/report")
                    .post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                    .build()
                client.newCall(req).execute().use { it.isSuccessful }
            }.getOrDefault(false)
        }

    /**
     * 공유 시트(이메일·카톡 등)로 진단 리포트 보내기. **서버 직송 실패 시 폴백**으로만 사용.
     * @param imageUri 스크린샷 등 첨부 이미지(선택). 있으면 이미지+본문, 없으면 본문만 텍스트.
     */
    fun share(context: Context, report: String, imageUri: Uri? = null) {
        val send = Intent(Intent.ACTION_SEND).apply {
            if (imageUri != null) {
                type = "image/*"
                putExtra(Intent.EXTRA_STREAM, imageUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } else {
                type = "text/plain"
            }
            putExtra(Intent.EXTRA_EMAIL, arrayOf(TARGET_EMAIL))
            putExtra(Intent.EXTRA_SUBJECT, "[시공막내] 문제 신고 / 진단")
            putExtra(Intent.EXTRA_TEXT, report)
        }
        context.startActivity(
            Intent.createChooser(send, "진단 보내기").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
