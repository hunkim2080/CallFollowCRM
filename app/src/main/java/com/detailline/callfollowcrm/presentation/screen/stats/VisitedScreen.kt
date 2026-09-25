package com.detailline.callfollowcrm.presentation.screen.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.detailline.callfollowcrm.presentation.theme.AppTheme
import com.detailline.callfollowcrm.presentation.theme.TossBlue
import com.detailline.callfollowcrm.presentation.theme.TossDivider
import com.detailline.callfollowcrm.presentation.theme.TossGrayBg
import com.detailline.callfollowcrm.presentation.theme.TossSuccess
import com.detailline.callfollowcrm.presentation.theme.TossTextPrimary
import com.detailline.callfollowcrm.presentation.theme.TossTextInfo
import com.detailline.callfollowcrm.presentation.theme.TossTextTertiary
import com.detailline.callfollowcrm.presentation.component.tossCardShadow
import com.detailline.callfollowcrm.presentation.component.pressScale
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.detailline.callfollowcrm.presentation.theme.TossBlueSoft

/**
 * 다녀온/다녀올 현장 — 프로토 `s-visited` 확장.
 *   상단 요약(다녀온 N곳 · 매출 + 다녀올 M곳) → "다녀올 현장"(파랑) 섹션 → "다녀온 현장"(초록) 섹션.
 *   행 = 날짜 · 이름/주소 · › → 탭 시 고객 카드.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisitedScreen(
    viewModel: VisitedViewModel,
    onBack: () -> Unit,
    onOpenCustomer: (Long) -> Unit,
    /**
     * 무엇만 볼까. null = 전부 · "todo" = 완료 안 누른 것만 · "addr" = 주소 없는 것만.
     *   **할 일이라고 써놓고 누르면 전부를 보여주면 앱이 일을 안 한 것이다.**
     *   (2026-09-25 사장님 "클릭하면 안한것만 나오면 찾기편한데.. 다녀온현장이 다보이네")
     */
    filter: String? = null
) {
    val raw by viewModel.state.collectAsState()
    var only by remember(filter) { mutableStateOf(filter) }
    val state = remember(raw, only) {
        when (only) {
            "todo" -> raw.copy(visitedRows = raw.visitedRows.filter { !it.done }, upcomingRows = emptyList())
            "addr" -> raw.copy(
                visitedRows = raw.visitedRows.filter { !it.hasAddr },
                upcomingRows = raw.upcomingRows.filter { !it.hasAddr }
            )
            else -> raw
        }
    }

    Scaffold(
        containerColor = TossGrayBg,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (only) {
                            "todo" -> "완료를 안 누른 곳"
                            "addr" -> "주소가 없는 곳"
                            else -> "다녀온 현장 · ${state.monthLabel}"
                        },
                        fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로", tint = TossTextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = TossGrayBg)
            )
        }
    ) { inner ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(inner),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 4.dp, bottom = 18.dp)
        ) {
            // 걸러진 화면엔 **나가는 길**이 있어야 한다. 안 그러면 갇힌 것처럼 느껴진다.
            if (only != null) {
                item(key = "filterbar") {
                    Row(
                        Modifier.fillMaxWidth()
                            .padding(top = 4.dp, bottom = 10.dp)
                            .clip(com.detailline.callfollowcrm.presentation.theme.AppShape.md)
                            .background(TossBlueSoft)
                            .clickable { only = null }
                            .padding(horizontal = 13.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            // ⚠️ 길면 [전체 보기] 와 붙어 **안내문의 일부처럼** 보이고 오탭이 난다.
                            //   하는 법은 아래 한 줄로 따로 내린다. (2026-09-25 폰에서 확인)
                            if (only == "todo") "완료를 안 누른 곳만 보는 중"
                            else "주소가 없는 곳만 보는 중",
                            style = com.detailline.callfollowcrm.presentation.theme.AppType.label,
                            fontWeight = FontWeight.Bold, color = TossBlue,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "전체 보기",
                            style = com.detailline.callfollowcrm.presentation.theme.AppType.label,
                            fontWeight = FontWeight.Bold, color = TossBlue,
                            modifier = Modifier.padding(start = 10.dp)
                        )
                    }
                    Text(
                        if (only == "todo") "줄을 누르면 그 현장으로 가요 — 거기서 완료를 눌러주세요"
                        else "줄을 누르면 그 현장으로 가요 — 거기서 주소를 채워주세요",
                        style = com.detailline.callfollowcrm.presentation.theme.AppType.caption,
                        color = TossTextTertiary,
                        modifier = Modifier.padding(start = 2.dp, top = 6.dp)
                    )
                }
            }
            item(key = "sub") {
                val sub = buildString {
                    // 걸러서 볼 땐 **보이는 줄만** 더한다 — 곳 수는 걸러졌는데 금액만 전체면 거짓말이 된다.
                    val won = if (only == null) state.revenueManwon
                    else state.visitedRows.sumOf { it.amountManwon }
                    append("다녀온 ${state.visitedCount}곳 · 매출 합계 ${"%,d".format(won)}만원")
                    if (state.upcomingCount > 0) append("  ·  다녀올 ${state.upcomingCount}곳")
                }
                Text(sub, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TossTextInfo,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
            }

            if (state.visitedRows.isEmpty() && state.upcomingRows.isEmpty()) {
                item(key = "empty") {
                    if (state.loaded) {
                        // 빈 화면도 **무슨 상황인지** 말해야 한다.
                        //   · 걸러서 봤는데 없다 = 다 챙긴 것 → 칭찬. "현장이 없어요" 는 거짓말이다.
                        //   · 지난달을 보는 중이면 "이번 달" 이 아니다. (2026-09-25 달 고르기가 생김)
                        com.detailline.callfollowcrm.presentation.component.MascotEmptyState(
                            speech = when (only) {
                                "todo" -> "완료를 안 누른 곳이 없어요 · 다 챙기셨네요"
                                "addr" -> "주소가 빠진 곳이 없어요 · 다 채우셨네요"
                                else -> "${state.monthLabel} 현장이 아직 없어요"
                            }
                        )
                    } else {
                        Box(Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
                            Text("불러오는 중…", color = TossTextTertiary, fontSize = 13.sp)
                        }
                    }
                }
            }

            // 다녀온 현장 — 지난 것. **완료를 눌렀는지는 줄마다 다르다.**
            //   ⚠️ 전엔 머리글에 "(완료)", 딱지에 "완료" 를 **손으로 박아** 뒀다.
            //      그래서 「완료를 안 누른 곳」 목록인데 줄마다 초록 「완료」 가 붙어 있었다.
            //      (2026-09-25 사장님 "완료 인데 왜 이 페이지에 나와?")
            if (state.visitedRows.isNotEmpty()) {
                item(key = "done-head") {
                    // 완료 안 누른 것만 모아 보는 중이면 **초록 점이 어울리지 않는다.**
                    SectionHead("다녀온 현장", if (only == "todo") AppTheme.colors.unpaid else TossSuccess)
                }
                items(state.visitedRows, key = { "done-${it.customerId}" }) { v ->
                    // 딱지는 **v.done 을 보고** 정한다. 손으로 박지 않는다.
                    if (v.done) {
                        VisitedRowItem(v, TossSuccess, "완료", onClick = { onOpenCustomer(v.customerId) })
                    } else {
                        VisitedRowItem(v, AppTheme.colors.unpaid, "완료 안 누름",
                            onClick = { onOpenCustomer(v.customerId) })
                    }
                }
            }
            // 다녀올 현장 (예정) — 파랑 · 아래
            if (state.upcomingRows.isNotEmpty()) {
                item(key = "up-head") { SectionHead("다녀올 현장 (예정)", TossBlue) }
                items(state.upcomingRows, key = { "up-${it.customerId}" }) { v ->
                    VisitedRowItem(v, TossBlue, "예정", onClick = { onOpenCustomer(v.customerId) })
                }
            }
        }
    }
}

