package com.detailline.callfollowcrm.presentation.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.detailline.callfollowcrm.domain.ui.FitBox
import com.detailline.callfollowcrm.domain.ui.MarkStroke
import com.detailline.callfollowcrm.domain.ui.PhotoFit
import com.detailline.callfollowcrm.presentation.theme.AppShape
import com.detailline.callfollowcrm.presentation.theme.AppTheme
import com.detailline.callfollowcrm.presentation.theme.AppType
import com.detailline.callfollowcrm.presentation.theme.PEN_COLORS
import com.detailline.callfollowcrm.presentation.theme.PenBarBg
import com.detailline.callfollowcrm.presentation.theme.PenStageBg
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
     * 🗑 **이 사진 지우기 — 내 사진만**([ViewerPhoto.OfFile] 에 photoId>0). (2026-10-24 사장님 "현장사진 삭제를 못하는데")
     *   없으면 삭제 버튼을 안 그린다 — 팀원·협업·문자 사진은 여기서 안 지운다(남의 것).
     *   누르면 **확인 한 번** 뒤 지우고 **뷰어를 닫는다**(목록이 그 자리에서 바뀐 걸 보여준다).
     *   실제 삭제는 부른 쪽의 `deleteSitePhoto`(→`SitePhotoRepository.delete`) 한 곳 — 여긴 입구만.
     */
    onDelete: ((photoId: Long) -> Unit)? = null,
    /**
     * ✏️ **사진에 펜으로 표시 — 내 사진만**([ViewerPhoto.OfFile] photoId>0). (2026-10-24 사장님 "펜으로 체크")
     *   있으면 [펜] 버튼이 뜨고, 그린 뒤 완료하면 **「저장할까요?」를 묻고** 획(0..1 좌표)을 넘긴다.
     *   부른 쪽이 **그 사진을 그린 걸로 바꾸고(대체) 갤러리에도 저장**한다 — 진짜 원본은 갤러리에 있어 안전.
     *   (2026-10-25 사장님 "업로드한 원본이 수정한 사진으로 대체. 보내기는 없어도 됨.") 저장·합성은 부른 쪽 한 곳.
     */
    onSaveMarked: ((sourcePhotoId: Long, marks: List<MarkStroke>) -> Unit)? = null,
    /**
     * ✏️ **보낼 첨부 사진에 펜 표시 — 보내기 전 그 사진만 바꾼다.** (2026-10-25 사장님 "고객한테 보낼 때도 그리기")
     *   있으면 [펜] 버튼이 뜨고(현장사진용 [onSaveMarked] 와 달리 photoId 없어도), 완료 후 **「표시할까요?」** 를 묻고
     *   지금 보는 쪽 번호([page])와 획을 넘긴다. 부른 쪽이 **붙여둔 그 사진을 그린 걸로 교체**한다(갤러리 원본은 그대로).
     *   둘 중 하나만 준다 — 현장사진이면 [onSaveMarked], 보낼 첨부면 이것.
     */
    onMarkAttachment: ((page: Int, marks: List<MarkStroke>) -> Unit)? = null,
    /**
     * 화면 왼쪽 위에 더 얹을 것 — 문자 사진의 「갤러리에 저장」 같은 것.
     *   받는 값은 **지금 보고 있는 쪽 번호**다.
     */
    topStartAction: (@Composable (Int) -> Unit)? = null
) {
    if (photos.isEmpty()) return
    val ctx0 = androidx.compose.ui.platform.LocalContext.current
    // 쪽마다 돌린 각도. 저장은 **한 번에** 한다 — 누를 때마다 쓰면 화질이 깎인다.
    val rotations = remember(photos) { mutableStateMapOf<Int, Int>() }

    /** 아직 저장 안 한 **내 사진**의 돌린 각도들. 남의 사진은 여기 안 들어온다. */
    fun pending(): List<Pair<Long, Int>> = rotations.mapNotNull { (idx, deg) ->
        if (deg % 360 == 0) return@mapNotNull null
        (photos.getOrNull(idx) as? ViewerPhoto.OfFile)
            ?.takeIf { it.photoId > 0L }?.let { it.photoId to deg }
    }

    val saveAll = {
        val list = pending()
        if (onSaveRotation != null && list.isNotEmpty()) {
            for ((id, deg) in list) onSaveRotation(id, deg)
            rotations.clear()
            android.widget.Toast.makeText(ctx0, "돌려서 저장했어요", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    // 💾 **그냥 닫으면 묻는다.** (2026-10-02 사장님 "적용 버튼이 생겨야 하지 않을까?
    //   그냥 끄는 UX 는 사람들이 익숙하지 않지 않니?")
    //   전엔 **말없이 저장**했다 — 저장됐는지 알 길이 없어 사장님도 9/30 에 물어보셔야 아셨다.
    //   삼성 갤러리와 같은 방식으로 맞춘다: 돌린 채 닫으려 하면 **저장할지 묻는다.**
    var askSave by remember { mutableStateOf(false) }
    val tryClose = {
        if (onSaveRotation != null && pending().isNotEmpty()) askSave = true else onDismiss()
    }
    val closeAndSave = tryClose
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
        // 🗑 지울 수 있는 건 **지금 보는 게 내 사진**(OfFile·photoId>0)이고 onDelete 가 있을 때뿐.
        var askDelete by remember { mutableStateOf(false) }
        val deletablePhotoId = (photos.getOrNull(pagerState.currentPage) as? ViewerPhoto.OfFile)
            ?.photoId?.takeIf { it > 0L }
        val canDeleteCur = onDelete != null && deletablePhotoId != null
        // ✏️ 펜 표시 — 지금 보는 게 내 사진이고 onSaveMarked 가 있을 때만.
        var penMode by remember { mutableStateOf(false) }
        // ✏️ 완료 후 묻는 중 들고 있는 획(지금 보는 쪽 번호 + 획). null 이면 안 묻는 중.
        var pendingMark by remember { mutableStateOf<Pair<Int, List<MarkStroke>>?>(null) }
        val curOfFile = photos.getOrNull(pagerState.currentPage) as? ViewerPhoto.OfFile
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
                    onTapClose = tryClose,
                    canSave = onSaveRotation != null &&
                        (photos[page] as? ViewerPhoto.OfFile)?.photoId?.let { it > 0L } == true,
                    // 돌린 게 있고 **내 사진**일 때만 [이대로 저장] 이 나타난다.
                    onSaveNow = if (
                        onSaveRotation != null &&
                        ((rotations[page] ?: 0) % 360) != 0 &&
                        (photos[page] as? ViewerPhoto.OfFile)?.photoId?.let { it > 0L } == true
                    ) ({ saveAll(); onDismiss() }) else null,
                    // ✏️ [펜] — 내 현장 사진(photoId>0, 저장)이거나 보낼 첨부(onMarkAttachment, 확인)일 때.
                    onPen = if (
                        (onSaveMarked != null && (photos[page] as? ViewerPhoto.OfFile)?.photoId?.let { it > 0L } == true) ||
                        (onMarkAttachment != null && photos[page] is ViewerPhoto.OfFile)
                    ) ({ penMode = true }) else null
                )
            }
            // 여러 장이면 몇 번째인지. 한 장이면 군더더기라 안 띄운다.
            if (photos.size > 1) {
                Box(
                    Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 18.dp)
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
                Box(Modifier.align(Alignment.TopStart).statusBarsPadding().padding(14.dp)) {
                    action(pagerState.currentPage)
                }
            }
            // 오른쪽 위 — [🗑 지우기(내 사진만)] [✕ 닫기]. 삭제는 확인 한 번 뒤 뷰어를 닫는다.
            //   statusBarsPadding — S23U(상태바·펀치홀 큼)에서 ✕·🗑 이 시계/카메라에 안 겹치게. (2026-10-25 S23U sweep)
            Row(
                Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (canDeleteCur) {
                    Box(
                        Modifier.size(38.dp)
                            .clip(AppShape.pill).background(Color.Black.copy(alpha = 0.55f))
                            .clickable { askDelete = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Delete, "사진 지우기", tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }
                Box(
                    Modifier.size(38.dp)
                        .clip(AppShape.pill).background(Color.Black.copy(alpha = 0.55f))
                        .clickable { closeAndSave() },
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.material3.Text(
                        "✕", style = AppType.title, color = Color.White, fontWeight = FontWeight.Bold
                    )
                }
            }
            // ✏️ 펜 화면 — 그릴 땐 사진창 전체를 덮는다(넘김·확대·닫기 멈춤). 완료하면 한 번 묻는다.
            if (penMode && curOfFile != null) {
                PenStage(
                    photo = curOfFile,
                    onCancel = { penMode = false },
                    onDone = { marks ->
                        penMode = false
                        // 그린 게 있으면 묻는다(지금 보는 쪽). 없으면 그냥 닫는다.
                        if (marks.isNotEmpty()) pendingMark = pagerState.currentPage to marks else onDismiss()
                    }
                )
            }
            // ✏️ **완료 뒤 한 번 묻는다**(삼성 갤러리 편집과 같은 방식). 취소 = 그린 것 버리고 닫기.
            //   현장 사진: 「저장할까요?」 → 그 사진 교체 + 갤러리 저장(onSaveMarked).
            //   보낼 첨부: 「표시할까요?」 → 그 보낼 사진만 교체(onMarkAttachment). 둘 중 하나만 걸려 있다.
            pendingMark?.let { (page, marks) ->
                val toSite = onSaveMarked != null
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { pendingMark = null; onDismiss() },
                    containerColor = AppTheme.colors.surface,
                    tonalElevation = 0.dp,
                    title = {
                        androidx.compose.material3.Text(
                            if (toSite) "저장할까요?" else "표시할까요?", style = AppType.title,
                            fontWeight = FontWeight.Bold, color = AppTheme.colors.text
                        )
                    },
                    text = {
                        androidx.compose.material3.Text(
                            if (toSite) "표시한 내용으로 이 현장 사진을 바꾸고, 갤러리에도 저장해요."
                            else "표시한 걸로 바꿔서 보내요. (갤러리 원본은 그대로)",
                            style = AppType.body, color = AppTheme.colors.textSub
                        )
                    },
                    confirmButton = {
                        androidx.compose.material3.TextButton(
                            onClick = {
                                pendingMark = null
                                if (toSite) (photos.getOrNull(page) as? ViewerPhoto.OfFile)
                                    ?.let { onSaveMarked?.invoke(it.photoId, marks) }
                                else onMarkAttachment?.invoke(page, marks)
                                onDismiss()
                            }
                        ) {
                            androidx.compose.material3.Text(
                                if (toSite) "저장" else "확인",
                                fontWeight = FontWeight.Bold, color = AppTheme.colors.primary
                            )
                        }
                    },
                    dismissButton = {
                        androidx.compose.material3.TextButton(
                            onClick = { pendingMark = null; onDismiss() }
                        ) {
                            androidx.compose.material3.Text("취소", color = AppTheme.colors.textHint)
                        }
                    }
                )
            }
            // 💾 돌려놓고 그냥 닫으려 할 때 — **실수로 날아가지 않게.**
            if (askSave) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { askSave = false },
                    containerColor = AppTheme.colors.surface,
                    tonalElevation = 0.dp,
                    title = {
                        androidx.compose.material3.Text(
                            "돌린 걸 저장할까요?", style = AppType.title,
                            fontWeight = FontWeight.Bold, color = AppTheme.colors.text
                        )
                    },
                    text = {
                        androidx.compose.material3.Text(
                            "저장하지 않으면 돌리기 전으로 돌아가요.",
                            style = AppType.body, color = AppTheme.colors.textSub
                        )
                    },
                    confirmButton = {
                        androidx.compose.material3.TextButton(
                            onClick = { askSave = false; saveAll(); onDismiss() }
                        ) {
                            androidx.compose.material3.Text(
                                "저장", fontWeight = FontWeight.Bold, color = AppTheme.colors.primary
                            )
                        }
                    },
                    dismissButton = {
                        androidx.compose.material3.TextButton(
                            onClick = { askSave = false; onDismiss() }
                        ) {
                            androidx.compose.material3.Text("그냥 닫기", color = AppTheme.colors.textHint)
                        }
                    }
                )
            }
            // 🗑 **이 사진 지울까요?** — 되돌릴 수 없으니 한 번 묻는다. 지우면 뷰어를 닫아 목록으로.
            if (askDelete && deletablePhotoId != null && onDelete != null) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { askDelete = false },
                    containerColor = AppTheme.colors.surface,
                    tonalElevation = 0.dp,
                    title = {
                        androidx.compose.material3.Text(
                            "이 사진을 지울까요?", style = AppType.title,
                            fontWeight = FontWeight.Bold, color = AppTheme.colors.text
                        )
                    },
                    text = {
                        androidx.compose.material3.Text(
                            "되돌릴 수 없어요.", style = AppType.body, color = AppTheme.colors.textSub
                        )
                    },
                    confirmButton = {
                        androidx.compose.material3.TextButton(
                            onClick = { askDelete = false; onDelete(deletablePhotoId); onDismiss() }
                        ) {
                            androidx.compose.material3.Text(
                                "삭제", fontWeight = FontWeight.Bold, color = AppTheme.colors.unpaid
                            )
                        }
                    },
                    dismissButton = {
                        androidx.compose.material3.TextButton(onClick = { askDelete = false }) {
                            androidx.compose.material3.Text("취소", color = AppTheme.colors.textHint)
                        }
                    }
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
    canSave: Boolean,
    /** 돌린 게 있을 때만 들어온다 — null 이면 [이대로 저장] 을 안 그린다. */
    onSaveNow: (() -> Unit)? = null,
    /** ✏️ 내 사진일 때만 들어온다 — null 이면 [펜으로 표시] 를 안 그린다. */
    onPen: (() -> Unit)? = null
) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val isZoomed = scale > 1.01f
    LaunchedEffect(isZoomed) { onZoomedChange(isZoomed) }

    BoxWithConstraints(
        Modifier.fillMaxSize()
            .pointerInput(Unit) {
                // 🖐️ 한 손가락 + 확대 전이면 **제스처를 안 삼킨다** → 바깥 HorizontalPager 가 좌우로 넘긴다.
                //   (2026-10-08 사장님 "여러 장(1/2)인데 옆으로 스와이프가 안 돼")
                //   전엔 detectTransformGestures 가 한 손가락 드래그까지 전부 소비해 페이저가 못 받았다.
                //   두 손가락(핀치)이거나 **이미 확대된** 상태에서만 확대/이동을 처리하고 소비한다.
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val pointers = event.changes.count { it.pressed }
                        if (pointers >= 2 || scale > 1.01f) {
                            scale = (scale * event.calculateZoom()).coerceIn(1f, 5f)
                            offset = if (scale <= 1.01f) Offset.Zero else offset + event.calculatePan()
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
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
        // 🔄 **돌려도 작아지지 않게.** (2026-10-02 사장님 "사진이 회전하면 작아져")
        //   전엔 90도로 돌릴 때 **화면 비율만큼 통째로 줄였다**(1080/2316 ≈ 0.47) —
        //   화면 밖으로 안 나가게 하려던 건데, **사진이 절반으로 쪼그라들었다.**
        //   제대로 된 방법: 줄이지 말고 **담는 그릇의 가로세로를 바꿔** 끼운다.
        //   눕힐 그릇(H×W)에 맞춰 그린 뒤 90도 돌리면 정확히 화면(W×H)을 채운다.
        val sideways = (rotation % 180) != 0
        val boxW = if (sideways) maxHeight else maxWidth
        val boxH = if (sideways) maxWidth else maxHeight
        val imgMod = Modifier.size(boxW, boxH).graphicsLayer {
            rotationZ = rotation.toFloat()
            scaleX = scale; scaleY = scale
            translationX = offset.x; translationY = offset.y
        }
        val ctx = androidx.compose.ui.platform.LocalContext.current
        when (photo) {
            // 🖼️ 열쇠에 **파일이 바뀐 시각**을 붙인다 — 안 그러면 돌려도 **옛 그림**이 남는다.
            //   (2026-10-02 사장님 "작은 사진은 그대로인데 들어가면 회전되어 있어")
            is ViewerPhoto.OfFile -> coil.compose.AsyncImage(
                model = com.detailline.callfollowcrm.util.PhotoModel.of(ctx, photo.uri),
                contentDescription = "사진",
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
                // 🖐️ **시스템 버튼(||| ○ ‹) 위로 띄운다.** (2026-10-02 사장님)
                //   ① 34dp 로 박아뒀더니 S23U 에서 **버튼이 통째로 가려** 있었다 → 기능이 없는 줄 아셨다.
                //   ② navBarBottomDp() + 20dp 로 고쳤더니 이번엔 **겹쳐 보였다**
                //      (사장님 "겹쳐보여 아래 봐봐" — 알약 아래 끝 y2230 vs 내비바 y2190, 40px 겹침).
                //   ③ `maxOf(navBarBottomDp(), 48.dp) + 24.dp` 로 고쳤는데도 **안 움직였다**
                //      (2026-10-02 실측: 알약 위 테두리 y2094 — 고치기 전과 같은 자리).
                //      이 창에선 그 값이 왜 안 먹는지 아직 모른다.
                //   🔑 **그래서 폰에 아무것도 안 묻는다.** 숫자를 박는다 —
                //      내비바는 어떤 폰도 48dp 를 안 넘으니 96dp 면 **두 배**로 안전하다.
                //      사진 위 빈 검정 자리라 조금 높이 떠도 손해가 없다. 안 보이는 것보다 낫다.
                modifier = Modifier.align(Alignment.BottomCenter)
                    .padding(bottom = 96.dp),
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
                Row(
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    ViewerToolBtn("왼쪽", glyph = "↺") { onRotate(-90) }
                    ViewerToolBtn("오른쪽", glyph = "↻") { onRotate(90) }
                    // ✏️ **내 사진이면 펜으로 그리기.** 눌러 그리는 화면으로. (2026-10-24 사장님 "표시가 뭔지 헷갈려 → 펜")
                    onPen?.let { ViewerToolBtn("펜", accent = true, icon = Icons.Default.Edit, onClick = it) }
                    // 💾 **돌려야 나타난다.** 할 게 없을 땐 안 보인다. (2026-10-02 사장님)
                    onSaveNow?.let { ViewerToolBtn("저장", accent = true, icon = Icons.Default.Check, onClick = it) }
                }
            }
        }
    }
}

/**
 * 뷰어 아래 **동그란 도구 버튼** — 아이콘(또는 ↺↻ 글자) + 아래 작은 이름. (2026-10-25 사장님 "버튼 디자인 개선")
 *   돌리기는 **회색**, 주 동작(펜·저장)은 **파랑**으로 위계를 준다 — 뭘 누를지 한눈에.
 *
 * ⚠️ **흰 사진 위에서도 보여야 한다.** 밝은 사진에 묻히지 않게(2026-10-01 실기)
 *   닫기(✕)와 같은 **검정 반투명 + 흰 테두리**(파랑은 그 자체로 보임)로 맞춘다.
 *   동그라미 52dp — 손가락이 닿는다.
 */
@Composable
private fun ViewerToolBtn(
    label: String,
    accent: Boolean = false,
    glyph: String? = null,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(if (accent) AppTheme.colors.primary else Color.Black.copy(alpha = 0.55f))
                .border(
                    1.dp,
                    if (accent) Color.Transparent else Color.White.copy(alpha = 0.34f),
                    CircleShape
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onClick() },
            contentAlignment = Alignment.Center
        ) {
            if (icon != null) {
                Icon(icon, label, tint = Color.White, modifier = Modifier.size(24.dp))
            } else {
                androidx.compose.material3.Text(
                    glyph ?: label, style = AppType.title, color = Color.White, fontWeight = FontWeight.Bold
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        androidx.compose.material3.Text(
            label, style = AppType.caption, color = Color.White, fontWeight = FontWeight.Medium
        )
    }
}

/** 그리는 획 한 줄 — 색 + 굵기(화면 px) + 지나간 점들(화면 좌표). */
private class PenLine(val color: Color, val wPx: Float) {
    val pts = mutableStateListOf<Offset>()
}

/** DrawScope 에서 획 한 줄을 선분으로 잇는다. 점 하나면 아주 짧게 찍어 보이게. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPenLine(l: PenLine) {
    if (l.pts.isEmpty()) return
    if (l.pts.size == 1) {
        drawLine(l.color, l.pts[0], l.pts[0] + Offset(0.5f, 0.5f), strokeWidth = l.wPx, cap = StrokeCap.Round)
        return
    }
    for (i in 1 until l.pts.size) {
        drawLine(l.color, l.pts[i - 1], l.pts[i], strokeWidth = l.wPx, cap = StrokeCap.Round)
    }
}

/**
 * ✏️ **펜 화면 — 사진 위에 손가락으로 그린다.** 프로토 「사진에 펜 표시」 그대로.
 *   색 4(빨강 기본·노랑·파랑·흰) · 굵기 2 · ↶ 되돌리기 · 🗑 전체지우기 · 완료.
 *   완료 = 획을 **0..1 좌표**([PhotoFit])로 바꿔 넘긴다 → 부른 쪽이 원본에 얹어 새 사진으로.
 *   회전·확대·넘김은 **여기선 멈춘다**(그리기에 집중). 사진은 Fit(전체 보이게)로만 띄운다.
 */
@Composable
private fun PenStage(
    photo: ViewerPhoto.OfFile,
    onCancel: () -> Unit,
    onDone: (List<MarkStroke>) -> Unit
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val density = LocalDensity.current

    val lines = remember { mutableStateListOf<PenLine>() }
    var current by remember { mutableStateOf<PenLine?>(null) }
    var color by remember { mutableStateOf(PEN_COLORS.first()) }
    var widthDp by remember { mutableStateOf(6f) }   // 6 / 14

    // 사진 원본 크기 — **EXIF 로 세워진 방향**(Coil 이 띄우는 그대로)에 맞춘다.
    //   갤러리 사진은 눕혀 저장되고 화면엔 세워 보이므로, 안 맞추면 획이 어긋난다.
    var bw by remember { mutableIntStateOf(0) }
    var bh by remember { mutableIntStateOf(0) }
    LaunchedEffect(photo.uri) {
        withContext(Dispatchers.IO) {
            val o = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            runCatching {
                ctx.contentResolver.openInputStream(photo.uri)?.use {
                    android.graphics.BitmapFactory.decodeStream(it, null, o)
                }
            }
            var ow = o.outWidth.coerceAtLeast(0)
            var oh = o.outHeight.coerceAtLeast(0)
            val path = photo.uri.path
            if (path != null && ow > 0 && oh > 0) {
                val orient = runCatching {
                    android.media.ExifInterface(path).getAttributeInt(
                        android.media.ExifInterface.TAG_ORIENTATION,
                        android.media.ExifInterface.ORIENTATION_NORMAL
                    )
                }.getOrDefault(android.media.ExifInterface.ORIENTATION_NORMAL)
                val sideways = orient == android.media.ExifInterface.ORIENTATION_ROTATE_90 ||
                    orient == android.media.ExifInterface.ORIENTATION_ROTATE_270 ||
                    orient == android.media.ExifInterface.ORIENTATION_TRANSPOSE ||
                    orient == android.media.ExifInterface.ORIENTATION_TRANSVERSE
                if (sideways) { val t = ow; ow = oh; oh = t }
            }
            bw = ow; bh = oh
        }
    }

    // 완료 때 화면좌표→0..1 변환에 쓸 네모. BoxWithConstraints 안에서 채운다.
    var fit by remember { mutableStateOf(FitBox(0f, 0f, 0f, 0f)) }

    Column(Modifier.fillMaxSize().background(PenStageBg)) {
        // ── 상단바: ✕ · 사진에 표시 · 완료 ──
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(38.dp).clip(AppShape.pill).clickable { onCancel() },
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.material3.Text("✕", style = AppType.title, color = Color.White, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.weight(1f))
            androidx.compose.material3.Text("사진에 표시", style = AppType.title, color = Color.White, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Box(
                Modifier.clip(AppShape.md).background(AppTheme.colors.primary)
                    .clickable {
                        val marks = lines.toList().mapNotNull { l ->
                            if (l.pts.isEmpty() || fit.dispW <= 0f) return@mapNotNull null
                            MarkStroke(
                                colorArgb = l.color.toArgb(),
                                normWidth = l.wPx / fit.dispW,
                                pts = l.pts.map { p -> PhotoFit.toNorm(fit, p.x, p.y) }
                            )
                        }
                        onDone(marks)
                    }
                    .padding(horizontal = 16.dp, vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.material3.Text("완료", style = AppType.body, color = Color.White, fontWeight = FontWeight.Bold)
            }
        }

        // ── 사진 + 그리기 ──
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            val fitLocal = remember(bw, bh, maxWidth, maxHeight) {
                PhotoFit.of(bw, bh, with(density) { maxWidth.toPx() }, with(density) { maxHeight.toPx() })
            }
            SideEffect { fit = fitLocal }
            coil.compose.AsyncImage(
                model = com.detailline.callfollowcrm.util.PhotoModel.of(ctx, photo.uri),
                contentDescription = "표시할 사진",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
            Canvas(
                Modifier.fillMaxSize().pointerInput(color, widthDp) {
                    val wPx = with(density) { widthDp.dp.toPx() }
                    detectDragGestures(
                        onDragStart = { o -> current = PenLine(color, wPx).also { it.pts.add(o) } },
                        onDrag = { change, _ -> current?.pts?.add(change.position); change.consume() },
                        onDragEnd = { current?.let { if (it.pts.isNotEmpty()) lines.add(it) }; current = null },
                        onDragCancel = { current = null }
                    )
                }
            ) {
                for (l in lines) drawPenLine(l)
                current?.let { drawPenLine(it) }
            }
        }

        // ── 하단 도구바: 색 · ↶ · 🗑 · 굵기 ──
        Row(
            Modifier.fillMaxWidth().background(PenBarBg).padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            PEN_COLORS.forEach { c ->
                Box(
                    Modifier.size(28.dp).clip(androidx.compose.foundation.shape.CircleShape).background(c)
                        .border(
                            if (c == color) 2.dp else 1.dp,
                            if (c == color) Color.White else Color.White.copy(alpha = 0.3f),
                            androidx.compose.foundation.shape.CircleShape
                        )
                        .clickable { color = c }
                )
            }
            Spacer(Modifier.width(2.dp))
            Box(
                Modifier.size(40.dp).clip(AppShape.md).background(Color.White.copy(alpha = 0.08f))
                    .clickable { lines.removeLastOrNull() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.AutoMirrored.Filled.Undo, "되돌리기", tint = Color.White, modifier = Modifier.size(18.dp))
            }
            Box(
                Modifier.size(40.dp).clip(AppShape.md).background(Color.White.copy(alpha = 0.08f))
                    .clickable { lines.clear() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Delete, "전체 지우기", tint = Color.White, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.weight(1f))
            listOf(6f, 14f).forEach { w ->
                Box(
                    Modifier.size(40.dp).clip(AppShape.md)
                        .background(if (w == widthDp) AppTheme.colors.primary else Color.White.copy(alpha = 0.08f))
                        .clickable { widthDp = w },
                    contentAlignment = Alignment.Center
                ) {
                    Box(Modifier.size((w * 0.9f).dp).clip(androidx.compose.foundation.shape.CircleShape).background(Color.White))
                }
            }
        }
    }
}
