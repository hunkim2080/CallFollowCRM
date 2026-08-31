package com.detailline.callfollowcrm.data.calendar

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

/**
 * 구글 캘린더 REST API 얇은 래퍼 (OkHttp + org.json).
 *
 * 동생분 Flutter 앱 `jeongsan/lib/calendar_sync.dart` 의 gcal.CalendarApi + _AuthClient 패턴을
 * Kotlin 으로 이식. 모든 호출은 accessToken(Bearer)을 인자로 받아 stateless 로 실행하고,
 * IO 디스패처에서 돈다. 실패 시 [CalendarApiException] 을 던져 호출측이 재시도/무시를 판단한다.
 *
 * 참고: 우리는 캘린더 하나("시공막내")만 쓰므로 동생 코드의 '라벨별 다중 캘린더/move' 는 생략.
 */
class CalendarApi(private val client: OkHttpClient) {

    class CalendarApiException(val code: Int, message: String) : Exception(message)

    /** 내 구글 캘린더 목록 항목. accessRole = owner/writer/reader/… */
    data class RemoteCalendar(val id: String, val summary: String, val accessRole: String)

    private val base = "https://www.googleapis.com/calendar/v3"
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    private suspend fun exec(req: Request): String = withContext(Dispatchers.IO) {
        client.newCall(req).execute().use { res ->
            val body = res.body?.string() ?: ""
            if (!res.isSuccessful) throw CalendarApiException(res.code, "HTTP ${res.code}: $body")
            body
        }
    }

    private fun authed(token: String, url: String): Request.Builder =
        Request.Builder().url(url).header("Authorization", "Bearer $token")

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    // ── 캘린더 목록 / 찾기 / 생성 ────────────────────────────
    suspend fun listCalendars(token: String): List<RemoteCalendar> {
        val out = mutableListOf<RemoteCalendar>()
        var pageToken: String? = null
        do {
            val url = "$base/users/me/calendarList?maxResults=250" +
                (pageToken?.let { "&pageToken=${enc(it)}" } ?: "")
            val json = JSONObject(exec(authed(token, url).get().build()))
            val items = json.optJSONArray("items") ?: JSONArray()
            for (i in 0 until items.length()) {
                val c = items.getJSONObject(i)
                val id = c.optString("id", "")
                if (id.isEmpty()) continue
                out.add(
                    RemoteCalendar(
                        id = id,
                        summary = c.optString("summary", "(이름 없음)"),
                        accessRole = c.optString("accessRole", "")
                    )
                )
            }
            pageToken = json.optString("nextPageToken", "").ifEmpty { null }
        } while (pageToken != null)
        return out
    }

    /** summary(이름)로 내가 쓰기 가능한 캘린더 찾기. 없으면 null. */
    suspend fun findCalendarBySummary(token: String, name: String): String? =
        listCalendars(token).firstOrNull {
            it.summary == name && (it.accessRole == "owner" || it.accessRole == "writer")
        }?.id

    /** 새 캘린더 생성 → calendarId. */
    suspend fun createCalendar(
        token: String,
        name: String,
        description: String,
        timeZone: String = "Asia/Seoul"
    ): String {
        val body = JSONObject()
            .put("summary", name)
            .put("description", description)
            .put("timeZone", timeZone)
        val json = JSONObject(
            exec(authed(token, "$base/calendars").post(body.toString().toRequestBody(jsonType)).build())
        )
        return json.getString("id")
    }

    // ── 이벤트 생성 / 갱신 / 삭제 ────────────────────────────
    /** 이벤트 생성 → eventId. */
    suspend fun insertEvent(token: String, calendarId: String, event: JSONObject): String {
        val url = "$base/calendars/${enc(calendarId)}/events"
        val json = JSONObject(
            exec(authed(token, url).post(event.toString().toRequestBody(jsonType)).build())
        )
        return json.getString("id")
    }

    /** 이벤트 갱신 (전체 교체, PUT). */
    suspend fun updateEvent(token: String, calendarId: String, eventId: String, event: JSONObject) {
        val url = "$base/calendars/${enc(calendarId)}/events/${enc(eventId)}"
        exec(authed(token, url).put(event.toString().toRequestBody(jsonType)).build())
    }

    /** 이벤트 삭제. 이미 없으면(404/410) 조용히 넘어간다. */
    suspend fun deleteEvent(token: String, calendarId: String, eventId: String) {
        val url = "$base/calendars/${enc(calendarId)}/events/${enc(eventId)}"
        try {
            exec(authed(token, url).delete().build())
        } catch (e: CalendarApiException) {
            if (e.code != 404 && e.code != 410) throw e
        }
    }
}
