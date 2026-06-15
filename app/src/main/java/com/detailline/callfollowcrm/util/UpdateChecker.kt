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
 *   GET https://si0in.kr/api/download/version 의 mtime_ms(서버 APK 파일 수정시각) 가
 *   이 APK 의 BuildConfig.BUILD_TIMESTAMP(빌드시각) 보다 [여유분] 이상 새로우면 = 새 버전 있음.
 *
 *   여유분(MARGIN): 빌드→서버 업로드까지 시차가 있어, 같은 APK 가 올라간 직후에도 서버 mtime 이
 *   내 BUILD_TIMESTAMP 보다 조금 큼 → '자기 자신'을 새 버전으로 오판 방지(10분).
 *   네트워크/파싱 실패는 조용히 false(배너 안 띄움).
 */
object UpdateChecker {
    private const val URL = "https://si0in.kr/api/download/version"
    private const val MARGIN_MS = 10 * 60 * 1000L

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    suspend fun isNewerAvailable(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder().url(URL).get().build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use false
                val body = resp.body?.string().orEmpty()
                val mtime = JSONObject(body).optLong("mtime_ms", 0L)
                mtime > BuildConfig.BUILD_TIMESTAMP + MARGIN_MS
            }
        }.getOrDefault(false)
    }
}
