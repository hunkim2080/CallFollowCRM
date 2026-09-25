package com.detailline.callfollowcrm.presentation.screen.stats

import com.detailline.callfollowcrm.presentation.theme.AppTheme
import com.detailline.callfollowcrm.presentation.theme.AppType
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.launch
import com.detailline.callfollowcrm.presentation.theme.AppShape
import com.detailline.callfollowcrm.presentation.theme.AppSpace
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.detailline.callfollowcrm.presentation.component.Mascot
import com.detailline.callfollowcrm.presentation.component.tossCardShadow
import com.detailline.callfollowcrm.presentation.component.pressScale
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import com.detailline.callfollowcrm.presentation.theme.TossBlue
import com.detailline.callfollowcrm.presentation.theme.TossBlueDark
import com.detailline.callfollowcrm.presentation.theme.TossDivider
import com.detailline.callfollowcrm.presentation.theme.TossError
import com.detailline.callfollowcrm.presentation.theme.TossGrayBg
import com.detailline.callfollowcrm.presentation.theme.TossSuccess
import com.detailline.callfollowcrm.presentation.theme.TossTextPrimary
import com.detailline.callfollowcrm.presentation.theme.TossTextSecondary
import com.detailline.callfollowcrm.presentation.theme.TossTextInfo
import com.detailline.callfollowcrm.presentation.theme.TossTextTertiary

/** 인증샷 모양 — 이름이 길어서 여기서 한 번만 줄인다. */
private typealias ShotShape = com.detailline.callfollowcrm.util.RecordShot.Shape

/**
 * 통계 탭 — 프로토 `s-stats` 1:1 (2026-06-02 재구성).
 *
 * 순서(프로토 markup 그대로):
 *   stats-hero(인사·현장 N곳·작년 대비·페이스 배지[모이는 중]) → stats-mascot →
 *   stat-grid(다녀온 현장/받은 문의/시공 전환율/보낸 답장) → sec-sub "문의 추이 · 전기간 비교" →
 *   period-toggle + 추이 panel + 시장 비교[모이는 중] → "이번 달 시공 종류" → 타입 panel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    viewModel: StatsViewModel,
    onOpenVisited: () -> Unit = {},
    /** 현장 줄을 누르면 그 손님 상세로. (2026-09-24 사장님 "덕양 탭을 누르니까 9월 전체가 나오는데") */
    onOpenCustomer: (Long) -> Unit = {},
    /** 완료를 안 누른 곳**만** 보여주러. (2026-09-25 "클릭하면 안한것만 나오면 찾기편한데") */
    onOpenTodo: () -> Unit = onOpenVisited,
    /** 주소가 없는 곳**만** 보여주러. */
    onOpenNoAddr: () -> Unit = onOpenVisited
) {
    val s by viewModel.state.collectAsState()
    val trend by viewModel.trend.collectAsState()
    val rec by viewModel.myRecord.collectAsState()

    // 되돌릴 수 없는 일엔 **되돌릴 길**이 있어야 한다. (2026-09-25 기본 UX 점검)
    val snackbar = remember { androidx.compose.material3.SnackbarHostState() }
    // 손가락으로 맞춘 확대 — **지도와 인증샷이 같이 쓴다**(보이던 그대로 담으려고).
    var mapZoom by remember(rec.dots) { mutableStateOf(1f) }
    var mapPanX by remember(rec.dots) { mutableStateOf(0f) }
    var mapPanY by remember(rec.dots) { mutableStateOf(0f) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    Scaffold(
        containerColor = TossGrayBg,
        snackbarHost = { androidx.compose.material3.SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets.statusBars.add(WindowInsets(top = 10.dp)),
                title = {
                    // 부제로 헤더에 무게 → 무거운 파란 카드에 제목이 안 눌림. (2026-08-02 사장님 A안 승인)
                    Column {
                        Text("내 기록", fontSize = 23.sp, fontWeight = FontWeight.ExtraBold, color = TossTextPrimary, letterSpacing = (-0.6).sp)
                        Text("내가 다녀온 현장", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TossTextInfo, letterSpacing = (-0.1).sp)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = TossGrayBg)
            )
        }
    ) { inner ->
        LazyColumn(
            modifier = Modifier.padding(top = inner.calculateTopPadding()).fillMaxSize().background(TossGrayBg),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 8.dp)  // top 14 = 헤더와 첫 카드 숨 쉬는 간격
        ) {
            // ── 「내 기록」 — 이 탭의 주인공. (2026-09-24 사장님, 프로토 artifact/EDcGwV4F)
            //   통계는 나만 보는 숫자지만 **기록은 남한테 보여줄 수 있는 것**이다.
            item(key = "myrecord") {
                MyRecordCard(
                    rec, onOpenVisited,
                    onOpenTodo = onOpenTodo, onOpenNoAddr = onOpenNoAddr,
                    zoom = mapZoom, panX = mapPanX, panY = mapPanY
                )
                Spacer(Modifier.height(12.dp))
            }
            // 지도 — 다녀온 동네. 점 크기 = 몇 번 갔나. 다녀온 곳이 없으면 아예 안 그린다.
            item(key = "map") {
                MyRecordMap(
                    rec, onShiftMonth = viewModel::shiftRecordMonth,
                    zoom = mapZoom, panX = mapPanX, panY = mapPanY,
                    onTransform = { z, x, y -> mapZoom = z; mapPanX = x; mapPanY = y }
                )
                Spacer(Modifier.height(12.dp))
            }
            // 현장 목록 — 번호가 붙어 쌓이는 곳.
            item(key = "rows") {
                if (rec.rows.isNotEmpty()) {
                    MyRecordRows(
                        rec = rec,
                        onOpenRow = { r -> onOpenCustomer(r.customerId) },
                        onComplete = { r ->
                            viewModel.completeRecordJob(r.jobId, r.customerId)
                            scope.launch {
                                val res = snackbar.showSnackbar(
                                    (r.town ?: "이 현장") + " 완료로 표시했어요",
                                    actionLabel = "되돌리기",
                                    duration = androidx.compose.material3.SnackbarDuration.Short
                                )
                                if (res == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                                    viewModel.undoRecordJob(r.jobId, r.customerId)
                                }
                            }
                        }
                    )
                    Spacer(Modifier.height(18.dp))
                }
            }
            // ── 여기부터는 **나만 보는 숫자.** 기록 밑으로 내린다. ──
            item(key = "sec-num") {
                Text(
                    "숫자로 보기",
                    fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary,
                    modifier = Modifier.padding(start = 2.dp, bottom = 11.dp)
                )
            }
            item(key = "grid") { StatGrid(s, onOpenVisited) }
            item(key = "sec-sub") {
                Text(
                    "문의 추이 · 전기간 비교",
                    fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TossTextInfo,
                    modifier = Modifier.padding(start = 2.dp, top = 26.dp, bottom = 11.dp)
                )
            }
            item(key = "trend") { TrendSection(trend, onSelect = viewModel::setPeriod); Spacer(Modifier.height(18.dp)) }
            // 🗑 걷어낸 것 (2026-09-24 사장님 "싹 갈아엎어야지"):
            //   · StatsHero(파란 "9월, 잘 하고 계세요") — 위 기록 카드와 같은 말을 두 번 했다
            //   · StatsMascot("이번 달도 옆에서 챙길게요") — 자리만 먹고 아무 정보가 없다
            //   · StatTypes(시공 종류) — 사장님 확정 "상품명이 매번 달라져서 통계 잡을 이유가 없다"
            //   · 시장 비교 — 2년째 "모이는 중". 빈 약속은 치운다 (TrendSection 안에서 제거)
        }
    }
}

