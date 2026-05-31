package com.detailline.callfollowcrm.presentation.screen.notebook

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.detailline.callfollowcrm.data.local.entity.NotebookContactEntity
import com.detailline.callfollowcrm.presentation.component.TossCard
import com.detailline.callfollowcrm.presentation.theme.TossBlue
import com.detailline.callfollowcrm.presentation.theme.TossBlueSoft
import com.detailline.callfollowcrm.presentation.theme.TossError
import com.detailline.callfollowcrm.presentation.theme.TossGrayBg
import com.detailline.callfollowcrm.presentation.theme.TossSuccess
import com.detailline.callfollowcrm.presentation.theme.TossTextPrimary
import com.detailline.callfollowcrm.presentation.theme.TossTextSecondary
import com.detailline.callfollowcrm.presentation.theme.TossTextTertiary
import com.detailline.callfollowcrm.util.PhoneNumberFormatter

/**
 * 수첩 (2026-06-01) — 일당/거래처 한 곳 관리. 설정에서 진입.
 *   탭 [일당][거래처] + 카드(이름·분류·번호·[전화][문자]) + FAB 추가 + 탭→편집/삭제.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotebookScreen(
    viewModel: NotebookViewModel,
    onBack: () -> Unit
) {
    val tab by viewModel.tabState.collectAsState()
    val workers by viewModel.workers.collectAsState()
    val vendors by viewModel.vendors.collectAsState()
    val list = if (tab == NotebookTab.WORKER) workers else vendors

    // 편집 대상: null=닫힘, id=0 추가, id>0 수정.
    var editing by remember { mutableStateOf<NotebookContactEntity?>(null) }

    Scaffold(
        containerColor = TossGrayBg,
        topBar = {
            TopAppBar(
                title = { Text("수첩", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "뒤로", tint = TossTextPrimary) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = TossGrayBg)
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    editing = NotebookContactEntity(kind = tab.kind, name = "", createdAt = 0, updatedAt = 0)
                },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("${tab.label} 추가", fontWeight = FontWeight.SemiBold) },
                containerColor = TossBlue, contentColor = Color.White
            )
        }
    ) { inner ->
        Column(Modifier.padding(inner).fillMaxSize().background(TossGrayBg)) {
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NotebookTab.values().forEach { t ->
                    val sel = t == tab
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                            .background(if (sel) TossTextPrimary else Color.White)
                            .clickable { viewModel.setTab(t) }.padding(vertical = 11.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(t.label, style = MaterialTheme.typography.titleSmall,
                            color = if (sel) Color.White else TossTextSecondary, fontWeight = FontWeight.Bold)
                    }
                }
            }
            if (list.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("📓", fontSize = 32.sp)
                        Spacer(Modifier.height(8.dp))
                        Text("${tab.label}이 아직 없어요", color = TossTextSecondary,
                            style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text("아래 '+ ${tab.label} 추가'로 등록하세요", color = TossTextTertiary,
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(list, key = { it.id }) { c ->
                        ContactCard(c, onEdit = { editing = c })
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }

    editing?.let { target ->
        ContactDialog(
            target = target,
            onSave = { name, phone, tag, memo ->
                if (target.id > 0) viewModel.update(target.id, name, phone, tag, memo)
                else viewModel.add(target.kind, name, phone, tag, memo)
                editing = null
            },
            onDelete = if (target.id > 0) { { viewModel.delete(target.id); editing = null } } else null,
            onDismiss = { editing = null }
        )
    }
}

@Composable
private fun ContactCard(c: NotebookContactEntity, onEdit: () -> Unit) {
    val context = LocalContext.current
    TossCard(onClick = onEdit) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(c.name, style = MaterialTheme.typography.titleMedium,
                        color = TossTextPrimary, fontWeight = FontWeight.SemiBold)
                    if (c.tag.isNotBlank()) {
                        Spacer(Modifier.width(6.dp))
                        Box(Modifier.clip(RoundedCornerShape(999.dp)).background(TossBlueSoft)
                            .padding(horizontal = 8.dp, vertical = 2.dp)) {
                            Text(c.tag, style = MaterialTheme.typography.labelSmall, color = TossBlue)
                        }
                    }
                }
                if (c.phone.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(PhoneNumberFormatter.format(c.phone),
                        style = MaterialTheme.typography.bodyMedium, color = TossTextSecondary)
                }
                if (c.memo.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(c.memo, style = MaterialTheme.typography.bodySmall, color = TossTextTertiary, maxLines = 1)
                }
            }
            if (c.phone.isNotBlank()) {
                ActionPill("전화", TossSuccess) {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${c.phone}")))
                    }
                }
                Spacer(Modifier.width(6.dp))
                ActionPill("문자", TossBlue) {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${c.phone}")))
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionPill(label: String, color: Color, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(999.dp)).background(color.copy(alpha = 0.12f))
            .clickable { onClick() }.padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = color, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ContactDialog(
    target: NotebookContactEntity,
    onSave: (name: String, phone: String, tag: String, memo: String) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    val kindLabel = if (target.kind == NotebookContactEntity.KIND_WORKER) "일당" else "거래처"
    var name by remember { mutableStateOf(target.name) }
    var phone by remember { mutableStateOf(target.phone) }
    var tag by remember { mutableStateOf(target.tag) }
    var memo by remember { mutableStateOf(target.memo) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (target.id > 0) "$kindLabel 수정" else "$kindLabel 추가", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                DialogField("이름", name) { name = it }
                DialogField("전화번호", phone, KeyboardType.Phone) { phone = it }
                DialogField(if (target.kind == NotebookContactEntity.KIND_WORKER) "분류 (보조/기공 등)" else "분류 (자재/협력 등)", tag) { tag = it }
                DialogField("메모", memo) { memo = it }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name, phone, tag, memo) }, enabled = name.isNotBlank()) {
                Text("저장", color = if (name.isNotBlank()) TossBlue else TossTextTertiary, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text("삭제", color = TossError) }
                    Spacer(Modifier.width(4.dp))
                }
                TextButton(onClick = onDismiss) { Text("취소", color = TossTextSecondary) }
            }
        }
    )
}

@Composable
private fun DialogField(label: String, value: String, keyboard: KeyboardType = KeyboardType.Text, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    )
}
