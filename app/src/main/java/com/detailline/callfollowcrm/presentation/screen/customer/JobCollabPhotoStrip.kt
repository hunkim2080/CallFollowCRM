package com.detailline.callfollowcrm.presentation.screen.customer

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material3.Text
import androidx.compose.ui.unit.dp
import com.detailline.callfollowcrm.ai.SharedSiteRepository.SharedPhoto
import com.detailline.callfollowcrm.domain.collab.CollabJobPhotos
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
    Row(
        modifier.clip(AppShape.sm).background(AppTheme.colors.surfaceMuted).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        PhotoTabSeg(mineLabel, tab == "mine") { onTab("mine") }
        PhotoTabSeg(collabLabel, tab == "collab") { onTab("collab") }
    }
}

@Composable
private fun PhotoTabSeg(text: String, on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .then(if (on) Modifier.shadow(1.dp, AppShape.sm) else Modifier)
            .clip(AppShape.sm)
            .background(if (on) AppTheme.colors.surface else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 11.dp, vertical = 6.dp)
    ) {
        Text(
            text, style = AppType.label,
            color = if (on) TossTextPrimary else TossTextTertiary
        )
    }
}

/**
 * 🤝 **협업 사장 사진** 가로 스트립 — 그 건에 걸린 협업 사진만(1차/2차 안 섞임, [CollabJobPhotos.group] 로 걸러서 들어온다).
 *   업체 2곳 이상이면 **업체 칩**으로 가르고(1곳이면 칩 생략), 각 사진엔 `🤝 상호` 이름표. **보기 전용**(×·＋ 없음).
 *   탭하면 그 업체 사진 전부를 뷰어로(크게보기는 원본 dataUrl 에서 고해상 재디코딩 — 512 썸네일을 키워 흐리던 것 방지).
 */
@Composable
internal fun JobCollabPhotoStrip(
    groups: List<CollabJobPhotos.Group<SharedPhoto>>,
    onOpenPhoto: (List<ViewerPhoto>, Int) -> Unit,
) {
    if (groups.isEmpty()) return
    var sel by remember(groups.map { it.shareId }) { mutableStateOf(groups.first().shareId) }
    val active = groups.firstOrNull { it.shareId == sel } ?: groups.first()

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
        active.photos.mapNotNull { p -> p.bitmap?.let { ViewerPhoto.OfRemote(it, p.dataUrl) } }
    }
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        active.photos.forEach { p ->
            Box(Modifier.size(64.dp).clip(AppShape.md).background(TossGrayBg)) {
                val bmp = p.bitmap
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "협업 사진 (${active.partnerName})",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clickable {
                            val start = viewerCells.indexOfFirst {
                                it is ViewerPhoto.OfRemote && it.thumb === bmp
                            }.coerceAtLeast(0)
                            onOpenPhoto(viewerCells, start)
                        }
                    )
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