/* ─────────────── 내 기록 ─────────────── */

/**
 * 「내 기록」 카드 — **번호가 주인공.** (2026-09-24 사장님)
 *
 * "이번 달 12집" 은 이번 달로 끝나지만 **「현장 038」 은 039, 040 이 계속 생긴다.**
 * 처음엔 007 같은 작은 숫자라 별것 아닌데, 100 을 넘는 순간 그게 그대로 **경력**이 된다.
 *
 * 아직 한 곳도 안 한 사람에겐 **"기록이 없어요" 라고 하지 않는다** — 그건 내 탓처럼 들린다.
 * 대신 **001 자리를 비워 두고** 보여준다. 번호가 준비돼 있으면 채우고 싶어진다.
 *
 * ⚠️ 주소를 못 찾은 곳은 **숨기지 않고 적는다.** (테스트폰 실측: 9월 7곳 중 2곳)
 *    숫자를 부풀리면 기록이 아니다.
 */
@Composable
private fun MyRecordCard(
    rec: MyRecordState,
    onOpenVisited: () -> Unit,
    /** 「완료를 안 누른 N곳」 — **그것만** 보여주러 간다. */
    onOpenTodo: () -> Unit = onOpenVisited,
    /** 「주소 못 찾은 N곳」 — **그것만** 보여주러 간다. */
    onOpenNoAddr: () -> Unit = onOpenVisited,
    zoom: Float = 1f,
    panX: Float = 0f,
    panY: Float = 0f
) {
    val clip = androidx.compose.ui.platform.LocalClipboardManager.current
    val ctx = androidx.compose.ui.platform.LocalContext.current
    Column(
        modifier = Modifier.fillMaxWidth().tossCardShadow(RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp)).background(Color.White).padding(18.dp)
    ) {
        // ⚠️ Composable 안에서 early return@Column 절대 금지 —
        //    빈→로드 전환 때 슬롯테이블이 어긋나 화면이 통째로 안 그려진다.
        //    바로 오늘 통계 탭이 그것 때문에 꺼졌고, 고치면서 또 같은 짓을 했다.
        if (rec.lastNo <= 0) {
            // 아직 한 곳도 없음 — 약속이지 변명이 아니다.
            Text("내 기록", style = AppType.label, color = TossTextTertiary)
            Spacer(Modifier.height(AppSpace.s8))
            Text("첫 현장을 기다리고 있어요", fontSize = 19.sp, fontWeight = FontWeight.ExtraBold,
                color = TossTextPrimary, letterSpacing = (-0.4).sp)
            Spacer(Modifier.height(AppSpace.s4))
            Text("시공을 끝내고 [완료] 를 누르면\n여기에 현장 001 부터 번호가 붙어 쌓여요.",
                style = AppType.body, color = TossTextInfo, lineHeight = 19.sp)
        } else {
        Text("내 기록", style = AppType.label, color = TossTextTertiary)
        Spacer(Modifier.height(AppSpace.s4))
        Text("현장 %03d".format(rec.lastNo), fontSize = 30.sp, fontWeight = FontWeight.ExtraBold,
            color = TossBlue, letterSpacing = (-1.0).sp)
        Spacer(Modifier.height(AppSpace.s4))
        Text(
            // 부제는 **올해 누적**. 아래 세 칸이 이번 달을 말하므로 여기서 또 말하면 같은 말을 두 번 한다.
            buildString {
                append("올해 ")
                if (rec.yearTownCount > 0) append(rec.yearTownCount).append("개 동네 · ")
                append("지금까지 ").append(rec.lastNo).append("곳")
            },
            style = AppType.body, color = TossTextInfo, fontWeight = FontWeight.Bold
        )
        if (rec.towns.isNotEmpty()) {
            Spacer(Modifier.height(AppSpace.s12))
            Text(rec.towns.joinToString(" · "), style = AppType.label, color = TossTextSecondary,
                lineHeight = 18.sp)
        }
        // ── 세 칸 — 셋이 **서로 다른 말**을 한다. (2026-09-24 사장님 "디테일")
        //   곳 수 = 결과 · 현장 며칠 = 몸이 나간 날 · 매출 = 그 결과.
        Spacer(Modifier.height(AppSpace.s16))
        Row(Modifier.fillMaxWidth()) {
            RecordCell("현장", "${rec.monthSites}", "곳", Modifier.weight(1f))
            RecordCell("현장 나간 날", "${rec.monthWorkDays}", "일", Modifier.weight(1f))
            RecordCell("매출", java.text.NumberFormat.getNumberInstance(java.util.Locale.KOREA)
                .format(rec.monthSalesManwon), "만원", Modifier.weight(1f))
        }
        // 지난달 대비 — 자료가 있을 때만. 줄었다고 숨기지 않는다(기록이니까).
        if (rec.prevMonthSites >= 0) {
            val d = rec.monthSites - rec.prevMonthSites
            Spacer(Modifier.height(AppSpace.s8))
            Text(
                when {
                    d > 0 -> "지난달 ${rec.prevMonthSites}곳 → 이번 달 ${rec.monthSites}곳 · ${d}곳 늘었어요"
                    d < 0 -> "지난달 ${rec.prevMonthSites}곳 → 이번 달 ${rec.monthSites}곳"
                    else -> "지난달과 같아요 · ${rec.monthSites}곳"
                },
                style = AppType.caption,
                color = if (d > 0) AppTheme.colors.done else TossTextTertiary,
                fontWeight = if (d > 0) FontWeight.Bold else FontWeight.Medium
            )
        }
        if (rec.notDoneCount > 0) {
            // 다녀왔는데 완료를 안 누른 곳 — **번호가 안 붙는다.** 안내가 아니라 할 일이다.
            Spacer(Modifier.height(AppSpace.s8))
            Text(
                "완료를 안 누른 ${rec.notDoneCount}곳이 있어요 — 누르면 번호가 붙어요 ›",
                style = AppType.caption, color = TossBlue, fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { onOpenTodo() }.padding(vertical = 2.dp)
            )
        }
        if (rec.noAddrCount > 0) {
            // 숨기지 않는다. 그리고 **누르면 채우러 갈 수 있게** 한다.
            Spacer(Modifier.height(AppSpace.s8))
            Text(
                "주소 못 찾은 ${rec.noAddrCount}곳은 동네에 안 들어가요 — 채우러 가기 ›",
                style = AppType.caption, color = AppTheme.colors.unpaid, fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { onOpenNoAddr() }.padding(vertical = 2.dp)
            )
        }
        // ── 인증샷 ── (2026-09-24 사장님 "kyro처럼 인증샷 만드는 기능은 없나?")
        //   글은 갈 곳이 없어서 안 쓴다고 하셨다 — 사장님이 올리는 건 **그림**이다.
        //   ⚠️ **보고 나서** 저장한다. 안 보고 저장하면 어떻게 생겼는지 모르고 누르는 셈이다.
        if (rec.lastNo > 0) {
            var shotOpen by remember { mutableStateOf(false) }
            Spacer(Modifier.height(AppSpace.s16))
            Box(
                Modifier.fillMaxWidth().clip(AppShape.md).background(TossBlue)
                    .clickable { shotOpen = true }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("인증샷 만들기", style = AppType.headline, fontWeight = FontWeight.ExtraBold,
                    color = Color.White)
            }
            Spacer(Modifier.height(AppSpace.s8))
            Text("현장 번호·다닌 동네·지도가 한 장에 들어가요 — 보고 나서 저장해요",
                style = AppType.caption, color = TossTextTertiary, lineHeight = 16.sp)
            if (shotOpen) ShotPreviewDialog(rec, zoom, panX, panY) { shotOpen = false }
        }
        }   // ── if (아직 없음) … else 끝
    }
}

