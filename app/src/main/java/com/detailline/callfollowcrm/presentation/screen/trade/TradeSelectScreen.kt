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
    "페인트·도색", "필름", "방충망", "중문·샷시", "에어컨", "입주청소",
    "누수·방수", "도어", "조명·전기"
)

/**
 * 내 업종 선택 (2026-06-01) — 최대 3개, 첫 번째 = 대표.
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
        if (selected.contains(t)) selected.remove(t)
        else if (selected.size < 3) selected.add(t)
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
            Modifier.padding(inner).fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text("최대 3개까지 고를 수 있어요. 첫 번째가 대표 업종이에요.",
                style = MaterialTheme.typography.bodyMedium, color = TossTextSecondary)
            Spacer(Modifier.height(4.dp))
            Text("선택 ${selected.size}/3", style = MaterialTheme.typography.labelMedium, color = TossTextTertiary)
            Spacer(Modifier.height(14.dp))

            FlowRow(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                (TRADES + selected.filter { it !in TRADES }).distinct().forEach { t ->
                    val isSel = selected.contains(t)
                    val isPrimary = selected.firstOrNull() == t
                    Box(
                        Modifier.padding(bottom = 8.dp).clip(RoundedCornerShape(999.dp))
                            .background(if (isSel) TossBlueSoft else Color.White)
                            .clickable { toggle(t) }
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Text(
                            (if (isPrimary) "★ " else "") + t,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isSel) TossBlue else TossTextSecondary,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Text("목록에 없으면 직접 추가", style = MaterialTheme.typography.labelMedium, color = TossTextSecondary)
            Spacer(Modifier.height(6.dp))
            androidx.compose.material3.OutlinedTextField(
                value = customText, onValueChange = { customText = it }, singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("예: 방역, 인테리어 필름…") },
                trailingIcon = {
                    Text("추가", color = if (customText.isNotBlank() && selected.size < 3) TossBlue else TossTextTertiary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable {
                            val v = customText.trim()
                            if (v.isNotBlank() && selected.size < 3 && v !in selected) { selected.add(v); customText = "" }
                        }.padding(horizontal = 12.dp))
                }
            )

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
