package com.detailline.callfollowcrm.util

import android.content.Context
import android.provider.CalendarContract
import java.util.Calendar
import java.util.TimeZone

/**
 * 📥 **폰에 있는 캘린더(구글·삼성·공휴일…) 읽기** — "외부 캘린더 가져오기"용. (2026-10-31 사장님)
 *
 *   READ_CALENDAR 권한으로 CalendarContract 를 읽는다. 읽기만 — 원본 캘린더는 **안 건드린다.**
 *   우리 자신의 "시공막내" 캘린더는 **제외**(우리가 구글로 내보낸 걸 되가져오면 중복). 가르기는 [isOurs].
 *
 *   ⚠️ 종일 일정 함정: CalendarContract 는 종일을 **UTC 자정**으로 저장한다 → 그냥 쓰면 **하루 밀린다.**
 *      [readEvents] 가 UTC 날짜 → 로컬 자정으로 보정한다.
 *   ⚠️ 반복 일정: Events 표의 '원본'만 본다(과거 DTSTART) → 범위 밖이라 안 들어온다(잘못된 날짜로 안 들어옴). MVP.
 */
object DeviceCalendars {

    /** 우리가 구글로 내보내는 캘린더 이름 — 이건 가져오기에서 뺀다(되가져오기=중복). */
    private const val OUR_CALENDAR_NAME = "시공막내"
    private const val DAY = 86_400_000L

    data class DeviceCalendar(
        val id: Long,
        val name: String,
        val account: String,
        val color: Int,
        /** 우리 앱이 구글로 내보낸 "시공막내" 캘린더면 true — 가져오기 목록에서 숨긴다(루프 방지). */
        val isOurs: Boolean,
    )

    /** 간단 일정으로 바로 넣을 수 있게 미리 빚은 모양(종일 UTC 보정·메모 합침 끝). */
    data class ImportedEvent(
        val sourceId: String,
        val title: String,
        val dayStartMs: Long,
        /** 자정부터 분. null = 하루 종일. */
        val minutes: Int?,
        val days: Int,
        /** 장소 + 설명을 합친 메모 — 탭하면 보인다. */
        val memo: String,
    )

