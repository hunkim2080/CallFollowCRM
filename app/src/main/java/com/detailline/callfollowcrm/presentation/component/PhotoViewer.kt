package com.detailline.callfollowcrm.presentation.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.detailline.callfollowcrm.presentation.theme.AppShape
import com.detailline.callfollowcrm.presentation.util.navBarBottomDp
import com.detailline.callfollowcrm.presentation.theme.AppType

/**
 * 📷 **크게 볼 사진 한 장.**
 *
 *   [OfFile] 은 **내 폰에 있는 내 사진** — [photoId] 가 있으면 돌린 것을 파일에 저장할 수 있다.
 *   [OfBitmap] 은 **서버에서 받아온 남의 사진**(팀원·협업) — 내 파일이 아니라 **돌려 보기만** 된다.
 */
sealed interface ViewerPhoto {
    data class OfFile(
        val uri: android.net.Uri,
        /** 0 이면 내 현장 사진이 아니다 → 돌려도 저장 안 됨. */
        val photoId: Long = 0L
    ) : ViewerPhoto

    data class OfBitmap(val bitmap: android.graphics.Bitmap) : ViewerPhoto
}

/**
 * 📷 **사진을 크게 보는 자리 — 앱 전체에 하나뿐.** (2026-10-01 사장님 "사진 회전기능 왜 추가안됐니")
 *
 * ## 왜 합쳤나
 *   돌리기를 2026-09-30 에 넣었는데 **고객 상세의 '내 사진' 한 군데에만** 들어갔다.
 *   팀원 사진·협업 현장 사진·협업 증거 사진·문자 사진은 **크게 봐도 돌릴 수가 없었다.**
 *   사장님이 늘 말씀하신 "프로토로 잘 만들고 적용할 때 되면 다 빼먹는다" 가 그대로 났다.
 *   → **자리를 하나로 만든다.** 다음에 뭘 더 붙여도 다섯 군데에 한꺼번에 붙는다.
 *
 * ## 규칙
 *   · **돌려 보기는 어디서나** 된다. 사진이 누워 있으면 누구 사진이든 눕혀 보는 건 내 자유다.
 *   · **저장은 내 사진만** — [ViewerPhoto.OfFile] 에 photoId 가 있고 [onSaveRotation] 이 있을 때.
 *     남의 사진을 내가 영영 돌려놓을 수는 없다. 돌리면 그 자리에서 **왜 저장이 안 되는지 말해준다.**
 *   · 돌린 각도는 **화면에만** 두고 파일은 **닫을 때 한 번만** 쓴다. (2026-09-30 사장님)
 *     누를 때마다 쓰면 그만큼 화질이 깎인다. 네 번 눌러 제자리면 **아무것도 안 쓴다.**
 *   · **두 손가락으로 확대**된다. 확대 중엔 좌우 넘김과 탭으로 닫기를 멈춘다 —
 *     안 그러면 들여다보려다 닫힌다.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun FullscreenPhotoViewer(
    photos: List<ViewerPhoto>,
    startIndex: Int = 0,
    onDismiss: () -> Unit,
    /** 내 사진의 돌린 각도를 파일에 저장. 없으면 돌려 보기만 된다. */
    onSaveRotation: ((photoId: Long, degrees: Int) -> Unit)? = null,
    /**
     * 화면 왼쪽 위에 더 얹을 것 — 문자 사진의 「갤러리에 저장」 같은 것.
     *   받는 값은 **지금 보고 있는 쪽 번호**다.
     */
    topStartAction: (@Composable (Int) -> Unit)? = null
) {
    if (photos.isEmpty()) return
    // 쪽마다 돌린 각도. 닫을 때 **한 번에** 저장한다.
    val rotations = remember(photos) { mutableStateMapOf<Int, Int>() }
    val closeAndSave = {
        if (onSaveRotation != null) {
            for ((idx, deg) in rotations) {
                if (deg % 360 == 0) continue
                val p = photos.getOrNull(idx)
                if (p is ViewerPhoto.OfFile && p.photoId > 0L) onSaveRotation(p.photoId, deg)
            }
        }
        onDismiss()
    }
    Dialog(
        onDismissRequest = closeAndSave,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val pagerState = rememberPagerState(
            initialPage = startIndex.coerceIn(0, photos.size - 1)
        ) { photos.size }
        // 확대 중이면 좌우 넘김을 멈춘다 — 들여다보려다 옆 사진으로 넘어가면 성가시다.
        var zoomed by remember { mutableStateOf(false) }
        LaunchedEffect(pagerState.currentPage) { zoomed = false }
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = !zoomed,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                ViewerPage(
                    photo = photos[page],
                    rotation = rotations[page] ?: 0,
                    onRotate = { d -> rotations[page] = (rotations[page] ?: 0) + d },
                    onZoomedChange = { z -> if (page == pagerState.currentPage) zoomed = z },
                    onTapClose = closeAndSave,
                    canSave = onSaveRotation != null &&
                        (photos[page] as? ViewerPhoto.OfFile)?.photoId?.let { it > 0L } == true
                )
            }
            // 여러 장이면 몇 번째인지. 한 장이면 군더더기라 안 띄운다.
            if (photos.size > 1) {
                Box(
                    Modifier.align(Alignment.TopCenter).padding(top = 18.dp)
                        .clip(AppShape.pill).background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    androidx.compose.material3.Text(
                        "${pagerState.currentPage + 1} / ${photos.size}",
                        style = AppType.body, color = Color.White, fontWeight = FontWeight.Bold
                    )
                }
            }
            topStartAction?.let { action ->
                Box(Modifier.align(Alignment.TopStart).padding(14.dp)) {
                    action(pagerState.currentPage)
                }
            }
            Box(
                Modifier.align(Alignment.TopEnd).padding(14.dp).size(38.dp)
                    .clip(AppShape.pill).background(Color.Black.copy(alpha = 0.55f))
                    .clickable { closeAndSave() },
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.material3.Text(
                    "✕", style = AppType.title, color = Color.White, fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/** 한 쪽 — 돌리기 + 두 손가락 확대. */
@Composable
private fun ViewerPage(
    photo: ViewerPhoto,
    rotation: Int,
    onRotate: (Int) -> Unit,
    onZoomedChange: (Boolean) -> Unit,
    onTapClose: () -> Unit,
    canSave: Boolean
) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val isZoomed = scale > 1.01f
    LaunchedEffect(isZoomed) { onZoomedChange(isZoomed) }

    BoxWithConstraints(
        Modifier.fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 5f)
                    offset = if (scale <= 1.01f) Offset.Zero else offset + pan
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    // 두 번 두드리면 확대/원래대로 — 두 손가락이 안 되는 상황의 짝.
                    onDoubleTap = {
                        if (isZoomed) { scale = 1f; offset = Offset.Zero } else scale = 2.5f
                    },
                    // 확대 중엔 탭으로 안 닫는다 — 들여다보다 닫히면 처음부터 다시다.
                    onTap = { if (!isZoomed) onTapClose() }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        // 90·270 도로 돌리면 가로세로가 바뀐다 → 화면 밖으로 나가지 않게 그만큼 줄인다.
        val sideways = (rotation % 180) != 0
        val shrink = if (sideways) minOf(maxWidth / maxHeight, maxHeight / maxWidth) else 1f
        val imgMod = Modifier.fillMaxSize().graphicsLayer {
            rotationZ = rotation.toFloat()
            scaleX = shrink * scale; scaleY = shrink * scale
            translationX = offset.x; translationY = offset.y
        }
        when (photo) {
            is ViewerPhoto.OfFile -> coil.compose.AsyncImage(
                model = photo.uri, contentDescription = "사진",
                modifier = imgMod, contentScale = ContentScale.Fit
            )
            is ViewerPhoto.OfBitmap -> Image(
                bitmap = photo.bitmap.asImageBitmap(), contentDescription = "사진",
                modifier = imgMod, contentScale = ContentScale.Fit
            )
        }
        // 🔄 돌리기 — **크게 보면서** 돌린다. 작은 썸네일에선 제대로 됐는지 안 보인다.
        //   확대 중엔 숨긴다: 들여다보는 중엔 손가락이 아래쪽에 있어 잘못 눌린다.
        if (!isZoomed) {
            androidx.compose.foundation.layout.Column(
                // 🖐️ **시스템 버튼(||| ○ ‹) 위로 띄운다.** (2026-10-02 사장님 "사진 회전은 어느곳에서해야해?")
                //   34dp 로 박아뒀더니 S23U(내비바 45dp)에서 **버튼이 통째로 가려** 있었다.
                //   사장님은 기능이 없는 줄 아셨다. 이 창은 Dialog(별도 창)라 내비바 인셋이
                //   0 으로 오는 기기가 있어, 그걸 대신 재주는 [navBarBottomDp] 를 쓴다.
                modifier = Modifier.align(Alignment.BottomCenter)
                    .padding(bottom = navBarBottomDp() + 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 남의 사진은 돌려도 안 남는다 — **돌리고 나서** 조용히 알려준다.
                //   처음부터 띄우면 안 돌릴 사람한테까지 잔소리가 된다.
                if (!canSave && rotation % 360 != 0) {
                    Box(
                        Modifier.padding(bottom = 10.dp)
                            .clip(AppShape.pill).background(Color.Black.copy(alpha = 0.55f))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        androidx.compose.material3.Text(
                            "내 사진이 아니라 돌려 보기만 돼요",
                            style = AppType.caption, color = Color.White
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ViewerRotateBtn("↺  왼쪽") { onRotate(-90) }
                    ViewerRotateBtn("↻  오른쪽") { onRotate(90) }
                }
            }
        }
    }
}

/**
 * 돌리기 버튼. 손가락이 닿게 48dp.
 *
 * ⚠️ **흰 사진 위에서도 보여야 한다.** 처음엔 흰 반투명(0.16)으로 했다가
 *   밝은 사진에선 **통째로 묻혀 안 보였다**(2026-10-01 실기). 닫기(✕)가 쓰는
 *   **검정 반투명 + 흰 테두리**로 맞춘다 — 어떤 사진 위든 읽힌다.
 */
@Composable
private fun ViewerRotateBtn(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            // 누르는 것은 48dp 는 돼야 한다 — 손가락은 정중앙을 못 찍는다. (2026-10-01)
            .heightIn(min = 48.dp)
            .clip(AppShape.pill)
            .background(Color.Black.copy(alpha = 0.55f))
            .border(1.dp, Color.White.copy(alpha = 0.34f), AppShape.pill)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() }
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.material3.Text(
            label, style = AppType.body, color = Color.White, fontWeight = FontWeight.Bold
        )
    }
}
