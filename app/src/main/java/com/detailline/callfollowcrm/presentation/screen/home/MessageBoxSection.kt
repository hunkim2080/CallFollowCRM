package com.detailline.callfollowcrm.presentation.screen.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.detailline.callfollowcrm.presentation.theme.TossBlue
import com.detailline.callfollowcrm.presentation.theme.TossBlueSoft
import com.detailline.callfollowcrm.presentation.theme.TossGrayBg
import com.detailline.callfollowcrm.presentation.theme.TossTextPrimary
import com.detailline.callfollowcrm.presentation.theme.TossTextSecondary
import com.detailline.callfollowcrm.presentation.theme.TossTextTertiary
import com.detailline.callfollowcrm.util.DateTimeUtils
import com.detailline.callfollowcrm.util.PhoneNumberFormatter

/**
 * 상담함 / 문자함 전환 탭 — "크롬 탭 윗부분처럼" 위가 둥근 폴더 2개. (2026-07-11 사장님)
 *   활성 탭 = 흰색(본문에 붙은 느낌), 비활성 = 톤다운. 문자함엔 안 읽음 배지.
 */
@Composable
fun InboxFolderTabs(selected: Int, onSelect: (Int) -> Unit, consultBadge: Int, generalBadge: Int) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 18.dp, end = 18.dp, top = 2.dp, bottom = 0.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 두 탭 다 미확인/안읽음 숫자 배지 — 다른 탭에 쌓여도 안 놓치게. (2026-07-13 사장님)
        FolderTab("상담함", selected == 0, badge = consultBadge, modifier = Modifier.weight(1f)) { onSelect(0) }
        FolderTab("문자함", selected == 1, badge = generalBadge, modifier = Modifier.weight(1f)) { onSelect(1) }
    }
}

@Composable
private fun FolderTab(label: String, active: Boolean, badge: Int, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .height(42.dp)
            .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
            .background(if (active) Color.White else TossGrayBg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                fontSize = 15.sp,
                fontWeight = if (active) FontWeight.ExtraBold else FontWeight.Bold,
                color = if (active) TossTextPrimary else TossTextTertiary
            )
            if (badge > 0) {
                Spacer(Modifier.size(6.dp))
                // 고정 18x18 = 항상 정원. 글자 여백 주면 옆으로 늘어나 타원 되므로 여백 없이 중앙정렬. (2026-07-13 사장님)
                Box(
                    Modifier.size(18.dp).clip(CircleShape).background(TossBlue),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (badge > 9) "9+" else "$badge",
                        color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        // 기본 font padding(위아래 여백)을 끄고 라인 높이를 중앙정렬 → 숫자가 원 정중앙. (2026-07-13 사장님)
                        style = androidx.compose.ui.text.TextStyle(
                            platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false),
                            lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(
                                alignment = androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center,
                                trim = androidx.compose.ui.text.style.LineHeightStyle.Trim.None
                            )
                        )
                    )
                }
            }
        }
    }
}

/**
 * 문자함 — 고객 아닌 문자(광고·인증·알림)를 삼성 기본 메시지처럼 단순 목록으로. (2026-07-11 사장님)
 *   기능 최소: 탭=대화 열기, 길게누름=상담함으로/스팸으로. 광고엔 '광고' 회색 딱지.
 */
@Composable
fun MessageBoxSection(
    threads: List<GeneralThread>,
    onOpen: (String) -> Unit,
    onMoveToConsult: (String) -> Unit,
    onMoveToSpam: (String, String?) -> Unit,
    modifier: Modifier = Modifier
) {
    if (threads.isEmpty()) {
        Box(modifier.fillMaxSize().background(Color.White), contentAlignment = Alignment.Center) {
            com.detailline.callfollowcrm.presentation.component.MascotEmptyState(
                speech = "문자함이 비어 있어요",
                sub = "고객이 아닌 문자(광고·인증·알림)가 여기 모여요"
            )
        }
        return
    }
    LazyColumn(
        modifier = modifier.fillMaxSize().background(Color.White),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        items(threads, key = { it.suffix }) { t ->
            GeneralRow(t, onOpen = { onOpen(t.phone) },
                onMoveToConsult = { onMoveToConsult(t.phone) },
                onMoveToSpam = { onMoveToSpam(t.phone, t.displayName) })
        }
    }
}

@Composable
private fun GeneralRow(
    t: GeneralThread,
    onOpen: () -> Unit,
    onMoveToConsult: () -> Unit,
    onMoveToSpam: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    val title = t.displayName?.takeIf { it.isNotBlank() } ?: PhoneNumberFormatter.format(t.phone)
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .background(Color.White)
                .clickable(onClick = onOpen)
                .padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 이니셜 원 아바타
            Box(
                Modifier.size(42.dp).clip(CircleShape).background(TossBlueSoft),
                contentAlignment = Alignment.Center
            ) {
                Text(title.take(1), fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TossBlue)
            }
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, fontSize = 15.sp,
                        fontWeight = if (t.unread) FontWeight.ExtraBold else FontWeight.Bold,
                        color = TossTextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false))
                    if (t.isAd) {
                        Spacer(Modifier.size(6.dp))
                        Box(Modifier.clip(RoundedCornerShape(5.dp)).background(TossGrayBg).padding(horizontal = 5.dp, vertical = 1.dp)) {
                            Text("광고", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TossTextTertiary)
                        }
                    }
                }
                Spacer(Modifier.size(2.dp))
                Text(t.lastBody.replace("\n", " "), fontSize = 12.5.sp,
                    color = if (t.unread) TossTextSecondary else TossTextTertiary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.size(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(DateTimeUtils.formatShort(t.lastDateMs), fontSize = 11.sp, color = TossTextTertiary)
                if (t.unread) {
                    Spacer(Modifier.size(6.dp))
                    Box(Modifier.size(8.dp).clip(CircleShape).background(TossBlue))
                }
            }
            // ⋮ 탭 = 메뉴 열기. 메뉴를 이 버튼 Box 안에 둬야 버튼 옆(오른쪽)에 뜸.
            //   밖(행 전체 Box)에 두면 그 Box 왼쪽 위에 떠서 엉뚱한 위치에 나왔음. (2026-07-13 사장님)
            Box(
                Modifier.size(28.dp).clickable { menu = true },
                contentAlignment = Alignment.Center
            ) {
                Text("⋮", fontSize = 18.sp, color = TossTextTertiary)
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("상담함으로 옮기기") }, onClick = { menu = false; onMoveToConsult() })
                    DropdownMenuItem(text = { Text("스팸으로") }, onClick = { menu = false; onMoveToSpam() })
                }
            }
        }
    }
}
