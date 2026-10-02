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
//  🍃 대화에서 견적 만들 때 쓰는 조각 — 달력 목록·딱지·버튼
//
//  ChatScreen.kt 에서 **그대로 옮겨온** 조각들이다. (2026-10-02)
//  옮긴 기준: 그 파일의 **다른 private 이름을 하나도 안 쓰는** 잎(leaf) 선언.
//  같은 패키지라 `private` → `internal` 한 글자만 바뀌었고 **부르는 곳은 그대로**다.
//  (Fable 점검 ✋ — 큰 화면을 통째로 쪼개는 건 위험하니 **잎부터** 떼어낸다)
// ======================================================================

/** 프로토 .seg .sg — 보내는 방식 탭. */
/**
 * 시트 안 라벨 줄 — **왼쪽은 이름, 오른쪽은 늘 지금 고른 값.** (2026-09-24 사장님)
 *
 * 전엔 같은 자리에 시공일은 **값**("미정 · 날짜를 골라주세요"),
 * 시공 기간은 **설명**("며칠 걸리는 공사인지")이 와서, 눈이 매번
 * "여기가 뭘 보여주는 자리지?" 하고 판단해야 했다.
 * 이제 오른쪽은 언제나 값이라 **스크롤하면서도 뭘 골랐는지 보인다.**
 *
 * 그리고 같은 라벨 코드가 여섯 군데 복사돼 있던 것을 여기 하나로 모았다.
 */
@Composable
internal fun EstLabelRow(name: String, value: String, dim: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(name, style = AppType.label, color = TossTextTertiary)
        Spacer(Modifier.weight(1f))
        Text(
            value,
            style = AppType.body.copy(fontWeight = if (dim) FontWeight.Bold else FontWeight.ExtraBold),
            color = if (dim) TossTextTertiary else TossTextPrimary,
            maxLines = 1
        )
    }
}

/** 프로토 .sheet-cta — 가득 찬 파란 버튼(filled) / 회색 보조 버튼. */
@Composable
internal fun EstSheetCta(text: String, enabled: Boolean, filled: Boolean, onClick: () -> Unit) {
    val bg = if (filled) (if (enabled) TossBlue else TossDivider) else TossGrayBg
    val fg = if (filled) Color.White else TossBlue
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier.fillMaxWidth()
            .pressScale(interaction)
            // 프로토 .sheet-cta box-shadow:0 8px 18px rgba(49,130,246,.3) — 활성 파란 CTA만 파란 그림자.
            .then(if (filled && enabled) Modifier.shadow(10.dp, RoundedCornerShape(14.dp), spotColor = TossBlue, ambientColor = TossBlue) else Modifier)
            .clip(RoundedCornerShape(14.dp)).background(bg)
            .clickable(interactionSource = interaction, indication = null) { onClick() }
            .padding(vertical = 15.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = fg, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

/**
 * ↕️ 차례 바꾸기 목록 — 「순서 바꾸기」를 누른 동안만 나온다. (2026-09-27 사장님)
 *   가격표 화면([PricingItemsScreen])과 **같은 손놀림**이다: ≡ 를 잡고 위아래로.
 *   끄는 동안은 여기 목록으로 바로 반응하고, **손을 떼야** 저장한다 —
 *   한 칸 올라갈 때마다 쓰면 목록이 다시 그려지며 손가락을 놓친다.
 *   이 화면엔 누를 것이 없으므로 **줄 어디를 잡아도** 끌린다(가격표는 왼쪽 48dp 만 — 거긴 스크롤이 있다).
 */
@Composable
internal fun EstOrderList(
    items: List<com.detailline.callfollowcrm.data.local.entity.PricingItemEntity>,
    onDone: (List<Long>) -> Unit
) {
    val ids = items.map { it.id }
    val orderState = remember(ids) { mutableStateOf(ids) }
    val dragId = remember { mutableStateOf<Long?>(null) }
    val dragDy = remember { mutableStateOf(0f) }
    val rowH = androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateMapOf<Long, Int>()
    }
    val byId = items.associateBy { it.id }
    Column(
        Modifier.fillMaxWidth().pointerInput(ids) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                var top = 0f
                var hit: Long? = null
                for (id2 in orderState.value) {
                    val hh = (rowH[id2] ?: 0).toFloat()
                    if (down.position.y in top..(top + hh)) { hit = id2; break }
                    top += hh
                }
                val id = hit ?: return@awaitEachGesture
                dragId.value = id
                dragDy.value = 0f
                down.consume()
                var going = true
                while (going) {
                    val ev = awaitPointerEvent()
                    val ch = ev.changes.firstOrNull { it.id == down.id }
                    if (ch == null || !ch.pressed) going = false else {
                        dragDy.value += ch.positionChange().y
                        ch.consume()
                        // 셈은 한 곳에서만 — 가격표 화면도 같은 것을 쓴다. (DragReorderTest)
                        val cur = orderState.value
                        val i = cur.indexOf(id)
                        val r = com.detailline.callfollowcrm.util.DragReorder.step(
                            cur, id, dragDy.value,
                            upH = if (i > 0) (rowH[cur[i - 1]] ?: 0) else 0,
                            dnH = if (i < cur.lastIndex) (rowH[cur[i + 1]] ?: 0) else 0
                        )
                        orderState.value = r.order
                        dragDy.value = r.dy
                    }
                }
                dragId.value = null
                dragDy.value = 0f
                onDone(orderState.value)
            }
        }
    ) {
        orderState.value.forEach { rowId ->
            val row = byId[rowId] ?: return@forEach
            androidx.compose.runtime.key(rowId) {
                Row(
                    Modifier
                        .onSizeChanged { rowH[rowId] = it.height }
                        .zIndex(if (dragId.value == rowId) 1f else 0f)
                        .offset {
                            androidx.compose.ui.unit.IntOffset(
                                0, if (dragId.value == rowId) dragDy.value.toInt() else 0
                            )
                        }
                        .fillMaxWidth()
                        .background(
                            if (dragId.value == rowId) AppTheme.colors.surfaceMuted else Color.Transparent
                        )
                        .padding(vertical = AppSpace.s12),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("≡", style = AppType.headline, color = TossTextTertiary,
                        modifier = Modifier.padding(horizontal = AppSpace.s12))
                    Text(
                        row.title, style = AppType.body, color = TossTextPrimary,
                        modifier = Modifier.weight(1f), maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    // 🏷️ 여기서도 같은 말로 — 가격표에서 「할인」을 만들 때 바로 보인다.
                    Text(
                        com.detailline.callfollowcrm.domain.quote.QuoteMoney.label(row.title, row.price),
                        style = AppType.label,
                        color = if (com.detailline.callfollowcrm.domain.quote.QuoteMoney.isDiscount(row.title)) TossError
                            else if (com.detailline.callfollowcrm.domain.quote.QuoteMoney.isService(row.title, row.price)) TossSuccess
                            else TossTextSecondary,
                        modifier = Modifier.padding(horizontal = AppSpace.s12)
                    )
                }
            }
        }
    }
}
