package com.detailline.callfollowcrm.presentation.screen.chat

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.detailline.callfollowcrm.data.repository.DeviceGalleryRepository
import com.detailline.callfollowcrm.data.repository.DeviceGalleryRepository.GalleryPhoto
import com.detailline.callfollowcrm.domain.ui.PickLimit
import com.detailline.callfollowcrm.presentation.component.AppSheet
import com.detailline.callfollowcrm.presentation.component.FullscreenPhotoViewer
import com.detailline.callfollowcrm.presentation.component.ViewerPhoto
import com.detailline.callfollowcrm.presentation.screen.customer.PartialAccessBar
import com.detailline.callfollowcrm.presentation.theme.AppShape
import com.detailline.callfollowcrm.presentation.theme.AppTheme
import com.detailline.callfollowcrm.presentation.theme.AppType
import com.detailline.callfollowcrm.util.PermissionHelper
import com.detailline.callfollowcrm.util.PhotoMarkRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 🖼️ **카톡식 사진 첨부 — 채팅 ⊕ 「사진 첨부」.** (2026-11-21 사장님 「가」 · 프로토 6tW9xbZXAVJd5jj32uRYiH)
 *
 *   ⊕ → **바텀시트**(최근 사진 가로줄 · 카메라 · 전체 보기) → 사진 탭 → **그 자리에서 펜**(공용 뷰어) →
 *   「첨부」 = **입력칸에 담기만**(전송 아님). 전엔 시스템 선택기로 담은 뒤 **썸네일을 다시 눌러야** 펜이 나왔다.
 *
 *   새로 만든 건 **이 시트 한 장**뿐이다 — 나머지는 전부 있는 걸 쓴다(§12):
 *     사진첩 읽기 `DeviceGalleryRepository` · 권한 `PermissionHelper` · 시트 틀 `AppSheet` ·
 *     펜 `FullscreenPhotoViewer(onAttachDirect)` · 합성 `PhotoMarkRenderer.renderToFileUri` ·
 *     몇 장까지 `PickLimit` · 부분 허용 띠 `PartialAccessBar`(현장 사진 선택창과 같은 것).
 *
 *   권한을 안 주거나(거부) 한도가 차면 **화면에 말하고**, 거부면 시스템 선택기로 넘긴다(조용히 죽지 않는다 §13).
 */

/** 가로줄에 보여줄 최근 사진 수. 더 보려면 「전체 보기」(시스템 선택기). */
private const val RECENT_LIMIT = 30

/** 타일 한 변(프로토 .ph 112px). */
private val TILE = 112.dp

/** [AppSheet] 안쪽 좌우 여백 — 가로줄·구분선이 시트 끝까지 닿게 이만큼 밖으로 뺀다. */
private val SHEET_SIDE = 20.dp

