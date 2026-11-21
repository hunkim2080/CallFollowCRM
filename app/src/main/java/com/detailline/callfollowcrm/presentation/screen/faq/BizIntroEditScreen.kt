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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.detailline.callfollowcrm.presentation.component.SheetTextField
import com.detailline.callfollowcrm.presentation.component.TossPrimaryButton
import com.detailline.callfollowcrm.presentation.theme.AppShape
import com.detailline.callfollowcrm.presentation.theme.AppSpace
import com.detailline.callfollowcrm.presentation.theme.AppTheme
import com.detailline.callfollowcrm.presentation.theme.AppType
import com.detailline.callfollowcrm.presentation.util.keyboardOrNavPadding
import com.detailline.callfollowcrm.presentation.util.keyboardPadding

/** 대표 사진 썸네일 한 변. */
private val COVER_TILE = 72.dp

/**
 * 🏷️ **업체 소개 편집** — 손님 공개 FAQ 페이지 상단(톤·슬로건·소개글·대표 사진+설명·경력·건수). 프로토 7U7dnG1GFEoD2RMmd7gRmk ②.
 *   느낌은 사장님이 정한다 — 톤 프리셋으로 슬로건을 채워 넣고 고쳐 쓴다. 업체명·지역은 사업자정보에서 읽어 보여주기만.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BizIntroEditScreen(
    viewModel: BizIntroEditViewModel,
    onDone: () -> Unit,
    onEditBizInfo: () -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val toast by viewModel.toast.collectAsState()
    LaunchedEffect(toast) {
        toast?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_SHORT).show()
            viewModel.consumeToast()
        }
    }
    // 사업자 정보 화면 다녀오면 상호·지역을 다시 읽는다(등록하고 돌아왔을 때 바로 반영).
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) viewModel.reloadBizInfo() }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    var confirmExit by remember { mutableStateOf(false) }
    fun tryExit() { if (viewModel.isDirty && !state.saving) confirmExit = true else onDone() }
    BackHandler { tryExit() }

    // 여러 장 한 번에 — 최대 5장(합쳐서 넘치면 VM 이 잘라 말한다). 권한 필요 없는 시스템 선택기.
    val pickImages = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(BizIntroEditViewModel.COVER_MAX)
    ) { uris ->
        if (uris.isNotEmpty()) viewModel.addPhotos(uris)
    }

    Scaffold(
        containerColor = AppTheme.colors.surface,
        topBar = {
            TopAppBar(
                title = { Text("업체 소개", style = AppType.title, color = AppTheme.colors.text) },
                navigationIcon = {
                    IconButton(onClick = { tryExit() }) {
                        Icon(Icons.Default.ArrowBack, "뒤로", tint = AppTheme.colors.text)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppTheme.colors.surface)
            )
        },
        bottomBar = {
            Column(
                Modifier.fillMaxWidth().background(AppTheme.colors.surface).keyboardOrNavPadding()
                    .padding(start = 14.dp, end = 14.dp, top = 6.dp, bottom = AppSpace.s16)
            ) {
                TossPrimaryButton(text = "저장하기", onClick = { viewModel.save(onDone) }, enabled = !state.saving)
            }
        }
    ) { inner ->
        Column(
            Modifier.padding(inner).fillMaxSize()
                .keyboardPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AppSpace.s16).padding(top = 2.dp, bottom = AppSpace.s16)
        ) {
            // 상호·지역은 사업자 정보에서 가져온다. 탭하면 그 화면으로(없으면 등록 유도).
            val hasBiz = state.bizName.isNotBlank()
            Row(
                Modifier.fillMaxWidth().clip(AppShape.lg)
                    .background(if (hasBiz) AppTheme.colors.surfaceMuted else AppTheme.colors.primaryBg)
                    .clickable { onEditBizInfo() }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (hasBiz) state.bizName else "사업자 정보를 먼저 등록해주세요",
                        style = AppType.headline, fontWeight = FontWeight.ExtraBold,
                        color = if (hasBiz) AppTheme.colors.text else AppTheme.colors.primaryText
                    )
                    if (hasBiz && state.region.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(state.region, style = AppType.caption, color = AppTheme.colors.textSub)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (hasBiz) "상호·지역은 사업자 정보에서 가져와요. 탭해서 수정"
                        else "상호·지역이 손님 페이지에 그대로 나와요. 탭해서 등록",
                        style = AppType.caption, color = AppTheme.colors.textHint
                    )
                }
                Text("›", style = AppType.title, color = AppTheme.colors.neutral)
            }

            // 톤 프리셋 — 누르면 슬로건에 채워진다(고쳐 쓸 수 있음).
            BizFormLabel("느낌 고르기", tag = "눌러서 슬로건 채우기")
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppSpace.s8),
                verticalArrangement = Arrangement.spacedBy(AppSpace.s8)
            ) {
                BizIntroEditViewModel.TONE_PRESETS.forEach { (name, slogan) ->
                    ToneChip(name, selected = state.slogan == slogan) { viewModel.applyPreset(slogan) }
                }
                ToneChip("직접 쓰기", selected = false, dashed = true) { viewModel.setSlogan("") }
            }

            BizFormLabel("슬로건", tag = "제목 위 한 줄 · 비우면 안 나와요")
            SheetTextField(
                value = state.slogan,
                onValueChange = viewModel::setSlogan,
                placeholder = "예) 작은 틈까지, 정성스럽게"
            )

            BizFormLabel("대표 시공 사진", tag = "최대 ${BizIntroEditViewModel.COVER_MAX}장 · 설명은 선택")
            CoverPhotoList(
                photos = state.photos,
                captions = state.captions,
                busy = state.pickingImage,
                onAdd = { pickImages.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                onCaption = viewModel::setCaption,
                onRemove = viewModel::removePhotoAt
            )

            BizFormLabel("소개글", counter = "${state.intro.length}/${BizIntroEditViewModel.INTRO_MAX}")
            SheetTextField(
                value = state.intro,
                onValueChange = viewModel::setIntro,
                placeholder = "예) 안녕하세요, ○○입니다. 상담부터 시공, 마무리까지 대표가 직접 챙깁니다.",
                singleLine = false, minHeightDp = 92
            )

            BizFormLabel("경력", tag = "선택")
            SheetTextField(value = state.career, onValueChange = viewModel::setCareer, placeholder = "예) 10 또는 경력 10년")

            BizFormLabel("시공 건수", tag = "선택")
            SheetTextField(value = state.count, onValueChange = viewModel::setCount, placeholder = "예) 1200 또는 시공 1,200건")
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
}

/** 톤 프리셋 칩(알약). 고른 것은 파랗게. 「직접 쓰기」는 점선. */
@Composable
private fun ToneChip(text: String, selected: Boolean, dashed: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.clip(CircleShape)
            .background(if (selected) AppTheme.colors.primary else AppTheme.colors.surface)
            .border(1.dp, if (selected) AppTheme.colors.primary else AppTheme.colors.line, CircleShape)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 9.dp)
    ) {
        Text(
            text, style = AppType.label,
            color = when { selected -> AppTheme.colors.textOnPrimary; dashed -> AppTheme.colors.primaryText; else -> AppTheme.colors.textSub }
        )
    }
}

