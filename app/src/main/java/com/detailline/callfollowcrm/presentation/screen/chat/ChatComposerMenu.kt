package com.detailline.callfollowcrm.presentation.screen.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.detailline.callfollowcrm.presentation.component.tossCardShadow
import com.detailline.callfollowcrm.presentation.theme.AppTheme
import com.detailline.callfollowcrm.presentation.theme.TossBlue
import com.detailline.callfollowcrm.presentation.theme.TossGrayBg
import com.detailline.callfollowcrm.presentation.theme.TossSuccess

// (2026-11-21) ChatScreen.kt 에서 **그대로 옮겨온 잎**(CLAUDE.md §12-F) — 「대표질문」 줄 하나만 더했다.

/**
 * ⊕ 액션 메뉴 (2026-08-14 사장님) — 입력줄 왼쪽 ⊕ + 누르면 풍선처럼 뜨는 팝업.
 *   견적 작성 / 내 일정 확인 / 문구 넣기. ⊕ 바로 위에서 scaleIn(좌하단 기준, spring 오버슈트) 으로 톡.
 *   Popup 이라 화면 안 덮음. 구 chat-actions 상시 3칩 대체 → 대화 공간 확보(표면엔 덜).
 *   focusable Popup 이 포커스를 가져가 키보드도 내려가 팝업이 안 가림.
 */
@Composable
internal fun ComposerActionMenu(
    onEstimate: () -> Unit,
    onSchedule: () -> Unit,
    onTemplate: () -> Unit,
    /** 💬 대표질문 — 등록해 둔 질문을 골라 그 답을 입력칸에 넣는다. (2026-11-21 · 프로토 EYKUs11cVT7JW71yVrVoBA ①) */
    onRepQuestion: () -> Unit = {},
    // 사진 첨부 — 알약에서 ⊕ 맨 위로 이동. (2026-10-14 사장님)
    onAttachPhoto: () -> Unit = {},
    /**
     * 입력창에 쓴 글을 문구로 저장. **글이 있을 때만** 보인다. (2026-09-16 사장님 개편)
     *   전엔 [문구 넣기] 시트 맨 위에 있었는데, 거기는 '꺼내 쓰는 방'이라 자리가 안 맞았다.
     *   글을 다 쓴 그 순간, 손가락이 이미 ⊕ 근처에 있는 여기가 맞다.
     */
    canSaveText: Boolean = false,
    onSaveText: () -> Unit = {}
) {
    var open by remember { mutableStateOf(false) }
    val rot by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (open) 135f else 0f, label = "plusRot"
    )
    Box {
        // ⊕ 버튼 — 44dp 터치(거친 손가락도). 열리면 × 로 회전.
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(TossGrayBg)
                .clickable { open = !open },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Add, "더보기", tint = TossBlue, modifier = Modifier.size(24.dp).rotate(rot))
        }
        if (open) {
            val positioner = remember {
                object : androidx.compose.ui.window.PopupPositionProvider {
                    override fun calculatePosition(
                        anchorBounds: androidx.compose.ui.unit.IntRect,
                        windowSize: androidx.compose.ui.unit.IntSize,
                        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
                        popupContentSize: androidx.compose.ui.unit.IntSize
                    ): androidx.compose.ui.unit.IntOffset {
                        // 앵커(⊕) 바로 위 · 좌측 정렬 · 8px 틈
                        val x = anchorBounds.left
                        val y = anchorBounds.top - popupContentSize.height - 8
                        return androidx.compose.ui.unit.IntOffset(x, y.coerceAtLeast(8))
                    }
                }
            }
            // 뒤를 살짝 어둡게 — 안 그러면 대화 카드들과 겹쳐 **어디까지가 메뉴인지** 안 보인다.
            //   Popup 은 제 크기만큼만 창을 잡으므로 화면 크기를 직접 넣어준다.
            val cfg = androidx.compose.ui.platform.LocalConfiguration.current
            androidx.compose.ui.window.Popup(
                popupPositionProvider = object : androidx.compose.ui.window.PopupPositionProvider {
                    override fun calculatePosition(
                        anchorBounds: androidx.compose.ui.unit.IntRect,
                        windowSize: androidx.compose.ui.unit.IntSize,
                        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
                        popupContentSize: androidx.compose.ui.unit.IntSize
                    ) = androidx.compose.ui.unit.IntOffset(-anchorBounds.left, -anchorBounds.top)
                },
                onDismissRequest = { open = false }
            ) {
                Box(
                    Modifier
                        .size(cfg.screenWidthDp.dp, cfg.screenHeightDp.dp)
                        .background(Color.Black.copy(alpha = 0.26f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { open = false }
                )
            }
            androidx.compose.ui.window.Popup(
                popupPositionProvider = positioner,
                onDismissRequest = { open = false },
                properties = androidx.compose.ui.window.PopupProperties(focusable = true)
            ) {
                val vis = remember { androidx.compose.animation.core.MutableTransitionState(false) }
                vis.targetState = true
                androidx.compose.animation.AnimatedVisibility(
                    visibleState = vis,
                    enter = androidx.compose.animation.scaleIn(
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 1f),
                        animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.62f, stiffness = 420f)
                    ) + androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(120)),
                    exit = androidx.compose.animation.scaleOut(
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 1f),
                        animationSpec = androidx.compose.animation.core.tween(110)
                    ) + androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(90))
                ) {
                    Column(
                        modifier = Modifier
                            .width(202.dp)
                            .tossCardShadow(RoundedCornerShape(18.dp))
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color.White)
                            .padding(6.dp)
                    ) {
                        // 사진 = 제일 자주 쓰는 첨부라 맨 위. (2026-10-14 사장님: 알약에서 ⊕ 맨 위로 이동)
                        ActionMenuRow(Icons.Default.Image, "사진 첨부", AppTheme.colors.caution) { open = false; onAttachPhoto() }
                        // 색은 **하는 일의 종류**. 견적=돈/문서, 일정=달력, 문구=글.
                        ActionMenuRow(Icons.Default.Description, "견적 작성", AppTheme.colors.primary) { open = false; onEstimate() }
                        ActionMenuRow(Icons.Default.DateRange, "내 일정 확인", AppTheme.colors.category) { open = false; onSchedule() }
                        ActionMenuRow(Icons.AutoMirrored.Filled.Chat, "문구 넣기", TossSuccess) { open = false; onTemplate() }
                        // 문구 바로 아래 — 둘 다 '써 둔 글을 꺼내 넣는' 줄이라 같은 초록. (프로토 ① 메뉴: 문구 다음이 대표질문)
                        ActionMenuRow(Icons.Default.QuestionAnswer, "대표질문", TossSuccess) { open = false; onRepQuestion() }
                        // ❌ "다음 답변 AI 추천" 뺐다. (2026-09-20 사장님 "+ 쪽은 버리자")
                        //   AI 추천을 누르는 자리가 두 군데라 역할이 안 갈렸다. 게다가 ⊕ 쪽도 속으로는
                        //   **3개를 만들고 2개를 버려** 비용은 같은데 쓰는 건 하나였다.
                        //   입력창 바로 위 띠 하나로 남긴다 — 손가락에도 더 가깝다.
                        if (canSaveText) {
                            // 문구를 '꺼내 쓰는' 줄과 '넣어 두는' 줄이라 같은 초록.
                            ActionMenuRow(Icons.Default.Add, "이 글을 문구로 저장", TossSuccess) { open = false; onSaveText() }
                        }
                    }
                }
            }
        }
    }
}
