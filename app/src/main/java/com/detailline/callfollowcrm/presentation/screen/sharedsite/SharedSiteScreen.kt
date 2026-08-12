package com.detailline.callfollowcrm.presentation.screen.sharedsite

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.ClickableText
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.detailline.callfollowcrm.ai.SharedSiteRepository
import com.detailline.callfollowcrm.presentation.component.CollabCommentSection
import com.detailline.callfollowcrm.ai.siteDisplayName
import com.detailline.callfollowcrm.presentation.theme.TossDivider
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
    initialShareId: String? = null,
    /** "shared" 면 "내가 공유한 현장" 탭부터 연다(협업 수락/진행 알림 탭). 기본은 받은 현장. (2026-06-20 사장님) */
    initialTab: String? = null
) {
    val sites by viewModel.sites.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val toast by viewModel.toast.collectAsState()
    val photos by viewModel.photos.collectAsState()
    val photoBusy by viewModel.photoBusy.collectAsState()
    val comments by viewModel.comments.collectAsState()
    val commentBusy by viewModel.commentBusy.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var fullscreenPhoto by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var pendingUploadShareId by remember { mutableStateOf("") }
    var showPhotoPicker by remember { mutableStateOf(false) }  // 카톡식 사진첨부 바텀시트
    // 증거사진 선택 → base64 변환(IO) → 업로드. 여러 장 한 번에.
    //   2026-07-06 사장님: 시스템 Photo Picker 전환 때 여기만 단일(PickVisualMedia)로 들어가 "한 장씩" 회귀 → 다중으로 복구.
    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(10)
    ) { uris ->
        val sid = pendingUploadShareId
        if (uris.isNotEmpty() && sid.isNotBlank()) {
            scope.launch {
                var ok = 0
                for (uri in uris) {
                    val b64 = withContext(Dispatchers.IO) {
                        com.detailline.callfollowcrm.util.ImageEncoder.uriToJpegBase64(context, uri)
                    }
                    if (b64 != null) { viewModel.uploadPhotoBase64(sid, b64); ok++ }
                }
                if (ok < uris.size) {
                    android.widget.Toast.makeText(context, "일부 사진을 불러오지 못했어요 ($ok/${uris.size})", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    val accountPrompt = "입금받을 계좌를 먼저 등록해주세요. 더보기 → 견적서·사업자 정보에서 등록할 수 있어요."
    var selectedId by rememberSaveableShareId()
    // 1차 분리(2026-06-18 사장님): "received" 공유받은 현장 | "shared" 내가 공유한 현장.
    //   협업 수락/진행 알림(주인 A)으로 들어오면 initialTab="shared" → 내가 공유한 현장부터. (2026-06-20 사장님)
    var topMode by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(if (initialTab == "shared") "shared" else "received") }
    var listView by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("date") } // "date" 현장순 | "biz" 업체별
    var bizPartner by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) } // 업체별에서 고른 사장님 key
    var showTrash by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) } // 휴지통 보기
    val trashed by viewModel.trashed.collectAsState()
    // 협업 빼기 확인 대상 — 수락된(진행중) 건은 "그만두기"(상대 알림+양쪽 빠짐), 완료된 건 "정리"(내 목록만). null=닫힘. (2026-06-20 사장님)
    var confirmRemoveSite by remember { mutableStateOf<SharedSiteRepository.SharedSite?>(null) }
    // 내가(A) 공유한 현장 내리기/삭제 확인 대상. null=닫힘. (2026-06-23 사장님)
    var confirmCancelMine by remember { mutableStateOf<SharedSiteRepository.SharedSite?>(null) }
    // 완료 처리 확인 대상 — 완료를 누르면 등록한 계좌가 상대 사장님께 전달되므로 한 번 더 묻는다. null=닫힘. (2026-06-30 사장님)
    var confirmComplete by remember { mutableStateOf<SharedSiteRepository.SharedSite?>(null) }
    // 증거 사진 삭제 확인 — (shareId, 사진). null=닫힘. 내가 올린 사진만 ✕ 가 떠서 여기로 옴. (2026-07-07 사장님)
    var confirmDeletePhoto by remember { mutableStateOf<Pair<String, SharedSiteRepository.SharedPhoto>?>(null) }
    // 일당 지급(입금) 계좌 — 화면에서 인라인 등록/수정. prefs 는 비반응형이라 화면 상태로 들고 즉시 반영.
    var navChooserAddr by remember { mutableStateOf<String?>(null) } // 길찾기 앱 선택 다이얼로그(주소)
    var payoutBank by remember { mutableStateOf(viewModel.accountBank) }
    var payoutNo by remember { mutableStateOf(viewModel.accountNo) }
    var payoutHolder by remember { mutableStateOf(viewModel.accountHolder) }

    LaunchedEffect(Unit) { viewModel.load() }
    // 링크/알림으로 진입 — 목록 로드 후 그 현장 상세 1회 자동 열기(사용자가 뒤로 가면 다시 안 엶).
    //   ⚠️ 받은현장(sites)뿐 아니라 '내가 공유한'(mySharedSites)도 매칭 → 오너(A)가 댓글 알림 탭 시 오너 상세로. (2026-07-02)
    var consumedInitial by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    // 딥링크(일정 등)로 특정 협업 상세를 바로 열고 들어온 경우 → 그 상세에서 뒤로가기 = 목록이 아니라 화면 자체를
    //   닫아 호출한 곳(일정)으로 돌아가야 함. 목록에서 탭해 연 상세는 뒤로=목록(기존). (2026-07-08 사장님 버그)
    var openedViaDeepLink by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    // 거절 사유 고르기 대상 — 거절 누르면 사유 선택 시트. 만료된 요청 '지우기'는 사유 없이 바로. null=닫힘. (2026-07-08 사장님)
    var declineReasonTarget by remember { mutableStateOf<SharedSiteRepository.SharedSite?>(null) }
    LaunchedEffect(toast) {
        toast?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_SHORT).show()
            viewModel.consumeToast()
        }
    }

    val serverPartners by viewModel.partners.collectAsState()
    val mySharedSites by viewModel.mySharedSites.collectAsState()
    val selected = sites.firstOrNull { it.shareId == selectedId }
    // 내가(A) 공유한 현장을 열었을 때 — with-me 엔 없으니 by-me 에서 찾는다. 오너용 상세(댓글 중심). (2026-07-01 사장님)
    val selectedMine = if (selected == null) mySharedSites.firstOrNull { it.shareId == selectedId } else null
    // 알림/링크 진입 자동 상세 오픈 — 받은현장·내가공유한현장 둘 다 매칭. (2026-07-02 사장님)
    LaunchedEffect(sites, mySharedSites, initialShareId) {
        if (!consumedInitial && !initialShareId.isNullOrBlank() &&
            (sites.any { it.shareId == initialShareId } || mySharedSites.any { it.shareId == initialShareId })) {
            selectedId = initialShareId
            consumedInitial = true
            openedViaDeepLink = true   // 이 상세는 딥링크로 연 것 → 뒤로가기 시 화면 닫아 호출처(일정)로.
        }
    }
    // 휴지통에 넣은 건 목록·집계에서 제외. 거절(declined)/해제(ended)된 협업도 활성 목록에서 제외(기록은 서버 보존).
    val gone = setOf("declined", "ended")
    val activeSites = remember(sites, trashed) { sites.filter { it.shareId !in trashed && it.status !in gone } }
    val trashedSites = remember(sites, trashed) { sites.filter { it.shareId in trashed && it.status !in gone } }
    // 응답 안 한 요청(inbox) vs 수락한 현장 분리 — 맨 위 inbox 로 끌어올려 묻히지 않게. (2026-06-18 사장님)
    //   48h 지난 요청은 카드 자체 제거(2026-07-09 사장님). 12~48h 는 "지났어요" 상태로 계속 보임.
    val pendingSites = remember(activeSites) { activeSites.filter { it.status == "pending" && !viewModel.requestFullyExpired(it) }.sortedByDescending { it.createdAtMs } }
    val acceptedSites = remember(activeSites) { activeSites.filter { it.status != "pending" } }
    // 내가 공유한 현장(by-me) — 거절/종료 제외, 시공일 가까운 순. (2026-06-18 사장님)
    val myActiveShared = remember(mySharedSites) {
        mySharedSites.filter { it.status !in gone }.sortedByDescending { it.scheduledAtMs }
    }
    // 업체별: 서버 §B 집계 있으면 그걸로(전체 이력), 없으면 로드된 현장 로컬 그룹핑(폴백).
    val partnerGroups = remember(acceptedSites, serverPartners) {
        if (serverPartners.isNotEmpty()) serverPartners.map { p ->
            val key = p.ownerPhone.filter { it.isDigit() }.takeLast(8).ifBlank { p.ownerName }
            PartnerGroup(
                key = key,
                name = p.ownerName,
                count = p.count,
                recentMs = p.lastAtMs,
                wageSum = p.totalWage,
                sites = acceptedSites.filter { it.ownerPhone.filter { c -> c.isDigit() }.takeLast(8) == key }
                    .sortedByDescending { it.scheduledAtMs }
            )
        }.sortedByDescending { it.recentMs }
        else groupByPartner(acceptedSites)
    }
    val openPartner = partnerGroups.firstOrNull { it.key == bizPartner }
    BackHandler(enabled = selected != null || selectedMine != null || bizPartner != null || showTrash) {
        when {
            // 딥링크로 연 상세면 목록 말고 화면 자체를 닫아 호출처(일정)로. (2026-07-08 사장님)
            (selected != null || selectedMine != null) && openedViaDeepLink -> { openedViaDeepLink = false; onBack() }
            selected != null || selectedMine != null -> selectedId = null
            showTrash -> showTrash = false
            else -> bizPartner = null
        }
    }
    // 상세 열면 그 현장 증거사진·댓글 로드(받은현장·내가공유한현장 둘 다).
    LaunchedEffect(selectedId) {
        if (selectedId == null) openedViaDeepLink = false   // 상세 닫히면 딥링크 플래그도 해제(목록서 다시 탭한 건 정상 동작)
        val s = sites.firstOrNull { it.shareId == selectedId } ?: mySharedSites.firstOrNull { it.shareId == selectedId }
        if (s != null) { viewModel.loadPhotos(s.shareId); viewModel.loadComments(s.shareId) }
    }
    // 댓글 자동 새로고침(폴링) — 카톡처럼 상대 댓글이 저절로 올라오게. 상세 열린 동안 4초 간격. (2026-07-01 사장님)
    LaunchedEffect(selectedId) {
        val id = selectedId
        if (id.isNullOrBlank()) return@LaunchedEffect
        if (sites.none { it.shareId == id } && mySharedSites.none { it.shareId == id }) return@LaunchedEffect
        while (true) {
            kotlinx.coroutines.delay(4000)
            viewModel.loadComments(id)
        }
    }
    // §E: 출발 누른 현장의 3km 자동 도착 펜스 등록(위치 권한 받은 뒤).
    val locationPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val s = sites.firstOrNull { it.shareId == selectedId }
        if (granted && s != null) {
            scope.launch { com.detailline.callfollowcrm.service.GeofenceManager.registerCollabArrival(context, s.shareId, s.addr) }
            android.widget.Toast.makeText(context, "현장 3km 자동 도착이 켜졌어요", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    fun armCollabArrival(site: SharedSiteRepository.SharedSite) {
        if (site.addr.isNullOrBlank()) return
        if (com.detailline.callfollowcrm.service.GeofenceManager.hasFineLocation(context)) {
            scope.launch { com.detailline.callfollowcrm.service.GeofenceManager.registerCollabArrival(context, site.shareId, site.addr) }
        } else {
            locationPerm.launch(android.Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    Scaffold(
        containerColor = TossGrayBg,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when {
                            selected != null -> siteDisplayName(selected)
                            selectedMine != null -> siteDisplayName(selectedMine)
                            showTrash -> "휴지통"
                            openPartner != null -> openPartner.name
                            else -> "협업 현장"
                        },
                        fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        when {
                            (selected != null || selectedMine != null) && openedViaDeepLink -> { openedViaDeepLink = false; onBack() }
                            selected != null || selectedMine != null -> selectedId = null
                            showTrash -> showTrash = false
                            bizPartner != null -> bizPartner = null
                            else -> onBack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로", tint = TossTextPrimary)
                    }
                },
                actions = {
                    // 휴지통 들어가기 — 메인 목록에서 휴지통에 뭔가 있을 때만.
                    if (selected == null && selectedMine == null && !showTrash && bizPartner == null && trashedSites.isNotEmpty()) {
                        IconButton(onClick = { showTrash = true }) {
                            Icon(Icons.Outlined.Delete, "휴지통", tint = TossTextSecondary)
                            Text("${trashedSites.size}", fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                color = CollabPurple, modifier = Modifier.padding(start = 1.dp, bottom = 14.dp))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = TossGrayBg)
            )
        }
    ) { inner ->
        // 목록/상세/휴지통/탭/업체드릴다운이 스크롤 상태 하나를 공유 → 뷰 바뀔 때 이전 위치가 남아 '중간부터' 보이던 버그.
        //   뷰 정체성(아래 상태들)이 바뀌면 맨 위로. (2026-08-03 사장님 — 더보기 서브탭과 같은 부류)
        val scrollState = rememberScrollState()
        LaunchedEffect(selectedId, topMode, bizPartner, showTrash, listView) { scrollState.scrollTo(0) }
        Column(
            Modifier
                .padding(inner)
                .fillMaxSize()
                .background(TossGrayBg)
                .imePadding()
                .verticalScroll(scrollState)
                .padding(horizontal = 18.dp, vertical = 6.dp)
        ) {
            if (showTrash && selected == null && selectedMine == null) {
                TrashView(
                    trashedSites = trashedSites,
                    onRestore = {
                        viewModel.restore(it.shareId)
                        android.widget.Toast.makeText(context, "되살렸어요", android.widget.Toast.LENGTH_SHORT).show()
                    },
                    onOpen = { selectedId = it.shareId }
                )
            } else if (selected == null && selectedMine == null) {
                // 1차 분리: [공유받은 현장] [내가 공유한 현장]. 업체별 드릴다운(openPartner) 중엔 숨김. (2026-06-18 사장님)
                if (openPartner == null) {
                    CollabTopTabs(topMode, onSelect = { topMode = it; bizPartner = null })
                    Spacer(Modifier.height(12.dp))
                }
                if (topMode == "shared") {
                    MySharedArea(
                        sites = myActiveShared,
                        loading = loading,
                        noBizPhone = viewModel.noBizPhone,
                        onOpen = { selectedId = it.shareId },
                        onDelete = { confirmCancelMine = it }
                    )
                } else {
                    // 맨 위: 응답 안 한 협업 요청 inbox — 푸시 놓쳐도 여기서 바로 수락/거절. (2026-06-18 사장님)
                    if (openPartner == null && pendingSites.isNotEmpty()) {
                        PendingInbox(
                            sites = pendingSites,
                            isExpired = { viewModel.acceptExpired(it) },
                            onAccept = { site ->
                                if (viewModel.acceptExpired(site)) {
                                    android.widget.Toast.makeText(context, "수락 시간이 지났어요 — 12시간이 지나 만료됐어요", android.widget.Toast.LENGTH_LONG).show()
                                } else viewModel.respond(site, true)
                            },
                            // 거절 = 사유 고르기 시트(요청자에게 전달). 만료된 요청 '지우기'는 사유 없이 바로. (2026-07-08 사장님)
                            onReject = { site -> if (viewModel.acceptExpired(site)) viewModel.respond(site, false) else declineReasonTarget = site },
                            onOpen = { selectedId = it.shareId }
                        )
                        Spacer(Modifier.height(16.dp))
                    }
                    ListArea(
                        sites = acceptedSites,
                        loading = loading,
                        noBizPhone = viewModel.noBizPhone,
                        hasPending = pendingSites.isNotEmpty(),
                        listView = listView,
                        onListView = { listView = it; bizPartner = null },
                        partnerGroups = partnerGroups,
                        openPartner = openPartner,
                        onPickPartner = { bizPartner = it },
                        onOpen = { selectedId = it.shareId },
                        onTrash = { confirmRemoveSite = it }   // 바로 안 빼고 확인부터 — 수락된 협업은 상대에게 알림이 감. (2026-06-20 사장님)
                    )
                }
            } else if (selected != null) {
                DetailBody(
                    site = selected,
                    hasAccount = payoutNo.isNotBlank(),
                    payoutBank = payoutBank,
                    payoutNo = payoutNo,
                    payoutHolder = payoutHolder,
                    onSavePayout = { b, n, h ->
                        viewModel.saveAccount(b, n, h)
                        payoutBank = b.trim(); payoutNo = n.trim(); payoutHolder = h.trim()
                    },
                    acceptExpired = viewModel.acceptExpired(selected),
                    photos = photos,
                    photoBusy = photoBusy,
                    comments = comments,
                    commentBusy = commentBusy,
                    myPhone = viewModel.myPhoneDigits,
                    onSendComment = { body, onResult -> viewModel.postComment(selected.shareId, body, onResult) },
                    onPickPhoto = {
                        pendingUploadShareId = selected.shareId
                        showPhotoPicker = true
                    },
                    onViewPhoto = { fullscreenPhoto = it },
                    onDeletePhoto = { confirmDeletePhoto = selected.shareId to it },
                    onNavigate = { addr ->
                        // 처음이면 지도앱 선택받고 기억, 다음부터 바로 그 앱으로. (2026-06-14 사장님)
                        val saved = viewModel.navApp()
                        if (saved.isBlank()) navChooserAddr = addr
                        else com.detailline.callfollowcrm.util.NavApps.launch(context, saved, addr)
                    },
                    onProgress = { step ->
                        if (step == SharedSiteRepository.Progress.DEPARTED && isBeforeScheduledDay(selected.scheduledAtMs)) {
                            // 미래 시공의 출발을 미리 못 누르게 — 그날만 누를 수 있음. (2026-06-23 사장님)
                            android.widget.Toast.makeText(context, "아직 시공일이 아니에요. 출발은 시공 당일에 누를 수 있어요.", android.widget.Toast.LENGTH_LONG).show()
                        } else if (step == SharedSiteRepository.Progress.COMPLETED && payoutNo.isBlank()) {
                            android.widget.Toast.makeText(context, accountPrompt, android.widget.Toast.LENGTH_LONG).show()
                        } else if (step == SharedSiteRepository.Progress.COMPLETED) {
                            // 완료 = 등록한 계좌가 상대 사장님께 전달됨 → 바로 처리하지 말고 한 번 더 확인. (2026-06-30 사장님)
                            confirmComplete = selected
                        } else {
                            viewModel.updateProgress(selected, step)
                            // §E: 출발 알리면 그 현장 3km 자동 도착 켜기(권한 받고). 도착/완료면 펜스 정리.
                            when (step) {
                                SharedSiteRepository.Progress.DEPARTED -> armCollabArrival(selected)
                                SharedSiteRepository.Progress.ARRIVED,
                                SharedSiteRepository.Progress.COMPLETED ->
                                    com.detailline.callfollowcrm.service.GeofenceManager.removeCollabArrival(context, selected.shareId)
                                else -> {}
                            }
                        }
                    },
                    onRespond = { accept ->
                        when {
                            accept -> viewModel.respond(selected, true)
                            viewModel.acceptExpired(selected) -> { viewModel.respond(selected, false); selectedId = null }  // 만료 '지우기'
                            else -> declineReasonTarget = selected   // 거절 = 사유 고르기 시트
                        }
                    },
                    onLeave = { confirmRemoveSite = selected }
                )
            } else if (selectedMine != null) {
                // 내가(A) 공유한 현장 상세 — 댓글 중심(협업 사장과 논의). (2026-07-01 사장님)
                val mine = selectedMine
                OwnerSharedDetail(
                    site = mine,
                    photos = photos,
                    photoBusy = photoBusy,
                    comments = comments,
                    commentBusy = commentBusy,
                    myPhone = viewModel.myPhoneDigits,
                    onPickPhoto = { pendingUploadShareId = mine.shareId; showPhotoPicker = true },
                    onViewPhoto = { fullscreenPhoto = it },
                    onDeletePhoto = { confirmDeletePhoto = mine.shareId to it },
                    onSendComment = { body, onResult -> viewModel.postComment(mine.shareId, body, onResult) },
                    onCancel = { confirmCancelMine = mine }
                )
            }
            Spacer(Modifier.height(20.dp))
        }
    }

    // 길찾기 앱 선택 — 한 번 고르면 기억(기본값). (2026-06-14 사장님)
    navChooserAddr?.let { addr ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { navChooserAddr = null },
            title = { Text("어떤 지도로 안내할까요?", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("한 번 고르면 다음부터 바로 그 앱으로 열려요.", fontSize = 13.sp, color = TossTextTertiary)
                    Spacer(Modifier.height(10.dp))
                    com.detailline.callfollowcrm.util.NavApps.ALL.forEach { app ->
                        Box(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(TossGrayBg)
                                .clickable {
                                    viewModel.setNavApp(app)
                                    com.detailline.callfollowcrm.util.NavApps.launch(context, app, addr)
                                    navChooserAddr = null
                                }
                                .padding(vertical = 13.dp, horizontal = 14.dp)
                        ) {
                            Text(
                                com.detailline.callfollowcrm.util.NavApps.label(app),
                                fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { navChooserAddr = null }) {
                    Text("취소", color = TossTextSecondary)
                }
            }
        )
    }

    // 거절 사유 고르기 — 고르면 요청자(A)에게 전달돼 "왜 거절했는지" 알게. (2026-07-08 사장님)
    declineReasonTarget?.let { site ->
        DeclineReasonSheet(
            ownerName = site.ownerName,
            onPick = { reason ->
                viewModel.respond(site, false, reason)
                declineReasonTarget = null
                selectedId = null   // 상세에서 왔으면 닫기
            },
            onDismiss = { declineReasonTarget = null }
        )
    }

    // 협업 빼기 확인 — 수락된(진행중) 건은 그만두기(상대 알림+양쪽 빠짐), 완료된 건 정리(내 목록만). (2026-06-20 사장님)
    confirmRemoveSite?.let { s ->
        val done = s.progress == SharedSiteRepository.Progress.COMPLETED
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmRemoveSite = null },
            title = { Text(if (done) "이 현장을 정리할까요?" else "협업을 그만할까요?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    if (done) "끝난 현장이에요. 내 목록에서만 치워요. (사진·진행 기록은 남아요)"
                    else "${s.ownerName}님께 '협업을 그만뒀어요' 알림이 가요. 사진·메모·진행 기록은 그대로 남아요."
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    confirmRemoveSite = null
                    if (done) {
                        viewModel.trash(s.shareId)
                        android.widget.Toast.makeText(context, "목록에서 정리했어요 — 우상단 🗑에서 되살릴 수 있어요", android.widget.Toast.LENGTH_SHORT).show()
                    } else {
                        viewModel.leaveCollab(s)
                    }
                    if (selectedId == s.shareId) selectedId = null
                }) { Text(if (done) "정리" else "그만하기", color = Color(0xFFF0436A), fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { confirmRemoveSite = null }) {
                    Text(if (done) "그대로 둘게요" else "계속 함께", color = TossTextSecondary)
                }
            }
        )
    }

    // 내가(A) 공유한 현장 내리기/삭제 — 수락 전이면 취소, 수락 후면 상대에 해제 알림. (2026-06-23 사장님)
    confirmCancelMine?.let { s ->
        val pending = s.status == "pending"
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmCancelMine = null },
            title = { Text(if (pending) "공유를 취소할까요?" else "이 현장을 내릴까요?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    if (pending) "아직 상대가 수락 전이에요. 조용히 취소돼요. (사진·기록은 남아요)"
                    else "${s.partnerName ?: "함께하는"} 사장님께 '협업이 해제됐어요' 알림이 가요. 사진·메모·진행 기록은 그대로 남아요."
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    val site = s; confirmCancelMine = null; viewModel.cancelMyShared(site)
                }) { Text(if (pending) "취소하기" else "내리기", color = Color(0xFFF0436A), fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { confirmCancelMine = null }) {
                    Text("그대로 둘게요", color = TossTextSecondary)
                }
            }
        )
    }

    // 완료 처리 확인 — 완료로 바꾸면 등록한 계좌가 상대 사장님께 전달돼요. (2026-06-30 사장님)
    confirmComplete?.let { s ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmComplete = null },
            title = { Text("완료로 바꿀까요?", fontWeight = FontWeight.Bold) },
            text = { Text("완료로 바꾸면 등록한 계좌가 상대 사장님께 전달돼요. 진행할까요?") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    confirmComplete = null
                    viewModel.updateProgress(s, SharedSiteRepository.Progress.COMPLETED)
                    // §E: 완료면 현장 3km 자동 도착 펜스 정리.
                    com.detailline.callfollowcrm.service.GeofenceManager.removeCollabArrival(context, s.shareId)
                }) { Text("완료 처리", color = ProtoSuccess, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { confirmComplete = null }) {
                    Text("취소", color = TossTextSecondary)
                }
            }
        )
    }

    // 증거 사진 삭제 확인 — 내가 올린 사진 ✕ 탭 → 여기. 되돌릴 수 없어 한 번 더 묻는다. (2026-07-07 사장님)
    confirmDeletePhoto?.let { (sid, photo) ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmDeletePhoto = null },
            title = { Text("이 사진을 삭제할까요?", fontWeight = FontWeight.Bold) },
            text = { Text("내가 올린 증거 사진을 지워요. 되돌릴 수 없어요.") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    viewModel.deletePhoto(sid, photo.photoId)
                    confirmDeletePhoto = null
                }) { Text("삭제", color = Color(0xFFF0436A), fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { confirmDeletePhoto = null }) {
                    Text("그대로 두기", color = TossTextSecondary)
                }
            }
        )
    }

    // 증거사진 풀스크린 뷰어 — 카톡식: 전체화면 검정 배경 + 사진 가운데, 아무데나 탭하면 닫힘. (2026-07-01 사장님)
    //   usePlatformDefaultWidth=false 라야 다이얼로그가 화면 가득(예전엔 좁은 카드라 세로사진이 하단에 붙어 보였음).
    fullscreenPhoto?.let { bmp ->
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { fullscreenPhoto = null },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                Modifier.fillMaxSize().background(Color.Black)
                    .clickable { fullscreenPhoto = null },
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.foundation.Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "현장 사진",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit
                )
            }
        }
    }

    // 현장 사진 — 카톡식 바텀시트(아래서 위로 올라오는 갤러리). "파일에서"는 시스템 피커 fallback. (2026-06-14 사장님)
    // 현장 사진 첨부 = 시스템 사진 선택기(권한 없음, Play 정책). 자체 갤러리 제거. (2026-07-05)
    LaunchedEffect(showPhotoPicker) {
        if (showPhotoPicker) {
            showPhotoPicker = false
            photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
    }
}

