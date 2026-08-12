package com.detailline.callfollowcrm.presentation.screen.expo

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import kotlin.math.sin
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.detailline.callfollowcrm.ai.ExpoRepository
import com.detailline.callfollowcrm.data.AppContainer
import com.detailline.callfollowcrm.presentation.component.tossCardShadow
import com.detailline.callfollowcrm.util.QrGen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 박람회 모드 (2026-07-21 사장님) — 박람회 시공 팀 전용 창구.
 *   시공막내 안의 **완전 별개 공간**: 하단 탭바 없이 풀스크린, 카카오톡 느낌(노랑·둥근사각)으로 격리.
 *   Phase 1(종이 계약서 없애기): 방 개설(코드)·상품카탈로그·QR 계약서·팀 접수서 목록.
 *   서버: docs/SERVER_HANDOFF_expo_phase1.md (전부 라이브). 확정: docs/EXPO_DECISIONS.md.
 *   분배·진행률(확정6·8)은 Phase 3 → 여기 없음.
 *
 * ⚠️ 이 화면은 앱 나머지(토스 스타일)와 디자인이 일부러 다르다. "박람회는 별세계" 컨셉.
 */

private val Kk = Color(0xFFFEE500)          // 카카오 옐로
private val KkInk = Color(0xFF1A1A1A)       // 옐로 위 글자
private val ExpoBg = Color(0xFFEDEFF2)      // 페이지 배경
private val Panel = Color(0xFFFFFFFF)
private val T1 = Color(0xFF1A1A1A)
private val T2 = Color(0xFF5F666D)
private val T3 = Color(0xFF9AA0A6)
private val Field = Color(0xFFF1F3F5)
private val AccentBlue = Color(0xFF5B7CFA)
private val OcrFill = Color(0xFFFFF7D6)      // OCR로 채운 칸 형광(연한 노랑) 하이라이트

/** OCR로 채운 입력칸 하이라이트 컬러. on 이면 연한 형광 배경. */
@Composable
private fun ocrFieldColors(on: Boolean) = if (on)
    androidx.compose.material3.OutlinedTextFieldDefaults.colors(
        unfocusedContainerColor = OcrFill, focusedContainerColor = OcrFill)
else androidx.compose.material3.OutlinedTextFieldDefaults.colors()

private sealed class Nav {
    object List : Nav()
    data class RoomView(val roomId: String, val name: String, val role: String) : Nav()
    data class Products(val roomId: String, val name: String) : Nav()
    data class Qr(val roomId: String, val name: String, val session: ExpoRepository.Session) : Nav()
    data class Subs(val roomId: String, val name: String) : Nav()
    data class MyInbox(val roomId: String, val name: String) : Nav()   // 내 접수서함(배정받은 것만)
    data class Calendar(val roomId: String, val name: String) : Nav()
    /** 앱 안 네이티브 계약서 보기 (웹 안 열고). fromCalendar=달력에서 왔는지(뒤로가기 목적지). */
    data class Contract(val roomId: String, val name: String, val contractId: Long, val fromCalendar: Boolean) : Nav()
    /** 박람회 기본정보 폼 (방장) — 방 개설 직후 or 방 상세에서 수정. */
    data class RoomForm(val roomId: String, val name: String) : Nav()
}

/**
 * 재진입 즉시 렌더용 인메모리 캐시 (stale-while-revalidate).
 * 화면은 마지막으로 본 값을 즉시 그리고, 뒤에서 새로고침해 최신으로 교체 → 재진입 시 스켈레톤/스피너 안 보임.
 * 앱 프로세스 동안만 유지(휘발). 데이터가 바뀌는 액션(배정·시공일·메모 등)은 각 화면이 다시 fetch 하므로 최신 반영.
 */
private object ExpoCache {
    var rooms: List<ExpoRepository.Room>? = null
    val details = HashMap<String, ExpoRepository.RoomDetail>()
    val submissions = HashMap<String, ExpoRepository.Submissions>()
}

@Composable
fun ExpoScreen(container: AppContainer, onExit: () -> Unit) {
    val ctx = LocalContext.current
    val repo = remember { ExpoRepository() }
    val myPhone = remember { container.preferences.bizPhone }
    val myName = remember {
        container.preferences.bizOwner.ifBlank { container.preferences.bizName }.ifBlank { "나" }
    }
    fun toast(m: String) = Toast.makeText(ctx, m, Toast.LENGTH_SHORT).show()

    var nav by remember { mutableStateOf<Nav>(Nav.List) }
    BackHandler {
        nav = when (val n = nav) {
            is Nav.List -> { onExit(); n }
            is Nav.RoomView -> Nav.List
            is Nav.Products -> Nav.RoomView(n.roomId, n.name, "owner")
            is Nav.Qr -> Nav.RoomView(n.roomId, n.name, "member")
            is Nav.Subs -> Nav.RoomView(n.roomId, n.name, "member")
            is Nav.MyInbox -> Nav.RoomView(n.roomId, n.name, "member")
            is Nav.Calendar -> Nav.RoomView(n.roomId, n.name, "member")
            is Nav.Contract -> if (n.fromCalendar) Nav.Calendar(n.roomId, n.name) else Nav.Subs(n.roomId, n.name)
            is Nav.RoomForm -> Nav.RoomView(n.roomId, n.name, "owner")
        }
    }

    Column(Modifier.fillMaxSize().background(ExpoBg)) {
        // ── 카카오풍 상단 바 ──
        val title = when (val n = nav) {
            is Nav.List -> "박람회"
            is Nav.RoomView -> n.name
            is Nav.Products -> "상품·서비스 준비"
            is Nav.Qr -> "계약서 작성"
            is Nav.Subs -> "우리 팀 접수서"
            is Nav.MyInbox -> "내 접수서함"
            is Nav.Calendar -> "박람회 달력"
            is Nav.Contract -> "계약서"
            is Nav.RoomForm -> "박람회 기본정보"
        }
        val sub = if (nav is Nav.List) "팀으로 상담·계약을 한 번에" else null
        ExpoTopBar(title, sub, isRoot = nav is Nav.List) {
            // 뒤로/닫기
            nav = when (val n = nav) {
                is Nav.List -> { onExit(); n }
                is Nav.RoomView -> Nav.List
                is Nav.Products -> Nav.RoomView(n.roomId, n.name, "owner")
                is Nav.Qr -> Nav.RoomView(n.roomId, n.name, "member")
                is Nav.Subs -> Nav.RoomView(n.roomId, n.name, "member")
                is Nav.MyInbox -> Nav.RoomView(n.roomId, n.name, "member")
                is Nav.Calendar -> Nav.RoomView(n.roomId, n.name, "member")
                is Nav.Contract -> if (n.fromCalendar) Nav.Calendar(n.roomId, n.name) else Nav.Subs(n.roomId, n.name)
                is Nav.RoomForm -> Nav.RoomView(n.roomId, n.name, "owner")
            }
        }

        when (val n = nav) {
            is Nav.List -> RoomListView(repo, myPhone, myName, ::toast,
                onOpen = { nav = Nav.RoomView(it.roomId, it.name, it.role) },
                onCreated = { nav = Nav.RoomForm(it.roomId, it.name) })
            is Nav.RoomView -> RoomDetailView(repo, n, myPhone, myName, ::toast,
                onProducts = { nav = Nav.Products(n.roomId, n.name) },
                onQr = { nav = Nav.Qr(n.roomId, n.name, it) },
                onSubs = { nav = Nav.Subs(n.roomId, n.name) },
                onMyInbox = { nav = Nav.MyInbox(n.roomId, n.name) },
                onCalendar = { nav = Nav.Calendar(n.roomId, n.name) },
                onEditInfo = { nav = Nav.RoomForm(n.roomId, n.name) })
            is Nav.Products -> ProductsEditorView(repo, n, myPhone, ::toast,
                onDone = { nav = Nav.RoomView(n.roomId, n.name, "owner") })
            is Nav.Qr -> QrView(repo, n, myPhone, ::toast)
            is Nav.Subs -> SubmissionsView(repo, n, myPhone, ::toast,
                onContract = { nav = Nav.Contract(n.roomId, n.name, it, fromCalendar = false) })
            is Nav.MyInbox -> MyInboxView(repo, n, myPhone, ::toast,
                onContract = { nav = Nav.Contract(n.roomId, n.name, it, fromCalendar = false) })
            is Nav.Calendar -> CalendarView(repo, n, myPhone,
                onContract = { nav = Nav.Contract(n.roomId, n.name, it, fromCalendar = true) })
            is Nav.Contract -> ContractView(repo, n, myPhone, ::toast)
            is Nav.RoomForm -> RoomFormView(repo, n, myPhone, ::toast,
                onSaved = { nav = Nav.RoomView(n.roomId, n.name, "owner") })
        }
    }
}

@Composable
private fun ExpoTopBar(title: String, sub: String?, isRoot: Boolean, onNav: () -> Unit) {
    Column(Modifier.background(Kk).statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 10.dp, top = 6.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!isRoot) {
                Box(Modifier.size(34.dp).clickable(onClick = onNav), contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로", tint = KkInk, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(2.dp))
            }
            Column(Modifier.weight(1f).padding(start = if (isRoot) 4.dp else 0.dp)) {
                Text(title, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = KkInk,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (sub != null) Text(sub, fontSize = 11.5.sp, color = Color(0xFF6B5A00), fontWeight = FontWeight.SemiBold)
            }
            if (isRoot) {
                Box(Modifier.size(34.dp).clickable(onClick = onNav), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Close, "시공막내로 나가기", tint = KkInk, modifier = Modifier.size(22.dp))
                }
            }
        }
    }
}

// ══════════════ 방 목록 ══════════════
@Composable
private fun ColumnScope.RoomListView(
    repo: ExpoRepository, myPhone: String, myName: String, toast: (String) -> Unit,
    onOpen: (ExpoRepository.Room) -> Unit, onCreated: (ExpoRepository.Room) -> Unit
) {
    val scope = rememberCoroutineScope()
    var rooms by remember { mutableStateOf(ExpoCache.rooms) }
    var showCreate by remember { mutableStateOf(false) }
    var showJoin by remember { mutableStateOf(false) }

    fun reload() = scope.launch {
        repo.rooms(myPhone).onSuccess { rooms = it; ExpoCache.rooms = it }
            .onFailure { if (rooms == null) rooms = emptyList(); toast("목록을 불러오지 못했어요") }
    }
    LaunchedEffect(Unit) { reload() }

    Box(Modifier.weight(1f).fillMaxWidth()) {
        val list = rooms
        when {
            list == null -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(4) {
                    Row(Modifier.fillMaxWidth().tossCardShadow(RoundedCornerShape(16.dp)).background(Panel, RoundedCornerShape(16.dp)).padding(13.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Skel(Modifier.size(46.dp), 16)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Skel(Modifier.fillMaxWidth(0.55f).height(15.dp))
                            Spacer(Modifier.height(8.dp))
                            Skel(Modifier.fillMaxWidth(0.75f).height(11.dp))
                        }
                    }
                }
            }
            list.isEmpty() -> Column(
                Modifier.fillMaxSize().padding(28.dp),
                verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("🎪", fontSize = 44.sp)
                Spacer(Modifier.height(14.dp))
                Text("아직 참여한 방이 없어요", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = T1)
                Spacer(Modifier.height(7.dp))
                Text(
                    "방을 만들어 팀원을 초대하거나,\n초대코드로 팀 방에 합류하세요.",
                    fontSize = 13.sp, color = T3, fontWeight = FontWeight.Medium, lineHeight = 20.sp,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
                )
            }
            else -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(list) { r -> RoomRow(r) { onOpen(r) } }
            }
        }
    }

    // 하단 버튼 2개
    Row(
        Modifier.background(ExpoBg).navigationBarsPadding().padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            Modifier.weight(1f).background(Color.White, RoundedCornerShape(15.dp))
                .clickable { showJoin = true }.padding(vertical = 15.dp),
            contentAlignment = Alignment.Center
        ) { Text("초대코드로 합류", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = T1) }
        Row(
            Modifier.weight(1f).background(Kk, RoundedCornerShape(15.dp))
                .clickable { showCreate = true }.padding(vertical = 15.dp),
            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Add, null, tint = KkInk, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(4.dp))
            Text("방 만들기", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = KkInk)
        }
    }

    if (showCreate) InputDialog(
        title = "새 박람회 방", label = "방(부스) 이름을 적어주세요.",
        placeholder = "예: 2026 봄 홈리모델링박람회", confirm = "만들기",
        onDismiss = { showCreate = false }, onConfirm = { name ->
            showCreate = false
            scope.launch {
                repo.createRoom(myPhone, name, myName)
                    .onSuccess { toast("방이 만들어졌어요 · 기본정보를 채워주세요"); reload(); onCreated(it) }
                    .onFailure { toast("방을 만들지 못했어요 — 잠시 후 다시 시도해주세요") }
            }
        }
    )
    if (showJoin) InputDialog(
        title = "초대코드로 합류", label = "방장에게 받은 6자리 코드를 입력하세요.",
        placeholder = "예: 324573", confirm = "합류", numeric = true,
        onDismiss = { showJoin = false }, onConfirm = { code ->
            showJoin = false
            scope.launch {
                repo.joinRoom(code, myPhone, myName)
                    .onSuccess { toast("방에 합류했어요"); reload(); onOpen(it) }
                    .onFailure { toast("합류 실패: 코드를 확인하세요") }
            }
        }
    )
}

/** 로딩 스켈레톤 조각 — 꽉 찬 스피너 대신 화면 구조를 먼저 보여줌(끊김 느낌 제거). */
@Composable
private fun Skel(modifier: Modifier, corner: Int = 10) {
    Box(modifier.clip(RoundedCornerShape(corner.dp)).background(Color(0xFFE4E7EC)))
}

