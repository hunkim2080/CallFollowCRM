package com.detailline.callfollowcrm.data.calendar

import com.detailline.callfollowcrm.data.local.entity.CustomerEntity
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** 고객 일정 종류 — 시공(scheduledWorkDate) / A/S(asScheduledDate). */
enum class ScheduleType(val key: String) { WORK("work"), AS("as") }

/**
 * SyncManager 가 앱 저장소(prefs + Room)에 요구하는 것. 나중에 prefs+DAO 로 구현.
 * - calendarId: "시공막내" 구글 캘린더 id (prefs)
 * - eventId: (고객, 종류) → 구글 이벤트 id (DB — CustomerEntity 에 컬럼 추가 예정, v44)
 */
interface CalendarSyncStore {
    suspend fun getCalendarId(): String?
    suspend fun setCalendarId(id: String?)
    suspend fun eventId(customerId: Long, type: ScheduleType): String?
    suspend fun setEventId(customerId: Long, type: ScheduleType, eventId: String?)
    /** 시공/AS 일정이 있거나, 이미 올려둔 이벤트가 있는(=지울 수도 있는) 고객 전부. */
    suspend fun scheduledCustomers(): List<CustomerEntity>
}

/**
 * 구글 캘린더 동기화 핵심 (앱 → 캘린더, 1단계).
 *
 * 동생분 `jeongsan/lib/calendar_sync.dart` 의 CalendarSync 를 이식하되, **캘린더 하나("시공막내")**
 * 전제로 단순화. 고객 1명당 시공/A/S 이벤트를 만들고(생성/수정/삭제) 이벤트 id 를 저장한다.
 * 실패해도 앱은 정상 — 다음 기회에 재시도(호출측이 dirty 관리).
 *
 * ⚠️ 이벤트 표기(제목·시간블록·설명)는 잠정 기본값 — **사장님 확인 후 조정**(§0, 프로토 없음).
 */