/** 협업 현장 목록 — 프로토 b-list: 현장순 / 업체별 세그먼트. 업체별은 로드된 현장을 사장님별로 묶음. */
@Composable
private fun ListArea(
    sites: List<SharedSiteRepository.SharedSite>,
    loading: Boolean,
    noBizPhone: Boolean,
    hasPending: Boolean = false,
    listView: String,
    onListView: (String) -> Unit,
    partnerGroups: List<PartnerGroup>,
    openPartner: PartnerGroup?,
    onPickPartner: (String) -> Unit,
    onOpen: (SharedSiteRepository.SharedSite) -> Unit,
    onTrash: (SharedSiteRepository.SharedSite) -> Unit
) {
    when {
        noBizPhone -> EmptyCard(
            "먼저 사업자 전화를 등록해주세요",
            "더보기 → 견적서·사업자 정보에서 전화번호를 넣으면, 다른 사장님이 그 번호로 현장을 공유할 수 있어요."
        )
        sites.isEmpty() && loading -> EmptyCard("불러오는 중…", "")
        // 위 inbox 에 응답 안 한 요청이 있으면 큰 빈 카드 대신 가벼운 안내(모순 방지).
        sites.isEmpty() && hasPending -> Text(
            "수락하면 여기 '공유받은 현장'에 쌓여요.",
            fontSize = 12.5.sp, color = TossTextTertiary, fontWeight = FontWeight.Medium,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)
        )
        sites.isEmpty() -> EmptyCard(
            "공유받은 현장이 없어요",
            "다른 사장님이 'OO 현장 같이 하자'고 공유하면 여기에 모여요. 초대받은 현장만 보이고, 그 사장님의 다른 고객은 안 보여요."
        )
        // 업체별 → 사장님 한 명 선택: 그 사장님과 한 현장 전부 + 받은 일당 합계
        openPartner != null -> {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(CollabPurpleSoft)
                    .border(1.dp, Color(0xFFE2D8FB), RoundedCornerShape(16.dp)).padding(15.dp)
            ) {
                Text("${openPartner.name}과 함께한 현장", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF6B4FD8))
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("함께한 현장 ${openPartner.count}곳", fontSize = 12.5.sp, color = Color(0xFF5A4A7A), fontWeight = FontWeight.Medium)
                    Spacer(Modifier.weight(1f))
                    Text("받은 일당 ", fontSize = 12.sp, color = Color(0xFF5A4A7A))
                    Text("${openPartner.wageSum}만원", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = CollabPurple)
                }
            }
            Spacer(Modifier.height(6.dp))
            Text("금액은 나와 이 사장님 사이 일당만 보여요. 완료된 현장 기준이에요.",
                fontSize = 11.sp, color = TossTextTertiary, modifier = Modifier.padding(start = 2.dp, bottom = 8.dp))
            openPartner.sites.forEach { site ->
                SiteRow(site, onClick = { onOpen(site) })
                Spacer(Modifier.height(9.dp))
            }
            // 서버 전체이력 합계 > 지금 열 수 있는 현장 → 과거 현장은 합계에만 포함.
            if (openPartner.count > openPartner.sites.size) {
                Text("이전 현장 ${openPartner.count - openPartner.sites.size}곳은 합계에 포함돼요 (목록은 최근 것만 열려요).",
                    fontSize = 11.sp, color = TossTextTertiary, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 4.dp))
            }
        }
        else -> {
            SegTabs(listView, onListView)
            Spacer(Modifier.height(12.dp))
            if (listView == "date") {
                Text(
                    "다른 사장님과 같이 하는 현장이에요. 내 고객 목록과는 따로 모여요. (밀어서 휴지통)",
                    fontSize = 12.5.sp, color = TossTextTertiary, modifier = Modifier.padding(start = 2.dp, bottom = 8.dp)
                )
                sites.forEach { site ->
                    SharedSwipeBox(onDelete = { onTrash(site) }) {
                        SiteRow(site, onClick = { onOpen(site) })
                    }
                    Spacer(Modifier.height(9.dp))
                }
                Text("초대받은 현장만 보여요. 상대 사장님의 다른 고객은 안 보여요.",
                    fontSize = 11.sp, color = TossTextTertiary, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
            } else {
                Text(
                    "나를 부른 사장님별로 모았어요. 함께한 현장 수와 받은 일당 합계가 쌓여요.",
                    fontSize = 12.5.sp, color = TossTextTertiary, modifier = Modifier.padding(start = 2.dp, bottom = 8.dp)
                )
                partnerGroups.forEach { g ->
                    PartnerRow(g, onClick = { onPickPartner(g.key) })
                    Spacer(Modifier.height(9.dp))
                }
                Text("업체를 누르면 그 사장님과 한 현장이 전부 나와요.",
                    fontSize = 11.sp, color = TossTextTertiary, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
            }
        }
    }
}