/**
 * 다녀온 동네 지도. 점 하나 = 동네 하나, **점 크기 = 몇 번 갔나.** (2026-09-24 사장님)
 *   면을 안 칠하는 이유는 [RegionMap] 주석 참고 — 화성시가 강서구보다 20배 넓어서 그림이 거짓말을 한다.
 */
@Composable
private fun MyRecordMap(
    rec: MyRecordState,
    onShiftMonth: (Int) -> Unit,
    zoom: Float = 1f,
    panX: Float = 0f,
    panY: Float = 0f,
    onTransform: ((Float, Float, Float) -> Unit)? = null
) {
    Column(
        modifier = Modifier.fillMaxWidth().tossCardShadow(RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp)).background(Color.White).padding(14.dp)
    ) {
        // ── 달 넘기기 — 지도·목록·숫자가 다 이걸 따라간다. (2026-09-24 사장님) ──
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            MonthArrow("‹", enabled = true) { onShiftMonth(-1) }
            Text(
                rec.monthLabel, modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                style = AppType.headline, fontWeight = FontWeight.ExtraBold, color = TossTextPrimary
            )
            MonthArrow("›", enabled = rec.canGoNext) { onShiftMonth(+1) }
        }
        Spacer(Modifier.height(AppSpace.s8))
        if (rec.dots.isEmpty()) {
            Box(
                Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center
            ) {
                Text("이 달은 다녀온 기록이 없어요", style = AppType.body, color = TossTextTertiary)
            }
        } else {
        com.detailline.callfollowcrm.presentation.component.RegionMap(
            spots = rec.dots,
            zoom = zoom, panX = panX, panY = panY,
            onTransform = onTransform
        )
        Spacer(Modifier.height(AppSpace.s8))
        val sorted = rec.dots.sortedByDescending { it.count }
        Text(
            buildString {
                append(sorted.take(8).joinToString(" · ") { it.name })
                if (sorted.size > 8) append(" 외 ").append(sorted.size - 8).append("곳")
            },
            style = AppType.label, color = TossTextSecondary, lineHeight = 18.sp,
            modifier = Modifier.padding(horizontal = 2.dp)
        )
        Spacer(Modifier.height(AppSpace.s4))
        Text(
            buildString {
                append("이 달 다닌 곳 · 🚛 가 간 순서대로 달려요 · 지도를 톡 치면 다시 달려요 · 두 손가락으로 확대")
                if (rec.yearTownCount > rec.dots.size) append(" · 올해 ").append(rec.yearTownCount).append("개 동네")
            },
            style = AppType.caption, color = TossTextTertiary,
            modifier = Modifier.padding(horizontal = 2.dp)
        )
        }   // ── if (dots 비었음) … else 끝
    }
}

