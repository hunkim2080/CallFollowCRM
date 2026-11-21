package com.detailline.callfollowcrm.presentation.screen.faq

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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.detailline.callfollowcrm.domain.faq.RepQuestionRules
import com.detailline.callfollowcrm.presentation.component.AppSheet
import com.detailline.callfollowcrm.presentation.component.SheetTextField
import com.detailline.callfollowcrm.presentation.component.TossPrimaryButton
import com.detailline.callfollowcrm.presentation.component.TossSecondaryButton
import com.detailline.callfollowcrm.presentation.theme.AppShape
import com.detailline.callfollowcrm.presentation.theme.AppSpace
import com.detailline.callfollowcrm.presentation.theme.AppTheme
import com.detailline.callfollowcrm.presentation.theme.AppType
import com.detailline.callfollowcrm.presentation.theme.ShimmerLine
import com.detailline.callfollowcrm.presentation.util.keyboardOrNavPadding
import com.detailline.callfollowcrm.presentation.util.keyboardPadding

/** 사진 고르기 칸 한 변(프로토 .fimgpick 74px). */
private val IMAGE_TILE = 74.dp

/**
 * 💬 **대표질문 등록·수정** — 질문(20자) · 답변(500자) · 답장 내 이미지(0/1) · [미리보기][등록하기].
 *   프로토 EYKUs11cVT7JW71yVrVoBA ③ 을 1:1 로. (2026-11-21 사장님 「가」)
 *
 *   저장은 `SaveGuard` — 실패하면 말하고 쓴 글은 그대로 둔다. 쓰다가 나가면 한 번 묻는다(기본 UX).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepQuestionEditScreen(
    viewModel: RepQuestionEditViewModel,
    onDone: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    // 🗣 저장·사진 복사가 실패하면 사장님께 말한다.
    val toast by viewModel.toast.collectAsState()
    LaunchedEffect(toast) {
        toast?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_SHORT).show()
            viewModel.consumeToast()
        }
    }
    // 고치려던 줄이 이미 없으면(다른 데서 지움) 말하고 닫는다.
    LaunchedEffect(state.missing) { if (state.missing) onDone() }

    var confirmExit by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var showPreview by remember { mutableStateOf(false) }
    var showNewCat by remember { mutableStateOf(false) }
    var newCatText by remember { mutableStateOf("") }
    fun tryExit() { if (viewModel.isDirty && !state.saving) confirmExit = true else onDone() }
    BackHandler { tryExit() }

    // 사진은 시스템 사진 선택기로 1장 — 권한이 필요 없다(문구 편집과 같은 길).
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { viewModel.pickImage(it) }
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = AppTheme.colors.surface,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            if (viewModel.isEdit) "대표질문 수정" else "대표질문 등록",
                            style = AppType.title, color = AppTheme.colors.text
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { tryExit() }) {
                            Icon(Icons.Default.ArrowBack, "뒤로", tint = AppTheme.colors.text)
                        }
                    },
                    actions = {
                        if (viewModel.isEdit) {
                            TextButton(onClick = { confirmDelete = true }, enabled = state.loaded && !state.saving) {
                                Text("삭제", style = AppType.label, color = AppTheme.colors.unpaid)
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = AppTheme.colors.surface)
                )
            },
            bottomBar = {
                // 프로토 .fbtns — [미리보기][등록하기] 나란히, 위에 얇은 선. 키보드/네비바에 안 가리게 공용 여백.
                Column(Modifier.fillMaxWidth().background(AppTheme.colors.surface).keyboardOrNavPadding()) {
                    Box(Modifier.fillMaxWidth().height(1.dp).background(AppTheme.colors.line))
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = AppSpace.s12),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        TossSecondaryButton(
                            text = "미리보기",
                            onClick = {
                                if (state.answer.isBlank()) {
                                    android.widget.Toast.makeText(context, "답변을 먼저 써 주세요", android.widget.Toast.LENGTH_SHORT).show()
                                } else {
                                    keyboard?.hide(); showPreview = true
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                        TossPrimaryButton(
                            text = if (viewModel.isEdit) "저장하기" else "등록하기",
                            onClick = { viewModel.save(onDone) },
                            modifier = Modifier.weight(1f),
                            enabled = state.canSave
                        )
                    }
                }
            }
        ) { inner ->
            Column(
                Modifier.padding(inner).fillMaxSize()
                    // 키보드 여백은 스크롤 **바깥** — 뷰포트가 줄어 쓰는 칸이 키보드 위로 따라 올라온다.
                    .keyboardPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = AppSpace.s16).padding(top = 2.dp, bottom = AppSpace.s16)
            ) {
                FormLabel("분류", tag = "손님이 이 묶음으로 찾아요")
                CategoryPicker(
                    categories = categories,
                    selected = state.category,
                    onSelect = viewModel::setCategory,
                    onNew = { newCatText = ""; showNewCat = true },
                    enabled = state.loaded
                )
                FormLabel("질문", RepQuestionRules.counter(state.question, RepQuestionRules.QUESTION_MAX))
                SheetTextField(
                    value = state.question,
                    onValueChange = viewModel::setQuestion,
                    placeholder = "자주 묻는 질문 또는 제목을 써 주세요.",
                    enabled = state.loaded
                )
                FormLabel("답변", RepQuestionRules.counter(state.answer, RepQuestionRules.ANSWER_MAX))
                if (state.refining) {
                    // 다듬는 중 — 입력칸 자리에 시머 스켈레톤. 끝나면 결과가 촥 들어온다.
                    Column(
                        Modifier.fillMaxWidth().clip(AppShape.lg).background(AppTheme.colors.surfaceMuted)
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ShimmerLine(Modifier.fillMaxWidth())
                        ShimmerLine(Modifier.fillMaxWidth(0.9f))
                        ShimmerLine(Modifier.fillMaxWidth(0.6f))
                    }
                } else {
                    SheetTextField(
                        value = state.answer,
                        onValueChange = viewModel::setAnswer,
                        placeholder = "질문에 대한 답이나 안내할 내용을 써 주세요.",
                        singleLine = false, minHeightDp = 92,
                        enabled = state.loaded
                    )
                }
                // 답변을 전문 상담 톤으로 AI 다듬기 — 기존 다듬기(/faq/refine) 재사용.
                Row(Modifier.fillMaxWidth().padding(top = AppSpace.s8), horizontalArrangement = Arrangement.End) {
                    val canRefine = state.loaded && !state.refining && state.answer.isNotBlank()
                    Box(
                        Modifier.clip(AppShape.md)
                            .background(if (canRefine) AppTheme.colors.primaryBg else AppTheme.colors.surfaceMuted)
                            .clickable(enabled = canRefine) { keyboard?.hide(); viewModel.refineAnswer() }
                            .padding(horizontal = 14.dp, vertical = 9.dp)
                    ) {
                        Text(
                            if (state.refining) "다듬는 중…" else "전문 톤으로 다듬기",
                            style = AppType.label,
                            color = if (canRefine) AppTheme.colors.primaryText else AppTheme.colors.textHint
                        )
                    }
                }
                FormLabel("답장 내 이미지", tag = "선택")
                ImagePick(
                    imageUri = state.imageUri,
                    busy = state.pickingImage,
                    onPick = {
                        pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    onRemove = viewModel::removeImage
                )
            }
        }

        // 미리보기 — 답장칸에 어떻게 들어가는지(사진 + 내 말풍선). 보기만 한다.
        if (showPreview) {
            AppSheet(onDismiss = { showPreview = false }) {
                Text("미리보기", style = AppType.title, color = AppTheme.colors.text)
                Spacer(Modifier.height(AppSpace.s4))
                Text(
                    "손님 공개 페이지에서 이렇게 보여요. 질문을 누르면 답이 펼쳐져요.",
                    style = AppType.caption, color = AppTheme.colors.textHint
                )
                Spacer(Modifier.height(AppSpace.s16))
                // 손님 페이지 FAQ 카드(펼친 모습) — 흰 카드 · 분류 딱지 · Q. 질문 · 답 · 사진.
                Column(
                    Modifier.fillMaxWidth().clip(AppShape.lg).border(1.dp, AppTheme.colors.line, AppShape.lg)
                        .background(AppTheme.colors.surface).padding(14.dp)
                ) {
                    if (state.category.isNotBlank()) {
                        Text(
                            state.category, style = AppType.caption, color = AppTheme.colors.primaryText,
                            modifier = Modifier.clip(AppShape.sm).background(AppTheme.colors.primaryBg)
                                .padding(horizontal = AppSpace.s8, vertical = 2.dp)
                        )
                        Spacer(Modifier.height(AppSpace.s8))
                    }
                    Row {
                        Text("Q.", style = AppType.headline, fontWeight = FontWeight.Black, color = AppTheme.colors.primary)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            state.question.ifBlank { "질문" }, style = AppType.headline,
                            fontWeight = FontWeight.ExtraBold, color = AppTheme.colors.text, modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(Modifier.height(AppSpace.s8))
                    Text(state.answer.trim(), style = AppType.body, color = AppTheme.colors.textSub)
                    state.imageUri?.let { uri ->
                        Spacer(Modifier.height(10.dp))
                        AsyncImage(
                            model = uri, contentDescription = "답에 함께 보이는 사진",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().height(160.dp).clip(AppShape.md).background(AppTheme.colors.surfaceMuted)
                        )
                    }
                }
                Spacer(Modifier.height(AppSpace.s8))
            }
        }
    }

    if (confirmExit) {
        AlertDialog(
            containerColor = Color.White,
            tonalElevation = 0.dp,
            onDismissRequest = { confirmExit = false },
            title = { Text("저장 안 하고 나갈까요?") },
            text = { Text("쓴 내용이 사라져요.") },
            confirmButton = { TextButton(onClick = { confirmExit = false; onDone() }) { Text("나가기") } },
            dismissButton = { TextButton(onClick = { confirmExit = false }) { Text("계속 작성") } }
        )
    }
    if (confirmDelete) {
        AlertDialog(
            containerColor = Color.White,
            tonalElevation = 0.dp,
            onDismissRequest = { confirmDelete = false },
            title = { Text("이 대표질문을 지울까요?", fontWeight = FontWeight.Bold, color = AppTheme.colors.text) },
            text = { Text("질문과 답이 삭제돼요.", color = AppTheme.colors.textSub) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; viewModel.delete(onDone) }) {
                    Text("삭제", color = AppTheme.colors.unpaid, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("취소", color = AppTheme.colors.textSub) }
            }
        )
    }
    if (showNewCat) {
        AlertDialog(
            containerColor = Color.White,
            tonalElevation = 0.dp,
            onDismissRequest = { showNewCat = false },
            title = { Text("새 분류", fontWeight = FontWeight.Bold, color = AppTheme.colors.text) },
            text = {
                OutlinedTextField(
                    value = newCatText,
                    onValueChange = { newCatText = RepQuestionRules.clampCategory(it) },
                    singleLine = true,
                    placeholder = { Text("예) 예약·결제") }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { if (newCatText.isNotBlank()) viewModel.setCategory(newCatText); showNewCat = false },
                    enabled = newCatText.isNotBlank()
                ) { Text("추가", color = AppTheme.colors.primary, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showNewCat = false }) { Text("취소", color = AppTheme.colors.textSub) }
            }
        )
    }
}

/** 프로토 .catpick — 분류 칩(알약). 고른 칩은 파랗게, 같은 칩을 다시 누르면 해제(미분류). 끝에 「＋ 새 분류」. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryPicker(
    categories: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    onNew: () -> Unit,
    enabled: Boolean
) {
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AppSpace.s8),
        verticalArrangement = Arrangement.spacedBy(AppSpace.s8)
    ) {
        // 고른 분류가 추천 목록에 없으면(막 만든 것) 맨 앞에 보여준다.
        val shown = if (selected.isNotBlank() && selected !in categories) listOf(selected) + categories else categories
        shown.forEach { c ->
            CatChip(text = c, selected = c == selected, enabled = enabled) {
                onSelect(if (selected == c) "" else c)
            }
        }
        CatChip(text = "＋ 새 분류", selected = false, dashed = true, enabled = enabled, onClick = onNew)
    }
}

@Composable
private fun CatChip(
    text: String,
    selected: Boolean,
    dashed: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val bg = if (selected) AppTheme.colors.primary else AppTheme.colors.surface
    val fg = when {
        selected -> AppTheme.colors.textOnPrimary
        dashed -> AppTheme.colors.primaryText
        else -> AppTheme.colors.textSub
    }
    Box(
        Modifier.clip(CircleShape).background(bg)
            .border(1.dp, if (selected) AppTheme.colors.primary else AppTheme.colors.line, CircleShape)
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 14.dp, vertical = 9.dp)
    ) {
        Text(text, style = AppType.label, color = fg)
    }
}

/** 프로토 .flabel — 굵은 라벨, 오른쪽 끝에 글자 수([counter]) 또는 라벨 바로 옆 작은 [tag](「선택」). */
@Composable
private fun FormLabel(text: String, counter: String? = null, tag: String? = null) {
    Row(
        Modifier.fillMaxWidth().padding(top = 14.dp, bottom = AppSpace.s8),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, style = AppType.headline, fontWeight = FontWeight.ExtraBold, color = AppTheme.colors.text)
        if (tag != null) {
            Spacer(Modifier.width(AppSpace.s4))
            Text(tag, style = AppType.caption, fontWeight = FontWeight.SemiBold, color = AppTheme.colors.textHint)
        }
        Spacer(Modifier.weight(1f))
        if (counter != null) {
            Text(counter, style = AppType.caption, fontWeight = FontWeight.SemiBold, color = AppTheme.colors.neutral)
        }
    }
}

