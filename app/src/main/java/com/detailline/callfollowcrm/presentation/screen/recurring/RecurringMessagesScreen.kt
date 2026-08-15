package com.detailline.callfollowcrm.presentation.screen.recurring

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.detailline.callfollowcrm.data.local.entity.CategoryEntity
import com.detailline.callfollowcrm.data.local.entity.RecurringMessageEntity
import com.detailline.callfollowcrm.presentation.component.TossCard
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.CalendarMonth
import com.detailline.callfollowcrm.presentation.theme.TossBlue
import com.detailline.callfollowcrm.presentation.theme.TossBlueSoft
import com.detailline.callfollowcrm.presentation.theme.TossDivider
import com.detailline.callfollowcrm.presentation.theme.TossError
import com.detailline.callfollowcrm.presentation.theme.TossGrayBg
import com.detailline.callfollowcrm.presentation.theme.TossTextPrimary
import com.detailline.callfollowcrm.presentation.theme.TossTextSecondary
import com.detailline.callfollowcrm.presentation.theme.TossTextTertiary
import com.detailline.callfollowcrm.util.DateTimeUtils

/**
 * 정기문자 규칙 관리 화면 (2026-06-01) — 설정 → 정기문자.
 *   시공 후 N일마다 보낼 안부/점검 문자 규칙 CRUD. 실제 발송은 사장님이 "보낼 때 됐어요" 목록에서 확인 후.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringMessagesScreen(
    viewModel: RecurringMessagesViewModel,
    onBack: () -> Unit
) {
    val rules by viewModel.rules.collectAsState()
    val categories by viewModel.categories.collectAsState()
    var editing by remember { mutableStateOf<RecurringMessageEntity?>(null) }
    var showEditor by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = TossGrayBg,
        topBar = {
            TopAppBar(
                title = { Text("정기문자", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "뒤로", tint = TossTextPrimary) }
                },
                actions = {
                    // 프로토 s-recurring appbar 우상단 plus
                    IconButton(onClick = { editing = null; showEditor = true }) {
                        Icon(Icons.Default.Add, "규칙 추가", tint = TossTextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = TossGrayBg)
            )
        }
    ) { inner ->
        LazyColumn(
            modifier = Modifier.padding(inner).fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 프로토 info-note — 각 고객 날짜 기준 안내
            item {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(TossBlueSoft)
                        .padding(horizontal = 13.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(Icons.Default.CalendarMonth, null, tint = TossBlue, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(7.dp))
                    Text(
                        "고객마다 시공 날짜가 다르죠? 각 고객 시공일 기준으로 다 같은 날 보내는 게 아니라 각자 때 맞춰 한 명씩 알려드려요. {고객명}은 자동으로 채워져요. (자동 발송은 안 하고 사장님이 확인 후 보냄)",
                        fontSize = 12.5.sp, color = TossBlue, fontWeight = FontWeight.Medium, lineHeight = 17.sp
                    )
                }
            }
            // 프로토 invite — 정기 문자 만들기
            item {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                        .border(1.5.dp, TossDivider, RoundedCornerShape(14.dp))
                        .clickable { editing = null; showEditor = true }
                        .padding(vertical = 14.dp),
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Add, null, tint = TossBlue, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("정기 문자 만들기", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TossBlue)
                }
            }
            if (rules.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("🔁", fontSize = 34.sp)
                            Spacer(Modifier.height(8.dp))
                            Text("아직 정기문자 규칙이 없어요", color = TossTextSecondary, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.height(2.dp))
                            Text("예: 시공 30일 후 점검 안부", style = MaterialTheme.typography.bodySmall, color = TossTextTertiary)
                        }
                    }
                }
            } else {
                item {
                    Text("예약된 정기 문자 ${rules.size}개", fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        color = TossTextTertiary, modifier = Modifier.padding(top = 2.dp, start = 2.dp))
                }
                items(rules, key = { it.id }) { rule ->
                    RuleCard(
                        rule = rule,
                        categoryName = rule.targetCategoryId?.let { cid -> categories.firstOrNull { it.id == cid }?.name },
                        onClick = { editing = rule; showEditor = true },
                        onToggle = { viewModel.setEnabled(rule.id, it) }
                    )
                }
            }
            item { Spacer(Modifier.height(60.dp)) }
        }
    }

    if (showEditor) {
        RuleEditorDialog(
            initial = editing,
            categories = categories,
            onDismiss = { showEditor = false },
            onDelete = editing?.let { e -> { viewModel.delete(e.id); showEditor = false } },
            onSave = { name, catId, interval, minutes, body ->
                val e = editing
                if (e == null) viewModel.add(name, catId, interval, minutes, body)
                else viewModel.update(e.id, name, catId, interval, minutes, body, e.enabled)
                showEditor = false
            }
        )
    }
}

@Composable
private fun RuleCard(
    rule: RecurringMessageEntity,
    categoryName: String?,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit
) {
    TossCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(rule.name, style = MaterialTheme.typography.titleMedium,
                    color = if (rule.enabled) TossTextPrimary else TossTextTertiary, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(3.dp))
                Text(
                    "각 고객 시공일 +${rule.intervalDays}일 · ${categoryName ?: "전체 고객"} · ${DateTimeUtils.formatWorkMinutes(rule.sendMinutes)}",
                    style = MaterialTheme.typography.bodySmall, color = TossTextSecondary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    com.detailline.callfollowcrm.domain.recurring.RecurringDueCalc.render(rule.bodyTemplate, null),
                    style = MaterialTheme.typography.bodySmall, color = TossTextTertiary, maxLines = 2
                )
            }
            Switch(
                checked = rule.enabled, onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(checkedTrackColor = TossBlue)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RuleEditorDialog(
    initial: RecurringMessageEntity?,
    categories: List<CategoryEntity>,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)?,
    onSave: (name: String, categoryId: Long?, interval: Int, minutes: Int, body: String) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var interval by remember { mutableStateOf(initial?.intervalDays ?: 30) }
    var confirmDelete by remember { mutableStateOf(false) }   // 규칙 삭제 확인(앱 기조 통일). 2026-07-30
    var minutes by remember { mutableStateOf(initial?.sendMinutes ?: 600) }
    var categoryId by remember { mutableStateOf(initial?.targetCategoryId) }
    var body by remember { mutableStateOf(initial?.bodyTemplate ?: "{고객명}님, 안녕하세요 😊 시공하신 지 한 달 됐어요. 줄눈 상태 점검 도와드릴까요?") }

    Dialog(onDismissRequest = onDismiss) {
        androidx.compose.material3.Surface(shape = RoundedCornerShape(20.dp), color = Color.White,
            modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState())) {
                com.detailline.callfollowcrm.presentation.util.ForceDialogResize()
                Text(if (initial == null) "정기 문자 만들기" else "규칙 수정",
                    style = MaterialTheme.typography.titleLarge, color = TossTextPrimary, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(2.dp))
                Text("규칙만 정해두면 막내가 날짜를 계산해서 보낼 때 알려드려요.",
                    fontSize = 12.5.sp, color = TossTextTertiary)
                Spacer(Modifier.height(20.dp))

                EditorLabel("이름")
                com.detailline.callfollowcrm.presentation.component.SheetTextField(
                    name, { name = it }, placeholder = "예: 시공 한 달 점검")

                Spacer(Modifier.height(18.dp))
                EditorLabel("주기 (시공일로부터)")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("30일마다" to 30, "60일마다" to 60, "90일마다" to 90, "6개월마다" to 180, "매년" to 365).forEach { (l, v) ->
                        PickChip(l, interval == v) { interval = v }
                    }
                }

                Spacer(Modifier.height(18.dp))
                EditorLabel("보낼 대상")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PickChip("전체 고객", categoryId == null) { categoryId = null }
                    categories.forEach { cat ->
                        val label = if (cat.emoji != null) "${cat.emoji} ${cat.name}" else cat.name
                        PickChip(label, categoryId == cat.id) { categoryId = cat.id }
                    }
                }

                Spacer(Modifier.height(18.dp))
                EditorLabel("발송 시각 (표시용 · 자동발송 안 함)")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "오전 8시" to 480, "오전 9시" to 540, "오전 10시" to 600,
                        "오전 11시" to 660, "오후 1시" to 780, "오후 6시" to 1080
                    ).forEach { (l, v) ->
                        PickChip(l, minutes == v) { minutes = v }
                    }
                }

                Spacer(Modifier.height(18.dp))
                EditorLabel("문구 ({고객명} = 고객 이름으로 자동 치환)")
                com.detailline.callfollowcrm.presentation.component.SheetTextField(
                    body, { body = it }, placeholder = "{고객명}님, ...", singleLine = false, minHeightDp = 76)
                Spacer(Modifier.height(6.dp))
                TextButton(onClick = { if (!body.contains("{고객명}")) body = "{고객명}님, " + body }) {
                    Text("+ {고객명} 넣기", color = TossBlue)
                }

                Spacer(Modifier.height(16.dp))
                val canSaveRecur = name.isNotBlank()   // 이름 비면 저장 막기 — 수첩(NotebookScreen) 패턴과 통일. (2026-08-15 UX감사#13)
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                        .background(if (canSaveRecur) TossBlue else Color(0xFFB8C0CC))
                        .then(if (canSaveRecur) Modifier.clickable { onSave(name, categoryId, interval, minutes, body) } else Modifier)
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) { Text(if (canSaveRecur) "예약하기" else "이름을 먼저 입력하세요", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp) }

                if (onDelete != null) {
                    Spacer(Modifier.height(6.dp))
                    TextButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("이 규칙 삭제", color = TossError, fontWeight = FontWeight.SemiBold)
                    }
                    if (confirmDelete) androidx.compose.material3.AlertDialog(
                        onDismissRequest = { confirmDelete = false },
                        title = { Text("이 규칙을 지울까요?", fontWeight = FontWeight.Bold) },
                        text = { Text("이 정기문자 규칙이 삭제돼요. 잠깐 끄기만 할 수도 있어요.") },
                        confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("지울게요", color = TossError, fontWeight = FontWeight.Bold) } },
                        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("취소", color = TossTextSecondary) } }
                    )
                }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text("닫기", color = TossTextSecondary)
                }
            }
        }
    }
}

@Composable
private fun EditorLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = TossTextSecondary,
        fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 8.dp))
}

@Composable
private fun PickChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(999.dp))
            .background(if (selected) TossBlue else TossGrayBg)
            .clickable { onClick() }
            .padding(horizontal = 15.dp, vertical = 11.dp)
    ) {
        Text(label, color = if (selected) Color.White else TossTextSecondary,
            fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
    }
}