/**
 * 인증샷 미리보기 — **보고 나서** 저장한다. (2026-09-24 사장님)
 *   두 가지를 바꿔가며 본다: 정사각 한 장 / 사진 위에 얹을 것(배경 빈 스티커).
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ShotPreviewDialog(
    rec: MyRecordState,
    /** 지도에서 손가락으로 맞춘 그대로 — 보이던 대로 그림·영상에 담는다. (2026-09-25 사장님) */
    zoom: Float = 1f,
    panX: Float = 0f,
    panY: Float = 0f,
    onClose: () -> Unit
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    // 배경이 **현장 사진**이 될 거라 '사진 위에 얹을 것' 이 기본이다. (2026-09-24 사장님)
    var shape by remember {
        mutableStateOf(com.detailline.callfollowcrm.util.RecordShot.Shape.STICKER)
    }
    /** 영상 만드는 중이면 0~1, 아니면 -1. */
    var making by remember { mutableStateOf(-1f) }
    /** 만드는 중인 일 — [취소] 로 끊는다. */
    var reelJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    /** 방금 사진첩에 저장한 것 — 어디 갔는지 **열어볼 수 있게** 주소를 들고 있는다. */
    var savedUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var savedMime by remember { mutableStateOf("image/png") }

    // 🔆 영상 만드는 동안 **화면이 꺼지면 만들던 게 끊긴다.** 만드는 중에만 켜 둔다.
    val actWindow = (ctx as? android.app.Activity)?.window
    androidx.compose.runtime.DisposableEffect(making >= 0f) {
        if (making >= 0f) actWindow?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { actWindow?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
    val sticker = shape == com.detailline.callfollowcrm.util.RecordShot.Shape.STICKER
    // 큰 숫자 고르기 — 한가한 달엔 '이번 달' 이 초라하니 **올해 누적**이 기본.
    val picks = remember(rec) { bigPicks(rec) }
    var pick by remember(picks) { mutableStateOf(0) }
    val data = remember(rec, shape, pick, picks) {
        val p = picks.getOrElse(pick) { picks.first() }
        // 고른 것 말고 **나머지 숫자 한 줄** — "이번 달 7집 · 동네 38곳".
        //   사장님이 가져온 조언 ②('누적 수치 강조'). 지도 한 장에서만 쓴다.
        val sub = picks.filterIndexed { i, _ -> i != pick }
            .take(2).joinToString(" · ") { "${it.short} ${it.value}${it.unit}" }
        com.detailline.callfollowcrm.util.RecordShot.Data(
            bigValue = p.value, bigUnit = p.unit, bigCaption = p.caption, subLine = sub,
            zoom = zoom, panX = panX, panY = panY,
            no = rec.lastNo, monthLabel = rec.monthLabel, sites = rec.monthSites,
            workDays = rec.monthWorkDays, towns = rec.towns, dots = rec.dots,
            bizName = rec.bizName, tradeName = rec.tradeName,
            phone = rec.bizPhone, area = rec.areaLabel
        )
    }
    val bmp = remember(data, shape) {
        com.detailline.callfollowcrm.util.RecordShot.render(ctx, data, shape)
    }
    androidx.compose.ui.window.Dialog(onDismissRequest = onClose) {
        androidx.compose.material3.Surface(
            shape = RoundedCornerShape(20.dp), color = Color.White, modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("인증샷", style = AppType.title, fontWeight = FontWeight.ExtraBold,
                        color = TossTextPrimary)
                    Spacer(Modifier.weight(1f))
                    Text("닫기", style = AppType.label, color = TossTextSecondary,
                        modifier = Modifier.clickable { onClose() }.padding(6.dp))
                }
                Spacer(Modifier.height(AppSpace.s12))
                // 배경 빈 스티커는 **바둑판** 위에 올려야 "여기가 비어 있다" 가 보인다.
                Box(
                    Modifier.fillMaxWidth().clip(AppShape.md)
                        .background(if (sticker) TossGrayBg else Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.foundation.Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "인증샷 미리보기",
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(Modifier.height(AppSpace.s12))
                // ── 큰 숫자 고르기 ── 자랑할 숫자는 사람마다 다르다. (2026-09-24 사장님)
                Text("크게 넣을 숫자", style = AppType.caption, color = TossTextTertiary,
                    modifier = Modifier.padding(start = 2.dp, bottom = 6.dp))
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    picks.forEachIndexed { i, p ->
                        val on = i == pick
                        Box(
                            Modifier.clip(AppShape.pill)
                                .background(if (on) TossBlue else TossGrayBg)
                                .clickable { pick = i }
                                .padding(horizontal = 13.dp, vertical = 8.dp)
                        ) {
                            Text("${p.value}${p.unit} ${p.short}", style = AppType.caption,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (on) Color.White else TossTextSecondary, maxLines = 1)
                        }
                    }
                }
                Spacer(Modifier.height(AppSpace.s12))
                // 세 갈래 — 사진 위에 얹기 / **지도 크게** / 정사각 한 장.
                //   [지도 크게] 는 사장님이 가져온 조언 ①: 지도 한 장이 "수도권을 다 돈다" 를 바로 말해준다.
                Row(Modifier.fillMaxWidth().clip(AppShape.md).background(TossGrayBg).padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    ShotTab("사진 위에", shape == ShotShape.STICKER, Modifier.weight(1f)) {
                        shape = ShotShape.STICKER
                    }
                    ShotTab("지도 크게", shape == ShotShape.MAP, Modifier.weight(1f)) {
                        shape = ShotShape.MAP
                    }
                    ShotTab("정사각", shape == ShotShape.CARD, Modifier.weight(1f)) {
                        shape = ShotShape.CARD
                    }
                }
                Spacer(Modifier.height(AppSpace.s12))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    ShotSmall("사진첩에 저장", Modifier.weight(1f)) {
                        scope.launch {
                            val name = "shigongmagne_%03d".format(rec.lastNo) + shotSuffix(shape)
                            val uri = com.detailline.callfollowcrm.util.RecordShot.save(ctx, bmp, name)
                            savedUri = uri
                            savedMime = "image/png"
                            if (uri == null) {
                                android.widget.Toast.makeText(
                                    ctx, "저장하지 못했어요 — [올리기] 로 보내보세요",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                    Box(
                        Modifier.weight(1f).clip(AppShape.md).background(TossBlue)
                            .clickable {
                                com.detailline.callfollowcrm.util.RecordShot.share(
                                    ctx, bmp,
                                    "shigongmagne_%03d".format(rec.lastNo) + shotSuffix(shape)
                                )
                            }
                            .padding(vertical = 11.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("올리기", style = AppType.label, fontWeight = FontWeight.ExtraBold,
                            color = Color.White)
                    }
                }
                // 🎬 **영상** — 움직이는 지도를 SNS 에 올리려면 그림이 아니라 영상이어야 한다.
                //   인스타 릴스 크기(9:16). 만드는 데 시간이 걸려서 진행률을 보여준다.
                Spacer(Modifier.height(AppSpace.s12))
                if (making >= 0f) {
                    // ⏹ **취소할 길**이 있어야 한다 — 10초짜리라도 폰에선 한참 걸린다.
                    //   되돌릴 수 없는 기다림은 고장처럼 느껴진다. (2026-09-25 기본 UX 점검)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        Box(
                            Modifier.weight(1f).clip(AppShape.md).background(TossGrayBg)
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("영상 만드는 중 ${(making * 100).toInt()}%",
                                style = AppType.label, fontWeight = FontWeight.ExtraBold, color = TossTextSecondary)
                        }
                        Box(
                            Modifier.clip(AppShape.md).background(TossGrayBg)
                                .clickable { reelJob?.cancel(); reelJob = null; making = -1f }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("취소", style = AppType.label,
                                fontWeight = FontWeight.ExtraBold, color = TossTextSecondary)
                        }
                    }
                } else {
                    Box(
                        Modifier.fillMaxWidth().clip(AppShape.md)
                            .background(AppTheme.colors.primaryBg)
                            .clickable {
                                if (reelJob != null) return@clickable   // 연타 막기
                                reelJob = scope.launch {
                                    making = 0f
                                    val reel = com.detailline.callfollowcrm.util.RecordReel.make(
                                        ctx,
                                        com.detailline.callfollowcrm.util.RecordReel.Data(
                                            monthLabel = rec.monthLabel,
                                            metricValue = picks.getOrElse(pick) { picks.first() }.value,
                                            metricUnit = picks.getOrElse(pick) { picks.first() }.unit,
                                            metricLabel = picks.getOrElse(pick) { picks.first() }.caption,
                                            towns = rec.towns, dots = rec.dots,
                                            bizName = rec.bizName, tradeName = rec.tradeName,
                                            phone = rec.bizPhone, area = rec.areaLabel,
                                            zoom = zoom, panX = panX, panY = panY
                                        )
                                    ) { p -> making = p }
                                    making = -1f
                                    reelJob = null
                                    if (reel != null) {
                                        val uri = com.detailline.callfollowcrm.util.RecordShot.saveVideo(
                                            ctx, reel, "shigongmagne_%03d".format(rec.lastNo)
                                        )
                                        savedUri = uri
                                        savedMime = "video/mp4"
                                        if (uri == null) {
                                            android.widget.Toast.makeText(
                                                ctx, "저장하지 못했어요 — 바로 올려볼게요",
                                                android.widget.Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                        com.detailline.callfollowcrm.util.RecordShot.shareVideo(ctx, reel)
                                    } else {
                                        // 실패는 **이유를 말해야** 한다 — 한 줄로 끝내면 아무것도 못 고친다.
                                        val why = com.detailline.callfollowcrm.util.VideoMaker.lastError
                                        android.widget.Toast.makeText(
                                            ctx,
                                            if (why.isNullOrBlank()) "영상을 만들지 못했어요"
                                            else "영상을 만들지 못했어요 — " + why,
                                            android.widget.Toast.LENGTH_LONG
                                        ).show()
                                        // 길어서 토스트에 다 안 들어갈 수 있다 — 로그에도 남긴다.
                                        android.util.Log.e("VideoMaker", "실패: " + why)
                                    }
                                }
                            }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("영상 만들기 (릴스용 10초)", style = AppType.label,
                            fontWeight = FontWeight.ExtraBold, color = TossBlue)
                    }
                }

                // ✅ 저장했으면 **어디 갔는지 열어볼 길**을 남긴다.
                //   토스트는 사라지고 나면 확인할 방법이 없다. (2026-09-25 기본 UX 점검)
                savedUri?.let { uri ->
                    Spacer(Modifier.height(AppSpace.s8))
                    Row(
                        Modifier.fillMaxWidth().clip(AppShape.md)
                            .background(AppTheme.colors.primaryBg)
                            .clickable {
                                com.detailline.callfollowcrm.util.RecordShot.openSaved(ctx, uri, savedMime)
                            }
                            .padding(horizontal = 13.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            if (savedMime.startsWith("video")) "사진첩에 영상으로 저장했어요"
                            else "사진첩에 저장했어요",
                            style = AppType.label, color = TossTextSecondary,
                            modifier = Modifier.weight(1f)
                        )
                        Text("열어보기", style = AppType.label,
                            fontWeight = FontWeight.ExtraBold, color = TossBlue)
                    }
                }

                if (sticker) {
                    Spacer(Modifier.height(AppSpace.s8))
                    Text("배경이 비어 있어요 — 인스타 스토리에서 [스티커 → 사진] 으로 내 현장 사진 위에 올리면 돼요",
                        style = AppType.caption, color = TossTextTertiary, lineHeight = 16.sp)
                }
            }
        }
    }
}

/** 인증샷에 크게 넣을 수 있는 숫자 하나. */
private data class BigPick(val value: String, val unit: String, val short: String, val caption: String)

/**
 * 고를 수 있는 큰 숫자들 — **자랑할 수 있는 게 사람마다 다르다.** (2026-09-24 사장님)
 *   "몇 집 안 다녀서 올리기 쪽팔린 사람" 이 있으니, 이번 달이 한가해도 올해 누적은 크다.
 *   0인 건 아예 안 보여준다(고를 이유가 없다).
 *   ⚠ km 은 없다 — 타임라인 없이는 직선거리뿐이라 실제보다 한참 작게 나온다.
 */
private fun bigPicks(rec: MyRecordState): List<BigPick> = buildList {
    if (rec.lastNo > 0) add(BigPick("${rec.lastNo}", "집", "올해", "올해 다녀온 집"))
    if (rec.monthSites > 0) add(BigPick("${rec.monthSites}", "집", "이번 달", "이번 달 다녀온 집"))
    // 💰 **번 돈** — 프로토엔 있는데 여기 빠져 있었다. 돈을 숨기고 싶은 사람을 위해
    //   고르게 한 건데, 정작 고를 '돈' 이 없으면 말이 안 된다. (2026-09-25)
    if (rec.monthSalesManwon > 0) add(
        BigPick(
            java.text.NumberFormat.getNumberInstance(java.util.Locale.KOREA)
                .format(rec.monthSalesManwon),
            "만원", "번 돈", "이번 달 번 돈"
        )
    )
    if (rec.yearTownCount > 0) add(BigPick("${rec.yearTownCount}", "곳", "동네", "올해 다녀온 동네"))
    if (rec.monthWorkDays > 0) add(BigPick("${rec.monthWorkDays}", "일", "현장", "이번 달 현장에 나간 날"))
    // 🚛 **달린 거리** — 길을 타고 간 거리라 직선보다 훨씬 정직하다.
    //   다만 우리 자료엔 큰길뿐이라 실제보다 작게 나온다 → 숫자 앞에 '약'.
    if (rec.monthKm >= 1) add(BigPick("약 ${rec.monthKm}", "km", "달린 거리", "이번 달 달린 거리"))
    if (isEmpty()) add(BigPick("1", "집", "첫 현장", "첫 현장"))
}

/** 저장 파일 이름 꼬리 — 사진첩에서 뭐가 뭔지 알아보게. */
private fun shotSuffix(shape: com.detailline.callfollowcrm.util.RecordShot.Shape): String =
    when (shape) {
        com.detailline.callfollowcrm.util.RecordShot.Shape.STICKER -> "_sticker"
        com.detailline.callfollowcrm.util.RecordShot.Shape.MAP -> "_map"
        com.detailline.callfollowcrm.util.RecordShot.Shape.CARD -> ""
    }

/** 미리보기 창 위쪽 갈래 하나. */
@Composable
private fun ShotTab(label: String, on: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.clip(AppShape.sm).background(if (on) Color.White else Color.Transparent)
            .clickable { onClick() }.padding(vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, style = AppType.label, fontWeight = FontWeight.ExtraBold,
            color = if (on) TossBlue else TossTextSecondary, maxLines = 1)
    }
}

/** 인증샷 보조 버튼 — 테두리만 있는 조용한 버튼. */
@Composable
private fun ShotSmall(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.clip(AppShape.md).background(TossGrayBg).clickable { onClick() }
            .padding(vertical = 11.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, style = AppType.label, fontWeight = FontWeight.ExtraBold, color = TossTextSecondary,
            maxLines = 1)
    }
}

/** 기록 카드 안 작은 숫자 칸 — 이름·값·단위. */
@Composable
private fun RecordCell(label: String, value: String, unit: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = AppType.caption, color = TossTextTertiary)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = AppType.title, fontWeight = FontWeight.Black, color = TossTextPrimary,
                maxLines = 1)
            Spacer(Modifier.width(2.dp))
            Text(unit, style = AppType.caption, color = TossTextTertiary,
                modifier = Modifier.padding(bottom = 2.dp))
        }
    }
}

