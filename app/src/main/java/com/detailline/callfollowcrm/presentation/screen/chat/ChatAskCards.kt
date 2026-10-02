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
//  🍃 대화에서 묻는 카드·창 — 거래·완료·잔금·알림
//
//  ChatScreen.kt 에서 **그대로 옮겨온** 조각들이다. (2026-10-02)
//  옮긴 기준: 그 파일의 **다른 private 이름을 하나도 안 쓰는** 잎(leaf) 선언.
//  같은 패키지라 `private` → `internal` 한 글자만 바뀌었고 **부르는 곳은 그대로**다.
//  (Fable 점검 ✋ — 큰 화면을 통째로 쪼개는 건 위험하니 **잎부터** 떼어낸다)
// ======================================================================

/** 변경 이력 [고객에게 알리기] 발송 전 확인 — 보낼 문자 미리보기 + [보내기]. (2026-06-30 사장님) */
@Composable
internal fun EventNotifyConfirmDialog(body: String, onSend: () -> Unit, onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        containerColor = Color.White,
        tonalElevation = 0.dp,
        onDismissRequest = onDismiss,
        title = { Text("고객에게 보낼까요?", fontWeight = FontWeight.ExtraBold, color = TossTextPrimary) },
        text = {
            Column {
                Text("아래 내용이 고객에게 문자로 발송돼요.", fontSize = 12.5.sp, color = TossTextTertiary)
                Spacer(Modifier.height(8.dp))
                Surface(color = AppTheme.colors.bg, shape = RoundedCornerShape(12.dp)) {
                    Text(
                        body, fontSize = 13.sp, color = TossTextPrimary, lineHeight = 19.sp,
                        modifier = Modifier.fillMaxWidth().padding(12.dp)
                    )
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onSend) {
                Text("보내기", color = TossBlue, fontWeight = FontWeight.ExtraBold)
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text("취소", color = TossTextTertiary)
            }
        }
    )
}

/**
 * 고객 마지막 메시지에 대한 AI 추천 답변 영역.
 * 표시 조건: ChatScreen 진입 시 messages.firstOrNull()?.sent == false 일 때만 호출.
 * 칩 탭 → 입력칸 채워짐. ↻ → 서버에 재생성 요청 + 폴링.
 *
 * 2026-05-25 expanded/collapsed 토글:
 *   - expanded = true → 헤더 + 칩 row (큰 영역)
 *   - expanded = false → 헤더만 (1줄). 헤더 탭하면 펼침.
 *   - 사장님이 타이핑 시작 (input.isNotBlank) → 호출부에서 expanded=false 로 자동 접힘.
 */
/**
 * "어느 쪽 일 하세요?" — 추천답변 바로 위 한 줄. (2026-09-16 사장님)
 *
 * 온보딩에서 업종을 묻지 않기로 하면서(문자함부터 쓰게), 대신 **답이 달라지는 그 순간**에 묻는다.
 *   · [guesses] 는 사장님 문자에서 추측한 것 — 실측으로 줄눈 884 : 타일 270 : 실리콘 132 처럼 갈린다.
 *   · 못 맞히면 빈 리스트로 온다. 그럼 **추측한 척하지 않고** '직접 고르기'만 보여준다.
 *     (사장님: "추측 못하겠으면 안 하면 되는 거야. 물어보면 되는 거지.")
 *   · ✕ 로 닫으면 다시 안 묻는다. 상단 [○ 시공 AI] 칩으로 언제든 고를 수 있다.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun TradeAskCard(
    guesses: List<String>,
    onPick: (String) -> Unit,
    onOther: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(AppTheme.colors.primaryBg)
            .padding(horizontal = 15.dp, vertical = 13.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (guesses.isEmpty()) "어느 쪽 일 하세요?" else "혹시 이 중에 사장님 일이 있나요?",
                fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = TossTextPrimary,
                modifier = Modifier.weight(1f)
            )
            // 글자 ✕ 대신 앱이 그리는 닫기 아이콘. (2026-09-22 사장님)
            Icon(
                Icons.Default.Close, "닫기", tint = TossTextTertiary,
                modifier = Modifier.clickable(onClick = onDismiss)
                    .padding(start = 8.dp, end = 2.dp).size(26.dp)
            )
        }
        Spacer(Modifier.height(3.dp))
        Text(
            "알려주시면 막내가 그 일에 맞게 답을 써요.",
            fontSize = 11.5.sp, color = TossTextTertiary
        )
        Spacer(Modifier.height(10.dp))
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            guesses.forEach { t ->
                Text(
                    t,
                    fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold, color = Color.White,
                    modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(TossBlue)
                        .clickable { onPick(t) }.padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
            Text(
                if (guesses.isEmpty()) "업종 고르기" else "다른 업종",
                fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold, color = TossBlueDark,
                modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(Color.White)
                    .clickable(onClick = onOther).padding(horizontal = 14.dp, vertical = 8.dp)
            )
        }
    }
}

/**
 * 🔨 **"시공일이 지났는데 끝났나요?" 확인 카드.** (2026-09-28 사장님)
 *
 * 「입금했습니다」 카드의 쌍둥이다 — 같은 규칙으로 움직인다:
 *   **앱은 짐작하지 않는다. 사장님이 고르신다.**
 *
 * 왜 묻기만 하나: 잘못 찍으면 **안 한 일이 「끝난 일」이 되고, 손님에게 잔금 독촉이 나간다.**
 *   못 물어보는 건 손해가 없다 — 예전처럼 홈에서 [완료]를 누르면 된다.
 *
 * 「아직이에요」를 고르면 **그 시공일로는 다시 안 묻는다.**
 *   날짜를 새로 잡으시면 키가 바뀌어 그때 다시 묻는다 — 미뤄진 일도 언젠가 끝나니까.
 */