class CalendarSyncManager(
    private val connection: GoogleCalendarConnection,
    private val api: CalendarApi,
    private val store: CalendarSyncStore,
) {
    companion object {
        const val CALENDAR_NAME = "시공막내"
        private const val DEFAULT_BLOCK_MS = 2 * 60 * 60_000L // 시각만 있을 때 기본 2시간 블록(사장님 확인)
        private const val DAY_MS = 86_400_000L
    }

    private val calMutex = Mutex()

    /** "시공막내" 캘린더 id — 없으면 찾거나(이름) 만든다. 동시 호출에도 한 번만 생성. */
    suspend fun ensureCalendar(token: String): String? {
        store.getCalendarId()?.let { return it }
        return calMutex.withLock {
            store.getCalendarId() ?: run {
                val id = api.findCalendarBySummary(token, CALENDAR_NAME)
                    ?: api.createCalendar(token, CALENDAR_NAME, "시공막내 — 시공/AS 일정 (앱 자동 동기화)")
                store.setCalendarId(id)
                id
            }
        }
    }

    /** 한 고객의 시공·A/S 일정을 캘린더에 반영. 미연결이면 조용히 넘어감(나중에 재시도). */
    suspend fun syncCustomer(c: CustomerEntity) {
        val token = connection.getTokenSilently() ?: return
        val cal = ensureCalendar(token) ?: return
        syncOne(token, cal, c, ScheduleType.WORK)
        syncOne(token, cal, c, ScheduleType.AS)
    }

    /**
     * 시공/AS 일정 있는(또는 있던) 고객 전부를 한 번에 반영 — 연결 직후 / 수동 '지금 동기화'.
     * 토큰·캘린더 준비는 한 번만. 반환 = 훑은 고객 수. 미연결이면 -1.
     */
    suspend fun syncAll(): Int {
        val token = connection.getTokenSilently() ?: return -1
        val cal = ensureCalendar(token) ?: return -1
        val customers = store.scheduledCustomers()
        for (c in customers) {
            syncOne(token, cal, c, ScheduleType.WORK)
            syncOne(token, cal, c, ScheduleType.AS)
        }
        return customers.size
    }

    /** 고객 삭제 시 그 고객의 모든 이벤트 정리. */
    suspend fun deleteCustomerEvents(customerId: Long) {
        val token = connection.getTokenSilently() ?: return
        val cal = store.getCalendarId() ?: return
        for (type in ScheduleType.entries) {
            store.eventId(customerId, type)?.let { ev ->
                runCatching { api.deleteEvent(token, cal, ev) }
                store.setEventId(customerId, type, null)
            }
        }
    }

    // ── 한 종류(시공/AS) 반영 ────────────────────────────────
    private suspend fun syncOne(token: String, cal: String, c: CustomerEntity, type: ScheduleType) {
        val existing = store.eventId(c.id, type)
        val event = buildEvent(c, type)

        if (event == null) {
            // 일정이 사라짐 → 있던 이벤트 삭제
            if (existing != null) {
                runCatching { api.deleteEvent(token, cal, existing) }
                store.setEventId(c.id, type, null)
            }
            return
        }

        try {
            if (existing == null) {
                store.setEventId(c.id, type, api.insertEvent(token, cal, event))
            } else {
                try {
                    api.updateEvent(token, cal, existing, event)
                } catch (e: CalendarApi.CalendarApiException) {
                    // 캘린더에서 지워진 이벤트(404/410) → 새로 만든다
                    if (e.code == 404 || e.code == 410) {
                        store.setEventId(c.id, type, api.insertEvent(token, cal, event))
                    } else throw e
                }
            }
        } catch (_: Exception) {
            // 실패 → 이벤트 id 유지/미변경. 다음 동기화 때 재시도.
        }
    }

    // ── 고객 → 이벤트 JSON ───────────────────────────────────
    /** 해당 종류의 일정이 없으면 null. */
    private fun buildEvent(c: CustomerEntity, type: ScheduleType): JSONObject? {
        val date = if (type == ScheduleType.WORK) c.scheduledWorkDate else c.asScheduledDate
        date ?: return null
        val days = (if (type == ScheduleType.WORK) c.scheduledWorkDays else c.asScheduledDays).coerceAtLeast(1)
        val minutes = if (type == ScheduleType.WORK) c.scheduledWorkMinutes else null // A/S 는 시각 없음
        // 제목: 짧은 주소(지역+아파트+동+호) 우선, 주소 없거나 못 뽑으면 이름/번호. (2026-09-01 사장님)
        val base = c.address?.let { shortAddress(it) } ?: (c.name?.takeIf { it.isNotBlank() } ?: c.phoneNumber)
        val summary = if (type == ScheduleType.WORK) "🛠️ $base" else "🔧 $base (A/S)"

        val start = JSONObject()
        val end = JSONObject()
        if (minutes != null && days <= 1) {
            val startMs = date + minutes * 60_000L
            start.put("dateTime", rfc3339(startMs)).put("timeZone", "Asia/Seoul")
            end.put("dateTime", rfc3339(startMs + DEFAULT_BLOCK_MS)).put("timeZone", "Asia/Seoul")
        } else {
            // 종일(또는 여러 날) — end.date 는 exclusive 라 +days
            start.put("date", dateOnly(date))
            end.put("date", dateOnly(date + days * DAY_MS))
        }

        // 내용: 고객 메모 + 총금액 · 계약금. (2026-09-01 사장님)
        val money = buildList {
            c.totalAmount?.takeIf { it > 0L }?.let { add("총금액 ${won(it)}") }
            c.depositAmount?.takeIf { it > 0L }?.let { add("계약금 ${won(it)}") }
        }.joinToString(" · ")
        val desc = buildString {
            if (c.memo.isNotBlank()) appendLine(c.memo.trim())
            if (money.isNotEmpty()) append("💰 $money")
        }.trim()

        return JSONObject().apply {
            put("summary", summary)
            c.address?.takeIf { it.isNotBlank() }?.let { put("location", it) }
            if (desc.isNotEmpty()) put("description", desc)
            put("start", start)
            put("end", end)
            // 무손실 왕복/식별용 (2단계 내리기에서 사용)
            put(
                "extendedProperties",
                JSONObject().put(
                    "private",
                    JSONObject()
                        .put("app", "sigongmagne")
                        .put("customerId", c.id.toString())
                        .put("type", type.key)
                )
            )
        }
    }

    private fun rfc3339(epochMs: Long): String = seoulFmt("yyyy-MM-dd'T'HH:mm:ssXXX").format(Date(epochMs))
    private fun dateOnly(epochMs: Long): String = seoulFmt("yyyy-MM-dd").format(Date(epochMs))

    private fun seoulFmt(pattern: String): SimpleDateFormat =
        SimpleDateFormat(pattern, Locale.US).apply { timeZone = TimeZone.getTimeZone("Asia/Seoul") }

    /**
     * 긴 주소 → 짧은 캘린더 제목. (2026-09-01 사장님)
     * 예) "경기도 성남시 분당구 대왕판교로 364 하늘채아파트 10동 202호" → "분당 하늘채 10동 202호"
     *   지역(구>시>군) + 아파트명 + N동 + N호. 2조각 미만이면 null(→ 이름/번호로 폴백).
     */
    private fun shortAddress(addr: String): String? {
        val a = addr.trim()
        if (a.isBlank()) return null
        val parts = mutableListOf<String>()
        (Regex("([가-힣]+)구(?=\\s|$)").find(a)?.groupValues?.getOrNull(1)
            ?: Regex("([가-힣]+)시(?=\\s|$)").find(a)?.groupValues?.getOrNull(1)
            ?: Regex("([가-힣]+)군(?=\\s|$)").find(a)?.groupValues?.getOrNull(1))
            ?.let { parts.add(it) }
        Regex("([가-힣A-Za-z0-9]+?)(?:아파트|아파|APT|apt|빌라|오피스텔|타워|캐슬|팰리스|파크|자이|래미안)")
            .find(a)?.groupValues?.getOrNull(1)?.let { parts.add(it) }
        Regex("(\\d+)\\s*동").find(a)?.groupValues?.getOrNull(1)?.let { parts.add("${it}동") }
        Regex("(\\d+)\\s*호").find(a)?.groupValues?.getOrNull(1)?.let { parts.add("${it}호") }
        return if (parts.size >= 2) parts.joinToString(" ") else null
    }

    /** 원 → 보기 좋은 금액. 만원 단위 딱 떨어지면 "N만원", 아니면 콤마. */
    private fun won(amount: Long): String =
        if (amount % 10_000L == 0L) "${amount / 10_000L}만원" else "%,d원".format(amount)
}
