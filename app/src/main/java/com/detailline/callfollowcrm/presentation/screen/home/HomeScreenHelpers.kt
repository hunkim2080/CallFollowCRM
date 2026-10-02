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
//  🍃 상담함의 작은 도우미 — 글자 만들기·전화·날짜 말·짧은 주소
//
//  HomeScreen.kt 에서 **그대로 옮겨온** 조각들이다. (2026-10-02)
//  옮긴 기준: 그 파일의 **다른 private 이름을 하나도 안 쓰는** 잎(leaf) 선언.
//  같은 패키지라 `private` → `internal` 한 글자만 바뀌었고 **부르는 곳은 그대로**다.
//  (Fable 점검 ✋ — 큰 화면을 통째로 쪼개는 건 위험하니 **잎부터** 떼어낸다)
// ======================================================================

/**
 * 업데이트 받기 — **Play 스토어 앱의 우리 앱 페이지**를 바로 연다. 사장님은 [업데이트] 한 번만 누르면 끝. (2026-09-12 사장님)
 *
 * 왜 Play 인가: 앱이 Play 로 설치되면 구글이 재서명하므로, 예전처럼 si0in.kr 에서 받은 APK 는
 *   서명이 안 맞아 **덮어쓰기 설치가 아예 거부**된다(2026-09-03 실측). 업데이트 경로는 Play 가 유일.
 *
 * 순서: ① market:// (Play 앱이 바로 뜸) → ② play.google.com (Play 앱 없으면 브라우저)
 *   → ③ si0in.kr/install (Play 자체가 없는 기기 대비 최후 폴백).
 */
internal fun openInstallPage(context: android.content.Context) {
    val pkg = context.packageName
    // ① Play 앱 직행 — 설치된 Play 로만 열리게 setPackage 고정(브라우저 선택창 안 뜸).
    val market = android.content.Intent(
        android.content.Intent.ACTION_VIEW,
        android.net.Uri.parse("market://details?id=$pkg")
    ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        .setPackage("com.android.vending")
    if (runCatching { context.startActivity(market); true }.getOrDefault(false)) return

    // ② Play 웹 페이지 (Play 앱이 없거나 막힌 기기)
    val web = android.content.Intent(
        android.content.Intent.ACTION_VIEW,
        android.net.Uri.parse("https://play.google.com/store/apps/details?id=$pkg")
    ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    if (runCatching { context.startActivity(web); true }.getOrDefault(false)) return

    // ③ 최후 폴백 — 예전 설치 페이지(Play 미탑재 기기용).
    runCatching {
        context.startActivity(
            android.content.Intent(
                android.content.Intent.ACTION_VIEW,
                android.net.Uri.parse("https://si0in.kr/install")
            ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

/**
 * 앱을 **새로 켠 것인지** 알려주는 표식. (2026-09-24 사장님)
 *
 * 전역 변수는 앱 프로세스와 함께 태어나고 함께 죽는다 — 그래서 이 값이 아직 true 면
 * "이번 실행에서 상담함을 처음 여는 것" 이다. 한 번 쓰고 false 로 눕힌다.
 *
 * 왜 필요한가: 칩은 `rememberSaveable` 이라 **앱을 닫아도 남는다.** [오늘 신규] 를 켠 채 닫으면
 *   다음에 열 때 그날 새 문의가 0 이라 목록이 텅 비고, 사장님 눈엔 **문자가 다 날아간 화면**이다.
 */
internal var homeChipFreshLaunch = true

/** "다음 · 9/22(화) 09:00 · 협업 · 안산" — 다음이 협업일 때의 띠 둘째 줄. (2026-09-21) */
internal fun collabNextLine(s: com.detailline.callfollowcrm.ai.SharedSiteRepository.SharedSite): String {
    val d = s.scheduledAtMs
    if (d <= 0L) return "다음 · 협업 · 날짜 미정"
    val t = s.timeLabel?.takeIf { it.isNotBlank() }?.let { " $it" } ?: ""
    val region = com.detailline.callfollowcrm.util.RegionName.shortRegion(s.addr)
    return "다음 · " + DateTimeUtils.formatScheduledDate(d) + t + " · 협업" + (region?.let { " · $it" } ?: "")
}

/** "다음 · 9/28(월) 오후 1시 · 동대문" — 띠 둘째 줄. */
internal fun nextLine(c: com.detailline.callfollowcrm.data.local.entity.CustomerEntity): String {
    val d = c.scheduledWorkDate ?: return "다음 시공"
    val when0 = DateTimeUtils.formatScheduledDate(d)
    val t = c.scheduledWorkMinutes?.let { " " + DateTimeUtils.formatWorkMinutes(it) } ?: ""
    val region = com.detailline.callfollowcrm.util.RegionName.shortRegion(c.address)
    return "다음 · " + when0 + t + (region?.let { " · $it" } ?: "")
}

/** 협업 상대 표기 — "사업자명 사장님". 여러 협업자도 안 헷갈리게. 이미 '사장' 들어가면 그대로. (2026-06-14) */
internal fun bossLabel(name: String): String = when {
    name.isBlank() -> "협업 사장님"
    name.contains("사장") -> name
    else -> "$name 사장님"
}

/** 프로토 shortAddr — 주소에서 "구/시" + "동" 만 추려 짧게. 없으면 "주소 미입력". */
/**
 * '다음 시공' 한 줄 — **어디로 가는지**를 먼저 말한다. (2026-09-18 사장님)
 *   주소가 있으면 주소(동·호수까지), 없으면 그때만 이름·번호로 대신한다.
 *   `shortAddr`(구·동만) 는 오늘 시공 다크 카드용이라 여기선 안 쓴다 — "화성시" 만 봐선 어딘지 모른다.
 */
internal fun nextJobHeadline(j: com.detailline.callfollowcrm.data.local.entity.CustomerEntity): String {
    val addr = j.address?.trim()?.takeIf { it.isNotBlank() }
    if (addr != null) return addr.removePrefix("경기 ").removePrefix("서울 ").removePrefix("인천 ")
    return j.name?.takeIf { it.isNotBlank() } ?: PhoneNumberFormatter.format(j.phoneNumber)
}

/** 주소를 크게 쓴 줄 아래 작게 붙는 '누구' — 주소가 없으면(이미 이름을 썼으면) 안 붙인다. */
internal fun nextJobWho(j: com.detailline.callfollowcrm.data.local.entity.CustomerEntity): String? {
    if (j.address.isNullOrBlank()) return null
    return j.name?.takeIf { it.isNotBlank() } ?: PhoneNumberFormatter.format(j.phoneNumber)
}

internal fun shortAddr(a: String?): String {
    if (a.isNullOrBlank()) return "주소 미입력"
    val gu = Regex("([가-힣]+[구시])").find(a)?.value
    val dong = Regex("([가-힣]+동)").find(a)?.value
    val parts = listOfNotNull(gu, dong)
    return if (parts.isNotEmpty()) parts.joinToString(" ") else a
}

/** 프로토 word — 다음 시공일이 오늘 기준 내일/모레/그 외 "M/D". */
internal fun relativeDayWord(epoch: Long, now: Long = System.currentTimeMillis()): String {
    val today = DateTimeUtils.startOfDay(now)
    val target = DateTimeUtils.startOfDay(epoch)
    val diff = ((target - today) / DateTimeUtils.DAY_MS).toInt()
    val cal = java.util.Calendar.getInstance().apply { timeInMillis = target }
    val md = "${cal.get(java.util.Calendar.MONTH) + 1}/${cal.get(java.util.Calendar.DAY_OF_MONTH)}"
    return when (diff) {
        1 -> "내일($md)"
        2 -> "모레($md)"
        else -> md
    }
}

internal fun callTypeLabel(raw: String): String = when (raw) {
    "INCOMING" -> "수신"
    "OUTGOING" -> "발신"
    "MISSED" -> "부재중"
    "REJECTED" -> "거절"
    "MANUAL" -> "수동 등록"
    HomeViewModel.CALL_TYPE_SMS_ONLY -> "문자만"
    else -> "통화"
}

/**
 * 칩을 켰는데 아무것도 없을 때 할 말. (2026-09-20 실기)
 *
 * "여기 아무도 없어요" 하나로 때우면 **왜 비었는지**를 모른다. 특히 [미수] 는 글자조차 없어서
 * 앱이 죽은 줄 아신다. 칩마다 *지금 무엇이 없는지* 와 *언제 여기 채워지는지* 를 같이 말한다.
 */
internal fun chipEmptyText(chip: String): Pair<String, String?> = when (chip) {
    "today" -> "오늘 새로 온 문의가 없어요" to "저장 안 된 번호에서 연락이 오면 여기 쌓여요"
    "wait" -> "잡혀 있는 시공이 없어요" to "날짜를 잡으면 여기 모여요"
    "owe" -> "기다리는 잔금이 없어요" to "시공이 끝났는데 잔금이 남으면 여기 떠요"
    "as" -> "A/S 해드릴 손님이 없어요" to null
    "done" -> "시공을 끝낸 손님이 아직 없어요" to null
    else -> "여기 아무도 없어요" to null
}

/** "540,000원" 은 딱지에 넣기엔 길다 → "54만원". 딱 떨어지지 않으면 원래대로. */
internal fun shortWon(won: Long): String =
    if (won >= 10_000L && won % 10_000L == 0L) "${won / 10_000L}만원" else MoneyFormatter.won(won)

internal fun dialHome(context: android.content.Context, phone: String) {
    // ☎️ 전화 거는 자리는 **한 곳**이다 — 거는 번호를 기억해 두어야
    //   통화가 시작될 때 그 손님 카드를 띄운다. (2026-09-30 사장님)
    com.detailline.callfollowcrm.util.PhoneDialer.open(context, phone)
}

/**
 * 아바타 원 — **한 색.** (2026-09-20 사장님)
 *   전엔 다섯 색이 번갈아 붙어 김=파랑, 박=초록 … 처럼 보였다. 그런데 그 색에는 **뜻이 없다**
 *   (이름 순서일 뿐). 색은 **상태에만** 쓴다 — 완료는 초록, 미수는 빨강처럼.
 *   사람마다 색이 다르면 그 규칙이 흐려진다.
 *   ⚠️ 목록은 리스트가 5색을 돌려 쓰던 자리라 한 칸짜리로 남겨 둔다(호출부 그대로).
 */
internal val AV_TINTS = listOf(
    LightColors.primaryBg to LightColors.primaryText,
)

/** 최근 대화 줄에서 쓰는 날짜 포맷 — 줄마다 새로 만들면 낭비라 하나만 둔다. (2026-09-15) */
internal val RECENT_MD_FORMAT = java.text.SimpleDateFormat("M/d", java.util.Locale.KOREAN)
