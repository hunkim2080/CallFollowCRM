@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.detailline.callfollowcrm.presentation.screen.chat

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import com.detailline.callfollowcrm.ai.ReplySuggestions
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.detailline.callfollowcrm.data.local.entity.MessageTemplateEntity
import com.detailline.callfollowcrm.data.repository.SmsRepository
import com.detailline.callfollowcrm.presentation.theme.TossBlue
import com.detailline.callfollowcrm.presentation.theme.TossBlueSoft
import com.detailline.callfollowcrm.presentation.theme.TossDivider
import com.detailline.callfollowcrm.presentation.theme.TossGrayBg
import com.detailline.callfollowcrm.presentation.theme.TossTextPrimary
import com.detailline.callfollowcrm.presentation.theme.TossTextSecondary
import com.detailline.callfollowcrm.presentation.theme.TossTextTertiary
import com.detailline.callfollowcrm.util.DateTimeUtils
import com.detailline.callfollowcrm.util.PhoneNumberFormatter
import kotlinx.coroutines.launch

/**
 * 대시보드 → 번호 탭의 메인 진입 화면.
 *
 * 구조 (위→아래):
 *  1. TopAppBar — ← 뒤로 / 이름·번호 / 📞 전화 / ⓘ 고객카드
 *  2. 메시지 채팅 (LazyColumn, reverseLayout=true — 최신이 아래에 표시되는 카톡 스타일)
 *  3. 템플릿 알약 가로 스크롤 — 탭하면 입력칸에 본문 채워짐 (즉시 전송 X)
 *  4. composer pill — [입력칸][▶]
 *  5. 풀스크린 이미지 뷰어 (썸네일 탭 시)
 */
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onBack: () -> Unit,
    onOpenCustomerDetail: (Long) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    val customer by viewModel.customer.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val templates by viewModel.templates.collectAsState()
    val toast by viewModel.toast.collectAsState()
    val starred by viewModel.starred.collectAsState()
    val polishing by viewModel.aiPolishing.collectAsState()
    val suggestion by viewModel.effectiveSuggestions.collectAsState()
    val suggestionsLoading by viewModel.suggestionsLoading.collectAsState()
    // 별표된 메시지 식별 키 set — ChatBubble 의 isStarred 여부 빠르게 판정
    val starredKeys = remember(starred) {
        starred.map { it.messageDateMs to it.sent }.toHashSet()
    }
    var starredViewerOpen by remember { mutableStateOf(false) }

    var input by remember { mutableStateOf("") }
    var fullscreenImageUri by remember { mutableStateOf<android.net.Uri?>(null) }
    // 권한 요청 직후 자동 재시도용 — 입력 본문을 기억.
    var pendingSend by remember { mutableStateOf<String?>(null) }
    // 사진 첨부 — Photo Picker 로 선택된 URI 들. 발송 시 갤럭시 메시지로 본문+사진 같이 전달.
    var attachedPhotos by remember { mutableStateOf<List<android.net.Uri>>(emptyList()) }

    val pickPhotos = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 5)
    ) { uris ->
        if (uris.isNotEmpty()) attachedPhotos = attachedPhotos + uris
    }

    val sendPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        val body = pendingSend
        pendingSend = null
        if (granted && body != null) {
            viewModel.sendMessage(context, body) { ok -> if (ok) input = "" }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.loadMessages()
        viewModel.loadSuggestions()
        viewModel.loadFullSummary()
    }

    LaunchedEffect(toast) {
        toast?.let {
            snackbar.showSnackbar(it)
            viewModel.consumeToast()
        }
    }

    val displayName = customer?.name?.takeIf { it.isNotBlank() }
        ?: PhoneNumberFormatter.format(viewModel.phoneNumber)
    val displayPhone = PhoneNumberFormatter.format(viewModel.phoneNumber)

    Scaffold(
        containerColor = TossGrayBg,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            displayName,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = TossTextPrimary,
                            maxLines = 1
                        )
                        if (customer?.name?.isNotBlank() == true) {
                            // 이름이 따로 있으면 작게 번호 함께 (헷갈리지 않게)
                            Text(
                                displayPhone,
                                fontSize = 11.sp,
                                color = TossTextTertiary
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "뒤로", tint = TossTextPrimary)
                    }
                },
                actions = {
                    // ⭐ 모아보기 — 카운트가 0 이면 outlined, 있으면 fill
                    IconButton(onClick = { starredViewerOpen = true }) {
                        if (starred.isEmpty()) {
                            Icon(Icons.Outlined.StarBorder, "중요 메시지", tint = TossTextSecondary)
                        } else {
                            androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Star, "중요 메시지 ${starred.size}건", tint = Color(0xFFFFAA00))
                            }
                        }
                    }
                    IconButton(onClick = { dialPhone(context, viewModel.phoneNumber) }) {
                        Icon(Icons.Default.Phone, "전화 걸기", tint = TossBlue)
                    }
                    IconButton(onClick = {
                        // Customer 없으면 upsert 후 진입 (ChatScreen 의 [ⓘ] 는 항상 진입 가능)
                        scope.launch {
                            val id = viewModel.ensureCustomerId()
                            onOpenCustomerDetail(id)
                        }
                    }) {
                        Icon(Icons.Default.Info, "고객 카드", tint = TossTextSecondary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = TossGrayBg)
            )
        }
    ) { inner ->
        Column(
            modifier = Modifier
                .padding(inner)
                .fillMaxSize()
                .background(TossGrayBg)
                .imePadding()
                .navigationBarsPadding()
        ) {
            // P0/P1/P2 — 상단 AI 요약 박스 + AI 제안 박스 (서버 응답 있을 때만 표시).
            val aiSummary by viewModel.aiSummary.collectAsState()
            aiSummary?.let { s ->
                ConversationSummaryBox(entity = s)
                NextActionBox(json = s.nextActionJson)
            }

            // 메시지 채팅 영역 — 풀 카톡 스타일. reverseLayout=true 라 newest 가 아래에 표시.
            // 그래서 messages 도 dateMs DESC 그대로 넘기면 됨 (LazyColumn 이 뒤집어 렌더).
            val listState = rememberLazyListState()
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp),
                reverseLayout = true,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                state = listState
            ) {
                if (messages.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "주고받은 문자가 없어요",
                                style = MaterialTheme.typography.bodySmall,
                                color = TossTextTertiary
                            )
                        }
                    }
                } else {
                    // key 안 줌 — SMS id 와 MMS id 가 별도 테이블이라 같은 값일 수 있어 충돌 위험.
                    items(messages) { msg ->
                        ChatBubble(
                            body = msg.body,
                            timeMs = msg.dateMs,
                            sent = msg.sent,
                            imageUris = msg.imageUris,
                            isStarred = starredKeys.contains(msg.dateMs to msg.sent),
                            onImageTap = { fullscreenImageUri = it },
                            onLongPress = {
                                viewModel.toggleStar(msg.body, msg.dateMs, msg.sent)
                            }
                        )
                    }
                }
            }

            // 템플릿 알약 (가로 스크롤) — 탭하면 입력칸에 본문 채워짐. 즉시 전송 안 함.
            if (templates.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(templates, key = { it.id }) { tpl ->
                        TemplatePill(
                            template = tpl,
                            onTap = { input = tpl.body }
                        )
                    }
                }
            }

            // AI 추천 답변 영역 — 가장 최신 메시지가 고객이 보낸 것일 때만 표시.
            // SmsReceiver 가 백그라운드에서 서버에 prepare 트리거 → ChatScreen 진입 시 fetch.
            // 없거나 stale 이면 사장님이 ↻ 로 재생성.
            val showSuggestionArea = messages.firstOrNull()?.sent == false
            if (showSuggestionArea) {
                SuggestionArea(
                    suggestion = suggestion,
                    loading = suggestionsLoading,
                    onPickSuggestion = { picked -> input = picked },
                    onRegenerate = { viewModel.regenerateSuggestions() }
                )
            }

            // composer pill — 인스타 DM 스타일 ([✨][📷][입력][▶]) + 사진 첨부 미리보기
            Composer(
                input = input,
                onChange = { input = it },
                isPolishing = polishing,
                onAiPolish = {
                    viewModel.aiPolish(input) { polished -> input = polished }
                },
                onAttachPhoto = {
                    pickPhotos.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                attachments = attachedPhotos,
                onRemoveAttachment = { uri -> attachedPhotos = attachedPhotos - uri },
                onSend = {
                    val body = input.trim()
                    // 사진 첨부 = MMS 경로. (c)안: klinker 우선 시도 + 실패 시 갤럭시 메시지 fallback.
                    if (attachedPhotos.isNotEmpty()) {
                        if (!com.detailline.callfollowcrm.util.SmsSender.hasPermission(context)) {
                            // 권한 먼저. pendingSend 에 빈 marker — 권한 받은 뒤 사장님이 다시 ▶ 누르도록 안내
                            pendingSend = body
                            sendPermissionLauncher.launch(Manifest.permission.SEND_SMS)
                            return@Composer
                        }
                        val photosSnapshot = attachedPhotos
                        viewModel.sendMessageWithPhotos(context, body, photosSnapshot) { ok ->
                            if (ok) {
                                input = ""
                                attachedPhotos = emptyList()
                            } else {
                                // klinker 실패 → 갤럭시 메시지 fallback (사장님이 거기서 직접 ▶)
                                val result = com.detailline.callfollowcrm.util.SmsIntentHelper
                                    .openSmsComposeWithAttachments(
                                        context = context,
                                        phoneNumber = viewModel.phoneNumber,
                                        body = body,
                                        attachmentUris = photosSnapshot
                                    )
                                if (result is com.detailline.callfollowcrm.util.SmsIntentHelper.Result.Opened) {
                                    input = ""
                                    attachedPhotos = emptyList()
                                }
                            }
                        }
                        return@Composer
                    }
                    if (body.isEmpty()) return@Composer
                    if (!com.detailline.callfollowcrm.util.SmsSender.hasPermission(context)) {
                        pendingSend = body
                        sendPermissionLauncher.launch(Manifest.permission.SEND_SMS)
                    } else {
                        viewModel.sendMessage(context, body) { ok -> if (ok) input = "" }
                    }
                }
            )
        }
    }

    // ⭐ 모아보기 다이얼로그 — 이 번호의 별표된 메시지만 시간순.
    if (starredViewerOpen) {
        AlertDialog(
            onDismissRequest = { starredViewerOpen = false },
            title = {
                Text(
                    "⭐ 중요 메시지 ${starred.size}건",
                    color = TossTextPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                if (starred.isEmpty()) {
                    Text(
                        "채팅 말풍선을 길게 누르면 ⭐ 표시가 돼요.\n분쟁 시 빠르게 찾을 수 있어요.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TossTextTertiary
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.height(400.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(starred) { msg ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        if (msg.sent) TossBlueSoft else TossGrayBg,
                                        RoundedCornerShape(10.dp)
                                    )
                                    .padding(10.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        if (msg.sent) "보냄" else "받음",
                                        color = if (msg.sent) TossBlue else TossTextSecondary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        DateTimeUtils.formatShort(msg.messageDateMs),
                                        color = TossTextTertiary,
                                        fontSize = 11.sp
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    msg.messageBody,
                                    color = TossTextPrimary,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { starredViewerOpen = false }) {
                    Text("닫기", color = TossTextSecondary)
                }
            },
            containerColor = Color.White
        )
    }

    // 풀스크린 이미지 뷰어 (썸네일 탭 시)
    fullscreenImageUri?.let { uri ->
        Dialog(
            onDismissRequest = { fullscreenImageUri = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .clickable { fullscreenImageUri = null },
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = uri,
                    contentDescription = "사진",
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = ContentScale.Fit
                )
                IconButton(
                    onClick = { fullscreenImageUri = null },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                ) {
                    Icon(Icons.Default.Close, "닫기", tint = Color.White)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatBubble(
    body: String,
    timeMs: Long,
    sent: Boolean,
    imageUris: List<android.net.Uri>,
    isStarred: Boolean,
    onImageTap: (android.net.Uri) -> Unit,
    onLongPress: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (sent) Arrangement.End else Arrangement.Start
    ) {
        // 보낸 메시지면 별표가 왼쪽에 (말풍선 왼쪽 = 옆), 받은 메시지면 오른쪽에 (말풍선 오른쪽 = 옆).
        // 사장님이 한눈에 어느 게 중요 표시된 건지 보이도록.
        if (sent && isStarred) {
            Icon(
                Icons.Default.Star,
                contentDescription = "중요",
                tint = Color(0xFFFFAA00),
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(4.dp))
        }
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (sent) TossBlue else TossBlueSoft,
            modifier = Modifier
                .widthIn(max = 280.dp)
                .combinedClickable(
                    onClick = {},
                    onLongClick = onLongPress
                )
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                if (imageUris.isNotEmpty()) {
                    // 한 줄에 최대 3장 썸네일. 탭하면 풀스크린.
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        imageUris.chunked(3).forEach { rowUris ->
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                rowUris.forEach { uri ->
                                    AsyncImage(
                                        model = uri,
                                        contentDescription = "첨부 사진",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(80.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(TossGrayBg)
                                            .clickable { onImageTap(uri) }
                                    )
                                }
                            }
                        }
                    }
                    if (body.isNotBlank()) Spacer(Modifier.height(6.dp))
                }
                if (body.isNotBlank()) {
                    Text(
                        body,
                        color = if (sent) Color.White else TossTextPrimary,
                        fontSize = 14.sp
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    DateTimeUtils.formatShort(timeMs),
                    color = if (sent) Color.White.copy(alpha = 0.7f) else TossTextTertiary,
                    fontSize = 10.sp
                )
            }
        }
        // 받은 메시지는 별표가 오른쪽 (보낸 메시지는 왼쪽 — 위에서 처리됨)
        if (!sent && isStarred) {
            Spacer(Modifier.width(4.dp))
            Icon(
                Icons.Default.Star,
                contentDescription = "중요",
                tint = Color(0xFFFFAA00),
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

/**
 * 고객 마지막 메시지에 대한 AI 추천 답변 영역.
 * 표시 조건: ChatScreen 진입 시 messages.firstOrNull()?.sent == false 일 때만 호출.
 * 칩 탭 → 입력칸 채워짐. ↻ → 서버에 재생성 요청 + 폴링.
 */
@Composable
private fun SuggestionArea(
    suggestion: ReplySuggestions?,
    loading: Boolean,
    onPickSuggestion: (String) -> Unit,
    onRegenerate: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "✨ AI 추천 답변",
                color = TossBlue,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.weight(1f))
            IconButton(
                onClick = onRegenerate,
                enabled = !loading,
                modifier = Modifier.size(28.dp)
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        color = TossBlue,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(16.dp)
                    )
                } else {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "재생성",
                        tint = TossTextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
        when {
            suggestion != null && suggestion.suggestions.isNotEmpty() -> {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                ) {
                    itemsIndexed(suggestion.suggestions) { idx, text ->
                        SuggestionChip(
                            index = idx + 1,
                            text = text,
                            onTap = { onPickSuggestion(text) }
                        )
                    }
                }
            }
            !loading -> {
                Text(
                    "↻ 눌러서 답변 추천 받기",
                    color = TossTextTertiary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun SuggestionChip(index: Int, text: String, onTap: () -> Unit) {
    Surface(
        modifier = Modifier
            .widthIn(max = 280.dp)
            .clickable { onTap() },
        shape = RoundedCornerShape(16.dp),
        color = TossBlue
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Text(
                "$index",
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(end = 8.dp)
            )
            Text(
                text,
                color = Color.White,
                fontSize = 13.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun TemplatePill(template: MessageTemplateEntity, onTap: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = Color.White,
        onClick = onTap
    ) {
        Box(modifier = Modifier
            .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Text(
                template.title,
                color = TossBlue,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun Composer(
    input: String,
    onChange: (String) -> Unit,
    isPolishing: Boolean,
    onAiPolish: () -> Unit,
    onAttachPhoto: () -> Unit,
    attachments: List<android.net.Uri>,
    onRemoveAttachment: (android.net.Uri) -> Unit,
    onSend: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
        // 첨부된 사진 썸네일 영역 (있을 때만)
        if (attachments.isNotEmpty()) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(attachments) { uri ->
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier.size(64.dp)
                    ) {
                        AsyncImage(
                            model = uri,
                            contentDescription = "첨부 사진",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(8.dp))
                                .background(TossGrayBg)
                        )
                        // 우측 상단 X 버튼 — 첨부 제거
                        androidx.compose.foundation.layout.Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(2.dp)
                                .size(20.dp)
                                .background(Color.Black.copy(alpha = 0.6f), androidx.compose.foundation.shape.CircleShape)
                                .clickable { onRemoveAttachment(uri) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "제거",
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }
        // composer pill
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = Color.White,
            border = androidx.compose.foundation.BorderStroke(1.dp, TossDivider)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // ✨ AI 다듬기 — polishing 중엔 회전 인디케이터로 교체 + 중복 클릭 차단
                IconButton(
                    onClick = onAiPolish,
                    enabled = !isPolishing,
                    modifier = Modifier
                        .size(40.dp)
                        .background(TossBlueSoft, RoundedCornerShape(20.dp))
                ) {
                    if (isPolishing) {
                        CircularProgressIndicator(
                            color = TossBlue,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp)
                        )
                    } else {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = "AI 다듬기",
                            tint = TossBlue,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Spacer(Modifier.width(4.dp))
                // 📷 사진 첨부 (Photo Picker)
                IconButton(
                    onClick = onAttachPhoto,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        Icons.Default.Image,
                        contentDescription = "사진 첨부",
                        tint = TossTextSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(Modifier.width(2.dp))
                BasicTextField(
                value = input,
                onValueChange = onChange,
                textStyle = TextStyle(
                    color = TossTextPrimary,
                    fontSize = 15.sp
                ),
                cursorBrush = SolidColor(TossBlue),
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 8.dp),
                decorationBox = { inner ->
                    if (input.isEmpty()) {
                        Text(
                            "메시지 입력",
                            color = TossTextTertiary,
                            fontSize = 15.sp
                        )
                    }
                    inner()
                }
            )
            Spacer(Modifier.width(8.dp))
            // 사진 첨부가 있으면 본문 없어도 발송 가능 (사진 단독 발송).
            val canSend = input.isNotBlank() || attachments.isNotEmpty()
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (canSend) TossBlue else TossDivider,
                onClick = { if (canSend) onSend() }
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 40.dp, height = 36.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.ArrowUpward,
                        contentDescription = "보내기",
                        tint = if (canSend) Color.White else TossTextTertiary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }   // Row
        }   // Surface (composer pill)
    }       // Column (composer outer)
}           // fun Composer

private fun dialPhone(context: android.content.Context, phoneNumber: String) {
    runCatching {
        val intent = android.content.Intent(android.content.Intent.ACTION_DIAL).apply {
            data = android.net.Uri.parse("tel:$phoneNumber")
        }
        context.startActivity(intent)
    }
}

/**
 * P1 — ChatScreen 상단 대화 요약 박스. 에이닷 벤치마킹.
 * conversationSummaryJson (List<String>) 표시. 박스 없으면 null 받아서 호출 측이 안 그림.
 */
@Composable
private fun ConversationSummaryBox(
    entity: com.detailline.callfollowcrm.data.local.entity.AiSummaryEntity
) {
    val lines = com.detailline.callfollowcrm.ai.parseConversationLines(entity.conversationSummaryJson)
    if (lines.isEmpty()) return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .padding(14.dp)
    ) {
        Text(
            "✨ 대화 요약",
            style = MaterialTheme.typography.labelMedium,
            color = TossBlue,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))
        lines.forEach { line ->
            Text(
                line,
                style = MaterialTheme.typography.bodyMedium,
                color = TossTextPrimary,
                modifier = Modifier.padding(vertical = 2.dp)
            )
        }
    }
}

/**
 * P2 — AI 제안 박스. nextActionJson 파싱해서 표시. 박스 없으면 안 그림.
 * urgency 별 색상: high=빨강 / medium=노랑 / low=파랑.
 */
@Composable
private fun NextActionBox(json: String?) {
    val action = com.detailline.callfollowcrm.ai.NextAction.parse(json) ?: return
    val accent = when (action.urgency) {
        "high" -> Color(0xFFEF4444)
        "medium" -> Color(0xFFF59E0B)
        else -> TossBlue
    }
    val bg = when (action.urgency) {
        "high" -> Color(0xFFFEF2F2)
        "medium" -> Color(0xFFFFF7ED)
        else -> Color(0xFFEEF4FF)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .padding(bottom = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "✨ AI 제안",
                style = MaterialTheme.typography.labelSmall,
                color = accent,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(2.dp))
            Text(
                action.title,
                style = MaterialTheme.typography.titleMedium,
                color = TossTextPrimary,
                fontWeight = FontWeight.SemiBold
            )
            action.subtitle?.let { sub ->
                Spacer(Modifier.height(2.dp))
                Text(
                    sub,
                    style = MaterialTheme.typography.bodySmall,
                    color = TossTextSecondary
                )
            }
        }
        action.primaryLabel?.let { label ->
            Spacer(Modifier.width(8.dp))
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = accent,
                onClick = { /* P3 — primary_action 별 분기. 지금은 placeholder. */ }
            ) {
                Box(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                    Text(
                        label,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}
