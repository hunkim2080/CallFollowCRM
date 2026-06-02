package com.detailline.callfollowcrm.presentation.screen.template

import androidx.activity.compose.rememberLauncherForActivityResult
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateEditScreen(
    viewModel: TemplateEditViewModel,
    onDone: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val attachments by viewModel.attachments.collectAsState()
    val context = LocalContext.current
    val canSave = state.title.isNotBlank() && state.body.isNotBlank()

    val pickImage = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.addAttachment(context, it) }
    }

    Scaffold(
        containerColor = TossGrayBg,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (state.isDefault) "기본 템플릿 수정" else "템플릿 편집",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TossTextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onDone) {
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
                    .imePadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                TossPrimaryButton(
                    text = "저장",
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 제목
            TossCard {
                Column {
                    SectionLabel("템플릿 이름")
                    Spacer(Modifier.height(10.dp))
                    com.detailline.callfollowcrm.presentation.component.SheetTextField(
                        value = state.title,
                        onValueChange = viewModel::setTitle,
                        placeholder = "예: 사진 요청"
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "후속 처리 화면에서 칩으로 표시되는 이름이에요",
                        style = MaterialTheme.typography.labelSmall,
                        color = TossTextTertiary
                    )
                }
            }

            // 본문
            TossCard {
                Column {
                    SectionLabel("문자 본문")
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
                        "이 템플릿으로 문자 보낼 때 자동으로 첨부돼요. 명함이나 시공 사진 등을 넣어두면 편해요.",
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
                                pickImage.launch(arrayOf("image/*"))
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
                    "기본 템플릿의 이름과 본문도 자유롭게 바꿀 수 있어요. v1 에서는 삭제는 지원하지 않아요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TossTextSecondary,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }

            Spacer(Modifier.height(8.dp))
        }
    }
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
