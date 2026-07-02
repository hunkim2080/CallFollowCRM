@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.detailline.callfollowcrm.presentation.screen.pricing

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.detailline.callfollowcrm.data.local.entity.PricingItemEntity
import com.detailline.callfollowcrm.data.repository.PricingCategory
import com.detailline.callfollowcrm.presentation.theme.TossBlue
import com.detailline.callfollowcrm.presentation.theme.TossBlueSoft
import com.detailline.callfollowcrm.presentation.theme.TossDivider
import com.detailline.callfollowcrm.presentation.theme.TossError
import com.detailline.callfollowcrm.presentation.theme.TossGrayBg
import com.detailline.callfollowcrm.presentation.theme.TossTextPrimary
import com.detailline.callfollowcrm.presentation.theme.TossTextSecondary
import com.detailline.callfollowcrm.presentation.theme.TossTextTertiary

/**
 * 가격표 관리 — 견적서 작성기에서 사용하는 항목 CRUD.
 *
 * 구성:
 *  - 카테고리별 그룹 (신축 / 구축 / 공통)
 *  - 각 항목 = 제목 + 가격 + 활성 토글 + 탭하면 편집
 *  - FAB [+ 항목 추가] → PricingItemEditDialog
 *  - 비활성 항목 (isActive=false) 은 회색으로 표시. 견적서 다이얼로그에서는 노출 안 됨.
 */
@Composable
fun PricingItemsScreen(
    viewModel: PricingItemsViewModel,
    onBack: () -> Unit
) {
    val items by viewModel.items.collectAsState()
    var editTarget by remember { mutableStateOf<PricingItemEntity?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<PricingItemEntity?>(null) }

    val grouped = remember(items) {
        items.groupBy { it.category }
    }

    Scaffold(
        containerColor = TossGrayBg,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "가격표",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TossTextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "뒤로", tint = TossTextPrimary)
                    }
                },
                actions = {
                    // 프로토 s-pricing appbar 우상단 plus
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, "항목 추가", tint = TossTextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = TossGrayBg)
            )
        }
    ) { inner ->
        if (items.isEmpty()) {
            Box(
                Modifier
                    .padding(inner)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("💰", fontSize = 40.sp)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "등록된 가격표가 없어요",
                        style = MaterialTheme.typography.titleMedium,
                        color = TossTextPrimary
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "+ 버튼으로 항목을 추가하세요",
                        style = MaterialTheme.typography.bodySmall,
                        color = TossTextTertiary
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .padding(inner)
                    .fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 프로토 renderPricing info-note — AI 안내 배너
                item(key = "ai-note") {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(TossBlueSoft)
                            .padding(horizontal = 13.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text("✨", fontSize = 13.sp)
                        Spacer(Modifier.width(7.dp))
                        Text(
                            "AI 견적이 이 단가로 자동 계산돼요. 항목을 탭하면 정액/평당과 금액을 수정할 수 있어요.",
                            fontSize = 12.5.sp, color = TossBlue, fontWeight = FontWeight.Medium, lineHeight = 17.sp
                        )
                    }
                }
                // 카테고리 순서: 신축 → 구축 → 공통
                listOf(PricingCategory.NEW, PricingCategory.OLD, PricingCategory.COMMON).forEach { cat ->
                    val group = grouped[cat.name].orEmpty()
                    if (group.isEmpty()) return@forEach
                    item(key = "header-${cat.name}") {
                        Text(
                            cat.label,
                            style = MaterialTheme.typography.labelLarge,
                            color = TossTextSecondary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                        )
                    }
                    items(group, key = { it.id }) { item ->
                        PricingItemRow(
                            item = item,
                            onTap = { editTarget = item },
                            onToggleActive = { viewModel.toggleActive(item) }
                        )
                    }
                }
                item(key = "bottom-spacer") { Spacer(Modifier.height(20.dp)) }
            }
        }
    }

    if (showAddDialog) {
        PricingItemEditDialog(
            title = "항목 추가",
            initial = null,
            onConfirm = { t, p, u, c ->
                viewModel.add(t, p, u, c)
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false }
        )
    }

    editTarget?.let { target ->
        PricingItemEditDialog(
            title = "항목 수정",
            initial = target,
            onConfirm = { t, p, u, c ->
                viewModel.update(target, t, p, u, c)
                editTarget = null
            },
            onDelete = {
                editTarget = null
                deleteTarget = target
            },
            onDismiss = { editTarget = null }
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("이 항목을 삭제할까요?", color = TossTextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "${target.title} (${formatWonShort(target.price)})\n삭제하면 복구할 수 없어요. 잠시 숨기려면 항목의 스위치를 끄세요.",
                    color = TossTextSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(target)
                    deleteTarget = null
                }) {
                    Text("삭제", color = TossError, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text("취소", color = TossTextSecondary)
                }
            },
            containerColor = Color.White
        )
    }
}

@Composable
private fun PricingItemRow(
    item: PricingItemEntity,
    onTap: () -> Unit,
    onToggleActive: () -> Unit
) {
    val alpha = if (item.isActive) 1f else 0.45f
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onTap),
        color = Color.White
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 프로토 price-row: 이름(+평당 태그) … 가격 우측 … 토글
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = TossTextPrimary.copy(alpha = alpha),
                    fontWeight = FontWeight.SemiBold
                )
                if (item.unit == PricingItemEntity.UNIT_PYEONG) {
                    Spacer(Modifier.width(6.dp))
                    Box(
                        Modifier.clip(RoundedCornerShape(6.dp)).background(TossBlueSoft)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) { Text("평당", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TossBlue) }
                }
            }
            Text(
                formatWonShort(item.price),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = TossBlue.copy(alpha = alpha)
            )
            Spacer(Modifier.width(10.dp))
            Switch(
                checked = item.isActive,
                onCheckedChange = { onToggleActive() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = TossBlue,
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = TossTextTertiary
                )
            )
        }
    }
}

