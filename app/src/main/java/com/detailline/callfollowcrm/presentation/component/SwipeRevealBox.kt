package com.detailline.callfollowcrm.presentation.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 밀어서 "바로 삭제" 대신 → 밀면 오른쪽에 🗑 버튼이 드러나고, 그 버튼을 눌러야 실제 동작.
 * (2026-06-21 사장님 요청 — 증권앱 관심목록 스타일. 실수 삭제 방지 + 안정감.)
 *
 *   - 우→좌 로 밀면 콘텐츠가 왼쪽으로 밀리며 휴지통 버튼 노출(절반 이상 밀면 열린 채 고정).
 *   - 휴지통 버튼 탭 → [onAction] 실행 후 닫힘.
 *   - 열린 상태에서 콘텐츠를 탭하거나 오른쪽으로 밀면 다시 닫힘(오작동 방지).
 *   - 좌→우 단독 동작 없음(닫기 전용).
 *
 * 기존 *SwipeBox 들(confirmValueChange=false 즉시 동작)을 전부 이걸로 위임해 통일.
 */
@Composable
fun SwipeRevealBox(
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    label: String = "삭제",
    containerColor: Color = Color(0xFFF0436A),
    contentColor: Color = Color.White,
    icon: ImageVector = Icons.Filled.Delete,
    shape: Shape = RoundedCornerShape(14.dp),
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val revealDp = 76.dp
    val revealPx = with(density) { revealDp.toPx() }
    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var opened by remember { mutableStateOf(false) }

    Box(
        modifier
            .fillMaxWidth()
            .clip(shape)
    ) {
        // 뒤: 오른쪽 끝 휴지통 버튼 (콘텐츠 높이에 자동 맞춤)
        Box(Modifier.matchParentSize(), contentAlignment = Alignment.CenterEnd) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .width(revealDp)
                    .background(containerColor)
                    .clickable(enabled = opened) {
                        onAction()
                        opened = false
                        scope.launch { offsetX.animateTo(0f, tween(180)) }
                    },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(icon, label, tint = contentColor, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.height(2.dp))
                    Text(label, color = contentColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        // 앞: 콘텐츠 — offset 으로 밀림. 콘텐츠 자체 배경(흰색 등)이 휴지통을 덮음.
        //   .clip(shape): 콘텐츠를 박스와 같은 모서리로 깎아, 안 카드 라운드가 더 커도 뒤 버튼이 모서리로 안 비침. (2026-06-23 사장님)
        Box(
            Modifier
                .clip(shape)
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            val open = offsetX.value < -revealPx * 0.4f
                            opened = open
                            scope.launch { offsetX.animateTo(if (open) -revealPx else 0f, tween(200)) }
                        },
                        onDragCancel = {
                            scope.launch { offsetX.animateTo(if (opened) -revealPx else 0f, tween(200)) }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            val target = (offsetX.value + dragAmount).coerceIn(-revealPx, 0f)
                            scope.launch { offsetX.snapTo(target) }
                        }
                    )
                }
                .pointerInput(opened) {
                    if (opened) {
                        detectTapGestures(onTap = {
                            opened = false
                            scope.launch { offsetX.animateTo(0f, tween(180)) }
                        })
                    }
                }
        ) { content() }
    }
}

/**
 * SwipeRevealBox 의 "두 버튼" 버전 — 밀면 오른쪽에 [첫 버튼][둘째 버튼] 두 개가 같이 드러난다.
 *   (2026-06-23 사장님 — 상담함 대기 카드: 밀면 [스팸][정리] 둘 다 보이게.)
 *   제스처·동작은 SwipeRevealBox 와 동일(절반 넘게 밀면 열린 채 고정, 콘텐츠 탭/오른쪽 밀기=닫힘, 버튼 눌러야 실제 동작).
 */
