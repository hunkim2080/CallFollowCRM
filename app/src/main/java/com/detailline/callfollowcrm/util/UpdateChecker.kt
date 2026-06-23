package com.detailline.callfollowcrm.util

import com.detailline.callfollowcrm.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 새 버전 감지 (2026-06-16 사장님).
 *   GET https://si0in.kr/api/download/version.
 *
 *   1순위(정확): 서버가 version_code(올라간 APK 의 versionCode)를 주면 → 내 BuildConfig.VERSION_CODE 와 직접 비교.
 *     빌드시각 비교의 고질적 오탐(빌드→업로드 시차로 '최신'에서도 배너 뜸)을 없앤다. (2026-06-22 사장님)
 *     ※ 서버 측: /api/download/version 응답에 version_code(int) 추가 필요 (cowork). 추가 전까진 아래 폴백.
 *   폴백: mtime_ms(서버 APK 파일시각) > 내 BUILD_TIMESTAMP + 여유분. 여유분은 빌드→업로드 시차 흡수용.
 *   네트워크/파싱 실패는 조용히 false(배너 안 띄움).
 */
object UpdateChecker {
    private const val URL = "https://si0in.kr/api/download/version"
    // 폴백(version_code 없을 때) 여유분 — 빌드→서버 업로드까지 시차(검증·전송 포함)를 넉넉히 흡수. (10분→2시간, 2026-06-22)
    private const val MARGIN_MS = 2 * 60 * 60 * 1000L

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    /** version_code 가 있으면 반환 (없으면 0). */
    suspend fun checkUpdate(): UpdateInfo = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder().url(URL).get().build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use UpdateInfo(false, 0)
                val json = JSONObject(resp.body?.string().orEmpty())
                val serverCode = json.optInt("version_code", 0)
                if (serverCode > 0) {
                    return@use UpdateInfo(serverCode > BuildConfig.VERSION_CODE, serverCode)
                }
                val mtime = json.optLong("mtime_ms", 0L)
                UpdateInfo(mtime > BuildConfig.BUILD_TIMESTAMP + MARGIN_MS, 0)
            }
        }.getOrDefault(UpdateInfo(false, 0))
    }

    /** 하위 호환 — Boolean 만 필요한 곳 (내부 폴백용). */
    suspend fun isNewerAvailable(): Boolean = checkUpdate().available

    data class UpdateInfo(val available: Boolean, val latestCode: Int)
}