@Composable
private fun RoomRow(r: ExpoRepository.Room, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().tossCardShadow(RoundedCornerShape(16.dp)).background(Panel, RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(46.dp).background(AccentBlue, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
            Text(r.name.take(1), fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(r.name, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = T1, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("팀원 ${r.memberCount} · 상품 ${r.productCount} · 접수 ${r.contractCount}",
                fontSize = 11.5.sp, color = T3, fontWeight = FontWeight.Medium)
        }
        val isOwner = r.role == "owner"
        val badge = if (isOwner) "방장" else "팀원"
        // 방장=금색, 팀원=파랑 (색으로 역할 구분)
        val badgeFg = if (isOwner) Color(0xFFB58A00) else Color(0xFF2F6FDB)
        val badgeBg = if (isOwner) Color(0xFFFFF6D6) else Color(0xFFE7F0FB)
        Text(badge, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = badgeFg,
            modifier = Modifier.background(badgeBg, RoundedCornerShape(7.dp)).padding(horizontal = 8.dp, vertical = 3.dp))
    }
}

// ══════════════ 방 상세 ══════════════
@Composable
private fun ColumnScope.RoomDetailView(
    repo: ExpoRepository, n: Nav.RoomView, myPhone: String, myName: String, toast: (String) -> Unit,
    onProducts: () -> Unit, onQr: (ExpoRepository.Session) -> Unit, onSubs: () -> Unit, onMyInbox: () -> Unit, onCalendar: () -> Unit,
    onEditInfo: () -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var detail by remember { mutableStateOf(ExpoCache.details[n.roomId]) }
    var code by remember { mutableStateOf<String?>(null) }
    var opening by remember { mutableStateOf(false) }

    LaunchedEffect(n.roomId) {
        repo.roomDetail(n.roomId, myPhone).onSuccess { dt ->
            detail = dt
            ExpoCache.details[n.roomId] = dt
            // 방장이면 초대코드도 (rooms 목록에서 code 내려오지만 상세엔 없음 → rooms 재조회).
            // ⚠️ role 판정은 n.role 말고 서버가 준 dt.myRole 로. n.role 은 접수서/달력 갔다 뒤로가기 때
            //    "member" 로 덮여서, 돌아오면 방장 기능(상품등록·초대코드)이 사라지는 버그가 있었음.
            if (dt.myRole == "owner") repo.rooms(myPhone).onSuccess { list -> code = list.find { it.roomId == n.roomId }?.code }
        }.onFailure { toast("방 정보를 못 불러왔어요") }
    }

    var memOpen by remember { mutableStateOf(false) }
    val d = detail
    LazyColumn(
        Modifier.weight(1f).fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (d == null) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Skel(Modifier.fillMaxWidth().height(92.dp), 18)
                    Skel(Modifier.fillMaxWidth().height(118.dp), 18)
                    Skel(Modifier.fillMaxWidth().height(150.dp), 18)
                }
            }
        } else {
            // ── ① 방 정보: 초대코드(방장) + 팀원(접기) ──
            item {
                Column(Modifier.fillMaxWidth().tossCardShadow(RoundedCornerShape(18.dp)).background(Panel, RoundedCornerShape(18.dp)).padding(16.dp)) {
                    if (d.myRole == "owner" && code != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("팀원 초대코드", fontSize = 11.5.sp, color = T3, fontWeight = FontWeight.Bold)
                                Text(code!!, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = T1, letterSpacing = 4.sp)
                            }
                            Box(
                                Modifier.background(Kk, RoundedCornerShape(10.dp)).clickable {
                                    val share = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT,
                                            "[시공막내 박람회] '${n.name}' 방 초대코드: $code\n앱에서 박람회 → '초대코드로 합류'에 입력하세요.")
                                    }
                                    ctx.startActivity(Intent.createChooser(share, "초대코드 공유").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                }.padding(horizontal = 14.dp, vertical = 8.dp)
                            ) { Text("공유", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = KkInk) }
                        }
                        Spacer(Modifier.height(12.dp))
                        Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFF0F1F3)))
                        Spacer(Modifier.height(12.dp))
                    }
                    Row(Modifier.fillMaxWidth().clickable { memOpen = !memOpen }, verticalAlignment = Alignment.CenterVertically) {
                        Text("팀원 ${d.members.size}명", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = T1)
                        Spacer(Modifier.weight(1f))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(if (memOpen) "접기" else "보기", fontSize = 12.sp, color = T3)
                            Icon(if (memOpen) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, null, tint = T3, modifier = Modifier.size(16.dp))
                        }
                    }
                    if (memOpen) {
                        Spacer(Modifier.height(4.dp))
                        d.members.forEach { m ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(m.name.ifBlank { "이름없음" }, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = T1)
                                if (m.role == "owner") {
                                    Spacer(Modifier.width(6.dp))
                                    Text("방장", fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFB58A00),
                                        modifier = Modifier.background(Color(0xFFFFF6D6), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp))
                                }
                                Spacer(Modifier.weight(1f))
                                Text(ph(m.phone), fontSize = 12.sp, color = T3)
                            }
                        }
                    }
                }
            }

            // ── ② 고객 계약서 (주 액션) ──
            item {
                val hasCatalog = d.catalog.isNotEmpty()
                val isTplRoom = d.info?.templateId?.isNotBlank() == true   // 줄눈 등 템플릿 방 = 카탈로그 없이도 계약 가능
                val canOpen = hasCatalog || isTplRoom
                Column(Modifier.fillMaxWidth().tossCardShadow(RoundedCornerShape(18.dp)).background(Panel, RoundedCornerShape(18.dp)).padding(16.dp)) {
                    Text("고객 계약서 받기", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = T1)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        when {
                            isTplRoom -> "QR을 고객 폰으로 찍게 하면, 사장님이 줄눈 항목을 체크하고 시공·예약금·잔금을 입력해요. 고객은 실시간으로 보며 서명해요."
                            canOpen -> "QR을 띄워 고객 폰으로 찍게 하면, 사장님이 항목을 고르고 고객은 실시간으로 보며 서명해요."
                            else -> "방장이 상품·서비스를 먼저 등록해야 계약서를 열 수 있어요."
                        },
                        fontSize = 12.5.sp, color = T2, lineHeight = 18.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    BigButton("계약서 열기 (QR)", enabled = canOpen && !opening, bg = if (canOpen) Kk else Field,
                        fg = if (canOpen) KkInk else T3) {
                        opening = true
                        scope.launch {
                            repo.createSession(n.roomId, myPhone)
                                .onSuccess { opening = false; onQr(it) }
                                .onFailure { opening = false; toast("계약서를 열지 못했어요 · 잠시 후 다시 시도해 주세요") }
                        }
                    }
                }
            }

            // ── ③ 팀 관리 (그룹 메뉴) ──
            item {
                Column {
                    Text("팀 관리", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = T2,
                        modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Panel)) {
                        GroupRow("우리 팀 접수서", "받은 계약 모아보기 · 시공자에게 나눠 배정", onSubs)
                        RowDivider()
                        GroupRow("내 접수서함", "나에게 배정된 시공만 모아보기", onMyInbox)
                        RowDivider()
                        GroupRow("박람회 달력", "시공 일정을 날짜별로 한눈에 📅", onCalendar)
                        if (d.myRole == "owner") {
                            RowDivider()
                            val ap = d.info?.apartment ?: ""
                            GroupRow("박람회 기본정보",
                                if (ap.isBlank()) "아파트명·타입·약관·업체정보 설정"
                                else "$ap · 타입 ${d.info?.unitTypes?.size ?: 0}개 · 수정", onEditInfo)
                            RowDivider()
                            if (d.info?.templateId.isNullOrBlank()) {   // 자유상품 방 → 상품·단가 미리 등록
                                val cnt = d.catalog.size
                                GroupRow("상품·서비스 준비", if (cnt > 0) "등록된 항목 ${cnt}개 · 수정" else "계약서에 쓸 상품·단가 등록", onProducts)
                            } else {                                    // 템플릿 방(줄눈) → 상품등록 불필요, 대신 '어떤 양식인지' 보여줌. 가격은 계약서에서.
                                GroupRow("계약서 양식", "${templateName(d.info?.templateId)} · 가격은 계약할 때 입력", onEditInfo)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 그룹 카드 안 컴팩트 메뉴 행 (아이콘 없이 제목+설명+›). */
@Composable
private fun GroupRow(title: String, desc: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = T1)
            Text(desc, fontSize = 11.5.sp, color = T3)
        }
        Icon(Icons.Default.KeyboardArrowRight, null, tint = T3, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun RowDivider() {
    Box(Modifier.fillMaxWidth().padding(start = 16.dp).height(1.dp).background(Color(0xFFF0F1F3)))
}

// ══════════════ 상품·서비스 준비 (방장) ══════════════
private data class Row3(val kind: String, val name: String, val priceText: String)

@Composable
private fun ColumnScope.ProductsEditorView(
    repo: ExpoRepository, n: Nav.Products, myPhone: String, toast: (String) -> Unit, onDone: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val rows = remember { mutableStateListOf<Row3>() }
    var loaded by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    LaunchedEffect(n.roomId) {
        repo.getProducts(n.roomId).onSuccess { cat ->
            rows.clear()
            cat.forEach { rows.add(Row3(it.kind, it.name, if (it.unitPrice > 0) it.unitPrice.toString() else "")) }
            if (rows.isEmpty()) rows.add(Row3("product", "", ""))
            loaded = true
        }.onFailure { rows.add(Row3("product", "", "")); loaded = true }
    }

    Column(Modifier.weight(1f).fillMaxWidth()) {
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text("고객 계약서에 뜨는 항목이에요. 상품엔 단가를, 서비스는 무료(빈칸)로 두세요.",
                    fontSize = 12.sp, color = T2, lineHeight = 17.sp, modifier = Modifier.padding(bottom = 4.dp))
            }
            items(rows.size) { i ->
                val row = rows[i]
                Column(Modifier.fillMaxWidth().tossCardShadow(RoundedCornerShape(14.dp)).background(Panel, RoundedCornerShape(14.dp)).padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        KindChip("상품", row.kind == "product") { rows[i] = row.copy(kind = "product") }
                        Spacer(Modifier.width(6.dp))
                        KindChip("서비스", row.kind == "service") { rows[i] = row.copy(kind = "service") }
                        Spacer(Modifier.weight(1f))
                        Box(Modifier.size(28.dp).clickable { if (rows.size > 1) rows.removeAt(i) }, contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Close, "삭제", tint = T3, modifier = Modifier.size(18.dp))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = row.name, onValueChange = { rows[i] = row.copy(name = it) },
                        placeholder = { Text(if (row.kind == "service") "예: 기존 줄눈 제거" else "예: 프리미엄 줄눈 시공", fontSize = 13.sp) },
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    if (row.kind == "product") {
                        Spacer(Modifier.height(6.dp))
                        OutlinedTextField(
                            value = row.priceText, onValueChange = { v -> rows[i] = row.copy(priceText = v.filter { it.isDigit() }) },
                            placeholder = { Text("단가 (원)", fontSize = 13.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            visualTransformation = com.detailline.callfollowcrm.presentation.component.ThousandsCommaTransformation,
                            singleLine = true, modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
            item {
                Box(
                    Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(14.dp))
                        .clickable { rows.add(Row3("product", "", "")) }.padding(vertical = 13.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Add, null, tint = AccentBlue, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("항목 추가", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AccentBlue)
                    }
                }
            }
        }
        Box(Modifier.background(ExpoBg)
            .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars)).padding(14.dp)) {
            BigButton(if (saving) "저장 중…" else "저장", enabled = loaded && !saving, bg = Kk, fg = KkInk) {
                val drafts = rows.mapNotNull { r ->
                    val nm = r.name.trim()
                    if (nm.isEmpty()) null
                    else ExpoRepository.ProductDraft(r.kind, nm, if (r.kind == "product") (r.priceText.toLongOrNull() ?: 0L) else 0L)
                }
                if (drafts.isEmpty()) { toast("항목을 하나 이상 적어주세요"); return@BigButton }
                saving = true
                scope.launch {
                    repo.setProducts(n.roomId, myPhone, drafts)
                        .onSuccess { saving = false; toast("저장했어요 (${it.size}개)"); onDone() }
                        .onFailure { saving = false; toast("저장하지 못했어요 · 다시 시도해 주세요") }
                }
            }
        }
    }
}

@Composable
private fun KindChip(label: String, on: Boolean, onClick: () -> Unit) {
    Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (on) Color.White else T2,
        modifier = Modifier.background(if (on) AccentBlue else Field, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp))
}

// ══════════════ 계약서 QR ══════════════
@Composable
private fun ColumnScope.QrView(repo: ExpoRepository, n: Nav.Qr, myPhone: String, toast: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val sid = n.session.sessionId
    val sec = n.session.secret
    val qr = remember(n.session.qrUrl) { QrGen.bitmap(n.session.qrUrl, 640) }

    var catalog by remember { mutableStateOf<List<ExpoRepository.Product>>(emptyList()) }
    val qty = remember { mutableStateMapOf<Long, Int>() }        // product_id -> 수량
    var discountText by remember { mutableStateOf("") }
    var depositOn by remember { mutableStateOf(false) }
    var depositText by remember { mutableStateOf("") }
    var noteText by remember { mutableStateOf("") }
    var live by remember { mutableStateOf<ExpoRepository.LiveState?>(null) }
    var shownFinal by remember { mutableStateOf(0L) }
    var finalized by remember { mutableStateOf<ExpoRepository.Finalized?>(null) }
    var finalizing by remember { mutableStateOf(false) }

    // ── 템플릿 방(줄눈 등) — 카탈로그 대신 체크리스트+가격 ──
    var tpl by remember { mutableStateOf<ExpoRepository.TemplateDef?>(null) }
    val matrixSel = remember { mutableStateMapOf<String, String>() }   // "$secKey$item" → 재질
    val checkSel = remember { mutableStateMapOf<String, Boolean>() }   // "$secKey$item" → 체크
    val priceSel = remember { mutableStateMapOf<String, String>() }    // "$grpKey$field" → 금액(숫자문자)
    var payerText by remember { mutableStateOf("") }
    val isTpl = tpl != null

    LaunchedEffect(n.roomId) { repo.getProducts(n.roomId).onSuccess { catalog = it } }
    // 방 template_id 를 미리 읽어 tpl 즉시 로드 — live 폴링(1.5초) 기다리는 동안 빈 카탈로그가 잠깐 보이던 것 방지.
    LaunchedEffect(n.roomId) {
        repo.roomDetail(n.roomId, myPhone).onSuccess { d ->
            val tid = d.info?.templateId ?: ""
            if (tid.isNotBlank() && tpl?.id != tid) repo.getTemplate(tid).onSuccess { tpl = it }
        }
    }
    // 방이 템플릿이면 정의 로드 (live GET 의 template_id 로도 판단 — 위 즉시 로드 실패 대비 백업)
    val liveTplId = live?.templateId ?: ""
    LaunchedEffect(liveTplId) {
        if (liveTplId.isNotBlank() && tpl?.id != liveTplId) repo.getTemplate(liveTplId).onSuccess { tpl = it }
    }

    // 디바운스 push (자유상품 방) — 선택/할인/계약금 바뀌면 400ms 후 서버로. 템플릿 방은 스킵.
    val pushKey = qty.entries.sortedBy { it.key }.joinToString(",") { "${it.key}:${it.value}" } +
        "|$discountText|$depositOn|$depositText|$noteText"
    LaunchedEffect(pushKey, isTpl) {
        if (isTpl) return@LaunchedEffect
        delay(400)
        val items = qty.filter { it.value > 0 }.map { it.key to it.value }
        repo.liveAgentPush(sid, sec, items, discountText.digitsToLong(), depositOn, depositText.digitsToLong(), noteText.trim())
            .onSuccess { shownFinal = it.finalAmount }
    }
    // 디바운스 push (템플릿 방) — 체크/재질/가격 바뀌면 400ms 후 서버로. 키 형식 = "섹션키::항목" / "그룹키::필드".
    val tplKey = matrixSel.entries.sortedBy { it.key }.joinToString(",") { "${it.key}=${it.value}" } + "|" +
        checkSel.filter { it.value }.keys.sorted().joinToString(",") + "|" +
        priceSel.entries.sortedBy { it.key }.joinToString(",") { "${it.key}=${it.value}" } + "|$payerText|$noteText"
    LaunchedEffect(tplKey, isTpl) {
        val t = tpl ?: return@LaunchedEffect
        delay(400)
        val matrix = HashMap<String, MutableMap<String, String>>()
        val checklist = HashMap<String, MutableSet<String>>()
        for (sc in t.sections) {
            if (sc.type == "matrix") for (it2 in sc.items) matrixSel["${sc.key}::$it2"]?.let { mat -> matrix.getOrPut(sc.key) { HashMap() }[it2] = mat }
            else for (it2 in sc.items) if (checkSel["${sc.key}::$it2"] == true) checklist.getOrPut(sc.key) { HashSet() }.add(it2)
        }
        val prices = HashMap<String, MutableMap<String, Long>>()
        var grand = 0L
        for (g in t.priceGroups) {
            for (f in g.fields) { val v = priceSel["${g.key}::$f"]?.digitsToLong() ?: 0L; if (v > 0) prices.getOrPut(g.key) { HashMap() }[f] = v }
            grand += priceSel["${g.key}::${g.fields.firstOrNull() ?: "total"}"]?.digitsToLong() ?: 0L
        }
        repo.liveAgentTemplate(sid, sec, matrix, checklist, prices, grand, payerText.trim(), noteText.trim())
            .onSuccess { shownFinal = it.finalAmount }
    }

    // 라이브 폴링 — 고객 정보·서명 도착 확인(1.5초).
    LaunchedEffect(sid) {
        while (finalized == null) {
            repo.liveGet(sid, sec).onSuccess { live = it; shownFinal = it.finalAmount }
            delay(1500)
        }
    }

    val done = finalized
    if (done != null) {
        Column(Modifier.weight(1f).fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(36.dp))
            Text("🎉", fontSize = 52.sp)
            Spacer(Modifier.height(12.dp))
            Text("계약이 정상적으로 체결되었어요!", fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = T1, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text("${won(done.finalAmount)} · 계약서가 보관됐어요", fontSize = 13.sp, color = T2)
            Spacer(Modifier.height(24.dp))
            BigButton("계약서 보기 · 공유 (PDF)", enabled = true, bg = Kk, fg = KkInk) {
                shareUrl(ctx, repo.receiptUrl(done.contractId))
            }
        }
    } else {
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth().windowInsetsPadding(WindowInsets.ime),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // QR + 고객 연결 상태
            item {
                Column(Modifier.fillMaxWidth().tossCardShadow(RoundedCornerShape(18.dp)).background(Panel, RoundedCornerShape(18.dp)).padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("고객이 이 QR을 찍으면 같은 화면을 봐요", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = T1)
                    Spacer(Modifier.height(10.dp))
                    if (qr != null) Image(qr.asImageBitmap(), "계약서 QR", Modifier.size(170.dp))
                    else Box(Modifier.size(170.dp), Alignment.Center) { Text("QR 생성 실패", color = T3) }
                    Spacer(Modifier.height(10.dp))
                    val l = live
                    val connected = l != null && (l.customerName.isNotBlank() || l.signaturePresent || l.customerPhone.isNotBlank())
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).background(if (connected) Color(0xFF13C47B) else T3, RoundedCornerShape(4.dp)))
                        Spacer(Modifier.width(6.dp))
                        Text(if (connected) "고객 연결됨" else "고객 연결 대기중…",
                            fontSize = 12.sp, color = T2, fontWeight = FontWeight.Medium)
                    }
                    if (connected && l != null) {
                        Spacer(Modifier.height(10.dp))
                        val site = listOf(l.apartment, l.dongHo).filter { it.isNotBlank() }.joinToString(" ").ifBlank { l.address }
                        Column(Modifier.fillMaxWidth().background(Field, RoundedCornerShape(12.dp)).padding(12.dp)) {
                            ChkRow("성함", l.customerName)
                            ChkRow("연락처", ph(l.customerPhone))
                            ChkRow("시공주소", site)
                            ChkRow("서명", if (l.signaturePresent) "완료" else "")
                        }
                    }
                }
            }
            if (!isTpl) {
            item { Text("상품·서비스 (탭해서 선택)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = T2) }
            items(catalog) { p ->
                val q = qty[p.productId] ?: 0
                val sel = q > 0
                Column(
                    Modifier.fillMaxWidth()
                        .background(if (sel) Color(0xFFFFFBEA) else Panel, RoundedCornerShape(14.dp))
                        .clickable { if (sel) qty.remove(p.productId) else qty[p.productId] = 1 }
                        .padding(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(22.dp).background(if (sel) Kk else Field, RoundedCornerShape(6.dp)),
                            contentAlignment = Alignment.Center) {
                            if (sel) Text("✓", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = KkInk)
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(p.name.ifBlank { "(이름없음)" }, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = T1)
                            Text(if (p.kind == "service") "서비스" else won(p.unitPrice), fontSize = 12.sp, color = T3)
                        }
                        if (sel && p.kind == "product") {
                            Stepper(q, { qty[p.productId] = (q - 1).coerceAtLeast(1) }, { qty[p.productId] = q + 1 })
                        }
                    }
                }
            }
            // 할인 · 계약금 · 최종금액
            item {
                Column(Modifier.fillMaxWidth().tossCardShadow(RoundedCornerShape(16.dp)).background(Panel, RoundedCornerShape(16.dp)).padding(16.dp)) {
                    MoneyField("총액 할인", discountText) { discountText = it }
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("계약금 받기", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = T1, modifier = Modifier.weight(1f))
                        KindChip("끔", !depositOn) { depositOn = false }
                        Spacer(Modifier.width(6.dp))
                        KindChip("켬", depositOn) { depositOn = true }
                    }
                    if (depositOn) { Spacer(Modifier.height(10.dp)); MoneyField("계약금", depositText) { depositText = it } }
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = noteText, onValueChange = { noteText = it },
                        label = { Text("특이사항 · 비고 (선택)", fontSize = 12.sp) },
                        placeholder = { Text("예: 현관 좁아 자재 반입 주의", fontSize = 13.sp) },
                        minLines = 2, modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("최종 금액", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = T1, modifier = Modifier.weight(1f))
                        Text(won(shownFinal), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = AccentBlue)
                    }
                }
            }
            } else {
                // ── 템플릿 방(줄눈 등): 체크리스트 + 가격 ──
                val t = tpl!!
                t.sections.forEach { sc ->
                    item {
                        Text(sc.title, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = T2, modifier = Modifier.padding(top = 4.dp))
                    }
                    if (sc.type == "matrix") {
                        items(sc.items) { it2 ->
                            val curMat = matrixSel["${sc.key}::$it2"]
                            Column(Modifier.fillMaxWidth().background(if (curMat != null) Color(0xFFFFFBEA) else Panel, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 9.dp)) {
                                Text(it2, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = if (curMat != null) T1 else T2)
                                Spacer(Modifier.height(7.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    sc.materials.forEach { mat ->
                                        val on = curMat == mat
                                        Box(Modifier.background(if (on) Kk else Field, RoundedCornerShape(8.dp))
                                            .clickable { if (on) matrixSel.remove("${sc.key}::$it2") else matrixSel["${sc.key}::$it2"] = mat }
                                            .padding(horizontal = 12.dp, vertical = 6.dp)) {
                                            Text((if (on) "✓ " else "") + mat, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (on) KkInk else T2)
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        item {
                            Column {
                                sc.items.chunked(3).forEach { rowItems ->
                                    Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        rowItems.forEach { it2 ->
                                            val on = checkSel["${sc.key}::$it2"] == true
                                            Box(Modifier.weight(1f).background(if (on) Color(0xFFFFF6D6) else Field, RoundedCornerShape(8.dp))
                                                .clickable { checkSel["${sc.key}::$it2"] = !on }.padding(vertical = 9.dp), contentAlignment = Alignment.Center) {
                                                Text((if (on) "✓ " else "") + it2, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = if (on) T1 else T3, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            }
                                        }
                                        repeat(3 - rowItems.size) { Spacer(Modifier.weight(1f)) }
                                    }
                                }
                            }
                        }
                    }
                }
                item {
                    Column(Modifier.fillMaxWidth().tossCardShadow(RoundedCornerShape(16.dp)).background(Panel, RoundedCornerShape(16.dp)).padding(16.dp)) {
                        t.priceGroups.forEach { g ->
                            Text(g.title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = T1)
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                g.fields.forEach { f ->
                                    Box(Modifier.weight(1f)) {
                                        MoneyField(priceFieldLabel(f), priceSel["${g.key}::$f"] ?: "") { priceSel["${g.key}::$f"] = it }
                                    }
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                        }
                        OutlinedTextField(value = payerText, onValueChange = { payerText = it },
                            label = { Text("입금자명", fontSize = 12.sp) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(value = noteText, onValueChange = { noteText = it },
                            label = { Text("특이사항 · 비고 (선택)", fontSize = 12.sp) }, minLines = 2, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(14.dp))
                        // 총금액 = 각 그룹 시공금액 합(로컬 계산). 서버 live GET final_amount 는 템플릿에서 0 이라 안 씀.
                        val tplGrand = t.priceGroups.sumOf { g -> priceSel["${g.key}::${g.fields.firstOrNull() ?: "total"}"]?.digitsToLong() ?: 0L }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("총 금액", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = T1, modifier = Modifier.weight(1f))
                            Text(won(tplGrand), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = AccentBlue)
                        }
                    }
                }
            }
            item {
                // 고객이 서명·완료를 누르면 서버가 customer_confirmed=true → 배너 표시. 상담사가 수정하면 서버가 풀음.
                if (live?.customerConfirmed == true) {
                    Box(Modifier.fillMaxWidth().background(Color(0xFFE9FBF2), RoundedCornerShape(12.dp)).padding(14.dp)) {
                        Text("✅ 고객이 계약서 작성을 완료했어요 · 수정사항 없으신가요?",
                            fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0E9B63))
                    }
                    Spacer(Modifier.height(10.dp))
                }
                val hasSel = if (isTpl) (matrixSel.isNotEmpty() || checkSel.any { it.value }) else qty.values.any { it > 0 }
                BigButton(
                    if (finalizing) "보관 중…" else "계약서 보관하기",
                    enabled = hasSel && !finalizing, bg = Kk, fg = KkInk
                ) {
                    finalizing = true
                    scope.launch {
                        repo.finalize(sid, sec)
                            .onSuccess { finalized = it }
                            .onFailure { finalizing = false; toast(finalizeErrorText(it)) }
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

/** 템플릿 가격 필드 라벨 (total=시공금액·deposit=예약금·balance=잔금). */
private fun priceFieldLabel(f: String): String = when (f) {
    "total" -> "시공금액"; "deposit" -> "예약금"; "balance" -> "잔금"; else -> f
}

@Composable
private fun Stepper(q: Int, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        StepBtn(Icons.Default.Remove, onMinus)
        Text("$q", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = T1, modifier = Modifier.padding(horizontal = 12.dp))
        StepBtn(Icons.Default.Add, onPlus)
    }
}

@Composable
private fun StepBtn(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Box(Modifier.size(28.dp).background(Field, RoundedCornerShape(8.dp)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center) { Icon(icon, null, tint = T1, modifier = Modifier.size(18.dp)) }
}

@Composable
private fun MoneyField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = { onChange(it.filter { c -> c.isDigit() }) },
        label = { Text(label, fontSize = 12.sp) },
        placeholder = { Text("0", fontSize = 13.sp) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        visualTransformation = com.detailline.callfollowcrm.presentation.component.ThousandsCommaTransformation,
        singleLine = true, modifier = Modifier.fillMaxWidth()
    )
}

private fun String.digitsToLong(): Long = filter { it.isDigit() }.toLongOrNull() ?: 0L

private fun shareUrl(ctx: android.content.Context, url: String) {
    if (url.isBlank()) return
    runCatching {
        ctx.startActivity(
            Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"; putExtra(Intent.EXTRA_TEXT, url)
            }, "계약서 공유").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

// ══════════════ 우리 팀 접수서 ══════════════
@Composable
private fun ColumnScope.SubmissionsView(repo: ExpoRepository, n: Nav.Subs, myPhone: String, toast: (String) -> Unit, onContract: (Long) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var data by remember { mutableStateOf(ExpoCache.submissions[n.roomId]) }
    var members by remember { mutableStateOf<List<ExpoRepository.Member>>(emptyList()) }
    var reloadTick by remember { mutableStateOf(0) }
    var assignTarget by remember { mutableStateOf<ExpoRepository.Submission?>(null) }   // 시공자 배정 대상
    var myRole by remember { mutableStateOf("member") }
    var pickCrew by remember { mutableStateOf(false) }                 // 시공자 선택 시트 (방장만)
    val crewSel = remember { mutableStateListOf<String>() }            // 선택된 시공자 phone
    var crewInit by remember { mutableStateOf(false) }
    var animPlan by remember { mutableStateOf<List<Pair<ExpoRepository.Submission, ExpoRepository.Member>>?>(null) }
    var confirmReset by remember { mutableStateOf(false) }                 // 배정 초기화 확인

    // 금액 비슷하게 + 랜덤 타이브레이크 (누를 때마다 조합이 달라짐) → 다시 돌리기용
    fun makePlan(subs: List<ExpoRepository.Submission>, targets: List<ExpoRepository.Member>): List<Pair<ExpoRepository.Submission, ExpoRepository.Member>> {
        val load = HashMap<String, Long>().apply { targets.forEach { put(it.phone, 0L) } }
        val plan = ArrayList<Pair<ExpoRepository.Submission, ExpoRepository.Member>>()
        subs.sortedByDescending { it.finalAmount }.forEach { sub ->
            val t = targets.shuffled().minByOrNull { load[it.phone] ?: 0L } ?: return@forEach
            plan.add(sub to t); load[t.phone] = (load[t.phone] ?: 0L) + sub.finalAmount
        }
        return plan
    }

    LaunchedEffect(n.roomId, reloadTick) {
        repo.submissions(n.roomId, myPhone).onSuccess { data = it; ExpoCache.submissions[n.roomId] = it }
            .onFailure { if (data == null) data = ExpoRepository.Submissions(0, 0L, emptyList()) }
    }
    LaunchedEffect(n.roomId) { repo.roomDetail(n.roomId, myPhone).onSuccess { members = it.members; myRole = it.myRole } }

    var expanded by remember { mutableStateOf<Long?>(null) }
    val d = data
    if (d == null) {
        Column(Modifier.weight(1f).fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Skel(Modifier.fillMaxWidth(0.5f).height(18.dp))
            Spacer(Modifier.height(2.dp))
            repeat(3) { Skel(Modifier.fillMaxWidth().height(78.dp), 14) }
        }
    } else {
        val unassigned = d.items.filter { it.assignedPhone.isBlank() }
        val assignedCnt = d.count - unassigned.size
        Column(Modifier.weight(1f).fillMaxWidth()) {
            // 요약 카드 + 나눠 배정 (프로토 ①)
            Column(Modifier.fillMaxWidth().padding(14.dp)) {
                Column(Modifier.fillMaxWidth().tossCardShadow(RoundedCornerShape(16.dp)).background(Panel, RoundedCornerShape(16.dp)).padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("이번 박람회 접수", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = T3)
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("총 ${d.count}건 · ", fontSize = 21.sp, fontWeight = FontWeight.ExtraBold, color = T1)
                        Text(won(d.totalAmount), fontSize = 21.sp, fontWeight = FontWeight.ExtraBold, color = AccentBlue)
                    }
                    Spacer(Modifier.height(6.dp))
                    Row {
                        if (unassigned.isNotEmpty()) {
                            Text("미배정 ${unassigned.size}건", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE1483B))
                            Text("  ·  ", fontSize = 12.sp, color = T3)
                        }
                        Text("배정완료 ${assignedCnt}건", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = T3)
                    }
                    if (myRole == "owner" && unassigned.isNotEmpty() && members.size >= 2) {
                        Spacer(Modifier.height(13.dp))
                        Box(
                            Modifier.fillMaxWidth().background(AccentBlue, RoundedCornerShape(13.dp)).clickable {
                                if (!crewInit) { crewSel.clear(); crewSel.addAll(members.filter { it.role != "owner" }.map { it.phone }); crewInit = true }
                                pickCrew = true
                            }.padding(vertical = 13.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("미배정 ${unassigned.size}건, 시공자에게 나눠 배정", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                                Text("랜덤 · 금액 비슷하게 자동 분배", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Color(0xCCFFFFFF))
                            }
                        }
                    }
                    // 배정된 게 있으면 → 다시 돌리기(재분배) / 초기화(미배정으로 리셋) (방장만)
                    if (myRole == "owner" && assignedCnt > 0) {
                        Spacer(Modifier.height(if (unassigned.isNotEmpty()) 8.dp else 13.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(
                                Modifier.weight(1f).background(Color(0xFFEFF3F8), RoundedCornerShape(11.dp)).clickable {
                                    val targets = if (crewSel.isNotEmpty()) members.filter { crewSel.contains(it.phone) }
                                        else members.filter { m -> d.items.any { it.assignedPhone.filter { c -> c.isDigit() } == m.phone.filter { c -> c.isDigit() } } }
                                    if (targets.size >= 2) {
                                        val plan = makePlan(d.items, targets)
                                        animPlan = plan
                                        scope.launch { plan.forEach { (sub, mem) -> repo.assign(sub.contractId, myPhone, mem.phone) } }
                                    } else { crewInit = false; pickCrew = true }
                                }.padding(vertical = 11.dp),
                                contentAlignment = Alignment.Center
                            ) { Text("🎲 다시 돌리기", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = T1) }
                            Box(
                                Modifier.weight(1f).background(Color(0xFFFFF0F0), RoundedCornerShape(11.dp))
                                    .clickable { confirmReset = true }.padding(vertical = 11.dp),
                                contentAlignment = Alignment.Center
                            ) { Text("↺ 초기화", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE1483B)) }
                        }
                    }
                }
            }
            if (d.items.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), Alignment.Center) {
                    Text("아직 접수된 계약서가 없어요", fontSize = 14.sp, color = T3, fontWeight = FontWeight.Medium)
                }
            } else LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 12.dp, end = 12.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(d.items) { s ->
                    val open = expanded == s.contractId
                    val site = listOf(s.apartment, s.dongHo).filter { it.isNotBlank() }.joinToString(" ")
                    Column(
                        Modifier.fillMaxWidth().tossCardShadow(RoundedCornerShape(14.dp)).background(Panel, RoundedCornerShape(14.dp))
                            .clickable { expanded = if (open) null else s.contractId }.padding(14.dp)
                    ) {
                        // 목록 = 이름 + 일정배지 + 계약자·시공자 + 금액 (상세는 클릭해서 펼침)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(s.customerName.ifBlank { "고객" }, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = T1)
                                    Spacer(Modifier.width(6.dp))
                                    val scheduled = s.scheduledAtMs > 0L
                                    Text(if (scheduled) "일정 ${dateShort(s.scheduledAtMs)}" else "일정 미정",
                                        fontSize = 9.5.sp, fontWeight = FontWeight.ExtraBold,
                                        color = if (scheduled) Color(0xFF0E9B63) else Color(0xFFB58A00),
                                        modifier = Modifier.background(if (scheduled) Color(0xFFE9FBF2) else Color(0xFFFFF6D6), RoundedCornerShape(6.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp))
                                }
                                Text("계약자 ${s.agentName.ifBlank { "-" }}" + (s.assignedName?.let { " · 시공자 $it" } ?: " · 시공자 미배정"),
                                    fontSize = 11.5.sp, color = T3)
                            }
                            Text(won(s.finalAmount), fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = AccentBlue)
                            Spacer(Modifier.width(6.dp))
                            Icon(if (open) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, null, tint = T3, modifier = Modifier.size(18.dp))
                        }
                        if (open) {
                            Spacer(Modifier.height(12.dp))
                            Box(Modifier.fillMaxWidth().height(1.dp).background(ExpoBg))
                            Spacer(Modifier.height(12.dp))
                            SubRow("현장", site.ifBlank { s.address.ifBlank { "-" } })   // 아파트명 동호수
                            SubRow("시공 상품", s.products.ifBlank { "-" })
                            if (s.note.isNotBlank()) SubRow("비고", s.note)
                            SubRow("시공일", if (s.scheduledAtMs > 0L) dateKo(s.scheduledAtMs) else "미정")
                            SubRow("계약자", s.agentName.ifBlank { "-" })
                            SubRow("시공자", s.assignedName ?: "미배정")
                            SubRow("접수", "${dateShort(s.createdAtMs)} ${hhmm(s.createdAtMs)}")
                            // 전화 (탭 → 통화)
                            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("전화", fontSize = 12.sp, color = T3, modifier = Modifier.width(66.dp))
                                val fp = s.customerPhone
                                Text("📞 ${ph(fp).ifBlank { ph(s.customerPhoneMasked).ifBlank { "-" } }}", fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold, color = if (fp.isNotBlank()) AccentBlue else T1,
                                    modifier = Modifier.clickable(enabled = fp.isNotBlank()) { dialPhone(ctx, fp) })
                            }
                            Spacer(Modifier.height(12.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(
                                    Modifier.weight(1f).background(Color(0xFFEDF2FF), RoundedCornerShape(12.dp))
                                        .clickable {
                                            pickDate(ctx, s.scheduledAtMs) { picked ->
                                                scope.launch {
                                                    repo.schedule(s.contractId, myPhone, picked)
                                                        .onSuccess { toast("시공일 저장됐어요"); reloadTick++ }
                                                        .onFailure { toast("시공일을 저장하지 못했어요 · 다시 시도해 주세요") }
                                                }
                                            }
                                        }.padding(vertical = 11.dp),
                                    contentAlignment = Alignment.Center
                                ) { Text(if (s.scheduledAtMs > 0L) "시공일 변경" else "시공일 잡기", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = AccentBlue) }
                                Box(
                                    Modifier.weight(1f).background(Color(0xFFEFEAFE), RoundedCornerShape(12.dp))
                                        .clickable { assignTarget = s }.padding(vertical = 11.dp),
                                    contentAlignment = Alignment.Center
                                ) { Text("시공자 배정", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF7C5CFC)) }
                            }
                            Spacer(Modifier.height(8.dp))
                            Box(
                                Modifier.fillMaxWidth().background(Field, RoundedCornerShape(12.dp))
                                    .clickable { onContract(s.contractId) }.padding(vertical = 11.dp),
                                contentAlignment = Alignment.Center
                            ) { Text("계약서 보기", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = T1) }
                        }
                    }
                }
            }
        }

        // 시공자 배정(분배) — 팀원 중 시공자 고르기. 계약자≠시공자.
        val at = assignTarget
        if (at != null) {
            AlertDialog(
                onDismissRequest = { assignTarget = null },
                title = { Text("시공자 배정", fontWeight = FontWeight.ExtraBold, color = T1) },
                text = {
                    Column {
                        com.detailline.callfollowcrm.presentation.util.ForceDialogResize()
                        val site2 = listOf(at.apartment, at.dongHo).filter { it.isNotBlank() }.joinToString(" ")
                        Text("${at.customerName.ifBlank { "고객" }}${if (site2.isNotBlank()) " ($site2)" else ""} 을(를) 시공할 팀원을 고르세요.",
                            fontSize = 12.5.sp, color = T2)
                        Spacer(Modifier.height(10.dp))
                        if (members.isEmpty()) Text("팀원 정보를 불러오는 중…", fontSize = 12.sp, color = T3)
                        members.forEach { m ->
                            Row(Modifier.fillMaxWidth().clickable {
                                scope.launch {
                                    repo.assign(at.contractId, myPhone, m.phone)
                                        .onSuccess { toast("${m.name.ifBlank { "팀원" }} 배정됨"); assignTarget = null; reloadTick++ }
                                        .onFailure { toast("배정하지 못했어요 · 다시 시도해 주세요") }
                                }
                            }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(m.name.ifBlank { "이름없음" }, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = T1)
                                if (m.role == "owner") {
                                    Spacer(Modifier.width(6.dp))
                                    Text("방장", fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFB58A00),
                                        modifier = Modifier.background(Color(0xFFFFF6D6), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp))
                                }
                                Spacer(Modifier.weight(1f))
                                if (at.assignedName != null && at.assignedName == m.name)
                                    Text("현재 ✓", fontSize = 11.sp, color = AccentBlue, fontWeight = FontWeight.Bold)
                            }
                        }
                        if (at.assignedName != null) {
                            Box(Modifier.fillMaxWidth().clickable {
                                scope.launch {
                                    repo.assign(at.contractId, myPhone, "")
                                        .onSuccess { toast("배정 해제됨"); assignTarget = null; reloadTick++ }
                                        .onFailure { toast("배정을 해제하지 못했어요 · 다시 시도해 주세요") }
                                }
                            }.padding(vertical = 10.dp)) { Text("배정 해제", fontSize = 13.sp, color = T3) }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { assignTarget = null }) { Text("닫기", color = AccentBlue, fontWeight = FontWeight.Bold) } },
                containerColor = Color.White,
                tonalElevation = 0.dp   // M3 tonalElevation 회색 톤 제거 → 진짜 흰색. (2026-08-12)
            )
        }

        // ── 시공자 선택 (방장) → 선택한 사람들에게 미배정 나눠 배정 (프로토 ①) ──
        if (pickCrew) {
            AlertDialog(
                onDismissRequest = { pickCrew = false },
                title = { Text("나눠 가질 시공자 선택", fontWeight = FontWeight.ExtraBold, color = T1) },
                text = {
                    Column {
                        com.detailline.callfollowcrm.presentation.util.ForceDialogResize()
                        Text("접수서를 나눠 가질 시공자만 체크하세요. 상담만 하는 분은 빼고요.", fontSize = 12.5.sp, color = T2, lineHeight = 18.sp)
                        Spacer(Modifier.height(12.dp))
                        members.forEach { m ->
                            val on = crewSel.contains(m.phone)
                            Row(Modifier.fillMaxWidth().clickable { if (on) crewSel.remove(m.phone) else crewSel.add(m.phone) }.padding(vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(22.dp).background(if (on) AccentBlue else Field, RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) {
                                    if (on) Text("✓", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                                }
                                Spacer(Modifier.width(10.dp))
                                Text(m.name.ifBlank { "이름없음" }, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = T1)
                                if (m.role == "owner") {
                                    Spacer(Modifier.width(6.dp))
                                    Text("방장", fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFB58A00),
                                        modifier = Modifier.background(Color(0xFFFFF6D6), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp))
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(enabled = crewSel.isNotEmpty(), onClick = {
                        val targets = members.filter { crewSel.contains(it.phone) }
                        if (unassigned.isNotEmpty() && targets.isNotEmpty()) {
                            val plan = makePlan(unassigned, targets)
                            pickCrew = false
                            animPlan = plan
                            scope.launch { plan.forEach { (sub, m) -> repo.assign(sub.contractId, myPhone, m.phone) } }
                        } else pickCrew = false
                    }) { Text("${crewSel.size}명에게 나눠 배정", color = AccentBlue, fontWeight = FontWeight.ExtraBold) }
                },
                dismissButton = { TextButton(onClick = { pickCrew = false }) { Text("취소", color = T3) } },
                containerColor = Color.White,
                tonalElevation = 0.dp   // M3 tonalElevation 회색 톤 제거 → 진짜 흰색. (2026-08-12)
            )
        }
        // ── 배정 초기화 확인 (방장) ──
        if (confirmReset) {
            AlertDialog(
                onDismissRequest = { confirmReset = false },
                title = { Text("배정 초기화", fontWeight = FontWeight.ExtraBold, color = T1) },
                text = {
                    Column {
                        com.detailline.callfollowcrm.presentation.util.ForceDialogResize()
                        Text("모든 시공자 배정을 풀고 미배정 상태로 되돌릴까요?\n그 다음 시공자를 다시 골라 나눠 배정할 수 있어요.", fontSize = 13.sp, color = T2, lineHeight = 19.sp)
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        confirmReset = false
                        scope.launch {
                            d.items.forEach { repo.assign(it.contractId, myPhone, "") }
                            reloadTick++; toast("배정을 초기화했어요")
                        }
                    }) { Text("초기화", color = Color(0xFFE1483B), fontWeight = FontWeight.ExtraBold) }
                },
                dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("취소", color = T3) } },
                containerColor = Color.White,
                tonalElevation = 0.dp   // M3 tonalElevation 회색 톤 제거 → 진짜 흰색. (2026-08-12)
            )
        }

        // ── 배분 애니메이션 (종이 한 장씩) (프로토 ②) ──
        animPlan?.let { plan ->
            val crew = members.filter { m -> plan.any { it.second.phone == m.phone } }
            AssignAnimOverlay(plan, crew) { animPlan = null; reloadTick++; toast("${plan.size}건 배정 완료 📩") }
        }
    }
}

/** 배분 애니메이션 — 종이 접수서가 위 뭉치에서 한 장씩 날아가 시공자 트레이에 꽂힘.
 *  많으면(>16) 12장부터는 촤르륵 빠르게. (프로토 ②) */
@Composable
private fun AssignAnimOverlay(
    plan: List<Pair<ExpoRepository.Submission, ExpoRepository.Member>>,
    crew: List<ExpoRepository.Member>,
    onDone: () -> Unit
) {
    val idxOf = remember(crew) { crew.withIndex().associate { (i, m) -> m.phone to i } }
    val counts = remember { mutableStateListOf<Int>().apply { repeat(crew.size) { add(0) } } }
    var step by remember { mutableStateOf(0) }
    val fly = remember { Animatable(0f) }
    val total = plan.size
    val big = total > 16

    androidx.compose.ui.window.Dialog(
        onDismissRequest = { },
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFAFFFFFF))) {
            val W = maxWidth; val H = maxHeight
            val n = crew.size.coerceAtLeast(1)
            val done = step >= total
            val burst = big && step >= 12
            val remain = (total - step).coerceAtLeast(0)
            val stackTop = 150.dp
            val trayY = H - 156.dp

            // 제목 + 남은 장수
            Column(Modifier.align(Alignment.TopCenter).padding(top = 58.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (done) "✓ 배정 완료 · ${total}건" else "접수서 나눠 배정",
                    fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = if (done) Color(0xFF0CA678) else T1)
                Spacer(Modifier.height(6.dp))
                Text(if (done) "${n}명에게 나눠드렸어요" else "$remain 장 남음 · 한 장씩",
                    fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (done) AccentBlue else T3)
            }

            // 위 종이 뭉치 (남을수록 두껍게)
            if (!done) {
                Box(Modifier.align(Alignment.TopCenter).offset(y = stackTop).size(width = 96.dp, height = 96.dp), contentAlignment = Alignment.TopCenter) {
                    val depth = remain.coerceIn(1, 4)
                    for (i in depth downTo 1) {
                        Box(Modifier.offset(x = (i * 3 - 6).dp, y = (i * 3).dp).size(width = 72.dp, height = 90.dp)
                            .background(Color.White, RoundedCornerShape(8.dp))
                            .border(1.dp, Color(0xFFE3E8EF), RoundedCornerShape(8.dp)))
                    }
                }
            }

            // 날아가는 종이 한 장 (버스트 중엔 안 그림 — 촤르륵)
            if (!done && !burst && step < total) {
                val (sub, m) = plan[step]
                val ti = idxOf[m.phone] ?: 0
                val t = fly.value
                val startX = W * 0.5f
                val endX = W * ((ti + 0.5f) / n)
                val curX = startX + (endX - startX) * t
                val curY = stackTop + (trayY - stackTop) * t
                val arcDp = (sin(t * Math.PI).toFloat() * 74f).dp
                PaperSheet(
                    name = sub.customerName.ifBlank { "고객" },
                    amount = won(sub.finalAmount),
                    modifier = Modifier.align(Alignment.TopStart)
                        .offset(x = curX - 58.dp, y = curY - arcDp)
                        .rotate(-7f + 12f * t).scale(1f - 0.34f * t).alpha(1f - 0.30f * t)
                )
            }

            // 하단 시공자 트레이 (종이 쌓임 + 건수)
            Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = 40.dp, start = 4.dp, end = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
                crew.forEachIndexed { i, m ->
                    val c = counts.getOrElse(i) { 0 }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        // 쌓인 종이 더미
                        Box(Modifier.height(60.dp).width(72.dp), contentAlignment = Alignment.BottomCenter) {
                            if (c == 0) Box(Modifier.size(width = 56.dp, height = 12.dp).background(Color(0xFFEEF1F4), RoundedCornerShape(6.dp)))
                            for (k in 0 until c.coerceAtMost(6)) {
                                Box(Modifier.offset(y = -(k * 7).dp).size(width = 52.dp, height = 30.dp)
                                    .background(Color.White, RoundedCornerShape(5.dp))
                                    .border(1.dp, Color(0xFFDBE1E8), RoundedCornerShape(5.dp)))
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Box(Modifier.size(50.dp).background(Kk, RoundedCornerShape(25.dp)), contentAlignment = Alignment.Center) {
                            Text(m.name.take(1).ifBlank { "?" }, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = KkInk)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(m.name.ifBlank { "팀원" }, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = T1, maxLines = 1)
                        Text("${c}건", fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF0E9B63))
                    }
                }
            }
        }
    }

    LaunchedEffect(step) {
        if (step >= total) { delay(650); onDone(); return@LaunchedEffect }
        if (big && step >= 12) {                                   // 촤르륵 — 카운트만 빠르게
            val ti = idxOf[plan[step].second.phone] ?: 0
            if (ti < counts.size) counts[ti] = counts[ti] + 1
            delay(45); step++
        } else {
            val dur = (520 - step * 34).coerceAtLeast(150)          // 뒤로 갈수록 빨라짐
            val gap = (150 - step * 16).coerceAtLeast(40)
            fly.snapTo(0f)
            fly.animateTo(1f, tween(dur, easing = FastOutSlowInEasing))
            val ti = idxOf[plan[step].second.phone] ?: 0
            if (ti < counts.size) counts[ti] = counts[ti] + 1
            delay(gap.toLong()); step++
        }
    }
}

/** 접수서 한 장 (종이 느낌 — 이름·금액·본문 줄). */
@Composable
private fun PaperSheet(name: String, amount: String, modifier: Modifier = Modifier) {
    Column(
        modifier.width(116.dp).background(Color.White, RoundedCornerShape(10.dp))
            .border(1.5.dp, Color(0xFFDBE1E8), RoundedCornerShape(10.dp)).padding(11.dp)
    ) {
        Text(name, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = T1, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(7.dp))
        Box(Modifier.fillMaxWidth(0.82f).height(4.dp).background(Color(0xFFEDF0F4), RoundedCornerShape(2.dp)))
        Spacer(Modifier.height(4.dp))
        Box(Modifier.fillMaxWidth(0.58f).height(4.dp).background(Color(0xFFEDF0F4), RoundedCornerShape(2.dp)))
        Spacer(Modifier.height(9.dp))
        Text(amount, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = AccentBlue)
    }
}

@Composable
private fun SubRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, fontSize = 12.sp, color = T3, modifier = Modifier.width(66.dp))
        Text(value, fontSize = 12.5.sp, color = T1, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
    }
}

/** 내 접수서함 — 나에게 배정된 시공만 (프로토 ③). */
@Composable
private fun ColumnScope.MyInboxView(repo: ExpoRepository, n: Nav.MyInbox, myPhone: String, toast: (String) -> Unit, onContract: (Long) -> Unit) {
    val ctx = LocalContext.current
    var data by remember { mutableStateOf(ExpoCache.submissions[n.roomId]) }
    LaunchedEffect(n.roomId) {
        repo.submissions(n.roomId, myPhone).onSuccess { data = it; ExpoCache.submissions[n.roomId] = it }
            .onFailure { if (data == null) data = ExpoRepository.Submissions(0, 0L, emptyList()) }
    }
    val myDigits = myPhone.filter { it.isDigit() }
    val d = data
    if (d == null) {
        Column(Modifier.weight(1f).fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Skel(Modifier.fillMaxWidth(0.5f).height(18.dp)); repeat(3) { Skel(Modifier.fillMaxWidth().height(78.dp), 14) }
        }
    } else {
        val mine = d.items.filter { it.assignedPhone.filter { c -> c.isDigit() } == myDigits }
        val total = mine.sumOf { it.finalAmount }
        val scheduled = mine.count { it.scheduledAtMs > 0L }
        Column(Modifier.weight(1f).fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(14.dp)) {
                Column(Modifier.fillMaxWidth().tossCardShadow(RoundedCornerShape(16.dp)).background(Panel, RoundedCornerShape(16.dp)).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("나에게 배정된 시공", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = T3)
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("${mine.size}건 · ", fontSize = 21.sp, fontWeight = FontWeight.ExtraBold, color = T1)
                        Text(won(total), fontSize = 21.sp, fontWeight = FontWeight.ExtraBold, color = AccentBlue)
                    }
                    if (mine.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text("일정 잡힘 ${scheduled}건  ·  일정 미정 ${mine.size - scheduled}건", fontSize = 12.sp, color = T3, fontWeight = FontWeight.Medium)
                    }
                }
            }
            if (mine.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), Alignment.Center) {
                    Text("아직 배정받은 시공이 없어요", fontSize = 14.sp, color = T3, fontWeight = FontWeight.Medium)
                }
            } else LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 12.dp, end = 12.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(mine) { s ->
                    val site = listOf(s.apartment, s.dongHo).filter { it.isNotBlank() }.joinToString(" ")
                    Column(Modifier.fillMaxWidth().tossCardShadow(RoundedCornerShape(14.dp)).background(Panel, RoundedCornerShape(14.dp)).padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val sch = s.scheduledAtMs > 0L
                            Text(if (sch) "${dateShort(s.scheduledAtMs)} 시공" else "일정 미정",
                                fontSize = 9.5.sp, fontWeight = FontWeight.ExtraBold,
                                color = if (sch) Color(0xFF0E9B63) else Color(0xFFB58A00),
                                modifier = Modifier.background(if (sch) Color(0xFFE9FBF2) else Color(0xFFFFF6D6), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp))
                            Spacer(Modifier.weight(1f))
                            Text(won(s.finalAmount), fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = AccentBlue)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(site.ifBlank { s.customerName.ifBlank { "고객" } }, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = T1)
                        Text(s.products.ifBlank { "-" }, fontSize = 12.sp, color = T3, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(10.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val fp = s.customerPhone
                            Box(Modifier.weight(1f).background(Color(0xFFEDF2FF), RoundedCornerShape(12.dp))
                                .clickable(enabled = fp.isNotBlank()) { dialPhone(ctx, fp) }.padding(vertical = 11.dp), contentAlignment = Alignment.Center) {
                                Text("📞 전화", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (fp.isNotBlank()) AccentBlue else T3)
                            }
                            Box(Modifier.weight(1f).background(Field, RoundedCornerShape(12.dp))
                                .clickable { onContract(s.contractId) }.padding(vertical = 11.dp), contentAlignment = Alignment.Center) {
                                Text("계약서 보기", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = T1)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 상담사 화면에서 고객이 채운 필수 항목(성함·연락처·주소·서명) 확인 표시. 채우면 초록, 비면 '대기'. */
@Composable
private fun ChkRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 11.5.sp, color = T3, modifier = Modifier.width(64.dp))
        val ok = value.isNotBlank()
        Text(if (ok) value else "대기", fontSize = 12.sp,
            color = if (ok) Color(0xFF13C47B) else T3, fontWeight = if (ok) FontWeight.Bold else FontWeight.Normal)
    }
}

/** 사업자번호 하이픈 000-00-00000 (입력 중 점진). */
private fun bizNoHyphen(raw: String): String {
    val d = raw.filter { it.isDigit() }.take(10)
    return when {
        d.length <= 3 -> d
        d.length <= 5 -> "${d.take(3)}-${d.drop(3)}"
        else -> "${d.take(3)}-${d.substring(3, 5)}-${d.drop(5)}"
    }
}

/** 대표/사무실 번호 하이픈 — 8자리 로컬 0000-0000, 핸드폰(01) 000-0000-0000, 서울(02), 기타 지역(0xx). 입력 중 점진. */
private fun bizPhoneHyphen(raw: String): String {
    val d = raw.filter { it.isDigit() }
    return when {
        d.startsWith("02") -> {
            val x = d.take(10)
            when (x.length) {
                in 0..2 -> x
                in 3..5 -> "${x.take(2)}-${x.drop(2)}"
                in 6..9 -> "${x.take(2)}-${x.substring(2, 5)}-${x.drop(5)}"
                else -> "${x.take(2)}-${x.substring(2, 6)}-${x.drop(6)}"
            }
        }
        d.startsWith("01") -> {
            val x = d.take(11)
            when (x.length) {
                in 0..3 -> x
                in 4..7 -> "${x.take(3)}-${x.drop(3)}"
                else -> "${x.take(3)}-${x.substring(3, 7)}-${x.drop(7)}"
            }
        }
        d.startsWith("0") -> {
            val x = d.take(11)
            when (x.length) {
                in 0..3 -> x
                in 4..6 -> "${x.take(3)}-${x.drop(3)}"
                in 7..10 -> "${x.take(3)}-${x.substring(3, 6)}-${x.drop(6)}"
                else -> "${x.take(3)}-${x.substring(3, 7)}-${x.drop(7)}"
            }
        }
        else -> {
            val x = d.take(8)
            if (x.length <= 4) x else "${x.take(4)}-${x.drop(4)}"
        }
    }
}

/** 사진→OCR 자동입력 버튼(촬영·앨범) + 인식중 표시. 사업자등록증/약관 공용. guide=인식률 안내. */
@Composable
private fun OcrButtons(busy: Boolean, guide: String, onCamera: () -> Unit, onGallery: () -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f).background(Color(0xFFEAF0FF), RoundedCornerShape(10.dp))
                .clickable(enabled = !busy) { onCamera() }.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                Text("📷 촬영", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (busy) T3 else Color(0xFF2F6FDB))
            }
            Box(Modifier.weight(1f).background(Color(0xFFEAF0FF), RoundedCornerShape(10.dp))
                .clickable(enabled = !busy) { onGallery() }.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                Text("🖼 앨범", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (busy) T3 else Color(0xFF2F6FDB))
            }
        }
        Text(if (busy) "🔎 사진에서 읽는 중… (몇 초 걸려요)" else guide,
            fontSize = 11.sp, color = if (busy) Color(0xFF2F6FDB) else T3, fontWeight = FontWeight.Medium,
            lineHeight = 15.sp, modifier = Modifier.padding(top = 5.dp))
    }
}

