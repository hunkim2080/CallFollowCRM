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

    /** 연결된 구글 계정 이메일 — userinfo. email 권한 없으면(옛 연결) 실패 → null. (2026-10-05 사장님) */
    suspend fun fetchAccountEmail(token: String): String? =
        runCatching {
            val body = exec(authed(token, "https://www.googleapis.com/oauth2/v3/userinfo").get().build())
            org.json.JSONObject(body).optString("email").takeIf { it.isNotBlank() }
        }.getOrNull()

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

    /** 우리 앱이 만든 캘린더 이벤트 한 건의 식별 정보. 고아 청소가 '내 폰/내 고객 것만' 가리는 데 쓴다. */
    data class AppCalEvent(val id: String, val deviceId: String, val customerId: String)

    /**
     * 🧹 **이 앱(sigongmagne)이 만든 이벤트들 전부 (식별정보 포함).** 고아 청소용. (2026-10-06 사장님)
     *   `privateExtendedProperty=app=sigongmagne` 로 **우리가 만든 것만** 추려 받는다 —
     *   사장님이 손으로 만든 일정·다른 앱 일정은 애초에 목록에 안 들어온다.
     *   각 건의 **deviceId(어느 폰이 만들었나)·customerId** 를 같이 돌려줘, 같은 계정을 쓰는
     *   다른 폰의 일정을 실수로 안 지우게 한다.
     */
    suspend fun listAppEvents(token: String, calendarId: String): List<AppCalEvent> {
        val out = mutableListOf<AppCalEvent>()
        var pageToken: String? = null
        do {
            val url = "$base/calendars/${enc(calendarId)}/events" +
                "?privateExtendedProperty=${enc("app=sigongmagne")}" +
                "&showDeleted=false&maxResults=2500" +
                (pageToken?.let { "&pageToken=${enc(it)}" } ?: "")
            val json = JSONObject(exec(authed(token, url).get().build()))
            val items = json.optJSONArray("items") ?: JSONArray()
            for (i in 0 until items.length()) {
                val it = items.getJSONObject(i)
                val id = it.optString("id", "")
                if (id.isEmpty()) continue
                val priv = it.optJSONObject("extendedProperties")?.optJSONObject("private")
                out.add(
                    AppCalEvent(
                        id = id,
                        deviceId = priv?.optString("deviceId", "").orEmpty(),
                        customerId = priv?.optString("customerId", "").orEmpty()
                    )
                )
            }
            pageToken = json.optString("nextPageToken", "").ifEmpty { null }
        } while (pageToken != null)
        return out
    }
}
