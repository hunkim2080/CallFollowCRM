package com.detailline.callfollowcrm.presentation.screen.newleads

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.heightIn
import com.detailline.callfollowcrm.presentation.util.bottomBarClearance
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.detailline.callfollowcrm.data.repository.SmsRepository
import com.detailline.callfollowcrm.util.DateTimeUtils
import com.detailline.callfollowcrm.util.PhoneNumberFormatter
import com.detailline.callfollowcrm.presentation.theme.TossBlue
import com.detailline.callfollowcrm.presentation.theme.TossBlueDark
import com.detailline.callfollowcrm.presentation.theme.TossBlueSoft
import com.detailline.callfollowcrm.presentation.theme.TossDivider
import com.detailline.callfollowcrm.presentation.theme.TossError
import com.detailline.callfollowcrm.presentation.theme.TossGrayBg
import com.detailline.callfollowcrm.presentation.theme.TossSuccess
import com.detailline.callfollowcrm.presentation.theme.TossTextPrimary
import com.detailline.callfollowcrm.presentation.theme.TossTextSecondary
import com.detailline.callfollowcrm.presentation.theme.TossTextTertiary
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

/**
 * 신규 고객 · 날짜별 (프로토 `s-newleads` / renderNewLeads) 1:1.
 *   상단 안내 + [전체/미답장만] 필터 + 날짜 그룹 + 고객 줄(미답장=빨간 점 + [재연락]).
 *   줄 탭 → 고객 상세. [재연락] → 채팅(사장님이 보냄, 자동발송 X).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewLeadsScreen(
    viewModel: NewLeadsViewModel,
    onBack: () -> Unit,
    /** 줄 탭 — 고객 카드 열기. customerId<=0 면 phone 으로 고객을 만들어 연다(AppNavHost 처리). */
    onOpenLead: (phone: String, customerId: Long) -> Unit,
    onReContact: (phone: String, customerId: Long) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val peek by viewModel.peek.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    // 꾹 누른 줄의 대화 미리보기 — 읽기 전용(입력 없음)이라 바텀시트 안전.
    if (peek != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { viewModel.closePeek() },
            sheetState = sheetState,
            containerColor = Color.White
        ) {
            PeekSheet(
                peek = peek!!,
                onOpenChat = { val p = peek!!; viewModel.closePeek(); onReContact(p.phone, p.customerId) }
            )
        }
    }

    Scaffold(
        containerColor = TossGrayBg,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("신규 고객 · 날짜별", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로", tint = TossTextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = TossGrayBg)
            )
        }
    ) { inner ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(inner),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 18.dp)
        ) {
            // nl-hint — 안내 배너
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 14.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(TossBlueSoft)
                        .padding(horizontal = 13.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.AutoAwesome, null, tint = TossBlue, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "연락 없는 신규는 하루이틀 뒤 재연락하면 전환율이 올라가요.",
                        color = TossBlueDark, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, lineHeight = 18.sp
                    )
                }
            }
            // cfilter — 전체 / 미답장만
            item {
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CFilterChip("전체", state.totalCount, !state.unreadOnly) { viewModel.setUnreadOnly(false) }
                    CFilterChip("미답장만", state.unreadCount, state.unreadOnly) { viewModel.setUnreadOnly(true) }
                }
            }

            if (state.groups.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(vertical = 30.dp), contentAlignment = Alignment.Center) {
                        Text("해당 신규가 없어요", color = TossTextTertiary, fontSize = 13.sp)
                    }
                }
            } else {
                state.groups.forEach { group ->
                    item(key = "date-${group.dateLabel}") {
                        Row(
                            Modifier.padding(top = 18.dp, bottom = 10.dp).padding(horizontal = 2.dp),
                            verticalAlignment = Alignment.Bottom
                        ) {
                            Text(group.dateLabel, color = TossTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                            Text(" · ${group.count}통", color = TossTextTertiary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    items(group.leads, key = { it.phone }) { lead ->
                        // 밀어서 정리(우→좌) = 광고/스팸 마킹 → 목록·집계에서 제외. 되돌리기 스낵바. (2026-06-08 #3)
                        LeadSwipeBox(onDismiss = {
                            viewModel.dismissAsSpam(lead.phone)
                            scope.launch {
                                val r = snackbarHostState.showSnackbar(
                                    "광고/스팸으로 정리했어요 — 신규 집계에서 빠져요",
                                    actionLabel = "되돌리기", duration = SnackbarDuration.Short
                                )
                                if (r == SnackbarResult.ActionPerformed) viewModel.undoDismiss(lead.phone)
                            }
                        }) {
                            NewLeadRow(
                                lead = lead,
                                onClick = { onOpenLead(lead.phone, lead.customerId) },
                                onLongClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.openPeek(lead)
                                },
                                onReContact = { onReContact(lead.phone, lead.customerId) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CFilterChip(label: String, count: Int, on: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (on) TossBlue else TossGrayBg)
            .clickable { onClick() }
            .padding(horizontal = 15.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = if (on) Color.White else TossTextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Text(
            " $count",
            color = if (on) Color.White.copy(alpha = 0.85f) else TossTextSecondary.copy(alpha = 0.55f),
            fontSize = 13.sp, fontWeight = FontWeight.ExtraBold
        )
    }
}

/** 신규 줄 우→좌 swipe → '광고/정리'. SpamSwipeBox 와 동일 패턴(confirmValueChange=false, 데이터가 제거). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LeadSwipeBox(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val swipeState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) onDismiss()
            false   // 원위치 복귀 — 실제 제거는 데이터(스팸 마킹)가 처리.
        },
        positionalThreshold = { total -> total * 0.6f }
    )
    SwipeToDismissBox(
        state = swipeState,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            Box(
                Modifier.fillMaxWidth().padding(bottom = 9.dp)
                    .clip(RoundedCornerShape(14.dp)).background(TossBlueSoft)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Check, "정리", tint = TossBlue, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("광고/정리", color = TossBlue, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                }
            }
        },
        content = { content() }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NewLeadRow(lead: NewLeadUi, onClick: () -> Unit, onLongClick: () -> Unit, onReContact: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 9.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White)
            .border(1.dp, TossDivider, RoundedCornerShape(14.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // nl-dot — 계약완료=초록, 미답장=빨강, 답장함=회색
        Box(Modifier.size(8.dp).clip(CircleShape).background(
            when { lead.contracted -> TossSuccess; lead.replied -> TossDivider; else -> TossError }
        ))
        Spacer(Modifier.width(11.dp))
        // nl-b
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(lead.displayName, color = TossTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                Spacer(Modifier.width(8.dp))
                Text(lead.timeLabel, color = TossTextTertiary, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
            }
            // "어떻게 끝났는지" 한 줄 — ✨AI 요약(파랑) / 💬 마지막 문자 / 📞 통화. 없으면 memo.
            if (lead.summaryLine != null) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        when {
                            lead.summaryIsAi -> Icons.Default.AutoAwesome
                            lead.lastWasCall -> Icons.Default.Call
                            else -> Icons.Default.Sms
                        },
                        null,
                        tint = if (lead.summaryIsAi) TossBlue else TossTextTertiary,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        lead.summaryLine,
                        color = TossTextSecondary, fontSize = 12.5.sp, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                    )
                }
            } else {
                Text(lead.memo, color = TossTextSecondary, fontSize = 12.5.sp, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
            }
        }
        Spacer(Modifier.width(11.dp))
        // right — 계약완료 배지 / 답장함 태그 / 재연락 버튼
        if (lead.contracted) {
            Text(
                "계약완료",
                color = Color(0xFF0E9F56), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp)).background(Color(0xFFE5F8EE))
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            )
        } else if (lead.replied) {
            Text(
                "답장함",
                color = TossTextTertiary, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp)).background(TossGrayBg)
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            )
        } else {
            Text(
                "재연락",
                color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp)).background(TossBlue)
                    .clickable { onReContact() }
                    .padding(horizontal = 14.dp, vertical = 9.dp)
            )
        }
    }
}

/** 꾹 눌러 보는 대화 미리보기 — 읽기 전용 바텀시트. 최신 메시지가 아래(채팅처럼)에 오게 reverseLayout. */
@Composable
private fun PeekSheet(peek: PeekState, onOpenChat: () -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .padding(start = 18.dp, end = 18.dp)
            .bottomBarClearance(extra = 14.dp)
    ) {
        Text(
            peek.displayName, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold,
            color = TossTextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis
        )
        Text(
            PhoneNumberFormatter.format(peek.phone) + " · 최근 대화",
            fontSize = 12.sp, color = TossTextTertiary, modifier = Modifier.padding(top = 2.dp)
        )
        Spacer(Modifier.height(12.dp))
        when {
            peek.loading -> Box(
                Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = TossBlue)
                    Spacer(Modifier.width(10.dp))
                    Text("불러오는 중…", color = TossTextTertiary, fontSize = 13.sp)
                }
            }
            peek.items.isEmpty() -> Box(
                Modifier.fillMaxWidth().padding(vertical = 34.dp), contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        peek.fallbackLine ?: "주고받은 내용이 없어요",
                        color = TossTextSecondary, fontSize = 14.sp, fontWeight = FontWeight.Bold
                    )
                    Text(
                        "채팅을 열면 자세히 볼 수 있어요",
                        color = TossTextTertiary, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
            else -> LazyColumn(
                Modifier.fillMaxWidth().heightIn(max = 440.dp),
                reverseLayout = true
            ) {
                items(peek.items, key = { peekItemKey(it) }) { item ->
                    when (item) {
                        is PeekItem.Sms -> PeekBubble(item.msg)
                        is PeekItem.Call -> PeekCallCard(item)
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(
            "채팅 열기",
            color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                .background(TossBlue).clickable { onOpenChat() }.padding(vertical = 13.dp)
        )
    }
}

private fun peekItemKey(item: PeekItem): String = when (item) {
    is PeekItem.Sms -> "s-${item.msg.id}-${item.msg.sent}"
    is PeekItem.Call -> "c-${item.record.id}"
}

/** 미리보기 통화 카드 — 채팅 CallSegment 축약판(읽기 전용). 에이닷 요약 있으면 불릿으로 노출. */
@Composable
private fun PeekCallCard(item: PeekItem.Call) {
    val record = item.record
    val type = runCatching {
        com.detailline.callfollowcrm.domain.model.CallType.valueOf(record.callType)
    }.getOrNull()
    val (label, accent) = when (type) {
        com.detailline.callfollowcrm.domain.model.CallType.INCOMING -> "수신 통화" to Color(0xFF0E9E90)
        com.detailline.callfollowcrm.domain.model.CallType.OUTGOING -> "발신 통화" to Color(0xFF0E9E90)
        com.detailline.callfollowcrm.domain.model.CallType.MISSED -> "부재중 전화" to TossError
        com.detailline.callfollowcrm.domain.model.CallType.REJECTED -> "거절한 전화" to TossTextSecondary
        else -> "통화" to Color(0xFF0E9E90)
    }
    val durSec = record.duration
    val durLabel = if (durSec > 0) {
        val m = durSec / 60; val s = durSec % 60
        if (m > 0) (if (s > 0) "${m}분 ${s}초" else "${m}분") else "${s}초"
    } else null
    val summarizable = type != com.detailline.callfollowcrm.domain.model.CallType.MISSED &&
        type != com.detailline.callfollowcrm.domain.model.CallType.REJECTED && durSec > 0

    Column(
        Modifier.fillMaxWidth().padding(vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp)).background(Color(0xFFEAF4F1))
            .border(1.dp, Color(0xFFCDE8E0), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 11.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(28.dp).clip(RoundedCornerShape(9.dp)).background(accent),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Default.Phone, null, tint = Color.White, modifier = Modifier.size(14.dp)) }
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    buildString { append(label); if (durLabel != null) { append(" · "); append(durLabel) } },
                    fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF0A7D72)
                )
                Text(
                    DateTimeUtils.formatShort(record.endedAt) + if (item.summarized) " · AI 요약됨" else "",
                    fontSize = 11.sp, color = TossTextTertiary, fontWeight = FontWeight.Bold
                )
            }
        }
        if (item.summaryBullets.isNotEmpty()) {
            Column(Modifier.padding(top = 9.dp)) {
                item.summaryBullets.forEach { line ->
                    Row(Modifier.padding(bottom = 4.dp)) {
                        Text("• ", fontSize = 12.5.sp, color = Color(0xFF0A7D72), fontWeight = FontWeight.Bold)
                        Text(line, fontSize = 12.5.sp, color = TossTextSecondary, lineHeight = 19.sp)
                    }
                }
            }
        } else if (summarizable) {
            Text(
                "에이닷에서 이 통화 녹음을 공유하면 자동으로 요약돼요",
                color = TossTextTertiary, fontSize = 11.5.sp, modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun PeekBubble(msg: SmsRepository.SmsMessage) {
    val sent = msg.sent
    val text = msg.body.ifBlank { if (msg.imageUris.isNotEmpty()) "[사진]" else "" }
    if (text.isBlank()) return
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = if (sent) Arrangement.End else Arrangement.Start
    ) {
        Column(horizontalAlignment = if (sent) Alignment.End else Alignment.Start) {
            Box(
                Modifier.widthIn(max = 268.dp).clip(RoundedCornerShape(14.dp))
                    .background(if (sent) TossBlue else TossGrayBg)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(text, color = if (sent) Color.White else TossTextPrimary, fontSize = 13.5.sp, lineHeight = 19.sp)
            }
            Text(
                DateTimeUtils.formatShort(msg.dateMs),
                color = TossTextTertiary, fontSize = 10.sp,
                modifier = Modifier.padding(top = 2.dp, start = 4.dp, end = 4.dp)
            )
        }
    }
}
