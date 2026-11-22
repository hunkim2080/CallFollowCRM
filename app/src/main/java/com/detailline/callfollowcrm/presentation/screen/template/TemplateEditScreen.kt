package com.detailline.callfollowcrm.presentation.screen.template

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.detailline.callfollowcrm.presentation.component.SectionLabel
import com.detailline.callfollowcrm.presentation.component.TossCard
import com.detailline.callfollowcrm.presentation.component.TossPrimaryButton
import com.detailline.callfollowcrm.presentation.theme.TossBlue
import com.detailline.callfollowcrm.presentation.theme.TossDivider
import com.detailline.callfollowcrm.presentation.theme.TossGrayBg
import com.detailline.callfollowcrm.presentation.theme.TossTextPrimary
import com.detailline.callfollowcrm.presentation.theme.TossTextSecondary
import com.detailline.callfollowcrm.presentation.theme.TossTextTertiary
import com.detailline.callfollowcrm.presentation.util.keyboardPadding
import com.detailline.callfollowcrm.presentation.util.keyboardOrNavPadding

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateEditScreen(
    viewModel: TemplateEditViewModel,
    onDone: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val saving by viewModel.saving.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val attachments by viewModel.attachments.collectAsState()
    val context = LocalContext.current
    // 🗣 문구 저장이 실패하면 사장님께 말한다.
    val tmplToast by viewModel.toast.collectAsState()
    androidx.compose.runtime.LaunchedEffect(tmplToast) {
        tmplToast?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_SHORT).show()
            viewModel.consumeToast()
        }
    }
    val canSave = !saving && !loading && state.title.isNotBlank() && state.body.isNotBlank()

    var confirmTplExit by remember { mutableStateOf(false) }
    fun tryTplExit() { if (viewModel.hasChanges()) confirmTplExit = true else onDone() }
    androidx.activity.compose.BackHandler { tryTplExit() }
    if (confirmTplExit) {
        androidx.compose.material3.AlertDialog(
            containerColor = Color.White,
            tonalElevation = 0.dp,
            onDismissRequest = { confirmTplExit = false },
            title = { Text("저장 안 하고 나갈까요?") },
            text = { Text("고친 문구가 사라져요.") },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { confirmTplExit = false; onDone() }) { Text("나가기") } },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { confirmTplExit = false }) { Text("계속 작성") } }
        )
    }

    // 갤러리 바로 열기(PickVisualMedia). 예전 OpenDocument 는 '파일 탐색기'라 갤러리를 못 찾던 문제. (2026-07-17 사장님)
    val pickImage = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let { viewModel.addAttachment(context, it) }
    }

    Scaffold(
        containerColor = TossGrayBg,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "저장한 말 편집",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TossTextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { tryTplExit() }) {
                        Icon(Icons.Default.ArrowBack, "뒤로", tint = TossTextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = TossGrayBg)
            )
        },
        bottomBar = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(TossGrayBg)
                    .keyboardOrNavPadding()   // 🔧 바닥 고정 버튼 — 키보드/내비바 중 큰 쪽만큼 띄워 '저장'이 내비바에 안 가리게. (2026-10-23 사장님)
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                TossPrimaryButton(
                    text = if (saving) "저장 중…" else "저장",
                    onClick = { viewModel.save(onDone) },
                    enabled = canSave
                )
            }
        }
    ) { inner ->
        Column(
            Modifier
                .padding(inner)
                .fillMaxSize()
                .background(TossGrayBg)
                // ⌨️ keyboardPadding 은 verticalScroll **바깥** — 뷰포트를 키보드만큼 줄여 입력칸이 위로 자동 스크롤. (2026-10-20 사장님)
                .keyboardPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TossCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("손님 FAQ에 공개", modifier = Modifier.weight(1f),
                        style = com.detailline.callfollowcrm.presentation.theme.AppType.headline,
                        color = com.detailline.callfollowcrm.presentation.theme.AppTheme.colors.text)
                    Switch(checked = state.isPublic, onCheckedChange = viewModel::setPublic, enabled = !loading && !saving)
                }
            }
            if (state.isPublic) {
                TossCard {
                    Column {
                        SectionLabel("분류")
                        Spacer(Modifier.height(10.dp))
                        com.detailline.callfollowcrm.presentation.component.SheetTextField(
                            value = state.category, onValueChange = viewModel::setCategory, placeholder = "예: 가격, 시공시간"
                        )
                    }
                }
            }
            // 제목
            TossCard {
                Column {
                    SectionLabel(if (state.isPublic) "손님 질문" else "문구 이름")
                    Spacer(Modifier.height(10.dp))
                    com.detailline.callfollowcrm.presentation.component.SheetTextField(
                        value = state.title,
                        onValueChange = viewModel::setTitle,
                        placeholder = if (state.isPublic) "예: 시공은 얼마나 걸리나요?" else "예: 사진 요청"
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (state.isPublic) "공개 FAQ에 손님 질문으로 표시돼요" else "후속 처리 화면에서 칩으로 표시되는 이름이에요",
                        style = MaterialTheme.typography.labelSmall,
                        color = TossTextTertiary
                    )
                }
            }

            // 본문
            TossCard {
                Column {
                    SectionLabel(if (state.isPublic) "답" else "문자 본문")
                    Spacer(Modifier.height(10.dp))
                    com.detailline.callfollowcrm.presentation.component.SheetTextField(
                        value = state.body,
                        onValueChange = viewModel::setBody,
                        placeholder = "문자에 자동 입력될 본문을 적어주세요",
                        singleLine = false, minHeightDp = 220
                    )
                }
            }

            // 첨부 사진 (명함, 시공 예시 등)
            TossCard {
                Column {
                    SectionLabel("첨부 사진")
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (state.isPublic) "첫 첨부 사진이 FAQ 답 사진으로 나가요. 문자에도 함께 첨부돼요." else "이 문구로 문자 보낼 때 자동으로 첨부돼요.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TossTextTertiary
                    )
                    Spacer(Modifier.height(12.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(attachments) { item ->
                            AttachmentThumb(
                                item = item,
                                onRemove = {
                                    when (item) {
                                        is AttachmentItem.Pending -> viewModel.removePendingAttachment(item.uri)
                                        is AttachmentItem.Saved -> viewModel.removeSavedAttachment(context, item.entity)
                                    }
                                }
                            )
                        }
                        item {
                            AddAttachmentTile(onClick = {
                                pickImage.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            })
                        }
                    }
                    if (attachments.size >= 3) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "사진이 많으면 MMS 용량 제한으로 메시지 앱이 자동 압축할 수 있어요.",
                            style = MaterialTheme.typography.labelSmall,
                            color = TossTextTertiary
                        )
                    }
                }
            }

            // 활성
            TossCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "활성",
                            style = MaterialTheme.typography.titleMedium,
                            color = TossTextPrimary
                        )
                        Text(
                            "끄면 템플릿 선택 목록에서 숨겨져요",
                            style = MaterialTheme.typography.bodySmall,
                            color = TossTextTertiary
                        )
                    }
                    Switch(
                        checked = state.isActive,
                        onCheckedChange = viewModel::setActive,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = TossBlue,
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = TossDivider,
                            uncheckedBorderColor = TossDivider
                        )
                    )
                }
            }

            if (state.isDefault) {
                Text(
                    "이름과 본문을 자유롭게 바꿀 수 있어요. 필요 없으면 목록에서 삭제해도 돼요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TossTextSecondary,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}


@Composable
private fun AttachmentThumb(
    item: AttachmentItem,
    onRemove: () -> Unit
) {
    Box(modifier = Modifier.size(84.dp)) {
        AsyncImage(
            model = item.uri,
            contentDescription = item.displayName,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(84.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(TossGrayBg)
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(22.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable { onRemove() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = "삭제",
                tint = Color.White,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Composable
private fun AddAttachmentTile(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(84.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(TossGrayBg)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Add, contentDescription = "사진 추가", tint = TossBlue)
            Spacer(Modifier.height(2.dp))
            Text("사진 추가", style = MaterialTheme.typography.labelSmall, color = TossBlue)
        }
    }
}
