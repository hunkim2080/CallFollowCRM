package com.detailline.callfollowcrm.presentation.screen.customer

import com.detailline.callfollowcrm.presentation.util.bottomBarClearance
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.Add
import com.detailline.callfollowcrm.presentation.theme.AppShape
import com.detailline.callfollowcrm.presentation.theme.AppSpace
import com.detailline.callfollowcrm.presentation.theme.AppType
import androidx.compose.material.icons.filled.Check
import com.detailline.callfollowcrm.presentation.theme.AppTheme
import com.detailline.callfollowcrm.presentation.theme.LightColors
import com.detailline.callfollowcrm.util.copyToClip
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.Send
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.detailline.callfollowcrm.data.local.entity.RecordingAttachmentEntity
import com.detailline.callfollowcrm.presentation.component.CelebrationOverlay
import com.detailline.callfollowcrm.presentation.component.SectionLabel
import com.detailline.callfollowcrm.presentation.component.TossCard
import com.detailline.callfollowcrm.presentation.component.tossCardShadow
import com.detailline.callfollowcrm.presentation.component.pressScale
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.detailline.callfollowcrm.presentation.component.TossChip
import com.detailline.callfollowcrm.presentation.component.TossPrimaryButton
import com.detailline.callfollowcrm.presentation.component.TossSecondaryButton
import com.detailline.callfollowcrm.presentation.component.vibrateCelebration
import com.detailline.callfollowcrm.presentation.theme.TossBlue
import com.detailline.callfollowcrm.presentation.theme.TossBlueDark
import com.detailline.callfollowcrm.presentation.theme.TossBlueSoft
import com.detailline.callfollowcrm.presentation.theme.TossDivider
import com.detailline.callfollowcrm.presentation.theme.TossGrayBg
import com.detailline.callfollowcrm.presentation.theme.TossError
import com.detailline.callfollowcrm.presentation.theme.TossSuccess
import com.detailline.callfollowcrm.presentation.theme.TossTextPrimary
import com.detailline.callfollowcrm.presentation.theme.TossTextSecondary
import com.detailline.callfollowcrm.presentation.theme.TossTextTertiary
import com.detailline.callfollowcrm.util.DateTimeUtils
import com.detailline.callfollowcrm.util.PhoneNumberFormatter
import com.detailline.callfollowcrm.util.splitSiteAddress
import kotlinx.coroutines.launch
import com.detailline.callfollowcrm.presentation.util.keyboardPadding
import com.detailline.callfollowcrm.util.PhoneKey
import androidx.compose.ui.graphics.graphicsLayer

// ======================================================================
//  🍃 고객 정보의 협업 조각 — 같이 보기 시트·협업 뒤 카드
//
//  CustomerDetailScreen.kt 에서 **그대로 옮겨온** 조각들이다. (2026-10-02)
//  옮긴 기준: 그 파일의 **다른 private 이름을 하나도 안 쓰는** 잎(leaf) 선언.
//  같은 패키지라 `private` → `internal` 한 글자만 바뀌었고 **부르는 곳은 그대로**다.
//  (Fable 점검 ✋ — 큰 화면을 통째로 쪼개는 건 위험하니 **잎부터** 떼어낸다)
// ======================================================================