private fun say(ctx: Context, msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()

/**
 * 담긴 사진 뒤에 [add] 를 붙인다 — 같은 사진은 한 번만, 전체 [PickLimit.CHAT_MAX] 장까지.
 *   못 담은 게 있으면 **말한다**. 시스템 선택기로 고른 것도 이 길로 담는다(한도가 한 곳).
 */
internal fun attachCapped(ctx: Context, attached: List<Uri>, add: List<Uri>): List<Uri> {
    if (PickLimit.dropped(attached, add) > 0) say(ctx, "사진은 ${PickLimit.CHAT_MAX}장까지 담을 수 있어요")
    return PickLimit.merge(attached, add)
}

/**
 * 채팅 화면이 부르는 **입구 하나** — 권한 확인/요청 · 시트 · 펜 화면 · 카메라를 여기서 다 한다.
 *   [request] 가 true 가 되면 연다([onRequestHandled] 로 곧장 내려준다).
 *   [onSystemPicker] = 시스템 사진 선택기(「전체 보기」 · 권한 거부 시 폴백). [onAttached] = 새 첨부 목록 전체.
 */
@Composable
internal fun ChatPhotoPickerHost(
    request: Boolean,
    onRequestHandled: () -> Unit,
    attached: List<Uri>,
    onSystemPicker: () -> Unit,
    onAttached: (List<Uri>) -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val attachedNow by rememberUpdatedState(attached)
    var show by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<List<Uri>>(emptyList()) }
    // 펜 화면에 띄운 사진. null 이면 안 떠 있다.
    var editing by remember { mutableStateOf<Uri?>(null) }
    // 「첨부」 연타 막기 — 합성(획 얹기)이 도는 동안 또 눌러도 한 번만 담긴다.
    var busy by remember { mutableStateOf(false) }
    // 카메라가 찍어 넣을 자리. 카메라 앱에 다녀오는 동안 화면이 다시 만들어져도 기억한다.
    var cameraUri by rememberSaveable { mutableStateOf<String?>(null) }

    val room = PickLimit.remaining(attachedNow.size)

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        if (PermissionHelper.hasGalleryImages(ctx)) {
            selected = emptyList(); show = true
        } else {
            say(ctx, "사진첩 권한이 없어 기본 사진 선택창으로 열어요")
            onSystemPicker()
        }
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val shot = cameraUri?.let { runCatching { Uri.parse(it) }.getOrNull() }
        cameraUri = null
        // 찍었으면 고른 것과 똑같이 **곧바로 펜 화면**으로. 취소면 시트 그대로.
        if (ok && shot != null) editing = shot
    }

    LaunchedEffect(request) {
        if (request) {
            onRequestHandled()
            when {
                room == 0 -> say(ctx, "사진은 ${PickLimit.CHAT_MAX}장까지 담을 수 있어요")
                PermissionHelper.hasGalleryImages(ctx) -> { selected = emptyList(); show = true }
                else -> permLauncher.launch(PermissionHelper.galleryImagePermissions())
            }
        }
    }

    /** 담고 닫는다 — 시트·펜 화면 둘 다. */
    fun finish(add: List<Uri>, msg: String?) {
        onAttached(attachCapped(ctx, attachedNow, add))
        selected = emptyList(); editing = null; show = false
        if (msg != null) say(ctx, msg)
    }

    fun openCamera() {
        // 견적서·사업자등록증과 같은 길(cacheDir/shared + FileProvider). 찍을 때마다 새 파일.
        val uri = runCatching {
            val dir = java.io.File(ctx.cacheDir, "shared").apply { mkdirs() }
            val f = java.io.File(dir, "chat_capture_${System.currentTimeMillis()}.jpg")
            androidx.core.content.FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f)
        }.getOrNull()
        val opened = uri != null && runCatching { cameraUri = uri.toString(); cameraLauncher.launch(uri) }.isSuccess
        if (!opened) { cameraUri = null; say(ctx, "카메라를 열지 못했어요") }
    }

    if (show) {
        ChatPhotoPickerSheet(
            selected = selected,
            onToggle = { uri ->
                if (PickLimit.isFull(selected, uri, room)) say(ctx, "사진은 ${PickLimit.CHAT_MAX}장까지 담을 수 있어요")
                selected = PickLimit.toggle(selected, uri, room)
            },
            onEdit = { uri ->
                // 고른 것만으로 자리가 찼으면 펜 화면을 열지 않는다(그려놓고 못 담는 일 없게).
                if (PickLimit.isFull(selected, uri, room)) say(ctx, "사진은 ${PickLimit.CHAT_MAX}장까지 담을 수 있어요")
                else editing = uri
            },
            onCamera = {
                if (selected.size >= room) say(ctx, "사진은 ${PickLimit.CHAT_MAX}장까지 담을 수 있어요")
                else openCamera()
            },
            onAlbum = {
                // 고른 게 있으면 그건 담아두고 넘어간다 — 말없이 버리지 않는다.
                finish(selected, null)
                onSystemPicker()
            },
            onAttachSelected = {
                if (selected.isNotEmpty()) finish(selected, "사진 ${selected.size}장을 담았어요")
            },
            onDismiss = { show = false; selected = emptyList() }
        )
    }

    // ✏️ 고른 직후 **바로 펜** — 공용 뷰어 그대로(색·굵기·되돌리기·전체 지우기). 「첨부」 = 입력칸에 담기.
    editing?.let { src ->
        FullscreenPhotoViewer(
            photos = listOf(ViewerPhoto.OfFile(src)),
            onDismiss = { if (!busy) editing = null },
            onAttachDirect = { marks ->
                if (!busy) {
                    busy = true
                    scope.launch {
                        // 안 그렸으면 원본 그대로. 그렸으면 획을 얹은 새 파일(갤러리 원본은 안 건드린다).
                        val out = if (marks.isEmpty()) src
                        else PhotoMarkRenderer.renderToFileUri(ctx, src, marks)
                        busy = false
                        if (out == null) {
                            // 펜 화면은 그대로 둔다 — 그린 게 안 날아가고 다시 누를 수 있다.
                            say(ctx, "표시를 적용하지 못했어요")
                        } else {
                            finish(
                                selected.filter { it != src } + out,
                                if (marks.isEmpty()) "사진을 담았어요" else "사진에 표시해서 담았어요"
                            )
                        }
                    }
                }
            }
        )
    }
}

