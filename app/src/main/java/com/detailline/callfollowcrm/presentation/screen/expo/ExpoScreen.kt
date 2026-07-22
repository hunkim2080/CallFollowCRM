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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
        }
    }

    Column(Modifier.fillMaxSize().background(ExpoBg)) {
        // ── 카카오풍 상단 바 ──
        val title = when (val n = nav) {
            is Nav.List -> "박람회"
            is Nav.RoomView -> n.name
            is Nav.Products -> "상품·서비스 준비"
            is Nav.Qr -> "계약서 QR"
            is Nav.Subs -> "우리 팀 접수서"
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
            }
        }

        when (val n = nav) {
            is Nav.List -> RoomListView(repo, myPhone, myName, ::toast,
                onOpen = { nav = Nav.RoomView(it.roomId, it.name, it.role) })
            is Nav.RoomView -> RoomDetailView(repo, n, myPhone, myName, ::toast,
                onProducts = { nav = Nav.Products(n.roomId, n.name) },
                onQr = { nav = Nav.Qr(n.roomId, n.name, it) },
                onSubs = { nav = Nav.Subs(n.roomId, n.name) })
            is Nav.Products -> ProductsEditorView(repo, n, myPhone, ::toast,
                onDone = { nav = Nav.RoomView(n.roomId, n.name, "owner") })
            is Nav.Qr -> QrView(repo, n, myPhone, ::toast)
            is Nav.Subs -> SubmissionsView(repo, n, myPhone, ::toast)
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
    onProducts: () -> Unit, onQr: (ExpoRepository.Session) -> Unit, onSubs: () -> Unit
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

    val d = detail
    LazyColumn(
        Modifier.weight(1f).fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 방장: 초대코드 카드
        if (n.role == "owner" && code != null) item {
            Column(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(18.dp)).padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text("팀원 초대코드", fontSize = 12.sp, color = T2, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(code!!, fontSize = 40.sp, fontWeight = FontWeight.Black, color = T1, letterSpacing = 6.sp)
                Spacer(Modifier.height(10.dp))
                Box(
                    Modifier.background(Kk, RoundedCornerShape(12.dp)).clickable {
                        val share = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT,
                                "[시공막내 박람회] '${n.name}' 방 초대코드: ${code}\n앱에서 박람회 → '초대코드로 합류'에 입력하세요.")
                        }
                        ctx.startActivity(Intent.createChooser(share, "초대코드 공유").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }.padding(horizontal = 20.dp, vertical = 10.dp)
                ) { Text("코드 공유", fontSize = 14.sp, fontWeight = FontWeight.Black, color = KkInk) }
            }
        }

        // 계약서 열기 (상담원 = 모두)
        item {
            val hasCatalog = (d?.catalog?.size ?: 0) > 0
            Column(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(18.dp)).padding(16.dp)) {
                Text("고객 계약서 받기", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = T1)
                Spacer(Modifier.height(4.dp))
                Text(
                    if (hasCatalog) "QR을 띄워 고객 폰으로 찍게 하면, 고객이 상품을 고르고 서명해요."
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

        // 우리 팀 접수서
        item {
            MenuCard("우리 팀 접수서", "팀이 지금까지 받은 계약을 모아 봐요 (번호 뒷자리 가림)", onSubs)
        }

        // 방장: 상품 준비
        if (n.role == "owner") item {
            val cnt = d?.catalog?.size ?: 0
            MenuCard("상품·서비스 준비", if (cnt > 0) "등록된 항목 ${cnt}개 · 수정" else "계약서에 쓸 상품·단가를 등록하세요", onProducts)
        }

        // 팀원 목록
        if (d != null) item {
            Column(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(18.dp)).padding(16.dp)) {
                Text("팀원 ${d.members.size}명", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = T1)
                Spacer(Modifier.height(8.dp))
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

        if (d == null) item { Box(Modifier.fillMaxWidth().padding(40.dp), Alignment.Center) { CircularProgressIndicator(color = AccentBlue) } }
    }
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
    val qr = remember(n.session.qrUrl) { QrGen.bitmap(n.session.qrUrl, 640) }
    var baseline by remember { mutableStateOf<Int?>(null) }
    var newContractId by remember { mutableStateOf<Long?>(null) }

    // 제출 감지: 접수 건수가 baseline 보다 늘면 최신 계약서 = 방금 제출.
    LaunchedEffect(n.session.sessionId) {
        repo.submissions(n.roomId, myPhone).onSuccess { baseline = it.count }
        while (newContractId == null) {
            delay(3000)
            repo.submissions(n.roomId, myPhone).onSuccess { s ->
                val b = baseline
                if (b != null && s.count > b && s.items.isNotEmpty()) {
                    newContractId = s.items.maxByOrNull { it.createdAtMs }?.contractId
                }
            }
        }
    }

    Column(Modifier.weight(1f).fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        val done = newContractId
        if (done == null) {
            Spacer(Modifier.height(8.dp))
            Text("고객 폰으로 이 QR을 찍어주세요", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = T1)
            Spacer(Modifier.height(4.dp))
            Text("고객이 상품을 고르고 서명하면 자동으로 넘어가요", fontSize = 12.5.sp, color = T3, textAlign = TextAlign.Center)
            Spacer(Modifier.height(20.dp))
            Box(Modifier.background(Color.White, RoundedCornerShape(18.dp)).padding(16.dp)) {
                if (qr != null) Image(qr.asImageBitmap(), "계약서 QR", Modifier.size(260.dp))
                else Box(Modifier.size(260.dp), Alignment.Center) { Text("QR 생성 실패", color = T3) }
            }
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(color = AccentBlue, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("고객 제출을 기다리는 중…", fontSize = 12.sp, color = T2)
            }
        } else {
            Spacer(Modifier.height(30.dp))
            Text("✅", fontSize = 52.sp)
            Spacer(Modifier.height(12.dp))
            Text("계약서가 제출됐어요!", fontSize = 18.sp, fontWeight = FontWeight.Black, color = T1)
            Spacer(Modifier.height(20.dp))
            BigButton("계약서 보기", enabled = true, bg = Kk, fg = KkInk) {
                openUrl(ctx, repo.receiptUrl(done))
            }
        }
    }
}

// ══════════════ 우리 팀 접수서 ══════════════
@Composable
private fun ColumnScope.SubmissionsView(repo: ExpoRepository, n: Nav.Subs, myPhone: String, toast: (String) -> Unit) {
    val ctx = LocalContext.current
    var data by remember { mutableStateOf<ExpoRepository.Submissions?>(null) }

    LaunchedEffect(n.roomId) {
        repo.submissions(n.roomId, myPhone).onSuccess { data = it }.onFailure { data = ExpoRepository.Submissions(0, 0L, emptyList()) }
    }

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
                Column(
                    Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(14.dp))
                        .clickable { openUrl(ctx, repo.receiptUrl(s.contractId)) }.padding(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(s.customerName.ifBlank { "고객" }, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = T1)
                        Spacer(Modifier.width(8.dp))
                        Text(s.customerPhoneMasked, fontSize = 12.sp, color = T3)
                        Spacer(Modifier.weight(1f))
                        Text(won(s.finalAmount), fontSize = 14.sp, fontWeight = FontWeight.Black, color = AccentBlue)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(s.products.ifBlank { "상품 없음" }, fontSize = 12.sp, color = T2, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(2.dp))
                    Text("상담: ${s.agentName.ifBlank { "-" }}", fontSize = 11.sp, color = T3)
                }
            }
        }
    }
    }
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

private fun openUrl(ctx: android.content.Context, url: String) {
    if (url.isBlank()) return
    runCatching {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