/** 사진 0/1 — 없으면 「📷 0/1」 칸(누르면 고르기), 있으면 썸네일 + ✕(빼기). 썸네일을 누르면 다른 사진으로 바꾼다. */
@Composable
private fun ImagePick(imageUri: String?, busy: Boolean, onPick: () -> Unit, onRemove: () -> Unit) {
    if (imageUri == null) {
        Column(
            Modifier.size(IMAGE_TILE).clip(AppShape.md).background(AppTheme.colors.surfaceMuted)
                .clickable(enabled = !busy) { onPick() },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Default.PhotoCamera, "사진 고르기", tint = AppTheme.colors.textHint, modifier = Modifier.size(22.dp))
            Spacer(Modifier.height(3.dp))
            Text(
                if (busy) "담는 중" else "0/${RepQuestionRules.IMAGE_MAX}",
                style = AppType.caption, color = AppTheme.colors.textHint
            )
        }
    } else {
        Box(Modifier.size(IMAGE_TILE)) {
            AsyncImage(
                model = imageUri, contentDescription = "답장 내 이미지 — 누르면 바꾸기",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(AppShape.md).background(AppTheme.colors.surfaceMuted)
                    .clickable(enabled = !busy) { onPick() }
            )
            Box(
                Modifier.align(Alignment.TopEnd).padding(AppSpace.s4).size(22.dp).clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f)).clickable { onRemove() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Close, "사진 빼기", tint = Color.White, modifier = Modifier.size(14.dp))
            }
        }
    }
}