/** 달 넘기는 화살표. 갈 수 없으면 옅게. */
@Composable
private fun MonthArrow(glyph: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(34.dp).clip(AppShape.md)
            .then(if (enabled) Modifier.clickable { onClick() } else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Text(glyph, style = AppType.title, fontWeight = FontWeight.ExtraBold,
            color = if (enabled) TossTextSecondary else TossDivider)
    }
}

/**
 * 현장 목록 — **번호가 붙어 쌓인다.**
 *   맨 위는 '다음 현장'(예정). 다음 번호가 눈에 보이면 채우고 싶어진다.
 *   번호가 없는 줄 = 다녀왔는데 **완료를 안 누른** 것. 눌러서 채우러 간다.
 */
@Composable
private fun MyRecordRows(
    rec: MyRecordState,
    /** 그 줄을 눌렀을 때 — **그 집**으로 간다. 목록으로 가면 누른 게 무시된다. */
    onOpenRow: (MyRecordRow) -> Unit,
    /** 「완료 누르기」 를 눌러 확인까지 마쳤을 때. */
    onComplete: (MyRecordRow) -> Unit
) {
    // 되돌리기가 쉽지 않은 일(번호가 박힌다)이라 한 번 묻는다.
    var confirm by remember { mutableStateOf<MyRecordRow?>(null) }
    confirm?.let { r ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirm = null },
            containerColor = Color.White,
            tonalElevation = 0.dp,
            title = {
                Text("${r.town ?: "이 현장"} ${r.date} 시공, 끝났나요?",
                    style = AppType.headline, fontWeight = FontWeight.ExtraBold, color = TossTextPrimary)
            },
            text = {
                Text("완료로 표시하면 현장 번호가 붙고 「내 기록」에 쌓여요.",
                    style = AppType.body, color = TossTextSecondary)
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { onComplete(r); confirm = null }) {
                    Text("완료로 표시", style = AppType.label,
                        fontWeight = FontWeight.ExtraBold, color = TossBlue)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { confirm = null }) {
                    Text("아니요", style = AppType.label, color = TossTextSecondary)
                }
            }
        )
    }
    Column(
        modifier = Modifier.fillMaxWidth().tossCardShadow(RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp)).background(Color.White).padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        rec.rows.forEachIndexed { i, r ->
            if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(TossDivider))
            Row(
                Modifier.fillMaxWidth().clickable { onOpenRow(r) }.padding(vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(38.dp).clip(AppShape.md)
                        .background(if (r.upcoming) TossGrayBg else AppTheme.colors.primaryBg),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        r.no ?: "—",
                        style = AppType.label, fontWeight = FontWeight.Black,
                        color = if (r.upcoming) TossTextTertiary else AppTheme.colors.primaryText
                    )
                }
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (r.upcoming) "다음 현장" else (r.town ?: "주소 미등록"),
                        style = AppType.body, fontWeight = FontWeight.ExtraBold,
                        color = if (r.town == null && !r.upcoming) AppTheme.colors.unpaid else TossTextPrimary
                    )
                    Text(
                        buildString {
                            append(r.date)
                            // 하루짜리는 안 쓴다 — 당연한 값이라 줄만 길어진다.
                            if (r.days > 1) append(" · ").append(r.days).append("일")
                            // 얼마짜리 일이었는지. 안 적은 건 조용히 뺀다.
                            if (r.amountManwon > 0) {
                                append(" · ")
                                append(java.text.NumberFormat.getNumberInstance(java.util.Locale.KOREA)
                                    .format(r.amountManwon)).append("만")
                            }
                        },
                        style = AppType.caption, color = TossTextTertiary, maxLines = 1
                    )
                }
                // 번호가 없는 줄 = 아직 완료를 안 누른 것. **여기서 바로** 누르게 한다.
                //   전엔 이 글씨를 눌러도 목록으로만 갔다 — 버튼처럼 생겼는데 아무 일도 안 났다.
                val canComplete = !r.upcoming && r.no == null
                Box(
                    Modifier
                        .then(
                            if (canComplete) Modifier.clip(AppShape.pill).background(AppTheme.colors.primaryBg)
                                .clickable { confirm = r }.padding(horizontal = 11.dp, vertical = 7.dp)
                            else Modifier
                        )
                ) {
                    Text(
                        when {
                            r.upcoming -> "예정"
                            r.no == null -> "완료 누르기"
                            else -> "완료"
                        },
                        style = AppType.caption, fontWeight = FontWeight.ExtraBold,
                        color = when {
                            r.upcoming -> TossTextTertiary
                            r.no == null -> TossBlue
                            else -> AppTheme.colors.done
                        }
                    )
                }
            }
        }
    }
}