/** 굵은 라벨 + 오른쪽 끝 글자수(counter) 또는 작은 태그(tag). */
@Composable
private fun BizFormLabel(text: String, counter: String? = null, tag: String? = null) {
    Row(
        Modifier.fillMaxWidth().padding(top = 16.dp, bottom = AppSpace.s8),
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

/** 대표 사진 세로 목록 — 썸네일(✕로 빼기) + 옆에 설명 칸. 끝에 「＋ 사진 추가」. */
@Composable
private fun CoverPhotoList(
    photos: List<String>,
    captions: List<String>,
    busy: Boolean,
    onAdd: () -> Unit,
    onCaption: (Int, String) -> Unit,
    onRemove: (Int) -> Unit
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(AppSpace.s8)) {
        photos.forEachIndexed { i, uri ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppSpace.s8)) {
                Box(Modifier.size(COVER_TILE)) {
                    AsyncImage(
                        model = uri, contentDescription = "대표 사진 ${i + 1}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(AppShape.md).background(AppTheme.colors.surfaceMuted)
                    )
                    Box(
                        Modifier.align(Alignment.TopEnd).padding(3.dp).size(20.dp).clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.55f)).clickable { onRemove(i) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Close, "사진 빼기", tint = Color.White, modifier = Modifier.size(13.dp))
                    }
                }
                SheetTextField(
                    value = captions.getOrElse(i) { "" },
                    onValueChange = { onCaption(i, it) },
                    placeholder = "사진 설명 (예: 시공 전·후)",
                    modifier = Modifier.weight(1f)
                )
            }
        }
        if (photos.size < BizIntroEditViewModel.COVER_MAX) {
            Row(
                Modifier.fillMaxWidth().clip(AppShape.md).background(AppTheme.colors.surfaceMuted)
                    .clickable(enabled = !busy) { onAdd() }.padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Add, null, tint = AppTheme.colors.textHint, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(AppSpace.s4))
                Text(if (busy) "담는 중" else "＋ 사진 추가", style = AppType.label, color = AppTheme.colors.textHint)
            }
        }
    }
}