@Composable
private fun ChatPhotoPickerSheet(
    selected: List<Uri>,
    onToggle: (Uri) -> Unit,
    onEdit: (Uri) -> Unit,
    onCamera: () -> Unit,
    onAlbum: () -> Unit,
    onAttachSelected: () -> Unit,
    onDismiss: () -> Unit,
) {
    val ctx = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    // null = 불러오는 중(자리표시 타일). 34+ 「사진 더 고르기」 로 범위가 바뀌면 다시 읽는다.
    var photos by remember { mutableStateOf<List<GalleryPhoto>?>(null) }
    var reloadKey by remember { mutableIntStateOf(0) }
    val partial = remember(reloadKey) { PermissionHelper.isPartialGallery(ctx) }
    val morePhotosLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { reloadKey++ }

    // 키보드가 떠 있으면 내린다 — 안 그러면 시트가 키보드 위에 얹혀 사진이 반만 보인다.
    LaunchedEffect(Unit) { keyboard?.hide() }
    LaunchedEffect(reloadKey) {
        photos = null
        photos = withContext(Dispatchers.IO) { DeviceGalleryRepository.query(ctx, null, limit = RECENT_LIMIT) }
    }

    AppSheet(onDismiss = onDismiss) {
        // 머리: 사진 첨부 ……… [첨부]
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "사진 첨부", style = AppType.headline, fontWeight = FontWeight.ExtraBold,
                color = AppTheme.colors.text, modifier = Modifier.weight(1f)
            )
            val can = selected.isNotEmpty()
            Box(
                Modifier.clip(AppShape.pill)
                    .background(if (can) AppTheme.colors.primary else AppTheme.colors.neutral.copy(alpha = 0.4f))
                    .clickable(enabled = can) { onAttachSelected() }
                    .padding(horizontal = 15.dp, vertical = 7.dp)
            ) {
                Text("첨부", style = AppType.label, color = AppTheme.colors.textOnPrimary)
            }
        }
        Spacer(Modifier.height(10.dp))

        // 🔒 34+ 부분 허용 — 「최근 사진」이 폰 전체가 아니라 허용한 사진만이라는 걸 알린다.
        if (partial) {
            PartialAccessBar { morePhotosLauncher.launch(PermissionHelper.galleryImagePermissions()) }
            Spacer(Modifier.height(10.dp))
        }

        // 최근 사진 가로줄 — 맨 앞은 카메라. 시트 끝까지 닿게 좌우 여백 밖으로 뺀다.
        val list = photos
        LazyRow(
            modifier = Modifier.bleed(SHEET_SIDE).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item(key = "camera") { CameraTile(onCamera) }
            when {
                // 불러오는 중 — 자리표시 타일(없다가 툭 생기지 않게).
                list == null -> items(3) { Box(Modifier.size(TILE).clip(AppShape.md).background(AppTheme.colors.surfaceMuted)) }
                list.isEmpty() -> item(key = "empty") {
                    Box(Modifier.height(TILE).padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
                        Text(
                            if (partial) "허용한 사진이 없어요." else "사진이 없어요.",
                            style = AppType.body, color = AppTheme.colors.textSub
                        )
                    }
                }
                else -> items(list, key = { it.uri.toString() }) { p ->
                    RecentTile(
                        photo = p,
                        order = selected.indexOf(p.uri) + 1,
                        onEdit = { onEdit(p.uri) },
                        onToggle = { onToggle(p.uri) }
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        // 전체 보기 — 시스템 사진 선택기(앨범 전체).
        Box(Modifier.bleed(SHEET_SIDE).fillMaxWidth().height(1.dp).background(AppTheme.colors.line))
        Row(
            Modifier.bleed(SHEET_SIDE).fillMaxWidth().clickable { onAlbum() }
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(30.dp).clip(AppShape.sm).background(AppTheme.colors.primaryBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.PhotoLibrary, null, tint = AppTheme.colors.primaryText, modifier = Modifier.size(17.dp))
            }
            Spacer(Modifier.width(13.dp))
            Text(
                "전체 보기 (앨범에서 고르기)", style = AppType.headline, fontWeight = FontWeight.SemiBold,
                color = AppTheme.colors.text
            )
        }

        // 안내 — 프로토 .hint 글자 그대로.
        Text(
            buildAnnotatedString {
                append("사진을 탭하면 ")
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("그 자리에서 펜으로 표시") }
                append("하고 담을 수 있어요. 바로 전송되지 않고 ")
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("입력칸에 담깁니다.") }
            },
            style = AppType.caption, color = AppTheme.colors.textHint
        )
    }
}

