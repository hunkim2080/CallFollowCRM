package com.detailline.callfollowcrm.presentation.screen.home

import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.detailline.callfollowcrm.CallFollowCrmApplication
import com.detailline.callfollowcrm.domain.model.CustomerStatus
import com.detailline.callfollowcrm.presentation.component.TossBadge
import com.detailline.callfollowcrm.presentation.component.TossCard
import com.detailline.callfollowcrm.presentation.component.TossChip
import com.detailline.callfollowcrm.presentation.theme.TossBlue
import com.detailline.callfollowcrm.presentation.theme.TossBlueSoft
import com.detailline.callfollowcrm.presentation.theme.TossError
import com.detailline.callfollowcrm.presentation.theme.TossGrayBg
import com.detailline.callfollowcrm.presentation.theme.TossSuccess
import com.detailline.callfollowcrm.presentation.theme.TossTextPrimary
import com.detailline.callfollowcrm.presentation.theme.TossTextSecondary
import com.detailline.callfollowcrm.presentation.theme.TossTextTertiary
import com.detailline.callfollowcrm.util.DateTimeUtils
import com.detailline.callfollowcrm.util.PhoneNumberFormatter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    /** 메인 진입: 번호 클릭하면 ChatScreen 으로. customerId 있으면 빠른 로드용. */
    onOpenChat: (phone: String, customerId: Long?) -> Unit,
    /** FAB "수동 입력" 전용 — 번호 직접 타이핑하는 FollowUp 화면. */
    onOpenManualEntry: () -> Unit,
    onOpenPipeline: (statusName: String) -> Unit,
    onOpenSchedule: () -> Unit,
    onOpenTemplates: () -> Unit,
    onOpenAiMessage: () -> Unit,
    onOpenStyleLearning: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val timeline by viewModel.timeline.collectAsState()
    val filter by viewModel.filterState.collectAsState()
    val todayNew by viewModel.todayNewInquiryCount.collectAsState()
    val unhandled by viewModel.unhandledCount.collectAsState()
    val weekScheduled by viewModel.thisWeekScheduledCount.collectAsState()
    val estimateSent by viewModel.estimateSentCount.collectAsState()

    // 서버 상태 indicator — AppContainer 의 ServerHealthMonitor 를 직접 구독.
    // 30초마다 GET /health 호출 → 결과 반영. 사장님만 알아볼 작은 동그라미. tap = Toast 안내.
    val context = LocalContext.current
    val serverHealth = remember {
        (context.applicationContext as CallFollowCrmApplication).container.serverHealth
    }
    val serverAlive by serverHealth.alive.collectAsState()
    val lastOkAtMs by serverHealth.lastOkAtMs.collectAsState()

    Scaffold(
        containerColor = TossGrayBg,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "RING-GO",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = TossTextPrimary
                        )
                        Spacer(Modifier.width(8.dp))
                        ServerStatusDot(
                            alive = serverAlive,
                            onClick = {
                                val msg = when (serverAlive) {
                                    true -> {
                                        val secs = lastOkAtMs?.let { (System.currentTimeMillis() - it) / 1000 } ?: 0L
                                        "서버 연결 정상 (${secs}초 전)"
                                    }
                                    false -> "서버 연결 실패 — Tailscale 확인하세요"
                                    null -> "서버 상태 체크 중..."
                                }
                                android.widget.Toast.makeText(
                                    context, msg, android.widget.Toast.LENGTH_SHORT
                                ).show()
                            }
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSchedule) {
                        Icon(Icons.Default.DateRange, "시공 예약", tint = TossTextSecondary)
                    }
                    IconButton(onClick = onOpenTemplates) {
                        Icon(Icons.Default.Description, "템플릿", tint = TossTextSecondary)
                    }
                    IconButton(onClick = onOpenAiMessage) {
                        Icon(Icons.Default.Sms, "AI 문자함", tint = TossTextSecondary)
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, "설정", tint = TossTextSecondary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = TossGrayBg)
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onOpenManualEntry() },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("수동 입력", fontWeight = FontWeight.SemiBold) },
                containerColor = TossBlue,
                contentColor = Color.White
            )
        }
    ) { inner ->
        Column(
            Modifier
                .padding(inner)
                .fillMaxSize()
                .background(TossGrayBg)
        ) {
            // KPI 4장 (2x2 그리드). 탭하면 해당 작업 화면 또는 필터로.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    KpiCard(
                        emoji = "🆕",
                        label = "오늘 신규",
                        count = todayNew,
                        accent = TossBlue,
                        modifier = Modifier.weight(1f),
                        onClick = { viewModel.setFilter(HomeFilter.NEW_INQUIRY) }
                    )
                    KpiCard(
                        emoji = "⚠️",
                        label = "미처리",
                        count = unhandled,
                        accent = TossError,
                        modifier = Modifier.weight(1f),
                        onClick = { viewModel.setFilter(HomeFilter.UNHANDLED) }
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    KpiCard(
                        emoji = "📅",
                        label = "이번주 시공",
                        count = weekScheduled,
                        accent = TossSuccess,
                        modifier = Modifier.weight(1f),
                        onClick = onOpenSchedule
                    )
                    KpiCard(
                        emoji = "💰",
                        label = "견적 답대기",
                        count = estimateSent,
                        accent = TossBlue,
                        modifier = Modifier.weight(1f),
                        onClick = { onOpenPipeline(CustomerStatus.ESTIMATE_SENT.name) }
                    )
                }
            }

            // 필터 칩 — KPI 와 연동되는 3개만. 다른 상태별 보기는 KPI / "모든 고객" 으로.
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    TossChip(
                        text = "내 말투 학습",
                        selected = false,
                        onClick = onOpenStyleLearning
                    )
                }
                items(HomeFilter.values().toList()) { f ->
                    TossChip(
                        text = f.label,
                        selected = f == filter,
                        onClick = { viewModel.setFilter(f) }
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // 메인 타임라인 (날짜 그룹 + sticky 헤더) — 갤럭시 통화 기록 같은 느낌
            if (timeline.isEmpty()) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(bottom = 80.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("📭", fontSize = 40.sp)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            when (filter) {
                                HomeFilter.ALL -> "기록된 통화가 없어요"
                                HomeFilter.UNHANDLED -> "미처리 통화 없음 — 모두 후속 완료"
                                HomeFilter.NEW_INQUIRY -> "신규 문의 없음"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = TossTextPrimary
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "옛 고객은 우측 하단 ‘+ 수동 입력’ 으로 첫 만난 날짜와 함께 등록할 수 있어요",
                            style = MaterialTheme.typography.bodySmall,
                            color = TossTextTertiary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    timeline.forEach { group ->
                        stickyHeader(key = "day-${group.dayStartMs}") {
                            // sticky 헤더 — 스크롤 시 상단에 날짜 고정. 배경 = TossGrayBg 라서 시각적 분리.
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(TossGrayBg)
                                    .padding(vertical = 8.dp, horizontal = 4.dp)
                            ) {
                                Text(
                                    DateTimeUtils.dayGroupLabel(group.dayStartMs),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TossTextSecondary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        items(group.items, key = { "row-${it.record.id}" }) { item ->
                            HomeRow(
                                item = item,
                                onClick = { onOpenChat(item.record.phoneNumber, item.customer?.id) }
                            )
                        }
                    }
                    item { Spacer(Modifier.height(80.dp)) } // FAB 공간
                }
            }
        }
    }
}

/**
 * 정사각형에 가까운 KPI 카드. 큰 숫자 + 라벨 + 좌상단 이모지.
 * 탭 동작은 호출부 정의 (필터 변경, 외부 화면 이동 등).
 */
@Composable
private fun KpiCard(
    emoji: String,
    label: String,
    count: Int,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val emphasized = count > 0
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(emoji, fontSize = 18.sp)
            Spacer(Modifier.weight(1f))
        }
        Spacer(Modifier.height(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = TossTextSecondary,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                count.toString(),
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = if (emphasized) accent else TossTextTertiary
            )
            Spacer(Modifier.width(3.dp))
            Text(
                "건",
                style = MaterialTheme.typography.labelMedium,
                color = if (emphasized) accent else TossTextTertiary,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
    }
}

@Composable
private fun HomeRow(
    item: HomeItem,
    onClick: () -> Unit
) {
    val isUnhandled = item.anyUnhandled
    val statusLabel = item.customer?.status
    TossCard(onClick = onClick) {
        Column {
            // 헤더: 이름 + 영업 상태 알약 (우측). 미처리는 더 이상 헤더에 두지 않음.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.customer?.name?.takeIf { it.isNotBlank() } ?: PhoneNumberFormatter.format(item.record.phoneNumber),
                    style = MaterialTheme.typography.titleLarge,
                    color = TossTextPrimary,
                    modifier = Modifier.weight(1f)
                )
                if (statusLabel != null) {
                    StatusBadgeSmall(statusLabel)
                } else if (isUnhandled) {
                    // 아직 customer 가 없는 (= 처음 들어온 통화) 케이스만 미처리 배지 노출.
                    val label = if (item.unhandledCount > 1) "미처리 ${item.unhandledCount}건" else "미처리"
                    TossBadge(label, color = TossError, background = Color(0xFFFEECEE))
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                PhoneNumberFormatter.format(item.record.phoneNumber),
                style = MaterialTheme.typography.bodyMedium,
                color = TossTextSecondary
            )
            Spacer(Modifier.height(2.dp))
            // 시간 줄 + (미처리 있으면) 작은 빨간 인디케이터를 같은 줄 끝에.
            Row(verticalAlignment = Alignment.CenterVertically) {
                val timeLine = buildString {
                    append(DateTimeUtils.formatShort(item.record.endedAt))
                    append(" · ")
                    append(callTypeLabel(item.record.callType))
                    if (item.callCount > 1) append(" · 오늘 ${item.callCount}통")
                }
                Text(
                    timeLine,
                    style = MaterialTheme.typography.bodySmall,
                    color = TossTextTertiary,
                    modifier = Modifier.weight(1f)
                )
                if (isUnhandled && statusLabel != null) {
                    Text(
                        if (item.unhandledCount > 1) "• 후속 ${item.unhandledCount}건 미처리" else "• 후속 미처리",
                        style = MaterialTheme.typography.labelSmall,
                        color = TossError
                    )
                }
            }
            item.customer?.let { c ->
                if (c.memo.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        c.memo.lineSequence().firstOrNull().orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TossTextSecondary,
                        maxLines = 1
                    )
                }
                c.scheduledWorkDate?.let { scheduled ->
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "🗓 ${DateTimeUtils.formatDateOnly(scheduled)} 시공 예약 · ${DateTimeUtils.dDayLabel(scheduled)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = TossBlue,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

/**
 * 홈 리스트 카드 우측 상단의 영업 상태 알약. 4색 톤은 CustomerDetail 의 statusColors 와 일관.
 * 별도 파일로 분리하지 않은 이유: 작고, 변경 시 두 화면을 함께 보는 게 자연스러움.
 */
@Composable
private fun StatusBadgeSmall(label: String) {
    val (fg, bg) = statusColors(label)
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            label,
            color = fg,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

private fun statusColors(label: String): Pair<Color, Color> {
    val blue = TossBlue to TossBlueSoft
    val green = TossSuccess to Color(0xFFE6F7EC)
    val gray = TossTextSecondary to Color(0xFFF1F3F5)
    val red = TossError to Color(0xFFFEEBEC)
    return when (label) {
        "신규 문의", "견적 대기", "견적 발송" -> blue
        "예약 대기", "예약 확정" -> green
        "시공 완료" -> gray
        "보류", "이탈" -> red
        else -> blue
    }
}

private fun callTypeLabel(raw: String): String = when (raw) {
    "INCOMING" -> "수신"
    "OUTGOING" -> "발신"
    "MISSED" -> "부재중"
    "REJECTED" -> "거절"
    "MANUAL" -> "수동 등록"
    else -> "통화"
}

/**
 * 서버 살아있음 indicator — 작은 동그라미.
 * alive == null = 첫 체크 전(회색) / true = 초록 / false = 빨강.
 * tap 시 onClick (상위에서 다이얼로그 띄움).
 */
@Composable
private fun ServerStatusDot(alive: Boolean?, onClick: () -> Unit) {
    val color = when (alive) {
        true -> TossSuccess
        false -> TossError
        null -> TossTextTertiary
    }
    Box(
        modifier = Modifier
            .size(10.dp)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(color)
            .clickable { onClick() }
    )
}