    /** 폰에 등록된 캘린더 목록. 권한 없거나 실패 = 빈 리스트. */
    fun listCalendars(context: Context): List<DeviceCalendar> {
        val cols = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.CALENDAR_COLOR,
        )
        val out = ArrayList<DeviceCalendar>()
        runCatching {
            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI, cols, null, null, null
            )?.use { c ->
                val iId = c.getColumnIndexOrThrow(CalendarContract.Calendars._ID)
                val iNm = c.getColumnIndexOrThrow(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)
                val iAc = c.getColumnIndexOrThrow(CalendarContract.Calendars.ACCOUNT_NAME)
                val iCo = c.getColumnIndexOrThrow(CalendarContract.Calendars.CALENDAR_COLOR)
                while (c.moveToNext()) {
                    val name = c.getString(iNm) ?: continue
                    out += DeviceCalendar(
                        id = c.getLong(iId),
                        name = name,
                        account = c.getString(iAc) ?: "",
                        color = if (c.isNull(iCo)) 0 else c.getInt(iCo),
                        isOurs = name.contains(OUR_CALENDAR_NAME),
                    )
                }
            }
        }
        return out
    }

    /** 고른 캘린더들의 [fromMs, toMs] 범위 일정 개수 — 토글 목록의 "N개" 표시용. */
    fun countInRange(context: Context, calendarIds: Collection<Long>, fromMs: Long, toMs: Long): Map<Long, Int> {
        if (calendarIds.isEmpty()) return emptyMap()
        val counts = HashMap<Long, Int>()
        val ids = calendarIds.joinToString(",")
        val sel = "${CalendarContract.Events.CALENDAR_ID} IN ($ids) AND " +
            "${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTSTART} <= ? AND " +
            "${CalendarContract.Events.DELETED} = 0"
        runCatching {
            context.contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                arrayOf(CalendarContract.Events.CALENDAR_ID),
                sel, arrayOf(fromMs.toString(), toMs.toString()), null
            )?.use { c ->
                val iCal = c.getColumnIndexOrThrow(CalendarContract.Events.CALENDAR_ID)
                while (c.moveToNext()) {
                    val cid = c.getLong(iCal)
                    counts[cid] = (counts[cid] ?: 0) + 1
                }
            }
        }
        return counts
    }

    /** 고른 캘린더들의 [fromMs, toMs] 일정을 간단일정용 모양으로. 종일 UTC 보정·장소/설명 메모 합침. */
    fun readEvents(context: Context, calendarIds: Collection<Long>, fromMs: Long, toMs: Long): List<ImportedEvent> {
        if (calendarIds.isEmpty()) return emptyList()
        val ids = calendarIds.joinToString(",")
        val cols = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.ALL_DAY,
            CalendarContract.Events.DESCRIPTION,
            CalendarContract.Events.EVENT_LOCATION,
        )
        val sel = "${CalendarContract.Events.CALENDAR_ID} IN ($ids) AND " +
            "${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTSTART} <= ? AND " +
            "${CalendarContract.Events.DELETED} = 0"
        val out = ArrayList<ImportedEvent>()
        runCatching {
            context.contentResolver.query(
                CalendarContract.Events.CONTENT_URI, cols,
                sel, arrayOf(fromMs.toString(), toMs.toString()),
                "${CalendarContract.Events.DTSTART} ASC"
            )?.use { c ->
                val iId = c.getColumnIndexOrThrow(CalendarContract.Events._ID)
                val iTi = c.getColumnIndexOrThrow(CalendarContract.Events.TITLE)
                val iDs = c.getColumnIndexOrThrow(CalendarContract.Events.DTSTART)
                val iDe = c.getColumnIndexOrThrow(CalendarContract.Events.DTEND)
                val iAll = c.getColumnIndexOrThrow(CalendarContract.Events.ALL_DAY)
                val iDesc = c.getColumnIndexOrThrow(CalendarContract.Events.DESCRIPTION)
                val iLoc = c.getColumnIndexOrThrow(CalendarContract.Events.EVENT_LOCATION)
                while (c.moveToNext()) {
                    val title = (c.getString(iTi) ?: "").trim()
                    if (title.isBlank()) continue
                    if (c.isNull(iDs)) continue
                    val dtStart = c.getLong(iDs)
                    val dtEnd = if (c.isNull(iDe)) dtStart else c.getLong(iDe)
                    val allDay = !c.isNull(iAll) && c.getInt(iAll) == 1

                    val dayStart: Long
                    val minutes: Int?
                    val days: Int
                    if (allDay) {
                        dayStart = localMidnightOfUtcDate(dtStart)   // UTC 자정 → 로컬 날짜 (하루 밀림 보정)
                        minutes = null
                        val span = ((dtEnd - dtStart).coerceAtLeast(DAY) / DAY).toInt()   // 종일 끝은 exclusive
                        days = span.coerceAtLeast(1)
                    } else {
                        dayStart = DateTimeUtils.startOfDay(dtStart)
                        val cal = Calendar.getInstance().apply { timeInMillis = dtStart }
                        minutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
                        val endDay = DateTimeUtils.startOfDay(if (dtEnd > dtStart) dtEnd else dtStart)
                        days = (((endDay - dayStart) / DAY).toInt() + 1).coerceAtLeast(1)
                    }

                    val loc = (c.getString(iLoc) ?: "").trim()
                    val desc = (c.getString(iDesc) ?: "").trim()
                    val memo = listOfNotNull(
                        loc.takeIf { it.isNotBlank() }?.let { "장소: $it" },
                        desc.takeIf { it.isNotBlank() },
                    ).joinToString("\n")

                    out += ImportedEvent(
                        sourceId = c.getLong(iId).toString(),
                        title = title,
                        dayStartMs = dayStart,
                        minutes = minutes,
                        days = days,
                        memo = memo,
                    )
                }
            }
        }
        return out
    }

    /** UTC 자정(종일 저장 방식) → 그 날짜의 **로컬** 자정. (하루 밀림 방지) */
    private fun localMidnightOfUtcDate(utcMs: Long): Long {
        val u = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utcMs }
        return Calendar.getInstance().apply {
            clear()
            set(u.get(Calendar.YEAR), u.get(Calendar.MONTH), u.get(Calendar.DAY_OF_MONTH), 0, 0, 0)
        }.timeInMillis
    }
}
