package com.detailline.callfollowcrm.presentation.screen.home

import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import kotlinx.coroutines.flow.first
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.HomeWork
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.CallMissed
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.platform.LocalView
import com.detailline.callfollowcrm.presentation.util.bottomBarClearance
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.detailline.callfollowcrm.CallFollowCrmApplication
import com.detailline.callfollowcrm.presentation.component.TossBadge
import com.detailline.callfollowcrm.presentation.component.NavAppPickerDialog
import com.detailline.callfollowcrm.presentation.component.TossCard
import com.detailline.callfollowcrm.presentation.component.TossChip
import com.detailline.callfollowcrm.presentation.component.tossCardShadow
import com.detailline.callfollowcrm.presentation.theme.TossBlue
import com.detailline.callfollowcrm.presentation.theme.TossBlueDark
import com.detailline.callfollowcrm.presentation.theme.TossBlueSoft
import com.detailline.callfollowcrm.presentation.theme.TossDivider
import com.detailline.callfollowcrm.presentation.theme.TossError
import com.detailline.callfollowcrm.presentation.theme.TossGrayBg
import com.detailline.callfollowcrm.presentation.theme.TossSuccess
import com.detailline.callfollowcrm.presentation.theme.TossTextPrimary
import com.detailline.callfollowcrm.presentation.theme.TossTextSecondary
import com.detailline.callfollowcrm.presentation.theme.TossTextInfo
import com.detailline.callfollowcrm.presentation.theme.TossTextTertiary
import com.detailline.callfollowcrm.presentation.theme.TossWarning
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.graphics.Brush
import com.detailline.callfollowcrm.presentation.theme.AppTheme
import com.detailline.callfollowcrm.presentation.theme.AppSpace
import com.detailline.callfollowcrm.presentation.theme.AppSize
import com.detailline.callfollowcrm.presentation.theme.AppShape
import com.detailline.callfollowcrm.presentation.theme.AppType
import com.detailline.callfollowcrm.presentation.theme.LightColors
import com.detailline.callfollowcrm.presentation.theme.CallFollowCrmTheme
import androidx.compose.ui.tooling.preview.Preview
import com.detailline.callfollowcrm.util.DateTimeUtils
import com.detailline.callfollowcrm.util.PhoneNumberFormatter
import com.detailline.callfollowcrm.util.MoneyFormatter
import androidx.compose.material.icons.filled.ChevronRight
import com.detailline.callfollowcrm.util.PhoneKey

// ======================================================================
//  🍃 상담함의 단독 카드·창 — 완료·리마인드·알림·오늘 신규
//
//  HomeScreen.kt 에서 **그대로 옮겨온** 조각들이다. (2026-10-02)
//  옮긴 기준: 그 파일의 **다른 private 이름을 하나도 안 쓰는** 잎(leaf) 선언.
//  같은 패키지라 `private` → `internal` 한 글자만 바뀌었고 **부르는 곳은 그대로**다.
//  (Fable 점검 ✋ — 큰 화면을 통째로 쪼개는 건 위험하니 **잎부터** 떼어낸다)
// ======================================================================

/**
 * 완료한 오늘 시공 — 회색 작은 카드로 '완료 ✓' 남김(다음날 사라짐). 다시 전화 가능. (2026-06-14 사장님)
 */
@Composable
internal fun CompletedHeroJobCard(
    c: com.detailline.callfollowcrm.data.local.entity.CustomerEntity,
    onOpenCustomer: (Long) -> Unit,
    onCall: (String) -> Unit
) {
    val name = c.name?.takeIf { it.isNotBlank() } ?: PhoneNumberFormatter.format(c.phoneNumber)
    Row(
        Modifier
            .fillMaxWidth()
            .tossCardShadow(RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(AppTheme.colors.bg)
            .clickable { onOpenCustomer(c.id) }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(30.dp).clip(CircleShape).background(Color(0xFFD9DEE6)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Check, null, tint = Color(0xFF6B7280), modifier = Modifier.size(17.dp))
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            // ✓ 는 바로 왼쪽 동그라미 안 체크 아이콘이 이미 하는 말이다. (2026-09-22)
            Text("오늘 시공 완료", fontSize = 10.5.sp, fontWeight = FontWeight.ExtraBold, color = AppTheme.colors.textHint)
            Text(
                name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF6B7280),
                maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 1.dp)
            )
            c.address?.takeIf { it.isNotBlank() }?.let {
                Text(
                    shortAddr(it), fontSize = 12.sp, color = Color(0xFFAAB0BA),
                    maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
        }
        // 다시 전화 — 완료해도 오늘은 다시 걸 수 있게.
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(Color.White).clickable { onCall(c.phoneNumber) },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Call, "전화", tint = Color(0xFF6B7280), modifier = Modifier.size(17.dp))
        }
    }
}