@Composable
private fun PricingItemEditDialog(
    title: String,
    initial: PricingItemEntity?,
    onConfirm: (String, Long, String, PricingCategory) -> Unit,
    onDelete: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    var titleInput by remember(initial?.id) { mutableStateOf(initial?.title.orEmpty()) }
    var priceInput by remember(initial?.id) {
        // 프로토는 만원 단위 입력. 저장은 원(×10000). 표시는 만원으로 환산.
        mutableStateOf(initial?.price?.takeIf { it > 0 }?.let { (it / 10_000L).toString() }.orEmpty())
    }
    var unit by remember(initial?.id) { mutableStateOf(initial?.unit ?: PricingItemEntity.UNIT_FLAT) }
    var category by remember(initial?.id) {
        mutableStateOf(
            initial?.category?.let { name ->
                runCatching { PricingCategory.valueOf(name) }.getOrDefault(PricingCategory.NEW)
            } ?: PricingCategory.NEW
        )
    }
    val priceLong = (priceInput.toLongOrNull() ?: 0L) * 10_000L // 만원 입력 → 원 저장
    val canSave = titleInput.isNotBlank() && priceLong > 0L

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = TossTextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            // 키보드 올라오면 맨 아래 '카테고리'가 가리고 스크롤도 안 되던 문제 → 본문 스크롤 추가. (2026-07-02 사장님)
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                com.detailline.callfollowcrm.presentation.util.ForceDialogResize()
                // 프로토 sh-sub
                Text(
                    "AI 견적에 이 단가가 자동 반영돼요.",
                    fontSize = 12.5.sp, color = TossTextTertiary
                )
                LabeledField(label = "항목명") {
                    InputBox(
                        value = titleInput,
                        onValueChange = { titleInput = it },
                        placeholder = "예: 욕조 있는 화장실 바닥 1곳"
                    )
                }
                LabeledField(label = if (unit == PricingItemEntity.UNIT_PYEONG) "평당 단가 (만원)" else "가격 (만원)") {
                    InputBox(
                        value = priceInput,
                        onValueChange = { v -> priceInput = v.filter { it.isDigit() } },
                        placeholder = "예: 30",
                        keyboardType = KeyboardType.Number,
                        visualTransformation = com.detailline.callfollowcrm.presentation.component.ThousandsCommaTransformation
                    )
                }
                LabeledField(label = "계산 방식") {
                    Column {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            CategoryChip(
                                label = "정액 (한 건)",
                                selected = unit == PricingItemEntity.UNIT_FLAT,
                                onClick = { unit = PricingItemEntity.UNIT_FLAT },
                                modifier = Modifier.weight(1f)
                            )
                            CategoryChip(
                                label = "평당",
                                selected = unit == PricingItemEntity.UNIT_PYEONG,
                                onClick = { unit = PricingItemEntity.UNIT_PYEONG },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        // 프로토 wk-cat-hint — 계산 방식 설명.
                        Spacer(Modifier.height(6.dp))
                        Text(
                            if (unit == PricingItemEntity.UNIT_PYEONG)
                                "평당 단가예요. 견적 만들 때 평수를 곱해 자동 계산돼요."
                            else "한 건에 정해진 금액이에요.",
                            fontSize = 12.sp, color = TossTextTertiary, lineHeight = 17.sp
                        )
                    }
                }
                LabeledField(label = "카테고리") {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(PricingCategory.NEW, PricingCategory.OLD, PricingCategory.COMMON).forEach { c ->
                            CategoryChip(
                                label = c.label,
                                selected = category == c,
                                onClick = { category = c },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
                if (onDelete != null) {
                    Spacer(Modifier.height(4.dp))
                    TextButton(onClick = onDelete) {
                        Text("이 항목 삭제", color = TossError, fontSize = 13.sp)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (canSave) onConfirm(titleInput.trim(), priceLong, unit, category)
                },
                enabled = canSave
            ) {
                Text(
                    if (initial == null) "추가" else "저장",
                    color = if (canSave) TossBlue else TossTextTertiary,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소", color = TossTextSecondary)
            }
        },
        containerColor = Color.White
    )
}

@Composable
private fun LabeledField(label: String, content: @Composable () -> Unit) {
    Column {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = TossTextSecondary,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(6.dp))
        content()
    }
}

@Composable
private fun InputBox(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    visualTransformation: androidx.compose.ui.text.input.VisualTransformation =
        androidx.compose.ui.text.input.VisualTransformation.None
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = TextStyle(color = TossTextPrimary, fontSize = 14.sp),
        cursorBrush = SolidColor(TossBlue),
        visualTransformation = visualTransformation,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = Modifier
            .fillMaxWidth()
            .background(TossGrayBg, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        decorationBox = { inner ->
            if (value.isEmpty()) {
                Text(placeholder, color = TossTextTertiary, fontSize = 14.sp)
            }
            inner()
        }
    )
}

@Composable
private fun CategoryChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = if (selected) TossBlue else TossGrayBg,
        onClick = onClick
    ) {
        Box(
            modifier = Modifier.padding(vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                label,
                color = if (selected) Color.White else TossTextSecondary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp
            )
        }
    }
}

private fun formatWonShort(amount: Long): String {
    if (amount == 0L) return "0원"
    return if (amount >= 10_000L && amount % 10_000L == 0L) {
        "${amount / 10_000L}만원"
    } else {
        "${java.text.NumberFormat.getNumberInstance(java.util.Locale.KOREA).format(amount)}원"
    }
}