@Composable
private fun SectionHead(label: String, color: Color) {
    Row(
        Modifier.padding(top = 14.dp, bottom = 8.dp, start = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(7.dp).clip(RoundedCornerShape(99.dp)).background(color))
        Spacer(Modifier.width(7.dp))
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = TossTextPrimary)
    }
}

@Composable
private fun VisitedRowItem(v: VisitedRow, accent: Color, tag: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        Modifier.fillMaxWidth().padding(bottom = 9.dp)
            .pressScale(interaction)
            .tossCardShadow(RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp)).background(Color.White)
            .border(1.dp, accent.copy(alpha = 0.22f), RoundedCornerShape(14.dp))
            .clickable(interactionSource = interaction, indication = null) { onClick() }
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(v.dateLabel, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = accent,
            modifier = Modifier.width(44.dp))
        Spacer(Modifier.width(6.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(v.name, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = TossTextPrimary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                Spacer(Modifier.width(6.dp))
                Box(
                    Modifier.clip(RoundedCornerShape(999.dp)).background(accent.copy(alpha = 0.12f))
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) { Text(tag, fontSize = 10.5.sp, fontWeight = FontWeight.ExtraBold, color = accent) }
            }
            Text(v.addr, fontSize = 12.5.sp, color = TossTextInfo, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
        }
        Icon(Icons.Default.ChevronRight, null, tint = TossDivider, modifier = Modifier.size(20.dp))
    }
}
