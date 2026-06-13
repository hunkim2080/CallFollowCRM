@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.detailline.callfollowcrm.presentation.screen.team

import android.app.Activity
import android.content.Intent
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.detailline.callfollowcrm.ai.TeamRepository
import com.detailline.callfollowcrm.presentation.component.SheetFieldLabel
import com.detailline.callfollowcrm.presentation.component.SheetTextField
import com.detailline.callfollowcrm.presentation.theme.TossBlue
import com.detailline.callfollowcrm.presentation.theme.TossBlueSoft
import com.detailline.callfollowcrm.presentation.theme.TossDivider
import com.detailline.callfollowcrm.presentation.theme.TossError
import com.detailline.callfollowcrm.presentation.theme.TossGrayBg
import com.detailline.callfollowcrm.presentation.theme.TossPurple
import com.detailline.callfollowcrm.presentation.theme.TossSuccess
import com.detailline.callfollowcrm.presentation.theme.TossTextPrimary
import com.detailline.callfollowcrm.presentation.theme.TossTextSecondary
import com.detailline.callfollowcrm.presentation.theme.TossTextTertiary
import com.detailline.callfollowcrm.util.PhoneNumberFormatter
import kotlinx.coroutines.launch

/**
 * 팀 관리 화면 — 프로토 #s-team 1:1.  2026-06-05.
 *   배너 / 팀원 추가(초대링크 SMS) / 팀원 화면 미리보기(실제 링크) / 팀원 목록(밀어서 빼기) / 출발 알림.
 *   팀원은 앱 설치 X — 링크로 본인 일정만 봄. 비즈니스 요금제(서버 tier 검사).
 */