/** 띠 껍데기 — 아이콘 칸 + 두 줄 + 오른쪽 버튼. 색만 갈아 끼운다. */
/**
 * 홈 맨 위 띠 한 장. **장마다 색이 다른 것은 일부러다.** (2026-09-21 사장님
 *   "다른 색상이어야 일정이 있다는 걸 확실히 알 것 같아")
 *
 *   1쪽  오늘 시공 있음   → **진한 초록** (오늘 할 일)
 *        오늘 시공 끝남   → 연초록
 *        오늘 시공 없음   → 흰색 (할 일 없음)
 *   2쪽  다음 시공        → **연파랑** (앞으로 올 일)
 *
 * ⚠️ 나중에 "통일하자" 며 한 색으로 맞추지 말 것 — **색이 곧 상태**다.
 */
@Composable
internal fun BandShell(
    bg: Color, fg: Color, subFg: Color, icon: String,
    line1: String, line2: String,
    action: String?, onAction: () -> Unit, onTap: () -> Unit,
    border: Color? = null,
    /** 이모지 대신 쓸 그림 — 글꼴마다 다르게 그려지는 이모지를 피할 때. */
    iconVector: androidx.compose.ui.graphics.vector.ImageVector? = null,
    /** 아이콘 칸 바탕·그림 색. 안 주면 띠 색에 맞춰 알아서. */
    iconBg: Color? = null,
    iconTint: Color? = null
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 62.dp)
            .clip(RoundedCornerShape(14.dp))
            .then(if (border != null) Modifier.border(1.dp, border, RoundedCornerShape(14.dp)) else Modifier)
            .background(bg)
            .clickable { onTap() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 아이콘 칸 — 전엔 띠 왼쪽을 **꽉 채운 네모**였다. 흰 띠에선 그 회색 사각형이
        //   *사진이 깨져서 생긴 빈 자리* 처럼 보였다. (2026-09-20 사장님 "이미지 깨진 것 같지 않니?")
        //   → 둥근 작은 칸에 담는다. 앱의 다른 아이콘들과 같은 모양이라 '그림'으로 읽힌다.
        Box(
            Modifier.padding(start = 11.dp).size(34.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(iconBg ?: if (border != null) TossGrayBg else Color(0x24FFFFFF)),
            contentAlignment = Alignment.Center
        ) {
            if (iconVector != null) Icon(
                iconVector, null,
                tint = iconTint ?: if (border != null) TossTextSecondary else fg,
                modifier = Modifier.size(18.dp)
            )
            else Text(icon, fontSize = 16.sp)
        }
        // 📢 띠 안쪽도 벌린다 — 사장님: "오늘 시공이 없어요 이부분도 글 위아래 간격이 너무 딱붙어있어"
        Column(Modifier.weight(1f).padding(start = 12.dp, top = 13.dp, bottom = 13.dp, end = 4.dp)) {
            // 띠 첫 줄 = label(14 Bold) · 둘째 줄 = caption(13 Medium). 둘째 줄은 읽는 글이다.
            Text(line1, color = fg, style = AppType.label,
                maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            Spacer(Modifier.height(5.dp))
            Text(line2, color = subFg, style = AppType.caption,
                maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
        if (action != null) {
            Text(
                action,
                color = if (border != null) TossTextSecondary else Color.White,
                style = AppType.micro,
                modifier = Modifier
                    .padding(end = 11.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (border != null) TossGrayBg else Color(0x29FFFFFF))
                    .clickable { onAction() }
                    .padding(horizontal = 10.dp, vertical = 7.dp)
            )
        } else {
            Spacer(Modifier.width(13.dp))
        }
    }
}

/**
 * 📨 문자함 안 갈래 칩 — [광고] [택배]. (2026-09-20)
 *   위 칩 줄과 **모양을 달리한다**(흰 바탕 위 회색 알약) — 같은 모양이면 칩 줄이 두 줄인 줄 안다.
 *   숫자는 회색. 여긴 손댈 일이 없는 구역이다.
 */
@Composable
internal fun BoxSubChip(label: String, count: Int, on: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            // ⚠️ 고른 것도 **진한 파랑을 안 쓴다.** 위 칩 줄과 똑같이 칠했더니
            //   파란 알약이 두 줄로 겹쳐 **칩 줄이 두 줄인 것처럼** 보였다. (2026-09-20 실기)
            //   여긴 한 단 아래니까 연한 파랑으로 — '안쪽 갈래' 로 읽힌다.
            .background(if (on) TossBlueSoft else TossGrayBg)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label, color = if (on) TossBlue else TossTextSecondary,
            fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold
        )
        if (count > 0) {
            Spacer(Modifier.width(4.dp))
            Text(
                count.toString(), color = if (on) TossBlue else TossTextTertiary,
                fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold
            )
        }
    }
}

/** 칩 하나 — 고른 건 파랑(앱 색), 숫자만 빨강. 검정은 남의 앱 색이라 안 쓴다. */
@Composable
internal fun ChipPill(
    label: String, count: Int?, on: Boolean,
    /** 셀 게 0 — 흐리게. 자리는 지킨다(숨기면 옆 칩이 밀려 손이 기억하는 위치가 흔들린다). */
    dim: Boolean = false,
    /** 숫자가 **할 일이 아닌** 칩(광고 등) — 숫자를 회색으로. 빨강은 손댈 것에만. */
    quiet: Boolean = false,
    onClick: () -> Unit
) {
    // ⚠️ 안 고른 칩을 TossGrayBg 로 했더니 **화면 배경과 같은 회색이라 안 보였다**(2026-09-20 실기).
    //   칩이 아니라 그냥 글자로 보인다. 흰 바탕 + 얇은 테두리라야 "누를 수 있는 것"으로 읽힌다.
    Row(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (on) TossBlue else Color.White)
            .then(
                when {
                    on -> Modifier
                    // 0 인 칩은 테두리까지 연하게 — 글자색만 바꿨더니 **차이가 안 보였다.** (2026-09-20 실기)
                    dim -> Modifier.border(1.dp, AppTheme.colors.surfaceMuted, RoundedCornerShape(999.dp))
                    else -> Modifier.border(1.dp, TossDivider, RoundedCornerShape(999.dp))
                }
            )
            .clickable { onClick() }
            .padding(horizontal = 13.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            color = when {
                on -> Color.White
                // 야외에서 칩 이름이 읽혀야 누를 수 있다 → 한 단계 진하게. (2026-09-20 사장님)
                dim -> AppTheme.colors.textHint
                else -> TossTextSecondary
            },
            style = AppType.label, maxLines = 1
        )
        if (count != null) {
            Spacer(Modifier.width(5.dp))
            Text(
                count.toString(),
                color = if (on) Color.White else if (quiet) TossTextTertiary else TossError,
                style = AppType.label
            )
        }
    }
}

