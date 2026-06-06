package com.detailline.callfollowcrm.presentation.screen.stats

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
import com.detailline.callfollowcrm.presentation.theme.TossDivider
import com.detailline.callfollowcrm.presentation.theme.TossGrayBg
import com.detailline.callfollowcrm.presentation.theme.TossTextPrimary
import com.detailline.callfollowcrm.presentation.theme.TossTextSecondary
import com.detailline.callfollowcrm.presentation.theme.TossTextTertiary

/**
 * 다녀온 현장 · {월} — 프로토 `s-visited`/openVisited 1:1.
 *   sec-sub "총 N곳 · 매출 합계 OOO만원" + vrow(날짜 · 이름/주소 · ›) → 탭 시 고객 카드.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisitedScreen(
    viewModel: VisitedViewModel,
    onBack: () -> Unit,
    onOpenCustomer: (Long) -> Unit
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        containerColor = TossGrayBg,
        topBar = {
            TopAppBar(
                title = { Text("다녀온 현장 · ${state.monthLabel}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary) },
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
            item(key = "sub") {
                Text(
                    "총 ${state.count}곳 · 매출 합계 ${"%,d".format(state.revenueManwon)}만원",
                    fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TossTextTertiary,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                )
            }
            if (state.rows.isEmpty()) {
                item(key = "empty") {
                    Box(Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
                        Text(
                            if (state.loaded) "이번 달 다녀온 현장이 아직 없어요" else "불러오는 중…",
                            color = TossTextTertiary, fontSize = 13.sp
                        )
                    }
                }
            } else {
                items(state.rows, key = { it.customerId }) { v ->
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = 9.dp)
                            .clip(RoundedCornerShape(14.dp)).background(Color.White)
                            .clickable { onOpenCustomer(v.customerId) }
                            .padding(horizontal = 14.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(v.dateLabel, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = TossTextSecondary,
                            modifier = Modifier.width(44.dp))
                        Spacer(Modifier.width(6.dp))
                        Column(Modifier.weight(1f)) {
                            Text(v.name, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = TossTextPrimary,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(v.addr, fontSize = 12.5.sp, color = TossTextTertiary, maxLines = 1,
                                overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
                        }
                        Icon(Icons.Default.ChevronRight, null, tint = TossDivider, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}