/** 카메라 타일 — 찍으면 곧바로 펜 화면. */
@Composable
private fun CameraTile(onClick: () -> Unit) {
    Column(
        Modifier.size(TILE).clip(AppShape.md).background(AppTheme.colors.surfaceMuted).clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.PhotoCamera, null, tint = AppTheme.colors.textHint, modifier = Modifier.size(28.dp))
        Spacer(Modifier.height(6.dp))
        Text("카메라", style = AppType.caption, fontWeight = FontWeight.Bold, color = AppTheme.colors.textHint)
    }
}

/**
 * 최근 사진 한 칸. **칸을 누르면 펜 화면**(오른쪽 아래 ⤢ 가 그 뜻), **왼쪽 위 동그라미는 그냥 고르기**(여러 장).
 *   [order] = 고른 순서(1..), 안 골랐으면 0.
 */
@Composable
private fun RecentTile(photo: GalleryPhoto, order: Int, onEdit: () -> Unit, onToggle: () -> Unit) {
    val picked = order > 0
    Box(
        Modifier.size(TILE).clip(AppShape.md).background(AppTheme.colors.surfaceMuted).clickable { onEdit() }
    ) {
        coil.compose.AsyncImage(
            model = photo.uri,
            contentDescription = "사진 — 누르면 펜으로 표시",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            Modifier.align(Alignment.BottomEnd).padding(6.dp).size(26.dp)
                .clip(AppShape.sm).background(Color.Black.copy(alpha = 0.45f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.OpenInFull, null, tint = Color.White, modifier = Modifier.size(14.dp))
        }
        // 동그라미는 22dp 지만 누르는 자리는 44dp — 장갑 낀 손으로도 칸(펜)과 헷갈리지 않게.
        Box(
            Modifier.align(Alignment.TopStart).size(44.dp)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onToggle() }
        ) {
            Box(
                Modifier.padding(7.dp).size(22.dp).clip(CircleShape)
                    .background(if (picked) AppTheme.colors.primary else Color.Black.copy(alpha = 0.2f))
                    .border(2.dp, if (picked) AppTheme.colors.primary else Color.White, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (picked) Text("$order", style = AppType.micro, color = AppTheme.colors.textOnPrimary)
            }
        }
    }
}

/** 부모의 좌우 여백([side])만큼 밖으로 넓힌다 — 시트 안쪽 여백을 뚫고 끝에서 끝까지. */
private fun Modifier.bleed(side: Dp): Modifier = layout { measurable, constraints ->
    val px = side.roundToPx()
    val placeable = measurable.measure(constraints.copy(maxWidth = constraints.maxWidth + px * 2))
    layout((placeable.width - px * 2).coerceAtLeast(0), placeable.height) { placeable.place(-px, 0) }
}
