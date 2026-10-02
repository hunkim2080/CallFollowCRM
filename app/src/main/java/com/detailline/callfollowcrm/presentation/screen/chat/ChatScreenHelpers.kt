@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.detailline.callfollowcrm.presentation.screen.chat

import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.outlined.Info
import com.detailline.callfollowcrm.presentation.theme.AppTheme
import com.detailline.callfollowcrm.presentation.theme.AppShape
import com.detailline.callfollowcrm.presentation.theme.LightColors
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Description
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.union
import com.detailline.callfollowcrm.presentation.util.bottomBarClearance
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import com.detailline.callfollowcrm.ai.ReplySuggestions
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.detailline.callfollowcrm.presentation.component.tossCardShadow
import com.detailline.callfollowcrm.presentation.component.pressScale
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.CircleShape
import com.detailline.callfollowcrm.presentation.theme.AppSpace
import com.detailline.callfollowcrm.presentation.theme.AppType
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.detailline.callfollowcrm.ai.NextAction
import com.detailline.callfollowcrm.data.local.entity.MessageTemplateEntity
import com.detailline.callfollowcrm.data.repository.SmsRepository
import com.detailline.callfollowcrm.domain.model.TemplateCategory
import com.detailline.callfollowcrm.presentation.theme.TossBlue
import com.detailline.callfollowcrm.presentation.theme.TossBlueDark
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import com.detailline.callfollowcrm.presentation.theme.TossBlueSoft
import com.detailline.callfollowcrm.presentation.theme.TossDivider
import com.detailline.callfollowcrm.presentation.theme.TossError
import com.detailline.callfollowcrm.presentation.theme.TossGrayBg
import com.detailline.callfollowcrm.presentation.theme.TossSuccess
import com.detailline.callfollowcrm.presentation.theme.TossWarning
import com.detailline.callfollowcrm.presentation.theme.TossTextPrimary
import com.detailline.callfollowcrm.presentation.theme.TossTextSecondary
import com.detailline.callfollowcrm.presentation.theme.TossTextTertiary
import com.detailline.callfollowcrm.util.DateTimeUtils
import com.detailline.callfollowcrm.util.PhoneNumberFormatter
import kotlinx.coroutines.launch
import com.detailline.callfollowcrm.presentation.util.keyboardOrNavPadding
import com.detailline.callfollowcrm.presentation.util.keyboardClearance
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.zIndex
import com.detailline.callfollowcrm.util.PhoneKey

// ======================================================================
//  🍃 대화 화면의 작은 도우미 — 글자·파일·날짜 셈
//
//  ChatScreen.kt 에서 **그대로 옮겨온** 조각들이다. (2026-10-02)
//  옮긴 기준: 그 파일의 **다른 private 이름을 하나도 안 쓰는** 잎(leaf) 선언.
//  같은 패키지라 `private` → `internal` 한 글자만 바뀌었고 **부르는 곳은 그대로**다.
//  (Fable 점검 ✋ — 큰 화면을 통째로 쪼개는 건 위험하니 **잎부터** 떼어낸다)
// ======================================================================

/**
 * 타임라인(시간 DESC)에 날짜 경계 구분선을 끼워넣은 렌더 행 목록.
 *   reverseLayout=true 라 list[0]=화면 맨 아래(최신). 오름차순으로 "날짜 바뀌면 그 위에 구분선" 을
 *   넣은 뒤 다시 뒤집어 DESC 로 돌려준다 → 각 날짜 그룹 위에 구분선이 뜸.
 */
internal fun withDateDividers(items: List<ChatTimelineItem>): List<ChatTimelineItem> {
    if (items.isEmpty()) return items
    val asc = items.asReversed() // 오래된→최신
    val out = ArrayList<ChatTimelineItem>(asc.size + 8)
    var lastDay = Long.MIN_VALUE
    for (ti in asc) {
        val day = DateTimeUtils.startOfDay(ti.timeMs)
        if (day != lastDay) {
            out.add(ChatTimelineItem.DateDivider(day))
            lastDay = day
        }
        out.add(ti)
    }
    return out.asReversed() // 다시 DESC
}

