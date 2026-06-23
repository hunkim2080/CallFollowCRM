package com.detailline.callfollowcrm.presentation.screen.spam

import android.widget.Toast
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.detailline.callfollowcrm.data.AppContainer
import com.detailline.callfollowcrm.data.local.entity.SpamPhoneEntity
import com.detailline.callfollowcrm.presentation.theme.TossBlue
import com.detailline.callfollowcrm.presentation.theme.TossBlueSoft
import com.detailline.callfollowcrm.presentation.theme.TossGrayBg
import com.detailline.callfollowcrm.presentation.theme.TossTextPrimary
import com.detailline.callfollowcrm.presentation.theme.TossTextTertiary
import com.detailline.callfollowcrm.util.PhoneNumberFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 스팸 차단 번호 목록 — 더보기 진입. (2026-06-23 사장님)
 *   상담함에서 카드 밀어 '스팸' 누른 번호가 모이는 곳. 잘못 등록했으면 '해제'로 바로 복구.
 *   해제 = spamPhoneRepository.unmark → 다시 상담함·신규 목록에 보임.
 *   데이터는 container 직접 구독(별도 ViewModel 없이). 옛 데이터(phoneNumber="")는 suffix 로 표시.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpamListScreen(
    container: AppContainer,
    onBack: () -> Unit
) {
    val list by container.spamPhoneRepository.all.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    Scaffold(
        containerColor = TossGrayBg,
        topBar = {
            TopAppBar(
                title = { Text("스팸 차단 번호", fontWeight = FontWeight.Bold, color = TossTextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로", tint = TossTextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }
    ) { padding ->
        if (list.isEmpty()) {
            Box(
                Modifier.fillMaxSize().padding(padding).padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🛡️", fontSize = 42.sp)
                    Spacer(Modifier.height(12.dp))
                    Text("스팸으로 등록한 번호가 없어요", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "상담함에서 카드를 밀어 '스팸'을 누르면 여기 모여요.\n잘못 넣어도 여기서 바로 풀 수 있어요.",
                        fontSize = 12.5.sp, color = TossTextTertiary, textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Text(
                        "잘못 등록했으면 '해제'를 누르세요 — 다시 상담함·신규에 보여요.",
                        fontSize = 12.5.sp, color = TossTextTertiary,
                        modifier = Modifier.padding(start = 2.dp, bottom = 4.dp)
                    )
                }
                items(list, key = { it.phoneSuffix }) { entry ->
                    SpamRow(
                        entry = entry,
                        onUnmark = {
                            scope.launch {
                                container.spamPhoneRepository.unmark(entry.phoneSuffix)
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "스팸을 풀었어요 — 다시 보여요", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SpamRow(entry: SpamPhoneEntity, onUnmark: () -> Unit) {
    val numberText = when {
        entry.phoneNumber.isNotBlank() -> PhoneNumberFormatter.format(entry.phoneNumber)
        entry.phoneSuffix.length >= 8 ->
            entry.phoneSuffix.takeLast(8).let { "끝 ${it.substring(0, 4)}-${it.substring(4)}" }
        else -> "끝 ${entry.phoneSuffix}"
    }
    val name = entry.displayName?.takeIf { it.isNotBlank() }

    Row(
        Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(40.dp).background(Color(0xFFFFF1F3), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Block, null, tint = Color(0xFFF0436A), modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(name ?: numberText, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary)
            if (name != null) {
                Spacer(Modifier.height(2.dp))
                Text(numberText, fontSize = 12.5.sp, color = TossTextTertiary)
            }
        }
        Text(
            "해제",
            color = TossBlue, fontSize = 13.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(TossBlueSoft)
                .clickable(onClick = onUnmark)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}
