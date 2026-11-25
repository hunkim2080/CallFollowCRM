package com.detailline.callfollowcrm.presentation.screen.customer

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.Text
import androidx.compose.ui.unit.dp
import com.detailline.callfollowcrm.ai.SharedSiteRepository.SharedPhoto
import com.detailline.callfollowcrm.domain.collab.CollabJobPhotos
import com.detailline.callfollowcrm.domain.collab.CollabRepPhoto
import com.detailline.callfollowcrm.presentation.component.ViewerPhoto
import com.detailline.callfollowcrm.presentation.theme.AppShape
import com.detailline.callfollowcrm.presentation.theme.AppTheme
import com.detailline.callfollowcrm.presentation.theme.AppType
import com.detailline.callfollowcrm.presentation.theme.TossBlue
import com.detailline.callfollowcrm.presentation.theme.TossGrayBg
import com.detailline.callfollowcrm.presentation.theme.TossTextPrimary
import com.detailline.callfollowcrm.presentation.theme.TossTextSecondary
import com.detailline.callfollowcrm.presentation.theme.TossTextTertiary

/**
 * 📷 현장 사진 섹션 **토글 머리** — `내 사진 N` ↔ `🤝 협업 M`.
 *   업체가 몇이든 **2칸 고정**(업체를 토글칸으로 만들면 폰 폭이 깨진다 — 업체는 [JobCollabPhotoStrip] 안 칩으로 가른다).
 */
@Composable
internal fun PhotoTabToggle(
    mineLabel: String,
    collabLabel: String,
    tab: String,
    onTab: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 밑줄 탭 — 둥근 상자 없이 플랫. 고른 쪽만 진하게 + 파란 밑줄. (2026-11-24 사장님 "둥근게 어색")
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        PhotoTabSeg(mineLabel, tab == "mine") { onTab("mine") }
        PhotoTabSeg(collabLabel, tab == "collab") { onTab("collab") }
    }
}

@Composable
private fun PhotoTabSeg(text: String, on: Boolean, onClick: () -> Unit) {
    // width(IntrinsicSize.Max) = 글자 폭에 맞춰 칸을 잡는다 → 밑줄 fillMaxWidth 가 글자 폭만큼만.
    //   (이거 없으면 밑줄이 Row 남은 폭 전체로 늘어난다 — 2026-11-24 그 버그)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(IntrinsicSize.Max).clickable { onClick() }.padding(vertical = 4.dp)
    ) {
        Text(
            text, style = AppType.label,
            fontWeight = FontWeight.Bold,
            color = if (on) TossTextPrimary else TossTextTertiary
        )
        Spacer(Modifier.height(5.dp))
        Box(
            Modifier.fillMaxWidth().height(2.5.dp).clip(AppShape.pill)
                .background(if (on) TossBlue else Color.Transparent)
        )
    }
}

/**
 * 🤝 **협업 사장 사진** 가로 스트립 — 그 건에 걸린 협업 사진만(1차/2차 안 섞임, [CollabJobPhotos.group] 로 걸러서 들어온다).
 *   업체 2곳 이상이면 **업체 칩**으로 가르고, 각 사진엔 이름표. 꾹 누르면 내 기록의 대표로 지정.
 *   탭하면 그 업체 사진 전부를 뷰어로(크게보기는 원본 dataUrl 에서 고해상 재디코딩 — 512 썸네일을 키워 흐리던 것 방지).
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun JobCollabPhotoStrip(
    groups: List<CollabJobPhotos.Group<SharedPhoto>>,
    onOpenPhoto: (List<ViewerPhoto>, Int) -> Unit,
    chosenPhotos: Map<String, Long>,
    onPickRepresentative: (shareId: String, photoId: Long) -> Unit,
    /** 크게보기 탭 때 그 1장의 **원본**을 받는 로더(목록은 썸네일만 받음). null 이면 썸네일 그대로. (2026-11-24) */
    onLoadFull: (suspend (shareId: String, photoId: Long) -> String?)? = null,
) {
    if (groups.isEmpty()) return
    var sel by remember(groups.map { it.shareId }) { mutableStateOf(groups.first().shareId) }
    val active = groups.firstOrNull { it.shareId == sel } ?: groups.first()
    val representative = CollabRepPhoto.pick(active.photos, chosenPhotos[active.shareId])

    if (groups.size > 1) {
        Spacer(Modifier.height(2.dp))
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            groups.forEach { g ->
                val on = g.shareId == active.shareId
                Box(
                    Modifier.clip(AppShape.pill)
                        .background(if (on) TossBlue.copy(alpha = 0.12f) else AppTheme.colors.surfaceMuted)
                        .clickable { sel = g.shareId }
                        .padding(horizontal = 11.dp, vertical = 6.dp)
                ) {
                    Text(
                        "${g.partnerName} ${g.photos.size}",
                        style = AppType.label,
                        color = if (on) TossBlue else TossTextSecondary
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    } else {
        Spacer(Modifier.height(10.dp))
    }

    val viewerCells = remember(active) {
        active.photos.mapNotNull { p ->
            p.bitmap?.let { bmp ->
                ViewerPhoto.OfRemote(
                    bmp, p.dataUrl,
                    loadFull = onLoadFull?.let { lf -> suspend { lf(active.shareId, p.photoId) } }
                )
            }
        }
    }
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        active.photos.forEach { p ->
            Box(Modifier.size(64.dp).clip(AppShape.md).background(TossGrayBg)
                .combinedClickable(
                    onLongClickLabel = "대표 사진으로 지정",
                    onLongClick = { onPickRepresentative(active.shareId, p.photoId) },
                    onClick = {
                        val start = viewerCells.indexOfFirst {
                            it is ViewerPhoto.OfRemote && it.thumb === p.bitmap
                        }
                        if (start >= 0) onOpenPhoto(viewerCells, start)
                    }
                )) {
                val bmp = p.bitmap
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "협업 사진 (${active.partnerName})",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                if (p.photoId == representative?.photoId) {
                    Text("⭐", style = AppType.micro,
                        modifier = Modifier.align(Alignment.TopStart).padding(3.dp)
                            .clip(AppShape.sm).background(AppTheme.colors.surface)
                            .padding(horizontal = 3.dp, vertical = 1.dp))
                }
                // 🤝 업체 이름표 — 누구 사진인지. 위쪽 딱지(대표·전)와 안 겹치게 아래.
                Box(
                    Modifier.align(Alignment.BottomStart).padding(4.dp)
                        .clip(AppShape.sm).background(Color.Black.copy(alpha = 0.5f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(active.partnerName, style = AppType.micro, color = Color.White)
                }
            }
        }
    }
}
