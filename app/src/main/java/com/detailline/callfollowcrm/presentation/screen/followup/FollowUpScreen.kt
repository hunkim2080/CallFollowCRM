package com.detailline.callfollowcrm.presentation.screen.followup

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.detailline.callfollowcrm.util.PermissionHelper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.detailline.callfollowcrm.util.DateTimeUtils
import com.detailline.callfollowcrm.util.PhoneNumberFormatter
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.detailline.callfollowcrm.domain.model.CustomerStatus
import com.detailline.callfollowcrm.presentation.component.SectionLabel
import com.detailline.callfollowcrm.presentation.component.TossCard
import com.detailline.callfollowcrm.presentation.component.TossChip
import com.detailline.callfollowcrm.presentation.component.TossPrimaryButton
import com.detailline.callfollowcrm.presentation.component.TossSecondaryButton
import com.detailline.callfollowcrm.presentation.theme.TossBlue
import com.detailline.callfollowcrm.presentation.theme.TossDivider
import com.detailline.callfollowcrm.presentation.theme.TossGrayBg
import com.detailline.callfollowcrm.presentation.theme.TossTextPrimary
import com.detailline.callfollowcrm.presentation.theme.TossTextSecondary
import com.detailline.callfollowcrm.presentation.theme.TossTextTertiary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FollowUpScreen(
    viewModel: FollowUpViewModel,
    onDone: () -> Unit,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val templates by viewModel.templates.collectAsState()
    val attachments by viewModel.attachments.collectAsState()
    val recentCalls by viewModel.recentCalls.collectAsState()
    val recentSms by viewModel.recentSms.collectAsState()
    val toast by viewModel.toast.collectAsState()
    val context = LocalContext.current
    var recentDialogOpen by remember { mutableStateOf(false) }
    var smsDialogOpen by remember { mutableStateOf(false) }
    var overflowOpen by remember { mutableStateOf(false) }
    var firstMetPickerOpen by remember { mutableStateOf(false) }

    val callLogPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.loadRecentCalls(context)
        // granted/denied 둘 다 다이얼로그 열어 결과 안내. 거부 시엔 "설정 열기" 버튼 노출.
        recentDialogOpen = true
    }

    val smsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.loadRecentSmsContacts()
        smsDialogOpen = true
    }

    fun openAppSettings() {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null)
        ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        context.startActivity(intent)
    }
    // 폰번 입력 커서를 항상 맨 뒤로 보내기 위해 TextFieldValue 사용.
    // 진행형 하이픈 포맷이 중간에 '-' 를 끼워넣으면 커서가 그 사이에 머물러
    // 다음 타이핑이 잘못된 위치에 들어가는 버그 방지.
    var phoneField by remember {
        mutableStateOf(
            androidx.compose.ui.text.input.TextFieldValue(
                text = state.phoneNumber,
                selection = androidx.compose.ui.text.TextRange(state.phoneNumber.length)
            )
        )
    }
    // ViewModel state 가 외부 경로(최근 통화/문자에서 선택 등)로 바뀌면 동기화.
    LaunchedEffect(state.phoneNumber) {
        if (state.phoneNumber != phoneField.text) {
            phoneField = androidx.compose.ui.text.input.TextFieldValue(
                text = state.phoneNumber,
                selection = androidx.compose.ui.text.TextRange(state.phoneNumber.length)
            )
        }
    }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(toast) {
        toast?.let {
            snackbar.showSnackbar(it)
            viewModel.consumeToast()
        }
    }

    Scaffold(
        containerColor = TossGrayBg,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "후속 처리",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TossTextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "뒤로", tint = TossTextPrimary)
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { overflowOpen = true }) {
                            Icon(Icons.Default.MoreVert, "더보기", tint = TossTextPrimary)
                        }
                        DropdownMenu(
                            expanded = overflowOpen,
                            onDismissRequest = { overflowOpen = false }
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "나중에 처리",
                                        color = TossTextPrimary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                },
                                onClick = {
                                    overflowOpen = false
                                    viewModel.postpone(context, onDone)
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = TossGrayBg)
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            // 하단 고정 CTA — 두 단계 위계.
            // 1차(파란 fill): 문자 보내기 = 메인 흐름
            // 2차(아웃라인): 저장만 하기 = 전화로 끝난 경우
            // '나중에 처리'(SKIPPED)는 상단 ⋮ 메뉴로 이동 — 자주 안 쓰는 액션이라 시각에서 빼서 위계 단순화.
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(TossGrayBg)
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                TossPrimaryButton(
                    text = "문자 보내기",
                    onClick = { viewModel.sendSms(context, onDone) }
                )
                Spacer(Modifier.height(8.dp))
                TossSecondaryButton(
                    text = "저장만 하기",
                    onClick = { viewModel.saveOnly(context, onDone) }
                )
            }
        }
    ) { inner ->
        Column(
            modifier = Modifier
                .padding(inner)
                .fillMaxSize()
                // 키보드 올라오면 스크롤 영역 축소 → 포커스된 입력칸 자동 정렬
                .imePadding()
                .background(TossGrayBg)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 전화번호 카드 — 직접 입력 외에 최근 통화/최근 문자에서 가져올 수 있음
            // (문자만 주고받은 고객도 잡히도록 두 경로 모두 제공).
            TossCard {
                Column {
                    SectionLabel("고객 번호")
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = {
                            // 권한 있으면 바로 로드 + 열기. 없으면 시스템 권한 다이얼로그 띄움
                            // (런처 콜백에서 결과에 따라 로드/안내 처리).
                            if (PermissionHelper.hasCallLog(context)) {
                                viewModel.loadRecentCalls(context)
                                recentDialogOpen = true
                            } else {
                                callLogPermissionLauncher.launch(Manifest.permission.READ_CALL_LOG)
                            }
                        }) {
                            Text("📞 최근 통화에서", color = TossBlue, fontWeight = FontWeight.SemiBold)
                        }
                        TextButton(onClick = {
                            if (PermissionHelper.isGranted(context, Manifest.permission.READ_SMS)) {
                                viewModel.loadRecentSmsContacts()
                                smsDialogOpen = true
                            } else {
                                smsPermissionLauncher.launch(Manifest.permission.READ_SMS)
                            }
                        }) {
                            Text("💬 최근 문자에서", color = TossBlue, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(
                        value = phoneField,
                        onValueChange = { tfv ->
                            // 입력 도중에도 하이픈 자동 추가. 포맷 적용 후 커서는 항상 맨 끝으로
                            // 강제 (그렇지 않으면 끼워넣어진 '-' 뒤에 머물러 다음 입력이 잘못된 위치로 감).
                            val formatted = PhoneNumberFormatter.formatProgressive(tfv.text)
                            phoneField = androidx.compose.ui.text.input.TextFieldValue(
                                text = formatted,
                                selection = androidx.compose.ui.text.TextRange(formatted.length)
                            )
                            viewModel.setPhone(formatted)
                        },
                        placeholder = { Text("010-1234-5678", color = TossTextTertiary) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth(),
                        colors = tossFieldColors()
                    )

                    // 이름 (선택) — 첫 통화 직후 사장님이 들은 이름을 바로 저장 가능.
                    // 빈 값이면 기존 이름 보존 (CustomerRepository.upsertByPhone 에서 처리).
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "이름 (선택)",
                        style = MaterialTheme.typography.labelMedium,
                        color = TossTextSecondary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(
                        value = state.customerName,
                        onValueChange = viewModel::setName,
                        placeholder = { Text("김사장", color = TossTextTertiary) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = tossFieldColors()
                    )

                    // 첫 만난 날짜 — 옛 고객을 오늘 등록할 때 실제 첫 만난 날짜를 지정.
                    // 미지정 = 오늘. 지정 시 가짜 CallRecord 가 그 날짜에 만들어져 타임라인 정확.
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "첫 만난 날짜 (옛 고객이면 지정)",
                        style = MaterialTheme.typography.labelMedium,
                        color = TossTextSecondary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(4.dp))
                    androidx.compose.foundation.layout.Row(
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { firstMetPickerOpen = true }) {
                            Text(
                                state.firstMetAt?.let { com.detailline.callfollowcrm.util.DateTimeUtils.formatKoreanDate(it) }
                                    ?: "📅 오늘",
                                color = TossBlue,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        if (state.firstMetAt != null) {
                            TextButton(onClick = { viewModel.setFirstMetAt(null) }) {
                                Text("초기화", color = TossTextTertiary, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // 상태 카드
            TossCard {
                Column {
                    SectionLabel("고객 상태")
                    Spacer(Modifier.height(10.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(CustomerStatus.values().toList()) { status ->
                            TossChip(
                                text = status.label,
                                selected = state.status == status,
                                onClick = { viewModel.setStatus(status) }
                            )
                        }
                    }
                }
            }

            // 템플릿 + 본문 카드
            TossCard {
                Column {
                    SectionLabel("문자 템플릿")
                    Spacer(Modifier.height(10.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(templates) { template ->
                            TossChip(
                                text = template.title,
                                selected = state.selectedTemplateId == template.id,
                                onClick = { viewModel.selectTemplate(template) }
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = state.messageBody,
                        onValueChange = viewModel::setBody,
                        placeholder = { Text("문자 본문을 입력하거나 템플릿을 선택하세요", color = TossTextTertiary) },
                        modifier = Modifier.fillMaxWidth().height(180.dp),
                        colors = tossFieldColors()
                    )

                    if (attachments.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "📎 첨부 사진 ${attachments.size}장 — 보낼 때 자동으로 포함돼요",
                            style = MaterialTheme.typography.labelMedium,
                            color = TossBlue,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(8.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(attachments) { a ->
                                AsyncImage(
                                    model = a.fileUri,
                                    contentDescription = a.displayName,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(72.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(TossGrayBg)
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "사진이 있으면 \"메시지\" 앱 선택 한 단계가 추가돼요. 수신자 번호 자동 입력은 앱에 따라 다를 수 있어요.",
                            style = MaterialTheme.typography.labelSmall,
                            color = TossTextTertiary
                        )
                    }
                }
            }

            // 메모 카드
            TossCard {
                Column {
                    SectionLabel("메모")
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = state.memo,
                        onValueChange = viewModel::setMemo,
                        placeholder = { Text("이번 상담 메모를 적어두세요", color = TossTextTertiary) },
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                        colors = tossFieldColors()
                    )
                }
            }

            // 안내
            Box(Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
                Text(
                    "이 앱은 문자를 자동으로 보내지 않아요. 기본 문자앱에서 직접 전송 버튼을 눌러주세요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TossTextTertiary
                )
            }

            Spacer(Modifier.height(8.dp))
        }
    }

    if (firstMetPickerOpen) {
        val initial = state.firstMetAt ?: System.currentTimeMillis()
        val pickerState = androidx.compose.material3.rememberDatePickerState(initialSelectedDateMillis = initial)
        androidx.compose.material3.DatePickerDialog(
            onDismissRequest = { firstMetPickerOpen = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { viewModel.setFirstMetAt(it) }
                    firstMetPickerOpen = false
                }) { Text("확인", color = TossBlue, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = { firstMetPickerOpen = false }) {
                    Text("취소", color = TossTextSecondary)
                }
            }
        ) {
            androidx.compose.material3.DatePicker(state = pickerState)
        }
    }

    if (recentDialogOpen) {
        val callLogPermissionMissing = !PermissionHelper.hasCallLog(context)
        AlertDialog(
            onDismissRequest = { recentDialogOpen = false },
            title = { Text("최근 통화에서 선택", color = TossTextPrimary) },
            text = {
                if (recentCalls.isEmpty()) {
                    Text(
                        if (callLogPermissionMissing)
                            "통화 기록 권한이 꺼져 있어요. 아래 \"앱 설정 열기\" 로 권한을 켜주세요."
                        else
                            "최근 통화 기록이 없어요.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TossTextTertiary
                    )
                } else {
                    LazyColumn(modifier = Modifier.height(360.dp)) {
                        items(recentCalls) { call ->
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.pickRecentCall(call.phoneNumber)
                                        recentDialogOpen = false
                                    }
                                    .padding(vertical = 10.dp, horizontal = 4.dp)
                            ) {
                                Text(
                                    PhoneNumberFormatter.format(call.phoneNumber),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TossTextPrimary
                                )
                                Text(
                                    "${DateTimeUtils.formatShort(call.date)} · ${callTypeLabelKorean(call.type.name)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TossTextTertiary
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (callLogPermissionMissing) {
                    TextButton(onClick = {
                        recentDialogOpen = false
                        openAppSettings()
                    }) {
                        Text("앱 설정 열기", color = TossBlue, fontWeight = FontWeight.SemiBold)
                    }
                } else {
                    TextButton(onClick = { recentDialogOpen = false }) {
                        Text("닫기", color = TossTextSecondary)
                    }
                }
            },
            dismissButton = if (callLogPermissionMissing) {
                {
                    TextButton(onClick = { recentDialogOpen = false }) {
                        Text("닫기", color = TossTextSecondary)
                    }
                }
            } else null,
            containerColor = Color.White
        )
    }

    if (smsDialogOpen) {
        val readSmsPermissionMissing = !PermissionHelper.isGranted(context, Manifest.permission.READ_SMS)
        AlertDialog(
            onDismissRequest = { smsDialogOpen = false },
            title = { Text("최근 문자에서 선택", color = TossTextPrimary) },
            text = {
                if (recentSms.isEmpty()) {
                    Text(
                        if (readSmsPermissionMissing)
                            "문자 읽기 권한이 꺼져 있어요. 아래 \"앱 설정 열기\" 로 권한을 켜주세요."
                        else
                            "최근 주고받은 문자가 없어요.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TossTextTertiary
                    )
                } else {
                    LazyColumn(modifier = Modifier.height(360.dp)) {
                        items(recentSms) { sms ->
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.pickRecentSms(sms.address)
                                        smsDialogOpen = false
                                    }
                                    .padding(vertical = 10.dp, horizontal = 4.dp)
                            ) {
                                Text(
                                    PhoneNumberFormatter.format(sms.address),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TossTextPrimary
                                )
                                Text(
                                    "${DateTimeUtils.formatShort(sms.lastDateMs)} · ${if (sms.lastSent) "보냄" else "받음"} · ${sms.lastBody.take(28).replace('\n', ' ')}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TossTextTertiary,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (readSmsPermissionMissing) {
                    TextButton(onClick = {
                        smsDialogOpen = false
                        openAppSettings()
                    }) {
                        Text("앱 설정 열기", color = TossBlue, fontWeight = FontWeight.SemiBold)
                    }
                } else {
                    TextButton(onClick = { smsDialogOpen = false }) {
                        Text("닫기", color = TossTextSecondary)
                    }
                }
            },
            dismissButton = if (readSmsPermissionMissing) {
                {
                    TextButton(onClick = { smsDialogOpen = false }) {
                        Text("닫기", color = TossTextSecondary)
                    }
                }
            } else null,
            containerColor = Color.White
        )
    }
}

private fun callTypeLabelKorean(raw: String): String = when (raw) {
    "INCOMING" -> "수신"
    "OUTGOING" -> "발신"
    "MISSED" -> "부재중"
    "REJECTED" -> "거절"
    else -> "통화"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun tossFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = TossBlue,
    unfocusedBorderColor = TossDivider,
    focusedTextColor = TossTextPrimary,
    unfocusedTextColor = TossTextPrimary,
    cursorColor = TossBlue,
    focusedContainerColor = Color.White,
    unfocusedContainerColor = Color.White
)
