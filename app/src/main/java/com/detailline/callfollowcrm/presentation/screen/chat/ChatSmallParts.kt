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
//  🍃 대화 화면의 작은 조각 — 녹음 플레이어·검색줄·말풍선 버튼·문구창·주소 고르기·요약 머리
//
//  ChatScreen.kt 에서 **그대로 옮겨온** 조각들이다. (2026-10-02)
//  옮긴 기준: 그 파일의 **다른 private 이름을 하나도 안 쓰는** 잎(leaf) 선언.
//  같은 패키지라 `private` → `internal` 한 글자만 바뀌었고 **부르는 곳은 그대로**다.
//  (Fable 점검 ✋ — 큰 화면을 통째로 쪼개는 건 위험하니 **잎부터** 떼어낸다)
// ======================================================================

/**
 * 통화 녹음 재생 플레이어 (2026-06-16 사장님) — 에이닷 안 들어가고 통화카드에서 바로 듣기.
 *   ▶/⏸ · 진행 슬라이더(드래그 탐색) · 0:00/총길이 · ±5초 · 배속(1.0→1.5→2.0). MediaPlayer 백엔드.
 *   재생 누를 때 lazy 로 준비(prepareAsync), 카드 사라지면 release. content:// (SAF 녹음 폴더) 재생.
 */