/** 현장순 / 업체별 세그먼트 (프로토 .seg). */
@Composable
private fun SegTabs(current: String, onSelect: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFFEEF0F3)).padding(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        listOf("date" to "현장순", "biz" to "업체별").forEach { (key, label) ->
            val on = current == key
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                    .background(if (on) Color.White else Color.Transparent)
                    .clickable { onSelect(key) }.padding(vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(label, fontSize = 13.sp, fontWeight = if (on) FontWeight.ExtraBold else FontWeight.Medium,
                    color = if (on) TossTextPrimary else TossTextTertiary)
            }
        }
    }
}

/** 1차 분리 세그먼트 — 공유받은 현장 / 내가 공유한 현장. (2026-06-18 사장님) */
@Composable
private fun CollabTopTabs(current: String, onSelect: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFFEEF0F3)).padding(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        listOf("received" to "공유받은 현장", "shared" to "내가 공유한 현장").forEach { (key, label) ->
            val on = current == key
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                    .background(if (on) Color.White else Color.Transparent)
                    .clickable { onSelect(key) }.padding(vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(label, fontSize = 13.sp, fontWeight = if (on) FontWeight.ExtraBold else FontWeight.Medium,
                    color = if (on) TossTextPrimary else TossTextTertiary)
            }
        }
    }
}