/**
 * 공유 후 카드 — collab-sites-proto `a-after` 1:1. A(주인)가 고객 정보에서 협업 진행을 봄.
 *   헤더(협업 중 + 이름)·일당·진행 stepper·영구보관 안내·해제. 진행/일당은 서버 owner-events(같은 현장 제목 매칭),
 *   서버 미가동/없으면 배정 단계만(graceful). 증거사진은 §F GET /api/shared/photos (A 는 보기만).
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
internal fun CollabAfterCard(
    partnerName: String,
    siteTitle: String,
    shareId: String,
    onRelease: () -> Unit
) {
    val context = LocalContext.current
    val container = remember { (context.applicationContext as com.detailline.callfollowcrm.CallFollowCrmApplication).container }
    val purpleSoft = AppTheme.colors.categoryBg

    // 서버 진행 이벤트(있으면) — 같은 현장 제목으로 매칭. 없으면 배정 단계만(graceful).
    var step by remember(siteTitle, partnerName) { mutableStateOf<String?>(null) }
    var wage by remember(siteTitle, partnerName) { mutableStateOf<Int?>(null) }
    var photos by remember(shareId, siteTitle) { mutableStateOf(emptyList<com.detailline.callfollowcrm.ai.SharedSiteRepository.SharedPhoto>()) }
    // 수락 여부 — by-me 서버 status("pending"/"accepted"). 수락 전엔 '협업 중' 대신 작은 '수락 대기중' 카드. (2026-07-01 사장님)
    var status by remember(shareId, siteTitle, partnerName) { mutableStateOf<String?>(null) }
    var confirmRelease by remember { mutableStateOf(false) }
    // 사진 뷰어 — 열린 사진의 인덱스(null=닫힘). 좌우 스와이프로 다음/이전 사진. (2026-07-01 사장님)
    var viewerIdx by remember { mutableStateOf<Int?>(null) }
    // 협업 사장과 현장 한 줄 논의(댓글) — 고객정보 협업 탭에서도. resolvedSid=실제 share_id(옛기록은 byMe로 보충). (2026-07-01 사장님)
    var resolvedSid by remember(shareId, siteTitle) { mutableStateOf(shareId) }
    var comments by remember(shareId, siteTitle) { mutableStateOf(emptyList<com.detailline.callfollowcrm.ai.SharedSiteRepository.SiteComment>()) }
    var commentBusy by remember { mutableStateOf(false) }
    val commentScope = rememberCoroutineScope()
    androidx.compose.runtime.LaunchedEffect(siteTitle, partnerName, shareId) {
        val owner = container.preferences.bizPhone.filter { it.isDigit() }
        if (owner.length >= 9) {
            var sid = shareId
            container.sharedSiteRepository.ownerEvents(owner).onSuccess { events ->
                // 진행 매칭은 shareId(고유키) 우선 — 제목/상대이름 문자열 일치는 깨지기 쉬움.
                //   버그: 초대 때 굳은 title(그때 주소) vs 지금 주소로 다시 만든 siteTitle 이 다르거나,
                //   로컬 상대이름("디테일라인 사장")과 서버 partner_name("디테일라인")이 달라 이벤트가 하나도
                //   안 잡히면 → 완료를 눌러도 step=null → '배정'에 멈춰 '협업 중'으로 보임. (2026-07-06 사장님)
                //   shareId 없는 옛 기록만 제목+상대이름으로 폴백.
                val mine = (if (sid.isNotBlank()) events.filter { it.shareId == sid }
                            else events.filter { it.title == siteTitle && (partnerName == "협업 사장님" || it.partnerName == partnerName) })
                    .maxByOrNull { it.atMs }
                step = mine?.step
                wage = mine?.dailyWage
                if (sid.isBlank()) sid = mine?.shareId.orEmpty()  // 옛 기록(shareId 없음) → 이벤트에서 보충
            }
            // 증거사진 조회(§F) — A 는 보기만(프로토 a-after).
            if (sid.isNotBlank()) container.sharedSiteRepository.photos(sid, owner).onSuccess { photos = it }
            // 수락 여부(by-me status) — shareId 우선, 없으면 제목+상대이름 매칭.
            container.sharedSiteRepository.byMe(owner).onSuccess { mySites ->
                val m = (if (sid.isNotBlank()) mySites.firstOrNull { it.shareId == sid } else null)
                    ?: mySites.firstOrNull { it.title == siteTitle && (partnerName == "협업 사장님" || it.partnerName == partnerName) }
                if (m != null) status = m.status
            }
            resolvedSid = sid
            if (sid.isNotBlank()) container.sharedSiteRepository.comments(sid, owner).onSuccess { comments = it }
        }
    }
    // 자동 새로고침(폴링) — 카톡처럼 상대 댓글이 저절로 올라오게. 화면 열려있는 동안 4초 간격. (2026-07-01 사장님)
    androidx.compose.runtime.LaunchedEffect(resolvedSid) {
        val sid = resolvedSid
        val ownerP = container.preferences.bizPhone.filter { it.isDigit() }
        if (sid.isBlank() || ownerP.length < 9) return@LaunchedEffect
        while (true) {
            kotlinx.coroutines.delay(4000)
            container.sharedSiteRepository.comments(sid, ownerP).onSuccess { comments = it }
        }
    }
    val curIdx = when (step?.lowercase()) {
        "departed" -> 1; "arrived" -> 2; "completed" -> 3; else -> 0
    }
    // 완료된 현장 = 헤더 '협업 완료'(초록) + 큰 stepper 접기. 끝난 현장인데 배정/출발/도착/완료가
    //   그대로 떠서 카드만 커 보이던 것 정리. (2026-07-04 사장님)
    val completed = curIdx >= 3
    // 확정된 accepted 이거나 진행 단계가 있으면 '협업 중' 전체 카드. 그 전(pending·확인 전)엔 작은 '수락 대기중'.
    //   수락 전인데 협업중처럼 큰 카드가 떠서 '이미 함께 일하는 줄' 착각하는 문제 방지. (2026-07-01 사장님)
    val collabActive = status == "accepted" || curIdx > 0
    if (!collabActive) {
        Column(
            Modifier.fillMaxWidth().tossCardShadow(RoundedCornerShape(16.dp)).clip(RoundedCornerShape(16.dp)).background(Color.White)
                .border(1.dp, Color(0xFFF0E4C8), RoundedCornerShape(16.dp)).padding(15.dp)
        ) {
            androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("${partnerName} 사장님", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = TossTextPrimary,
                    maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                Spacer(Modifier.weight(1f))
                Box(Modifier.clip(RoundedCornerShape(999.dp)).background(AppTheme.colors.cautionBg).padding(horizontal = 10.dp, vertical = 4.dp)) {
                    Text("수락 대기중", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFB8780A))
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("아직 상대 사장님이 수락 전이에요. 수락하면 진행·사진이 여기 바로 떠요.",
                fontSize = 12.sp, color = TossTextTertiary, lineHeight = 17.sp)
            Spacer(Modifier.height(10.dp))
            Text("요청 취소", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TossTextSecondary,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { confirmRelease = true }.padding(vertical = 10.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
        if (confirmRelease) {
            androidx.compose.material3.AlertDialog(
                containerColor = Color.White,
                tonalElevation = 0.dp,
                onDismissRequest = { confirmRelease = false },
                title = { Text("요청을 취소할까요?", fontWeight = FontWeight.Bold) },
                text = { Text("${partnerName}님께 보낸 협업 요청을 취소해요. 나중에 다시 보낼 수 있어요.") },
                confirmButton = {
                    androidx.compose.material3.TextButton(onClick = { confirmRelease = false; onRelease() }) {
                        Text("요청 취소", color = AppTheme.colors.unpaid, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(onClick = { confirmRelease = false }) {
                        Text("그대로 두기", color = TossTextSecondary)
                    }
                }
            )
        }
        return
    }

    Column(
        Modifier.fillMaxWidth().tossCardShadow(RoundedCornerShape(16.dp)).clip(RoundedCornerShape(16.dp)).background(Color.White)
            .border(1.dp, Color(0xFFE2D8FB), RoundedCornerShape(16.dp)).padding(15.dp)
    ) {
        androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            // 제목 = 업체명(협업 사장 이름) — 2명 이상일 때 한눈에 구분(사장님 2026-08-09). 상태는 오른쪽 알약에.
            Text("$partnerName", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = TossTextPrimary, maxLines = 1)
            Spacer(Modifier.weight(1f))
            Box(Modifier.clip(RoundedCornerShape(999.dp)).background(if (completed) AppTheme.colors.doneBg else purpleSoft).padding(horizontal = 10.dp, vertical = 4.dp)) {
                Text(if (completed) "협업 완료" else "협업 중", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = if (completed) Color(0xFF0E9F56) else Color(0xFF6B4FD8))
            }
        }
        wage?.let {
            Spacer(Modifier.height(9.dp))
            androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("그날 일당", fontSize = 13.sp, color = TossTextTertiary, fontWeight = FontWeight.Medium)
                Spacer(Modifier.weight(1f))
                Text("${it}만원", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary)
            }
        }
        Spacer(Modifier.height(8.dp))
        if (completed) {
            // 완료 = 배정~완료 다 끝남. 큰 4단 stepper 는 접고 초록 한 줄로. (2026-07-04 사장님)
            androidx.compose.foundation.layout.Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(AppTheme.colors.doneBg)
                    .padding(horizontal = 13.dp, vertical = 11.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Box(Modifier.size(22.dp).clip(RoundedCornerShape(999.dp)).background(AppTheme.colors.done),
                    contentAlignment = androidx.compose.ui.Alignment.Center) {
                    Text("✓", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                }
                Spacer(Modifier.width(9.dp))
                Text("모든 진행이 끝난 현장이에요", fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold, color = Color(0xFF0E9F56))
            }
        } else {
            Text("상대가 올린 진행·사진·메모가 여기 그대로 들어와요.", fontSize = 12.sp, color = TossTextTertiary)
            Spacer(Modifier.height(12.dp))
            // 진행 stepper (A가 보는 상대 진행)
            androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                listOf("배정", "출발", "도착", "완료").forEachIndexed { i, label ->
                    Column(Modifier.weight(1f), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                        val done = i < curIdx; val cur = i == curIdx
                        val bg = when { done -> AppTheme.colors.done; cur -> AppTheme.colors.primary; else -> TossGrayBg }
                        val fg = if (done || cur) Color.White else TossTextTertiary
                        Box(Modifier.size(28.dp).clip(RoundedCornerShape(999.dp)).background(bg), contentAlignment = androidx.compose.ui.Alignment.Center) {
                            Text(if (done) "✓" else "${i + 1}", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = fg)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(label, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = if (done || cur) TossTextPrimary else TossTextTertiary)
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        // 📸 협업 사장님이 올린 증거사진 (proto a-after) — A 는 보기만.
        Text("${partnerName}이 올린 현장 사진 · 증거용", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = TossTextTertiary)
        Spacer(Modifier.height(2.dp))
        Text("시공 전·작업 중 상태를 남겨둔 사진이에요. '원래 그랬어요' 증거 → 두 분 다 분쟁에서 보호돼요.",
            fontSize = 11.sp, color = TossTextTertiary, lineHeight = 16.sp)
        Spacer(Modifier.height(8.dp))
        if (photos.isEmpty()) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(TossGrayBg).padding(vertical = 16.dp),
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                Text("🖼️", fontSize = 20.sp)
                Spacer(Modifier.height(4.dp))
                Text("협업 사장님이 사진을 올리면 여기 보여요", fontSize = 11.5.sp, color = TossTextTertiary)
            }
        } else {
            // 스와이프 뷰어용 — null 아닌 비트맵만 모아 인덱스로 연다.
            val bmps = remember(photos) { photos.mapNotNull { it.bitmap } }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                photos.forEach { p ->
                    val bmp = p.bitmap
                    androidx.compose.foundation.layout.Box(
                        Modifier.size(92.dp).clip(RoundedCornerShape(11.dp)).background(AppTheme.colors.surfaceMuted)
                            .then(if (bmp != null) Modifier.clickable { viewerIdx = bmps.indexOf(bmp).coerceAtLeast(0) } else Modifier),
                        contentAlignment = androidx.compose.ui.Alignment.Center
                    ) {
                        if (bmp != null) androidx.compose.foundation.Image(
                            bitmap = bmp.asImageBitmap(), contentDescription = p.label ?: "현장 사진",
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(11.dp)),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        ) else Text("🖼️", fontSize = 18.sp)
                    }
                }
            }
        }
        // 협업 사장과 현장 한 줄 논의 — 자동 새로고침(폴링)으로 카톡처럼 올라옴. (2026-07-01 사장님)
        Spacer(Modifier.height(16.dp))
        com.detailline.callfollowcrm.presentation.component.CollabCommentSection(
            comments = comments,
            myPhone = container.preferences.bizPhone.filter { it.isDigit() },
            busy = commentBusy,
            onSend = { body, onResult ->
                val sid = resolvedSid
                val ownerP = container.preferences.bizPhone.filter { it.isDigit() }
                if (sid.isNotBlank() && ownerP.length >= 9) {
                    val myName = container.preferences.bizName.takeIf { it.isNotBlank() } ?: container.preferences.bizOwner
                    commentBusy = true
                    commentScope.launch {
                        val r = container.sharedSiteRepository.postComment(sid, ownerP, myName, body)
                        r.onSuccess { container.sharedSiteRepository.comments(sid, ownerP).onSuccess { comments = it } }
                            .onFailure {
                                // 실패해도 조용히 넘어가 쓴 글이 사라지던 것 → 안내. (2026-07-30)
                                android.widget.Toast.makeText(context, "한마디가 안 올라갔어요 — 잠시 후 다시 시도해주세요", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        commentBusy = false
                        onResult(r.isSuccess)   // 성공했을 때만 입력칸 비우기. (2026-08-12 오프라인 감사)
                    }
                } else onResult(false)   // 못 보냈으면 쓴 글 유지
            }
        )

        Spacer(Modifier.height(14.dp))
        // 영구보관 안내 (proto a-after verbatim)
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(TossBlueSoft).padding(13.dp)) {
            Text("이 기록은 계속 남아요", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = TossBlue)
            Spacer(Modifier.height(5.dp))
            Text("사진·메모·진행 기록은 이 고객 정보에 영구 보관돼요. 3개월 뒤 고객이 또 연락해도 이걸 바로 꺼내 보고 응대할 수 있어요. 협업을 풀어도 안 지워져요.",
                fontSize = 12.sp, color = Color(0xFF3A4A66), lineHeight = 18.sp)
        }
        Spacer(Modifier.height(10.dp))
        Text("협업 해제 (사진·메모는 그대로 보존)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TossTextSecondary,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { confirmRelease = true }.padding(vertical = 11.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
    if (confirmRelease) {
        androidx.compose.material3.AlertDialog(
            containerColor = Color.White,
            tonalElevation = 0.dp,
            onDismissRequest = { confirmRelease = false },
            title = { Text("협업 해제할까요?", fontWeight = FontWeight.Bold) },
            text = { Text("${partnerName}님께 '협업이 해제됐어요' 알림이 가요. 사진·메모·진행 기록은 그대로 남고, 나중에 다시 요청할 수 있어요.") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { confirmRelease = false; onRelease() }) {
                    Text("해제", color = AppTheme.colors.unpaid, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { confirmRelease = false }) {
                    Text("그대로 두기", color = TossTextSecondary)
                }
            }
        )
    }
    // 📷 협업 증거 사진 — 좌우로 넘김. **돌려 보기만** 된다(남의 사진).
    //   2026-10-01 공용 뷰어로 옮김 — 전엔 여기도 돌리기가 없었다.
    //   사장님 "사진 회전기능 왜 추가안됐니" — 크게 보는 자리가 다섯인데 한 군데에만 넣었었다.
    viewerIdx?.let { startIdx ->
        val bmps = remember(photos) { photos.mapNotNull { it.bitmap } }
        if (bmps.isNotEmpty()) {
            com.detailline.callfollowcrm.presentation.component.FullscreenPhotoViewer(
                photos = bmps.map {
                    com.detailline.callfollowcrm.presentation.component.ViewerPhoto.OfBitmap(it)
                },
                startIndex = startIdx,
                onDismiss = { viewerIdx = null }
            )
        }
    }
}

/**
 * 협업 현장으로 공유 시트 — collab-sites-proto a-share 1:1.
 *   상대 사장 번호 입력 → /api/shared/invite. 가입 사장이면 인앱(상대 앱 "협업 현장"에 뜸),
 *   아니면 문자 링크(SmsIntentHelper). 자동발송 아님 — 상대 수락해야 시작.
 *   고객 전화번호/대화는 보내지 않음(customer_label = 안전 라벨만).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CollabShareSheet(
    siteTitle: String,
    addr: String?,
    scheduledAtMs: Long?,
    customerId: Long,
    /** 이 현장에 이미 요청을 보낸 사장님 번호들 — 줄에 「요청함」을 붙인다. (2026-09-27) */
    requestedPhones: List<String> = emptyList(),
    onShared: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val container = remember { (context.applicationContext as com.detailline.callfollowcrm.CallFollowCrmApplication).container }
    val workers by container.notebookRepository.observeWorkers().collectAsState(initial = emptyList())
    val recentSmsContacts by container.smsContactCacheRepository.observeAll(40).collectAsState(initial = emptyList())
    var partnerPhone by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    var dailyWage by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    var startHour by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(-1) } // 출근 시간(24h). -1 = 미선택
    var sending by remember { mutableStateOf(false) }
    /** 고른 사장님들 — 번호 끝 8자리. (2026-09-27 사장님 "다중선택가능") */
    var selected by remember { mutableStateOf(setOf<String>()) }
    /** 끝 8자리 → 일당(만원, 글자). 사람마다 다르게 적을 수 있다. */
    val wages = remember { androidx.compose.runtime.mutableStateMapOf<String, String>() }
    var memo by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    /** 번호 직접 넣기는 **접어 둔다** — 처음 부르는 분일 때만 쓰는 길이다. */
    var manualOpen by remember { mutableStateOf(false) }
    /** 이 현장에 이미 요청을 보낸 사장님(끝 8자리). 줄에 「요청함」으로 표시만 한다. */
    val requestedKeys = remember(requestedPhones) {
        requestedPhones.map { PhoneKey.of(it) }.filter { it.isNotEmpty() }.toSet()
    }
    /** 몇 번 같이 갔나 — 많이 부른 사람이 위로. */
    val freq = remember(workers) {
        container.preferences.collabAssignments.mapNotNull {
            it.split("|").getOrNull(1)?.let { ph -> PhoneKey.of(ph) }?.takeIf { k -> k.isNotBlank() }
        }.groupingBy { it }.eachCount()
    }
    val people = remember(workers, freq) {
        workers.filter { it.phone.filter { c -> c.isDigit() }.length >= 9 }
            .distinctBy { PhoneKey.of(it.phone) }
            .sortedWith(compareByDescending<com.detailline.callfollowcrm.data.local.entity.NotebookContactEntity> {
                freq[PhoneKey.of(it.phone)] ?: 0
            }.thenBy { it.name })
    }
    /** 보낼 번호들 — 고른 사람 + (열어서 채웠으면) 직접 넣은 번호. */
    fun pickedPhones(): List<String> {
        val fromList = people.map { it.phone.filter { c -> c.isDigit() } }
            .filter { PhoneKey.of(it) in selected }
        val manual = partnerPhone.filter { it.isDigit() }.takeIf { manualOpen && it.length >= 9 }
        return (fromList + listOfNotNull(manual)).distinctBy { PhoneKey.of(it) }
    }

    fun hourLabel(h: Int): String {
        val ampm = if (h < 12) "오전" else "오후"
        val h12 = if (h % 12 == 0) 12 else h % 12
        return "$ampm ${h12}시"
    }

    val dateLabel = remember(scheduledAtMs) {
        if (scheduledAtMs == null || scheduledAtMs <= 0L) "날짜 미정"
        else java.text.SimpleDateFormat("M월 d일 (E)", java.util.Locale.KOREA).format(java.util.Date(scheduledAtMs))
    }

    /** 고른 사람들에게 **차례로** 보낸다. 하나가 실패해도 나머지는 간다. */
    fun sendAll() {
        if (sending) return
        val owner = container.preferences.bizPhone.filter { it.isDigit() }
        if (owner.length < 9) {
            android.widget.Toast.makeText(context, "먼저 더보기 → 견적서·사업자 정보에서 내 전화번호를 등록해주세요", android.widget.Toast.LENGTH_LONG).show()
            return
        }
        val picks = pickedPhones()
        if (picks.isEmpty()) {
            android.widget.Toast.makeText(context, "부를 사장님을 골라주세요", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        sending = true
        // 출근 시간 — 그 날짜에 정시를 박아 보낸다.
        val baseMs = scheduledAtMs ?: 0L
        val effectiveMs = if (startHour in 0..23 && baseMs > 0L) {
            java.util.Calendar.getInstance().apply {
                timeInMillis = baseMs
                set(java.util.Calendar.HOUR_OF_DAY, startHour)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }.timeInMillis
        } else baseMs
        val timeLabel = startHour.takeIf { it in 0..23 }?.let { hourLabel(it) }
        val memoToSend = memo.trim().takeIf { it.isNotBlank() }
        scope.launch {
            var ok = 0
            var linkOpened = false
            var dedupedAny = false
            for (partner in picks) {
                val k8 = PhoneKey.of(partner)
                val res = container.sharedSiteRepository.invite(
                    ownerPhone = owner, partnerPhone = partner, title = siteTitle,
                    addr = addr, scheduledAtMs = effectiveMs,
                    workSummary = null, memo = memoToSend, customerLabel = siteTitle,
                    dailyWage = wages[k8]?.toIntOrNull(), timeLabel = timeLabel,
                    ownerName = container.preferences.bizName
                )
                res.onSuccess { r ->
                    ok++
                    val partnerName = workers.firstOrNull {
                        PhoneKey.of(it.phone) == k8
                    }?.name ?: partner
                    // 명부에 없던 번호면 넣어둔다 — 다음엔 이름으로 고를 수 있게.
                    if (workers.none { PhoneKey.of(it.phone) == k8 }) {
                        runCatching {
                            container.notebookRepository.add(
                                kind = com.detailline.callfollowcrm.data.local.entity.NotebookContactEntity.KIND_WORKER,
                                name = partner, phone = partner, tag = "협업",
                                memo = "협업 현장으로 함께 일한 사장님"
                            )
                        }
                    }
                    runCatching {
                        val already = container.preferences.collabAssignments.any { e ->
                            val p = e.split("|")
                            p.size >= 3 && p[0].toLongOrNull() == customerId &&
                                PhoneKey.of(p[1]) == k8
                        }
                        if (!already) {
                            container.preferences.collabAssignments =
                                container.preferences.collabAssignments + "$customerId|$partner|$partnerName|${r.shareId}"
                        }
                    }
                    if (r.deduped) dedupedAny = true
                    // 📨 문자 링크는 **한 번만** 연다 — 문자앱이 여러 번 뜨면 뭐가 뭔지 모른다.
                    if (!r.deduped && r.route == "link" && !r.url.isNullOrBlank() && !linkOpened) {
                        linkOpened = true
                        com.detailline.callfollowcrm.util.SmsIntentHelper.openSmsCompose(
                            context, partner, r.smsDraft ?: "협업 현장 공유 — ${r.url}"
                        )
                    }
                }
            }
            sending = false
            if (ok > 0) {
                onShared()
                android.widget.Toast.makeText(
                    context,
                    when {
                        dedupedAny && ok == 1 -> "이미 이 현장으로 협업 중인 사장님이에요"
                        ok == 1 -> "협업 요청을 보냈어요 — 수락하면 시작돼요"
                        else -> "${ok}명에게 협업 요청을 보냈어요"
                    },
                    android.widget.Toast.LENGTH_LONG
                ).show()
                onDismiss()
            } else {
                android.widget.Toast.makeText(context, "보내지 못했어요 — 잠시 후 다시 해주세요", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        androidx.compose.material3.Surface(
            shape = AppShape.lg, color = Color.White, modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.heightIn(max = 620.dp).padding(20.dp)) {
                com.detailline.callfollowcrm.presentation.util.ForceDialogResize()
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                // 이름은 **일정 쪽과 하나로**. 같은 일을 두 이름으로 부르지 않는다. (2026-09-27 사장님)
                Text("같이 할 사장님", style = MaterialTheme.typography.titleLarge,
                    color = TossTextPrimary, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                // 어느 현장인지 — **주소는 한 번만.** 전엔 제목과 아랫줄에 같은 주소가 두 번 나왔다.
                Column(Modifier.fillMaxWidth().clip(AppShape.md).background(TossGrayBg).padding(13.dp)) {
                    Text(siteTitle, style = AppType.headline, color = TossTextPrimary)
                    Spacer(Modifier.height(3.dp))
                    Text(
                        dateLabel + (addr?.takeIf { it.isNotBlank() }
                            ?.let { a -> if (siteTitle.contains(a.take(6))) "" else " · $a" } ?: ""),
                        style = AppType.label, color = TossTextTertiary, lineHeight = 17.sp
                    )
                }
                Spacer(Modifier.height(16.dp))

                // ── 누구를 부를까 — **이름 목록.** 알약은 이름만 보여 누군지 몰랐다. (2026-09-27 사장님)
                Text("누구를 부를까요", style = AppType.label, fontWeight = FontWeight.Bold,
                    color = TossTextTertiary, modifier = Modifier.padding(bottom = 7.dp))
                if (people.isEmpty()) {
                    Text("아직 등록한 사장님이 없어요 — 아래 [번호로 부르기]로 처음 한 번만 번호를 넣어주세요.",
                        style = AppType.label, color = TossTextTertiary, lineHeight = 18.sp)
                } else {
                    Column(Modifier.fillMaxWidth().clip(AppShape.md)
                        .border(1.dp, AppTheme.colors.line, AppShape.md)) {
                        people.forEachIndexed { idx, w ->
                            val k8 = PhoneKey.of(w.phone)
                            val on = k8 in selected
                            val asked = k8 in requestedKeys
                            if (idx > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(TossDivider))
                            androidx.compose.foundation.layout.Row(
                                Modifier.fillMaxWidth()
                                    .background(if (on) AppTheme.colors.categoryBg else Color.Transparent)
                                    .clickable { selected = if (on) selected - k8 else selected + k8 }
                                    .padding(horizontal = 11.dp, vertical = 10.dp),
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                            ) {
                                Box(
                                    Modifier.size(33.dp).clip(AppShape.md)
                                        .background(if (on) AppTheme.colors.category else AppTheme.colors.categoryBg),
                                    contentAlignment = androidx.compose.ui.Alignment.Center
                                ) {
                                    if (on) Icon(Icons.Default.Check, null, tint = Color.White,
                                        modifier = Modifier.size(16.dp))
                                    else Text(w.name.take(1), fontSize = 13.sp,
                                        fontWeight = FontWeight.Black, color = AppTheme.colors.category)
                                }
                                Spacer(Modifier.width(11.dp))
                                Column(Modifier.weight(1f)) {
                                    androidx.compose.foundation.layout.Row(
                                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                                    ) {
                                        Text(w.name, style = AppType.body, fontWeight = FontWeight.Bold,
                                            color = TossTextPrimary, maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                        if (asked) {
                                            Spacer(Modifier.width(6.dp))
                                            Text("요청함", style = AppType.caption, fontWeight = FontWeight.Bold,
                                                color = AppTheme.colors.category)
                                        }
                                    }
                                    // ☎️ **번호 먼저.** 이름만으론 누군지 못 알아본다. (2026-09-26 사장님)
                                    val n = freq[k8] ?: 0
                                    Text(
                                        com.detailline.callfollowcrm.util.PhoneNumberFormatter.format(w.phone) +
                                            (if (n > 0) " · 함께 ${n}번" else " · 아직 같이 안 감"),
                                        style = AppType.caption, color = TossTextTertiary, maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(9.dp))
                // 번호로 부르기 — 처음 부르는 분일 때만 쓰는 길이라 **접어 둔다.**
                Text(
                    if (manualOpen) "번호로 부르기 접기" else "목록에 없어요 · 번호로 부르기",
                    style = AppType.label, color = TossBlue,
                    modifier = Modifier.fillMaxWidth().clip(AppShape.sm)
                        .clickable { manualOpen = !manualOpen }.padding(vertical = 9.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                if (manualOpen) {
                    androidx.compose.material3.OutlinedTextField(
                        value = partnerPhone,
                        onValueChange = { partnerPhone = it.filter { c -> c.isDigit() }.take(11) },
                        placeholder = { Text("010-0000-0000", color = TossTextTertiary) },
                        singleLine = true,
                        visualTransformation = com.detailline.callfollowcrm.presentation.component.PhoneHyphenTransformation,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // ── 일당 — 고른 사람마다. 전엔 칸이 하나뿐이라 여러 명을 부를 수 없었다.
                val pickedPeople = people.filter { PhoneKey.of(it.phone) in selected }
                if (pickedPeople.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Text("보낼 일당", style = AppType.label, fontWeight = FontWeight.Bold,
                        color = TossTextTertiary, modifier = Modifier.padding(bottom = 7.dp))
                    pickedPeople.forEach { w ->
                        val k8 = PhoneKey.of(w.phone)
                        androidx.compose.foundation.layout.Row(
                            Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            Text(w.name, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                color = TossTextPrimary, modifier = Modifier.weight(1f), maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            Box(Modifier.width(96.dp)) {
                                com.detailline.callfollowcrm.presentation.component.SheetTextField(
                                    wages[k8].orEmpty(),
                                    { v -> wages[k8] = v.filter { c -> c.isDigit() }.take(4) },
                                    placeholder = "25",
                                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                                )
                            }
                            Spacer(Modifier.width(6.dp))
                            Text("만원", style = AppType.label, fontWeight = FontWeight.Bold, color = TossTextSecondary)
                        }
                    }
                    Text(
                        if (pickedPeople.size > 1) "사람마다 다르게 적을 수 있어요. 비워도 돼요."
                        else "이 현장에서 보낼 일당이에요. 비워도 돼요.",
                        style = AppType.caption, color = TossTextTertiary, modifier = Modifier.padding(top = 4.dp)
                    )
                }

                // ── 몇 시까지 — 「안 정함」을 칩으로 둬서 되돌릴 길을 만든다.
                Spacer(Modifier.height(16.dp))
                Text("몇 시까지 오면 되나요", style = AppType.label, fontWeight = FontWeight.Bold,
                    color = TossTextTertiary, modifier = Modifier.padding(bottom = 7.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    listOf(7, 8, 9, 10, 11, 13, 14).forEach { h ->
                        val sel = startHour == h
                        Box(
                            Modifier.clip(AppShape.pill)
                                .background(if (sel) AppTheme.colors.category else TossGrayBg)
                                .clickable { startHour = if (sel) -1 else h }
                                .padding(horizontal = 13.dp, vertical = 8.dp)
                        ) {
                            Text(hourLabel(h), style = AppType.label, fontWeight = FontWeight.Bold,
                                color = if (sel) Color.White else TossTextSecondary, maxLines = 1)
                        }
                    }
                    Box(
                        Modifier.clip(AppShape.pill)
                            .background(if (startHour !in 0..23) AppTheme.colors.category else TossGrayBg)
                            .clickable { startHour = -1 }
                            .padding(horizontal = 13.dp, vertical = 8.dp)
                    ) {
                        Text("안 정함", style = AppType.label, fontWeight = FontWeight.Bold,
                            color = if (startHour !in 0..23) Color.White else TossTextSecondary, maxLines = 1)
                    }
                }

                // ── 전해둘 말 — 일정 쪽 시트엔 있었는데 여기만 없어 따로 문자해야 했다.
                Spacer(Modifier.height(16.dp))
                Text("전해둘 말", style = AppType.label, fontWeight = FontWeight.Bold,
                    color = TossTextTertiary, modifier = Modifier.padding(bottom = 7.dp))
                com.detailline.callfollowcrm.presentation.component.SheetTextField(
                    memo, { memo = it },
                    placeholder = "예) 지하 주차장 B2 · 현관 비번 1234#",
                    modifier = Modifier.fillMaxWidth()
                )
                if (pickedPeople.size > 1) {
                    Text("고른 사장님 모두에게 같이 전달돼요.", style = AppType.caption,
                        color = TossTextTertiary, modifier = Modifier.padding(top = 4.dp))
                }

                // ── 벽 안내 — **한 줄.** 보낼 때마다 읽는 글이 아니다.
                Spacer(Modifier.height(16.dp))
                Column(Modifier.fillMaxWidth().clip(AppShape.md)
                    .background(TossGrayBg).padding(horizontal = 12.dp, vertical = 10.dp)) {
                    Text("주소·날짜·시간·사진만 보여요 · 고객 번호와 대화는 안 보여요",
                        style = AppType.caption, color = TossTextSecondary, lineHeight = 17.sp)
                }
                } // ── 스크롤 끝 ──

                // 📤 **버튼이 이름을 말한다** — 누구에게 가는지 마지막에 한 번 더. (2026-09-26)
                val pickedNames = people
                    .filter { PhoneKey.of(it.phone) in selected }.map { it.name }
                val manualReady = manualOpen && partnerPhone.filter { it.isDigit() }.length >= 9
                val total = pickedNames.size + (if (manualReady) 1 else 0)
                Spacer(Modifier.height(16.dp))
                androidx.compose.foundation.layout.Box(
                    Modifier.fillMaxWidth().clip(AppShape.md)
                        .background(if (total > 0) AppTheme.colors.category else AppTheme.colors.surfaceMuted)
                        .clickable(enabled = !sending && total > 0) { sendAll() }
                        .padding(vertical = 15.dp),
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) {
                    Text(
                        when {
                            sending -> "보내는 중…"
                            total == 0 -> "부를 사장님을 골라주세요"
                            total == 1 && pickedNames.size == 1 -> "${pickedNames.first()}에게 요청 보내기"
                            total == 1 -> "요청 보내기"
                            pickedNames.isNotEmpty() -> "${pickedNames.first()} 외 ${total - 1}명에게 요청 보내기"
                            else -> "${total}명에게 요청 보내기"
                        },
                        color = if (total > 0) Color.White else TossTextTertiary,
                        fontSize = 15.sp, fontWeight = FontWeight.ExtraBold
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text("자동 발송 아님 · 상대가 수락해야 시작돼요", style = AppType.caption, color = TossTextTertiary,
                    modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Text("취소", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TossTextSecondary,
                    modifier = Modifier.fillMaxWidth().clip(AppShape.sm).clickable { onDismiss() }.padding(vertical = 10.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
    }
}
