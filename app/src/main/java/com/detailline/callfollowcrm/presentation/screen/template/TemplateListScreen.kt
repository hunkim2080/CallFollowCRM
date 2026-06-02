package com.detailline.callfollowcrm.presentation.screen.template

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.detailline.callfollowcrm.presentation.component.TossBadge
import com.detailline.callfollowcrm.presentation.component.TossCard
import com.detailline.callfollowcrm.presentation.component.TossPrimaryButton
import com.detailline.callfollowcrm.presentation.theme.TossBlue
import com.detailline.callfollowcrm.presentation.theme.TossBlueSoft
import com.detailline.callfollowcrm.presentation.theme.TossDivider
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

    Scaffold(
        containerColor = TossGrayBg,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "문자 템플릿",
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = TossGrayBg)
            )
        },
        bottomBar = {
            // 큰 "새 템플릿 만들기" CTA — 추가 동선을 명확하게.
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(TossGrayBg)
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                TossPrimaryButton(
                    text = "+ 새 템플릿 만들기",
                    onClick = onNew
                )
            }
        }
    ) { inner ->
        Column(
            Modifier
                .padding(inner)
                .fillMaxSize()
                .background(TossGrayBg)
        ) {
            if (templates.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("📝", fontSize = 40.sp)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "아직 템플릿이 없어요",
                            style = MaterialTheme.typography.titleMedium,
                            color = TossTextPrimary
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "아래 + 버튼으로 새 템플릿을 만들어보세요",
                            style = MaterialTheme.typography.bodySmall,
                            color = TossTextTertiary
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 프로토 info-note — 채팅·자동문자에서 불러 쓰는 문구 설명.
                    item(key = "info-note") {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(TossBlueSoft)
                                .padding(horizontal = 14.dp, vertical = 13.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text("💬", fontSize = 13.sp)
                            Spacer(Modifier.padding(3.dp))
                            Text(
                                "채팅·자동문자에서 불러 쓰는 문구예요. 끄면 목록에 안 떠요.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TossBlue,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    items(templates) { t ->
                        // 프로토: 꺼진 템플릿은 제목·본문을 흐리게(opacity .5).
                        val dim = if (t.isActive) 1f else 0.5f
                        TossCard(onClick = { onEdit(t.id) }) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        t.title,
                                        style = MaterialTheme.typography.titleLarge,
                                        color = TossTextPrimary.copy(alpha = dim),
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (t.isDefault) {
                                        TossBadge("기본", color = TossBlue, background = TossBlueSoft)
                                        Spacer(Modifier.padding(2.dp))
                                    }
                                    Switch(
                                        checked = t.isActive,
                                        onCheckedChange = { viewModel.toggleActive(t.id, it) },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = Color.White,
                                            checkedTrackColor = TossBlue,
                                            uncheckedThumbColor = Color.White,
                                            uncheckedTrackColor = TossDivider,
                                            uncheckedBorderColor = TossDivider
                                        )
                                    )
                                }
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    t.body,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TossTextSecondary.copy(alpha = dim),
                                    maxLines = 4
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "탭하면 수정할 수 있어요",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TossTextTertiary
                                )
                            }
                        }
                    }
                    item { Spacer(Modifier.height(8.dp)) }
                }
            }
        }
    }
}