/**
 * 응답 안 한 협업 요청 inbox — 공유받은 현장 맨 위. 푸시를 놓쳐도 여기서 바로 수락/거절. (2026-06-18 사장님)
 *   17·24일 사건(요청을 다시 볼 곳이 없어 묻힘) 해결. 카드 본문 탭 = 상세, [수락]/[거절] = 바로 응답.
 *   12h 만료(acceptExpired)면 수락 막고 "지났어요" + 거절은 '지우기'로.
 */
@Composable
private fun PendingInbox(
    sites: List<SharedSiteRepository.SharedSite>,
    isExpired: (SharedSiteRepository.SharedSite) -> Boolean,
    onAccept: (SharedSiteRepository.SharedSite) -> Unit,
    onReject: (SharedSiteRepository.SharedSite) -> Unit,
    onOpen: (SharedSiteRepository.SharedSite) -> Unit
) {
    // 헤더: 🤝 새 협업 요청 [N] · 응답 기다려요
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 2.dp, bottom = 9.dp)) {
        Text("🤝 새 협업 요청 ", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF6B4FD8))
        Box(Modifier.clip(RoundedCornerShape(999.dp)).background(CollabPurple).padding(horizontal = 7.dp, vertical = 1.dp)) {
            Text("${sites.size}", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
        }
        Spacer(Modifier.width(7.dp))
        Text("응답 기다려요", fontSize = 11.5.sp, color = TossTextTertiary, fontWeight = FontWeight.Medium)
    }
    sites.forEach { site ->
        val expired = isExpired(site)
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(CollabPurpleSoft)
                .border(1.dp, Color(0xFFE2D8FB), RoundedCornerShape(14.dp))
                .clickable { onOpen(site) }
                .padding(14.dp)
        ) {
            Text("🤝 ${site.ownerName} 사장님이 함께 하재요", fontSize = 13.5.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF6B4FD8))
            Spacer(Modifier.height(4.dp))
            val line = buildString {
                append(siteDisplayName(site)); append(" · "); append(dayLabel(site.scheduledAtMs))
                timeText(site)?.let { append(" · "); append(it) }
            }
            Text(line, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF5A4A7A))
            site.workSummary?.let {
                Spacer(Modifier.height(2.dp))
                Text(it, fontSize = 12.sp, color = Color(0xFF6B5E86), fontWeight = FontWeight.Medium,
                    maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
            // 일당 = 수락 판단에 제일 중요 → 크게(프로토 b-invite). 없으면 "미정".
            Spacer(Modifier.height(9.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color.White).padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("💰 그날 일당", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF5A4A7A))
                Spacer(Modifier.weight(1f))
                Text(
                    site.dailyWage?.let { "${it}만원" } ?: "미정",
                    fontSize = 17.sp, fontWeight = FontWeight.ExtraBold,
                    color = if (site.dailyWage != null) Color(0xFF6B4FD8) else TossTextTertiary
                )
            }
            if (expired) {
                Spacer(Modifier.height(8.dp))
                Text("⏰ 수락 시간이 지났어요 (12시간 경과) — 함께하려면 ${site.ownerName}께 다시 보내달라고 하세요.",
                    fontSize = 11.5.sp, color = com.detailline.callfollowcrm.presentation.theme.TossError, lineHeight = 16.sp)
            }
            Spacer(Modifier.height(11.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(11.dp)).background(Color.White)
                        .border(1.dp, Color(0xFFE2D8FB), RoundedCornerShape(11.dp))
                        .clickable { onReject(site) }.padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) { Text(if (expired) "지우기" else "거절", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TossTextSecondary) }
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(11.dp))
                        .background(if (expired) Color(0xFFE2E6EC) else CollabPurple)
                        .clickable { onAccept(site) }.padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) { Text("수락", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = if (expired) TossTextTertiary else Color.White) }
            }
        }
        Spacer(Modifier.height(10.dp))
    }
    Text("푸시를 놓쳐도 여기서 바로 수락·거절할 수 있어요. 수락하면 아래 '공유받은 현장'으로 들어와요.",
        fontSize = 11.sp, color = TossTextTertiary, modifier = Modifier.padding(start = 2.dp, top = 1.dp))
}

/**
 * 내가(A) 공유한 현장 모음 — 현장명 · 날짜 · 🤝 누구랑(상대방) · 진행상태. 서버 by-me. (2026-06-18 사장님)
 *   "누구랑"(partnerName)·진행상태는 상대가 수락하면 서버가 채움. 서버 미구현/실패면 빈 목록 안내.
 */
@Composable
private fun MySharedArea(
    sites: List<SharedSiteRepository.SharedSite>,
    loading: Boolean,
    noBizPhone: Boolean,
    onOpen: (SharedSiteRepository.SharedSite) -> Unit,
    onDelete: (SharedSiteRepository.SharedSite) -> Unit
) {
    when {
        noBizPhone -> EmptyCard(
            "먼저 사업자 전화를 등록해주세요",
            "더보기 → 견적서·사업자 정보에서 전화번호를 넣으면, 내 현장을 다른 사장님께 공유할 수 있어요."
        )
        sites.isEmpty() && loading -> EmptyCard("불러오는 중…", "")
        sites.isEmpty() -> EmptyCard(
            "내가 공유한 현장이 없어요",
            "고객 화면의 '협업 현장으로 공유', 일정 화면의 '전문가 배정'에서 부르면, 그 현장을 함께할 사장님과 여기에 모여요."
        )
        else -> {
            Text(
                "내가 다른 사장님께 공유한 현장이에요. 누가 함께하는지·어디까지 진행됐는지 한눈에 보여요.",
                fontSize = 12.5.sp, color = TossTextTertiary, modifier = Modifier.padding(start = 2.dp, bottom = 8.dp)
            )
            sites.forEach { site ->
                MySharedSwipeBox(onDelete = { onDelete(site) }) {
                    MySharedRow(site, onOpen = { onOpen(site) })
                }
                Spacer(Modifier.height(9.dp))
            }
            Text("탭하면 현장 상세·한마디, 밀면 '삭제'로 내려요. 함께하는 사장님 이름·진행상태는 상대가 수락하면 채워져요.",
                fontSize = 11.sp, color = TossTextTertiary, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
        }
    }
}

/** 내가 공유한 현장 우→좌 swipe → '삭제'(내리기). 드러나는 버튼 눌러야 동작. (2026-06-23 사장님) */
@Composable
private fun MySharedSwipeBox(onDelete: () -> Unit, content: @Composable () -> Unit) {
    com.detailline.callfollowcrm.presentation.component.SwipeRevealBox(onAction = onDelete, label = "삭제") { content() }
}

/** 내가 공유한 현장 1건 — 현장명 + 누구랑 + 날짜 + 상태. 탭 → 오너 상세(댓글). (2026-07-01 사장님) */
@Composable
private fun MySharedRow(site: SharedSiteRepository.SharedSite, onOpen: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White)
            .border(1.dp, Color(0xFFEEF0F3), RoundedCornerShape(14.dp)).clickable { onOpen() }.padding(13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(CollabPurpleSoft), contentAlignment = Alignment.Center) {
            Text("🤝", fontSize = 16.sp)
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(siteDisplayName(site), fontSize = 14.5.sp, fontWeight = FontWeight.ExtraBold, color = TossTextPrimary,
                    maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false))
                Spacer(Modifier.width(6.dp))
                // A측 상태: 완료 / 출발·도착(진행) / 수락 대기 / 예정
                val (stTxt, stBg, stFg) = when {
                    site.progress == SharedSiteRepository.Progress.COMPLETED -> Triple("완료", Color(0xFFE5F8EE), Color(0xFF0E9F56))
                    site.status == "pending" -> Triple("수락 대기", Color(0xFFF1ECFE), CollabPurple)
                    site.progress == SharedSiteRepository.Progress.DEPARTED -> Triple("출발", Color(0xFFEAF1FF), ProtoBlue)
                    site.progress == SharedSiteRepository.Progress.ARRIVED -> Triple("도착", Color(0xFFEAF1FF), ProtoBlue)
                    else -> Triple("예정", Color(0xFFEAF1FF), ProtoBlue)
                }
                Box(Modifier.clip(RoundedCornerShape(999.dp)).background(stBg).padding(horizontal = 8.dp, vertical = 2.dp)) {
                    Text(stTxt, fontSize = 10.5.sp, fontWeight = FontWeight.ExtraBold, color = stFg)
                }
                site.dailyWage?.let { Spacer(Modifier.width(5.dp)); WagePill(it) }
            }
            Spacer(Modifier.height(3.dp))
            // 누구랑 — 사장님 핵심 요청. partner_name(서버) 있으면 이름, 없으면 상태 안내.
            val who = site.partnerName?.let { "🤝 $it 사장님과 함께" }
                ?: if (site.status == "pending") "함께할 사장님 수락 대기 중" else "함께하는 사장님과"
            Text(who, fontSize = 12.5.sp, color = Color(0xFF6B4FD8), fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            val sub = buildString {
                append(dayLabel(site.scheduledAtMs))
                timeText(site)?.let { append(" · "); append(it) }
                site.workSummary?.let { append(" · "); append(it) }
            }
            Text(sub, fontSize = 12.sp, color = TossTextTertiary, fontWeight = FontWeight.Medium)
        }
    }
}