@Composable
internal fun CallRecordingPlayer(audioUri: String, durationHintMs: Long? = null, seekReqMs: Int? = null, seekReqTick: Int = 0) {
    val context = LocalContext.current
    var player by remember(audioUri) { mutableStateOf<android.media.MediaPlayer?>(null) }
    var prepared by remember(audioUri) { mutableStateOf(false) }
    var loading by remember(audioUri) { mutableStateOf(false) }
    var isPlaying by remember(audioUri) { mutableStateOf(false) }
    var pendingPlay by remember(audioUri) { mutableStateOf(false) }
    var pendingSeekMs by remember(audioUri) { mutableStateOf<Int?>(null) }   // 탭재생: 준비 완료 후 이 시각으로 seek
    var durationMs by remember(audioUri) { mutableStateOf((durationHintMs ?: 0L).toInt()) }
    var positionMs by remember(audioUri) { mutableStateOf(0) }
    var dragMs by remember(audioUri) { mutableStateOf<Int?>(null) }
    var speed by remember(audioUri) { mutableStateOf(1.0f) }
    var error by remember(audioUri) { mutableStateOf(false) }

    fun applySpeed(p: android.media.MediaPlayer) {
        runCatching { p.playbackParams = p.playbackParams.setSpeed(speed) }
    }
    fun create() {
        loading = true
        runCatching {
            android.media.MediaPlayer().apply {
                setDataSource(context, android.net.Uri.parse(audioUri))
                setOnPreparedListener { mp ->
                    prepared = true; loading = false; durationMs = mp.duration
                    if (pendingPlay) {
                        pendingSeekMs?.let { runCatching { mp.seekTo(it) }; positionMs = it; pendingSeekMs = null }
                        applySpeed(mp); runCatching { mp.start() }
                        isPlaying = mp.isPlaying; pendingPlay = false
                    }
                }
                setOnCompletionListener { isPlaying = false; positionMs = durationMs }
                setOnErrorListener { _, _, _ -> error = true; loading = false; isPlaying = false; true }
                prepareAsync()
            }.also { player = it }
        }.onFailure { error = true; loading = false }
    }
    fun togglePlay() {
        val p = player
        when {
            error -> {}
            p == null -> { pendingPlay = true; create() }
            !prepared -> { pendingPlay = true }
            p.isPlaying -> { p.pause(); isPlaying = false }
            else -> {
                if (durationMs in 1..positionMs) { runCatching { p.seekTo(0) }; positionMs = 0 }
                applySpeed(p); runCatching { p.start() }; isPlaying = p.isPlaying
            }
        }
    }
    fun seekAndPlay(ms: Int) {   // 탭재생 — 말풍선 시각으로 이동+재생. 준비 전이면 준비 후 seek(pendingSeekMs).
        if (error) return
        val p = player
        when {
            p == null -> { pendingSeekMs = ms; pendingPlay = true; create() }
            !prepared -> { pendingSeekMs = ms; pendingPlay = true }
            else -> {
                applySpeed(p); runCatching { p.seekTo(ms); p.start() }
                positionMs = ms; isPlaying = p.isPlaying
            }
        }
    }
    fun cycleSpeed() {   // 프로토 .sp 칩 — 탭하면 1×→1.5×→2× 순환. 통화는 빨리듣기가 유용. (2026-06-16 사장님)
        speed = when { speed < 1.25f -> 1.5f; speed < 1.75f -> 2.0f; else -> 1.0f }
        val p = player
        if (p != null && p.isPlaying) applySpeed(p)
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            val p = player
            if (p != null && dragMs == null) runCatching { positionMs = p.currentPosition }
            kotlinx.coroutines.delay(250)
        }
    }
    // 말풍선 탭 → 그 시각으로 재생 (tick 변할 때마다 = 같은 말풍선 재탭도 트리거)
    LaunchedEffect(seekReqTick) {
        if (seekReqTick > 0) seekReqMs?.let { seekAndPlay(it) }
    }
    androidx.compose.runtime.DisposableEffect(audioUri) {
        onDispose { runCatching { player?.release() }; player = null }
    }

    val shownPos = dragMs ?: positionMs
    val pteal = Color(0xFF1E6E6A)
    // 프로토 08352d6e .player — 한 줄: ▶ + 진행바 + 1:05/3:12 + [1.5×] 칩. (2026-08-15 1:1)
    Row(
        Modifier.fillMaxWidth().padding(top = 13.dp).clip(RoundedCornerShape(11.dp))
            .background(AppTheme.colors.bg).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (error) {
            Text("녹음을 재생할 수 없어요", color = TossTextTertiary, fontSize = 12.sp)
        } else {
            Box(
                Modifier.size(30.dp).clip(RoundedCornerShape(50)).background(pteal)
                    .clickable { togglePlay() },
                contentAlignment = Alignment.Center
            ) {
                if (loading) CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
                else Text(if (isPlaying) "⏸" else "▶", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(8.dp))
            androidx.compose.material3.Slider(
                value = if (durationMs > 0) (shownPos.toFloat() / durationMs).coerceIn(0f, 1f) else 0f,
                onValueChange = { frac -> dragMs = (frac * durationMs).toInt() },
                onValueChangeFinished = {
                    val d = dragMs
                    if (d != null) { runCatching { player?.seekTo(d) }; positionMs = d }
                    dragMs = null
                },
                colors = androidx.compose.material3.SliderDefaults.colors(
                    thumbColor = pteal, activeTrackColor = pteal, inactiveTrackColor = Color(0xFFCFDBDA)
                ),
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(6.dp))
            Text("${playerClock(shownPos)}/${playerClock(durationMs)}",
                fontSize = 10.sp, color = Color(0xFF5E7C7C), fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(7.dp))
            val spLabel = when { speed >= 1.9f -> "2×"; speed >= 1.4f -> "1.5×"; else -> "1×" }
            Box(
                Modifier.clip(RoundedCornerShape(8.dp)).background(Color.White)
                    .clickable { cycleSpeed() }.padding(horizontal = 8.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(spLabel, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF154D4A))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
/** 대화 안 검색 바 (카톡식) — 입력 + N/M 카운트 + ▲▼ 점프 + ✕ 닫기. (2026-09-02 사장님) */
@Composable
internal fun InChatSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    total: Int,
    current: Int,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onClose: () -> Unit
) {
    val fr = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }
    Row(
        Modifier.fillMaxWidth().background(Color.White).padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(TossGrayBg).padding(horizontal = 12.dp, vertical = 9.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            if (query.isEmpty()) Text("이 대화에서 검색 (문자·통화)", fontSize = 14.sp, color = TossTextTertiary)
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(fontFamily = com.detailline.callfollowcrm.presentation.theme.Pretendard, fontSize = 14.sp, color = TossTextPrimary),
                cursorBrush = SolidColor(TossBlue),
                modifier = Modifier.fillMaxWidth().focusRequester(fr)
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(
            if (query.isBlank()) "" else "$current/$total",
            fontSize = 12.sp, fontWeight = FontWeight.Bold,
            color = if (total > 0) TossBlue else TossTextTertiary
        )
        IconButton(onClick = onUp, enabled = total > 0) {
            Text("▲", fontSize = 13.sp, color = if (total > 0) TossTextSecondary else TossTextTertiary)
        }
        IconButton(onClick = onDown, enabled = total > 0) {
            Text("▼", fontSize = 13.sp, color = if (total > 0) TossTextSecondary else TossTextTertiary)
        }
        IconButton(onClick = onClose) {
            Icon(Icons.Default.Close, "닫기", tint = TossTextTertiary)
        }
    }
}

/** 이렇게 답해보세요 — 풀폭 답안 카드(세로 2개, 한눈에). 탭하면 입력칸에. (2026-08-14 사장님) */
@Composable
internal fun SuggestionCardWide(label: String?, text: String, onTap: () -> Unit) {
    val ci = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressScale(ci)
            .clip(shape)
            .background(AppTheme.colors.bg)
            .border(1.dp, AppTheme.colors.surfaceMuted, shape)
            .clickable(interactionSource = ci, indication = null) { onTap() }
            .padding(horizontal = 13.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            if (!label.isNullOrBlank()) {
                Text(label, color = TossBlue, fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(4.dp))
            }
            Text(
                text, color = TossTextSecondary, fontSize = 12.5.sp, lineHeight = 18.sp,
                maxLines = 3, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(8.dp))
        Text("›", color = Color(0xFFCED8E6), fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

/**
 * ⊕ 메뉴 한 줄 — **아이콘만 색, 네모는 없다.** (2026-09-22 사장님 "구분이 잘 안 간다")
 *
 * 전엔 세 줄이 다 같은 파란 네모라 눈에는 "파란 네모 3개" 로만 보였다.
 * ⚠️ 네모(면)를 되살리지 말 것 — 앱에서 넓은 면의 색은 **상태 딱지**로 읽힌다
 *    (초록=완료·보라=협업). 아이콘만 색을 주면 구분은 되면서 뜻은 안 섞인다.
 */
@Composable
internal fun ActionMenuRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    onTap: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onTap() }
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(13.dp))
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary)
    }
}

/**
 * 2026-05-24 — composer focus (ime 떠있음) 시 대화 요약 + AI 제안 박스를 한 줄로 압축.
 * 사장님이 입력 시작 = 위쪽 정보 압박 줄이고 메시지/composer 영역 확보.
 *   - "✨ 대화 요약 4줄 · {AI 제안 title}" 형식
 *   - ime 풀리면 호출부에서 자동으로 풀 박스로 복귀
 */
@Composable
internal fun CollapsedSummaryHeader(
    summaryLine: String,
    summaryLineCount: Int,
    nextActionTitle: String?,
    isRefreshing: Boolean = false,
    onExpand: () -> Unit
) {
    // 2026-06-02 사장님 결정(프로토 1:1) — 접힘 상태 = 프로토 chat-summary 바.
    //   흰 전체 바 + ✨ + 한 줄 요약(#1B64DA, 12.5sp w600) + 아래 테두리. 탭→펼침.
    val line = summaryLine.takeIf { it.isNotBlank() }
        ?: nextActionTitle?.takeIf { it.isNotBlank() }
        ?: (if (summaryLineCount > 0) "지난 대화를 정리했어요" else return)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .drawBehind {
                val s = 1.dp.toPx()
                drawLine(TossDivider, androidx.compose.ui.geometry.Offset(0f, size.height - s / 2),
                    androidx.compose.ui.geometry.Offset(size.width, size.height - s / 2), s)
            }
            .clickable { onExpand() }
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        com.detailline.callfollowcrm.presentation.theme.AiMark(TossBlue, 13.dp, 6.dp)
        Text(
            "요약: $line",
            color = TossBlueDark,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        // 2026-05-27 사장님 보고 fix: 갱신 중 spinner — "로딩 중인지 안 보임" 문제 해결.
        if (isRefreshing) {
            CircularProgressIndicator(
                color = TossBlue,
                strokeWidth = 1.5.dp,
                modifier = Modifier.size(12.dp).padding(start = 6.dp, end = 4.dp)
            )
        }
        // 펼치기 affordance — 탭하면 풍부한 요약 카드. (프로토 한 줄 바 + 펼침 = 사장님 결정)
        Text("▾", color = TossTextTertiary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 6.dp))
    }
}

