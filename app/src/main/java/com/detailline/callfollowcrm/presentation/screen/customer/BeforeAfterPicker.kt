package com.detailline.callfollowcrm.presentation.screen.customer

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import coil.compose.AsyncImage
import com.detailline.callfollowcrm.data.local.entity.SitePhotoEntity
import com.detailline.callfollowcrm.presentation.component.AppSheet
import com.detailline.callfollowcrm.presentation.component.TossPrimaryButton
import com.detailline.callfollowcrm.presentation.component.TossSecondaryButton
import com.detailline.callfollowcrm.presentation.theme.AppShape
import com.detailline.callfollowcrm.presentation.theme.AppType
import com.detailline.callfollowcrm.presentation.theme.TossBlue
import com.detailline.callfollowcrm.presentation.theme.TossBlueSoft
import com.detailline.callfollowcrm.presentation.theme.TossGrayBg
import com.detailline.callfollowcrm.presentation.theme.TossSuccess
import com.detailline.callfollowcrm.presentation.theme.TossTextPrimary
import com.detailline.callfollowcrm.presentation.theme.TossTextSecondary
import com.detailline.callfollowcrm.presentation.theme.TossWarning
import com.detailline.callfollowcrm.util.PhotoModel

/** 전/후 사진 고르기 시트에 넘길 한 벌 — 화면 루트에서 [BeforeAfterPickerSheet] 로 띄운다. (2026-10-28 사장님) */
internal data class BeforeAfterReq(
    val photos: List<SitePhotoEntity>,
    val repId: Long?,
    val beforeId: Long?
)

/**
 * 🖼️ **전/후 사진 고르기 — 앱이 콕콕 짚어주는 2단계 시트.** (2026-10-28 사장님, 프로토 artifact/Y4yXkYBiEUW9e4ydeVAajZ)
 *
 * ① "이 현장의 대표 사진을 골라주세요!" → 탭 = 대표(완성/후, 기존 `isRepresentative`)
 * ② "시공 전 사진도 한 장 골라주세요" → 탭 = '시공 전'(`SitePhotoEntity.LABEL_BEFORE`), **건너뛰기 가능**
 * ③ 완료 — 둘 다 있으면 내 기록 영상에서 **전→후**, 전 없으면 대표 한 장(지금 그대로).
 *
 * 선택은 누를 때마다 바로 저장된다(VM 이 `setRepresentativePhoto`·`setBeforePhoto`). 이 시트는 그걸 **짚어주는 흐름**만 맡는다.
 */