@Composable
internal fun WorkDoneAskCard(
    workLastDayMs: Long,
    /** 아직 받을 돈. 0 이면 금액을 안 적은 현장이라 **돈 얘기를 꺼내지 않는다.** */
    outstandingWon: Long,
    /** 일도 끝났고 돈도 받았다 — 완료 + 잔금을 **한 번에** 찍는다. */
    onDoneAndPaid: () -> Unit,
    /** 일은 끝났는데 돈은 아직 — 완료만. 미수금으로 남는다. */
    onDoneOnly: () -> Unit,
    /** 그날 안 갔다 — **아무것도 안 찍는다.** */
    onNotYet: () -> Unit
) {
    val purple = Color(0xFF6B4FBB)
    val days = ((DateTimeUtils.startOfDay(System.currentTimeMillis()) - workLastDayMs)
        / DateTimeUtils.DAY_MS).toInt().coerceAtLeast(1)
    val dayLabel = java.text.SimpleDateFormat("M월 d일", java.util.Locale.KOREA)
        .format(java.util.Date(workLastDayMs))
    val asksMoney = outstandingWon > 0L
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(AppTheme.colors.primaryBg)
            .padding(horizontal = 15.dp, vertical = 13.dp)
    ) {
        Text(
            "시공일이 지났어요",
            fontSize = 13.5.sp, fontWeight = FontWeight.ExtraBold, color = purple
        )
        Spacer(Modifier.height(4.dp))
        // 💰 **돈까지 한 번에 묻는다.** (2026-09-28 사장님 "잔금 다 받았나요? 받았으면 완료처리가 자동으로")
        //   전엔 「끝났나요?」만 물어서, 「네」를 눌러도 **완료만** 찍히고 돈은 그대로였다.
        //   사장님은 정산에 또 들어가 잔금을 찍어야 했다 — 한 가지 일에 두 번 손이 갔다.
        Text(
            if (asksMoney)
                "${dayLabel}로 잡혀 있었어요 (${days}일 지남).\n" +
                    "받을 잔금 " +
                    com.detailline.callfollowcrm.util.MoneyFormatter.manwonOrWon(outstandingWon) +
                    ", 다 받으셨나요?"
            else "${dayLabel}로 잡혀 있었어요 (${days}일 지남). 이 현장 끝났나요?",
            fontSize = 12.sp, color = TossTextSecondary, lineHeight = 18.sp
        )
        Spacer(Modifier.height(11.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(
                if (asksMoney) "네, 다 받았어요" else "네, 끝났어요",
                fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold, color = Color.White,
                modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(purple)
                    .clickable(onClick = if (asksMoney) onDoneAndPaid else onDoneOnly)
                    .padding(horizontal = 14.dp, vertical = 9.dp)
            )
            if (asksMoney) {
                // 일은 끝났는데 돈은 아직 — **완료만.** 미수금으로 남아 알림이 계속 챙긴다.
                Text(
                    "아직 못 받았어요",
                    fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold, color = TossTextSecondary,
                    modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(Color.White)
                        .clickable(onClick = onDoneOnly).padding(horizontal = 14.dp, vertical = 9.dp)
                )
            } else {
                Text(
                    "아직이에요",
                    fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold, color = TossTextSecondary,
                    modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(Color.White)
                        .clickable(onClick = onNotYet).padding(horizontal = 14.dp, vertical = 9.dp)
                )
            }
        }
        // ⚠️ **안 간 날일 수도 있다.** 시공이 미뤄졌는데 날짜를 안 고친 경우가 그렇다.
        //   이걸 안 두면 「아직 못 받았어요」를 눌러 **안 한 일이 끝난 일**이 되고,
        //   그다음부터 손님에게 잔금 독촉이 나간다. 잘못 찍는 쪽이 훨씬 비싸다.
        if (asksMoney) {
            Spacer(Modifier.height(9.dp))
            Text(
                "그날 시공을 안 했어요",
                fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = TossTextTertiary,
                modifier = Modifier.clickable(onClick = onNotYet).padding(vertical = 3.dp)
            )
        }
    }
}

