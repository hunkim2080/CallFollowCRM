@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.detailline.callfollowcrm.presentation.screen.template

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.detailline.callfollowcrm.presentation.theme.TossBlue
import com.detailline.callfollowcrm.presentation.theme.TossBlueSoft
import com.detailline.callfollowcrm.presentation.theme.TossGrayBg
import com.detailline.callfollowcrm.presentation.theme.TossTextPrimary
import com.detailline.callfollowcrm.presentation.theme.TossTextSecondary
import com.detailline.callfollowcrm.presentation.theme.TossTextTertiary

/**
 * 자주 쓰는 문자 찾기 — 반복해서 보낸 문자를 템플릿으로 저장 제안. ViewModel = TemplateDiscoverViewModel.
 * 서버 없이 폰에서 빈도만 세서 보여줌. 프로토 외 신규 흐름(사장님 2026-07-02 요청).
 */
@Composable
fun TemplateDiscoverScreen(
    viewModel: TemplateDiscoverViewModel,
    onBack: () -> Unit
) {
    val ui by viewModel.ui.collectAsState()

    Scaffold(
        containerColor = TossGrayBg,
        topBar = {
            TopAppBar(
                title = { Text("자주 쓰는 문자 찾기", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "뒤로") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (ui.phase) {
                TemplateDiscoverViewModel.Phase.LOADING -> Center {
                    CircularProgressIndicator(color = TossBlue)
                    Spacer(Modifier.height(18.dp))
                    Text("문자에서 자주 보낸 문구를 찾는 중…", color = TossTextSecondary, fontSize = 15.sp)
                }
                TemplateDiscoverViewModel.Phase.EMPTY -> Center {
                    Text("📝", fontSize = 40.sp)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "여러 번 보낸 문자가 아직 없어요.\n템플릿은 직접 만들 수도 있어요.",
                        color = TossTextSecondary, fontSize = 15.sp, textAlign = TextAlign.Center, lineHeight = 22.sp
                    )
                    Spacer(Modifier.height(22.dp))
                    Button(
                        onClick = onBack,
                        colors = ButtonDefaults.buttonColors(containerColor = TossBlue),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("직접 만들기", color = Color.White, fontWeight = FontWeight.Bold) }
                }
                TemplateDiscoverViewModel.Phase.LIST -> Column(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxWidth().background(Color.White).padding(20.dp)) {
                        Text("💬  이 문자들을 자주 보내셨네요", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "템플릿으로 저장하면 다음부턴 채팅·자동문자에서 한 번에 불러 써요.\n" +
                                "숫자는 비슷하게 보낸 문자를 모두 합친 거예요. 아래는 예시 한 개이고, 날짜·금액은 저장 후 바꿔 쓰면 돼요.",
                            fontSize = 13.sp, color = TossTextSecondary, lineHeight = 19.sp
                        )
                    }
                    LazyColumn(
                        Modifier.weight(1f),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(ui.rows, key = { it.key }) { row ->
                            Column(
                                Modifier.fillMaxWidth()
                                    .background(Color.White, RoundedCornerShape(14.dp))
                                    .padding(16.dp)
                            ) {
                                Box(
                                    Modifier.background(TossBlueSoft, RoundedCornerShape(8.dp))
                                        .padding(horizontal = 9.dp, vertical = 4.dp)
                                ) {
                                    Text("비슷한 문자 ${row.count}번", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TossBlue)
                                }
                                Spacer(Modifier.height(10.dp))
                                Text("예시", fontSize = 11.sp, color = TossTextTertiary, fontWeight = FontWeight.Medium)
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    row.body,
                                    fontSize = 14.sp, color = TossTextPrimary, lineHeight = 20.sp,
                                    maxLines = 5, overflow = TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.height(12.dp))
                                if (row.saved) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Filled.Check, contentDescription = null, tint = TossBlue, modifier = Modifier.height(18.dp))
                                        Spacer(Modifier.height(0.dp))
                                        Text(" 템플릿에 저장됨", color = TossBlue, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    }
                                } else {
                                    Button(
                                        onClick = { viewModel.save(row.key) },
                                        modifier = Modifier.fillMaxWidth().height(44.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = TossBlue),
                                        shape = RoundedCornerShape(12.dp)
                                    ) { Text("템플릿으로 저장", color = Color.White, fontWeight = FontWeight.Bold) }
                                }
                            }
                        }
                        item { Spacer(Modifier.height(20.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Center(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) { content() }
}