/** 프로토 chat-date 라벨 — 오늘/어제/그 외 "M월 D일 (요일)". */
internal fun chatDateLabel(dayStart: Long): String {
    val today = DateTimeUtils.startOfDay(System.currentTimeMillis())
    return when (((today - dayStart) / DateTimeUtils.DAY_MS).toInt()) {
        0 -> "오늘"
        1 -> "어제"
        else -> java.text.SimpleDateFormat("M월 d일 (E)", java.util.Locale.KOREAN).format(java.util.Date(dayStart))
    }
}

/**
 * 문자 + 통화를 시간 DESC 로 병합. 메시지 로딩(loadMessages) 은 그대로 두고 렌더 직전에만 합침.
 *   같은 시각이면 통화를 먼저(아래쪽=reverseLayout) — 통화 후 문자 흐름이 자연스럽게 보이도록.
 */
internal fun buildChatTimeline(
    messages: List<com.detailline.callfollowcrm.data.repository.SmsRepository.SmsMessage>,
    calls: List<com.detailline.callfollowcrm.data.local.entity.CallRecordEntity>,
    intakeEvents: List<com.detailline.callfollowcrm.data.local.entity.IntakeEventEntity>,
    timelineEvents: List<com.detailline.callfollowcrm.data.local.entity.TimelineEventEntity>,
    issuedDocs: List<com.detailline.callfollowcrm.data.local.entity.IssuedDocEntity>
): List<ChatTimelineItem> {
    if (calls.isEmpty() && intakeEvents.isEmpty() && timelineEvents.isEmpty() && issuedDocs.isEmpty())
        return messages.map { ChatTimelineItem.Msg(it) }
    val merged = ArrayList<ChatTimelineItem>(messages.size + calls.size + intakeEvents.size + timelineEvents.size + issuedDocs.size)
    messages.forEach { merged += ChatTimelineItem.Msg(it) }
    calls.forEach { merged += ChatTimelineItem.Call(it) }
    intakeEvents.forEach { merged += ChatTimelineItem.Intake(it) }
    timelineEvents.forEach { merged += ChatTimelineItem.Event(it) }
    issuedDocs.forEach { merged += ChatTimelineItem.Issued(it) }
    return merged.sortedWith(compareByDescending<ChatTimelineItem> { it.timeMs }.thenBy { it is ChatTimelineItem.Msg })
}

/** 통화 전문 화자분리 세그먼트 JSON → (speaker, text) 리스트. 실패/빈=빈 리스트. (2026-08-14) */
/** 통화 태그 JSON 배열 문자열 → List. 서버 tags[](# 없이) 저장분. (2026-08-17) */
internal fun parseCallTags(json: String?): List<String> {
    val s = json?.trim()
    if (s.isNullOrBlank()) return emptyList()
    return runCatching {
        val arr = org.json.JSONArray(s)
        (0 until arr.length()).mapNotNull {
            arr.optString(it).trim().removePrefix("#").trim().takeIf { t -> t.isNotBlank() }
        }
    }.getOrDefault(emptyList())
}

// (speaker, text, startMs?) — startMs 는 Whisper 세그먼트일 때만(탭재생용). 옛 워커/폴백은 null.
internal fun parseTranscriptSegments(json: String?): List<Triple<String, String, Long?>> {
    val s = json?.trim()
    if (s.isNullOrBlank()) return emptyList()
    return runCatching {
        val arr = org.json.JSONArray(s)
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val text = o.optString("text").trim()
            if (text.isBlank()) return@mapNotNull null
            val spk = o.optString("speaker").trim().ifBlank { "?" }
            val startMs = if (o.has("start_ms")) o.optLong("start_ms", -1L).takeIf { it >= 0 } else null
            Triple(spk, text, startMs)
        }
    }.getOrDefault(emptyList())
}

internal fun playerClock(ms: Int): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
}

/** 통화 길이(초)를 "3분 12초"/"45초" 로. 0 이하(부재중/거절)면 null = 길이 생략. */
internal fun formatCallDuration(seconds: Long): String? {
    if (seconds <= 0L) return null
    val m = seconds / 60
    val s = seconds % 60
    return if (m > 0) "${m}분 ${s}초" else "${s}초"
}