@Composable
fun BeforeAfterPickerSheet(
    photos: List<SitePhotoEntity>,
    repPhotoId: Long?,
    beforePhotoId: Long?,
    onPickRep: (Long) -> Unit,
    onPickBefore: (Long) -> Unit,
    onClearBefore: () -> Unit,
    onDone: () -> Unit
) {
    // 0=대표 고르기 · 1=시공 전 고르기 · 2=완료
    var step by remember { mutableStateOf(if (repPhotoId == null) 0 else 1) }
    var localRep by remember { mutableStateOf(repPhotoId) }
    var localBefore by remember { mutableStateOf(beforePhotoId) }

    AppSheet(onDismiss = onDone) {
        Text("전 / 후 사진 고르기", style = AppType.title, color = TossTextPrimary)
        Spacer(Modifier.height(12.dp))

        // ── 콕콕 짚어주는 안내 배너 ──
        val (bannerBg, accent, emoji, q, s) = when (step) {
            0 -> Guide(TossBlueSoft, TossBlue, "⭐", "이 현장의 대표 사진을 골라주세요!",
                "완성된 모습 — 영상·인증샷에 나와요. 사진 하나를 누르면 돼요.")
            1 -> Guide(TossWarning.copy(alpha = 0.12f), TossWarning, "🕐", "시공 전 사진도 한 장 골라주세요.",
                "전/후 비교용이에요(선택). 없으면 '건너뛰기' — 대표 한 장만 나와요.")
            else -> if (localBefore != null)
                Guide(TossSuccess.copy(alpha = 0.12f), TossSuccess, "🎬", "완료! 영상에서 전 → 후로 나와요.",
                    "멋진 비교샷이 됩니다.")
            else
                Guide(TossSuccess.copy(alpha = 0.12f), TossSuccess, "✅", "완료! 대표 한 장으로 나와요.",
                    "나중에 시공 전 사진을 더하면 전/후 비교가 돼요.")
        }
        Row(
            Modifier.fillMaxWidth().clip(AppShape.lg).background(bannerBg).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(emoji, style = AppType.display)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(q, style = AppType.headline, color = accent)
                Spacer(Modifier.height(2.dp))
                Text(s, style = AppType.caption, color = TossTextSecondary)
            }
        }
        Spacer(Modifier.height(10.dp))

        // ── 단계 진행바 ──
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            StepDot(Modifier.weight(1f), on = localRep != null, color = if (step >= 2) TossSuccess else TossBlue)
            StepDot(Modifier.weight(1f), on = localBefore != null, color = if (step >= 2) TossSuccess else TossWarning)
        }
        Spacer(Modifier.height(12.dp))

        // ── 사진 격자(3열) ── 바깥(AppSheet)이 스크롤하니 여기선 안 스크롤.
        photos.chunked(3).forEach { rowPhotos ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowPhotos.forEach { p ->
                    PhotoCell(
                        modifier = Modifier.weight(1f),
                        path = p.filePath,
                        isRep = p.id == localRep,
                        isBefore = p.id == localBefore,
                        onClick = {
                            if (step == 0) {
                                localRep = p.id
                                if (localBefore == p.id) { localBefore = null; onClearBefore() }
                                onPickRep(p.id)
                            } else if (step == 1 && p.id != localRep) {
                                // 같은 사진을 전·후로는 못 쓴다 → 대표와 다를 때만
                                localBefore = p.id
                                onPickBefore(p.id)
                            }
                        }
                    )
                }
                repeat(3 - rowPhotos.size) { Spacer(Modifier.weight(1f)) }
            }
            Spacer(Modifier.height(8.dp))
        }
        Spacer(Modifier.height(6.dp))

        // ── 버튼 ──
        when (step) {
            0 -> TossPrimaryButton(
                text = if (localRep == null) "사진을 골라주세요" else "다음 — 시공 전 고르기",
                onClick = { step = 1 }, modifier = Modifier.fillMaxWidth(), enabled = localRep != null
            )
            1 -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TossSecondaryButton(
                    text = "건너뛰기", onClick = { localBefore = null; onClearBefore(); step = 2 },
                    modifier = Modifier.weight(1f)
                )
                TossPrimaryButton(
                    text = "완료", onClick = { step = 2 }, modifier = Modifier.weight(1f),
                    enabled = localBefore != null
                )
            }
            else -> TossPrimaryButton(
                text = "확인", onClick = onDone, modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/** 배너 한 벌(구조 분해용). */
private data class Guide(
    val bg: Color, val accent: Color, val emoji: String, val q: String, val s: String
)

@Composable
private fun StepDot(modifier: Modifier, on: Boolean, color: Color) {
    Box(
        modifier.height(4.dp).clip(AppShape.pill)
            .background(if (on) color else TossGrayBg)
    )
}

@Composable
private fun PhotoCell(
    modifier: Modifier,
    path: String,
    isRep: Boolean,
    isBefore: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier.aspectRatio(1f).clip(AppShape.md).background(TossGrayBg)
            .then(
                when {
                    isRep -> Modifier.border(3.dp, TossBlue, AppShape.md)
                    isBefore -> Modifier.border(3.dp, TossWarning, AppShape.md)
                    else -> Modifier
                }
            )
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = PhotoModel.of(LocalContext.current, path),
            contentDescription = "현장 사진",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        if (isRep || isBefore) {
            Box(
                Modifier.align(Alignment.TopStart).padding(5.dp)
                    .clip(AppShape.pill).background(if (isRep) TossBlue else TossWarning)
                    .padding(horizontal = 7.dp, vertical = 2.dp)
            ) {
                Text(if (isRep) "⭐대표" else "🕐전", style = AppType.micro, color = Color.White)
            }
        }
    }
}
