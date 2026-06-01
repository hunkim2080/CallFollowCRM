package com.detailline.callfollowcrm.presentation.screen.search

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.LaunchedEffect
import com.detailline.callfollowcrm.presentation.theme.TossBlue
import com.detailline.callfollowcrm.presentation.theme.TossGrayBg
import com.detailline.callfollowcrm.presentation.theme.TossTextPrimary
import com.detailline.callfollowcrm.presentation.theme.TossTextSecondary
import com.detailline.callfollowcrm.presentation.theme.TossTextTertiary
import com.detailline.callfollowcrm.util.PhoneNumberFormatter

/**
 * 검색 화면 (프로토 s-search) — 앱바에 검색 입력칸, 아래 결과 목록.
 *   결과 탭 = 채팅으로. 진입 시 자동 키보드 포커스.
 */
@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    onBack: () -> Unit,
    onOpenChat: (phone: String, customerId: Long?) -> Unit
) {
    val query by viewModel.queryState.collectAsState()
    val results by viewModel.results.collectAsState()
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) { focus.requestFocus() }

    Column(
        Modifier
            .fillMaxSize()
            .background(TossGrayBg)
    ) {
        // 앱바 — 뒤로 + 검색 입력칸
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 6.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로", tint = TossTextPrimary)
            }
            Box(
                Modifier
                    .weight(1f)
                    .background(Color.White, RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (query.isEmpty()) {
                    Text("이름·전화번호·메시지 검색", fontSize = 15.sp, color = TossTextTertiary)
                }
                BasicTextField(
                    value = query,
                    onValueChange = { viewModel.setQuery(it) },
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 15.sp, color = TossTextPrimary),
                    cursorBrush = SolidColor(TossBlue),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus)
                )
            }
            if (query.isNotEmpty()) {
                IconButton(onClick = { viewModel.setQuery("") }) {
                    Icon(Icons.Filled.Close, "지우기", tint = TossTextTertiary, modifier = Modifier.size(20.dp))
                }
            }
        }

        when {
            query.isBlank() -> CenterHint("이름·전화번호·메시지로\n빠르게 찾아보세요")
            results.isEmpty() -> CenterHint("‘$query’ 검색 결과가 없어요")
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(results, key = { it.phone }) { r ->
                    ResultCard(r) {
                        keyboard?.hide()
                        onOpenChat(r.phone, r.customerId)
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultCard(r: SearchResult, onClick: () -> Unit) {
    val title = r.name ?: PhoneNumberFormatter.format(r.phone)
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary)
        if (r.name != null) {
            Spacer(Modifier.height(2.dp))
            Text(PhoneNumberFormatter.format(r.phone), fontSize = 12.sp, color = TossTextTertiary)
        }
        if (!r.snippet.isNullOrBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(r.snippet, fontSize = 13.sp, color = TossTextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun CenterHint(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text,
            fontSize = 14.sp,
            color = TossTextTertiary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            lineHeight = 21.sp
        )
    }
}