/** 문구 수정 / 이름 바꾸기 공용 입력창. 빈 값으로는 저장 안 한다(실수로 지워지는 것 방지). */
@Composable
internal fun TemplateTextDialog(
    title: String,
    label: String,
    initial: String,
    multiline: Boolean,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        tonalElevation = 0.dp,
        title = { Text(title, color = TossTextPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp) },
        text = {
            Column {
                Text(label, color = TossTextTertiary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(7.dp))
                androidx.compose.material3.OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = !multiline,
                    minLines = if (multiline) 4 else 1,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                enabled = text.isNotBlank(),
                onClick = { onConfirm(text.trim()) }
            ) { Text("저장", color = if (text.isNotBlank()) TossBlue else TossTextTertiary, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text("취소", color = TossTextSecondary) }
        }
    )
}

/** 등록 확인창의 '주소 후보' 한 칸 — 고르면 파란 테두리. */
@Composable
internal fun AddrChoiceCard(label: String, value: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) AppTheme.colors.primaryBg else TossGrayBg)
            .border(
                width = if (selected) 1.5.dp else 0.dp,
                color = if (selected) TossBlue else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable { onClick() }
            .padding(12.dp)
    ) {
        Column {
            Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                color = if (selected) TossBlue else TossTextTertiary)
            Spacer(Modifier.height(3.dp))
            Text(value, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold,
                color = TossTextPrimary, lineHeight = 19.sp)
        }
    }
}

/**
 * 말풍선 꾹 누름 BottomSheet 의 액션 한 줄. 좌측 아이콘 + 텍스트 (라벨 + 부제).
 * 사장님 손가락 도달성 위해 vertical padding 넉넉히.
 */
@Composable
internal fun BubbleActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    label: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(14.dp))
        Column {
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                color = TossTextPrimary,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = TossTextTertiary
            )
        }
    }
}

/**
 * 발송 확인 시트의 첨부 사진 썸네일 줄 — 보내기 전에 '무엇을 보내는지' 눈으로. (2026-10-05 사장님)
 *   작성칸 첨부 미리보기와 같은 AsyncImage 패턴. 탭 → onPhotoTap(index) 로 큰사진 뷰어. 여러 장은 옆으로 스크롤.
 */
@androidx.compose.runtime.Composable
internal fun SendPhotoThumbs(photos: List<android.net.Uri>, onPhotoTap: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        photos.forEachIndexed { idx, uri ->
            AsyncImage(
                model = uri,
                contentDescription = "보낼 사진 ${idx + 1} — 누르면 크게",
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(68.dp)
                    .clip(AppShape.sm)
                    .background(TossGrayBg)
                    .clickable { onPhotoTap(idx) }
            )
        }
    }
}