@Composable
fun TeamScreen(
    viewModel: TeamViewModel,
    notebookViewModel: com.detailline.callfollowcrm.presentation.screen.notebook.NotebookViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val members by viewModel.members.collectAsState()
    val departures by viewModel.departures.collectAsState()
    val recents by viewModel.recentNumbers.collectAsState()
    val toast by viewModel.toast.collectAsState()
    var addSheetOpen by remember { mutableStateOf(false) }
    var peopleTab by remember { mutableStateOf("team") } // "team" 팀원 | "worker" 일당사장

    LaunchedEffect(Unit) { viewModel.load() }
    LaunchedEffect(toast) {
        toast?.let { snackbar.showSnackbar(it); viewModel.consumeToast() }
    }

    // Box 로 감싸 시트를 액티비티 윈도우 안 오버레이로 → adjustResize 가 키보드 처리(채팅 입력창과 동일).
    Box(Modifier.fillMaxSize()) {
    Scaffold(
        containerColor = TossGrayBg,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("인원 관리", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "뒤로", tint = TossTextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = TossGrayBg)
            )
        }
    ) { inner ->
        Column(Modifier.padding(inner).fillMaxSize()) {
          PeopleToggle(peopleTab) { peopleTab = it }
          if (peopleTab == "worker") {
            com.detailline.callfollowcrm.presentation.screen.notebook.NotebookContent(
                notebookViewModel,
                com.detailline.callfollowcrm.presentation.screen.notebook.NotebookTab.WORKER,
                Modifier.weight(1f)
            )
          } else if (viewModel.ownerPhoneMissing) {
            OwnerMissingNotice(Modifier.weight(1f))
          } else {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
            ) {
            Spacer(Modifier.height(4.dp))
            // 프로토 .biz-banner — 보라 그라데이션
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF8B6CFF), TossPurple)))
                    .padding(18.dp)
            ) {
                Text("팀원과 함께 일해요", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                Spacer(Modifier.height(5.dp))
                Text(
                    "팀원을 초대하면 일정·현장 주소를 같이 보고, 현장 배정과 출발 알림을 받을 수 있어요. (비즈니스 요금제)",
                    fontSize = 12.5.sp, color = Color.White.copy(alpha = 0.85f), lineHeight = 18.sp
                )
            }
            Spacer(Modifier.height(14.dp))

            // 프로토 .invite — 점선 테두리 [+ 팀원 추가]
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .dashedBorder(1.5.dp, Color(0xFFC8D3E2), 16.dp)
                    .clickable { addSheetOpen = true }
                    .padding(15.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Add, null, tint = TossBlue, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(7.dp))
                Text("팀원 추가", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TossBlue)
            }
            Spacer(Modifier.height(10.dp))

            // 프로토 .lockcard — 팀원 화면 미리보기 (가장 최근 팀원 링크 열기)
            LockCardRow(
                title = "팀원 화면 미리보기",
                sub = "팀원이 링크 열면 보는 화면 (앱 설치 X)",
                onClick = {
                    val target = members.firstOrNull()
                    if (target == null) {
                        scope.launch { snackbar.showSnackbar("팀원을 먼저 추가해주세요") }
                    } else {
                        viewModel.previewLink(target) { url -> openUrl(context, url) }
                    }
                }
            )
            Spacer(Modifier.height(8.dp))

            // 프로토 .sec-sub "팀원 N명" (대표 포함)
            SecSub("팀원 ${members.size + 1}명")
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.White)
            ) {
                // 대표(사장님) — 합성 행. 제외/미리보기 X.
                MemberRow(
                    name = viewModel.ownerName,
                    roleLabel = "대표",
                    isOwner = true,
                    statusLine = PhoneNumberFormatter.format(viewModel.ownerPhone) + " · 나",
                    tintIndex = 3,
                    showDivider = members.isNotEmpty()
                )
                members.forEachIndexed { idx, m ->
                    SwipeMemberRow(
                        member = m,
                        showDivider = idx < members.size - 1,
                        onPreview = { viewModel.previewLink(m) { url -> openUrl(context, url) } },
                        onRemove = {
                            viewModel.remove(m) { undo ->
                                scope.launch {
                                    val r = snackbar.showSnackbar(
                                        message = "${m.name} 제외 · 링크 차단됨",
                                        actionLabel = "되돌리기"
                                    )
                                    if (r == SnackbarResult.ActionPerformed) undo()
                                }
                            }
                        }
                    )
                }
            }

            // 프로토 .sec-sub "오늘 출발 알림"
            SecSub("오늘 출발 알림")
            if (departures.isEmpty()) {
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White)
                        .padding(vertical = 22.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("아직 출발 알림이 없어요", fontSize = 13.sp, color = TossTextTertiary)
                }
            } else {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White)
                ) {
                    departures.forEachIndexed { idx, e ->
                        DepartAlertRow(e, showDivider = idx < departures.size - 1)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            }
          }
        }
    }

    if (addSheetOpen) {
        AddMemberOverlay(
            recents = recents,
            onDismiss = { addSheetOpen = false },
            onSubmit = { name, phone ->
                addSheetOpen = false
                viewModel.invite(name, phone) { memberPhone, draft ->
                    // 자동발송 X — 문자앱 prefill, 사장님이 ▶ 직접 발송.
                    com.detailline.callfollowcrm.util.SmsIntentHelper.openSmsCompose(context, memberPhone, draft)
                }
            }
        )
    }
    } // Box
}

/** 인원 관리 [팀원][일당사장] 세그먼트 토글. */
@Composable
private fun PeopleToggle(current: String, onSelect: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(16.dp).clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFEEF0F3)).padding(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        listOf("team" to "팀원", "worker" to "일당사장").forEach { (key, label) ->
            val on = current == key
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                    .background(if (on) Color.White else Color.Transparent)
                    .clickable { onSelect(key) }.padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(label, fontSize = 14.sp, fontWeight = if (on) FontWeight.ExtraBold else FontWeight.Medium,
                    color = if (on) TossTextPrimary else TossTextSecondary)
            }
        }
    }
}

