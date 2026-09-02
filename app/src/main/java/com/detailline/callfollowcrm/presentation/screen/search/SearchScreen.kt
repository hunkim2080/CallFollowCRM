package com.detailline.callfollowcrm.presentation.screen.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
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
import com.detailline.callfollowcrm.presentation.theme.TossDivider
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
                    Text("이름·전화·문자·통화 내용 검색", fontSize = 15.sp, color = TossTextTertiary)
                }
                BasicTextField(
                    value = query,
                    onValueChange = { viewModel.setQuery(it) },
                    singleLine = true,
                    textStyle = TextStyle(fontFamily = com.detailline.callfollowcrm.presentation.theme.Pretendard, fontSize = 15.sp, color = TossTextPrimary),
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
            // 프로토 doSearch 빈 쿼리 안내문 verbatim.
            query.isBlank() -> CenterHint("이름·전화·문자, 그리고 통화 내용까지\n뭐든 찾아보세요")
            results.isEmpty() -> CenterHint("검색 결과가 없어요")
            // 프로토: box.className='recent' → 흰 카드 하나 + recent-row 들.
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 18.dp)
            ) {
                item {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .background(Color.White, RoundedCornerShape(18.dp))
                    ) {
                        results.forEachIndexed { idx, r ->
                            if (idx > 0) {
                                Box(Modifier.fillMaxWidth().height(1.dp).background(TossDivider))
                            }
                            SearchRow(r) {
                                keyboard?.hide()
                                onOpenChat(r.phone, r.customerId)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 프로토 doSearch 결과 .recent-row — 아바타 + (이름 / 메시지 요약). */
@Composable
private fun SearchRow(r: SearchResult, onClick: () -> Unit) {
    val hasName = r.name?.isNotBlank() == true
    val title = r.name?.takeIf { it.isNotBlank() } ?: PhoneNumberFormatter.format(r.phone)
    val tintIdx = (((r.customerId ?: r.phone.hashCode().toLong()) % AV_TINTS.size + AV_TINTS.size) % AV_TINTS.size).toInt()
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(44.dp).background(
                if (hasName) AV_TINTS[tintIdx].first else TossGrayBg, CircleShape
            ),
            contentAlignment = Alignment.Center
        ) {
            if (hasName) {
                Text(
                    title.replace(Regex("[\\s()]"), "").firstOrNull()?.toString() ?: "?",
                    fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = AV_TINTS[tintIdx].second
                )
            } else {
                Icon(Icons.Filled.Person, null, tint = TossTextTertiary, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.size(13.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            if (!r.snippet.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SourceChip(r.source)
                    Text(r.snippet, fontSize = 13.sp, color = TossTextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** 검색 결과가 '어디서 걸렸는지' 배지 — 📞통화 / 💬문자 / 📝메모. 이름·전화 매칭(CUSTOMER)은 배지 없음. (2026-09-02 사장님) */
@Composable
private fun SourceChip(source: SearchSource) {
    val chip = when (source) {
        SearchSource.CALL -> Triple("📞 통화", Color(0xFFE7F8EE), Color(0xFF16A765))
        SearchSource.MESSAGE -> Triple("💬 문자", Color(0xFFE7F0FE), Color(0xFF3182F6))
        SearchSource.MEMO -> Triple("📝 메모", Color(0xFFEDE9FE), Color(0xFF7C5CFC))
        SearchSource.CUSTOMER -> null
    }
    if (chip != null) {
        Box(
            Modifier.padding(end = 6.dp).background(chip.second, RoundedCornerShape(6.dp))
                .padding(horizontal = 6.dp, vertical = 1.dp)
        ) {
            Text(chip.first, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = chip.third, maxLines = 1)
        }
    }
}

// 프로토 avatarHtml 틴트 5색 [bg, fg].
private val AV_TINTS = listOf(
    Color(0xFFE6EFFF) to Color(0xFF3182F6),
    Color(0xFFE7F8EE) to Color(0xFF16A765),
    Color(0xFFFDEAEF) to Color(0xFFF0436A),
    Color(0xFFF1ECFE) to Color(0xFF7C5CFC),
    Color(0xFFFEF3E0) to Color(0xFFE0920C)
)

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
