package com.detailline.callfollowcrm.presentation.screen.spam

import com.detailline.callfollowcrm.presentation.theme.AppTheme
import com.detailline.callfollowcrm.presentation.theme.AppShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.detailline.callfollowcrm.data.AppContainer
import com.detailline.callfollowcrm.presentation.theme.TossGrayBg
import com.detailline.callfollowcrm.presentation.theme.TossTextPrimary
import com.detailline.callfollowcrm.presentation.theme.TossTextTertiary

/**
 * 「지인 번호」 — 시공막내가 안 보는 사생활 번호 목록, 더보기 진입. (2026-06-23 사장님)
 *   상담함에서 카드 밀어 '지인' 누른 번호가 모이는 곳. 잘못 넣었으면 '해제'로 바로 복구.
 *   해제 = unmark + 바구니 풀기 → 다시 상담함·신규에 보임. 옛 데이터(phoneNumber="")는 suffix 로 표시.
 *
 *   ⚠️ 「스팸」(kind="spam") 은 2026-10-16 부터 여기 없다 — 더보기 「스팸·광고 거르기」([SpamHubScreen] ③) 로 갔다.
 *      줄 그리기·풀기는 [SpamPhoneRows] 한 곳을 둘이 같이 쓴다(§12 두 벌 금지).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpamListScreen(
    container: AppContainer,
    onBack: () -> Unit
) {
    val all by container.spamPhoneRepository.all.collectAsState(initial = emptyList())
    val list = all.filter { it.kind == "personal" }

    Scaffold(
        containerColor = TossGrayBg,
        topBar = {
            TopAppBar(
                title = { Text("지인 번호", fontWeight = FontWeight.Bold, color = TossTextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로", tint = TossTextPrimary)
                    }
                },
                // 앱의 다른 26개 화면과 같은 회색. 흰 띠면 바탕과 사이에 이음매가 생긴다. (2026-09-22 사장님)
                colors = TopAppBarDefaults.topAppBarColors(containerColor = TossGrayBg)
            )
        }
    ) { padding ->
        if (list.isEmpty()) {
            Box(
                Modifier.fillMaxSize().padding(padding).padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                // 🔴 전엔 **진짜 이모지**(👤)를 42sp 로 띄웠다 — 폰마다 다르게 그려진다.
                //   앱이 그리는 아이콘으로, 협업 현장 빈 화면과 같은 모양. (2026-09-22 사장님)
                Column(
                    Modifier.fillMaxWidth().clip(AppShape.lg).background(Color.White)
                        .padding(vertical = 26.dp, horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        Modifier.size(46.dp).clip(AppShape.md).background(AppTheme.colors.categoryBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Person, null, tint = AppTheme.colors.category, modifier = Modifier.size(23.dp))
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("지인으로 옮긴 번호가 없어요", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "상담함에서 카드를 밀어 '지인' 을 누르면 여기 모여요.\n잘못 넣어도 여기서 바로 풀 수 있어요.",
                        fontSize = 12.5.sp, color = TossTextTertiary, textAlign = TextAlign.Center, lineHeight = 19.sp
                    )
                }
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text(
                        "지인으로 옮긴 번호예요. 문자함 [지인] 에서 보이고, 시공막내가 안 건드려요. '해제'하면 상담함으로 돌아와요.",
                        fontSize = 12.5.sp, color = TossTextTertiary,
                        modifier = Modifier.padding(start = 2.dp, bottom = 4.dp)
                    )
                }
                // 묶음 하나 = 카드 하나, 줄 사이는 가는 선. (2026-09-22 사장님) 줄은 SpamPhoneRows 한 곳.
                item(key = "personal-card") {
                    Column(Modifier.fillMaxWidth().clip(AppShape.lg).background(Color.White)) {
                        SpamPhoneRows(
                            container = container,
                            entries = list,
                            actionLabel = "해제",
                            undoToast = "지인에서 풀었어요 — 상담함으로 돌아와요",
                            numberFirst = false,
                            outlinedAction = false
                        )
                    }
                }
            }
        }
    }
}
