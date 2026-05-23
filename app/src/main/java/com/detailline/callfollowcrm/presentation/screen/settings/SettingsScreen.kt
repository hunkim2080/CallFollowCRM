package com.detailline.callfollowcrm.presentation.screen.settings

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import com.detailline.callfollowcrm.data.local.entity.MessageTemplateEntity
import com.detailline.callfollowcrm.presentation.theme.TossDivider
import com.detailline.callfollowcrm.presentation.theme.TossError
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.detailline.callfollowcrm.data.AppContainer
import com.detailline.callfollowcrm.domain.model.CustomerStatus
import com.detailline.callfollowcrm.presentation.component.SectionLabel
import com.detailline.callfollowcrm.presentation.component.TossBadge
import com.detailline.callfollowcrm.presentation.component.TossCard
import com.detailline.callfollowcrm.presentation.component.TossChip
import com.detailline.callfollowcrm.presentation.component.TossPrimaryButton
import com.detailline.callfollowcrm.presentation.component.TossSecondaryButton
import com.detailline.callfollowcrm.presentation.theme.TossBlue
import com.detailline.callfollowcrm.presentation.theme.TossBlueSoft
import com.detailline.callfollowcrm.presentation.theme.TossGrayBg
import com.detailline.callfollowcrm.presentation.theme.TossSuccess
import com.detailline.callfollowcrm.presentation.theme.TossTextPrimary
import com.detailline.callfollowcrm.presentation.theme.TossTextSecondary
import com.detailline.callfollowcrm.presentation.theme.TossTextTertiary
import com.detailline.callfollowcrm.recording.AdotFolderScanner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    container: AppContainer,
    onBack: () -> Unit,
    onOpenTemplates: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val templates by viewModel.templates.collectAsState()
    val context = LocalContext.current
    var adotConnected by remember { mutableStateOf(AdotFolderScanner.isConnected(context)) }

    // 받은 문자 권한 요청 launcher. READ_SMS 와 RECEIVE_SMS 를 한 번에 요청.
    //  - READ_SMS = 고객 상세에서 주고받은 문자 표시
    //  - RECEIVE_SMS = SmsReceiver 가 SMS_RECEIVED broadcast 받음 → Phase 1 답변 추천 트리거
    // 둘 다 SMS 권한 그룹이라 사장님 입장에선 다이얼로그 하나로 보임.
    val smsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val readGranted = result[Manifest.permission.READ_SMS] == true
        if (readGranted) {
            viewModel.setReceivedSmsEnabled(true)
            Toast.makeText(context, "주고받은 문자를 고객 상세에서 볼 수 있어요", Toast.LENGTH_SHORT).show()
        } else {
            viewModel.setReceivedSmsEnabled(false)
            Toast.makeText(context, "권한이 거부되어 켤 수 없어요", Toast.LENGTH_SHORT).show()
        }
    }

    // SEND_SMS 권한 요청 launcher (자동 응답 발송용).
    val sendSmsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.setAutoFirstReplyEnabled(true)
            Toast.makeText(context, "자동 응답 켜졌어요. 첫 통화 후 10초 카운트다운 뒤 발송돼요.", Toast.LENGTH_LONG).show()
        } else {
            viewModel.setAutoFirstReplyEnabled(false)
            Toast.makeText(context, "SEND_SMS 권한이 거부되어 켤 수 없어요", Toast.LENGTH_SHORT).show()
        }
    }

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            AdotFolderScanner.connectFolder(context, uri)
            adotConnected = true
            // 연결 직후 첫 스캔
            AdotFolderScanner.scanIfConnected(context, container) { count ->
                Toast.makeText(context, "에이닷 폴더 연결됨 · 녹음 ${count}개 가져옴", Toast.LENGTH_LONG).show()
            }
        }
    }

    Scaffold(
        containerColor = TossGrayBg,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "설정",
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
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 에이닷 통화녹음 폴더
            TossCard {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "에이닷 통화녹음 연동",
                            style = MaterialTheme.typography.titleLarge,
                            color = TossTextPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        if (adotConnected) {
                            TossBadge("연결됨", color = TossSuccess, background = TossBlueSoft)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "내장 저장공간 > Music > TPhoneCallRecords 폴더를 한 번만 선택해주세요. " +
                            "앱을 켤 때마다 새 통화녹음 파일이 자동으로 추가되고, 전화번호로 고객과 자동 연결됩니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TossTextSecondary
                    )
                    Spacer(Modifier.height(12.dp))
                    if (adotConnected) {
                        TossPrimaryButton(
                            text = "지금 스캔하기",
                            onClick = {
                                AdotFolderScanner.scanIfConnected(context, container) { count ->
                                    Toast.makeText(
                                        context,
                                        if (count > 0) "녹음 ${count}개 새로 가져옴"
                                        else "새 녹음 없음",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        )
                        Spacer(Modifier.height(8.dp))
                        TossSecondaryButton(
                            text = "연결 해제",
                            onClick = {
                                AdotFolderScanner.disconnect(context)
                                adotConnected = false
                                Toast.makeText(context, "연결 해제됨", Toast.LENGTH_SHORT).show()
                            }
                        )
                    } else {
                        TossPrimaryButton(
                            text = "폴더 연결하기",
                            onClick = { folderPicker.launch(null) }
                        )
                    }
                }
            }

            // 데이터 정리 (자동 import된 과거 데이터 제거)
            TossCard {
                Column {
                    Text(
                        "자동 import 데이터 정리",
                        style = MaterialTheme.typography.titleLarge,
                        color = TossTextPrimary
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "에이닷 폴더에서 자동으로 가져온 녹음 첨부와, " +
                            "그로 인해 자동 생성된(이름/메모 없는) 고객을 한 번에 삭제합니다. " +
                            "직접 수정한 고객은 보존됩니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TossTextSecondary
                    )
                    Spacer(Modifier.height(12.dp))
                    TossSecondaryButton(
                        text = "에이닷 자동 import 모두 지우기",
                        onClick = {
                            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                                val deletedRec = container.recordingRepository.deleteAllAdotImports()
                                val deletedCust = container.customerRepository.deleteOrphans()
                                // 다음 스캔에서 옛 파일이 또 들어오지 않도록 cutoff를 지금으로 리셋.
                                AdotFolderScanner.resetConnectedAtToNow(context)
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(
                                        context,
                                        "녹음 ${deletedRec}개 · 고객 ${deletedCust}명 정리됨. 지금부터의 통화만 가져와요.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        }
                    )
                }
            }

            // 통화 종료 후 동작
            TossCard {
                Column {
                    SectionLabel("통화 종료 후 동작")
                    Spacer(Modifier.height(10.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(AfterCallBehavior.values().toList()) { b ->
                            TossChip(
                                text = b.label,
                                selected = state.afterCallBehavior == b,
                                onClick = { viewModel.setBehavior(b) }
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "기본값: 알림 표시. 전체화면 팝업은 사용하지 않아요.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TossTextTertiary
                    )
                }
            }

            // 기본 고객 상태
            TossCard {
                Column {
                    SectionLabel("새 고객의 기본 상태")
                    Spacer(Modifier.height(10.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(CustomerStatus.values().toList()) { s ->
                            TossChip(
                                text = s.label,
                                selected = state.defaultStatus == s,
                                onClick = { viewModel.setDefaultStatus(s) }
                            )
                        }
                    }
                }
            }

            // 템플릿 관리
            TossCard {
                Column {
                    SectionLabel("문자 템플릿")
                    Spacer(Modifier.height(10.dp))
                    TossPrimaryButton(
                        text = "템플릿 보기 / 편집",
                        onClick = onOpenTemplates
                    )
                }
            }

            // AI 요약 (준비 중)
            TossCard {
                Column {
                    SectionLabel("AI 요약")
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "추후 사용자 서버를 거쳐 STT/요약을 제공합니다. v1에서는 비활성.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TossTextTertiary
                    )
                }
            }

            // 데이터 백업 (준비 중)
            TossCard {
                Column {
                    SectionLabel("데이터 백업")
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "준비 중. (CSV 내보내기 등은 추후 추가)",
                        style = MaterialTheme.typography.bodySmall,
                        color = TossTextTertiary
                    )
                }
            }

            // 받은 문자 표시 (READ_SMS)
            TossCard {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "주고받은 문자 보기",
                                style = MaterialTheme.typography.titleLarge,
                                color = TossTextPrimary
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "고객 상세에서 갤럭시 메시지로 주고받은 문자를 모두 시간순으로 보여줘요.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TossTextSecondary
                            )
                        }
                        Switch(
                            checked = state.receivedSmsEnabled,
                            onCheckedChange = { checked ->
                                if (checked) {
                                    val readGranted = ContextCompat.checkSelfPermission(
                                        context, Manifest.permission.READ_SMS
                                    ) == PackageManager.PERMISSION_GRANTED
                                    val receiveGranted = ContextCompat.checkSelfPermission(
                                        context, Manifest.permission.RECEIVE_SMS
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (readGranted && receiveGranted) {
                                        viewModel.setReceivedSmsEnabled(true)
                                    } else {
                                        smsPermissionLauncher.launch(
                                            arrayOf(
                                                Manifest.permission.READ_SMS,
                                                Manifest.permission.RECEIVE_SMS
                                            )
                                        )
                                    }
                                } else {
                                    viewModel.setReceivedSmsEnabled(false)
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = TossBlue,
                                uncheckedThumbColor = Color.White,
                                uncheckedTrackColor = TossTextTertiary
                            )
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "끄면 권한과 무관하게 받은 문자를 표시하지 않아요. RING-GO 는 문자를 발송·수정하지 않고 화면에 보여주기만 합니다.",
                        style = MaterialTheme.typography.labelSmall,
                        color = TossTextTertiary
                    )
                }
            }

            // 후속 빠른 액션 — 두 번째 통화부터 뜨는 알림의 액션 버튼 3개 지정
            TossCard {
                Column {
                    Text(
                        "후속 알림 빠른 액션",
                        style = MaterialTheme.typography.titleLarge,
                        color = TossTextPrimary
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "통화 종료 알림(RING-GO 캐치)의 액션 버튼 3개에 표시할 템플릿. 탭하면 그 템플릿이 자동 선택된 채로 문자 화면이 열려요.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TossTextSecondary
                    )
                    Spacer(Modifier.height(14.dp))
                    if (templates.isEmpty()) {
                        Text(
                            "먼저 템플릿을 만들어주세요 (\"문자 템플릿 → 템플릿 보기/편집\").",
                            style = MaterialTheme.typography.bodySmall,
                            color = TossError
                        )
                    } else {
                        TemplateDropdown(
                            label = "버튼 1",
                            templates = templates,
                            selectedId = state.quickActionTemplateId1,
                            onSelect = { viewModel.setQuickAction(1, it) }
                        )
                        Spacer(Modifier.height(10.dp))
                        TemplateDropdown(
                            label = "버튼 2",
                            templates = templates,
                            selectedId = state.quickActionTemplateId2,
                            onSelect = { viewModel.setQuickAction(2, it) }
                        )
                        Spacer(Modifier.height(10.dp))
                        TemplateDropdown(
                            label = "버튼 3",
                            templates = templates,
                            selectedId = state.quickActionTemplateId3,
                            onSelect = { viewModel.setQuickAction(3, it) }
                        )
                    }
                }
            }

            // 첫 응대 자동 문자 발송 (SEND_SMS)
            TossCard {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "처음 연락온 고객 자동 응답",
                                style = MaterialTheme.typography.titleLarge,
                                color = TossTextPrimary
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "첫 통화 종료 10초 뒤 자동으로 응대 문자 발송. 알림에서 취소 가능.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TossTextSecondary
                            )
                        }
                        Switch(
                            checked = state.autoFirstReplyEnabled,
                            onCheckedChange = { checked ->
                                if (checked) {
                                    val granted = ContextCompat.checkSelfPermission(
                                        context, Manifest.permission.SEND_SMS
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (granted) {
                                        viewModel.setAutoFirstReplyEnabled(true)
                                    } else {
                                        sendSmsPermissionLauncher.launch(Manifest.permission.SEND_SMS)
                                    }
                                } else {
                                    viewModel.setAutoFirstReplyEnabled(false)
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = TossBlue,
                                uncheckedThumbColor = Color.White,
                                uncheckedTrackColor = TossTextTertiary
                            )
                        )
                    }

                    if (state.autoFirstReplyEnabled) {
                        Spacer(Modifier.height(14.dp))
                        if (templates.isEmpty()) {
                            Text(
                                "먼저 템플릿을 만들어주세요. 위 \"문자 템플릿 → 템플릿 보기/편집\" 에서 추가할 수 있어요.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TossError
                            )
                        } else {
                            TemplateDropdown(
                                label = "수신 통화 첫 응대 템플릿",
                                templates = templates,
                                selectedId = state.firstReplyIncomingTemplateId,
                                onSelect = { viewModel.setIncomingTemplate(it) }
                            )
                            Spacer(Modifier.height(10.dp))
                            TemplateDropdown(
                                label = "부재중 통화 첫 응대 템플릿",
                                templates = templates,
                                selectedId = state.firstReplyMissedTemplateId,
                                onSelect = { viewModel.setMissedTemplate(it) }
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "주의: 자동 발송된 SMS 는 통신사 요금이 부과될 수 있어요. 잘못된 번호 발송 위험도 있으니 신중히.",
                            style = MaterialTheme.typography.labelSmall,
                            color = TossError
                        )
                    } else {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "기본 OFF. 권한 부여하지 않으면 자동 발송 불가.",
                            style = MaterialTheme.typography.labelSmall,
                            color = TossTextTertiary
                        )
                    }
                }
            }

            // 문자 발송 정책
            TossCard {
                Column {
                    SectionLabel("문자 발송 정책")
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "수동 발송(템플릿 + 사진 첨부)은 항상 기본 문자앱 작성 화면을 열어 사용자가 전송 버튼을 직접 누릅니다. 자동 응답(위 토글)은 SEND_SMS 권한을 받아 발송하며, 10초 카운트다운 알림으로 취소 가능합니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TossTextSecondary
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

/**
 * 템플릿 선택 드롭다운. 라벨 + 현재 선택 라벨 + 펼치면 활성 템플릿 리스트.
 * 미선택 (-1L) 상태도 허용 — "선택 안 함" 옵션 포함.
 */
@Composable
private fun TemplateDropdown(
    label: String,
    templates: List<MessageTemplateEntity>,
    selectedId: Long,
    onSelect: (Long) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = templates.firstOrNull { it.id == selectedId }
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = TossTextSecondary,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(6.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .border(BorderStroke(1.dp, TossDivider), RoundedCornerShape(10.dp))
                .clickable { expanded = true }
                .padding(horizontal = 12.dp, vertical = 12.dp)
        ) {
            Text(
                selected?.title ?: "선택 안 함",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = if (selected != null) TossTextPrimary else TossTextTertiary,
                fontWeight = if (selected != null) FontWeight.Medium else FontWeight.Normal
            )
            Text("▾", color = TossTextTertiary)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = {
                    Text(
                        "선택 안 함 (이 케이스는 자동 발송 X)",
                        color = TossTextTertiary
                    )
                },
                onClick = {
                    onSelect(-1L)
                    expanded = false
                }
            )
            templates.forEach { t ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(t.title, color = TossTextPrimary, fontWeight = FontWeight.SemiBold)
                            Text(
                                t.body.lineSequence().firstOrNull().orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = TossTextSecondary,
                                maxLines = 1
                            )
                        }
                    },
                    onClick = {
                        onSelect(t.id)
                        expanded = false
                    }
                )
            }
        }
    }
}