/** 사업자 전화 미설정 안내. */
@Composable
private fun OwnerMissingNotice(modifier: Modifier) {
    Box(modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("📇 사업자 정보를 먼저 등록해주세요", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary)
            Spacer(Modifier.height(8.dp))
            Text(
                "팀원 초대 링크는 사장님 전화번호로 묶여요.\n더보기 → 견적서·사업자 정보 에서 전화번호를 등록해주세요.",
                fontSize = 13.sp, color = TossTextSecondary, lineHeight = 19.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

/** 프로토 .lockcard — 아이콘 박스 + 제목/부제 + 꺾쇠. */
@Composable
private fun LockCardRow(title: String, sub: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White)
            .clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(TossBlueSoft),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Default.PhoneAndroid, null, tint = TossBlue, modifier = Modifier.size(20.dp)) }
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary)
            Text(sub, fontSize = 12.sp, color = TossTextTertiary, modifier = Modifier.padding(top = 2.dp))
        }
        Icon(Icons.Default.ChevronRight, null, tint = TossTextTertiary, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun SecSub(text: String) {
    Text(
        text, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TossTextTertiary,
        modifier = Modifier.padding(start = 2.dp, top = 26.dp, bottom = 11.dp)
    )
}

/** 팀원 행(스와이프 제외 가능). 프로토 teamRowHtml + attachRowSwipe. */
@Composable
private fun SwipeMemberRow(
    member: TeamRepository.TeamMember,
    showDivider: Boolean,
    onPreview: () -> Unit,
    onRemove: () -> Unit
) {
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { v ->
            if (v == SwipeToDismissBoxValue.EndToStart) { onRemove() }
            false // 데이터 reload 가 행을 제거 — dismiss 안 시킴
        },
        positionalThreshold = { total -> total * 0.55f }
    )
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            Box(
                Modifier.fillMaxWidth().background(TossError.copy(alpha = 0.12f)).padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Text("제외", color = TossError, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
    ) {
        MemberRow(
            name = member.name,
            roleLabel = if (member.role == "owner") "대표" else "팀원",
            isOwner = member.role == "owner",
            statusLine = PhoneNumberFormatter.format(member.phone),
            tintIndex = member.tint,
            showDivider = showDivider,
            hint = "← 밀어서 빼기",
            onClick = onPreview
        )
    }
}

/** 팀원 한 행. 아바타 + 이름 + 역할 뱃지 + 상태. */
@Composable
private fun MemberRow(
    name: String,
    roleLabel: String,
    isOwner: Boolean,
    statusLine: String,
    tintIndex: Int,
    showDivider: Boolean,
    hint: String? = null,
    onClick: (() -> Unit)? = null
) {
    Column(Modifier.background(Color.White)) {
        Row(
            Modifier.fillMaxWidth()
                .let { if (onClick != null) it.clickable(onClick = onClick) else it }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Avatar(name, tintIndex)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary)
                    Spacer(Modifier.width(6.dp))
                    RoleBadge(roleLabel, isOwner)
                }
                Text(statusLine, fontSize = 12.sp, color = TossTextTertiary, modifier = Modifier.padding(top = 3.dp))
            }
            if (hint != null) {
                Text(
                    hint, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TossTextTertiary,
                    modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(TossGrayBg)
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
        }
        if (showDivider) Box(Modifier.fillMaxWidth().height(1.dp).background(TossDivider))
    }
}

/** 프로토 .mr / .mr.lead — 역할 뱃지. */
@Composable
private fun RoleBadge(label: String, lead: Boolean) {
    Text(
        label,
        fontSize = 11.sp, fontWeight = FontWeight.Bold,
        color = if (lead) TossBlue else TossTextSecondary,
        modifier = Modifier.clip(RoundedCornerShape(6.dp))
            .background(if (lead) TossBlueSoft else TossGrayBg)
            .padding(horizontal = 8.dp, vertical = 2.dp)
    )
}

/** 프로토 .alert-item — 출발 알림 한 행. */
@Composable
private fun DepartAlertRow(e: TeamRepository.TeamEvent, showDivider: Boolean) {
    val name = e.memberName ?: "팀원"
    val place = e.payload?.optString("customer_label")?.takeIf { it.isNotBlank() && it != "null" }
        ?: e.payload?.optString("addr")?.takeIf { it.isNotBlank() && it != "null" }
        ?: "현장"
    val time = com.detailline.callfollowcrm.util.DateTimeUtils.formatShort(e.createdAtMs)
    Column {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(36.dp).clip(RoundedCornerShape(50)).background(Color(0xFFE5F8EE)),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Default.Navigation, null, tint = Color(0xFF0E9F56), modifier = Modifier.size(18.dp)) }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    buildAnnotatedDepart(name, time, place),
                    fontSize = 13.5.sp, color = TossTextPrimary
                )
                Text(relativeAgo(e.createdAtMs), fontSize = 11.sp, color = TossTextTertiary, modifier = Modifier.padding(top = 2.dp))
            }
        }
        if (showDivider) Box(Modifier.fillMaxWidth().height(1.dp).background(TossDivider))
    }
}