// ══════════════ 박람회 기본정보 폼 (방장) ══════════════
@Composable
private fun ColumnScope.RoomFormView(
    repo: ExpoRepository, n: Nav.RoomForm, myPhone: String, toast: (String) -> Unit, onSaved: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var apartment by remember { mutableStateOf("") }
    var templateId by remember { mutableStateOf("julnun") }   // 방 계약서 양식 (서버 기본 julnun). 라벨 표시용.
    val types = remember { mutableStateListOf<String>() }
    var typeInput by remember { mutableStateOf("") }
    var terms by remember { mutableStateOf("") }
    var bizName by remember { mutableStateOf("") }
    var bizNo by remember { mutableStateOf("") }
    var repPhone by remember { mutableStateOf("") }
    var officePhone by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }

    // ── OCR (사업자등록증·약관 사진→자동입력). 서버 Vision. 촬영/앨범 둘 다. ──
    val context = LocalContext.current
    var ocrBusy by remember { mutableStateOf(false) }
    var ocrTarget by remember { mutableStateOf("") }   // "biz" | "terms"
    var cameraUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var bizFromOcr by remember { mutableStateOf(false) }    // 사업자등록증 OCR로 채운 칸 하이라이트
    var termsFromOcr by remember { mutableStateOf(false) }  // 약관 OCR로 채운 칸 하이라이트
    fun runOcr(uri: android.net.Uri) {
        val dataUrl = uriToDataUrl(context, uri)
        if (dataUrl == null) { toast("이미지를 읽지 못했어요"); return }
        val target = ocrTarget
        ocrBusy = true
        scope.launch {
            if (target == "biz") {
                repo.ocrBizReg(dataUrl)
                    .onSuccess { r ->
                        ocrBusy = false
                        if (r.bizName.isBlank() && r.bizNo.isBlank()) toast("사업자등록증을 인식하지 못했어요")
                        else {
                            if (r.bizName.isNotBlank()) bizName = r.bizName
                            if (r.bizNo.isNotBlank()) bizNo = bizNoHyphen(r.bizNo)
                            bizFromOcr = true
                            toast("사업자등록증에서 정보를 채웠어요 · 확인 후 저장")
                        }
                    }
                    .onFailure { ocrBusy = false; toast(ocrErrorText(it)) }
            } else {
                repo.ocrTerms(dataUrl)
                    .onSuccess { t ->
                        ocrBusy = false
                        if (t.isBlank()) toast("약관 글자를 인식하지 못했어요")
                        else { terms = t; termsFromOcr = true; toast("약관을 채웠어요 · 확인 후 저장") }
                    }
                    .onFailure { ocrBusy = false; toast(ocrErrorText(it)) }
            }
        }
    }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) runOcr(uri)
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) cameraUri?.let { runOcr(it) }
    }
    fun pickGallery(target: String) {
        ocrTarget = target
        galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
    fun pickCamera(target: String) {
        ocrTarget = target
        runCatching {
            val dir = java.io.File(context.cacheDir, "shared").apply { mkdirs() }
            val f = java.io.File(dir, "ocr_capture.jpg")
            val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f)
            cameraUri = uri
            cameraLauncher.launch(uri)
        }.onFailure { toast("카메라를 열지 못했어요") }
    }

    // 인식 중 로딩 모달 — 화면 정중앙(스크림 위). "멈춘 것 같다" 방지.
    if (ocrBusy) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    Modifier.background(Color.White, RoundedCornerShape(20.dp)).padding(horizontal = 34.dp, vertical = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(color = AccentBlue)
                    Spacer(Modifier.height(16.dp))
                    Text("사진에서 글자를 읽고 있어요", fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = T1)
                    Spacer(Modifier.height(3.dp))
                    Text("보통 5~10초 걸려요 · 잠시만요 🙂", fontSize = 12.sp, color = T3)
                }
            }
        }
    }

    LaunchedEffect(n.roomId) {
        repo.roomDetail(n.roomId, myPhone).onSuccess { d ->
            d.info?.let { info ->
                apartment = info.apartment; terms = info.terms
                bizName = info.bizName; bizNo = bizNoHyphen(info.bizNo)
                repPhone = bizPhoneHyphen(info.repPhone); officePhone = bizPhoneHyphen(info.officePhone)
                types.clear(); types.addAll(info.unitTypes)
                if (info.templateId.isNotBlank()) templateId = info.templateId
            }
        }
    }
    fun addTypeVal(t: String) {
        val v = t.trim().take(20)
        if (v.isNotEmpty() && !types.contains(v) && types.size < 20) types.add(v)
    }

    LazyColumn(
        Modifier.weight(1f).fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Box(Modifier.fillMaxWidth().background(Color(0xFFFFFBEA), RoundedCornerShape(12.dp)).padding(14.dp)) {
                Text("여기서 정한 아파트명·타입·업체정보·약관이 고객 계약서에 그대로 들어가요. 박람회는 보통 한 단지라, 주소는 아파트명 고정 + 고객은 동/호수·타입만 골라요.",
                    fontSize = 12.sp, color = Color(0xFF7A6A15), lineHeight = 18.sp)
            }
        }
        item {
            // 계약서 양식 — 이 방이 어떤 양식을 쓰는지 보여줌. 줄눈은 상품 미리등록 없이 계약서에서 항목·가격 입력.
            Column(Modifier.fillMaxWidth().tossCardShadow(RoundedCornerShape(16.dp)).background(Panel, RoundedCornerShape(16.dp)).padding(16.dp)) {
                Text("계약서 양식", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = T1)
                Spacer(Modifier.height(9.dp))
                Box(Modifier.background(T1, RoundedCornerShape(9.dp)).padding(horizontal = 12.dp, vertical = 7.dp)) {
                    Text("✓ ${templateName(templateId)}", fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                }
                Spacer(Modifier.height(9.dp))
                Text("이 방 계약서는 이 양식으로 만들어져요. 상품을 미리 등록하지 않고, 계약서를 열 때 줄눈 항목을 체크하고 시공·예약금·잔금 가격만 넣으면 돼요.",
                    fontSize = 11.5.sp, color = T3, lineHeight = 17.sp)
                Spacer(Modifier.height(6.dp))
                Text("다른 업종(청소·필름 등) 양식이 필요하면 알려주세요 — 맞춰서 만들어 드려요.",
                    fontSize = 11.5.sp, color = T3, lineHeight = 17.sp)
            }
        }
        item {
            Column(Modifier.fillMaxWidth().tossCardShadow(RoundedCornerShape(16.dp)).background(Panel, RoundedCornerShape(16.dp)).padding(16.dp)) {
                Text("아파트(단지)명", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = T1)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = apartment, onValueChange = { apartment = it },
                    placeholder = { Text("예: 동탄에스알골드", fontSize = 13.sp) },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        }
        item {
            Column(Modifier.fillMaxWidth().tossCardShadow(RoundedCornerShape(16.dp)).background(Panel, RoundedCornerShape(16.dp)).padding(16.dp)) {
                Text("타입(평형) 목록", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = T1)
                Text("자주 쓰는 평형은 눌러서 바로 추가 (A·B 포함). 여러 개는 쉼표로 한 번에.", fontSize = 11.5.sp, color = T3)
                Spacer(Modifier.height(10.dp))
                listOf("59", "59A", "59B", "74", "74A", "84", "84A", "84B", "101", "114").chunked(4).forEach { rowItems ->
                    Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        rowItems.forEach { p ->
                            val on = types.contains(p)
                            Box(
                                Modifier.weight(1f).background(if (on) Kk else Field, RoundedCornerShape(9.dp))
                                    .clickable { if (on) types.remove(p) else addTypeVal(p) }.padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) { Text(p, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (on) KkInk else T2) }
                        }
                        repeat(4 - rowItems.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = typeInput,
                    onValueChange = { v ->
                        if (v.any { it == ',' || it == ' ' || it == '\n' }) {
                            v.split(',', ' ', '\n').forEach { addTypeVal(it) }; typeInput = ""
                        } else typeInput = v.take(20)
                    },
                    placeholder = { Text("직접 입력 (예: 84A, 84B)", fontSize = 13.sp) },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                if (types.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    types.forEach { t ->
                        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.background(Field, RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 7.dp)) {
                                Text(t, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = T1)
                            }
                            Spacer(Modifier.weight(1f))
                            Text("삭제", fontSize = 12.sp, color = Color(0xFFE1483B), fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable { types.remove(t) })
                        }
                    }
                }
            }
        }
        item {
            Column(Modifier.fillMaxWidth().tossCardShadow(RoundedCornerShape(16.dp)).background(Panel, RoundedCornerShape(16.dp)).padding(16.dp)) {
                Text("시공업체 정보", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = T1)
                Text("빈칸으로 두면 계약서에 표시되지 않아요 — 없는 항목은 비워두세요.", fontSize = 11.5.sp, color = T3, lineHeight = 16.sp)
                Spacer(Modifier.height(8.dp))
                OcrButtons(ocrBusy && ocrTarget == "biz", "📷 사업자등록증이 화면에 꽉 차고 또렷하게 보이게 찍어 올려주세요",
                    { pickCamera("biz") }, { pickGallery("biz") })
                if (bizFromOcr) Text("✨ 형광 칸 = 사진에서 자동으로 채운 값이에요 (확인 후 저장)",
                    fontSize = 11.sp, color = Color(0xFFB58A00), fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 6.dp))
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(value = bizName, onValueChange = { bizName = it; bizFromOcr = false },
                    label = { Text("업체명", fontSize = 12.sp) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    colors = ocrFieldColors(bizFromOcr))
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = bizNo, onValueChange = { bizNo = bizNoHyphen(it); bizFromOcr = false },
                    label = { Text("사업자번호", fontSize = 12.sp) }, placeholder = { Text("000-00-00000", fontSize = 13.sp) },
                    singleLine = true, modifier = Modifier.fillMaxWidth(), colors = ocrFieldColors(bizFromOcr),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = repPhone, onValueChange = { repPhone = bizPhoneHyphen(it) },
                    label = { Text("대표번호", fontSize = 12.sp) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = officePhone, onValueChange = { officePhone = bizPhoneHyphen(it) },
                    label = { Text("사무실번호", fontSize = 12.sp) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
            }
        }
        item {
            Column(Modifier.fillMaxWidth().tossCardShadow(RoundedCornerShape(16.dp)).background(Panel, RoundedCornerShape(16.dp)).padding(16.dp)) {
                Text("계약 약관", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = T1)
                Text("계약서 하단에 그대로 들어가요.", fontSize = 11.5.sp, color = T3)
                Spacer(Modifier.height(8.dp))
                OcrButtons(ocrBusy && ocrTarget == "terms", "📷 인식할 약관 부분만 또렷하게 찍거나 잘라서 올려주세요 (인식률 ↑)",
                    { pickCamera("terms") }, { pickGallery("terms") })
                if (termsFromOcr) Text("✨ 형광 칸 = 사진에서 자동으로 읽은 약관이에요 (확인 후 저장)",
                    fontSize = 11.sp, color = Color(0xFFB58A00), fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 6.dp))
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = terms, onValueChange = { terms = it; termsFromOcr = false },
                    placeholder = { Text("예: 잔금은 시공 완료 당일 지급합니다…", fontSize = 13.sp) },
                    minLines = 4, modifier = Modifier.fillMaxWidth(), colors = ocrFieldColors(termsFromOcr))
            }
        }
        item { Spacer(Modifier.height(2.dp)) }
    }
    Box(
        Modifier.fillMaxWidth().background(Panel)
            .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars)).padding(14.dp)
    ) {
        BigButton(if (saving) "저장 중…" else "기본정보 저장", enabled = !saving, bg = Kk, fg = KkInk) {
            if (apartment.isBlank()) {
                toast("아파트(단지)명을 입력해 주세요")
            } else {
                saving = true
                scope.launch {
                    repo.setRoomInfo(n.roomId, myPhone,
                        ExpoRepository.RoomInfo(apartment, types.toList(), terms, bizName, bizNo, repPhone, officePhone))
                        .onSuccess { saving = false; toast("기본정보를 저장했어요"); onSaved() }
                        .onFailure { saving = false; toast("저장 실패 · 다시 시도해 주세요") }
                }
            }
        }
    }
}