/**
 * MMS 동영상 파트 재생 — content://mms/part/{id} 는 기본문자앱(우리)만 읽을 수 있어 외부 재생기에 직접 못 넘긴다.
 *   → 앱 캐시(shared/)로 복사한 뒤 FileProvider URI 로 재생기에 넘겨(권한 grant) 연다. (2026-07-13 사장님)
 */
internal fun playMmsVideo(context: android.content.Context, partUri: android.net.Uri) {
    runCatching {
        val dir = java.io.File(context.cacheDir, "shared").apply { mkdirs() }
        val out = java.io.File(dir, "mmsvid_${System.nanoTime()}.mp4")
        val copied = context.contentResolver.openInputStream(partUri)?.use { input ->
            out.outputStream().use { input.copyTo(it) }; true
        } ?: false
        if (!copied || out.length() == 0L) {
            android.widget.Toast.makeText(context, "동영상을 열지 못했어요", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        val shareUri = androidx.core.content.FileProvider.getUriForFile(
            context, context.packageName + ".fileprovider", out
        )
        context.startActivity(
            android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                setDataAndType(shareUri, "video/*")
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }.onFailure {
        android.util.Log.w("MmsVid", "play failed", it)
        android.widget.Toast.makeText(context, "동영상을 열지 못했어요", android.widget.Toast.LENGTH_SHORT).show()
    }
}

/** 본문에서 첫 URL 추출 — 스킴 없으면 https:// 보정해 반환. 없으면 null. */
internal fun firstUrlIn(body: String): String? {
    val m = android.util.Patterns.WEB_URL.matcher(body)
    if (!m.find()) return null
    val raw = m.group()
    return if (raw.startsWith("http://", true) || raw.startsWith("https://", true)) raw else "https://$raw"
}

/**
 * 본문 속 URL·전화번호·날짜를 밑줄+색으로 표시하고 위치별 annotation(tag=URL/PHONE/DATE)을 단 AnnotatedString.
 *   탭 동작은 말풍선 onClick 이 탭 지점 → annotation 조회로 처리. baseMs=문자 시각(상대 날짜 기준). (2026-08-04 사장님)
 */
/**
 * 찾은 글자에 **형광펜**. (2026-09-20 사장님 "키워드에 형광펜이 칠해져야 익숙할것같은데")
 *
 * 브라우저에서 글자 찾을 때와 같은 방식 —
 *   찾은 글자는 전부 **연한 노랑**, 지금 ▲▼ 로 보고 있는 것만 **진한 노랑**.
 * 내가 보낸 말풍선은 파란 바탕에 흰 글씨라, 형광펜 자리만 **글자색을 검게** 바꿔야 읽힌다.
 */
internal fun highlightBody(src: AnnotatedString, query: String, current: Boolean): AnnotatedString {
    val q = query.trim()
    if (q.isEmpty()) return src
    val text = src.text
    val hits = ArrayList<Int>()
    var i = text.indexOf(q, ignoreCase = true)
    while (i >= 0) {
        hits.add(i)
        i = text.indexOf(q, i + q.length, ignoreCase = true)
    }
    if (hits.isEmpty()) return src
    val pen = if (current) Color(0xFFFFD54A) else Color(0xFFFFF0B3)
    return androidx.compose.ui.text.buildAnnotatedString {
        append(src)   // 링크·전화·날짜 표시를 그대로 안고 간다
        for (start in hits) {
            addStyle(
                androidx.compose.ui.text.SpanStyle(
                    background = pen,
                    color = TossTextPrimary,
                    fontWeight = FontWeight.Bold
                ),
                start, start + q.length
            )
        }
    }
}

/**
 * 말풍선에 보일 링크 글자 — `https://` 는 떼고, 그래도 길면 가운데를 줄인다. (2026-09-23 사장님 화면 점검)
 *
 * ⚠️ **표시용일 뿐이다.** 복사·열기는 원문 URL 을 쓴다. 여기서 줄인 글자가 클립보드에 들어가면 안 된다.
 */
internal fun shortUrlLabel(raw: String): String {
    val noScheme = raw.removePrefix("https://").removePrefix("http://").removePrefix("HTTPS://").removePrefix("HTTP://")
    if (noScheme.length <= 36) return noScheme          // 대부분의 우리 링크는 여기서 끝
    val host = noScheme.substringBefore('/')
    val tail = noScheme.takeLast(12)
    return "$host/…$tail"
}

/** 돈 표기 — 규칙은 MoneyFormatter 한 곳에. 손님이 보는 글이라 **안 깎는다**. (2026-09-28) */
internal fun payWonLabel(won: Long): String =
    com.detailline.callfollowcrm.util.MoneyFormatter.manwonOrWon(won)

internal fun dialPhone(context: android.content.Context, phoneNumber: String) {
    // ☎️ 전화 거는 자리는 **한 곳**이다 — 거는 번호를 기억해 두어야
    //   통화가 시작될 때 그 손님 카드를 띄운다. (2026-09-30 사장님)
    com.detailline.callfollowcrm.util.PhoneDialer.open(context, phoneNumber)
}

internal fun categoryLabel(categoryName: String): String =
    runCatching { TemplateCategory.valueOf(categoryName).label }.getOrDefault("템플릿")

/**
 * P3 — 계약금 안내문 본문 앞에 시공일 한 줄 prepend.
 * 사장님이 보낼 메시지에서 일정 = 가장 중요한 정보. 템플릿 본문 그대로면 시공일이 빠질 위험.
 */
internal fun prependScheduleNote(body: String, scheduledMs: Long): String {
    val dateStr = DateTimeUtils.formatScheduledDate(scheduledMs)
    return "예약 일정: $dateStr\n\n$body"
}

/** 받은/보낸 문자 사진을 휴대폰 갤러리(사진/시공막내)에 저장. 성공 true. (2026-06-23 사장님) */
internal suspend fun saveImageToGallery(context: android.content.Context, uri: android.net.Uri): Boolean =
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, "시공막내_" + System.currentTimeMillis() + ".jpg")
                put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, android.os.Environment.DIRECTORY_PICTURES + "/시공막내")
                    put(android.provider.MediaStore.Images.Media.IS_PENDING, 1)
                }
            }
            val collection = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q)
                android.provider.MediaStore.Images.Media.getContentUri(android.provider.MediaStore.VOLUME_EXTERNAL_PRIMARY)
            else android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            val dest = resolver.insert(collection, values) ?: return@runCatching false
            val ok = resolver.openOutputStream(dest)?.use { out ->
                resolver.openInputStream(uri)?.use { input -> input.copyTo(out); true } ?: false
            } ?: false
            if (!ok) return@runCatching false
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                values.clear()
                values.put(android.provider.MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(dest, values, null, null)
            }
            true
        }.getOrDefault(false)
    }


/** 시작일~끝날이 **며칠짜리 공사**인지. 끝날이 없으면 당일(1일). */
internal fun estDaysBetween(startMs: Long?, endMs: Long?): Int {
    if (startMs == null) return 1
    if (endMs == null) return 1
    val a = DateTimeUtils.startOfDay(startMs)
    val b = DateTimeUtils.startOfDay(endMs)
    if (b <= a) return 1
    // 날짜 차이는 '하루 = 86400초' 로 세면 서머타임·윤초에서 하루씩 틀어질 수 있어 달력으로 센다.
    val cal = java.util.Calendar.getInstance().apply { timeInMillis = a }
    var n = 1
    while (cal.timeInMillis < b && n < 400) {
        cal.add(java.util.Calendar.DAY_OF_MONTH, 1); n++
    }
    return n
}

/** 원 단위를 "40만원" / "1,500,000원" 형식으로. */
internal fun formatWon(amount: Long): String {
    if (amount == 0L) return "0원"
    return if (amount >= 10_000L && amount % 10_000L == 0L) {
        "${amount / 10_000L}만원"
    } else {
        "${java.text.NumberFormat.getNumberInstance(java.util.Locale.KOREA).format(amount)}원"
    }
}
