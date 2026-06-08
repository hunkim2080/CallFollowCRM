package com.detailline.callfollowcrm.presentation.screen.sharedsite

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.detailline.callfollowcrm.ai.SharedSiteRepository
import com.detailline.callfollowcrm.presentation.theme.TossGrayBg
import com.detailline.callfollowcrm.presentation.theme.TossTextPrimary
import com.detailline.callfollowcrm.presentation.theme.TossTextSecondary
import com.detailline.callfollowcrm.presentation.theme.TossTextTertiary
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 협업 현장 (B = 협업자) — 프로토 collab-sites-proto.html 의 b-list / b-detail 1:1.
 *   내 고객 목록과 분리된 별도 영역. 고객 전화번호·상대 다른 고객은 절대 안 보임(벽).
 *   서버 endpoint(/api/shared/…) 대기 동안엔 목록 비어 "공유받은 현장 없음" 안내.
 */
private val CollabPurple = Color(0xFF7C5CFC)
private val CollabPurpleSoft = Color(0xFFF1ECFE)
private val ProtoBlue = Color(0xFF3182F6)
private val ProtoSuccess = Color(0xFF16C172)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedSiteScreen(
    viewModel: SharedSiteViewModel,
    onBack: () -> Unit,
    /** 공유 링크(App Link)로 들어왔을 때 그 현장 상세를 자동으로 연다. */
    initialShareId: String? = null
) {
    val sites by viewModel.sites.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val toast by viewModel.toast.collectAsState()
    val context = LocalContext.current
    val accountPrompt = "입금받을 계좌를 먼저 등록해주세요. 더보기 → 견적서·사업자 정보에서 등록할 수 있어요."
    var selectedId by rememberSaveableShareId()

    LaunchedEffect(Unit) { viewModel.load() }
    // 링크로 진입 — 목록 로드 후 그 현장 상세 1회 자동 열기(사용자가 뒤로 가면 다시 안 엶).
    var consumedInitial by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(sites, initialShareId) {
        if (!consumedInitial && !initialShareId.isNullOrBlank() && sites.any { it.shareId == initialShareId }) {
            selectedId = initialShareId
            consumedInitial = true
        }
    }
    LaunchedEffect(toast) {
        toast?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_SHORT).show()
            viewModel.consumeToast()
        }
    }

    val selected = sites.firstOrNull { it.shareId == selectedId }
    BackHandler(enabled = selected != null) { selectedId = null }

    Scaffold(
        containerColor = TossGrayBg,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (selected != null) selected.title else "협업 현장",
                        fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { if (selected != null) selectedId = null else onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로", tint = TossTextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = TossGrayBg)
            )
        }
    ) { inner ->
        Column(
            Modifier
                .padding(inner)
                .fillMaxSize()
                .background(TossGrayBg)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 6.dp)
        ) {
            if (selected == null) {
                ListBody(sites = sites, loading = loading, noBizPhone = viewModel.noBizPhone) { selectedId = it.shareId }
            } else {
                DetailBody(
                    site = selected,
                    hasAccount = viewModel.hasAccount(),
                    onNavigate = { addr ->
                        runCatching {
                            val uri = Uri.parse("geo:0,0?q=" + Uri.encode(addr))
                            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                        }
                    },
                    onProgress = { step ->
                        if (step == SharedSiteRepository.Progress.COMPLETED && !viewModel.hasAccount()) {
                            android.widget.Toast.makeText(context, accountPrompt, android.widget.Toast.LENGTH_LONG).show()
                        } else {
                            viewModel.updateProgress(selected, step)
                        }
                    },
                    onRespond = { accept -> viewModel.respond(selected, accept); if (!accept) selectedId = null }
                )
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun ListBody(
    sites: List<SharedSiteRepository.SharedSite>,
    loading: Boolean,
    noBizPhone: Boolean,
    onOpen: (SharedSiteRepository.SharedSite) -> Unit
) {
    Text(
        "다른 사장님과 같이 하는 현장이에요. 내 고객 목록과는 따로 모여요.",
        fontSize = 12.5.sp, color = TossTextTertiary, modifier = Modifier.padding(start = 2.dp, top = 2.dp, bottom = 8.dp)
    )

    when {
        noBizPhone -> EmptyCard(
            "먼저 사업자 전화를 등록해주세요",
            "더보기 → 견적서·사업자 정보에서 전화번호를 넣으면, 다른 사장님이 그 번호로 현장을 공유할 수 있어요."
        )
        sites.isEmpty() && loading -> EmptyCard("불러오는 중…", "")
        sites.isEmpty() -> EmptyCard(
            "공유받은 현장이 없어요",
            "다른 사장님이 'OO 현장 같이 하자'고 공유하면 여기에 모여요. 초대받은 현장만 보이고, 그 사장님의 다른 고객은 안 보여요."
        )
        else -> sites.forEach { site ->
            SiteRow(site, onClick = { onOpen(site) })
            Spacer(Modifier.height(9.dp))
        }
    }
}

@Composable
private fun SiteRow(site: SharedSiteRepository.SharedSite, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White)
            .border(1.dp, Color(0xFFEEF0F3), RoundedCornerShape(14.dp))
            .clickable { onClick() }.padding(13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(CollabPurpleSoft), contentAlignment = Alignment.Center) {
            Text("🤝", fontSize = 16.sp)
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(site.title, fontSize = 14.5.sp, fontWeight = FontWeight.ExtraBold, color = TossTextPrimary)
                Spacer(Modifier.width(6.dp))
                Pill(dayLabel(site.scheduledAtMs))
            }
            val sub = buildString {
                append(site.ownerName)
                site.workSummary?.let { append(" · "); append(it) }
                site.timeLabel?.let { append(" · "); append(it) }
            }
            Text(sub, fontSize = 12.sp, color = TossTextTertiary, fontWeight = FontWeight.Medium)
        }
        Text("›", fontSize = 18.sp, color = TossTextTertiary)
    }
}

