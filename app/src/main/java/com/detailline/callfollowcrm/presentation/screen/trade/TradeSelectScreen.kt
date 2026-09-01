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

// 4대 그룹(숨고 인테리어·설치수리·이사청소·자동차 잎사귀 기반). 대표업종 1개 선택 + "기타 직접입력" 크라우드소싱. (2026-09-01 사장님)
private val TRADE_GROUPS = listOf(
    "🎨 인테리어" to listOf("줄눈", "실리콘·코킹", "도배", "장판·마루", "타일", "페인트·도색", "인테리어필름", "욕실리모델링", "방수·누수", "미장", "목공·몰딩", "샷시·중문", "커튼·블라인드", "바닥(에폭시·폴리싱)"),
    "🔧 설치·수리" to listOf("에어컨 설치·청소", "보일러 수리", "조명·전기", "수전·배관설비", "도어·잠금장치", "가구 설치·조립", "CCTV·인터폰", "방충망·방범창", "가전 설치"),
    "🧹 이사·청소" to listOf("입주청소", "거주·정기청소", "사업장청소", "곰팡이 제거", "새집증후군", "이사(가정·원룸)", "용달·운송", "철거·폐기"),
    "🚗 자동차" to listOf("광택·디테일링", "썬팅·필름", "차량정비", "세차")
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

            // 4대 그룹 잎사귀 — 전부 활성(테스터 게이트 제거). 대표 1개 라디오. (2026-09-01 사장님)
            val allListed = remember { TRADE_GROUPS.flatMap { it.second } }
            TRADE_GROUPS.forEach { (group, trades) ->
                Text(group, style = MaterialTheme.typography.labelLarge, color = TossTextTertiary, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(7.dp))
                FlowRow(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                    trades.forEach { t ->
                        val isSel = selected.contains(t)
                        Box(
                            Modifier.padding(bottom = 8.dp).clip(RoundedCornerShape(999.dp))
                                .background(if (isSel) TossBlueSoft else Color.White)
                                .clickable { toggle(t) }
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Text(
                                (if (isSel) "★ " else "") + t,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isSel) TossBlue else TossTextSecondary,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            // ➕ 기타 직접입력 + 자동완성 — "cctv 수리" vs "cctv 고장수리" 처럼 같은 업종이 다른 통계로 쪼개지지 않게,
            //   타이핑하면 기존 업종을 먼저 제안(canonical 유도). 진짜 새 업종만 커스텀 추가(→서버 정규화·정식승격 대상). (2026-09-01 사장님)
            Text("목록에 없으면 직접 입력", style = MaterialTheme.typography.labelMedium, color = TossTextTertiary)
            Spacer(Modifier.height(6.dp))
            com.detailline.callfollowcrm.presentation.component.SheetTextField(
                value = customText, onValueChange = { customText = it },
                placeholder = "예: 방역, 폴리싱, 조적…", modifier = Modifier.fillMaxWidth(), enabled = true
            )
            val q = customText.trim()
            if (q.isNotEmpty()) {
                val qLower = q.lowercase()
                val qTokens = qLower.split(' ', '·', '/', ',', '-').map { it.trim() }.filter { it.length >= 2 }
                val matches = remember(q) {
                    allListed.filter { chip -> val c = chip.lowercase(); c.contains(qLower) || qTokens.any { c.contains(it) } }.take(6)
                }
                Spacer(Modifier.height(9.dp))
                if (matches.isNotEmpty()) {
                    Text("혹시 이거? 골라주시면 통계가 안 쪼개져요", style = MaterialTheme.typography.labelMedium, color = TossBlue, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(7.dp))
                    FlowRow(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                        matches.forEach { m ->
                            Box(
                                Modifier.padding(bottom = 8.dp).clip(RoundedCornerShape(999.dp)).background(TossBlueSoft)
                                    .clickable { selected.clear(); selected.add(m); customText = "" }
                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                            ) { Text("＋ $m", color = TossBlue, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold) }
                        }
                    }
                } else {
                    Spacer(Modifier.height(3.dp))
                    Box(
                        Modifier.clip(RoundedCornerShape(999.dp)).background(TossBlueSoft)
                            .clickable { selected.clear(); selected.add(q); customText = "" }
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) { Text("＋ '$q' 새 업종으로 추가", color = TossBlue, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold) }
                }
            }
            // 직접 입력한(목록에 없는) 업종이 선택됐으면 표시
            val customSel = selected.firstOrNull()?.takeIf { it !in allListed }
            if (customSel != null) {
                Spacer(Modifier.height(11.dp))
                Text("선택된 업종", style = MaterialTheme.typography.labelMedium, color = TossTextTertiary)
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier.clip(RoundedCornerShape(999.dp)).background(TossBlueSoft)
                        .clickable { selected.clear() }
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) { Text("★ $customSel   ✕", color = TossBlue, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold) }
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