// ══════════════ 앱 안 네이티브 계약서 (웹 안 열고) ══════════════
@Composable
private fun ColumnScope.ContractView(repo: ExpoRepository, n: Nav.Contract, myPhone: String, toast: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val cachedSub = ExpoCache.submissions[n.roomId]?.items?.find { it.contractId == n.contractId }
    var sub by remember { mutableStateOf(cachedSub) }
    var loadErr by remember { mutableStateOf(false) }
    var memo by remember { mutableStateOf(cachedSub?.note ?: "") }
    var memoInit by remember { mutableStateOf(cachedSub != null) }
    var savingMemo by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf(ExpoCache.details[n.roomId]?.info) }

    LaunchedEffect(n.contractId) {
        repo.submissions(n.roomId, myPhone)
            .onSuccess { data ->
                ExpoCache.submissions[n.roomId] = data
                val found = data.items.find { it.contractId == n.contractId }
                if (found == null) { if (sub == null) loadErr = true }
                else { sub = found; if (!memoInit) { memo = found.note; memoInit = true } }
            }
            .onFailure { if (sub == null) loadErr = true }
    }
    LaunchedEffect(n.roomId) {
        repo.roomDetail(n.roomId, myPhone).onSuccess { info = it.info; ExpoCache.details[n.roomId] = it }
    }

    val s = sub
    if (s == null) {
        if (loadErr) {
            Box(Modifier.weight(1f).fillMaxWidth(), Alignment.Center) {
                Text("계약서를 불러오지 못했어요", fontSize = 14.sp, color = T3)
            }
        } else {
            Column(Modifier.weight(1f).fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Skel(Modifier.fillMaxWidth().height(280.dp), 18)
                Skel(Modifier.fillMaxWidth().height(120.dp), 18)
            }
        }
    } else {
        val site = listOf(s.apartment, s.dongHo).filter { it.isNotBlank() }.joinToString(" ").ifBlank { s.address }
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Column(Modifier.fillMaxWidth().tossCardShadow(RoundedCornerShape(18.dp)).background(Panel, RoundedCornerShape(18.dp)).padding(18.dp)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("시공 계약서", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = T1, modifier = Modifier.weight(1f))
                        Text("No. ${s.contractId}", fontSize = 11.sp, color = T3, fontWeight = FontWeight.Bold)
                    }
                    info?.let { inf ->
                        val hasBiz = inf.bizName.isNotBlank() || inf.bizNo.isNotBlank() || inf.repPhone.isNotBlank() || inf.officePhone.isNotBlank()
                        if (hasBiz) {
                            Spacer(Modifier.height(10.dp))
                            Column(Modifier.fillMaxWidth().background(Field, RoundedCornerShape(10.dp)).padding(12.dp)) {
                                if (inf.bizName.isNotBlank()) Text(inf.bizName, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = T1)
                                if (inf.bizNo.isNotBlank()) Text("사업자번호 ${bizNoHyphen(inf.bizNo)}", fontSize = 11.sp, color = T3)
                                val phones = listOf(inf.repPhone, inf.officePhone).filter { it.isNotBlank() }.joinToString("  ·  ") { ph(it) }
                                if (phones.isNotBlank()) Text(phones, fontSize = 11.sp, color = T3)
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    SubRow("성함", s.customerName.ifBlank { "-" })
                    SubRow("연락처", ph(s.customerPhone).ifBlank { ph(s.customerPhoneMasked).ifBlank { "-" } })
                    SubRow("시공주소", site.ifBlank { "-" })
                    SubRow("시공일", if (s.scheduledAtMs > 0L) dateKo(s.scheduledAtMs) else "미정")
                    Spacer(Modifier.height(10.dp))
                    Text("시공 내역", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = T2)
                    Spacer(Modifier.height(6.dp))
                    val pick = s.tplPick
                    if (pick != null) {
                        // 줄눈 항목 · 재질 (어디를 무슨 재질로 시공하는지) — 현장에서 이거 보고 시공
                        if (pick.julnun.isNotEmpty()) {
                            Text("줄눈 시공", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = T3)
                            Spacer(Modifier.height(3.dp))
                            pick.julnun.forEach { (item, mat) ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(item, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = T1, modifier = Modifier.weight(1f))
                                    if (mat.isNotBlank()) Box(Modifier.background(Color(0xFFEEF4FF), RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 3.dp)) {
                                        Text(mat, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = AccentBlue)
                                    }
                                }
                            }
                        }
                        listOf("실리콘 오염 방지" to pick.silicone, "입주 청소" to pick.cleaning).forEach { (title, list) ->
                            if (list.isNotEmpty()) {
                                Spacer(Modifier.height(9.dp))
                                Text(title, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = T3)
                                Spacer(Modifier.height(4.dp))
                                list.chunked(2).forEach { rowItems ->
                                    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        rowItems.forEach { it2 ->
                                            Box(Modifier.weight(1f).background(Field, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 7.dp)) {
                                                Text("✓ $it2", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = T1, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            }
                                        }
                                        repeat(2 - rowItems.size) { Spacer(Modifier.weight(1f)) }
                                    }
                                }
                            }
                        }
                    } else {
                        Text(s.products.ifBlank { "-" }, fontSize = 13.sp, color = T1, fontWeight = FontWeight.Medium, lineHeight = 19.sp)
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(
                        Modifier.fillMaxWidth().background(Field, RoundedCornerShape(12.dp)).padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("최종 금액", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = T1, modifier = Modifier.weight(1f))
                        Text(won(s.finalAmount), fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = AccentBlue)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("계약자 ${s.agentName.ifBlank { "-" }} · 시공자 ${s.assignedName ?: "미배정"}", fontSize = 11.5.sp, color = T3)
                    if (s.note.isNotBlank()) {
                        Spacer(Modifier.height(10.dp))
                        Text("특이사항 · 비고", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = T2)
                        Spacer(Modifier.height(3.dp))
                        Text(s.note, fontSize = 12.5.sp, color = T1, lineHeight = 18.sp)
                    }
                    info?.terms?.takeIf { it.isNotBlank() }?.let { t ->
                        Spacer(Modifier.height(12.dp))
                        Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFEEF0F3)))
                        Spacer(Modifier.height(10.dp))
                        Text("계약 약관", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = T2)
                        Spacer(Modifier.height(3.dp))
                        Text(t, fontSize = 11.5.sp, color = T2, lineHeight = 17.sp)
                    }
                }
            }
            // 메모 (편집 — 팀이 특이사항 적어두기)
            item {
                Column(Modifier.fillMaxWidth().tossCardShadow(RoundedCornerShape(18.dp)).background(Panel, RoundedCornerShape(18.dp)).padding(16.dp)) {
                    Text("메모 · 특이사항", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = T1)
                    Text("고객 특이사항이 생기면 여기 적어두세요. (팀 공유)", fontSize = 11.5.sp, color = T3)
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = memo, onValueChange = { memo = it },
                        placeholder = { Text("예: 오전만 시공 가능, 반려동물 있음", fontSize = 13.sp) },
                        minLines = 3, modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    BigButton(if (savingMemo) "저장 중…" else "메모 저장", enabled = !savingMemo, bg = Kk, fg = KkInk) {
                        savingMemo = true
                        scope.launch {
                            repo.setMemo(s.contractId, myPhone, memo)
                                .onSuccess { savingMemo = false; toast("메모를 저장했어요") }
                                .onFailure {
                                    savingMemo = false
                                    toast(if ((it.message ?: "").contains("HTTP 404")) "메모 저장은 아직 준비 중이에요" else "저장하지 못했어요 · 다시 시도해 주세요")
                                }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(2.dp)) }
        }
        // ── 하단 고정: 공유 / PDF ──
        Row(
            Modifier.fillMaxWidth().background(Panel)
                .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars)).padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                Modifier.weight(1f).background(Kk, RoundedCornerShape(14.dp))
                    .clickable { shareUrl(ctx, repo.receiptUrl(s.contractId)) }.padding(vertical = 15.dp),
                contentAlignment = Alignment.Center
            ) { Text("카톡 공유", fontSize = 14.5.sp, fontWeight = FontWeight.ExtraBold, color = KkInk) }
            Box(
                Modifier.weight(1f).background(Field, RoundedCornerShape(14.dp))
                    .clickable { openUrl(ctx, repo.receiptUrl(s.contractId)) }.padding(vertical = 15.dp),
                contentAlignment = Alignment.Center
            ) { Text("PDF · 인쇄", fontSize = 14.5.sp, fontWeight = FontWeight.ExtraBold, color = T1) }
        }
    }
}