@Composable
private fun DetailBody(
    site: SharedSiteRepository.SharedSite,
    hasAccount: Boolean,
    onNavigate: (String) -> Unit,
    onProgress: (SharedSiteRepository.Progress) -> Unit,
    onRespond: (Boolean) -> Unit
) {
    // 협업 현장 pill + 주인
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
        PillStrong("협업 현장")
        Spacer(Modifier.width(8.dp))
        Text("${site.ownerName}과 함께", fontSize = 12.5.sp, color = TossTextTertiary, fontWeight = FontWeight.Medium)
    }
    Spacer(Modifier.height(10.dp))

    // 날짜·시공 카드
    Card {
        InfoRow("📅 날짜", buildString { append(dayLabel(site.scheduledAtMs)); site.timeLabel?.let { append(" · "); append(it) } })
        site.workSummary?.let { Spacer(Modifier.height(9.dp)); InfoRow("🔧 시공", it) }
    }

    // 주소
    if (!site.addr.isNullOrBlank()) {
        Spacer(Modifier.height(10.dp))
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                .background(Color(0xFFF5F9FF)).border(1.5.dp, Color(0xFFE2EDFD), RoundedCornerShape(16.dp)).padding(15.dp)
        ) {
            Text("📍 현장 주소", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = TossTextTertiary)
            Spacer(Modifier.height(7.dp))
            Text(site.addr, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary, lineHeight = 22.sp)
            Spacer(Modifier.height(11.dp))
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(ProtoBlue)
                    .clickable { onNavigate(site.addr) }.padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) { Text("길찾기 시작", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold) }
        }
    }

    // 대표님 전달사항
    if (!site.memo.isNullOrBlank()) {
        Spacer(Modifier.height(10.dp))
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                .background(Color(0xFFFFF8E8)).border(1.dp, Color(0xFFF6E4B8), RoundedCornerShape(14.dp)).padding(13.dp)
        ) {
            Text("📌 대표님 전달사항", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFB4790A))
            Spacer(Modifier.height(5.dp))
            Text(site.memo, fontSize = 14.sp, color = Color(0xFF5A4A1F), lineHeight = 21.sp)
        }
    }

    // 초대 수락 전(pending) — 수락/거절. 수락해야 진행 단계가 열림.
    if (site.status == "pending") {
        Spacer(Modifier.height(16.dp))
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(CollabPurpleSoft)
                .border(1.dp, Color(0xFFE2D8FB), RoundedCornerShape(14.dp)).padding(14.dp)
        ) {
            Text("🤝 ${site.ownerName}이 이 현장을 함께 하재요", fontSize = 13.5.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF6B4FD8))
            Spacer(Modifier.height(4.dp))
            Text("수락하면 내 '협업 현장'에 들어오고 진행을 같이 기록해요.", fontSize = 12.sp, color = Color(0xFF5A4A7A), lineHeight = 17.sp)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(TossGrayBg)
                        .clickable { onRespond(false) }.padding(vertical = 13.dp),
                    contentAlignment = Alignment.Center
                ) { Text("거절", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TossTextSecondary) }
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(CollabPurple)
                        .clickable { onRespond(true) }.padding(vertical = 13.dp),
                    contentAlignment = Alignment.Center
                ) { Text("수락", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = Color.White) }
            }
        }
        // 벽 안내만 보여주고 진행 단계는 수락 후.
        Spacer(Modifier.height(16.dp))
        WallNote(site.ownerName)
        return
    }

    // 진행 상황 (눌러서 알려요) — 수락된 현장만.
    Spacer(Modifier.height(16.dp))
    SectionSub("진행 상황 (눌러서 알려요)")
    Stepper(site.progress)
    Spacer(Modifier.height(8.dp))

    val next = when (site.progress) {
        SharedSiteRepository.Progress.ASSIGNED -> SharedSiteRepository.Progress.DEPARTED to "🚗 출발 알리기"
        SharedSiteRepository.Progress.DEPARTED -> SharedSiteRepository.Progress.ARRIVED to "📍 도착 알리기"
        SharedSiteRepository.Progress.ARRIVED -> SharedSiteRepository.Progress.COMPLETED to "✅ 완료 알리기"
        SharedSiteRepository.Progress.COMPLETED -> null
    }
    if (next != null) {
        val (step, label) = next
        val completing = step == SharedSiteRepository.Progress.COMPLETED
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                .background(if (completing && !hasAccount) Color(0xFFE5E8EF) else if (completing) ProtoSuccess else ProtoBlue)
                .clickable { onProgress(step) }.padding(vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (completing && !hasAccount) "계좌 등록 후 완료 알리기" else label,
                color = if (completing && !hasAccount) TossTextSecondary else Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
        if (completing) {
            Spacer(Modifier.height(7.dp))
            Text(
                if (hasAccount) "완료를 누르면 주인 사장님께 '완료 + 내 입금 계좌'가 전달돼요."
                else "⚠️ 입금받을 계좌가 없어요. 더보기 → 견적서·사업자 정보에서 계좌를 먼저 등록하면 완료 시 자동 전달돼요.",
                fontSize = 11.5.sp, color = if (hasAccount) TossTextTertiary else Color(0xFFD9534F), lineHeight = 16.sp,
                modifier = Modifier.padding(horizontal = 2.dp)
            )
        }
    } else {
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(CollabPurpleSoft).padding(vertical = 13.dp),
            contentAlignment = Alignment.Center
        ) { Text("완료된 현장이에요 ✓", color = CollabPurple, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold) }
    }

    // 벽 안내
    Spacer(Modifier.height(16.dp))
    WallNote(site.ownerName)
}

