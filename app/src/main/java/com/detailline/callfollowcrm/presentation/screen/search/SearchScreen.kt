package com.detailline.callfollowcrm.presentation.screen.search

import com.detailline.callfollowcrm.presentation.theme.AppTheme
import com.detailline.callfollowcrm.presentation.theme.LightColors
import androidx.compose.foundation.background
import androidx.compose.ui.text.withStyle
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.draw.clip
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
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    onBack: () -> Unit,
    onOpenChat: (phone: String, customerId: Long?) -> Unit
) {
    val query by viewModel.queryState.collectAsState()
    val results by viewModel.results.collectAsState()
    val recent by viewModel.recent.collectAsState()
    val todayCallers by viewModel.todayCallers.collectAsState()
    val bait by viewModel.baitChips.collectAsState()
    val sites by viewModel.siteResults.collectAsState()
    val unpaid by viewModel.unpaidResults.collectAsState()
    val period by viewModel.periodResults.collectAsState()
    // 02 — 접은 묶음. 화면을 떠나기 전까진 기억한다. (2026-09-19 사장님)
    val folded = remember { mutableStateListOf<SearchSource>() }
    /** 10건 넘어 처음엔 접힌 묶음 중, 사장님이 펼친 것. */
    val opened = remember { mutableStateListOf<SearchSource>() }
    val siteOpen = remember { mutableStateOf(true) }
    val unpaidOpen = remember { mutableStateOf(true) }
    val periodOpen = remember { mutableStateOf(true) }
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
                    // 홈 검색창과 **같은 말**로. (2026-09-19 — 홈만 바꾸고 여기를 안 바꿔 두 말이 달랐다)
                    Text("이름·주소·금액·통화 내용까지", fontSize = 15.sp, color = TossTextTertiary)
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
            // 03 — 빈 검색창이면 **최근 검색 + 오늘 통화한 손님**. 열자마자 누를 게 있게. (2026-09-19 사장님)
            query.isBlank() && (recent.isNotEmpty() || todayCallers.isNotEmpty() || bait.isNotEmpty()) -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 18.dp)
            ) {
                if (recent.isNotEmpty()) item {
                    Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("최근 검색", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = TossTextTertiary)
                        Spacer(Modifier.weight(1f))
                        Text("전체 지우기", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = TossTextTertiary,
                            modifier = Modifier.clip(RoundedCornerShape(8.dp))
                                .clickable { viewModel.clearRecent() }.padding(horizontal = 6.dp, vertical = 3.dp))
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        recent.forEach { word ->
                            Row(
                                Modifier.padding(bottom = 7.dp)
                                    .background(Color.White, RoundedCornerShape(999.dp))
                                    .clip(RoundedCornerShape(999.dp))
                                    .clickable { viewModel.setQuery(word) }
                                    .padding(start = 12.dp, end = 7.dp, top = 7.dp, bottom = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(word, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary)
                                Spacer(Modifier.size(5.dp))
                                Icon(
                                    Icons.Filled.Close, "지우기", tint = TossTextTertiary,
                                    modifier = Modifier.size(14.dp).clickable { viewModel.dropRecent(word) }
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                }
                // 🪝 "이렇게도 찾아요" — 설명이 아니라 **내 숫자**. 누르면 그 검색이 바로 돌아간다.
                //   (2026-09-19 사장님 · 통화 전문 검색을 9/2 에 넣었는데 오늘에야 아신 게 계기)
                if (bait.isNotEmpty()) item {
                    Text("이렇게도 찾아요", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold,
                        color = TossTextTertiary, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        bait.forEach { chip ->
                            Box(
                                Modifier.padding(bottom = 7.dp)
                                    .background(Color.White, RoundedCornerShape(999.dp))
                                    .clip(RoundedCornerShape(999.dp))
                                    .clickable { viewModel.setQuery(chip.query) }
                                    .padding(horizontal = 13.dp, vertical = 9.dp)
                            ) {
                                Text(chip.label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary)
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                }
                if (todayCallers.isNotEmpty()) item {
                    Text("오늘 통화한 손님", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold,
                        color = TossTextTertiary, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
                    Column(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(18.dp))) {
                        todayCallers.forEachIndexed { i, r ->
                            if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(TossDivider))
                            SearchRow(r, "") { keyboard?.hide(); onOpenChat(r.phone, r.customerId) }
                        }
                    }
                }
            }
            // 프로토 doSearch 빈 쿼리 안내문 verbatim.
            query.isBlank() -> CenterHint("이름·전화·문자, 그리고 통화 내용까지\n뭐든 찾아보세요")
            results.isEmpty() && sites.isEmpty() && unpaid.isEmpty() && period.isEmpty() ->
                CenterHint("검색 결과가 없어요")
            // 02 — 손님 → 통화 → 문자 → 메모 로 묶는다. 제목을 누르면 접힌다. (2026-09-19 사장님)
            //   사람 찾는 경우가 제일 많아 손님이 맨 위.
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 18.dp)
            ) {
                // 💰 못 받은 돈 · 📅 그달 시공 — **장부에서 나온 것**이라 말보다 위. (2026-09-19 사장님)
                val pick: (SiteHit) -> Unit = { s ->
                    keyboard?.hide(); viewModel.rememberQuery(query); onOpenChat(s.phone, s.customerId)
                }
                siteGroup("💰 못 받은 돈", unpaid, unpaidOpen, query, pick)
                siteGroup("📅 그달 시공", period, periodOpen, query, pick)
                // 📍 현장 — 주소로 찾은 것. 말(통화·문자)보다 **위**에 둔다.
                //   "동탄" 을 칠 땐 동탄에서 한 현장이 먼저 보여야 한다. (2026-09-19 사장님 1순위)
                if (sites.isNotEmpty()) {
                    val openSite = siteOpen.value
                    item(key = "h-site") {
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                .clickable { siteOpen.value = !siteOpen.value }
                                .padding(start = 4.dp, end = 4.dp, top = 6.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("📍 현장", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = TossTextTertiary)
                            Spacer(Modifier.size(7.dp))
                            Box(
                                Modifier.background(Color.White, RoundedCornerShape(999.dp))
                                    .padding(horizontal = 7.dp, vertical = 1.dp)
                            ) {
                                Text("${sites.size}", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = TossTextSecondary)
                            }
                            Spacer(Modifier.weight(1f))
                            Text(if (openSite) "⌃" else "⌄", fontSize = 13.sp, color = TossTextTertiary)
                        }
                    }
                    if (openSite) item(key = "b-site") {
                        Column(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(18.dp))) {
                            sites.forEachIndexed { i, s ->
                                if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(TossDivider))
                                SiteRow(s, query) {
                                    keyboard?.hide()
                                    viewModel.rememberQuery(query)
                                    onOpenChat(s.phone, s.customerId)
                                }
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                    }
                }
                val order = listOf(
                    SearchSource.CUSTOMER, SearchSource.CALL, SearchSource.MESSAGE, SearchSource.MEMO
                )
                for (src in order) {
                    val rows = results.filter { it.source == src }
                    if (rows.isEmpty()) continue
                    // 10건 넘는 묶음은 **접힌 채로** 시작. "9월" 처럼 문자에 흔한 말이면
                    //   수십 건이 걸려 장부 묶음이 화면 밖으로 밀린다. (2026-09-19 사장님)
                    val open = if (rows.size > 10) src in opened else src !in folded
                    item(key = "h-" + src.name) {
                        Row(
                            Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    if (rows.size > 10) {
                                        if (open) opened.remove(src) else opened.add(src)
                                    } else {
                                        if (open) folded.add(src) else folded.remove(src)
                                    }
                                }
                                .padding(start = 4.dp, end = 4.dp, top = 6.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(groupTitle(src), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = TossTextTertiary)
                            Spacer(Modifier.size(7.dp))
                            Box(
                                Modifier.background(Color.White, RoundedCornerShape(999.dp))
                                    .padding(horizontal = 7.dp, vertical = 1.dp)
                            ) {
                                Text("${rows.size}", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = TossTextSecondary)
                            }
                            Spacer(Modifier.weight(1f))
                            Text(if (open) "⌃" else "⌄", fontSize = 13.sp, color = TossTextTertiary)
                        }
                    }
                    if (open) item(key = "b-" + src.name) {
                        Column(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(18.dp))) {
                            rows.forEachIndexed { idx, r ->
                                if (idx > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(TossDivider))
                                SearchRow(r, query) {
                                    keyboard?.hide()
                                    viewModel.rememberQuery(query)
                                    onOpenChat(r.phone, r.customerId)
                                }
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                    }
                }
            }
        }
    }
}

/** 프로토 doSearch 결과 .recent-row — 아바타 + (이름 / 메시지 요약). */
@Composable
private fun SearchRow(r: SearchResult, query: String = "", onClick: () -> Unit) {
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
            // 주소가 있으면 번호 밑에 **작게 한 줄**. (2026-09-19 사장님 "지저분할까?")
            //   번호는 누군지 못 알려준다 — 시공은 현장으로 기억한다.
            //   한 줄·회색·말줄임이라 훑는 데 방해되지 않는다.
            r.address?.takeIf { it.isNotBlank() }?.let { addr ->
                Spacer(Modifier.height(2.dp))
                Text(
                    addr, fontSize = 12.sp, color = TossTextTertiary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            // 걸린 문장을 **여러 개** 보여준다 — 통화 안에서 몇 번 걸렸는지가 눈에 보여야
            //   "안까지 다 뒤졌다"는 게 전달된다. (2026-09-19 사장님)
            val lines = r.sentences.takeIf { it.isNotEmpty() } ?: listOfNotNull(r.snippet?.takeIf { it.isNotBlank() })
            if (lines.isNotEmpty()) {
                Spacer(Modifier.height(5.dp))
                Row(verticalAlignment = Alignment.CenterVertically) { SourceChip(r.source, r.fromSummary) }
                lines.forEachIndexed { i, line ->
                    Spacer(Modifier.height(if (i == 0) 4.dp else 4.dp))
                    Box(
                        Modifier
                            .background(TossGrayBg, RoundedCornerShape(10.dp))
                            .padding(horizontal = 9.dp, vertical = 6.dp)
                    ) {
                        Text(
                            highlighted(line, query),
                            fontSize = 12.5.sp, color = TossTextSecondary, lineHeight = 18.sp,
                            maxLines = 2, overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (r.moreCount > 0) {
                    Spacer(Modifier.height(5.dp))
                    Text(
                        (if (r.source == SearchSource.CALL) "이 통화에서 " else "이 대화에서 ") +
                            "${r.moreCount}곳 더 ›",
                        fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = TossBlue
                    )
                }
            }
        }
    }
}

/**
 * 장부에서 나온 묶음 하나 — 💰 못 받은 돈 · 📅 그달 시공 · 📍 현장 이 같은 모양을 쓴다.
 *   말(통화·문자)에서 찾은 묶음과 생김새를 맞춰 눈이 헷갈리지 않게. (2026-09-19 사장님)
 */
private fun androidx.compose.foundation.lazy.LazyListScope.siteGroup(
    title: String,
    rows: List<SiteHit>,
    open: androidx.compose.runtime.MutableState<Boolean>,
    query: String,
    onPick: (SiteHit) -> Unit
) {
    if (rows.isEmpty()) return
    item(key = "h-" + title) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                .clickable { open.value = !open.value }
                .padding(start = 4.dp, end = 4.dp, top = 6.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = TossTextTertiary)
            Spacer(Modifier.size(7.dp))
            Box(
                Modifier.background(Color.White, RoundedCornerShape(999.dp))
                    .padding(horizontal = 7.dp, vertical = 1.dp)
            ) {
                Text("${rows.size}", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = TossTextSecondary)
            }
            Spacer(Modifier.weight(1f))
            Text(if (open.value) "⌃" else "⌄", fontSize = 13.sp, color = TossTextTertiary)
        }
    }
    if (open.value) item(key = "b-" + title) {
        Column(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(18.dp))) {
            rows.forEachIndexed { i, s ->
                if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(TossDivider))
                SiteRow(s, query) { onPick(s) }
            }
        }
        Spacer(Modifier.height(14.dp))
    }
}

/**
 * 📍 현장 한 줄 — 주소(찾는 말 색칠) + 손님 + 언제·얼마. (2026-09-19 사장님)
 *   말에서 찾은 결과와 달리 **장부에서 나온 것**이라 날짜·돈이 같이 붙는다.
 */
@Composable
private fun SiteRow(s: SiteHit, query: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(44.dp).background(AppTheme.colors.cautionBg, CircleShape),
            contentAlignment = Alignment.Center
        ) { Text("📍", fontSize = 17.sp) }
        Spacer(Modifier.size(13.dp))
        Column(Modifier.weight(1f)) {
            Text(
                highlighted(s.address, query),
                fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary,
                maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 19.sp
            )
            Spacer(Modifier.height(3.dp))
            Text(
                listOfNotNull(
                    s.name ?: PhoneNumberFormatter.format(s.phone),
                    s.dayMs?.let { com.detailline.callfollowcrm.util.DateTimeUtils.formatDateLabel(it) + " 시공" },
                    s.money
                ).joinToString(" · "),
                fontSize = 12.sp, color = TossTextTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** 02 묶음 제목 — 손님 → 통화 → 문자 → 메모. (2026-09-19 사장님) */
private fun groupTitle(src: SearchSource): String = when (src) {
    SearchSource.CUSTOMER -> "👤 손님"
    SearchSource.CALL -> "📞 통화"
    SearchSource.MESSAGE -> "💬 문자"
    SearchSource.MEMO -> "📝 메모"
}

/**
 * 찾는 말을 **파랗게 칠한다.** (2026-09-19 사장님 — 에이닷 초록 하이라이트를 보고)
 *   눈이 바로 그 단어로 간다. 글자가 많을수록 이게 있고 없고가 크다.
 */
private fun highlighted(text: String, q: String): androidx.compose.ui.text.AnnotatedString {
    val query = q.trim()
    if (query.isEmpty()) return androidx.compose.ui.text.AnnotatedString(text)
    return androidx.compose.ui.text.buildAnnotatedString {
        var i = 0
        while (i <= text.length - query.length) {
            val at = text.indexOf(query, i, ignoreCase = true)
            if (at < 0) break
            append(text.substring(i, at))
            withStyle(
                androidx.compose.ui.text.SpanStyle(
                    color = TossBlue, fontWeight = FontWeight.ExtraBold,
                    background = Color(0x1F3182F6)
                )
            ) { append(text.substring(at, at + query.length)) }
            i = at + query.length
        }
        if (i < text.length) append(text.substring(i))
    }
}

/** 검색 결과가 '어디서 걸렸는지' 배지 — 📞통화 / 💬문자 / 📝메모. 이름·전화 매칭(CUSTOMER)은 배지 없음. (2026-09-02 사장님) */
@Composable
private fun SourceChip(source: SearchSource, fromSummary: Boolean = false) {
    val chip = when (source) {
        // 진짜 말이 아니라 AI 가 정리한 글에서 걸렸으면 **그렇다고 밝힌다.** (2026-09-19 사장님)
        SearchSource.CALL -> Triple(
            if (fromSummary) "📞 통화 요약" else "📞 통화",
            AppTheme.colors.doneBg, Color(0xFF16A765)
        )
        SearchSource.MESSAGE -> Triple("💬 문자", AppTheme.colors.primaryBg, AppTheme.colors.primary)
        SearchSource.MEMO -> Triple("📝 메모", AppTheme.colors.categoryBg, AppTheme.colors.category)
        SearchSource.CUSTOMER -> null
    }
    if (chip != null) {
        Box(
            Modifier.padding(end = 6.dp).background(chip.second, RoundedCornerShape(8.dp))
                .padding(horizontal = 6.dp, vertical = 1.dp)
        ) {
            Text(chip.first, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = chip.third, maxLines = 1)
        }
    }
}

// 프로토 avatarHtml 틴트 5색 [bg, fg].
private val AV_TINTS = listOf(
    Color(0xFFE6EFFF) to LightColors.primary,
    LightColors.doneBg to Color(0xFF16A765),
    LightColors.unpaidBg to LightColors.unpaid,
    LightColors.categoryBg to LightColors.category,
    LightColors.cautionBg to Color(0xFFE0920C)
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