private fun buildAnnotatedDepart(name: String, time: String, place: String) =
    androidx.compose.ui.text.buildAnnotatedString {
        pushStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold))
        append(name); pop()
        append("님이 $time ${place}으로 출발했어요")
    }

/**
 * 팀원 추가 오버레이 — 프로토 openAddMember + 최근 번호 고르기.
 *   ModalBottomSheet(별도 윈도우)가 갤S9/안드10 에서 키보드 대응을 못 해, 액티비티 윈도우 안 오버레이로 그림.
 *   액티비티는 adjustResize → 키보드 뜨면 영역이 줄고, 하단 정렬 카드가 위로 올라가 입력칸이 안 가림(채팅 입력창과 동일 원리).
 */
@Composable
private fun AddMemberOverlay(
    recents: List<RecentNumber>,
    onDismiss: () -> Unit,
    onSubmit: (name: String, phone: String) -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    // 전화번호 = TextFieldValue — formatProgressive 재포맷 후 커서를 항상 맨 뒤로 고정(순서 꼬임 방지).
    var phoneField by remember { mutableStateOf(TextFieldValue("")) }
    var search by remember { mutableStateOf("") }   // 최근 번호 검색어("010..." 또는 이름)
    val noRipple = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }

    val pickContact = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            res.data?.data?.let { uri ->
                runCatching {
                    context.contentResolver.query(
                        uri,
                        arrayOf(
                            ContactsContract.CommonDataKinds.Phone.NUMBER,
                            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
                        ), null, null, null
                    )?.use { cur ->
                        if (cur.moveToFirst()) {
                            val num = cur.getString(0) ?: ""
                            val nm = cur.getString(1) ?: ""
                            if (nm.isNotBlank()) name = nm
                            val f = PhoneNumberFormatter.formatProgressive(num)
                            phoneField = TextFieldValue(f, selection = TextRange(f.length))
                        }
                    }
                }
            }
        }
    }

    // 스크림(탭 시 닫힘) + 하단 정렬 카드.
    Box(
        Modifier.fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f))
            .clickable(interactionSource = noRipple, indication = null) { onDismiss() }
    ) {
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                .background(Color.White)
                .clickable(interactionSource = noRipple, indication = null) { /* 카드 탭은 닫지 않음 */ }
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(top = 8.dp, bottom = 20.dp)
                .heightIn(max = 600.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // grip
            Box(
                Modifier.align(Alignment.CenterHorizontally).padding(bottom = 12.dp)
                    .width(38.dp).height(4.dp).clip(RoundedCornerShape(999.dp)).background(TossDivider)
            )
            Text("팀원 추가", fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = TossTextPrimary)
            Spacer(Modifier.height(4.dp))
            Text("배정하면 이 번호로 현장 링크가 가요 (앱 설치 안 해도 돼요).", fontSize = 13.sp, color = TossTextTertiary)
            Spacer(Modifier.height(14.dp))

            // 최근 통화·문자 번호 — "010..." 검색해서 고름(주소록 저장 안 된 번호도). 평소 6개, 검색 시 전체에서 필터.
            if (recents.isNotEmpty()) {
                SheetFieldLabel("번호로 찾기 (최근 통화·문자)")
                SheetTextField(search, { search = it }, placeholder = "010… 입력해 찾기", keyboardType = KeyboardType.Phone)
                Spacer(Modifier.height(8.dp))
                val q = search.filter { it.isDigit() }
                val qName = search.trim()
                val filtered = if (search.isBlank()) {
                    recents.take(6)
                } else {
                    recents.filter { r ->
                        (q.isNotEmpty() && r.phone.filter { c -> c.isDigit() }.contains(q)) ||
                            (qName.isNotEmpty() && r.name?.contains(qName, ignoreCase = true) == true)
                    }.take(12)
                }
                if (filtered.isNotEmpty()) {
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(TossGrayBg)) {
                        filtered.forEachIndexed { idx, r ->
                            if (idx > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(TossDivider))
                            Row(
                                Modifier.fillMaxWidth()
                                    .clickable {
                                        if (!r.name.isNullOrBlank() && name.isBlank()) name = r.name
                                        val f = PhoneNumberFormatter.formatProgressive(r.phone)
                                        phoneField = TextFieldValue(f, selection = TextRange(f.length))
                                        search = ""
                                    }
                                    .padding(horizontal = 14.dp, vertical = 11.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Phone, null, tint = TossTextTertiary, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(9.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        r.name?.takeIf { it.isNotBlank() } ?: PhoneNumberFormatter.format(r.phone),
                                        fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary
                                    )
                                    if (!r.name.isNullOrBlank()) {
                                        Text(PhoneNumberFormatter.format(r.phone), fontSize = 12.sp, color = TossTextTertiary)
                                    }
                                }
                                Text(relativeAgo(r.lastMs), fontSize = 11.sp, color = TossTextTertiary)
                            }
                        }
                    }
                } else {
                    Text(
                        "그 번호로 통화·문자 기록이 없어요 · 아래에 직접 입력하세요",
                        fontSize = 12.5.sp, color = TossTextTertiary,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
                    )
                }
                Spacer(Modifier.height(12.dp))
            }

            Row(
                Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(12.dp)).background(TossBlueSoft)
                    .clickable {
                        runCatching {
                            pickContact.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI))
                        }
                    },
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Person, null, tint = TossBlue, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(7.dp))
                Text("연락처에서 불러오기", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TossBlue)
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f).height(1.dp).background(TossDivider))
                Text("또는 직접 입력", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TossTextTertiary, modifier = Modifier.padding(horizontal = 10.dp))
                Box(Modifier.weight(1f).height(1.dp).background(TossDivider))
            }
            SheetFieldLabel("이름")
            SheetTextField(name, { name = it }, placeholder = "예: 이기사")
            Spacer(Modifier.height(10.dp))
            SheetFieldLabel("전화번호")
            SheetTextField(
                phoneField,
                { tfv ->
                    val f = PhoneNumberFormatter.formatProgressive(tfv.text)
                    phoneField = TextFieldValue(f, selection = TextRange(f.length))
                },
                placeholder = "010-0000-0000", keyboardType = KeyboardType.Phone
            )
            Spacer(Modifier.height(16.dp))
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(TossBlue)
                    .clickable { onSubmit(name, phoneField.text) }.padding(vertical = 15.dp),
                contentAlignment = Alignment.Center
            ) { Text("팀원 추가", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold) }
        }
    }
}