/* ─────────────── stats-hero ─────────────── */

@Composable
private fun StatsHero(s: StatsUiState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .tossCardShadow(RoundedCornerShape(22.dp))
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.linearGradient(listOf(TossBlue, TossBlueDark)))
            .padding(22.dp)
    ) {
        Text(s.greeting, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.6).sp, modifier = Modifier.padding(top = 6.dp))
        // h2: 현장 N곳 + (작년 대비)
        Row(modifier = Modifier.padding(top = 4.dp)) {
            Text("이번 달 현장 ", fontSize = 13.sp, color = Color.White.copy(alpha = 0.82f))
            Text("${s.jobs}곳", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
            Text("을 다녀오셨어요.", fontSize = 13.sp, color = Color.White.copy(alpha = 0.82f))
        }
        s.jobsVsLastYear?.takeIf { it != 0 }?.let { d ->
            Row(modifier = Modifier.padding(top = 2.dp)) {
                Text("작년 같은 달보다 ", fontSize = 13.sp, color = Color.White.copy(alpha = 0.82f))
                Text(if (d > 0) "$d 곳 더!" else "${-d}곳 적게", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
            }
        }
        // 전국 비교는 **아직 안 되는 기능**이다. 파란 카드 한가운데서 자리를 먹지 않게 뺐다.
        //   아래 '시장 비교' 카드에 한 줄로 접어 뒀다가, 데이터가 모이면 그때 편다. (2026-09-21 사장님)
    }
}

