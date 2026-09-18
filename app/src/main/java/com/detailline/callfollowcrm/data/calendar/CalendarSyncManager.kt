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
    /** jobId 가 있으면 **그 건**의 일정 번호. 시공(WORK)은 건마다 따로다. (2026-09-18) */
    suspend fun eventId(customerId: Long, type: ScheduleType, jobId: Long? = null): String?
    suspend fun setEventId(customerId: Long, type: ScheduleType, eventId: String?, jobId: Long? = null)

    /** 시공일이 잡힌 **건**들 — (건 id, 그 건 값을 채운 고객 복사본). (2026-09-18) */
    suspend fun scheduledWorkJobs(): List<Pair<Long, CustomerEntity>> = emptyList()
    /** 시공/AS 일정이 있거나, 이미 올려둔 이벤트가 있는(=지울 수도 있는) 고객 전부. */
    suspend fun scheduledCustomers(): List<CustomerEntity>

    /** 간단 일정(번호 없는 메모형) 전부. (2026-09-16 사장님) */
    suspend fun simpleEvents(): List<com.detailline.callfollowcrm.data.local.entity.SimpleEventEntity>
    suspend fun setSimpleEventId(id: Long, eventId: String?)

    /**
     * 마지막으로 구글에 올린 **내용의 지문**. 같으면 다시 안 올린다.
     *
     * 🔴 왜 (2026-09-16 사장님 "구글캘린더 연결이 왜 자꾸 실패하지?"):
     *   실패가 아니라 **46초 걸려서** 실패로 보였다. 일정이 있는 고객이 수백 명인데
     *   내용이 그대로여도 매번 한 명당 2번(시공·A/S) 구글에 요청을 보냈다 = 수백 번의 왕복.
     *   지문이 같으면 건너뛰면, 두 번째부터는 **바뀐 일정만** 올리게 된다.
     *   구현 안 한 곳(테스트 등)은 null 이라 예전처럼 전부 올린다.
     */
    suspend fun eventHash(customerId: Long, type: ScheduleType, jobId: Long? = null): String? = null
    suspend fun setEventHash(customerId: Long, type: ScheduleType, hash: String?, jobId: Long? = null) {}

    /**
     * 캘린더 본문에 채울 시공 상세 — **접수서/견적서에 이미 다 있는데 안 쓰고 있었다.** (2026-09-14 사장님)
     *   구현 안 한 곳(테스트 등)은 null → 예전처럼 메모·금액만 들어간다.
     */
    suspend fun workDetail(c: CustomerEntity): WorkDetail? = null
}