/** 업체별 행 — 사장님 이름 · 함께한 현장 N곳 · 최근 / 받은 일당 합계. */
@Composable
private fun PartnerRow(g: PartnerGroup, onClick: () -> Unit) {
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
            Text(g.name, fontSize = 14.5.sp, fontWeight = FontWeight.ExtraBold, color = TossTextPrimary)
            Spacer(Modifier.height(2.dp))
            Text("함께한 현장 ${g.count}곳" + (if (g.recentMs > 0L) " · 최근 ${SimpleDateFormat("MM.dd", Locale.KOREA).format(Date(g.recentMs))}" else ""),
                fontSize = 12.sp, color = TossTextTertiary, fontWeight = FontWeight.Medium)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("${g.wageSum}만원", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = CollabPurple)
            Text("받은 일당", fontSize = 11.sp, color = TossTextTertiary)
        }
        Spacer(Modifier.width(8.dp))
        Text("›", fontSize = 18.sp, color = TossTextTertiary)
    }
}

/** 협업 현장을 사장님(주인 번호)별로 묶은 그룹. 받은 일당 = 완료된 현장의 일당 합계(로드된 현장 기준). */
private data class PartnerGroup(
    val key: String,
    val name: String,
    val count: Int,
    val recentMs: Long,
    val wageSum: Int,
    val sites: List<SharedSiteRepository.SharedSite>
)

private fun groupByPartner(sites: List<SharedSiteRepository.SharedSite>): List<PartnerGroup> =
    sites.groupBy { s -> s.ownerPhone.filter { it.isDigit() }.takeLast(8).ifBlank { s.ownerName } }
        .map { (key, list) ->
            PartnerGroup(
                key = key,
                name = list.first().ownerName,
                count = list.size,
                recentMs = list.maxOf { maxOf(it.scheduledAtMs, it.createdAtMs) },
                wageSum = list.filter { it.progress == SharedSiteRepository.Progress.COMPLETED }.sumOf { it.dailyWage ?: 0 },
                sites = list.sortedByDescending { it.scheduledAtMs }
            )
        }
        .sortedByDescending { it.recentMs }