/* ─────────────── stats-mascot ─────────────── */

@Composable
private fun StatsMascot() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .tossCardShadow(RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White)
            .border(1.dp, TossDivider, RoundedCornerShape(18.dp))
            .padding(start = 6.dp, top = 12.dp, end = 16.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) { Mascot(sizeDp = 40.dp) }
        Spacer(Modifier.width(6.dp))
        // 돈 화면에서 막내는 재롱 떨지 않는다 (브랜드 북 v5 §13). 한 일만 담백하게.
        Text("이번 달도 옆에서 챙길게요", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary)
    }
}

/* ─────────────── stat-grid ─────────────── */

@Composable
private fun StatGrid(s: StatsUiState, onOpenVisited: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // 프로토 openVisited — "다녀온 현장 ›" 셀만 탭 가능 → 현장 목록.
            StatCell("다녀온 현장 ›", "${s.jobs}", "곳", TossTextPrimary, Modifier.weight(1f), onClick = onOpenVisited)
            // 숫자는 검정 — 파랑·초록에 뜻이 없었다(좋다/나쁘다가 아니라 그냥 숫자). (2026-09-21 사장님)
            StatCell("받은 문의", "${s.inquiries}", "건", TossTextPrimary, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCell("시공 전환율", "${s.conversionPct}", "%", TossTextPrimary, Modifier.weight(1f))
            StatCell("보낸 답장", "${s.sentReplies}", "건", TossTextPrimary, Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatCell(label: String, value: String, unit: String, valueColor: Color, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier = modifier
            .then(if (onClick != null) Modifier.pressScale(interaction) else Modifier)
            .tossCardShadow(RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp)).background(Color.White)
            .then(if (onClick != null) Modifier.clickable(interactionSource = interaction, indication = null) { onClick() } else Modifier)
            .padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = valueColor, letterSpacing = (-0.9).sp)
            Text(unit, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = valueColor, modifier = Modifier.padding(start = 1.dp, bottom = 3.dp))
        }
        Text(label, fontSize = 12.5.sp, color = TossTextSecondary, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 4.dp))
    }
}

/* ─────────────── 문의 추이 ─────────────── */

