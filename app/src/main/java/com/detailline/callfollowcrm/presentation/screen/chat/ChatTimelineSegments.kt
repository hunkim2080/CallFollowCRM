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
//  🍃 대화 타임라인의 한 줄 조각들 — 접수서·이력·발행
//
//  ChatScreen.kt 에서 **그대로 옮겨온** 조각들이다. (2026-10-02)
//  옮긴 기준: 그 파일의 **다른 private 이름을 하나도 안 쓰는** 잎(leaf) 선언.
//  같은 패키지라 `private` → `internal` 한 글자만 바뀌었고 **부르는 곳은 그대로**다.
//  (Fable 점검 ✋ — 큰 화면을 통째로 쪼개는 건 위험하니 **잎부터** 떼어낸다)
// ======================================================================

/**
 * 채팅 안 시공접수서 제출 이벤트 카드 (2026-06-05) — 고객이 접수서를 작성 완료한 사실을 타임라인에 표시.
 *   통화 카드(CallSegment)와 같은 전체폭 이벤트 카드 형태(파란 accent). 문자 말풍선과 구분.
 *   내용: 📋 접수서 작성 완료 + 제출 시각, 그리고 (있으면) 📅 시공일 · 💰 만원 · 📍 주소.
 *   프로토 team-alert quote 의 카피(시공일·금액·주소 묶음)를 채팅 이벤트로 옮김.
 */