/** 캘린더 이벤트 본문 재료 — 접수서(intake_events) / 발행 견적(issued_docs) 에서 온다. */
data class WorkDetail(
    val itemsText: String? = null,     // 시공 내용 ("안방 화장실 바닥, 샤워부스 벽 3면")
    val customerMemo: String? = null,  // 고객이 접수서에 남긴 말
    val address: String? = null        // 고객이 접수서에 적은 주소 (앱에 주소가 비어 있을 때 대타)
)

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

        /**
         * 이 건을 **안 올리고 건너뛰어도 되는가.**
         *
         * ⚠️ 잘못 건너뛰면 일정이 구글 캘린더에 영영 안 올라간다(사장님이 현장을 놓친다) → 규칙을 따로 빼서
         *    [CalendarSkipRuleTest] 로 고정한다. 조건은 **둘 다** 만족해야 한다:
         *   ① 이미 올려둔 이벤트 id 가 있다 = 구글에 실제로 존재한다는 유일한 증거
         *   ② 지난번에 올린 내용의 지문이 지금과 같다 = 바뀐 게 없다
         *   지문이 없으면(처음이거나 지난번 실패) 무조건 올린다.
         */
        internal fun canSkipUpload(existingEventId: String?, lastHash: String?, newHash: String): Boolean =
            existingEventId != null && lastHash != null && lastHash == newHash
    }

    private val calMutex = Mutex()

    /**
     * "시공막내" 캘린더 id — 없으면 찾거나(이름) 만든다. 동시 호출에도 한 번만 생성.
     *
     * 권한을 `calendar.app.created`(앱이 만든 캘린더만)로 좁히면서 목록 조회가 막힐 수 있다.
     *   → 찾기는 **실패해도 무시**하고 곧장 새로 만든다. 기능이 멈추면 안 되니까.
     */
    suspend fun ensureCalendar(token: String): String? {
        store.getCalendarId()?.let { return it }
        return calMutex.withLock {
            store.getCalendarId() ?: run {
                val found = runCatching { api.findCalendarBySummary(token, CALENDAR_NAME) }.getOrNull()
                val id = found
                    ?: api.createCalendar(token, CALENDAR_NAME, "시공막내 — 시공/AS 일정 (앱 자동 동기화)")
                store.setCalendarId(id)
                id
            }
        }
    }

    /**
     * 저장해둔 캘린더가 더는 못 쓰는 것(권한 축소 전에 만든 것 / 사용자가 지운 것)일 때 버리고 새로 만든다.
     *   좁힌 권한으로는 **예전 넓은 권한으로 만든 캘린더에 못 쓴다** → 403/404 가 뜬다.
     *   그대로 두면 동기화가 영영 조용히 실패하므로, 한 번 갈아끼우고 이벤트 id 도 전부 비운다
     *   (다음 동기화에서 새 캘린더에 다시 만들어짐).
     */
    private suspend fun resetCalendar(): Unit = calMutex.withLock {
        store.setCalendarId(null)
        for (c in runCatching { store.scheduledCustomers() }.getOrDefault(emptyList())) {
            for (type in ScheduleType.entries) {
                store.setEventId(c.id, type, null)
                store.setEventHash(c.id, type, null)   // 새 캘린더엔 아무것도 없다 → 전부 다시 올려야 함
            }
        }
        // 건별로 들고 있는 시공 일정 번호도 전부 비운다 — 안 비우면 새 캘린더에 없는 번호로
        //   갱신을 시도해 조용히 실패한다. (2026-09-18)
        for ((jid, jc) in runCatching { store.scheduledWorkJobs() }.getOrDefault(emptyList())) {
            store.setEventId(jc.id, ScheduleType.WORK, null, jid)
            store.setEventHash(jc.id, ScheduleType.WORK, null, jid)
        }
    }

    /** 한 고객의 시공·A/S 일정을 캘린더에 반영. 미연결이면 조용히 넘어감(나중에 재시도). */
    suspend fun syncCustomer(c: CustomerEntity, retried: Boolean = false) {
        val token = connection.getTokenSilently() ?: return
        val cal = ensureCalendar(token) ?: return
        // 이 고객의 **모든 건**을 반영(시공). A/S 는 고객 단위 그대로. (2026-09-18)
        var broken = false
        for ((jid, jc) in store.scheduledWorkJobs()) {
            if (jc.id == c.id) broken = syncOne(token, cal, jc, ScheduleType.WORK, jid) || broken
        }
        broken = syncOne(token, cal, c, ScheduleType.AS) || broken
        if (broken && !retried) {
            resetCalendar()
            syncCustomer(c, retried = true)
        }
    }

    /**
     * 시공/AS 일정 있는(또는 있던) 고객 전부를 한 번에 반영 — 연결 직후 / 수동 '지금 동기화'.
     * 토큰·캘린더 준비는 한 번만. 반환 = 훑은 고객 수. 미연결이면 -1.
     */
    suspend fun syncAll(retried: Boolean = false): Int {
        val token = connection.getTokenSilently() ?: return -1
        val cal = ensureCalendar(token) ?: return -1
        val customers = store.scheduledCustomers()
        // 시공(WORK)은 **건마다** 한 일정. 전엔 고객마다 하나라 2차를 잡으면 1차 일정이 옮겨졌다. (2026-09-18)
        for ((jid, jc) in store.scheduledWorkJobs()) {
            if (syncOne(token, cal, jc, ScheduleType.WORK, jid) && !retried) {
                resetCalendar()
                return syncAll(retried = true)
            }
        }
        for (c in customers) {
            var broken = syncOne(token, cal, c, ScheduleType.AS)
            // 첫 건에서 캘린더가 못 쓰는 걸 알면 나머지를 헛돌리지 말고 바로 갈아끼우고 처음부터.
            if (broken && !retried) {
                resetCalendar()
                return syncAll(retried = true)
            }
        }
        // 간단 일정도 같이 올린다 — 사장님이 "이 캘린더가 편해서 다른 일정도 넣게 될 것 같다"고 한 게
        //   앱 안에만 있으면 반쪽이라서. 제목 앞 📌 로 시공(🏗️)·A/S(🔧) 와 한눈에 구분된다. (2026-09-16)
        val simples = store.simpleEvents()
        for (e in simples) syncSimple(token, cal, e)
        return customers.size + simples.size
    }

    /** 간단 일정 한 건 반영. 실패해도 조용히 넘어간다(다음 동기화에서 다시 시도). */
    private suspend fun syncSimple(
        token: String, cal: String,
        e: com.detailline.callfollowcrm.data.local.entity.SimpleEventEntity
    ) {
        val body = JSONObject().apply {
            put("summary", "📌 " + e.title)
            if (e.memo.isNotBlank()) put("description", e.memo)
            val start = JSONObject(); val end = JSONObject()
            val mins = e.minutes
            if (mins != null) {
                val startMs = e.dayStartMs + mins * 60_000L
                start.put("dateTime", rfc3339(startMs)).put("timeZone", "Asia/Seoul")
                end.put("dateTime", rfc3339(startMs + DEFAULT_BLOCK_MS)).put("timeZone", "Asia/Seoul")
            } else {
                start.put("date", dateOnly(e.dayStartMs))
                end.put("date", dateOnly(e.dayStartMs + DAY_MS))
            }
            put("start", start); put("end", end)
            put(
                "extendedProperties",
                JSONObject().put(
                    "private",
                    JSONObject().put("app", "sigongmagne").put("simpleId", e.id.toString()).put("type", "simple")
                )
            )
        }
        val existing = e.calendarEventId
        if (existing != null) {
            runCatching { api.updateEvent(token, cal, existing, body) }
                .onFailure { err ->
                    // 구글 쪽에서 사라진 이벤트(404/410) → 기억을 지우고 다음 번에 새로 만든다.
                    if (err is CalendarApi.CalendarApiException && (err.code == 404 || err.code == 410)) {
                        store.setSimpleEventId(e.id, null)
                    }
                }
        } else {
            runCatching { api.insertEvent(token, cal, body) }
                .onSuccess { store.setSimpleEventId(e.id, it) }
        }
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
    /** @return true = 캘린더 자체를 못 쓴다(갈아끼워야 함). false = 정상이거나 일시적 실패. */
    private suspend fun syncOne(
        token: String, cal: String, c: CustomerEntity, type: ScheduleType, jobId: Long? = null
    ): Boolean {
        val existing = store.eventId(c.id, type, jobId)
        // 접수서/견적에 있는 시공 내용·주소·고객 메모까지 본문에 채운다. 못 가져와도 그냥 진행.
        val detail = runCatching { store.workDetail(c) }.getOrNull()
        val event = buildEvent(c, type, detail)

        if (event == null) {
            // 일정이 사라짐 → 있던 이벤트 삭제
            if (existing != null) {
                runCatching { api.deleteEvent(token, cal, existing) }
                store.setEventId(c.id, type, null)
                store.setEventHash(c.id, type, null)
            }
            return false
        }

        // 지난번에 올린 내용과 똑같으면 구글에 안 물어본다 — 수백 번의 왕복이 여기서 사라진다.
        val hash = eventHash(event)
        if (canSkipUpload(existing, store.eventHash(c.id, type, jobId), hash)) return false

        try {
            if (existing == null) {
                store.setEventId(c.id, type, api.insertEvent(token, cal, event), jobId)
            } else {
                try {
                    api.updateEvent(token, cal, existing, event)
                } catch (e: CalendarApi.CalendarApiException) {
                    // 캘린더에서 지워진 이벤트(404/410) → 새로 만든다
                    if (e.code == 404 || e.code == 410) {
                        store.setEventId(c.id, type, api.insertEvent(token, cal, event), jobId)
                    } else throw e
                }
            }
            // 여기까지 왔으면 이 내용이 구글에 올라가 있다 — 다음엔 건너뛸 수 있게 지문 저장.
            store.setEventHash(c.id, type, hash, jobId)
        } catch (e: CalendarApi.CalendarApiException) {
            // 새 이벤트조차 못 만든다 = 이벤트가 아니라 **캘린더**가 문제(권한 없음/삭제됨).
            //   호출측이 캘린더를 갈아끼우고 한 번 다시 시도하게 알린다.
            store.setEventHash(c.id, type, null)   // 실패했으니 '올렸다' 표시를 남기면 안 된다
            if (e.code == 403 || e.code == 404) return true
        } catch (_: Exception) {
            // 그 외 실패(네트워크 등) → 이벤트 id 유지. 다음 동기화 때 재시도.
            store.setEventHash(c.id, type, null)
        }
        return false
    }

    /** 이벤트 내용의 지문 — 제목·시간·본문이 하나라도 바뀌면 달라진다. */
    private fun eventHash(event: JSONObject): String {
        val text = event.toString()
        var h = 1125899906842597L          // FNV 계열 간단 해시. 충돌 확률이 낮고 값이 짧다.
        for (ch in text) h = 31 * h + ch.code
        return java.lang.Long.toHexString(h)
    }

    // ── 고객 → 이벤트 JSON ───────────────────────────────────
    /** 해당 종류의 일정이 없으면 null. */
    private fun buildEvent(c: CustomerEntity, type: ScheduleType, detail: WorkDetail? = null): JSONObject? {
        val date = if (type == ScheduleType.WORK) c.scheduledWorkDate else c.asScheduledDate
        date ?: return null
        val days = (if (type == ScheduleType.WORK) c.scheduledWorkDays else c.asScheduledDays).coerceAtLeast(1)
        val minutes = if (type == ScheduleType.WORK) c.scheduledWorkMinutes else null // A/S 는 시각 없음
        // 제목 = 사장님이 예전에 쓰시던 양식 그대로. (2026-09-14 사장님)
        //   🏗️[125] 서울 송파구     ← [총금액 만원] + 시/도 + 시군구
        //   달력을 훑기만 해도 "그날 얼마짜리 현장인지" 가 바로 보여야 한다는 게 요지.
        //   앱에 주소가 비면 **접수서에 고객이 적은 주소**로 대신한다(전엔 곧장 전화번호가 제목이었다).
        val addr = c.address?.takeIf { it.isNotBlank() } ?: detail?.address?.takeIf { it.isNotBlank() }
        val region = addr?.let { regionOf(it) }
        val who = c.name?.takeIf { it.isNotBlank() }
        val base = region ?: who ?: c.phoneNumber
        val moneyTag = c.totalAmount?.takeIf { it > 0L }?.let { "[${it / 10_000L}]" } ?: ""
        val summary = if (type == ScheduleType.WORK) "🏗️$moneyTag $base".trim()
        else "🔧$moneyTag $base (A/S)".trim()

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

        // 본문 — 폰 안 열고 캘린더만 봐도 현장이 그려지게. (2026-09-14 사장님)
        //   재료는 접수서(intake_events)·발행 견적(issued_docs)에 이미 있었는데 안 쓰고 있었다.
        val nl = "\n"
        fun section(title: String, body: String?) = body?.trim()?.takeIf { it.isNotBlank() }?.let {
            title + nl + it.lines().joinToString(nl) { ln -> "- " + ln.trim() } + nl
        }
        val money = buildList {
            c.totalAmount?.takeIf { it > 0L }?.let { add("총금액 ${won(it)}") }
            c.depositAmount?.takeIf { it > 0L }?.let { add("계약금 ${won(it)}") }
            c.balanceAmount?.takeIf { it > 0L }?.let {
                add(if (c.balancePaidAt != null) "잔금 ${won(it)} 받음" else "잔금 ${won(it)}")
            }
        }.joinToString(" · ")
        val memoAll = listOfNotNull(
            c.memo.takeIf { it.isNotBlank() },
            detail?.customerMemo?.takeIf { it.isNotBlank() }?.let { "(고객) " + it }
        ).joinToString(nl)
        val desc = buildString {
            section("📞 고객님 연락처", c.phoneNumber)?.let { append(it).append(nl) }
            section("📋 시공 주소", addr)?.let { append(it).append(nl) }
            // 시공 내용은 쉼표로 붙이지 말고 **한 줄에 하나씩** (사장님: 그래야 가독성이 좋다)
            section("🔔 시공 내용", detail?.itemsText?.let { splitItems(it).joinToString(nl) })
                ?.let { append(it).append(nl) }
            section("💬 메모", memoAll)?.let { append(it).append(nl) }
            section("💰 금액", money.takeIf { it.isNotEmpty() })?.let { append(it) }
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

    /**
     * 캘린더 제목에 쓸 지역 — "서울 송파구", "경기 안산시". (2026-09-14 사장님 예전 양식)
     *   주소 첫 두 덩어리를 보되, 특별시/광역시/도 는 짧게 줄인다. 못 알아보면 null.
     */
    private fun regionOf(addr: String): String? {
        val t = addr.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (t.isEmpty()) return null
        val sido = t[0]
            .replace("특별자치도", "").replace("특별자치시", "")
            .replace("특별시", "").replace("광역시", "")
            .removeSuffix("도")
            .ifBlank { t[0] }
        val gu = t.getOrNull(1)?.takeIf { it.endsWith("시") || it.endsWith("군") || it.endsWith("구") }
        return listOfNotNull(sido.takeIf { it.isNotBlank() }, gu).joinToString(" ").takeIf { it.isNotBlank() }
    }

    /**
     * 시공 내용 항목 나누기 — 쉼표로 붙어 있으면 읽기 힘들다고 하셔서 **한 줄에 하나씩**. (2026-09-14 사장님)
     *   "안방 화장실 바닥, 샤워부스 벽 3면" → 두 줄.
     */
    private fun splitItems(raw: String): List<String> =
        raw.split('\n', ',', '·', ';').map { it.trim() }.filter { it.isNotBlank() }
}
