package com.detailline.callfollowcrm.presentation.screen.faq

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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

/** 대표 사진 칸 한 변. */
private val COVER_TILE = 92.dp

/**
 * 🏷️ **업체 소개 편집** — 손님 공개 FAQ 페이지 상단(소개글·대표 사진·경력·건수). 프로토 7U7dnG1GFEoD2RMmd7gRmk ②.
 *   업체명·지역은 사업자정보에서 읽어 보여주기만 한다(여기서 안 고침). 저장은 `SaveGuard`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BizIntroEditScreen(
    viewModel: BizIntroEditViewModel,
    onDone: () -> Unit
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

    var confirmExit by remember { mutableStateOf(false) }
    fun tryExit() { if (viewModel.isDirty && !state.saving) confirmExit = true else onDone() }
    BackHandler { tryExit() }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { viewModel.addPhoto(it) }
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
            // 사업자정보에서 읽어 보여주기만(여기서 못 고침) — 손님 페이지엔 이 이름·지역이 그대로 나간다.
            Column(
                Modifier.fillMaxWidth().clip(AppShape.lg).background(AppTheme.colors.surfaceMuted)
                    .padding(14.dp)
            ) {
                Text(
                    state.bizName.ifBlank { "상호를 설정해주세요" },
                    style = AppType.headline, fontWeight = FontWeight.ExtraBold, color = AppTheme.colors.text
                )
                if (state.region.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(state.region, style = AppType.caption, color = AppTheme.colors.textSub)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "상호·지역은 설정 → 사업자 정보에서 가져와요.",
                    style = AppType.caption, color = AppTheme.colors.textHint
                )
            }

            BizFormLabel("대표 시공 사진", tag = "최대 ${BizIntroEditViewModel.COVER_MAX}장")
            CoverPhotos(
                photos = state.photos,
                busy = state.pickingImage,
                onAdd = { pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                onRemove = viewModel::removePhoto
            )

            BizFormLabel("소개글", counter = "${state.intro.length}/${BizIntroEditViewModel.INTRO_MAX}")
            SheetTextField(
                value = state.intro,
                onValueChange = viewModel::setIntro,
                placeholder = "예) 안녕하세요, 10년 경력 줄눈 시공 디테일라인입니다. 꼼꼼한 마감과 1년 무상 A/S로 믿고 맡기실 수 있게 시공합니다.",
                singleLine = false, minHeightDp = 92
            )

            BizFormLabel("경력", tag = "선택")
            SheetTextField(
                value = state.career,
                onValueChange = viewModel::setCareer,
                placeholder = "예) 경력 10년"
            )

            BizFormLabel("시공 건수", tag = "선택")
            SheetTextField(
                value = state.count,
                onValueChange = viewModel::setCount,
                placeholder = "예) 시공 1,200건"
            )
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

/** 대표 사진 가로 줄 — 썸네일(✕로 빼기) + 끝에 「＋」 추가 칸(최대 장수 전까지). */
@Composable
private fun CoverPhotos(
    photos: List<String>,
    busy: Boolean,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(AppSpace.s8)
    ) {
        photos.forEach { uri ->
            Box(Modifier.size(COVER_TILE)) {
                AsyncImage(
                    model = uri, contentDescription = "대표 사진",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(AppShape.md).background(AppTheme.colors.surfaceMuted)
                )
                Box(
                    Modifier.align(Alignment.TopEnd).padding(AppSpace.s4).size(22.dp).clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f)).clickable { onRemove(uri) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Close, "사진 빼기", tint = Color.White, modifier = Modifier.size(14.dp))
                }
            }
        }
        if (photos.size < BizIntroEditViewModel.COVER_MAX) {
            Column(
                Modifier.size(COVER_TILE).clip(AppShape.md).background(AppTheme.colors.surfaceMuted)
                    .clickable(enabled = !busy) { onAdd() },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.Add, "사진 추가", tint = AppTheme.colors.textHint, modifier = Modifier.size(24.dp))
                Spacer(Modifier.height(3.dp))
                Text(if (busy) "담는 중" else "추가", style = AppType.caption, color = AppTheme.colors.textHint)
            }
        }
    }
}