/**
 * 내가 수락한 협업 현장(오늘 이후) — 홈 '다음 일' 카드. 협업자(B)는 자기 고객 일정이 없어도
 *   수락한 협업이 다음 일정으로 보여야 함("다음 예정 시공 없음" 통점 보완). 탭 → 협업 현장 화면. (2026-06-14)
 */
@Composable
internal fun CollabUpcomingCard(
    sites: List<com.detailline.callfollowcrm.ai.SharedSiteRepository.SharedSite>,
    onClick: () -> Unit,
    onOpenSite: (String) -> Unit
) {
    val purple = AppTheme.colors.category
    val purpleSoft = AppTheme.colors.categoryBg
    Column(
        Modifier
            .fillMaxWidth()
            .tossCardShadow(RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White)
            .border(1.dp, Color(0xFFE7E0FB), RoundedCornerShape(18.dp))
            .padding(16.dp)
    ) {
        // 헤더 — 누르면 협업 현장 전체 목록. (각 줄은 그 현장 상세로 따로 들어감) (2026-06-21 사장님)
        Row(
            Modifier.fillMaxWidth().clickable { onClick() },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(purpleSoft),
                contentAlignment = Alignment.Center
            ) { androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Filled.Handshake, null, tint = TossTextSecondary, modifier = Modifier.size(17.dp)) }
            Spacer(Modifier.width(10.dp))
            Text("협업 현장 · ${sites.size}곳", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold,
                color = purple, modifier = Modifier.weight(1f))
            Icon(Icons.Default.ChevronRight, null, tint = TossTextTertiary, modifier = Modifier.size(18.dp))
        }
        val today = DateTimeUtils.startOfDay(System.currentTimeMillis())
        sites.take(3).forEach { s ->
            val word = if (DateTimeUtils.startOfDay(s.scheduledAtMs) == today) "오늘" else relativeDayWord(s.scheduledAtMs)
            // 줄: 간략 주소(없으면 시간) · 누구 사장님 · 일당. "협업 현장"·"~과 함께" 군더더기 제거. (2026-06-20 사장님)
            val place = com.detailline.callfollowcrm.util.AddressExtractor.siteLabel(s.addr).takeIf { it.isNotBlank() }
                ?: s.timeLabel?.takeIf { it.isNotBlank() && it != "00:00" && it != "0:00" }
            val rowLine = listOfNotNull(place, bossLabel(s.ownerName), s.dailyWage?.let { "일당 ${it}만원" }).joinToString(" · ")
            // 각 줄 누르면 그 현장 상세로. (2026-06-21 사장님)
            Row(
                Modifier.fillMaxWidth()
                    .clickable(enabled = s.shareId.isNotBlank()) { onOpenSite(s.shareId) }
                    .padding(vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    word, color = purple, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(purpleSoft).padding(horizontal = 7.dp, vertical = 2.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    rowLine,
                    color = TossTextPrimary, fontSize = 13.5.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Icon(Icons.Default.ChevronRight, null, tint = TossTextTertiary, modifier = Modifier.size(15.dp))
            }
        }
    }
}