/** 협업 현장 카드 우→좌 swipe → 휴지통. 빨강 affordance. confirmValueChange=false 로 원위치(데이터가 카드 제거). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SharedSwipeBox(onDelete: () -> Unit, content: @Composable () -> Unit) {
    // 밀면 바로가 아니라 → 드러나는 '휴지통' 버튼을 눌러야 동작(2026-06-21 사장님). SwipeRevealBox 로 통일.
    com.detailline.callfollowcrm.presentation.component.SwipeRevealBox(
        onAction = onDelete,
        label = "휴지통"
    ) { content() }
}

/** 휴지통 — 밀어서 정리한 협업 현장. '되살리기'로 복구(기록 보존). */
@Composable
private fun TrashView(
    trashedSites: List<SharedSiteRepository.SharedSite>,
    onRestore: (SharedSiteRepository.SharedSite) -> Unit,
    onOpen: (SharedSiteRepository.SharedSite) -> Unit
) {
    Text("밀어서 정리한 협업 현장이에요. '되살리기'로 다시 목록에 올려요. (기록은 안 지워져요)",
        fontSize = 12.5.sp, color = TossTextTertiary, modifier = Modifier.padding(start = 2.dp, top = 2.dp, bottom = 10.dp))
    if (trashedSites.isEmpty()) {
        EmptyCard("휴지통이 비었어요", "밀어서 정리한 현장이 여기 모여요.")
    } else {
        trashedSites.forEach { site ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White)
                    .border(1.dp, Color(0xFFEEF0F3), RoundedCornerShape(14.dp)).padding(13.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f).clickable { onOpen(site) }) {
                    Text(siteDisplayName(site), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TossTextSecondary)
                    Spacer(Modifier.height(2.dp))
                    Text(site.ownerName, fontSize = 12.sp, color = TossTextTertiary)
                }
                Box(
                    Modifier.clip(RoundedCornerShape(999.dp)).background(CollabPurpleSoft)
                        .clickable { onRestore(site) }.padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text("되살리기", fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold, color = CollabPurple)
                }
            }
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
                Text(siteDisplayName(site), fontSize = 14.5.sp, fontWeight = FontWeight.ExtraBold, color = TossTextPrimary,
                    maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false))
                Spacer(Modifier.width(6.dp))
                // 상태 태그 — 완료 / 예정 / 수락 대기. (2026-06-14 사장님)
                val (stTxt, stBg, stFg) = when {
                    site.progress == SharedSiteRepository.Progress.COMPLETED -> Triple("완료", Color(0xFFE5F8EE), Color(0xFF0E9F56))
                    site.status == "pending" -> Triple("수락 대기", Color(0xFFF1ECFE), CollabPurple)
                    else -> Triple("예정", Color(0xFFEAF1FF), Color(0xFF3182F6))
                }
                Box(Modifier.clip(RoundedCornerShape(999.dp)).background(stBg).padding(horizontal = 8.dp, vertical = 2.dp)) {
                    Text(stTxt, fontSize = 10.5.sp, fontWeight = FontWeight.ExtraBold, color = stFg)
                }
                site.dailyWage?.let { Spacer(Modifier.width(5.dp)); WagePill(it) }
            }
            val sub = buildString {
                append(site.ownerName)
                append(" · "); append(dayLabel(site.scheduledAtMs))   // 날짜 없어 헷갈린다는 사장님 보고 → 날짜 추가 (2026-06-23)
                site.workSummary?.let { append(" · "); append(it) }
                timeText(site)?.let { append(" · "); append(it) }
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
    payoutBank: String,
    payoutNo: String,
    payoutHolder: String,
    onSavePayout: (bank: String, no: String, holder: String) -> Unit,
    acceptExpired: Boolean,
    photos: List<SharedSiteRepository.SharedPhoto>,
    photoBusy: Boolean,
    comments: List<SharedSiteRepository.SiteComment>,
    commentBusy: Boolean,
    myPhone: String,
    onSendComment: (String, onResult: (Boolean) -> Unit) -> Unit,
    onPickPhoto: () -> Unit,
    onViewPhoto: (android.graphics.Bitmap) -> Unit,
    onDeletePhoto: (SharedSiteRepository.SharedPhoto) -> Unit,
    onNavigate: (String) -> Unit,
    onProgress: (SharedSiteRepository.Progress) -> Unit,
    onRespond: (Boolean) -> Unit,
    onLeave: () -> Unit
) {
    val ctx = LocalContext.current
    // 협업 현장 pill + 주인
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
        PillStrong("협업 현장")
        Spacer(Modifier.width(8.dp))
        Text("${site.ownerName}과 함께", fontSize = 12.5.sp, color = TossTextTertiary, fontWeight = FontWeight.Medium)
    }
    Spacer(Modifier.height(10.dp))

    // 날짜·시공 카드
    Card {
        InfoRow("📅 날짜", buildString { append(dayLabel(site.scheduledAtMs)); timeText(site)?.let { append(" · "); append(it) } })
        site.workSummary?.let { Spacer(Modifier.height(9.dp)); InfoRow("🔧 시공", it) }
        // 수락 전(pending)엔 아래 큰 강조 박스에서 일당을 보여주므로 여기선 생략(중복 방지). 수락 후엔 여기서 표기.
        if (site.status != "pending") site.dailyWage?.let { Spacer(Modifier.height(9.dp)); InfoRow("💰 그날 일당", "${it}만원") }
    }

    // 대표님 전달사항
    if (!site.memo.isNullOrBlank()) {
        Spacer(Modifier.height(10.dp))
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                .background(Color(0xFFFFF3DF)).border(1.dp, Color(0xFFF6E4B8), RoundedCornerShape(14.dp)).padding(13.dp)
        ) {
            Text("📌 대표님 전달사항", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFB8780A))
            Spacer(Modifier.height(5.dp))
            // 전달사항 안 전화번호는 탭하면 다이얼러에 바로 채워져 통화 편하게. (2026-07-01 사장님)
            LinkifiedMemo(site.memo, baseColor = Color(0xFF5A4A1F))
        }
    }

    // 초대 수락 전(pending) — 수락/거절. 수락해야 진행 단계가 열림.
    if (site.status == "pending") {
        Spacer(Modifier.height(16.dp))
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(CollabPurpleSoft)
                .border(1.dp, Color(0xFFE2D8FB), RoundedCornerShape(14.dp)).padding(14.dp)
        ) {
            Text("🤝 ${site.ownerName} 사장님이 함께 하재요", fontSize = 13.5.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF6B4FD8))
            Spacer(Modifier.height(4.dp))
            // "이 현장" 대신 실제 현장명(주소) + 날짜를 보여줌(사장님 요청 2026-06-18·06-20).
            Text("${siteDisplayName(site)} · ${dayLabel(site.scheduledAtMs)}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF5A4A7A))
            Spacer(Modifier.height(8.dp))
            // 일당 = 수락 판단에 제일 중요 → 크게 강조(프로토 b-invite). 없으면 "미정".
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color.White).padding(horizontal = 12.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("💰 그날 일당", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF5A4A7A))
                Spacer(Modifier.weight(1f))
                Text(
                    site.dailyWage?.let { "${it}만원" } ?: "미정",
                    fontSize = 18.sp, fontWeight = FontWeight.ExtraBold,
                    color = if (site.dailyWage != null) Color(0xFF6B4FD8) else TossTextTertiary
                )
            }
            site.timeLabel?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(7.dp))
                Text("🕘 출근 $it", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF5A4A7A))
            }
            Spacer(Modifier.height(10.dp))
            if (acceptExpired) {
                // 수락 유효시간(12h) 경과 — 수락 막고 "지났어요" 안내. 거절(지우기)만 열어둠.
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFFFFF1F1)).border(1.dp, Color(0xFFF6C9C9), RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 11.dp)
                ) {
                    Text("⏰ 수락 시간이 지났어요", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = com.detailline.callfollowcrm.presentation.theme.TossError)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "보낸 지 12시간이 지나 만료됐어요. 함께 하려면 ${site.ownerName}께 다시 보내달라고 하세요.",
                        fontSize = 12.sp, color = Color(0xFF8A4B43), lineHeight = 17.sp
                    )
                }
            } else {
                Text("수락하면 내 '협업 현장'에 들어오고 진행을 같이 기록해요.", fontSize = 12.sp, color = Color(0xFF5A4A7A), lineHeight = 17.sp)
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(TossGrayBg)
                        .clickable { onRespond(false) }.padding(vertical = 13.dp),
                    contentAlignment = Alignment.Center
                ) { Text(if (acceptExpired) "지우기" else "거절", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TossTextSecondary) }
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                        .background(if (acceptExpired) Color(0xFFE2E6EC) else CollabPurple)
                        .clickable {
                            if (acceptExpired) {
                                android.widget.Toast.makeText(ctx, "수락 시간이 지났어요 — 12시간이 지나 만료됐어요", android.widget.Toast.LENGTH_LONG).show()
                            } else onRespond(true)
                        }
                        .padding(vertical = 13.dp),
                    contentAlignment = Alignment.Center
                ) { Text("수락", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = if (acceptExpired) TossTextTertiary else Color.White) }
            }
        }
        return
    }

    // 진행 상황 (눌러서 알려요) — 수락된 현장만. (계좌는 맨 아래로 이동, 2026-06-21 사장님)
    Spacer(Modifier.height(16.dp))
    SectionSub("진행 상황 (눌러서 알려요)")
    Stepper(site.progress)
    Spacer(Modifier.height(8.dp))

    val hasAddr = !site.addr.isNullOrBlank()
    when (site.progress) {
        SharedSiteRepository.Progress.ASSIGNED -> {
            // 출발 알리기 = '출발했어요' 알림만. 길찾기 자동 열기와 분리 (2026-06-26).
            //   ⚠️ 출발은 시공 당일부터 — 그 전엔 버튼을 회색(비활성처럼)으로 보여 '눌러도 될 것처럼' 오해 방지. (2026-07-06 사장님)
            //   실제 클릭 차단은 onProgress 가 그날 아니면 안내 토스트로 이미 함(억지 클릭 무해).
            val beforeDay = isBeforeScheduledDay(site.scheduledAtMs)
            StepActionButton(
                if (beforeDay) "🚗 출발 알리기 (시공 당일부터)" else "🚗 출발 알리기",
                if (beforeDay) Color(0xFFE2E6EC) else ProtoBlue,
                if (beforeDay) TossTextTertiary else Color.White
            ) {
                onProgress(SharedSiteRepository.Progress.DEPARTED)
            }
            Spacer(Modifier.height(7.dp))
            Text(
                if (beforeDay) "출발은 시공 당일에 누를 수 있어요. 당일이 되면 파랗게 켜져요."
                else "누르면 주인 사장님께 '출발했어요' 알림이 가요. 이때부터 현장 3km에 들어가면 '거의 도착'이 자동으로 가요.",
                fontSize = 11.5.sp, color = TossTextTertiary, lineHeight = 16.sp,
                modifier = Modifier.padding(horizontal = 2.dp))
        }
        SharedSiteRepository.Progress.DEPARTED -> {
            StepActionButton("📍 도착 알리기", ProtoBlue, Color.White) {
                onProgress(SharedSiteRepository.Progress.ARRIVED)
            }
            Spacer(Modifier.height(7.dp))
            Text("📍 현장 3km에 들어가면 '거의 도착'이 자동으로 가요. 자동이 안 잡히면 위 도착을 직접 눌러도 돼요.",
                fontSize = 11.5.sp, color = ProtoSuccess, lineHeight = 16.sp,
                modifier = Modifier.padding(horizontal = 2.dp))
        }
        SharedSiteRepository.Progress.ARRIVED -> {
            StepActionButton(
                if (hasAccount) "✅ 완료 알리기" else "계좌 등록 후 완료 알리기",
                if (hasAccount) ProtoSuccess else Color(0xFFE2E6EC),
                if (hasAccount) Color.White else TossTextSecondary
            ) { onProgress(SharedSiteRepository.Progress.COMPLETED) }
            Spacer(Modifier.height(7.dp))
            Text(
                if (hasAccount) "완료를 누르면 주인 사장님께 '완료 + 내 입금 계좌'가 전달돼요."
                else "⚠️ 입금받을 계좌가 없어요. 맨 아래 '일당 지급계좌'를 먼저 등록하면 완료 시 자동 전달돼요.",
                fontSize = 11.5.sp, color = if (hasAccount) TossTextTertiary else com.detailline.callfollowcrm.presentation.theme.TossError,
                lineHeight = 16.sp, modifier = Modifier.padding(horizontal = 2.dp)
            )
        }
        SharedSiteRepository.Progress.COMPLETED -> {
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(CollabPurpleSoft).padding(vertical = 13.dp),
                contentAlignment = Alignment.Center
            ) { Text("완료된 현장이에요 ✓", color = CollabPurple, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold) }
            Spacer(Modifier.height(8.dp))
            // 실수로 완료를 눌렀을 때 한 단계(도착)로 되돌리기 (2026-06-21 사장님)
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .border(1.dp, TossDivider, RoundedCornerShape(12.dp))
                    .clickable { onProgress(SharedSiteRepository.Progress.ARRIVED) }.padding(vertical = 11.dp),
                contentAlignment = Alignment.Center
            ) { Text("↩ 완료 되돌리기", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TossTextSecondary) }
        }
    }

    // 완료된 현장 = "이제 갈 곳"이 아니라 "끝나서 돈 받을 곳" → 길찾기 자리에 정산 한 줄(일당+계좌 전달)을 먼저. (2026-06-25 사장님)
    val completed = site.progress == SharedSiteRepository.Progress.COMPLETED
    if (completed) {
        Spacer(Modifier.height(16.dp))
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                .background(Color(0xFFE9F9F1)).border(1.dp, Color(0xFFBFEBD4), RoundedCornerShape(16.dp)).padding(15.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("💰 이 현장 일당", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF3A8C63))
                Spacer(Modifier.weight(1f))
                Text(
                    site.dailyWage?.let { "${it}만원" } ?: "미정",
                    fontSize = 18.sp, fontWeight = FontWeight.ExtraBold,
                    color = if (site.dailyWage != null) Color(0xFF0E9F56) else TossTextTertiary
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                if (hasAccount) "완료할 때 주인 사장님께 내 입금 계좌가 전달됐어요. 입금을 기다려요."
                else "⚠️ 계좌가 없어 전달 못 했어요. 맨 아래 '일당 지급계좌'를 등록하고 완료를 다시 눌러주세요.",
                fontSize = 11.5.sp, color = if (hasAccount) Color(0xFF3A8C63) else com.detailline.callfollowcrm.presentation.theme.TossError, lineHeight = 16.sp
            )
        }
    }

    // 현장 주소 — 큰 길찾기 버튼(2개라 조잡 + 출발 시 네비 자동열림)을 빼고, 오른쪽 위 작은 '복사' 아이콘만.
    //   주소를 복사해 원하는 지도앱에 붙여 쓰면 됨. (2026-06-26 사장님)
    if (hasAddr) {
        Spacer(Modifier.height(16.dp))
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                .background(Color(0xFFF5F9FF)).border(1.5.dp, Color(0xFFE2EDFD), RoundedCornerShape(16.dp)).padding(15.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("📍 현장 주소", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = TossTextTertiary)
                Spacer(Modifier.weight(1f))
                // 복사 아이콘 버튼 — 주소를 클립보드로.
                Row(
                    Modifier.clip(RoundedCornerShape(9.dp)).background(Color.White)
                        .border(1.dp, Color(0xFFD6E4FB), RoundedCornerShape(9.dp))
                        .clickable {
                            val clip = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            clip.setPrimaryClip(android.content.ClipData.newPlainText("현장 주소", site.addr))
                            android.widget.Toast.makeText(ctx, "주소를 복사했어요", android.widget.Toast.LENGTH_SHORT).show()
                        }
                        .padding(horizontal = 11.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("📋 복사", fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold, color = ProtoBlue)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(site.addr!!, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary, lineHeight = 22.sp)
        }
    }

    // 📸 증거 사진 (proto b-detail) — 시공 전·작업 중 상태 = "원래 그랬어요" 증거.
    Spacer(Modifier.height(18.dp))
    SectionSub("📸 현장 사진 · 증거용" + (if (photos.isNotEmpty()) " (${photos.size})" else ""))
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(Color(0xFFFFF3DF)).border(1.dp, Color(0xFFF6E4B8), RoundedCornerShape(14.dp)).padding(13.dp)
    ) {
        Text("📌 왜 찍어두나요?", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFB8780A))
        Spacer(Modifier.height(5.dp))
        Text("시공 전·작업 중 현장 상태를 미리 찍어두면 \"이건 원래 그랬어요\" 증거가 돼요. 나중에 \"여기 망가뜨렸죠?\" 같은 누명·분쟁을 막아줍니다.",
            fontSize = 13.sp, color = Color(0xFF5A4A1F), lineHeight = 20.sp)
    }
    Spacer(Modifier.height(10.dp))
    PhotoGrid(photos = photos, busy = photoBusy, onAdd = onPickPhoto, onView = onViewPhoto,
        myKind = "partner", onDelete = onDeletePhoto)   // B(협업자) 화면 → 내가 올린 = partner

    // 협업 사장끼리 현장 한 줄 논의 (2026-07-01 사장님) — 팀원 화면 코멘트처럼.
    Spacer(Modifier.height(18.dp))
    CollabCommentSection(comments = comments, myPhone = myPhone, busy = commentBusy, onSend = onSendComment)

    // 일당 지급계좌 — 맨 아래로 이동(이미 등록된 정보라 매번 위에 띄울 필요 없음). (2026-06-21 사장님)
    Spacer(Modifier.height(18.dp))
    CollabPayoutAccountSection(bank = payoutBank, no = payoutNo, holder = payoutHolder, onSave = onSavePayout)

    // 협업 그만하기 (B가 끝내기) — 사장님께 알림 + 기록 보존. 마음 안 맞을 때.
    Spacer(Modifier.height(14.dp))
    Text("협업 그만하기", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TossTextTertiary,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onLeave() }.padding(vertical = 11.dp),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
}

