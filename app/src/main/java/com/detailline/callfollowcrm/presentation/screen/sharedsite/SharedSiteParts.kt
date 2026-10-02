package com.detailline.callfollowcrm.presentation.screen.sharedsite

import androidx.compose.material.icons.filled.Handshake
import com.detailline.callfollowcrm.presentation.theme.AppTheme
import com.detailline.callfollowcrm.presentation.theme.AppType
import com.detailline.callfollowcrm.presentation.theme.LightColors
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.ClickableText
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.detailline.callfollowcrm.ai.SharedSiteRepository
import com.detailline.callfollowcrm.presentation.component.CollabCommentSection
import com.detailline.callfollowcrm.ai.siteDisplayName
import com.detailline.callfollowcrm.presentation.theme.TossDivider
import com.detailline.callfollowcrm.presentation.theme.TossGrayBg
import com.detailline.callfollowcrm.presentation.theme.TossTextPrimary
import com.detailline.callfollowcrm.presentation.theme.TossTextSecondary
import com.detailline.callfollowcrm.presentation.theme.TossTextTertiary
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import com.detailline.callfollowcrm.presentation.util.keyboardPadding
import com.detailline.callfollowcrm.util.PhoneKey
import com.detailline.callfollowcrm.presentation.theme.AppShape
import com.detailline.callfollowcrm.util.DateTimeUtils

// ======================================================================
//  🍃 협업 현장의 단독 조각 — 이력·판정·메모 링크·탭
//
//  SharedSiteScreen.kt 에서 **그대로 옮겨온** 조각들이다. (2026-10-02)
//  옮긴 기준: 그 파일의 **다른 private 이름을 하나도 안 쓰는** 잎(leaf) 선언.
//  같은 패키지라 `private` → `internal` 한 글자만 바뀌었고 **부르는 곳은 그대로**다.
//  (Fable 점검 ✋ — 큰 화면을 통째로 쪼개는 건 위험하니 **잎부터** 떼어낸다)
// ======================================================================

/**
 * 협업 현장 (B = 협업자) — 프로토 collab-sites-proto.html 의 b-list / b-detail 1:1.
 *   내 고객 목록과 분리된 별도 영역. 고객 전화번호·상대 다른 고객은 절대 안 보임(벽).
 *   서버 endpoint(/api/shared/…) 대기 동안엔 목록 비어 "공유받은 현장 없음" 안내.
 */
internal val CollabPurple = LightColors.category

internal val CollabPurpleSoft = LightColors.categoryBg

internal val ProtoBlue = LightColors.primary

internal val ProtoSuccess = LightColors.done

/** 현장순 / 업체별 세그먼트 (프로토 .seg). */
@Composable
internal fun SegTabs(current: String, onSelect: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(AppTheme.colors.surfaceMuted).padding(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        listOf("date" to "현장순", "biz" to "업체별").forEach { (key, label) ->
            val on = current == key
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                    .background(if (on) Color.White else Color.Transparent)
                    .clickable { onSelect(key) }.padding(vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(label, fontSize = 13.sp, fontWeight = if (on) FontWeight.ExtraBold else FontWeight.Medium,
                    color = if (on) TossTextPrimary else TossTextTertiary)
            }
        }
    }
}

/** 1차 분리 세그먼트 — 공유받은 현장 / 내가 공유한 현장. (2026-06-18 사장님) */
@Composable
internal fun CollabTopTabs(current: String, onSelect: (String) -> Unit) {
    // 앱 어디서나 같은 탭 모양. 전엔 여기만 '회색 바탕 + 흰 알약' 이었다. (2026-09-21 사장님)
    val keys = listOf("received", "shared")
    com.detailline.callfollowcrm.presentation.component.AppTabs(
        tabs = listOf("공유받은 현장", "내가 공유한 현장"),
        selected = keys.indexOf(current).coerceAtLeast(0),
        onSelect = { onSelect(keys[it]) }
    )
}

/** 내가 공유한 현장 우→좌 swipe → '삭제'(내리기). 드러나는 버튼 눌러야 동작. (2026-06-23 사장님) */
@Composable
internal fun MySharedSwipeBox(onDelete: () -> Unit, content: @Composable () -> Unit) {
    com.detailline.callfollowcrm.presentation.component.SwipeRevealBox(onAction = onDelete, label = "삭제") { content() }
}

