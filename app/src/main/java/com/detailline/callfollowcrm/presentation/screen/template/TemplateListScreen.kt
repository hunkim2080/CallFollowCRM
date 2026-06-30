package com.detailline.callfollowcrm.presentation.screen.template

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.detailline.callfollowcrm.data.local.entity.MessageTemplateEntity
import com.detailline.callfollowcrm.presentation.component.TossCard
import com.detailline.callfollowcrm.presentation.theme.TossBlue
import com.detailline.callfollowcrm.presentation.theme.TossBlueSoft
import com.detailline.callfollowcrm.presentation.theme.TossDivider
import com.detailline.callfollowcrm.presentation.theme.TossError
import com.detailline.callfollowcrm.presentation.theme.TossGrayBg
import com.detailline.callfollowcrm.presentation.theme.TossTextPrimary
import com.detailline.callfollowcrm.presentation.theme.TossTextSecondary
import com.detailline.callfollowcrm.presentation.theme.TossTextTertiary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateListScreen(
    viewModel: TemplateListViewModel,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onNew: () -> Unit
) {
    val templates by viewModel.templates.collectAsState()
    var renameTarget by remember { mutableStateOf<MessageTemplateEntity?>(null) }
    var deleteTarget by remember { mutableStateOf<MessageTemplateEntity?>(null) }

    Scaffold(
        containerColor = TossGrayBg,
        topBar = {
            TopAppBar(
                title = {
                    Text("문자 템플릿", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "뒤로", tint = TossTextPrimary)
                    }
                },
                actions = {
                    // 프로토 앱바 + (addTemplate)
                    Box(
                        Modifier.padding(end = 12.dp).size(38.dp).clip(RoundedCornerShape(11.dp))
                            .background(Color.White).clickable { onNew() },
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Default.Add, "새 템플릿", tint = TossBlue, modifier = Modifier.size(20.dp)) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = TossGrayBg)
            )
        }
    ) { inner ->
        Column(
            Modifier.padding(inner).fillMaxSize().background(TossGrayBg)
        ) {
            if (templates.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("📝", fontSize = 40.sp)
                        Spacer(Modifier.height(12.dp))
                        Text("아직 템플릿이 없어요", style = MaterialTheme.typography.titleMedium, color = TossTextPrimary)
                        Spacer(Modifier.height(4.dp))
                        Text("위 + 버튼으로 새 템플릿을 만들어보세요",
                            style = MaterialTheme.typography.bodySmall, color = TossTextTertiary)
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 프로토 info-note
                    item(key = "info-note") {
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(TossBlueSoft)
                                .padding(horizontal = 14.dp, vertical = 13.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text("💬", fontSize = 13.sp)
                            Spacer(Modifier.padding(3.dp))
                            Text("채팅·자동문자에서 불러 쓰는 문구예요. 끄면 목록에 안 떠요.",
                                style = MaterialTheme.typography.bodySmall, color = TossBlue,
                                modifier = Modifier.weight(1f))
                        }
                    }
                    items(templates, key = { it.id }) { t ->
                        // 프로토 tpl-card — 제목+토글 / 본문(탭=문구수정) / 액션(이름·문구·삭제). 꺼지면 흐리게.
                        val dim = if (t.isActive) 1f else 0.5f
                        TossCard {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(t.title, style = MaterialTheme.typography.titleLarge,
                                        color = TossTextPrimary.copy(alpha = dim), modifier = Modifier.weight(1f))
                                    Switch(
                                        checked = t.isActive,
                                        onCheckedChange = { viewModel.toggleActive(t.id, it) },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = Color.White, checkedTrackColor = TossBlue,
                                            uncheckedThumbColor = Color.White, uncheckedTrackColor = TossDivider,
                                            uncheckedBorderColor = TossDivider
                                        )
                                    )
                                }
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    t.body, style = MaterialTheme.typography.bodyMedium,
                                    color = TossTextSecondary.copy(alpha = dim),
                                    modifier = Modifier.fillMaxWidth().clickable { onEdit(t.id) }
                                )
                                // 프로토 tpl-actions
                                Spacer(Modifier.height(11.dp))
                                Box(Modifier.fillMaxWidth().height(1.dp).background(TossDivider))
                                Spacer(Modifier.height(11.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("이름 수정", fontSize = 12.5.sp, fontWeight = FontWeight.Bold,
                                        color = TossTextTertiary, modifier = Modifier.clickable { renameTarget = t })
                                    Spacer(Modifier.width(18.dp))
                                    Text("문구 수정", fontSize = 12.5.sp, fontWeight = FontWeight.Bold,
                                        color = TossTextTertiary, modifier = Modifier.clickable { onEdit(t.id) })
                                    Spacer(Modifier.weight(1f))
                                    Text("삭제", fontSize = 12.5.sp, fontWeight = FontWeight.Bold,
                                        color = TossError, modifier = Modifier.clickable { deleteTarget = t })
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(20.dp)) }
                }
            }
        }
    }

    // 이름 수정 다이얼로그 (프로토 editTemplateName)
    renameTarget?.let { t ->
        var name by remember(t.id) { mutableStateOf(t.title) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("템플릿 이름 수정", fontWeight = FontWeight.Bold, color = TossTextPrimary) },
            text = {
                com.detailline.callfollowcrm.presentation.util.ForceDialogResize()
                OutlinedTextField(value = name, onValueChange = { name = it },
                    placeholder = { Text("예: 주차 안내") }, modifier = Modifier.fillMaxWidth())
            },
            confirmButton = {
                TextButton(onClick = {
                    if (name.isNotBlank()) viewModel.rename(t.id, name)
                    renameTarget = null
                }) { Text("저장", color = if (name.isNotBlank()) TossBlue else TossTextTertiary, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("취소", color = TossTextSecondary) } },
            containerColor = Color.White
        )
    }

    // 삭제 확인
    deleteTarget?.let { t ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("\"${t.title}\" 삭제", fontWeight = FontWeight.Bold, color = TossTextPrimary) },
            text = { Text("이 템플릿을 삭제할까요?", color = TossTextSecondary) },
            confirmButton = {
                TextButton(onClick = { viewModel.delete(t.id); deleteTarget = null }) {
                    Text("삭제", color = TossError, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("취소", color = TossTextSecondary) } },
            containerColor = Color.White
        )
    }
}
