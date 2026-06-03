package com.detailline.callfollowcrm.presentation.screen.stylelearning

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import com.detailline.callfollowcrm.presentation.component.TossCard
import com.detailline.callfollowcrm.presentation.theme.TossBlue
import com.detailline.callfollowcrm.presentation.theme.TossDivider
import com.detailline.callfollowcrm.presentation.theme.TossGrayBg
import com.detailline.callfollowcrm.presentation.theme.TossTextPrimary
import com.detailline.callfollowcrm.presentation.theme.TossTextSecondary
import com.detailline.callfollowcrm.presentation.theme.TossTextTertiary

private val Purple = Color(0xFF7C5CFC)
private val PurpleSoft = Color(0xFFF1ECFF)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StyleLearningScreen(
    viewModel: StyleLearningViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    LaunchedEffect(state.toast) {
        state.toast?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show(); viewModel.consumeToast() }
    }
    var exampleOpen by remember { mutableStateOf(false) }
    var signatureOpen by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = TossGrayBg,
        topBar = {
            TopAppBar(
                title = { Text("내 말투 학습", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "뒤로", tint = TossTextPrimary) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = TossGrayBg)
            )
        }
    ) { inner ->
        Column(
            Modifier.padding(inner).fillMaxSize().background(TossGrayBg)
                .verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── tone-hero ──
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                    .background(Brush.linearGradient(listOf(Purple, Color(0xFF5B43C9)))).padding(20.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🐣", fontSize = 30.sp)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text("${state.sampleCount}", fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                            Text(" 개 학습", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.padding(bottom = 6.dp))
                        }
                        Text(
                            if (!state.enabled) "학습이 꺼져 있어요. 켜면 다시 배우기 시작해요"
                            else "사장님이 보낸 문자에서 말투를 배우는 중이에요",
                            fontSize = 12.5.sp, color = Color.White.copy(alpha = 0.9f)
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { (state.sampleCount.coerceAtMost(100)) / 100f },
                    modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(99.dp)),
                    color = Color.White, trackColor = Color.White.copy(alpha = 0.25f)
                )
                Spacer(Modifier.height(8.dp))
                Text("내가 보낸 문자 ${state.sampleCount}개 · 직접 가르친 예문 ${state.examplesCount}개 · 더 모일수록 정확해져요",
                    fontSize = 11.sp, color = Color.White.copy(alpha = 0.8f))
            }

            // ── tone-on (토글) ──
            TossCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(PurpleSoft),
                        contentAlignment = Alignment.Center) { Text("✨", fontSize = 17.sp) }
                    Spacer(Modifier.width(11.dp))
                    Column(Modifier.weight(1f)) {
                        Text("말투 학습 ${if (state.enabled) "켜짐" else "꺼짐"}", fontSize = 15.sp,
                            fontWeight = FontWeight.Bold, color = TossTextPrimary)
                        Text(
                            if (state.enabled) "추천 답변이 사장님 말투로 만들어지고, 채팅에 ✨내 말투 배지가 붙어요"
                            else "추천 답변이 일반 AI 말투로 나와요",
                            fontSize = 12.sp, color = TossTextTertiary
                        )
                    }
                    Switch(checked = state.enabled, onCheckedChange = viewModel::setEnabled)
                }
            }

            // ── 지금 학습하기 (실제 분석 실행) ──
            TossCard {
                Column {
                    Text("문자에서 말투 가져오기", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        listOf(1, 3, 6).forEach { m ->
                            ToneChip("최근 ${m}개월", state.periodMonths == m) { viewModel.selectPeriod(m) }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Box(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .background(if (state.loading) TossDivider else Purple)
                            .clickable(enabled = !state.loading) { viewModel.learnFromSamples() }
                            .padding(vertical = 13.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(if (state.loading) "분석 중... ${state.progress}%" else "지금 학습하기",
                            color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // ── AI가 분석한 사장님 말투 (traits) ──
            SecSub("AI가 분석한 사장님 말투")
            if (state.analyzed) {
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) {
                    TraitChip("평균 길이", "${state.avgLength}자", Modifier.weight(1f))
                    TraitChip("이모티콘", "메시지당 ${"%.1f".format(state.emojiPerMessage)}개", Modifier.weight(1f))
                    TraitChip("친절도", "${state.kindness}", Modifier.weight(1f))
                }
            } else {
                PlaceholderCard("‘지금 학습하기’를 누르면, 분석된 말투 특징(길이·이모티콘·친절도)이 여기에 떠요.")
            }

            // ── 같은 질문, 이렇게 달라져요 (before/after) — 서버 대기 ──
            SecSub("같은 질문, 이렇게 달라져요")
            PlaceholderCard("문자를 더 학습하면, 같은 질문에 ‘일반 AI’ 답변과 ‘사장님 말투’ 답변이 어떻게 다른지 비교를 보여드려요. (서버 분석 준비 중)")

            // ── 무엇으로 배우나요 (sources) ──
            SecSub("무엇으로 배우나요")
            SourceRow("📩", "내가 보낸 문자", "실제 고객에게 보낸 답장에서 말투를 배워요", state.sampleCount)
            Spacer(Modifier.height(8.dp))
            SourceRow("✨", "직접 가르친 예문", "“이렇게 답해줘” 하고 알려준 문장", state.examplesCount)

            // ── 직접 가르치기 (teach) ──
            SecSub("직접 가르치기")
            TeachButton("➕", "예문 추가하기", "“이런 상황엔 이렇게 답해줘” 알려주기") { exampleOpen = true }
            Spacer(Modifier.height(8.dp))
            TeachButton("💬", "말투 세부 설정",
                state.signature.takeIf { it.isNotBlank() }?.let { "시그니처: $it" } ?: "꼭 쓰는 인사 시그니처를 정해요") { signatureOpen = true }

            // ── privacy info-note ──
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(PurpleSoft)
                    .padding(horizontal = 13.dp, vertical = 12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Text("🔒", fontSize = 13.sp)
                Spacer(Modifier.width(7.dp))
                Text("내 문자는 사장님 계정에서 말투 학습에만 쓰이고, 다른 곳에 공유되지 않아요.",
                    fontSize = 12.sp, color = Purple, fontWeight = FontWeight.Medium, lineHeight = 17.sp)
            }
            Spacer(Modifier.height(12.dp))
        }
    }

    if (exampleOpen) {
        var txt by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { exampleOpen = false },
            title = { Text("예문 추가", fontWeight = FontWeight.Bold, color = TossTextPrimary) },
            text = {
                Column {
                    Text("이런 상황엔 이렇게 답한다 — 막내가 그대로 배워요.", fontSize = 12.5.sp, color = TossTextTertiary)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = txt, onValueChange = { txt = it },
                        placeholder = { Text("예: 계좌 물어보면 → \"국민 123-45 디테일라인이에요 😊\"") },
                        modifier = Modifier.fillMaxWidth().height(110.dp))
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.addExample(txt); exampleOpen = false }, enabled = txt.isNotBlank()) {
                    Text("배우기", color = if (txt.isNotBlank()) Purple else TossTextTertiary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { exampleOpen = false }) { Text("취소", color = TossTextSecondary) } },
            containerColor = Color.White
        )
    }

    if (signatureOpen) {
        var txt by remember { mutableStateOf(state.signature) }
        AlertDialog(
            onDismissRequest = { signatureOpen = false },
            title = { Text("꼭 쓰는 인사말", fontWeight = FontWeight.Bold, color = TossTextPrimary) },
            text = {
                Column {
                    Text("답장 끝에 자동으로 붙일 시그니처예요.", fontSize = 12.5.sp, color = TossTextTertiary)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = txt, onValueChange = { txt = it },
                        placeholder = { Text("예: 편하게 문의주세요!") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.setSignature(txt); signatureOpen = false }) {
                    Text("저장", color = Purple, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { signatureOpen = false }) { Text("취소", color = TossTextSecondary) } },
            containerColor = Color.White
        )
    }
}

@Composable
private fun SecSub(text: String) {
    Text(text, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = TossTextTertiary,
        modifier = Modifier.padding(start = 2.dp, top = 6.dp))
}

@Composable
private fun TraitChip(k: String, v: String, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(12.dp)).background(Color.White).padding(horizontal = 12.dp, vertical = 11.dp)
    ) {
        Text(k, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Purple)
        Spacer(Modifier.height(3.dp))
        Text(v, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary)
    }
}

