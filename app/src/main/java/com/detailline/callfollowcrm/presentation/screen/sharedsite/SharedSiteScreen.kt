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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.detailline.callfollowcrm.ai.SharedSiteRepository
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
    initialShareId: String? = null
) {
    val sites by viewModel.sites.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val toast by viewModel.toast.collectAsState()
    val photos by viewModel.photos.collectAsState()
    val photoBusy by viewModel.photoBusy.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var fullscreenPhoto by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var pendingUploadShareId by remember { mutableStateOf("") }
    var showPhotoPicker by remember { mutableStateOf(false) }  // 카톡식 사진첨부 바텀시트
    // 증거사진 선택 → base64 변환(IO) → 업로드. 한 장씩.
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val sid = pendingUploadShareId
        if (uri != null && sid.isNotBlank()) {
            scope.launch {
                val b64 = withContext(Dispatchers.IO) {
                    com.detailline.callfollowcrm.util.ImageEncoder.uriToJpegBase64(context, uri)
                }
                if (b64 != null) viewModel.uploadPhotoBase64(sid, b64)
                else android.widget.Toast.makeText(context, "사진을 불러오지 못했어요", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }
    val accountPrompt = "입금받을 계좌를 먼저 등록해주세요. 더보기 → 견적서·사업자 정보에서 등록할 수 있어요."
    var selectedId by rememberSaveableShareId()
    var listView by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("date") } // "date" 현장순 | "biz" 업체별
    var bizPartner by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) } // 업체별에서 고른 사장님 key
    var showTrash by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) } // 휴지통 보기
    val trashed by viewModel.trashed.collectAsState()
    var confirmLeave by remember { mutableStateOf(false) } // 협업 그만하기 확인
    // 일당 지급(입금) 계좌 — 화면에서 인라인 등록/수정. prefs 는 비반응형이라 화면 상태로 들고 즉시 반영.
    var navChooserAddr by remember { mutableStateOf<String?>(null) } // 길찾기 앱 선택 다이얼로그(주소)
    var payoutBank by remember { mutableStateOf(viewModel.accountBank) }
    var payoutNo by remember { mutableStateOf(viewModel.accountNo) }
    var payoutHolder by remember { mutableStateOf(viewModel.accountHolder) }

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

    val serverPartners by viewModel.partners.collectAsState()
    val selected = sites.firstOrNull { it.shareId == selectedId }
    // 휴지통에 넣은 건 목록·집계에서 제외. 거절(declined)/해제(ended)된 협업도 활성 목록에서 제외(기록은 서버 보존).
    val gone = setOf("declined", "ended")
    val activeSites = remember(sites, trashed) { sites.filter { it.shareId !in trashed && it.status !in gone } }
    val trashedSites = remember(sites, trashed) { sites.filter { it.shareId in trashed && it.status !in gone } }
    // 업체별: 서버 §B 집계 있으면 그걸로(전체 이력), 없으면 로드된 현장 로컬 그룹핑(폴백).
    val partnerGroups = remember(activeSites, serverPartners) {
        if (serverPartners.isNotEmpty()) serverPartners.map { p ->
            val key = p.ownerPhone.filter { it.isDigit() }.takeLast(8).ifBlank { p.ownerName }
            PartnerGroup(
                key = key,
                name = p.ownerName,
                count = p.count,
                recentMs = p.lastAtMs,
                wageSum = p.totalWage,
                sites = activeSites.filter { it.ownerPhone.filter { c -> c.isDigit() }.takeLast(8) == key }
                    .sortedByDescending { it.scheduledAtMs }
            )
        }.sortedByDescending { it.recentMs }
        else groupByPartner(activeSites)
    }
    val openPartner = partnerGroups.firstOrNull { it.key == bizPartner }
    BackHandler(enabled = selected != null || bizPartner != null || showTrash) {
        when {
            selected != null -> selectedId = null
            showTrash -> showTrash = false
            else -> bizPartner = null
        }
    }
    // 상세 열면 그 현장 증거사진 로드(닫히면 비움).
    LaunchedEffect(selected?.shareId) {
        selected?.let { viewModel.loadPhotos(it.shareId) } ?: run { /* 목록 — 비움은 다음 상세 열 때 */ }
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
                            selected != null -> selected.title
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
                            selected != null -> selectedId = null
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
                    if (selected == null && !showTrash && bizPartner == null && trashedSites.isNotEmpty()) {
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
        Column(
            Modifier
                .padding(inner)
                .fillMaxSize()
                .background(TossGrayBg)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 6.dp)
        ) {
            if (showTrash && selected == null) {
                TrashView(
                    trashedSites = trashedSites,
                    onRestore = {
                        viewModel.restore(it.shareId)
                        android.widget.Toast.makeText(context, "되살렸어요", android.widget.Toast.LENGTH_SHORT).show()
                    },
                    onOpen = { selectedId = it.shareId }
                )
            } else if (selected == null) {
                ListArea(
                    sites = activeSites,
                    loading = loading,
                    noBizPhone = viewModel.noBizPhone,
                    listView = listView,
                    onListView = { listView = it; bizPartner = null },
                    partnerGroups = partnerGroups,
                    openPartner = openPartner,
                    onPickPartner = { bizPartner = it },
                    onOpen = { selectedId = it.shareId },
                    onTrash = {
                        viewModel.trash(it.shareId)
                        android.widget.Toast.makeText(context, "휴지통에 넣었어요 — 우상단 🗑에서 되살릴 수 있어요", android.widget.Toast.LENGTH_SHORT).show()
                    }
                )
            } else {
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
                    onPickPhoto = {
                        pendingUploadShareId = selected.shareId
                        showPhotoPicker = true
                    },
                    onViewPhoto = { fullscreenPhoto = it },
                    onNavigate = { addr ->
                        // 처음이면 지도앱 선택받고 기억, 다음부터 바로 그 앱으로. (2026-06-14 사장님)
                        val saved = viewModel.navApp()
                        if (saved.isBlank()) navChooserAddr = addr
                        else com.detailline.callfollowcrm.util.NavApps.launch(context, saved, addr)
                    },
                    onProgress = { step ->
                        if (step == SharedSiteRepository.Progress.COMPLETED && payoutNo.isBlank()) {
                            android.widget.Toast.makeText(context, accountPrompt, android.widget.Toast.LENGTH_LONG).show()
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
                    onRespond = { accept -> viewModel.respond(selected, accept); if (!accept) selectedId = null },
                    onLeave = { confirmLeave = true }
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

    // 협업 그만하기 확인
    if (confirmLeave) {
        val s = selected
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text("협업을 그만할까요?", fontWeight = FontWeight.Bold) },
            text = { Text("${s?.ownerName ?: "사장님"}께 '협업을 그만뒀어요' 알림이 가요. 사진·메모·진행 기록은 그대로 남아요.") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    confirmLeave = false
                    if (s != null) { viewModel.leaveCollab(s); selectedId = null }
                }) { Text("그만하기", color = Color(0xFFF0436A), fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { confirmLeave = false }) {
                    Text("계속 함께", color = TossTextSecondary)
                }
            }
        )
    }

    // 증거사진 풀스크린 뷰어
    fullscreenPhoto?.let { bmp ->
        androidx.compose.ui.window.Dialog(onDismissRequest = { fullscreenPhoto = null }) {
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.Black)
                    .clickable { fullscreenPhoto = null },
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.foundation.Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "현장 사진",
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit
                )
            }
        }
    }

    // 현장 사진 — 카톡식 바텀시트(아래서 위로 올라오는 갤러리). "파일에서"는 시스템 피커 fallback. (2026-06-14 사장님)
    if (showPhotoPicker) {
        com.detailline.callfollowcrm.presentation.component.PhotoPickerSheet(
            maxSelectable = 10,
            onConfirm = { uris ->
                val sid = pendingUploadShareId
                showPhotoPicker = false
                if (sid.isNotBlank()) {
                    uris.forEach { uri ->
                        scope.launch {
                            val b64 = withContext(Dispatchers.IO) {
                                com.detailline.callfollowcrm.util.ImageEncoder.uriToJpegBase64(context, uri)
                            }
                            if (b64 != null) viewModel.uploadPhotoBase64(sid, b64)
                        }
                    }
                }
            },
            onDismiss = { showPhotoPicker = false },
            onOpenFiles = {
                showPhotoPicker = false
                photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
        )
    }
}

