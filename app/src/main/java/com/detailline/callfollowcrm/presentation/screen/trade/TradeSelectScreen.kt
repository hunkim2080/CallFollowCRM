package com.detailline.callfollowcrm.presentation.screen.trade

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.detailline.callfollowcrm.data.preferences.AppPreferences
import com.detailline.callfollowcrm.presentation.theme.TossBlue
import com.detailline.callfollowcrm.presentation.theme.TossBlueSoft
import com.detailline.callfollowcrm.presentation.theme.TossDivider
import com.detailline.callfollowcrm.presentation.theme.TossGrayBg
import com.detailline.callfollowcrm.presentation.theme.TossTextPrimary
import com.detailline.callfollowcrm.presentation.theme.TossTextSecondary
import com.detailline.callfollowcrm.presentation.theme.TossTextTertiary

private val TRADES = listOf(
    "줄눈", "실리콘·코킹", "도배", "장판·마루", "타일", "욕실리모델링",
    "페인트·도색", "필름", "방충망", "중문·샷시", "에어컨 설치·청소", "입주청소",
    "누수·방수", "도어", "조명·전기"
)

/**
 * 내 업종 선택 (2026-06-01) — 하나만(라디오). (2026-06-22 사장님: 1개만)
 *   업종이 AI지식·가격표·시나리오를 바꿈(해자). prefs.ownerTrades 저장.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TradeSelectScreen(
    prefs: AppPreferences,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val selected = remember { mutableStateListOf<String>().apply { addAll(prefs.ownerTrades) } }
    var customText by remember { mutableStateOf("") }

    fun toggle(t: String) {
        // 하나만 선택(라디오) — 다른 걸 누르면 교체. (2026-06-22 사장님)
        if (selected.contains(t)) selected.remove(t)
        else { selected.clear(); selected.add(t) }
    }

    Scaffold(
        containerColor = TossGrayBg,
        topBar = {
            TopAppBar(
                title = { Text("내 업종", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "뒤로", tint = TossTextPrimary) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = TossGrayBg)
            )
        }
    ) { inner ->
        Column(
            Modifier.padding(inner).fillMaxSize().imePadding().verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text("하시는 시공을 하나만 골라주세요.",
                style = MaterialTheme.typography.bodyMedium, color = TossTextSecondary)
            Spacer(Modifier.height(14.dp))

            FlowRow(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                TRADES.forEach { t ->
                    val enabled = t == "줄눈" || t == "에어컨 설치·청소"   // 테스터 빌드: 줄눈·에어컨만 활성 (2026-06-22 사장님)
                    val isSel = selected.contains(t)
                    val isPrimary = selected.firstOrNull() == t
                    Box(
                        Modifier.padding(bottom = 8.dp).clip(RoundedCornerShape(999.dp))
                            .background(if (!enabled) TossGrayBg else if (isSel) TossBlueSoft else Color.White)
                            .then(if (enabled) Modifier.clickable { toggle(t) } else Modifier)
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Text(
                            (if (isPrimary) "★ " else "") + t,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (!enabled) TossTextTertiary else if (isSel) TossBlue else TossTextSecondary,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            // 테스터 빌드: 직접 추가 비활성(회색). (2026-06-14 사장님)
            Text("목록에 없으면 직접 추가", style = MaterialTheme.typography.labelMedium, color = TossTextTertiary)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                com.detailline.callfollowcrm.presentation.component.SheetTextField(
                    value = customText, onValueChange = { customText = it },
                    placeholder = "예: 방역, 인테리어 필름…", modifier = Modifier.weight(1f), enabled = false
                )
                Text("추가", color = TossTextTertiary, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 12.dp))
            }

            Spacer(Modifier.height(24.dp))
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .background(if (selected.isEmpty()) TossDivider else TossBlue)
                    .clickable(enabled = selected.isNotEmpty()) {
                        prefs.ownerTrades = selected.toList()
                        android.widget.Toast.makeText(context, "저장했어요", android.widget.Toast.LENGTH_SHORT).show()
                        onBack()
                    }
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("저장", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}
