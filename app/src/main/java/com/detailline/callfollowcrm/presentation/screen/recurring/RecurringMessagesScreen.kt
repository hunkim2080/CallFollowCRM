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
import com.detailline.callfollowcrm.presentation.theme.TossBlue
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = TossGrayBg)
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { editing = null; showEditor = true },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("규칙 추가", fontWeight = FontWeight.SemiBold) },
                containerColor = TossBlue, contentColor = Color.White
            )
        }
    ) { inner ->
        LazyColumn(
            modifier = Modifier.padding(inner).fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    "시공 후 일정 기간마다 보낼 안부·점검 문자를 미리 정해두면, 보낼 때가 됐을 때 상담함에서 알려드려요. (자동 발송은 안 하고 사장님이 확인 후 보냄)",
                    style = MaterialTheme.typography.bodySmall, color = TossTextTertiary
                )
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
                    "시공 ${rule.intervalDays}일마다 · ${categoryName ?: "시공 전체"} · ${DateTimeUtils.formatWorkMinutes(rule.sendMinutes)}",
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
    var minutes by remember { mutableStateOf(initial?.sendMinutes ?: 600) }
    var categoryId by remember { mutableStateOf(initial?.targetCategoryId) }
    var body by remember { mutableStateOf(initial?.bodyTemplate ?: "{고객명}님, 디테일라인입니다 😊 시공하신 지 한 달 됐어요. 줄눈 상태 점검 도와드릴까요?") }

    Dialog(onDismissRequest = onDismiss) {
        androidx.compose.material3.Surface(shape = RoundedCornerShape(20.dp), color = Color.White,
            modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState())) {
                Text(if (initial == null) "정기문자 규칙 추가" else "규칙 수정",
                    style = MaterialTheme.typography.titleLarge, color = TossTextPrimary, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(14.dp))

                EditorLabel("이름")
                OutlinedTextField(name, { name = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("예: 시공 한 달 점검") })

                Spacer(Modifier.height(12.dp))
                EditorLabel("주기 (시공일로부터)")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("30일" to 30, "60일" to 60, "90일" to 90, "180일" to 180, "1년" to 365).forEach { (l, v) ->
                        PickChip(l, interval == v) { interval = v }
                    }
                }

                Spacer(Modifier.height(12.dp))
                EditorLabel("보낼 대상")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PickChip("시공 전체", categoryId == null) { categoryId = null }
                    categories.forEach { cat ->
                        val label = if (cat.emoji != null) "${cat.emoji} ${cat.name}" else cat.name
                        PickChip(label, categoryId == cat.id) { categoryId = cat.id }
                    }
                }

                Spacer(Modifier.height(12.dp))
                EditorLabel("권장 시각 (표시용)")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("오전 9시" to 540, "오전 10시" to 600, "오전 11시" to 660, "오후 2시" to 840).forEach { (l, v) ->
                        PickChip(l, minutes == v) { minutes = v }
                    }
                }

                Spacer(Modifier.height(12.dp))
                EditorLabel("문구 ({고객명} = 고객 이름으로 자동 치환)")
                OutlinedTextField(body, { body = it }, modifier = Modifier.fillMaxWidth(), minLines = 3,
                    placeholder = { Text("{고객명}님, ...") })
                Spacer(Modifier.height(6.dp))
                TextButton(onClick = { if (!body.contains("{고객명}")) body = "{고객명}님, " + body }) {
                    Text("+ {고객명} 넣기", color = TossBlue)
                }

                Spacer(Modifier.height(16.dp))
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(TossBlue)
                        .clickable { onSave(name, categoryId, interval, minutes, body) }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) { Text("저장", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp) }

                if (onDelete != null) {
                    Spacer(Modifier.height(6.dp))
                    TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
                        Text("이 규칙 삭제", color = TossError, fontWeight = FontWeight.SemiBold)
                    }
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
        fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 6.dp))
}

@Composable
private fun PickChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(999.dp))
            .background(if (selected) TossBlue else TossGrayBg)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 9.dp)
    ) {
        Text(label, color = if (selected) Color.White else TossTextSecondary,
            fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
    }
}