// ══════════════ 박람회 달력 ══════════════
@Composable
private fun ColumnScope.CalendarView(repo: ExpoRepository, n: Nav.Calendar, myPhone: String, onContract: (Long) -> Unit) {
    val ctx = LocalContext.current
    var items by remember { mutableStateOf(ExpoCache.submissions[n.roomId]?.items?.filter { it.scheduledAtMs > 0L }) }
    LaunchedEffect(n.roomId) {
        repo.submissions(n.roomId, myPhone)
            .onSuccess { r -> items = r.items.filter { it.scheduledAtMs > 0L }; ExpoCache.submissions[n.roomId] = r }
            .onFailure { if (items == null) items = emptyList() }
    }
    val today = remember { java.util.Calendar.getInstance() }
    var year by remember { mutableStateOf(today.get(java.util.Calendar.YEAR)) }
    var month by remember { mutableStateOf(today.get(java.util.Calendar.MONTH)) }   // 0-based
    var dialogDay by remember { mutableStateOf<Int?>(null) }   // 탭한 날짜(그날 시공 목록 다이얼로그)

    val list = items
    if (list == null) {
        Column(Modifier.weight(1f).fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Skel(Modifier.fillMaxWidth(0.5f).height(20.dp))
            Skel(Modifier.fillMaxWidth().height(380.dp), 14)
        }
    } else {
        val byDay = remember(list, year, month) {
            val m = HashMap<Int, MutableList<ExpoRepository.Submission>>()
            for (s in list) {
                val c = java.util.Calendar.getInstance().apply { timeInMillis = s.scheduledAtMs }
                if (c.get(java.util.Calendar.YEAR) == year && c.get(java.util.Calendar.MONTH) == month)
                    m.getOrPut(c.get(java.util.Calendar.DAY_OF_MONTH)) { mutableListOf() }.add(s)
            }
            m
        }
        val cal = java.util.Calendar.getInstance().apply { clear(); set(year, month, 1) }
        val firstDow = cal.get(java.util.Calendar.DAY_OF_WEEK) - 1   // 0=일
        val daysInMonth = cal.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)
        val gridRows = (firstDow + daysInMonth + 6) / 7

        Column(Modifier.weight(1f).fillMaxWidth().background(Panel)) {
            // 월 이동 헤더
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(34.dp).clickable { if (month == 0) { month = 11; year-- } else month-- },
                    contentAlignment = Alignment.Center) { Icon(Icons.Default.KeyboardArrowLeft, null, tint = T2, modifier = Modifier.size(24.dp)) }
                Spacer(Modifier.weight(1f))
                Text("${year}년 ${month + 1}월", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = T1)
                Spacer(Modifier.weight(1f))
                Box(Modifier.size(34.dp).clickable { if (month == 11) { month = 0; year++ } else month++ },
                    contentAlignment = Alignment.Center) { Icon(Icons.Default.KeyboardArrowRight, null, tint = T2, modifier = Modifier.size(24.dp)) }
            }
            // 요일 — 그리드와 좌우 패딩 0 으로 통일(요일 라벨이 날짜 열과 정확히 정렬되게). 4dp 차이로 그리드가 왼쪽 밀려 보이던 버그.
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                listOf("일", "월", "화", "수", "목", "금", "토").forEachIndexed { i, d ->
                    Text(d, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        color = if (i == 0) Color(0xFFE1483B) else T3,
                        textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                }
            }
            // 날짜 그리드 — 화면을 꽉 채워 넓게(타임트리식). 셀에 시공 텍스트를 여러 줄로 직접 표시.
            //   좌우 스와이프로 달 넘김(왼쪽=다음달, 오른쪽=이전달). 셀 탭(클릭)과는 slop 으로 구분됨.
            Column(
                Modifier.weight(1f).fillMaxWidth().pointerInput(Unit) {
                    var total = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { total = 0f },
                        onDragEnd = {
                            if (total <= -50f) { if (month == 11) { month = 0; year++ } else month++ }
                            else if (total >= 50f) { if (month == 0) { month = 11; year-- } else month-- }
                        }
                    ) { _, drag -> total += drag }
                }
            ) {
                for (r in 0 until gridRows) {
                    Row(Modifier.weight(1f).fillMaxWidth()) {
                        for (col in 0 until 7) {
                            val day = r * 7 + col - firstDow + 1
                            if (day in 1..daysInMonth) {
                                val dayItems = byDay[day] ?: emptyList()
                                val isToday = year == today.get(java.util.Calendar.YEAR) &&
                                    month == today.get(java.util.Calendar.MONTH) &&
                                    day == today.get(java.util.Calendar.DAY_OF_MONTH)
                                Column(
                                    Modifier.weight(1f).fillMaxHeight()
                                        .padding(0.5.dp)
                                        .background(Color(0xFFFAFBFC))
                                        .clickable(enabled = dayItems.isNotEmpty()) { dialogDay = day }
                                        .padding(horizontal = 2.dp, vertical = 3.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally   // 날짜 숫자를 요일 라벨(가운데)과 정렬 (치우침 방지)
                                ) {
                                    Text("$day", fontSize = 12.sp,
                                        fontWeight = if (isToday) FontWeight.ExtraBold else FontWeight.Medium,
                                        color = if (isToday) KkInk else if (col == 0) Color(0xFFE1483B) else T1,
                                        modifier = if (isToday) Modifier.background(Kk, RoundedCornerShape(7.dp)).padding(horizontal = 6.dp, vertical = 1.dp) else Modifier)
                                    Spacer(Modifier.height(2.dp))
                                    dayItems.take(3).forEach { s ->
                                        // 그날 몇동 몇호 시공인지 바로 보이게 = 동호수 우선.
                                        Text(
                                            s.dongHo.ifBlank { s.apartment.ifBlank { s.customerName.ifBlank { "고객" } } },
                                            fontSize = 9.5.sp, color = Color(0xFF2B59D6), fontWeight = FontWeight.Medium,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.fillMaxWidth()
                                                .background(Color(0xFFE8EEFF), RoundedCornerShape(3.dp))
                                                .padding(horizontal = 3.dp, vertical = 1.dp)
                                        )
                                        Spacer(Modifier.height(2.dp))
                                    }
                                    if (dayItems.size > 3) Text("+${dayItems.size - 3}", fontSize = 9.sp, color = T3)
                                }
                            } else {
                                Box(Modifier.weight(1f).fillMaxHeight().padding(0.5.dp).background(Color(0xFFF3F4F6)))
                            }
                        }
                    }
                }
            }
        }

        // 날짜 탭 → 그날 시공 목록 다이얼로그
        val dd = dialogDay
        if (dd != null) {
            val dayItems = byDay[dd] ?: emptyList()
            AlertDialog(
                onDismissRequest = { dialogDay = null },
                title = { Text("${month + 1}/$dd 시공 ${dayItems.size}건", fontWeight = FontWeight.ExtraBold, color = T1) },
                text = {
                    Column {
                        com.detailline.callfollowcrm.presentation.util.ForceDialogResize()
                        dayItems.forEachIndexed { i, s ->
                            val site = listOf(s.apartment, s.dongHo).filter { it.isNotBlank() }.joinToString(" ")
                            val fullPhone = s.customerPhone
                            Column(Modifier.fillMaxWidth().clickable { dialogDay = null; onContract(s.contractId) }.padding(vertical = 8.dp)) {
                                Text(site.ifBlank { s.customerName.ifBlank { "고객" } }, fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = T1)
                                if (s.products.isNotBlank()) {
                                    Spacer(Modifier.height(2.dp))
                                    Text("시공 · ${s.products}", fontSize = 12.sp, color = T2)
                                }
                                Spacer(Modifier.height(3.dp))
                                Text("📞 ${ph(fullPhone).ifBlank { ph(s.customerPhoneMasked).ifBlank { "-" } }}",
                                    fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                    color = if (fullPhone.isNotBlank()) AccentBlue else T3,
                                    modifier = Modifier.clickable(enabled = fullPhone.isNotBlank()) { dialPhone(ctx, fullPhone) })
                                Text("계약자 ${s.agentName.ifBlank { "-" }} · 시공자 ${s.assignedName ?: "미배정"} · ${won(s.finalAmount)}",
                                    fontSize = 11.5.sp, color = T3, modifier = Modifier.padding(top = 3.dp))
                                Text("탭해서 계약서 보기 ›", fontSize = 12.sp, color = AccentBlue, fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(top = 4.dp))
                            }
                            if (i < dayItems.size - 1) Box(Modifier.fillMaxWidth().height(1.dp).background(ExpoBg))
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { dialogDay = null }) { Text("닫기", color = AccentBlue, fontWeight = FontWeight.Bold) } },
                containerColor = Color.White,
                tonalElevation = 0.dp   // M3 tonalElevation 회색 톤 제거 → 진짜 흰색. (2026-08-12)
            )
        }
    }
}

/** 날짜 선택(삼성 기본 DatePicker). 저장된 값 있으면 그 날짜로 시작. */
private fun pickDate(ctx: android.content.Context, currentMs: Long, onPicked: (Long) -> Unit) {
    val c = java.util.Calendar.getInstance().apply { if (currentMs > 0L) timeInMillis = currentMs }
    android.app.DatePickerDialog(ctx, { _, y, mo, d ->
        val picked = java.util.Calendar.getInstance().apply { clear(); set(y, mo, d, 9, 0) }.timeInMillis
        onPicked(picked)
    }, c.get(java.util.Calendar.YEAR), c.get(java.util.Calendar.MONTH), c.get(java.util.Calendar.DAY_OF_MONTH)).show()
}

/** ms → "M/d(요일)". */
private fun dateKo(ms: Long): String {
    if (ms <= 0L) return "미정"
    val c = java.util.Calendar.getInstance().apply { timeInMillis = ms }
    val dow = arrayOf("일", "월", "화", "수", "목", "금", "토")[c.get(java.util.Calendar.DAY_OF_WEEK) - 1]
    return "${c.get(java.util.Calendar.MONTH) + 1}/${c.get(java.util.Calendar.DAY_OF_MONTH)}($dow)"
}

// ══════════════ 공통 조각 ══════════════
@Composable
private fun BigButton(text: String, enabled: Boolean, bg: Color, fg: Color, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().background(bg, RoundedCornerShape(15.dp))
            .clickable(enabled = enabled, onClick = onClick).padding(vertical = 15.dp),
        contentAlignment = Alignment.Center
    ) { Text(text, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = fg) }
}

@Composable
private fun MenuCard(title: String, desc: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().tossCardShadow(RoundedCornerShape(18.dp)).background(Panel, RoundedCornerShape(18.dp)).clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = T1)
            Text(desc, fontSize = 12.sp, color = T3, fontWeight = FontWeight.Medium)
        }
        Icon(Icons.Default.KeyboardArrowRight, null, tint = T3, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun InputDialog(
    title: String, label: String, placeholder: String, confirm: String,
    numeric: Boolean = false, onDismiss: () -> Unit, onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        // M3 AlertDialog 는 containerColor 흰색이어도 tonalElevation(기본 6dp)이 회색 톤을 덧씌운다
        //   → 둘 다 꺼야 진짜 흰색(날짜선택창 회색 버그와 동일한 M3 함정). (2026-08-12)
        containerColor = Color.White,
        tonalElevation = 0.dp,
        title = { Text(title, fontWeight = FontWeight.ExtraBold) },
        text = {
            Column {
                Text(label, fontSize = 13.sp, color = T2)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = if (numeric) it.filter { c -> c.isDigit() } else it },
                    placeholder = { Text(placeholder) }, singleLine = true,
                    keyboardOptions = if (numeric) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(enabled = text.trim().isNotEmpty(), onClick = { onConfirm(text.trim()) }) {
                Text(confirm, fontWeight = FontWeight.ExtraBold, color = Color(0xFFB58A00))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소", color = T3) } }
    )
}

private fun won(amount: Long): String = "%,d원".format(amount)

/** 표시용 전화번호 하이픈 (가독성). 빈 값/알 수 없는 형식이면 그대로. */
private fun ph(raw: String): String = com.detailline.callfollowcrm.util.PhoneNumberFormatter.format(raw)

/** 계약서 양식 표시 이름 (라벨용). 실제 양식 정의는 서버(GET /api/expo/template/{id}). 현재 줄눈 하나. */
private fun templateName(id: String?): String = when ((id ?: "").trim()) {
    "julnun" -> "줄눈 시공 표준"
    "" -> "자유 상품"
    else -> "표준 양식"
}

/**
 * 이미지 Uri → **다운스케일** base64 JPEG dataURL (OCR 업로드용).
 * 앨범 원본은 수 MB라 업로드 실패/타임아웃 원인 → 최대 1600px·JPEG85% 로 줄여 ~수백KB 로. OCR 인식엔 충분.
 */
private fun uriToDataUrl(ctx: android.content.Context, uri: android.net.Uri): String? = runCatching {
    val cr = ctx.contentResolver
    val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
    cr.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
    val maxDim = 1600
    var sample = 1
    while (bounds.outWidth / sample > maxDim || bounds.outHeight / sample > maxDim) sample *= 2
    val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
    val bmp = cr.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, opts) }
        ?: return@runCatching null
    val baos = java.io.ByteArrayOutputStream()
    bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, baos)
    bmp.recycle()
    "data:image/jpeg;base64," + android.util.Base64.encodeToString(baos.toByteArray(), android.util.Base64.NO_WRAP)
}.getOrNull()