/** 협업 현장 목록 — 프로토 b-list: 현장순 / 업체별 세그먼트. 업체별은 로드된 현장을 사장님별로 묶음. */
@Composable
private fun ListArea(
    sites: List<SharedSiteRepository.SharedSite>,
    loading: Boolean,
    noBizPhone: Boolean,
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
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { v -> if (v == SwipeToDismissBoxValue.EndToStart) onDelete(); false },
        positionalThreshold = { d -> d * 0.5f }
    )
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFFFDEAEF)).padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Delete, "휴지통", tint = Color(0xFFF0436A), modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("휴지통", color = Color(0xFFF0436A), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        },
        content = { content() }
    )
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
                    Text(site.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TossTextSecondary)
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
                Text(site.title, fontSize = 14.5.sp, fontWeight = FontWeight.ExtraBold, color = TossTextPrimary)
                Spacer(Modifier.width(6.dp))
                Pill(dayLabel(site.scheduledAtMs))
                site.dailyWage?.let { Spacer(Modifier.width(5.dp)); WagePill(it) }
            }
            val sub = buildString {
                append(site.ownerName)
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
    onPickPhoto: () -> Unit,
    onViewPhoto: (android.graphics.Bitmap) -> Unit,
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
                    Text("⏰ 수락 시간이 지났어요", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFC0392B))
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
                        .background(if (acceptExpired) Color(0xFFE5E8EF) else CollabPurple)
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
        // 벽 안내만 보여주고 진행 단계는 수락 후.
        Spacer(Modifier.height(16.dp))
        WallNote(site.ownerName)
        return
    }

    // 일당 지급계좌 — 수락 후. 끝나면 이 계좌로 받음. 등록/미등록 분기로 확인·등록. (2026-06-14 사장님)
    Spacer(Modifier.height(16.dp))
    CollabPayoutAccountSection(bank = payoutBank, no = payoutNo, holder = payoutHolder, onSave = onSavePayout)

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
        if (step == SharedSiteRepository.Progress.DEPARTED) {
            Spacer(Modifier.height(7.dp))
            Text(
                "한 번 누르면 주인 사장님이 '오는구나' 알아요 — 따로 연락 안 해도 돼요.\n이때부터 위치를 봐서 현장 3km에 들어가면 '거의 도착'이 자동으로 가요. (누르기 전엔 위치 안 봐요)",
                fontSize = 11.5.sp, color = TossTextTertiary, lineHeight = 16.sp,
                modifier = Modifier.padding(horizontal = 2.dp)
            )
        }
        if (step == SharedSiteRepository.Progress.ARRIVED) {
            Spacer(Modifier.height(7.dp))
            Text(
                "📍 현장 3km에 들어가면 '거의 도착'이 자동으로 가요. 자동이 안 잡히면 위 도착을 직접 눌러도 돼요.",
                fontSize = 11.5.sp, color = ProtoSuccess, lineHeight = 16.sp,
                modifier = Modifier.padding(horizontal = 2.dp)
            )
        }
    } else {
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(CollabPurpleSoft).padding(vertical = 13.dp),
            contentAlignment = Alignment.Center
        ) { Text("완료된 현장이에요 ✓", color = CollabPurple, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold) }
    }

    // 현장 주소 — 출발 알린 뒤에 길찾기 활성화(출발→길찾기). 출발 전엔 회색 비활성. (2026-06-14 사장님)
    if (!site.addr.isNullOrBlank()) {
        Spacer(Modifier.height(16.dp))
        val canNavigate = site.progress != SharedSiteRepository.Progress.ASSIGNED
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                .background(Color(0xFFF5F9FF)).border(1.5.dp, Color(0xFFE2EDFD), RoundedCornerShape(16.dp)).padding(15.dp)
        ) {
            Text("📍 현장 주소", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = TossTextTertiary)
            Spacer(Modifier.height(7.dp))
            Text(site.addr, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary, lineHeight = 22.sp)
            Spacer(Modifier.height(11.dp))
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(if (canNavigate) ProtoBlue else Color(0xFFE5E8EF))
                    .then(if (canNavigate) Modifier.clickable { onNavigate(site.addr) } else Modifier)
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (canNavigate) "길찾기 시작" else "출발 알리면 길찾기가 켜져요",
                    color = if (canNavigate) Color.White else TossTextSecondary,
                    fontSize = 14.sp, fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }

    // 📸 증거 사진 (proto b-detail) — 시공 전·작업 중 상태 = "원래 그랬어요" 증거.
    Spacer(Modifier.height(18.dp))
    SectionSub("📸 현장 사진 · 증거용" + (if (photos.isNotEmpty()) " (${photos.size})" else ""))
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(Color(0xFFFFF8E8)).border(1.dp, Color(0xFFF6E4B8), RoundedCornerShape(14.dp)).padding(13.dp)
    ) {
        Text("📌 왜 찍어두나요?", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFB4790A))
        Spacer(Modifier.height(5.dp))
        Text("시공 전·작업 중 현장 상태(기존 깨짐·들뜸·곰팡이)를 찍어두면 \"이건 원래 그랬어요\" 증거가 돼요. 나중에 \"여기 망가뜨렸지?\" 누명·분쟁을 막아줍니다.",
            fontSize = 13.sp, color = Color(0xFF5A4A1F), lineHeight = 20.sp)
    }
    Spacer(Modifier.height(10.dp))
    PhotoGrid(photos = photos, busy = photoBusy, onAdd = onPickPhoto, onView = onViewPhoto)

    // 벽 안내
    Spacer(Modifier.height(16.dp))
    WallNote(site.ownerName)

    // 협업 그만하기 (B가 끝내기) — 사장님께 알림 + 기록 보존. 마음 안 맞을 때.
    Spacer(Modifier.height(14.dp))
    Text("협업 그만하기", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TossTextTertiary,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onLeave() }.padding(vertical = 11.dp),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
}

/** 증거사진 그리드 — 3열, ＋추가 셀 + 사진 셀. 프로토 .pgrid. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun PhotoGrid(
    photos: List<SharedSiteRepository.SharedPhoto>,
    busy: Boolean,
    onAdd: () -> Unit,
    onView: (android.graphics.Bitmap) -> Unit
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
                }
            } else {
                Box(cellMod.background(Color(0xFFEDEFF3)), contentAlignment = Alignment.Center) {
                    Text("🖼️", fontSize = 20.sp)
                }
            }
        }
    }
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
                    listOfNotNull(bank.takeIf { it.isNotBlank() }, no.takeIf { it.isNotBlank() }).joinToString("  "),
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
