package com.detailline.callfollowcrm.presentation.screen.customers

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.detailline.callfollowcrm.data.local.entity.CustomerEntity
import com.detailline.callfollowcrm.presentation.theme.TossBlue
import com.detailline.callfollowcrm.presentation.theme.TossDivider
import com.detailline.callfollowcrm.presentation.theme.TossGrayBg
import com.detailline.callfollowcrm.presentation.theme.TossTextPrimary
import com.detailline.callfollowcrm.presentation.theme.TossTextSecondary
import com.detailline.callfollowcrm.presentation.theme.TossTextTertiary
import com.detailline.callfollowcrm.util.PhoneNumberFormatter
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * 고객 관리 목록 화면 (프로토 s-customers / renderCustomers) — 전체 고객.
 *   상단 상태 필터칩(전체/신규/미전환/예약/완료/단골/거래처/AS) + 고객별 상태 태그.
 *   상태는 앱 데이터(시공일·잔금·생성일)로 계산 — 신규/미전환/예약/완료. 거래처/AS/단골은 데이터 없어 빈 칩.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomersScreen(
    viewModel: CustomersViewModel,
    onBack: () -> Unit,
    onOpenCustomerDetail: (Long) -> Unit
) {
    val customers by viewModel.customers.collectAsState()
    var filter by remember { mutableStateOf("전체") }

    val now = remember { System.currentTimeMillis() }
    val today0 = remember { startOfToday() }
    val withStatus = remember(customers) {
        customers.map { it to customerStatus(it, today0, now) }
    }
    // 필터 적용 + 정렬. 예약/완료는 시공일 순(사장님 요청 2026-06-04):
    //   예약 = 가까운 시공 먼저(오름차순), 완료 = 최근 완료 먼저(내림차순). 그 외는 기존 이름순(ViewModel).
    val list = remember(filter, withStatus) {
        val filtered = if (filter == "전체") withStatus else withStatus.filter { it.second == filter }
        when (filter) {
            "예약" -> filtered.sortedBy { it.first.scheduledWorkDate ?: Long.MAX_VALUE }
            "완료" -> filtered.sortedByDescending { it.first.scheduledWorkDate ?: Long.MIN_VALUE }
            else -> filtered
        }
    }
    val note = when (filter) {
        "거래처" -> "숫자 = 이 거래처가 준 일감 누적 건수예요."
        "신규" -> "문의 후 14일 이내 · 예약 전 고객이에요. 14일 지나면 미전환으로 넘어가요."
        "미전환" -> "14일간 예약이 없던 고객이에요. 가끔 재연락하면 다시 살아나기도 해요."
        else -> null
    }

    Scaffold(
        containerColor = TossGrayBg,
        topBar = {
            TopAppBar(
                title = { Text("고객 관리", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로", tint = TossTextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = TossGrayBg)
            )
        }
    ) { inner ->
        Column(Modifier.padding(inner).fillMaxSize()) {
            // .cfilter — 상태 필터칩 (가로 스크롤)
            LazyRow(
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(CUST_STATUSES) { s ->
                    val cnt = if (s == "전체") withStatus.size else withStatus.count { it.second == s }
                    CfChip(s, cnt, filter == s) { filter = s }
                }
            }

            // .sec-sub — "고객 N명"
            Text(
                "고객 ${list.size}명",
                fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TossTextTertiary,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 8.dp)
            )
            if (note != null) {
                Text(
                    note,
                    fontSize = 11.5.sp, color = TossTextTertiary,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 10.dp)
                )
            }

            if (list.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        com.detailline.callfollowcrm.presentation.component.Mascot(sizeDp = 64.dp)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            if (customers.isEmpty()) "아직 등록된 고객이 없어요" else "해당 고객이 없어요",
                            fontSize = 14.sp, color = TossTextTertiary
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 18.dp)
                ) {
                    // 흰 카드 하나처럼 보이되 행마다 lazy 로 그림(고객 많아도 안 버벅).
                    //   첫/끝 행만 모서리 둥글게 + 사이 구분선 → 모양은 한 카드 그대로. spacedBy 없어서 행끼리 0 간격.
                    itemsIndexed(list, key = { _, pair -> pair.first.id }) { idx, (c, status) ->
                        val shape = when {
                            list.size == 1 -> RoundedCornerShape(18.dp)
                            idx == 0 -> RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)
                            idx == list.lastIndex -> RoundedCornerShape(bottomStart = 18.dp, bottomEnd = 18.dp)
                            else -> RoundedCornerShape(0.dp)
                        }
                        Column(Modifier.fillMaxWidth().background(Color.White, shape)) {
                            if (idx > 0) {
                                Box(Modifier.fillMaxWidth().height(1.dp).background(TossDivider))
                            }
                            CustomerRow(c, status) { onOpenCustomerDetail(c.id) }
                        }
                    }
                }
            }
        }
    }
}

/** .cf — 상태 필터칩. off=회색 fill, on=파랑 fill+그림자. cf-n=카운트. */
@Composable
private fun CfChip(label: String, count: Int, on: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(CircleShape)
            .background(if (on) TossBlue else TossGrayBg)
            .clickable(onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            fontSize = 13.sp, fontWeight = FontWeight.Bold,
            color = if (on) Color.White else TossTextSecondary
        )
        Spacer(Modifier.size(5.dp))
        Text(
            count.toString(),
            fontSize = 13.sp, fontWeight = FontWeight.ExtraBold,
            color = (if (on) Color.White else TossTextSecondary).copy(alpha = if (on) 0.85f else 0.55f)
        )
    }
}