/** 프로토 avatarHtml — 이니셜 아바타. */
@Composable
private fun Avatar(name: String, tintIndex: Int) {
    val (bg, fg) = AV_TINTS[((tintIndex % AV_TINTS.size) + AV_TINTS.size) % AV_TINTS.size]
    Box(
        Modifier.size(44.dp).clip(RoundedCornerShape(50)).background(bg),
        contentAlignment = Alignment.Center
    ) {
        Text(
            name.replace(Regex("[\\s()]"), "").take(1).ifBlank { "?" },
            fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = fg
        )
    }
}

// 프로토 AV_TINTS [bg, fg] 5색.
private val AV_TINTS = listOf(
    Color(0xFFE6EFFF) to Color(0xFF3182F6),
    Color(0xFFE7F8EE) to Color(0xFF16A765),
    Color(0xFFFDEAEF) to Color(0xFFF0436A),
    Color(0xFFF1ECFE) to Color(0xFF7C5CFC),
    Color(0xFFFEF3E0) to Color(0xFFE0920C),
)

/** 점선 테두리 modifier (프로토 .invite dashed border). */
private fun Modifier.dashedBorder(width: androidx.compose.ui.unit.Dp, color: Color, radius: androidx.compose.ui.unit.Dp) =
    drawBehind {
        val stroke = Stroke(
            width = width.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
        )
        drawRoundRect(color = color, style = stroke, cornerRadius = CornerRadius(radius.toPx(), radius.toPx()))
    }

private fun openUrl(context: android.content.Context, url: String) {
    if (url.isBlank()) return
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }
}

/** 간단 상대시간 — 방금/N분 전/N시간 전/어제. */
private fun relativeAgo(ms: Long): String {
    val now = System.currentTimeMillis()
    val d = now - ms
    return when {
        d < 60_000 -> "방금"
        d < 3_600_000 -> "${d / 60_000}분 전"
        d < 86_400_000 -> "${d / 3_600_000}시간 전"
        d < 172_800_000 -> "어제"
        else -> "${d / 86_400_000}일 전"
    }
}