@Composable
private fun PlaceholderCard(text: String) {
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White).padding(16.dp)) {
        Text(text, fontSize = 12.5.sp, color = TossTextTertiary, lineHeight = 18.sp)
    }
}

@Composable
private fun SourceRow(emoji: String, title: String, sub: String, count: Int) {
    TossCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(PurpleSoft),
                contentAlignment = Alignment.Center) { Text(emoji, fontSize = 15.sp) }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary)
                Text(sub, fontSize = 11.5.sp, color = TossTextTertiary)
            }
            Text("$count", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Purple)
        }
    }
}

@Composable
private fun TeachButton(emoji: String, title: String, sub: String, onClick: () -> Unit) {
    TossCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(PurpleSoft),
                contentAlignment = Alignment.Center) { Text(emoji, fontSize = 15.sp) }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary)
                Text(sub, fontSize = 11.5.sp, color = TossTextTertiary)
            }
            Text("›", fontSize = 20.sp, color = TossTextTertiary)
        }
    }
}

@Composable
private fun ToneChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(999.dp)).background(if (selected) Purple else TossGrayBg)
            .clickable { onClick() }.padding(horizontal = 13.dp, vertical = 7.dp)
    ) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold,
            color = if (selected) Color.White else TossTextSecondary)
    }
}