/** .recent-row — 아바타 + (이름·상태태그 / 한줄 요약). */
@Composable
private fun CustomerRow(c: CustomerEntity, status: String, onClick: () -> Unit) {
    val hasName = c.name?.isNotBlank() == true
    val title = c.name?.takeIf { it.isNotBlank() } ?: PhoneNumberFormatter.format(c.phoneNumber)
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // .avatar — 이름 있으면 틴트+이니셜, 번호만이면 회색+사람 아이콘
        Box(
            Modifier.size(44.dp).background(
                if (hasName) AV_TINTS[(c.id % AV_TINTS.size).toInt()].first else TossGrayBg,
                CircleShape
            ),
            contentAlignment = Alignment.Center
        ) {
            if (hasName) {
                Text(
                    avatarInitial(title),
                    fontSize = 16.sp, fontWeight = FontWeight.ExtraBold,
                    color = AV_TINTS[(c.id % AV_TINTS.size).toInt()].second
                )
            } else {
                Icon(Icons.Default.Person, null, tint = TossTextTertiary, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.size(13.dp))
        Column(Modifier.weight(1f)) {
            // .rtop — 이름 + 상태 태그
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(Modifier.size(7.dp))
                StatusTag(status)
            }
            Spacer(Modifier.height(3.dp))
            Text(
                customerSubLine(c, status),
                fontSize = 13.sp, color = TossTextSecondary,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** .tag — 10.5sp w700 radius7. custTag 색. */
@Composable
private fun StatusTag(status: String) {
    val (fg, bg) = custTag(status)
    Box(
        Modifier.clip(RoundedCornerShape(7.dp)).background(bg).padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(status, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = fg)
    }
}

private val CUST_STATUSES = listOf("전체", "신규", "미전환", "예약", "완료", "단골", "거래처", "AS")

// AV_TINTS — 프로토 avatarHtml 틴트 5색 [bg, fg].
private val AV_TINTS = listOf(
    Color(0xFFE6EFFF) to Color(0xFF3182F6),
    Color(0xFFE7F8EE) to Color(0xFF16A765),
    Color(0xFFFDEAEF) to Color(0xFFF0436A),
    Color(0xFFF1ECFE) to Color(0xFF7C5CFC),
    Color(0xFFFEF3E0) to Color(0xFFE0920C)
)

/** 프로토 custTag — 상태별 태그 색 (fg, bg). */
private fun custTag(s: String): Pair<Color, Color> = when (s) {
    "완료" -> Color(0xFF0E9F56) to Color(0xFFE5F8EE)   // green
    "단골" -> Color(0xFF6B4FD8) to Color(0xFFF1ECFE)   // purple
    "신규" -> Color(0xFFB7791F) to Color(0xFFFEF3E0)   // amber
    "거래처" -> Color(0xFF4F5BD8) to Color(0xFFECEEFE) // indigo
    "AS" -> Color(0xFFF0436A) to Color(0xFFFDEAEF)     // red
    "미전환" -> Color(0xFF9AA3AF) to Color(0xFFF4F5F7) // gray
    else -> Color(0xFF3182F6) to Color(0xFFEAF2FE)     // blue (예약/상담)
}

/** 프로토 initial() — 공백·괄호 제거 후 첫 글자. */
private fun avatarInitial(name: String): String =
    name.replace(Regex("[\\s()]"), "").firstOrNull()?.toString() ?: "?"

private fun startOfToday(): Long {
    val cal = Calendar.getInstance()
    cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}

/**
 * 앱 데이터 → 프로토 상태. 시공일 미래=예약, 지남=완료, 잔금 받음=완료,
 *   시공 없고 14일 이내=신규, 14일 지남=미전환. (거래처/AS/단골은 데이터 없어 미할당)
 */
private fun customerStatus(c: CustomerEntity, today0: Long, now: Long): String {
    val wd = c.scheduledWorkDate
    if (wd != null) return if (wd >= today0) "예약" else "완료"
    if (c.balancePaidAt != null) return "완료"
    val ageDays = (now - c.createdAt) / 86_400_000L
    return if (ageDays <= 14) "신규" else "미전환"
}

/** .rmsg — 한 줄 요약. 시공일·잔금 미수·메모·번호 순으로 의미 있는 것 채움. */
private fun customerSubLine(c: CustomerEntity, status: String): String {
    val parts = mutableListOf<String>()
    c.scheduledWorkDate?.let {
        val d = SimpleDateFormat("M/d", Locale.KOREA).format(it)
        parts += if (status == "완료") "$d 시공 완료" else "시공 $d"
    }
    val bal = c.balanceAmount
    if (bal != null && bal > 0 && c.balancePaidAt == null) parts += "잔금 ${bal / 10000}만 미수"
    if (parts.isEmpty() && c.memo.isNotBlank()) parts += c.memo
    if (parts.isEmpty()) parts += PhoneNumberFormatter.format(c.phoneNumber)
    return parts.joinToString(" · ")
}