/**
 * 내가(A) 공유한 현장 상세 — 협업 사장과 "한 줄 논의"(댓글) 중심 + 기본 정보. (2026-07-01 사장님)
 *   B측 DetailBody 는 수락·진행 컨트롤이 있지만, A측은 이미 내 현장이라 정보 확인 + 댓글 + 내리기만.
 */
@Composable
private fun OwnerSharedDetail(
    site: SharedSiteRepository.SharedSite,
    photos: List<SharedSiteRepository.SharedPhoto>,
    photoBusy: Boolean,
    comments: List<SharedSiteRepository.SiteComment>,
    commentBusy: Boolean,
    myPhone: String,
    onPickPhoto: () -> Unit,
    onViewPhoto: (android.graphics.Bitmap) -> Unit,
    onDeletePhoto: (SharedSiteRepository.SharedPhoto) -> Unit,
    onSendComment: (String, onResult: (Boolean) -> Unit) -> Unit,
    onCancel: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
        PillStrong("내가 공유한 현장")
        Spacer(Modifier.width(8.dp))
        site.partnerName?.let { Text("🤝 ${it} 사장님과", fontSize = 12.5.sp, color = TossTextTertiary, fontWeight = FontWeight.Medium) }
    }
    Spacer(Modifier.height(10.dp))
    Card {
        InfoRow("📅 날짜", buildString { append(dayLabel(site.scheduledAtMs)); timeText(site)?.let { append(" · "); append(it) } })
        site.workSummary?.let { Spacer(Modifier.height(9.dp)); InfoRow("🔧 시공", it) }
        site.dailyWage?.let { Spacer(Modifier.height(9.dp)); InfoRow("💰 그날 일당", "${it}만원") }
    }
    site.memo?.takeIf { it.isNotBlank() }?.let { memo ->
        Spacer(Modifier.height(10.dp))
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                .background(Color(0xFFFFF3DF)).border(1.dp, Color(0xFFF6E4B8), RoundedCornerShape(14.dp)).padding(13.dp)
        ) {
            Text("📌 대표님 전달사항", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFB8780A))
            Spacer(Modifier.height(5.dp))
            LinkifiedMemo(memo, baseColor = Color(0xFF5A4A1F))
        }
    }
    val statusText = when {
        site.status == "pending" -> "상대 사장님이 아직 수락 전이에요."
        site.progress == SharedSiteRepository.Progress.COMPLETED -> "완료된 현장이에요."
        site.status == "accepted" -> "함께 진행 중인 현장이에요."
        else -> ""
    }
    if (statusText.isNotBlank()) {
        Spacer(Modifier.height(10.dp))
        Text(statusText, fontSize = 12.5.sp, color = TossTextTertiary)
    }

    // 📸 현장 사진 — 협업 사장(B)이 올린 증거사진을 A 도 여기서 보고, 필요하면 직접 추가. (2026-07-02 사장님: 사진 푸시 탭 → 여기)
    Spacer(Modifier.height(18.dp))
    SectionSub("📸 현장 사진 · 증거용" + (if (photos.isNotEmpty()) " (${photos.size})" else ""))
    PhotoGrid(photos = photos, busy = photoBusy, onAdd = onPickPhoto, onView = onViewPhoto,
        myKind = "owner", onDelete = onDeletePhoto)      // A(주인) 화면 → 내가 올린 = owner

    // 협업 사장과 한 줄 논의
    Spacer(Modifier.height(18.dp))
    CollabCommentSection(comments = comments, myPhone = myPhone, busy = commentBusy, onSend = onSendComment)

    // 이 현장 내리기(취소/해제) — DetailBody 의 '그만하기'와 같은 위치·톤.
    Spacer(Modifier.height(14.dp))
    Text("이 현장 내리기", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TossTextTertiary,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onCancel() }.padding(vertical = 11.dp),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
}

/** 증거사진 그리드 — 3열, ＋추가 셀 + 사진 셀. 프로토 .pgrid. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun PhotoGrid(
    photos: List<SharedSiteRepository.SharedPhoto>,
    busy: Boolean,
    onAdd: () -> Unit,
    onView: (android.graphics.Bitmap) -> Unit,
    myKind: String = "",                                          // "owner"(A) | "partner"(B) — 내가 올린 사진만 ✕ 삭제
    onDelete: (SharedSiteRepository.SharedPhoto) -> Unit = {}
) {
    val cellMod = Modifier.size(96.dp).clip(RoundedCornerShape(12.dp))
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // ＋ 추가 셀
        Box(
            cellMod.background(CollabPurpleSoft).border(1.dp, Color(0xFFE2D8FB), RoundedCornerShape(12.dp))
                .clickable(enabled = !busy) { onAdd() },
            contentAlignment = Alignment.Center
        ) {
            if (busy) Text("올리는 중…", fontSize = 10.sp, color = CollabPurple, fontWeight = FontWeight.Bold)
            else Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("＋", fontSize = 22.sp, color = CollabPurple, fontWeight = FontWeight.Bold)
                Text("사진", fontSize = 10.sp, color = CollabPurple, fontWeight = FontWeight.Bold)
            }
        }
        photos.forEach { p ->
            val bmp = p.bitmap
            val mine = myKind.isNotBlank() && p.uploaderKind == myKind   // 내가 올린 사진만 삭제 가능
            if (bmp != null) {
                Box(cellMod.background(Color(0xFFEDEFF3)).clickable { onView(bmp) }, contentAlignment = Alignment.BottomStart) {
                    androidx.compose.foundation.Image(
                        bitmap = bmp.asImageBitmap(), contentDescription = p.label ?: "현장 사진",
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                    // 누가 올렸는지(나/주인) 작은 칩
                    Text(
                        if (p.uploaderKind == "owner") "주인" else "나",
                        fontSize = 9.sp, color = Color.White, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(4.dp).clip(RoundedCornerShape(6.dp))
                            .background(Color(0x99000000)).padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                    // 내가 올린 사진이면 우상단 ✕ 삭제 (탭하면 확인 다이얼로그)
                    if (mine) {
                        Box(
                            Modifier.align(Alignment.TopEnd).padding(4.dp).size(22.dp)
                                .clip(RoundedCornerShape(999.dp)).background(Color(0xCC000000))
                                .clickable { onDelete(p) },
                            contentAlignment = Alignment.Center
                        ) { Text("✕", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold) }
                    }
                }
            } else {
                Box(cellMod.background(Color(0xFFEDEFF3)), contentAlignment = Alignment.Center) {
                    Text("🖼️", fontSize = 20.sp)
                }
            }
        }
    }
}

/** 진행 단계 큰 버튼(출발/도착/완료) — 공통. (2026-06-21 사장님) */
@Composable private fun StepActionButton(label: String, bg: Color, textColor: Color, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(bg)
            .clickable { onClick() }.padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) { Text(label, color = textColor, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold) }
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
        Text(text, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFB8780A))
    }
}
@Composable private fun PillStrong(text: String) {
    Box(Modifier.clip(RoundedCornerShape(999.dp)).background(CollabPurpleSoft).padding(horizontal = 10.dp, vertical = 4.dp)) {
        Text(text, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF6B4FD8))
    }
}
/**
 * 일당 지급(입금) 계좌 — 협업 수락 후 확인/등록. (2026-06-14 사장님 문구)
 *   등록됨: 마지막 계좌 보여주고 "이 계좌로 올려둘까요? 변경은 [수정]".
 *   미등록: "사장님! 아직 일당 지급 계좌가 등록이 안됐어요!" + 인라인 등록폼(은행 선택+계좌+예금주).
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun CollabPayoutAccountSection(
    bank: String,
    no: String,
    holder: String,
    onSave: (bank: String, no: String, holder: String) -> Unit
) {
    val registered = no.isNotBlank()
    var editing by remember(registered) { mutableStateOf(!registered) }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(Color(0xFFF6F3FF)).border(1.dp, Color(0xFFE2D8FB), RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        Text("💰 일이 끝난 후 일당 지급계좌를 확인해주세요!", fontSize = 13.5.sp,
            fontWeight = FontWeight.ExtraBold, color = CollabPurple)
        Spacer(Modifier.height(10.dp))
        if (registered && !editing) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color.White).padding(12.dp)) {
                Text(
                    listOfNotNull(
                        bank.takeIf { it.isNotBlank() },
                        com.detailline.callfollowcrm.presentation.component.formatAccountNo(no).takeIf { it.isNotBlank() }
                    ).joinToString("  "),
                    fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = TossTextPrimary
                )
                if (holder.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text("예금주 $holder", fontSize = 12.5.sp, color = TossTextTertiary)
                }
            }
            Spacer(Modifier.height(9.dp))
            Text("이 계좌로 올려둘까요? 변경을 원하시면 수정 버튼을 누른 후 수정해주세요!",
                fontSize = 12.5.sp, color = Color(0xFF5A4A7A), lineHeight = 18.sp)
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier.clip(RoundedCornerShape(10.dp)).background(Color.White)
                    .border(1.dp, Color(0xFFCDBEF6), RoundedCornerShape(10.dp))
                    .clickable { editing = true }.padding(horizontal = 16.dp, vertical = 9.dp)
            ) { Text("수정", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = CollabPurple) }
        } else {
            if (!registered) {
                Text("사장님! 아직 일당 지급 계좌가 등록이 안됐어요!\n아래 일당 지급계좌를 등록해주세요!",
                    fontSize = 12.5.sp, color = Color(0xFF5A4A7A), lineHeight = 18.sp)
                Spacer(Modifier.height(10.dp))
            }
            var fBank by remember { mutableStateOf(bank) }
            var fNo by remember { mutableStateOf(no) }
            var fHolder by remember { mutableStateOf(holder) }
            var bankOpen by remember { mutableStateOf(false) }
            var bankQuery by remember { mutableStateOf("") }
            var showSaveConfirm by remember { mutableStateOf(false) }
            // 계좌 수정 중 뒤로가기 = 화면 나가기가 아니라 '편집만 닫기'. 바꿨으면 저장 물어봄. (2026-06-22 사장님)
            //   registered 일 때만(보여줄 원래 화면이 있을 때). 미등록 신규 입력 땐 기본 뒤로가기.
            BackHandler(enabled = registered) {
                when {
                    bankOpen -> bankOpen = false
                    fBank.trim() != bank.trim() ||
                        fNo.filter { it.isDigit() } != no.filter { it.isDigit() } ||
                        fHolder.trim() != holder.trim() -> showSaveConfirm = true
                    else -> editing = false
                }
            }
            com.detailline.callfollowcrm.presentation.component.BankPickerField(
                label = "은행", bank = fBank, open = bankOpen, query = bankQuery,
                onToggle = { bankOpen = !bankOpen; bankQuery = "" },
                onQuery = { bankQuery = it },
                onPick = { fBank = it; bankOpen = false; bankQuery = "" }
            )
            Spacer(Modifier.height(8.dp))
            com.detailline.callfollowcrm.presentation.component.SheetFieldLabel("계좌번호")
            com.detailline.callfollowcrm.presentation.component.FormattedTextField(
                value = fNo, onValueChange = { fNo = it },
                format = { com.detailline.callfollowcrm.presentation.component.formatAccountNo(it) },
                placeholder = "예: 1234-5678-9012",
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
            )
            Spacer(Modifier.height(8.dp))
            com.detailline.callfollowcrm.presentation.component.SheetFieldLabel("예금주 (선택)")
            com.detailline.callfollowcrm.presentation.component.SheetTextField(
                fHolder, { fHolder = it }, placeholder = "비우면 내 이름"
            )
            Spacer(Modifier.height(12.dp))
            val canSave = fNo.filter { it.isDigit() }.length >= 6 && fBank.isNotBlank()
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(if (canSave) CollabPurple else TossDivider)
                    .clickable(enabled = canSave) { onSave(fBank, fNo, fHolder); editing = false }
                    .padding(vertical = 13.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(if (registered) "계좌 저장" else "이 계좌로 등록",
                    color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
            }
            if (showSaveConfirm) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { showSaveConfirm = false },
                    title = { Text("바뀐 계좌를 저장할까요?") },
                    text = { Text("수정한 내용을 저장하지 않고 닫으면 사라져요.") },
                    confirmButton = {
                        androidx.compose.material3.TextButton(onClick = {
                            onSave(fBank, fNo, fHolder); showSaveConfirm = false; editing = false
                        }) { Text("저장", color = CollabPurple, fontWeight = FontWeight.Bold) }
                    },
                    dismissButton = {
                        androidx.compose.material3.TextButton(onClick = {
                            showSaveConfirm = false; editing = false
                        }) { Text("저장 안 함", color = TossTextSecondary) }
                    }
                )
            }
        }
    }
}

@Composable private fun WagePill(wage: Int) {
    Box(Modifier.clip(RoundedCornerShape(999.dp)).background(CollabPurpleSoft).padding(horizontal = 9.dp, vertical = 3.dp)) {
        Text("일당 ${wage}만", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF6B4FD8))
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
/** 시공일이 '오늘보다 미래'면 true → 출발 버튼을 미리 못 누르게(그날만 누름). 날짜 없으면(0) 막지 않음. (2026-06-23 사장님) */
private fun isBeforeScheduledDay(scheduledAtMs: Long): Boolean {
    if (scheduledAtMs <= 0L) return false
    val sched = Calendar.getInstance().apply { timeInMillis = scheduledAtMs }
    val today = Calendar.getInstance()
    fun ymd(c: Calendar) = c.get(Calendar.YEAR) * 10000 + c.get(Calendar.MONTH) * 100 + c.get(Calendar.DAY_OF_MONTH)
    return ymd(sched) > ymd(today)
}

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