/**
 * "입금했습니다" 확인 카드. (2026-09-17 사장님)
 *
 * 왜 묻기만 하나: 판별이 틀리면 **안 받은 돈이 '받음'이 되고 미수금이 조용히 사라진다.**
 *   못 잡는 건 손해가 없다(예전처럼 직접 누르면 된다). 그래서 자동 처리는 하지 않는다.
 *
 * 고객이 말한 금액이 **우리가 아는 잔금과 다르면** 그 사실을 같이 보여준다 —
 * 계약금만 보냈는데 잔금 전액을 받음으로 찍으면 돈이 틀어진다.
 */
@Composable
internal fun PayClaimCard(
    saidAmountWon: Long?,
    outstandingWon: Long,
    onYes: () -> Unit,
    onNo: () -> Unit
) {
    val green = Color(0xFF0E9F62)
    val mismatch = saidAmountWon != null && saidAmountWon != outstandingWon
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(AppTheme.colors.doneBg)
            .padding(horizontal = 15.dp, vertical = 13.dp)
    ) {
        Text(
            "입금하셨다고 하네요",
            fontSize = 13.5.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF0B5E3C)
        )
        Spacer(Modifier.height(4.dp))
        Text(
            buildString {
                append("남은 잔금 ")
                append(payWonLabel(outstandingWon))
                append(" 을 받음으로 표시할까요?")
                if (mismatch) {
                    append("\n(고객은 ")
                    append(payWonLabel(saidAmountWon!!))
                    append(" 이라고 했어요 — 확인해보세요)")
                }
            },
            fontSize = 12.sp, color = Color(0xFF3B7A5E), lineHeight = 18.sp
        )
        Spacer(Modifier.height(11.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(
                "네, 받았어요",
                fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold, color = Color.White,
                modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(green)
                    .clickable(onClick = onYes).padding(horizontal = 14.dp, vertical = 9.dp)
            )
            Text(
                "아니요",
                fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold, color = TossTextSecondary,
                modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(Color.White)
                    .clickable(onClick = onNo).padding(horizontal = 14.dp, vertical = 9.dp)
            )
        }
    }
}

/**
 * P3 — 시공일 등록 직후 표시. "이 일정으로 계약금 안내문도 만들어드릴까요?".
 * [네, 만들기] = RESERVATION 템플릿 picker 띄우고 본문에 시공일 자동 prepend.
 * [등록만 하기] = 닫기. 사장님이 나중에 직접 [계약금 안내] 액션 누를 수 있음.
 */
@Composable
internal fun DepositFollowupDialog(
    scheduledMs: Long,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val dateStr = DateTimeUtils.formatScheduledDate(scheduledMs)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "시공일을 등록했어요",
                color = TossTextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        },
        text = {
            Column {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = TossBlueSoft
                ) {
                    Text(
                        "예약 일정: $dateStr",
                        color = TossBlue,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "이 일정으로 계약금 안내문도 만들어드릴까요?",
                    color = TossTextSecondary,
                    fontSize = 13.sp
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("네, 만들기", color = TossBlue, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("등록만 하기", color = TossTextSecondary)
            }
        },
        containerColor = Color.White,
        tonalElevation = 0.dp,   // 흰 창에 회색이 덧칠되는 것 끄기 (2026-09-21 사장님)
    )
}