/** 협업 완료 정산 카드 — 금액·은행·계좌(읽기 쉽게)·예금주 + [계좌 복사][입금했어요]를 카드 하나에. (2026-06-14 사장님) */
@Composable
internal fun CollabSettleCard(
    up: com.detailline.callfollowcrm.ai.CollabEventCenter.CollabUpdate,
    onOpen: () -> Unit,
    onCopy: () -> Unit,
    onPaid: () -> Unit
) {
    Column(
        Modifier.fillMaxWidth().tossCardShadow(RoundedCornerShape(18.dp)).clip(RoundedCornerShape(18.dp)).background(Color.White)
            .border(1.dp, Color(0xFFE7E0FB), RoundedCornerShape(18.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("협업 작업 완료", fontSize = 14.5.sp, fontWeight = FontWeight.ExtraBold, color = TossTextPrimary)
                Text(
                    "${bossLabel(up.partnerName)} · ${up.timeLabel}",
                    fontSize = 12.sp, color = TossTextTertiary, maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Text(
                "현장 보기", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = AppTheme.colors.category,
                // 딱지는 둥근 네모(8). 알약은 누르는 것의 모양. (2026-09-20 사장님)
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(AppTheme.colors.categoryBg)
                    .clickable { onOpen() }.padding(horizontal = 11.dp, vertical = 6.dp)
            )
        }
        if (up.title.isNotBlank()) {
            Spacer(Modifier.height(7.dp))
            Text(
                "${up.title}", fontSize = 12.5.sp, color = TossTextSecondary, maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.height(12.dp))
        // 정산 박스 — 금액 + 계좌
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(TossGrayBg).padding(13.dp)) {
            up.dailyWage?.let { wage ->
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("보낼 금액", fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold, color = TossTextTertiary)
                    Spacer(Modifier.weight(1f))
                    Text("${wage}만원", fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = TossTextPrimary)
                }
                Spacer(Modifier.height(10.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(AppTheme.colors.surfacePressed))
                Spacer(Modifier.height(10.dp))
            }
            Text("입금 계좌", fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold, color = TossTextTertiary)
            Spacer(Modifier.height(3.dp))
            Text(
                buildString {
                    up.accountBank?.let { append(it); append("  ") }
                    append(up.accountNo?.let { com.detailline.callfollowcrm.presentation.component.formatAccountNo(it) } ?: (up.accountText ?: ""))
                },
                fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = TossTextPrimary
            )
            up.accountHolder?.let {
                Spacer(Modifier.height(2.dp))
                Text("예금주 $it", fontSize = 12.sp, color = TossTextTertiary)
            }
        }
        Spacer(Modifier.height(11.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(11.dp)).background(TossGrayBg)
                    .clickable { onCopy() }.padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) { Text("계좌 복사", color = TossTextSecondary, fontSize = 13.5.sp, fontWeight = FontWeight.Bold) }
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(11.dp)).background(TossBlue)
                    .clickable { onPaid() }.padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) { Text("입금했어요", color = Color.White, fontSize = 13.5.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
internal fun HeroBtn(label: String, icon: ImageVector, light: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(13.dp))
            .background(if (light) Color.White else Color.White.copy(alpha = 0.12f))
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = if (light) Color(0xFF14171F) else Color.White, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = if (light) Color(0xFF14171F) else Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

/**
 * 시공 완료 팝업 (프로토 openComplete) — 오늘 시공 히어로 [완료] 탭 시.
 *   "🎉 시공 완료 · 고생하셨습니다!" + 잔금/후기 안내 문구 + [완료처리][요청 보내기].
 *   잔금 남았으면 잔금 요청 + "후기 요청도 함께", 정산 끝났으면 후기 요청. (계좌는 prefs 미보유 → 잔금액만)
 */
@Composable
internal fun CompletionDialog(
    customer: com.detailline.callfollowcrm.data.local.entity.CustomerEntity,
    onDismiss: () -> Unit,
    onComplete: (name: String) -> Unit,
    onCompletePaid: (name: String) -> Unit,
    onSend: (phone: String, name: String, body: String, kind: String) -> Unit
) {
    val name = customer.name?.takeIf { it.isNotBlank() } ?: PhoneNumberFormatter.format(customer.phoneNumber)
    // 🔴 잔금은 **정산 단일 출처(SettlementCalc)**로 — 상세 화면과 같은 규칙(총액이 있으면 총액−계약금, stale balanceAmount 무시).
    //   전엔 customer.balanceAmount 를 직접 써서, 총액을 바꾸기 전 옛 잔금(예: 40만)이 남아 상세는 「완납」인데
    //   완료 카드는 「잔금 40만 미수」로 갈렸다. (2026-10-07 사장님 신고)
    val sRow = com.detailline.callfollowcrm.domain.settlement.SettlementCalc.rowOf(customer)
    val bal = sRow.outstanding
    val hasBal = bal > 0L
    val won = "%,d".format(bal)
    val reviewMsg = "고객님, 오늘 시공 잘 마쳤습니다 😊 만족스러우셨다면 후기 한 줄 부탁드려요! 또 필요하시면 언제든 연락주세요 :)"
    val balanceMsg = "고객님, 오늘 시공 잘 마쳤습니다 😊\n잔금은 ${won}원입니다.\n맡겨주셔서 대단히 감사합니다!"

    // 완료 흐름(2026-06-15 사장님): 잔금 남았으면 먼저 "다 받았나요?" → 네=완납 처리 / 아니요=안내문자 단계.
    //   잔금 없으면 바로 후기 문자 단계. 문자는 자유롭게 수정 가능(BasicTextField + ForceDialogResize 로 키보드 정상).
    var askedNo by remember { mutableStateOf(false) }                 // hasBal 에서 "아니요" → 문자 단계
    var msg by remember { mutableStateOf(if (hasBal) balanceMsg else reviewMsg) }
    val subtitle = when {
        hasBal && !askedNo -> "$name · 잔금 ${won}원"
        hasBal -> "$name · 잔금 ${won}원 미수"
        else -> "$name · 정산 완료"
    }

    // 프로토 .modal-card — 좌우 18 여백 + 세로 정중앙. usePlatformDefaultWidth=false + 전체화면 Box 가운데 정렬.
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
      Box(
          Modifier.fillMaxSize().padding(horizontal = 18.dp),
          contentAlignment = Alignment.Center
      ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Color.White)
                .padding(20.dp)
        ) {
            com.detailline.callfollowcrm.presentation.util.ForceDialogResize()  // 다이얼로그 키보드 가림 방지(갤S9)
            Text("시공 끝 · 고생하셨어요", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = TossTextPrimary)
            Spacer(Modifier.height(4.dp))
            Text(subtitle, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = TossBlueDark)
            Spacer(Modifier.height(14.dp))

            if (hasBal && !askedNo) {
                // ── 1단계: 잔금 다 받았는지 확인 ──
                Text("잔금 ${won}원은 다 받으셨어요?", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary)
                Spacer(Modifier.height(14.dp))
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(TossBlue)
                        .clickable { onCompletePaid(name) }.padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) { Text("네, 다 받았어요", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(TossGrayBg)
                        .clickable { askedNo = true }.padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) { Text("아니요, 아직이요", color = TossTextSecondary, fontSize = 15.sp, fontWeight = FontWeight.Bold) }
            } else {
                // ── 2단계: 안내 문자(자유 수정) + 발송/완료 ──
                Text(
                    if (hasBal) "잔금 안내 문자 — 자유롭게 고쳐서 보내세요" else "후기 요청 문자 — 자유롭게 고쳐서 보내세요",
                    fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = TossTextTertiary
                )
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(TossGrayBg).padding(13.dp)
                ) {
                    androidx.compose.foundation.text.BasicTextField(
                        value = msg,
                        onValueChange = { msg = it },
                        textStyle = androidx.compose.ui.text.TextStyle(fontFamily = com.detailline.callfollowcrm.presentation.theme.Pretendard, fontSize = 13.5.sp, color = TossTextPrimary, lineHeight = 21.sp),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(TossBlue),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(TossGrayBg)
                            .clickable { onComplete(name) }.padding(vertical = 13.dp),
                        contentAlignment = Alignment.Center
                    ) { Text("문자 없이 완료", color = TossTextSecondary, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(TossBlue)
                            .clickable { onSend(customer.phoneNumber, name, msg, if (hasBal) "잔금 요청" else "후기 요청") }
                            .padding(vertical = 13.dp),
                        contentAlignment = Alignment.Center
                    ) { Text("문자 보내기", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                }
            }
        }
      } // end center Box
    }
}

/**
 * 홈 진입 카드 (정기문자 / 시공 안내 등) — 이모지 + 라벨 + 강조 값 + chevron. N>0 일 때만 노출. (2026-06-01)
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun RemindCard(
    reminder: com.detailline.callfollowcrm.presentation.screen.home.HomeReminderUi,
    onSkip: () -> Unit,
    onSend: (String) -> Unit
) {
    // 프로토 .remind-card — 흰 카드 + 좌측 3px 앰버 inset + 라벨/이름/문구박스 + [건너뛰기][문자 보낼까요?].
    val amber = AppTheme.colors.caution
    // 2026-06-07 사장님 요청: 문구 박스를 꾹 누르면 그 자리에서 내용 수정.
    var editing by remember(reminder.body) { mutableStateOf(false) }
    var draft by remember(reminder.body) { mutableStateOf(reminder.body) }
    val editFocus = remember { FocusRequester() }
    val kb = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .tossCardShadow(RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White),
        verticalAlignment = Alignment.Top
    ) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(amber))
        Column(Modifier.weight(1f).padding(16.dp)) {
            // remind-label
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.DateRange, null, tint = Color(0xFFB8780A), modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(reminder.kindLabel, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFB8780A))
            }
            // remind-name
            Text(
                "${reminder.name} · ${reminder.whenLabel}",
                fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary,
                letterSpacing = (-0.3).sp, modifier = Modifier.padding(top = 8.dp)
            )
            // 주소 한 줄 — "이 번호가 내일 고객 맞나?" 눈으로 확인 (2026-06-21 사장님). 주소 없으면 숨김.
            if (reminder.addressLabel.isNotBlank()) {
                Text(
                    "${reminder.addressLabel}",
                    fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TossTextSecondary,
                    letterSpacing = (-0.2).sp,
                    maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            // remind-msg — 꾹 누르면 인라인 수정(activity 윈도우라 키보드 정상).
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 12.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(TossGrayBg)
                    .combinedClickable(onClick = {}, onLongClick = { editing = true })
                    .padding(12.dp)
            ) {
                if (editing) {
                    Column(Modifier.fillMaxWidth()) {
                        androidx.compose.foundation.text.BasicTextField(
                            value = draft,
                            onValueChange = { draft = it },
                            textStyle = androidx.compose.ui.text.TextStyle(
                                fontFamily = com.detailline.callfollowcrm.presentation.theme.Pretendard,
                                fontSize = 13.5.sp, color = TossTextPrimary, lineHeight = 21.sp
                            ),
                            cursorBrush = androidx.compose.ui.graphics.SolidColor(TossBlue),
                            modifier = Modifier.fillMaxWidth().focusRequester(editFocus)
                        )
                        Box(
                            Modifier.align(Alignment.End).padding(top = 8.dp)
                                .clip(RoundedCornerShape(999.dp)).background(TossBlueSoft)
                                .clickable { editing = false; kb?.hide() }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) { Text("수정 완료", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TossBlue) }
                    }
                    LaunchedEffect(Unit) { editFocus.requestFocus(); kb?.show() }
                } else {
                    Column(Modifier.fillMaxWidth()) {
                        Text(draft, fontSize = 13.5.sp, color = TossTextPrimary, lineHeight = 21.sp)
                        Text("꾹 눌러 수정", fontSize = 10.5.sp, color = TossTextTertiary,
                            modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }
            // remind-btns
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(TossGrayBg)
                        .clickable { onSkip() }.padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) { Text("건너뛰기", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TossTextSecondary) }
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(TossBlue)
                        .clickable { editing = false; kb?.hide(); onSend(draft) }.padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) { Text("문자 보낼까요?", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White) }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun InboxAlert(
    accent: Color,
    accentTint: Color,
    icon: ImageVector,
    title: String,
    tagText: String?,
    tagBg: Color,
    tagFg: Color,
    sub: String,
    goLabel: String,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    // 프로토 .team-alert — 흰 카드 + 좌측 4px 강조선 + 아이콘박스 + 제목/태그 + 부제 + go 칩.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .tossCardShadow(RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White)
            .border(1.dp, TossDivider, RoundedCornerShape(14.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(4.dp).fillMaxHeight().background(accent))
        Row(
            modifier = Modifier.weight(1f).padding(start = 14.dp, top = 13.dp, end = 14.dp, bottom = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(accentTint),
                contentAlignment = Alignment.Center
            ) { Icon(icon, null, tint = accent, modifier = Modifier.size(19.dp)) }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = TossTextPrimary)
                    if (!tagText.isNullOrEmpty()) {
                        Spacer(Modifier.width(6.dp))
                        Box(
                            Modifier.clip(RoundedCornerShape(8.dp)).background(tagBg).padding(horizontal = 7.dp, vertical = 2.dp)
                        ) { Text(tagText, fontSize = 10.5.sp, fontWeight = FontWeight.ExtraBold, color = tagFg) }
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(sub, fontSize = 12.sp, color = TossTextSecondary, maxLines = 2)
            }
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.clip(RoundedCornerShape(12.dp)).background(accentTint).padding(horizontal = 13.dp, vertical = 7.dp)
            ) { Text(goLabel, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = accent) }
        }
    }
}

/**
 * 카드 펼침 영역의 액션 버튼 — 아이콘 + 라벨 세로 배치. 에이닷 벤치마킹.
 * disabled 면 회색 + 클릭 무시.
 */
@Composable
internal fun InlineActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val tint = if (enabled) TossBlue else TossTextTertiary
    val labelColor = if (enabled) TossTextPrimary else TossTextTertiary
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Icon(icon, contentDescription = label, tint = tint)
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = labelColor,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * 정보성 배너(자동답장 보냄 등) 우→좌 swipe → 정리(dismiss). 회색 "정리" affordance.
 *   SpamSwipeBox 와 동일 패턴: confirmValueChange=false 로 원위치 복귀 + 데이터 흐름(dismissed id)이 카드 제거.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DismissSwipeBox(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    // 밀면 바로가 아니라 → 드러나는 버튼을 눌러야 정리(2026-06-21 사장님). SwipeRevealBox 로 통일.
    com.detailline.callfollowcrm.presentation.component.SwipeRevealBox(
        onAction = onDismiss,
        label = "정리",
        containerColor = TossBlue,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
    ) { content() }
}

/**
 * 카드 좌측 36dp 라운드 아이콘 — 통화/문자/부재중 한 눈에 식별.
 *  - 문자만 = 파랑 배경 + 💬 (TossBlue)
 *  - 부재중/거절 = 빨강 배경 + 부재중 아이콘 (TossError)
 *  - 수신/발신/통화 = 회색 배경 + 방향 화살표
 *  - 수동 등록 = 회색 배경 + 편집 아이콘
 *
 * 토스 스타일: 절제된 컬러 + 단색 아이콘 + soft 배경.
 * 사장님 피드백 (2026-05-25): "전화랑 문자메세지랑 구분이 잘 안 가" → 본 indicator 도입.
 */
@Composable
internal fun CallTypeIndicator(callType: String) {
    val (bg, fg, icon) = when (callType) {
        HomeViewModel.CALL_TYPE_SMS_ONLY ->
            Triple(TossBlueSoft, TossBlue, Icons.AutoMirrored.Filled.Chat)
        "MISSED", "REJECTED" ->
            Triple(AppTheme.colors.unpaidBg, TossError, Icons.Default.CallMissed)
        "INCOMING" ->
            Triple(AppTheme.colors.surfaceMuted, TossTextSecondary, Icons.Default.CallReceived)
        "OUTGOING" ->
            Triple(AppTheme.colors.surfaceMuted, TossTextSecondary, Icons.Default.CallMade)
        "MANUAL" ->
            Triple(AppTheme.colors.surfaceMuted, TossTextSecondary, Icons.Default.Edit)
        else ->
            Triple(AppTheme.colors.surfaceMuted, TossTextSecondary, Icons.Default.Call)
    }
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .size(36.dp)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(bg),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = callTypeLabel(callType),
            tint = fg,
            modifier = Modifier.size(18.dp)
        )
    }
}

/**
 * 프로토 .ai-badge (renderAiBadge) — 상담함 앱바 오른쪽 "{업종} AI" 알약.
 *   그라데이션 배경 + 초록 점 + ✨ + "{업종} AI" (예: "줄눈 AI"). 탭 = AI 설명/서버 상태.
 *   점 색으로 서버 연결 상태를 은근히 표시(정상=초록/끊김=빨강/확인중=회색).
 *   (확인중 노랑→회색: 앱 켜자마자 노랑/주황 점을 '서버 안 됨'으로 오인하는 보고 多. 2026-06-22 사장님)
 */
@Composable
internal fun AiBadge(trade: String, alive: Boolean?, onClick: () -> Unit) {
    val dot = when (alive) {
        true -> TossSuccess
        false -> TossError
        null -> TossTextTertiary  // 확인 중 = 옅은 회색. 빨강은 ServerHealthMonitor 가 연속 2회 실패해야만 띄움.
    }
    val label = "$trade AI"
    Row(
        modifier = Modifier
            .background(
                Brush.linearGradient(listOf(AppTheme.colors.primaryBg, AppTheme.colors.categoryBg)),
                RoundedCornerShape(999.dp)
            )
            .border(1.dp, Color(0xFFE0E7FB), RoundedCornerShape(999.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 프로토 ai-dot — 6px + box-shadow 0 0 0 3px rgba(.16) glow 링. (dot 색=서버상태 유지, glow 도 맞춤)
        Box(
            Modifier.size(12.dp).clip(CircleShape).background(dot.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(dot))
        }
        Spacer(Modifier.width(5.dp))
        Icon(Icons.Default.AutoAwesome, null, tint = TossBlue, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = TossBlueDark)
    }
}

/**
 * 프로토 today-new-slot (renderTodayNew) 그대로 — "오늘 신규 문의 N통 / 새 번호 기준 · 어제 M통" + ▲▼.
 *   ▲(초록)=어제보다 늘어남, ▼(빨강)=줄어듦, -(회색)=같음.
 */
@Composable
internal fun TodayNewCard(todayNew: Int, yesterdayNew: Int, onClick: () -> Unit) {
    val d = todayNew - yesterdayNew
    val deltaText = when { d > 0 -> "▲ $d"; d < 0 -> "▼ ${-d}"; else -> "-" }
    val deltaFg = when { d > 0 -> Color(0xFF0A8F44); d < 0 -> TossError; else -> TossTextTertiary }
    val deltaBg = when { d > 0 -> AppTheme.colors.doneBg; d < 0 -> AppTheme.colors.unpaidBg; else -> TossGrayBg }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .tossCardShadow(RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White, RoundedCornerShape(14.dp))
            .border(1.dp, TossDivider, RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(36.dp).background(TossBlueSoft, RoundedCornerShape(11.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text("+", color = TossBlue, fontSize = 21.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Row {
                Text("오늘 신규 문의 ", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary)
                Text("${todayNew}통", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TossBlue)
            }
            Spacer(Modifier.height(2.dp))
            Text("새 번호 기준 · 어제 ${yesterdayNew}통", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = TossTextTertiary)
        }
        Box(
            Modifier.background(deltaBg, RoundedCornerShape(999.dp)).padding(horizontal = 10.dp, vertical = 5.dp)
        ) {
            Text(deltaText, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = deltaFg)
        }
    }
}

@Composable
internal fun ChipEmpty(chip: String) {
    val (title, sub) = chipEmptyText(chip)
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TossTextSecondary)
        if (sub != null) {
            Spacer(Modifier.height(6.dp))
            Text(sub, fontSize = 12.sp, color = TossTextTertiary)
        }
    }
}

/** 프로토 avatarHtml — 이름 있으면 컬러 이니셜 원, 없으면 회색 사람 아이콘. */
/**
 * AI 가 쓴 글 표시 — 요약 줄 앞의 작은 반짝임. (2026-09-20 사장님)
 *   글자 ✨ 를 쓰면 갤럭시에서 남색 밤하늘 타일로 그려져 목록이 어두워진다.
 */
@Composable
internal fun AiMark() {
    Icon(
        Icons.Filled.AutoAwesome, contentDescription = null,
        tint = AppTheme.colors.textHint,
        modifier = Modifier.size(12.dp).padding(end = 0.dp)
    )
    Spacer(Modifier.width(5.dp))
}

@Composable
internal fun Avatar(name: String?, index: Int) {
    if (name.isNullOrBlank()) {
        Box(
            Modifier.size(44.dp).clip(androidx.compose.foundation.shape.CircleShape).background(TossGrayBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Person, null, tint = TossTextTertiary, modifier = Modifier.size(20.dp))
        }
    } else {
        val (bg, fg) = AV_TINTS[index % AV_TINTS.size]
        Box(
            Modifier.size(44.dp).clip(androidx.compose.foundation.shape.CircleShape).background(bg),
            contentAlignment = Alignment.Center
        ) {
            Text(name.trim().first().toString(), color = fg, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

/** 프로토 renderWaiting 빈 상태(.empty-mascot) — 막내 + 말풍선(em-speech) + 보조문구(em-sub). */
@Composable
internal fun WaitingEmptyMascot(newUser: Boolean = false) {
    Box(
        Modifier.fillMaxWidth().padding(top = 30.dp, bottom = 40.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            com.detailline.callfollowcrm.presentation.component.Mascot(sizeDp = 80.dp)
            Spacer(Modifier.height(10.dp))
            // .em-speech — 파란 pill + 위쪽 삼각 꼬리(::after 11x11 rotate45 top:-6).
            //   배경 var(--blue-tint) #EEF4FF / 글자 var(--blue-dark) #1B64DA / weight 800 / 14sp.
            //   padding 10x16, border-radius 14.
            Box(contentAlignment = Alignment.TopCenter) {
                // 삼각 꼬리: 11dp 정사각 45° → 윗절반만 pill 위로 노출. 같은 색이라 ▲ 만 보임.
                Box(
                    Modifier
                        .offset(y = (-5).dp)
                        .size(11.dp)
                        .rotate(45f)
                        .background(TossBlueSoft)
                )
                Box(
                    Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(TossBlueSoft)
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Text(
                        // 완전 신규(대화 이력 0)에게 "다 끝냈어요"는 앞뒤가 안 맞음 → 환영 문구로 분기. 기존 문구(프로토)는 그대로.
                        // 👏 👋 도 뺐다 — 폰마다 다르게 그려진다(앱 전체 규칙). (2026-09-22 사장님)
                        if (newUser) "사장님, 오늘도 잘 부탁드려요" else "사장님, 오늘 상담 다 끝냈어요",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = TossBlueDark
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            // .em-sub — 12.5sp / weight 600 / color t3 (#9AA3AF) / margin-top 12.
            Text(
                "새 문의가 오면 막내가 바로 알려드릴게요",
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = TossTextTertiary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}