@Composable private fun WallNote(ownerName: String) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(CollabPurpleSoft)
            .border(1.dp, Color(0xFFE2D8FB), RoundedCornerShape(14.dp)).padding(13.dp)
    ) {
        Text("🔒 이 현장만 보여요", fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF6B4FD8))
        Spacer(Modifier.height(6.dp))
        Text("• 고객 전화번호 · 대화는 안 보여요\n• ${ownerName}의 다른 고객도 안 보여요",
            fontSize = 12.5.sp, color = Color(0xFF5A4A7A), lineHeight = 20.sp)
    }
}

// ── 작은 컴포넌트들 ──
@Composable private fun Card(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White)
        .border(1.dp, Color(0xFFEEF0F3), RoundedCornerShape(16.dp)).padding(15.dp), content = content)
}

@Composable private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 13.sp, color = TossTextTertiary, fontWeight = FontWeight.Medium)
        Spacer(Modifier.weight(1f))
        Text(value, fontSize = 13.5.sp, color = TossTextPrimary, fontWeight = FontWeight.Bold)
    }
}

@Composable private fun SectionSub(text: String) {
    Text(text, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TossTextTertiary,
        modifier = Modifier.padding(start = 2.dp, bottom = 8.dp))
}

@Composable private fun Pill(text: String) {
    Box(Modifier.clip(RoundedCornerShape(999.dp)).background(Color(0xFFFEF3E0)).padding(horizontal = 9.dp, vertical = 3.dp)) {
        Text(text, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFB4790A))
    }
}
@Composable private fun PillStrong(text: String) {
    Box(Modifier.clip(RoundedCornerShape(999.dp)).background(CollabPurpleSoft).padding(horizontal = 10.dp, vertical = 4.dp)) {
        Text(text, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF6B4FD8))
    }
}