/** 협업 현장 카드 우→좌 swipe → 휴지통. 빨강 affordance. confirmValueChange=false 로 원위치(데이터가 카드 제거). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SharedSwipeBox(onDelete: () -> Unit, content: @Composable () -> Unit) {
    // 밀면 바로가 아니라 → 드러나는 '휴지통' 버튼을 눌러야 동작(2026-06-21 사장님). SwipeRevealBox 로 통일.
    com.detailline.callfollowcrm.presentation.component.SwipeRevealBox(
        onAction = onDelete,
        label = "휴지통"
    ) { content() }
}

/** 시공일이 '오늘보다 미래'면 true → 출발 버튼을 미리 못 누르게(그날만 누름). 날짜 없으면(0) 막지 않음. (2026-06-23 사장님) */
internal fun isBeforeScheduledDay(scheduledAtMs: Long): Boolean {
    if (scheduledAtMs <= 0L) return false
    val sched = Calendar.getInstance().apply { timeInMillis = scheduledAtMs }
    val today = Calendar.getInstance()
    fun ymd(c: Calendar) = c.get(Calendar.YEAR) * 10000 + c.get(Calendar.MONTH) * 100 + c.get(Calendar.DAY_OF_MONTH)
    return ymd(sched) > ymd(today)
}

internal fun dayLabel(ms: Long): String {
    if (ms <= 0L) return "날짜 미정"
    val cal = Calendar.getInstance().apply { timeInMillis = ms }
    val today = Calendar.getInstance()
    fun ymd(c: Calendar) = c.get(Calendar.YEAR) * 10000 + c.get(Calendar.MONTH) * 100 + c.get(Calendar.DAY_OF_MONTH)
    val diff = ymd(cal) - ymd(today)
    return when {
        diff == 0 -> "오늘"
        diff == 1 -> "내일"
        else -> SimpleDateFormat("M.d", Locale.KOREA).format(Date(ms))
    }
}

/** scheduled_at_ms 에 박힌 시각(출근시간) → "오전 9시". 자정(00:00)=미설정이면 null. 서버 time_label echo 없어도 시간 표시. */
internal fun timeOf(ms: Long): String? {
    if (ms <= 0L) return null
    val cal = Calendar.getInstance().apply { timeInMillis = ms }
    val h = cal.get(Calendar.HOUR_OF_DAY); val m = cal.get(Calendar.MINUTE)
    if (h == 0 && m == 0) return null
    val ampm = if (h < 12) "오전" else "오후"
    val h12 = if (h % 12 == 0) 12 else h % 12
    return "$ampm ${h12}시" + (if (m > 0) " ${m}분" else "")
}

@Composable
internal fun rememberSaveableShareId() =
    androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }

/**
 * 전달사항 텍스트를 그리되, 한국 전화번호(010-…, 02-…, 0507… 등)는 파란 밑줄 링크로.
 *   탭하면 다이얼러에 그 번호가 채워진 채 열림(ACTION_DIAL — 통화 권한 불필요, 사용자가 통화 누름). (2026-07-01 사장님)
 */
@Composable
internal fun LinkifiedMemo(memo: String, baseColor: Color) {
    val ctx = LocalContext.current
    val annotated = remember(memo) {
        // 0으로 시작하는 10~11자리(휴대폰/지역/안심번호). 구분자는 공백/하이픈 허용("010 8725 0878"·"010-8725-0878"·"01087250878").
        val re = Regex("0\\d{1,2}[\\s-]?\\d{3,4}[\\s-]?\\d{4}")
        buildAnnotatedString {
            var last = 0
            for (m in re.findAll(memo)) {
                if (m.range.first > last) append(memo.substring(last, m.range.first))
                pushStringAnnotation("tel", m.value.filter { it.isDigit() })
                withStyle(SpanStyle(color = Color(0xFF1A6DD8), fontWeight = FontWeight.Bold, textDecoration = TextDecoration.Underline)) {
                    append(m.value)
                }
                pop()
                last = m.range.last + 1
            }
            if (last < memo.length) append(memo.substring(last))
        }
    }
    ClickableText(
        text = annotated,
        style = TextStyle(color = baseColor, fontSize = 14.sp, lineHeight = 21.sp),
        onClick = { offset ->
            annotated.getStringAnnotations("tel", offset, offset).firstOrNull()?.let { ann ->
                com.detailline.callfollowcrm.util.PhoneDialer.open(ctx, ann.item)
            }
        }
    )
}

/**
 * 🤝 **그날 되나?** 한 줄 결론. (2026-09-30 사장님)
 *   비었으면 초록, 같은 날 일정이 있으면 노랑, **시간까지 겹치면** 주황.
 *   제일 중요한 말이라 카드 맨 위에 둔다 — 스크롤해서 찾게 하면 안 본다.
 */
@Composable
internal fun CollabVerdict(
    r: com.detailline.callfollowcrm.domain.collab.CollabDayCheck.Result,
    requestTime: String?
) {
    val free = r.kind == com.detailline.callfollowcrm.domain.collab.CollabDayCheck.Kind.FREE
    val clash = r.kind == com.detailline.callfollowcrm.domain.collab.CollabDayCheck.Kind.TIME_CLASH
    val bg = if (free) AppTheme.colors.doneBg else AppTheme.colors.cautionBg
    val fg = if (free) AppTheme.colors.doneText else AppTheme.colors.cautionText
    val head = when {
        free -> "그날은 비어 있어요"
        clash -> "⚠️ 그날 시간이 겹쳐요"
        else -> "그날 다른 현장이 있어요"
    }
    val where = r.place?.takeIf { it.isNotBlank() }
    val detail = if (free) "내 일정에 잡힌 게 없어요." else buildString {
        requestTime?.let { append(it); append(" 요청인데, ") }
        r.timeLabel?.let { append(it); append("에 ") }
        append(where ?: "다른 현장")
        append(if (clash) " 현장이 있어요." else " 현장이 있어요. 시간은 안 겹쳐요.")
    }
    Column(
        Modifier.fillMaxWidth().clip(AppShape.md).background(bg)
            .padding(horizontal = 13.dp, vertical = 11.dp)
    ) {
        Text(head, style = AppType.body, fontWeight = FontWeight.ExtraBold, color = fg)
        Spacer(Modifier.height(3.dp))
        Text(detail, style = AppType.caption, color = TossTextSecondary)
    }
}

/**
 * 🤝 **이 사장님과 얼마나 해왔나.** (2026-09-30 사장님)
 *
 *   "이 사장과 어디 현장을 같이 해왔는지? 이력들도 보이면 좋을 것 같은데?"
 *   "8월 9월 월별로 보이면 좋을듯."
 *
 * 서버를 새로 부르지 않는다 — 이미 받아둔 [SharedSiteRepository.Partner](합계)와
 * 수락해 해온 현장 목록으로 만든다. 요청 카드는 **빨리 떠야** 하는 자리다.
 *
 * ⚠️ **처음인 사장님도 말해준다.** 아무 말도 안 하면 「이력이 없는 건지 안 불러온 건지」 모른다.
 * ⚠️ 못 받은 돈은 **있을 때만** 적는다. 다 받았는데 「0원 밀림」이라 적으면 의심부터 하게 된다.
 */
@Composable
internal fun CollabHistory(
    site: SharedSiteRepository.SharedSite,
    partners: List<SharedSiteRepository.Partner>,
    doneSites: List<SharedSiteRepository.SharedSite>
) {
    val key = site.ownerPhone.filter { it.isDigit() }
    val p = remember(key, partners) {
        partners.firstOrNull { it.ownerPhone.filter { c -> c.isDigit() } == key }
    }
    // 달별 — 「8월 3곳 · 9월 5곳」. 최근 넉 달만(더 옛날은 판단에 안 쓴다).
    //   🧬 **세는 규칙은 내가 안 적는다** — [SharedSiteRepository.countedForHistory] · [ymOf] 를 쓴다.
    //   전엔 여기서 따로 셌다가 「협업 기록」 화면과 **답이 달랐다**. (2026-09-30 사장님)
    val months = remember(key, doneSites) {
        doneSites.asSequence()
            .filter {
                it.ownerPhone.filter { c -> c.isDigit() } == key &&
                    SharedSiteRepository.countedForHistory(it)
            }
            .groupBy { SharedSiteRepository.ymOf(it.scheduledAtMs) }
            .toSortedMap()
            .entries.toList().takeLast(4)
            .joinToString(" · ") { (ym, list) ->
                "${ym.substringAfter('-').trimStart('0')}월 ${list.size}곳"
            }
    }
    val count = p?.count ?: 0
    Column(
        Modifier.fillMaxWidth().clip(AppShape.md).background(Color.White)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        if (count <= 0) {
            // 처음 함께하는 분 — **나쁜 말이 아니다.** 사실만 적는다.
            Text("처음 함께하는 사장님이에요", style = AppType.label,
                fontWeight = FontWeight.ExtraBold, color = TossTextPrimary)
            Spacer(Modifier.height(2.dp))
            Text("계좌와 일당을 먼저 맞춰두면 좋아요", style = AppType.caption, color = TossTextTertiary)
        } else {
            Text(
                "이 사장님과 ${count}번 함께했어요" +
                    (if (p!!.paidTotal > 0) " · " + p.paidTotal + "만원 받음" else ""),
                style = AppType.label, fontWeight = FontWeight.ExtraBold, color = TossTextPrimary
            )
            if (months.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(months, style = AppType.caption, color = TossTextTertiary)
            }
            // 💸 🔴 **「아직 N만원 안 들어왔어요」는 안 적는다.** (2026-09-30 폰에서 확인하고 뺀 것)
            //   사장님 폰 실측: 함께한 현장 **22곳**인데 「받은 일당」은 **25만원**이었다.
            //   돈을 안 받은 게 아니라 **입금을 앱에 다 안 찍으신 것**이다.
            //   그대로 빼면 「아직 525만원 안 들어왔어요」가 뜨고,
            //   멀줦한 사장님을 **돈 안 주는 사람으로** 만든다. 그건 안 하느니만 못하다.
            //   진짜 미수를 말하려면 **입금이 꼬박꼬박 찍힐 때**에야 한다.
        }
    }
}