@Composable
fun SwipeRevealTwoBox(
    onFirst: () -> Unit,
    onSecond: () -> Unit,
    firstLabel: String,
    secondLabel: String,
    firstColor: Color,
    secondColor: Color,
    firstIcon: ImageVector,
    secondIcon: ImageVector,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(14.dp),
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val buttonDp = 72.dp
    val revealDp = buttonDp * 2
    val revealPx = with(density) { revealDp.toPx() }
    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var opened by remember { mutableStateOf(false) }

    Box(
        modifier
            .fillMaxWidth()
            .clip(shape)
    ) {
        // 뒤: 오른쪽 끝 두 버튼 [first][second] (콘텐츠 높이에 자동 맞춤)
        Box(Modifier.matchParentSize(), contentAlignment = Alignment.CenterEnd) {
            Row(Modifier.fillMaxHeight()) {
                RevealActionButton(buttonDp, firstColor, firstIcon, firstLabel, opened) {
                    onFirst()
                    opened = false
                    scope.launch { offsetX.animateTo(0f, tween(180)) }
                }
                RevealActionButton(buttonDp, secondColor, secondIcon, secondLabel, opened) {
                    onSecond()
                    opened = false
                    scope.launch { offsetX.animateTo(0f, tween(180)) }
                }
            }
        }
        // 앞: 콘텐츠 — offset 으로 밀림. .clip(shape) 로 뒤 버튼이 카드 모서리로 안 비침.
        Box(
            Modifier
                .clip(shape)
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            val open = offsetX.value < -revealPx * 0.4f
                            opened = open
                            scope.launch { offsetX.animateTo(if (open) -revealPx else 0f, tween(200)) }
                        },
                        onDragCancel = {
                            scope.launch { offsetX.animateTo(if (opened) -revealPx else 0f, tween(200)) }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            val target = (offsetX.value + dragAmount).coerceIn(-revealPx, 0f)
                            scope.launch { offsetX.snapTo(target) }
                        }
                    )
                }
                .pointerInput(opened) {
                    if (opened) {
                        detectTapGestures(onTap = {
                            opened = false
                            scope.launch { offsetX.animateTo(0f, tween(180)) }
                        })
                    }
                }
        ) { content() }
    }
}

@Composable
private fun RevealActionButton(
    buttonWidth: Dp,
    containerColor: Color,
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Box(
        Modifier
            .fillMaxHeight()
            .width(buttonWidth)
            .background(containerColor)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(icon, label, tint = Color.White, modifier = Modifier.size(22.dp))
            Spacer(Modifier.height(2.dp))
            Text(label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/**
 * SwipeRevealBox 의 "세 버튼" 버전 — 밀면 [첫][둘][셋] 세 개가 드러난다.
 *   (2026-06-23 사장님 — 상담함 대기 카드: [스팸][사생활][정리]). 버튼 64dp 라 갤S9 에서도 들어감.
 */
@Composable
fun SwipeRevealThreeBox(
    onFirst: () -> Unit,
    onSecond: () -> Unit,
    onThird: () -> Unit,
    firstLabel: String,
    secondLabel: String,
    thirdLabel: String,
    firstColor: Color,
    secondColor: Color,
    thirdColor: Color,
    firstIcon: ImageVector,
    secondIcon: ImageVector,
    thirdIcon: ImageVector,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(14.dp),
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val buttonDp = 64.dp
    val revealDp = buttonDp * 3
    val revealPx = with(density) { revealDp.toPx() }
    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var opened by remember { mutableStateOf(false) }

    Box(
        modifier
            .fillMaxWidth()
            .clip(shape)
    ) {
        Box(Modifier.matchParentSize(), contentAlignment = Alignment.CenterEnd) {
            Row(Modifier.fillMaxHeight()) {
                RevealActionButton(buttonDp, firstColor, firstIcon, firstLabel, opened) {
                    onFirst(); opened = false; scope.launch { offsetX.animateTo(0f, tween(180)) }
                }
                RevealActionButton(buttonDp, secondColor, secondIcon, secondLabel, opened) {
                    onSecond(); opened = false; scope.launch { offsetX.animateTo(0f, tween(180)) }
                }
                RevealActionButton(buttonDp, thirdColor, thirdIcon, thirdLabel, opened) {
                    onThird(); opened = false; scope.launch { offsetX.animateTo(0f, tween(180)) }
                }
            }
        }
        Box(
            Modifier
                .clip(shape)
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            val open = offsetX.value < -revealPx * 0.35f
                            opened = open
                            scope.launch { offsetX.animateTo(if (open) -revealPx else 0f, tween(200)) }
                        },
                        onDragCancel = {
                            scope.launch { offsetX.animateTo(if (opened) -revealPx else 0f, tween(200)) }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            val target = (offsetX.value + dragAmount).coerceIn(-revealPx, 0f)
                            scope.launch { offsetX.snapTo(target) }
                        }
                    )
                }
                .pointerInput(opened) {
                    if (opened) {
                        detectTapGestures(onTap = {
                            opened = false
                            scope.launch { offsetX.animateTo(0f, tween(180)) }
                        })
                    }
                }
        ) { content() }
    }
}