@Composable private fun EmptyCard(title: String, sub: String) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White)
            .border(1.dp, Color(0xFFEEF0F3), RoundedCornerShape(16.dp)).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("🤝", fontSize = 30.sp)
        Spacer(Modifier.height(8.dp))
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = TossTextPrimary)
        if (sub.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(sub, fontSize = 12.5.sp, color = TossTextTertiary, lineHeight = 19.sp,
                modifier = Modifier.padding(horizontal = 6.dp))
        }
    }
}

@Composable private fun Stepper(progress: SharedSiteRepository.Progress) {
    val steps = listOf("배정", "출발", "도착", "완료")
    val curIdx = when (progress) {
        SharedSiteRepository.Progress.ASSIGNED -> 0
        SharedSiteRepository.Progress.DEPARTED -> 1
        SharedSiteRepository.Progress.ARRIVED -> 2
        SharedSiteRepository.Progress.COMPLETED -> 3
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        steps.forEachIndexed { i, label ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                val done = i < curIdx
                val cur = i == curIdx
                val bg = when { done -> ProtoSuccess; cur -> ProtoBlue; else -> TossGrayBg }
                val fg = if (done || cur) Color.White else TossTextTertiary
                Box(Modifier.size(30.dp).clip(RoundedCornerShape(999.dp)).background(bg), contentAlignment = Alignment.Center) {
                    Text(if (done) "✓" else "${i + 1}", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = fg)
                }
                Spacer(Modifier.height(5.dp))
                Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    color = if (done || cur) TossTextPrimary else TossTextTertiary)
            }
        }
    }
}

// 날짜 라벨: 오늘/내일/M.d
private fun dayLabel(ms: Long): String {
    if (ms <= 0L) return "날짜 미정"
    val cal = Calendar.getInstance().apply { timeInMillis = ms }
    val today = Calendar.getInstance()
    fun ymd(c: Calendar) = c.get(Calendar.YEAR) * 10000 + c.get(Calendar.MONTH) * 100 + c.get(Calendar.DAY_OF_MONTH)
    val diff = ymd(cal) - ymd(today)
    return when {
        diff == 0 -> "오늘"
        diff == 1 -> "내일"
        else -> SimpleDateFormat("M.d", Locale.KOREA).format(Date(ms))
    }
}

@Composable
private fun rememberSaveableShareId() =
    androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }
