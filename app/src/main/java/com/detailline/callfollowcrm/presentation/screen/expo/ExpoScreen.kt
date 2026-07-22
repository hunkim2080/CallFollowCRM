package com.detailline.callfollowcrm.presentation.screen.expo

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.detailline.callfollowcrm.ai.ExpoRepository
import com.detailline.callfollowcrm.data.AppContainer
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

private sealed class Nav {
    object List : Nav()
    data class RoomView(val roomId: String, val name: String, val role: String) : Nav()
    data class Products(val roomId: String, val name: String) : Nav()
    data class Qr(val roomId: String, val name: String, val session: ExpoRepository.Session) : Nav()
    data class Subs(val roomId: String, val name: String) : Nav()
    data class Calendar(val roomId: String, val name: String) : Nav()
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
            is Nav.Calendar -> Nav.RoomView(n.roomId, n.name, "member")
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
            is Nav.Calendar -> "박람회 달력"
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
                is Nav.Calendar -> Nav.RoomView(n.roomId, n.name, "member")
            }
        }

        when (val n = nav) {
            is Nav.List -> RoomListView(repo, myPhone, myName, ::toast,
                onOpen = { nav = Nav.RoomView(it.roomId, it.name, it.role) })
            is Nav.RoomView -> RoomDetailView(repo, n, myPhone, myName, ::toast,
                onProducts = { nav = Nav.Products(n.roomId, n.name) },
                onQr = { nav = Nav.Qr(n.roomId, n.name, it) },
                onSubs = { nav = Nav.Subs(n.roomId, n.name) },
                onCalendar = { nav = Nav.Calendar(n.roomId, n.name) })
            is Nav.Products -> ProductsEditorView(repo, n, myPhone, ::toast,
                onDone = { nav = Nav.RoomView(n.roomId, n.name, "owner") })
            is Nav.Qr -> QrView(repo, n, myPhone, ::toast)
            is Nav.Subs -> SubmissionsView(repo, n, myPhone, ::toast)
            is Nav.Calendar -> CalendarView(repo, n, myPhone)
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
                Text(title, fontSize = 20.sp, fontWeight = FontWeight.Black, color = KkInk,
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
    onOpen: (ExpoRepository.Room) -> Unit
) {
    val scope = rememberCoroutineScope()
    var rooms by remember { mutableStateOf<List<ExpoRepository.Room>?>(null) }
    var showCreate by remember { mutableStateOf(false) }
    var showJoin by remember { mutableStateOf(false) }

    fun reload() = scope.launch {
        repo.rooms(myPhone).onSuccess { rooms = it }.onFailure { rooms = emptyList(); toast("목록을 불러오지 못했어요") }
    }
    LaunchedEffect(Unit) { reload() }

    Box(Modifier.weight(1f).fillMaxWidth()) {
        val list = rooms
        when {
            list == null -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = AccentBlue) }
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
        ) { Text("초대코드로 합류", fontSize = 15.sp, fontWeight = FontWeight.Black, color = T1) }
        Row(
            Modifier.weight(1f).background(Kk, RoundedCornerShape(15.dp))
                .clickable { showCreate = true }.padding(vertical = 15.dp),
            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Add, null, tint = KkInk, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(4.dp))
            Text("방 만들기", fontSize = 15.sp, fontWeight = FontWeight.Black, color = KkInk)
        }
    }

    if (showCreate) InputDialog(
        title = "새 박람회 방", label = "방(부스) 이름을 적어주세요.",
        placeholder = "예: 2026 봄 홈리모델링박람회", confirm = "만들기",
        onDismiss = { showCreate = false }, onConfirm = { name ->
            showCreate = false
            scope.launch {
                repo.createRoom(myPhone, name, myName)
                    .onSuccess { toast("방이 만들어졌어요"); reload(); onOpen(it) }
                    .onFailure { toast("방 만들기 실패: ${it.message}") }
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

@Composable
private fun RoomRow(r: ExpoRepository.Room, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(46.dp).background(AccentBlue, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
            Text(r.name.take(1), fontSize = 18.sp, fontWeight = FontWeight.Black, color = Color.White)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(r.name, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = T1, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("팀원 ${r.memberCount} · 상품 ${r.productCount} · 접수 ${r.contractCount}",
                fontSize = 11.5.sp, color = T3, fontWeight = FontWeight.Medium)
        }
        val badge = if (r.role == "owner") "방장" else "팀원"
        Text(badge, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFB58A00),
            modifier = Modifier.background(Color(0xFFFFF6D6), RoundedCornerShape(7.dp)).padding(horizontal = 8.dp, vertical = 3.dp))
    }
}

// ══════════════ 방 상세 ══════════════
@Composable
private fun ColumnScope.RoomDetailView(
    repo: ExpoRepository, n: Nav.RoomView, myPhone: String, myName: String, toast: (String) -> Unit,
    onProducts: () -> Unit, onQr: (ExpoRepository.Session) -> Unit, onSubs: () -> Unit, onCalendar: () -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var detail by remember { mutableStateOf<ExpoRepository.RoomDetail?>(null) }
    var code by remember { mutableStateOf<String?>(null) }
    var opening by remember { mutableStateOf(false) }

    LaunchedEffect(n.roomId) {
        repo.roomDetail(n.roomId, myPhone).onSuccess { detail = it }.onFailure { toast("방 정보를 못 불러왔어요") }
        // 방장이면 초대코드도 (rooms 목록에서 code 내려오지만 상세엔 없음 → rooms 재조회)
        if (n.role == "owner") repo.rooms(myPhone).onSuccess { list -> code = list.find { it.roomId == n.roomId }?.code }
    }

    var memOpen by remember { mutableStateOf(false) }
    val d = detail
    LazyColumn(
        Modifier.weight(1f).fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (d == null) {
            item { Box(Modifier.fillMaxWidth().padding(40.dp), Alignment.Center) { CircularProgressIndicator(color = AccentBlue) } }
        } else {
            // ── ① 방 정보: 초대코드(방장) + 팀원(접기) ──
            item {
                Column(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(18.dp)).padding(16.dp)) {
                    if (n.role == "owner" && code != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("팀원 초대코드", fontSize = 11.5.sp, color = T3, fontWeight = FontWeight.Bold)
                                Text(code!!, fontSize = 26.sp, fontWeight = FontWeight.Black, color = T1, letterSpacing = 4.sp)
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
                            ) { Text("공유", fontSize = 13.sp, fontWeight = FontWeight.Black, color = KkInk) }
                        }
                        Spacer(Modifier.height(12.dp))
                        Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFF0F1F3)))
                        Spacer(Modifier.height(12.dp))
                    }
                    Row(Modifier.fillMaxWidth().clickable { memOpen = !memOpen }, verticalAlignment = Alignment.CenterVertically) {
                        Text("팀원 ${d.members.size}명", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = T1)
                        Spacer(Modifier.weight(1f))
                        Text(if (memOpen) "접기 ▴" else "보기 ▾", fontSize = 12.sp, color = T3)
                    }
                    if (memOpen) {
                        Spacer(Modifier.height(4.dp))
                        d.members.forEach { m ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(m.name.ifBlank { "이름없음" }, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = T1)
                                if (m.role == "owner") {
                                    Spacer(Modifier.width(6.dp))
                                    Text("방장", fontSize = 9.sp, fontWeight = FontWeight.Black, color = Color(0xFFB58A00),
                                        modifier = Modifier.background(Color(0xFFFFF6D6), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp))
                                }
                                Spacer(Modifier.weight(1f))
                                Text(m.phone, fontSize = 12.sp, color = T3)
                            }
                        }
                    }
                }
            }

            // ── ② 고객 계약서 (주 액션) ──
            item {
                val hasCatalog = d.catalog.isNotEmpty()
                Column(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(18.dp)).padding(16.dp)) {
                    Text("고객 계약서 받기", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = T1)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (hasCatalog) "QR을 띄워 고객 폰으로 찍게 하면, 사장님이 상품을 고르고 고객은 실시간으로 보며 서명해요."
                        else "방장이 상품·서비스를 먼저 등록해야 계약서를 열 수 있어요.",
                        fontSize = 12.5.sp, color = T2, lineHeight = 18.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    BigButton("계약서 열기 (QR)", enabled = hasCatalog && !opening, bg = if (hasCatalog) Kk else Field,
                        fg = if (hasCatalog) KkInk else T3) {
                        opening = true
                        scope.launch {
                            repo.createSession(n.roomId, myPhone)
                                .onSuccess { opening = false; onQr(it) }
                                .onFailure { opening = false; toast("계약서 열기 실패: ${it.message?.take(60)}") }
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
                        GroupRow("우리 팀 접수서", "받은 계약 모아보기 (번호 뒷자리 가림)", onSubs)
                        RowDivider()
                        GroupRow("박람회 달력", "시공 일정을 날짜별로 한눈에 📅", onCalendar)
                        if (n.role == "owner") {
                            RowDivider()
                            val cnt = d.catalog.size
                            GroupRow("상품·서비스 준비", if (cnt > 0) "등록된 항목 ${cnt}개 · 수정" else "계약서에 쓸 상품·단가 등록", onProducts)
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
        Text("›", fontSize = 20.sp, color = T3, fontWeight = FontWeight.Bold)
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
                Column(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(14.dp)).padding(12.dp)) {
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
        Box(Modifier.background(ExpoBg).navigationBarsPadding().padding(14.dp)) {
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
                        .onFailure { saving = false; toast("저장 실패: ${it.message?.take(60)}") }
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

    LaunchedEffect(n.roomId) { repo.getProducts(n.roomId).onSuccess { catalog = it } }

    // 디바운스 push — 선택/할인/계약금 바뀌면 400ms 후 서버로(고객 웹에 실시간 반영).
    val pushKey = qty.entries.sortedBy { it.key }.joinToString(",") { "${it.key}:${it.value}" } +
        "|$discountText|$depositOn|$depositText|$noteText"
    LaunchedEffect(pushKey) {
        delay(400)
        val items = qty.filter { it.value > 0 }.map { it.key to it.value }
        repo.liveAgentPush(sid, sec, items, discountText.digitsToLong(), depositOn, depositText.digitsToLong(), noteText.trim())
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
            Text("계약이 정상적으로 체결되었어요!", fontSize = 19.sp, fontWeight = FontWeight.Black, color = T1, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text("${won(done.finalAmount)} · 계약서가 보관됐어요", fontSize = 13.sp, color = T2)
            Spacer(Modifier.height(24.dp))
            BigButton("계약서 보기 · 공유 (PDF)", enabled = true, bg = Kk, fg = KkInk) {
                shareUrl(ctx, repo.receiptUrl(done.contractId))
            }
        }
    } else {
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // QR + 고객 연결 상태
            item {
                Column(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(18.dp)).padding(16.dp),
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
                            ChkRow("연락처", l.customerPhone)
                            ChkRow("시공주소", site)
                            ChkRow("서명", if (l.signaturePresent) "완료" else "")
                        }
                    }
                }
            }
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
                            if (sel) Text("✓", fontSize = 13.sp, fontWeight = FontWeight.Black, color = KkInk)
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
                Column(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(16.dp)).padding(16.dp)) {
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
                        Text(won(shownFinal), fontSize = 20.sp, fontWeight = FontWeight.Black, color = AccentBlue)
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
                BigButton(
                    if (finalizing) "보관 중…" else "계약서 보관하기",
                    enabled = qty.values.any { it > 0 } && !finalizing, bg = Kk, fg = KkInk
                ) {
                    finalizing = true
                    scope.launch {
                        repo.finalize(sid, sec)
                            .onSuccess { finalized = it }
                            .onFailure { finalizing = false; toast("보관 실패: ${it.message?.take(50)}") }
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun Stepper(q: Int, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        StepBtn("−", onMinus)
        Text("$q", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = T1, modifier = Modifier.padding(horizontal = 12.dp))
        StepBtn("+", onPlus)
    }
}

@Composable
private fun StepBtn(s: String, onClick: () -> Unit) {
    Box(Modifier.size(28.dp).background(Field, RoundedCornerShape(8.dp)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center) { Text(s, fontSize = 16.sp, fontWeight = FontWeight.Black, color = T1) }
}

@Composable
private fun MoneyField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = { onChange(it.filter { c -> c.isDigit() }) },
        label = { Text(label, fontSize = 12.sp) },
        placeholder = { Text("0", fontSize = 13.sp) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
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
private fun ColumnScope.SubmissionsView(repo: ExpoRepository, n: Nav.Subs, myPhone: String, toast: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var data by remember { mutableStateOf<ExpoRepository.Submissions?>(null) }
    var reloadTick by remember { mutableStateOf(0) }

    LaunchedEffect(n.roomId, reloadTick) {
        repo.submissions(n.roomId, myPhone).onSuccess { data = it }.onFailure { data = ExpoRepository.Submissions(0, 0L, emptyList()) }
    }

    var expanded by remember { mutableStateOf<Long?>(null) }
    val d = data
    if (d == null) {
        Box(Modifier.weight(1f).fillMaxWidth(), Alignment.Center) { CircularProgressIndicator(color = AccentBlue) }
    } else {
        Column(Modifier.weight(1f).fillMaxWidth()) {
            // 합계 헤더
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("총 ${d.count}건", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = T1)
                Spacer(Modifier.weight(1f))
                Text("합계 ${won(d.totalAmount)}", fontSize = 14.sp, fontWeight = FontWeight.Black, color = AccentBlue)
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
                        Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(14.dp))
                            .clickable { expanded = if (open) null else s.contractId }.padding(14.dp)
                    ) {
                        // 목록 = 이름 + 계약자·접수시각 + 금액 (시공내역=현장은 클릭해서 펼침)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(s.customerName.ifBlank { "고객" }, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = T1)
                                Text("계약자 ${s.agentName.ifBlank { "-" }} · ${hhmm(s.createdAtMs)}", fontSize = 11.5.sp, color = T3)
                            }
                            Text(won(s.finalAmount), fontSize = 14.sp, fontWeight = FontWeight.Black, color = AccentBlue)
                            Spacer(Modifier.width(6.dp))
                            Text(if (open) "▴" else "▾", fontSize = 12.sp, color = T3)
                        }
                        if (open) {
                            Spacer(Modifier.height(12.dp))
                            Box(Modifier.fillMaxWidth().height(1.dp).background(ExpoBg))
                            Spacer(Modifier.height(12.dp))
                            SubRow("현장", site.ifBlank { s.address.ifBlank { "-" } })   // 아파트명 동호수
                            SubRow("시공 상품", s.products.ifBlank { "-" })
                            if (s.note.isNotBlank()) SubRow("비고", s.note)
                            SubRow("시공일", if (s.scheduledAtMs > 0L) dateKo(s.scheduledAtMs) else "미정")
                            SubRow("접수", "${dateShort(s.createdAtMs)} ${hhmm(s.createdAtMs)}")
                            SubRow("전화", s.customerPhoneMasked.ifBlank { "-" })
                            Spacer(Modifier.height(12.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(
                                    Modifier.weight(1f).background(Color(0xFFEDF2FF), RoundedCornerShape(12.dp))
                                        .clickable {
                                            pickDate(ctx, s.scheduledAtMs) { picked ->
                                                scope.launch {
                                                    repo.schedule(s.contractId, myPhone, picked)
                                                        .onSuccess { toast("시공일 저장됐어요"); reloadTick++ }
                                                        .onFailure { toast("실패: ${it.message?.take(40)}") }
                                                }
                                            }
                                        }.padding(vertical = 11.dp),
                                    contentAlignment = Alignment.Center
                                ) { Text(if (s.scheduledAtMs > 0L) "시공일 변경" else "시공일 잡기", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = AccentBlue) }
                                Box(
                                    Modifier.weight(1f).background(Field, RoundedCornerShape(12.dp))
                                        .clickable { openUrl(ctx, repo.receiptUrl(s.contractId)) }.padding(vertical = 11.dp),
                                    contentAlignment = Alignment.Center
                                ) { Text("계약서 · PDF", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = T1) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SubRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, fontSize = 12.sp, color = T3, modifier = Modifier.width(66.dp))
        Text(value, fontSize = 12.5.sp, color = T1, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
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

// ══════════════ 박람회 달력 ══════════════
@Composable
private fun ColumnScope.CalendarView(repo: ExpoRepository, n: Nav.Calendar, myPhone: String) {
    val ctx = LocalContext.current
    var items by remember { mutableStateOf<List<ExpoRepository.Submission>?>(null) }
    LaunchedEffect(n.roomId) {
        repo.submissions(n.roomId, myPhone)
            .onSuccess { r -> items = r.items.filter { it.scheduledAtMs > 0L } }
            .onFailure { items = emptyList() }
    }
    val today = remember { java.util.Calendar.getInstance() }
    var year by remember { mutableStateOf(today.get(java.util.Calendar.YEAR)) }
    var month by remember { mutableStateOf(today.get(java.util.Calendar.MONTH)) }   // 0-based
    var dialogDay by remember { mutableStateOf<Int?>(null) }   // 탭한 날짜(그날 시공 목록 다이얼로그)

    val list = items
    if (list == null) {
        Box(Modifier.weight(1f).fillMaxWidth(), Alignment.Center) { CircularProgressIndicator(color = AccentBlue) }
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
                    contentAlignment = Alignment.Center) { Text("◀", fontSize = 16.sp, color = T2) }
                Spacer(Modifier.weight(1f))
                Text("${year}년 ${month + 1}월", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = T1)
                Spacer(Modifier.weight(1f))
                Box(Modifier.size(34.dp).clickable { if (month == 11) { month = 0; year++ } else month++ },
                    contentAlignment = Alignment.Center) { Text("▶", fontSize = 16.sp, color = T2) }
            }
            // 요일
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp)) {
                listOf("일", "월", "화", "수", "목", "금", "토").forEachIndexed { i, d ->
                    Text(d, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        color = if (i == 0) Color(0xFFE1483B) else T3,
                        textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                }
            }
            // 날짜 그리드 — 화면을 꽉 채워 넓게(타임트리식). 셀에 시공 텍스트를 여러 줄로 직접 표시.
            Column(Modifier.weight(1f).fillMaxWidth()) {
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
                                        .padding(horizontal = 2.dp, vertical = 3.dp)
                                ) {
                                    Text("$day", fontSize = 12.sp,
                                        fontWeight = if (isToday) FontWeight.Black else FontWeight.Medium,
                                        color = if (isToday) KkInk else if (col == 0) Color(0xFFE1483B) else T1,
                                        modifier = if (isToday) Modifier.background(Kk, RoundedCornerShape(7.dp)).padding(horizontal = 6.dp, vertical = 1.dp) else Modifier)
                                    Spacer(Modifier.height(2.dp))
                                    dayItems.take(3).forEach { s ->
                                        Text(
                                            s.apartment.ifBlank { s.customerName.ifBlank { "고객" } },
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
                        dayItems.forEach { s ->
                            val site = listOf(s.apartment, s.dongHo).filter { it.isNotBlank() }.joinToString(" ")
                            Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)
                                .clickable { openUrl(ctx, repo.receiptUrl(s.contractId)) }) {
                                Text(site.ifBlank { s.customerName.ifBlank { "고객" } }, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = T1)
                                Text("${s.customerName} · 계약자 ${s.agentName.ifBlank { "-" }} · ${won(s.finalAmount)}", fontSize = 12.sp, color = T3)
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { dialogDay = null }) { Text("닫기", color = AccentBlue, fontWeight = FontWeight.Bold) } },
                containerColor = Color.White
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
    ) { Text(text, fontSize = 15.sp, fontWeight = FontWeight.Black, color = fg) }
}

@Composable
private fun MenuCard(title: String, desc: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(18.dp)).clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = T1)
            Text(desc, fontSize = 12.sp, color = T3, fontWeight = FontWeight.Medium)
        }
        Text("›", fontSize = 22.sp, color = T3, fontWeight = FontWeight.Bold)
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