/** OCR 실패 원인을 사람 말투로 (예외에 HTTP 코드/타임아웃 박혀 옴). */
private fun ocrErrorText(e: Throwable): String {
    val m = e.message ?: ""
    val low = m.lowercase()
    return when {
        "timeout" in low || "timed out" in low -> "시간 초과예요 — 사진이 크거나 서버가 느려요. 잠시 후 다시 시도해 주세요."
        "HTTP 413" in m || "too large" in low -> "사진이 너무 커요. 더 작게 찍어 다시 시도해 주세요."
        "HTTP 400" in m -> "이미지를 읽지 못했어요. 다른 사진으로 시도해 주세요."
        "HTTP 401" in m || "HTTP 403" in m || "key" in low || "quota" in low || "api" in low ->
            "서버 인식 키/한도 문제일 수 있어요. 사장님께 알려주세요."
        "HTTP 5" in m -> "서버 인식 오류예요. 잠시 후 다시 시도해 주세요."
        "unable to resolve host" in low || "failed to connect" in low -> "인터넷 연결을 확인해 주세요."
        m.isBlank() -> "인식 실패 — 네트워크를 확인해 주세요."
        else -> "인식 실패: ${m.take(90)}"
    }
}

/** 계약서 보관 실패를 원인별 사람 말투로 (예외에 HTTP 코드가 박혀 옴). */
private fun finalizeErrorText(e: Throwable): String {
    val m = e.message ?: ""
    return when {
        "HTTP 409" in m -> "고객이 아직 계약서 작성을 끝내지 않았어요. 고객 화면에서 [작성 완료]를 눌러야 보관돼요."
        "HTTP 400" in m -> "필수 항목이 빠졌어요. 상품 선택과 고객 정보(성함·연락처·주소·서명)를 확인해 주세요."
        "HTTP 404" in m -> "계약 정보를 찾지 못했어요. 잠시 후 다시 시도해 주세요."
        else -> "계약서를 보관하지 못했어요. 인터넷 연결을 확인하고 다시 시도해 주세요."
    }
}

private fun hhmm(ms: Long): String {
    if (ms <= 0L) return "-"
    val c = java.util.Calendar.getInstance().apply { timeInMillis = ms }
    val h = c.get(java.util.Calendar.HOUR_OF_DAY); val m = c.get(java.util.Calendar.MINUTE)
    return "%s %d:%02d".format(if (h < 12) "오전" else "오후", ((h + 11) % 12) + 1, m)
}

private fun dateShort(ms: Long): String {
    if (ms <= 0L) return "-"
    val c = java.util.Calendar.getInstance().apply { timeInMillis = ms }
    return "${c.get(java.util.Calendar.MONTH) + 1}/${c.get(java.util.Calendar.DAY_OF_MONTH)}"
}

private fun openUrl(ctx: android.content.Context, url: String) {
    if (url.isBlank()) return
    runCatching {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

/** 전화번호 탭 → 다이얼러(번호 채워진 채) 열기. */
private fun dialPhone(ctx: android.content.Context, phone: String) {
    val digits = phone.filter { it.isDigit() || it == '+' }
    if (digits.isBlank()) return
    runCatching {
        ctx.startActivity(Intent(Intent.ACTION_DIAL, android.net.Uri.parse("tel:$digits")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
