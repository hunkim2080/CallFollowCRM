@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.detailline.callfollowcrm.presentation.screen.principle

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.detailline.callfollowcrm.data.local.entity.PrincipleEntity
import com.detailline.callfollowcrm.presentation.theme.TossBlue
import com.detailline.callfollowcrm.presentation.theme.TossDivider
import com.detailline.callfollowcrm.presentation.theme.TossGrayBg
import com.detailline.callfollowcrm.presentation.theme.TossTextPrimary
import com.detailline.callfollowcrm.presentation.theme.TossTextSecondary
import com.detailline.callfollowcrm.presentation.theme.TossTextTertiary

@Composable
fun PrincipleManageScreen(
    viewModel: PrincipleManageViewModel,
    onBack: () -> Unit
) {
    val principles by viewModel.principles.collectAsState()
    var editing by remember { mutableStateOf<PrincipleEntity?>(null) }
    var newText by remember { mutableStateOf("") }

    Scaffold(
        containerColor = TossGrayBg,
        topBar = {
            TopAppBar(
                title = { Text("막내가 알아낸 원칙", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로", tint = TossTextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text(
                        "막내가 사장님 답변을 보고 \"왜 그렇게 하시는지\"를 알아낸 원칙이에요. 답변 만들 때 이 기준을 먼저 따라요.\n켜진 것만 반영돼요. 틀리면 수정하거나 지우세요.",
                        fontSize = 12.5.sp, color = TossTextTertiary, lineHeight = 18.sp
                    )
                    Spacer(Modifier.height(6.dp))
                }
                if (principles.isEmpty()) {
                    item {
                        Box(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White)
                                .padding(vertical = 28.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("아직 알아낸 원칙이 없어요.\n대화하다 보면 막내가 하나씩 찾아내요 🌱",
                                fontSize = 13.sp, color = TossTextTertiary, lineHeight = 19.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        }
                    }
                }
                items(principles, key = { it.id }) { p ->
                    PrincipleRow(
                        p = p,
                        onToggle = { viewModel.setEnabled(p.id, it) },
                        onEdit = { editing = p },
                        onDelete = { viewModel.delete(p.id) }
                    )
                }
            }

            // 직접 추가
            Row(
                Modifier.fillMaxWidth().background(Color.White).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(TossGrayBg).padding(horizontal = 14.dp, vertical = 13.dp)) {
                    BasicTextField(
                        value = newText, onValueChange = { newText = it },
                        textStyle = TextStyle(color = TossTextPrimary, fontSize = 14.sp),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(TossBlue),
                        decorationBox = { inner ->
                            if (newText.isEmpty()) Text("원칙 직접 추가 (예: 신축은 방문견적 먼저)", color = TossTextTertiary, fontSize = 14.sp)
                            inner()
                        }
                    )
                }
                Box(
                    Modifier.clip(RoundedCornerShape(12.dp))
                        .background(if (newText.isNotBlank()) TossBlue else TossGrayBg)
                        .clickable(enabled = newText.isNotBlank()) { viewModel.add(newText); newText = "" }
                        .padding(horizontal = 18.dp, vertical = 13.dp)
                ) { Text("추가", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (newText.isNotBlank()) Color.White else TossTextTertiary) }
            }
        }
    }

    // 수정 다이얼로그
    editing?.let { p ->
        var draft by remember(p.id) { mutableStateOf(p.text) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("원칙 수정", fontWeight = FontWeight.Bold) },
            text = {
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(TossGrayBg).padding(12.dp)) {
                    BasicTextField(
                        value = draft, onValueChange = { draft = it },
                        textStyle = TextStyle(color = TossTextPrimary, fontSize = 14.sp, lineHeight = 20.sp),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(TossBlue),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.updateText(p.id, draft); editing = null }) {
                    Text("저장", color = TossBlue, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("취소", color = TossTextSecondary) } }
        )
    }
}

@Composable
private fun PrincipleRow(
    p: PrincipleEntity,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White).padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                p.text,
                modifier = Modifier.weight(1f),
                fontSize = 14.sp, lineHeight = 20.sp,
                fontWeight = FontWeight.Medium,
                color = if (p.enabled) TossTextPrimary else TossTextTertiary
            )
            Spacer(Modifier.width(8.dp))
            Switch(
                checked = p.enabled, onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(checkedTrackColor = TossBlue)
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            val tag = if (p.source == "manual") "직접 추가" else "막내가 발견"
            Text(tag, fontSize = 11.sp, color = TossTextTertiary, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onEdit, modifier = Modifier.height(30.dp)) {
                Icon(Icons.Default.Edit, "수정", tint = TossTextSecondary, modifier = Modifier.width(18.dp))
            }
            IconButton(onClick = onDelete, modifier = Modifier.height(30.dp)) {
                Icon(Icons.Default.Delete, "삭제", tint = Color(0xFFD64545), modifier = Modifier.width(18.dp))
            }
        }
    }
}
