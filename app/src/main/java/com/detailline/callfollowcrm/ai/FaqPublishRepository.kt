package com.detailline.callfollowcrm.ai

import android.content.Context
import android.net.Uri
import com.detailline.callfollowcrm.AppConfig
import com.detailline.callfollowcrm.data.preferences.AppPreferences
import com.detailline.callfollowcrm.data.repository.RepQuestionRepository
import com.detailline.callfollowcrm.util.ImageEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 💬 **대표질문 = 손님 공개 FAQ 페이지 — 앱→서버 publish.** (2026-11-21 Phase 2 · `docs/DESIGN_faq_public_page.md`)
 *
 *   공개 토글·질문 변경·업체 소개 저장 때 호출. 본문 계약은 서버 `FaqPublishRequest` 와 **1:1**(두 벌 금지 — 문서가 출처).
 *   사진(대표사진·답 사진)은 `ImageEncoder` 로 base64 data URL — 현장/대표질문 사진과 같은 방식. 서버가 발급한
 *   **고정 slug/url** 을 돌려주면 `prefs.faqSlug` 에 캐시한다(한 번 받으면 안 바뀐다).
 *   네트워크는 `Net.builder()`(로그인 토큰 자동) — `OkHttpClient` 직접 만들지 않는다(§12).
 */
class FaqPublishRepository(
    private val appContext: Context,
    private val prefs: AppPreferences,
    private val repQuestionRepository: RepQuestionRepository,
    private val baseUrl: String = AppConfig.BASE_URL
) {
    private val client: OkHttpClient = Net.builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    /** 공개 페이지를 올린다(또는 숨긴다). 성공 시 공개 URL 반환. 실패는 삼키지 않고 Result.failure 로 올린다(화면이 말한다). */
    suspend fun publish(published: Boolean): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val questions = repQuestionRepository.observeAll().first()
            val cats = LinkedHashSet<String>()
            questions.forEach { if (it.category.isNotBlank()) cats += it.category }
            val payload = JSONObject().apply {
                put("published", published)
                put("biz_name", prefs.bizName)
                put("specialty", "")
                put("intro", prefs.faqIntro)
                put("region", prefs.ownerRegions.joinToString(" · "))
                put("career", prefs.faqCareer)
                put("count", prefs.faqCount)
                put("owner_sms", prefs.displayPhone.filter { it.isDigit() })
                put("slogan", prefs.faqSlogan)
                // 사진·설명을 **같이** 담아 index 정렬 유지(인코딩 실패한 사진은 설명도 함께 건너뜀).
                val coverArr = JSONArray()
                val capArr = JSONArray()
                val caps = prefs.faqCoverCaptions
                prefs.faqCoverPhotos.forEachIndexed { i, uri ->
                    val enc = encode(uri) ?: return@forEachIndexed
                    coverArr.put(enc)
                    capArr.put(caps.getOrElse(i) { "" })
                }
                put("cover_photos", coverArr)
                put("cover_captions", capArr)
                put("categories", JSONArray(cats.toList()))
                put("questions", JSONArray().apply {
                    questions.forEach { q ->
                        put(JSONObject().apply {
                            put("category", q.category)
                            put("q", q.question)
                            put("a", q.answer)
                            put("photo", q.imageUri?.let { encode(it) } ?: JSONObject.NULL)
                        })
                    }
                })
            }
            val req = Request.Builder()
                .url("$baseUrl/faq/publish")
                .post(payload.toString().toRequestBody(JSON_MEDIA))
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                val obj = JSONObject(resp.body?.string().orEmpty())
                val slug = obj.optString("slug")
                val url = obj.optString("url")
                if (slug.isNotBlank()) prefs.faqSlug = slug
                prefs.faqPublished = published
                url.ifBlank { if (slug.isNotBlank()) "$baseUrl/faq/$slug" else "" }
            }
        }
    }

    /** 대표질문 답변을 **전문 상담 톤**으로 다듬기(서버 `/faq/refine` — 기존 Gemini 다듬기 재사용·FAQ 전용 프롬프트). */
    suspend fun refineAnswer(text: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = JSONObject().apply { put("raw", text) }
            val req = Request.Builder()
                .url("$baseUrl/faq/refine")
                .post(payload.toString().toRequestBody(JSON_MEDIA))
                .build()
            client.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (resp.code == 429) throw RefineQuotaException(body.contains("quota_day") || body.contains("한도"))
                if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                JSONObject(body).optString("polished").trim().ifEmpty { throw IOException("빈 응답") }
            }
        }
    }

    /**
     * ✨ **AI로 대표질문 초안 뽑기** — 앱이 모은 문자 코퍼스(손님 질문 + 사장님 답)를 서버에 보내
     *   Gemini 가 {분류·질문·답·확인필요} 초안을 돌려준다(서버 `/faq/suggest`, 코퍼스는 서버에 저장 안 함).
     *   429=한도(RefineQuotaException). 실패는 삼키지 않고 Result.failure.
     */
    suspend fun suggest(corpus: String): Result<FaqSuggestResult> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = JSONObject().apply { put("corpus", corpus) }
            val req = Request.Builder()
                .url("$baseUrl/faq/suggest")
                .post(payload.toString().toRequestBody(JSON_MEDIA))
                .build()
            client.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (resp.code == 429) throw RefineQuotaException(true)
                if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                val obj = JSONObject(body)
                val arr = obj.optJSONArray("drafts") ?: JSONArray()
                val drafts = (0 until arr.length()).map { i ->
                    val d = arr.getJSONObject(i)
                    FaqSuggestDraft(
                        category = d.optString("category").trim(),
                        question = d.optString("q").trim(),
                        answer = d.optString("a").trim(),
                        needsReview = d.optBoolean("needs_review")
                    )
                }.filter { it.question.isNotBlank() }
                FaqSuggestResult(enough = obj.optBoolean("enough", true), drafts = drafts)
            }
        }
    }

    /** 앱 내부 사진 주소 → data URL base64(서버가 받는 형식). 실패하면 null — 그 사진만 빠진다(글은 간다). */
    private fun encode(uri: String): String? =
        // 웹 카드엔 1280px면 충분 — 업로드·서버 저장 둘 다 가볍게(서버가 파일로 저장·서빙).
        runCatching { ImageEncoder.uriToJpegBase64(appContext, Uri.parse(uri), maxDim = 1280, quality = 72) }
            .getOrNull()?.let { "data:image/jpeg;base64,$it" }

    companion object {
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }
}

/** 서버가 돌려준 대표질문 초안 한 줄(분류·질문·답·확인필요). */
data class FaqSuggestDraft(
    val category: String,
    val question: String,
    val answer: String,
    val needsReview: Boolean
)

/** `/faq/suggest` 응답 — enough=false 면 문자가 적어 초안을 못 뽑음(앱이 기본 질문으로 시작). */
data class FaqSuggestResult(
    val enough: Boolean,
    val drafts: List<FaqSuggestDraft>
)