@Composable
private fun TrendSection(t: StatsTrendState, onSelect: (StatPeriod) -> Unit) {
    Column {
        // period-toggle
        // 탭은 앱 어디서나 같은 모양(AppTabs). 전엔 화면마다 달랐다. (2026-09-21 사장님)
        com.detailline.callfollowcrm.presentation.component.AppTabs(
            tabs = StatPeriod.values().map { it.label },
            selected = StatPeriod.values().indexOf(t.period),
            modifier = Modifier.padding(bottom = 12.dp),
            onSelect = { onSelect(StatPeriod.values()[it]) }
        )
        // panel
        Column(
            modifier = Modifier.fillMaxWidth().tossCardShadow(RoundedCornerShape(20.dp)).clip(RoundedCornerShape(20.dp)).background(Color.White).padding(18.dp)
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("${t.curTotal}건", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = TossTextPrimary, letterSpacing = (-0.9).sp)
                val up = t.deltaPct >= 0
                Text(
                    "${if (up) "▲" else "▼"} ${kotlin.math.abs(t.deltaPct)}%",
                    fontSize = 15.sp, fontWeight = FontWeight.ExtraBold,
                    color = if (up) TossSuccess else TossError,
                    modifier = Modifier.padding(start = 6.dp, bottom = 4.dp)
                )
            }
            Text(
                "${t.prevLabel} ${t.prevTotal}건 → ${t.unitLabel} ${t.curTotal}건",
                fontSize = 13.sp, color = TossTextInfo, modifier = Modifier.padding(top = 4.dp)
            )
            // 양쪽 다 0이면 **빈 그래프**다 — 막대 하나 없는 140dp 판이 화면 한가운데를 먹는다.
            //   그릴 게 없으면 한 줄로 접는다. 데이터가 생기면 그때 편다. (2026-09-23 화면 점검)
            //   '시장 비교'를 한 줄로 접어 둔 것(2026-09-21 사장님)과 같은 방식.
            val hasBars = t.bars.any { it.cur > 0 || it.prev > 0 }
            // ⚠️ 여기서 early return@Column 을 하면 **앱이 꺼진다.**
            //   빈→로드 전환 때 슬롯테이블 그룹이 어긋나 AIOOBE(index=-5). 아래 StatTypes 주석과 같은 사고.
            //   2026-09-23 에 이 한 줄을 넣으면서 return 을 같이 넣었고, 다음 날 테스트폰에서
            //   **통계 탭을 누르면 바로 재현**됐다(문의 0건 구간이라 늘 이 가지를 탄다).
            //   → return 금지. if/else 로 감싼다. (2026-09-24)
            if (!hasBars) {
                Text(
                    "아직 그릴 게 없어요 — 문의가 들어오면 여기 쌓여요",
                    style = AppType.caption, color = TossTextTertiary,
                    modifier = Modifier.padding(top = 14.dp)
                )
            } else {
            // gbars — 프로토 .gbars(height)+.pair(flex:1): 막대 영역이 위 숫자·아래 요일 빼고 남는 높이에 비례.
            val max = (t.bars.maxOfOrNull { maxOf(it.cur, it.prev) } ?: 1).coerceAtLeast(1)
            Row(
                modifier = Modifier.fillMaxWidth().height(140.dp).padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                t.bars.forEach { b ->
                    Column(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        verticalArrangement = Arrangement.Bottom,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // 0 은 강조할 값이 아니다 — 파랑은 **있는 값**에만. (2026-09-21 사장님)
                        Text("${b.cur}", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold,
                            color = if (b.cur > 0) TossBlue else TossTextTertiary, maxLines = 1)
                        Spacer(Modifier.height(4.dp))
                        // 막대 영역 = 남는 높이(weight). 각 막대는 이 높이의 비율로 채움.
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            GBar(b.prev, max, color = TossDivider)
                            GBar(b.cur, max, brush = Brush.verticalGradient(listOf(Color(0xFF5BA0FF), TossBlue)))
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(b.label, fontSize = 11.sp, color = TossTextInfo, fontWeight = FontWeight.Medium, maxLines = 1)
                    }
                }
            }
            // legend
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                LegendItem(TossDivider, t.prevLabel)
                Spacer(Modifier.width(16.dp))
                LegendItem(TossBlue, t.unitLabel)
            }
            }   // ── if (!hasBars) … else 끝
        }
        // 🗑 시장 비교 제거 (2026-09-24 사장님) — 2년째 "모이는 중" 이었다. 빈 약속은 치운다.
        //   전국 시공자 데이터가 실제로 모이면 그때 다시 만든다.
    }
}

@Composable
private fun GBar(value: Int, max: Int, color: Color? = null, brush: Brush? = null) {
    // 프로토 .bp — 막대 영역(부모 Row, 높이 고정)의 비율로 채움. min 3% 로 0 값도 stub 보이게(프로토 min-height:3px).
    val frac = (value.toFloat() / max.toFloat()).coerceIn(0.03f, 1f)
    Box(
        modifier = Modifier
            .width(10.dp)
            .fillMaxHeight(frac)
            .clip(RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp))
            .then(if (brush != null) Modifier.background(brush) else Modifier.background(color ?: TossDivider))
    )
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(9.dp).clip(RoundedCornerShape(3.dp)).background(color))
        Spacer(Modifier.width(5.dp))
        Text(label, fontSize = 11.sp, color = TossTextInfo)
    }
}

@Composable
private fun MkRow(label: String, value: String, valueColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 14.sp, color = TossTextPrimary)
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = valueColor)
    }
}

/* ─────────────── 시공 종류 ─────────────── */

@Composable
private fun StatTypes(s: StatsUiState) {
    Column(
        modifier = Modifier.fillMaxWidth().tossCardShadow(RoundedCornerShape(20.dp)).clip(RoundedCornerShape(20.dp)).background(Color.White).padding(18.dp)
    ) {
        // early return@Column 은 빈→로드 전환 시 슬롯테이블 그룹 어긋나 AIOOBE 크래시(홈서 고친 패턴). if/else 로 감쌈. (2026-07-30 버그감사)
        if (s.types.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(vertical = 14.dp), contentAlignment = Alignment.Center) {
                Text("이번 달 시공 기록이 아직 없어요", fontSize = 13.sp, color = TossTextInfo, fontWeight = FontWeight.Medium)
            }
        } else {
        // wt-hero
        s.topType?.let { top ->
            val heroSub = when {
                top.delta > 0 -> "지난달보다 ${top.delta}번 더 했어요!"
                top.delta < 0 -> "지난달보다 ${-top.delta}번 줄었어요"
                else -> "지난달과 비슷해요"
            }
            Column(
                modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Brush.linearGradient(listOf(TossBlue, TossBlueDark)))
                    .padding(horizontal = 17.dp, vertical = 16.dp)
            ) {
                Text("가장 많이 한 시공", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Color.White.copy(alpha = 0.85f))
                Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth().padding(top = 7.dp)) {
                    Text(top.name, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, letterSpacing = (-0.4).sp)
                    Spacer(Modifier.weight(1f))
                    Text("${top.count}회", fontSize = 23.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                }
                Text(heroSub, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.9f), modifier = Modifier.padding(top = 8.dp))
            }
        }
        val max = (s.types.maxOfOrNull { it.count } ?: 1).coerceAtLeast(1)
        s.types.forEachIndexed { i, t ->
            if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(TossDivider))
            WtRow(t, max)
        }
        }
    }
}

@Composable
private fun WtRow(t: StatTypeRow, max: Int) {
    Column(Modifier.padding(vertical = 9.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(t.name, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TossTextPrimary, modifier = Modifier.weight(1f))
            Text("${t.count}회", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = TossBlue)
            if (t.delta != 0) {
                val up = t.delta > 0
                Box(
                    modifier = Modifier.clip(RoundedCornerShape(8.dp))
                        .background(if (up) AppTheme.colors.doneBg else AppTheme.colors.unpaidBg)
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    Text(
                        "${if (up) "▲" else "▼"} ${kotlin.math.abs(t.delta)}",
                        fontSize = 11.sp, fontWeight = FontWeight.ExtraBold,
                        color = if (up) Color(0xFF0A8F44) else TossError
                    )
                }
            }
        }
        Spacer(Modifier.height(7.dp))
        Box(
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(99.dp)).background(TossGrayBg)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth((t.count.toFloat() / max).coerceIn(0.03f, 1f))
                    .height(6.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(Brush.horizontalGradient(listOf(Color(0xFF7CB2FF), TossBlue)))
            )
        }
    }
}