/** scheduled_at_ms 에 박힌 시각(출근시간) → "오전 9시". 자정(00:00)=미설정이면 null. 서버 time_label echo 없어도 시간 표시. */
private fun timeOf(ms: Long): String? {
    if (ms <= 0L) return null
    val cal = Calendar.getInstance().apply { timeInMillis = ms }
    val h = cal.get(Calendar.HOUR_OF_DAY); val m = cal.get(Calendar.MINUTE)
    if (h == 0 && m == 0) return null
    val ampm = if (h < 12) "오전" else "오후"
    val h12 = if (h % 12 == 0) 12 else h % 12
    return "$ampm ${h12}시" + (if (m > 0) " ${m}분" else "")
}

/** 표시용 시간 — scheduled_at_ms 에서 추출(자정=null) 우선, 없으면 의미있는 time_label. "00:00" 자정 라벨은 안 보임. */
private fun timeText(site: SharedSiteRepository.SharedSite): String? =
    timeOf(site.scheduledAtMs) ?: site.timeLabel?.takeIf { it.isNotBlank() && it != "00:00" && it != "0:00" }

@Composable
private fun rememberSaveableShareId() =
    androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }

/**
 * 전달사항 텍스트를 그리되, 한국 전화번호(010-…, 02-…, 0507… 등)는 파란 밑줄 링크로.
 *   탭하면 다이얼러에 그 번호가 채워진 채 열림(ACTION_DIAL — 통화 권한 불필요, 사용자가 통화 누름). (2026-07-01 사장님)
 */
@Composable
private fun LinkifiedMemo(memo: String, baseColor: Color) {
    val ctx = LocalContext.current
    val annotated = remember(memo) {
        // 0으로 시작하는 10~11자리(휴대폰/지역/안심번호). 구분자는 공백/하이픈 허용("010 8725 0878"·"010-8725-0878"·"01087250878").
        val re = Regex("0\\d{1,2}[\\s-]?\\d{3,4}[\\s-]?\\d{4}")
        buildAnnotatedString {
            var last = 0
            for (m in re.findAll(memo)) {
                if (m.range.first > last) append(memo.substring(last, m.range.first))
                pushStringAnnotation("tel", m.value.filter { it.isDigit() })
                withStyle(SpanStyle(color = Color(0xFF1A6DD8), fontWeight = FontWeight.Bold, textDecoration = TextDecoration.Underline)) {
                    append(m.value)
                }
                pop()
                last = m.range.last + 1
            }
            if (last < memo.length) append(memo.substring(last))
        }
    }
    ClickableText(
        text = annotated,
        style = TextStyle(color = baseColor, fontSize = 14.sp, lineHeight = 21.sp),
        onClick = { offset ->
            annotated.getStringAnnotations("tel", offset, offset).firstOrNull()?.let { ann ->
                runCatching {
                    ctx.startActivity(
                        Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + ann.item))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }
        }
    )
}

/** 거절 사유 고르기 시트 (2026-07-08 사장님) — 프리셋 2개 + 기타(메모). 고른 사유는 요청자(A)에게 전달돼 "왜 거절했지?" 궁금증 해소. */
@Composable
private fun DeclineReasonSheet(
    ownerName: String,
    onPick: (String?) -> Unit,   // 사유(기타=메모). null = 사유 없이 거절.
    onDismiss: () -> Unit
) {
    val presets = listOf("일정이 있어요. 다음에 함께 해요!", "너무 멀어요 죄송해요!")
    var etcMode by remember { mutableStateOf(false) }
    var memo by remember { mutableStateOf("") }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White).padding(20.dp)) {
            Text("거절 사유를 알려줄까요?", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = TossTextPrimary)
            Spacer(Modifier.height(5.dp))
            Text(
                "${ownerName.ifBlank { "요청한" }} 사장님께 전달돼요. 이유를 알면 서로 서운함이 없어요 🙂",
                fontSize = 12.5.sp, color = TossTextTertiary, lineHeight = 18.sp
            )
            Spacer(Modifier.height(16.dp))
            if (!etcMode) {
                presets.forEach { r ->
                    ReasonRow(r) { onPick(r) }
                    Spacer(Modifier.height(8.dp))
                }
                ReasonRow("기타 (직접 입력)") { etcMode = true }
                Spacer(Modifier.height(14.dp))
                Text(
                    "사유 없이 거절", fontSize = 13.sp, color = TossTextSecondary, fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onPick(null) }.padding(vertical = 9.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            } else {
                androidx.compose.material3.OutlinedTextField(
                    value = memo,
                    onValueChange = { memo = it.take(100) },
                    placeholder = { Text("거절 사유를 적어주세요", fontSize = 13.sp, color = TossTextTertiary) },
                    modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 4
                )
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(Color(0xFFF2F4F6)).clickable { etcMode = false }.padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) { Text("뒤로", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TossTextSecondary) }
                    val canSend = memo.isNotBlank()
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                            .background(if (canSend) CollabPurple else Color(0xFFD9D3F0))
                            .clickable(enabled = canSend) { onPick(memo.trim()) }.padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) { Text("이 사유로 거절", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = Color.White) }
                }
            }
        }
    }
}

@Composable
private fun ReasonRow(text: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFFF5F3FF)).clickable { onClick() }.padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("· $text", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4A3E7A), modifier = Modifier.weight(1f))
        Text("›", fontSize = 16.sp, color = CollabPurple, fontWeight = FontWeight.Bold)
    }
}