@Composable
internal fun IntakeSegment(
    event: com.detailline.callfollowcrm.data.local.entity.IntakeEventEntity,
    onConfirm: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .tossCardShadow(RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
            .background(TossBlueSoft)
            .border(1.dp, TossBlue.copy(alpha = 0.22f), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 11.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(TossBlue),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Assignment, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "접수서 작성을 완료했어요",
                    fontSize = 13.5.sp, fontWeight = FontWeight.ExtraBold, color = TossBlueDark
                )
                Text(
                    DateTimeUtils.formatShort(event.submittedAtMs) + " · 고객이 직접 작성",
                    fontSize = 11.sp, color = TossTextTertiary, fontWeight = FontWeight.Bold
                )
            }
        }
        // 접수 내용 요약 — 프로토 team-alert quote 의 시공일·금액·주소 묶음.
        val detailLines = buildList {
            val sched = buildString {
                event.dateLabel?.let { append("시공일 $it") }
                event.totalManwon?.let {
                    if (isNotEmpty()) append("  ·  ")
                    append("${it}만원")
                }
            }
            if (sched.isNotEmpty()) add(sched)
            event.address?.let { add(it) }
            // 고객이 접수서에 남긴 메모(현관 비번·요청사항 등) — "다 뒤지지 않게" 카드에 바로 표시. (2026-09-02 사장님)
            event.customerMemo?.takeIf { it.isNotBlank() }?.let { add("남긴 메모 · $it") }
        }
        if (detailLines.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            detailLines.forEach { line ->
                Text(
                    line,
                    fontSize = 12.5.sp,
                    color = TossTextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        // 사장님이 직접 확인 → 고객에게 "확인했어요" 문자 발송(확인 후 발송 원칙). 보내면 '확인함'으로 잠김(1회). (2026-06-28 사장님)
        val confirmedAt = event.confirmedAt
        Spacer(Modifier.height(10.dp))
        if (confirmedAt == null) {
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(TossBlue)
                    .clickable { onConfirm() }.padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("확인했어요 — 고객에게 알리기", color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold)
            }
        } else {
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                    .background(TossBlue.copy(alpha = 0.10f)).padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "확인함 · ${DateTimeUtils.formatShort(confirmedAt)} 고객에게 알림 보냄",
                    color = TossBlueDark, fontSize = 12.sp, fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * 변경/처리 이력 카드 (2026-06-30 · 간결화 2026-07-02 사장님) — 일정/금액/잔금 변경을 처리 시각과 함께 한 줄로.
 *   [고객에게 알리기] 버튼 제거(사장님: 버튼 많고 거슬림). 순수 이력 표시로 간결하게.
 */
@Composable
internal fun TimelineEventSegment(
    event: com.detailline.callfollowcrm.data.local.entity.TimelineEventEntity
) {
    // 이모지 대신 **앱이 그리는 아이콘**. 이모지는 폰마다 그림이 다르고, 📅 는 "JUL 17" 이라고
    //   적힌 미국 달력으로 나와 우리 일정과 무관한 날짜를 보여줬다. (2026-09-20 사장님)
    val (icon, title, accent) = when (event.type) {
        "schedule" -> Triple(Icons.Filled.CalendarMonth, if (event.oldValue == null) "시공일정 등록" else "시공일정 변경", AppTheme.colors.primaryText)
        "amount" -> Triple(Icons.Filled.Payments, if (event.oldValue == null) "시공금액 등록" else "시공금액 변경", AppTheme.colors.cautionText)
        "balance_paid" -> Triple(Icons.Filled.Check, "잔금 받음 처리", AppTheme.colors.doneText)
        else -> Triple(Icons.Filled.EditNote, "변경", AppTheme.colors.textSub)
    }
    val accentBg = when (event.type) {
        "schedule" -> AppTheme.colors.primaryBg
        "amount" -> AppTheme.colors.cautionBg
        "balance_paid" -> AppTheme.colors.doneBg
        else -> AppTheme.colors.surfaceMuted
    }
    val changeText = when {
        event.type == "balance_paid" -> "${event.newValue ?: "잔금"} 받음"
        event.oldValue != null && event.newValue != null -> "${event.oldValue} → ${event.newValue}"
        event.newValue != null -> event.newValue!!
        event.oldValue != null -> "${event.oldValue} → (없음)"
        else -> "-"
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .tossCardShadow(RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(AppTheme.colors.surface)
            .padding(horizontal = 11.dp, vertical = 9.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(26.dp).clip(RoundedCornerShape(8.dp)).background(accentBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(13.dp))
            }
            Spacer(Modifier.width(9.dp))
            Text(title, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = TossTextSecondary,
                modifier = Modifier.weight(1f))
            Text(DateTimeUtils.formatShort(event.createdAt), fontSize = 10.5.sp,
                color = TossTextTertiary, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.height(3.dp))
        // 값은 **검정**. 색은 왼쪽 칩에만 — 한 화면에 파랑·주황·초록 글자가 겹치면 경중이 사라진다.
        Text(changeText, fontSize = 13.5.sp, fontWeight = FontWeight.ExtraBold, color = AppTheme.colors.text,
            modifier = Modifier.padding(start = 35.dp))
        event.reason?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(2.dp))
            Text("이유: $it", fontSize = 11.5.sp, color = TossTextTertiary,
                modifier = Modifier.padding(start = 35.dp),
                maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
    }
}

/** 발행 이력 카드 (2026-07-07) — 견적서/시공접수서 발행을 타임라인에. 탭하면 다시 열람. */
@Composable
internal fun IssuedDocSegment(
    doc: com.detailline.callfollowcrm.data.local.entity.IssuedDocEntity,
    onOpen: () -> Unit,
    /** 접수서(intake)만 — "수정" 탭 시 그 내용으로 편집기 재오픈. null=수정 버튼 숨김. (2026-07-10 사장님) */
    onEdit: (() -> Unit)? = null
) {
    val isQuote = doc.kind == "quote"
    val icon = if (isQuote) Icons.Filled.ReceiptLong else Icons.Filled.Assignment
    val title = if (isQuote) "견적서 발행" else "시공접수서 발행"
    val accent = if (isQuote) AppTheme.colors.primaryText else AppTheme.colors.doneText
    val accentBg = if (isQuote) AppTheme.colors.primaryBg else AppTheme.colors.doneBg
    val issuedInteraction = remember { MutableInteractionSource() }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .pressScale(issuedInteraction)
            .tossCardShadow(RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(AppTheme.colors.surface)
            .clickable(interactionSource = issuedInteraction, indication = null) { onOpen() }
            .padding(horizontal = 11.dp, vertical = 9.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(26.dp).clip(RoundedCornerShape(8.dp)).background(accentBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(13.dp))
            }
            Spacer(Modifier.width(9.dp))
            Text(title, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = TossTextSecondary,
                modifier = Modifier.weight(1f))
            Text(DateTimeUtils.formatShort(doc.issuedAtMs), fontSize = 10.5.sp,
                color = TossTextTertiary, fontWeight = FontWeight.Medium)
        }
        val summary = buildString {
            doc.itemsText?.takeIf { it.isNotBlank() }?.let { append(it) }
            if (doc.totalWon > 0L) {
                if (isNotEmpty()) append(" · ")
                append("${java.text.NumberFormat.getNumberInstance(java.util.Locale.KOREA).format(doc.totalWon)}원")
            }
        }
        if (summary.isNotBlank()) {
            Spacer(Modifier.height(3.dp))
            Text(summary, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = accent,
                modifier = Modifier.padding(start = 20.dp), maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
        doc.memo?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(2.dp))
            Text("비고: $it", fontSize = 11.5.sp, color = TossTextTertiary,
                modifier = Modifier.padding(start = 20.dp), maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(3.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (isQuote) "누르면 견적서 다시 보기 >" else "누르면 접수 링크 열기 >",
                fontSize = 11.sp, color = TossBlue, fontWeight = FontWeight.Bold
            )
            // 이미 보낸 접수서 수정하기 — intake 만. 카드 바깥 clickable 에 안 먹히게 별도 clickable. (2026-07-10 사장님)
            if (onEdit != null) {
                Spacer(Modifier.weight(1f))
                Text(
                    "수정",
                    fontSize = 11.sp, color = TossBlue, fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onEdit() }
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
    }
}
