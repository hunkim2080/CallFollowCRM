package com.detailline.callfollowcrm.presentation.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.detailline.callfollowcrm.CallFollowCrmApplication
import com.detailline.callfollowcrm.presentation.theme.AppTheme
import com.detailline.callfollowcrm.presentation.theme.AppType

/**
 * 첫 사용 힌트 — 제목 밑 "회색 설명글"을 **그 화면/시트를 처음 열 때 한 번만** 보여준다.
 * 닫고 다음에 다시 열면 안 뜬다. 까먹었을 때는 **제목 옆 ⓘ** 를 눌러 언제든 다시 본다.
 *
 * 왜 공용인가: 업데이트하며 화면마다 설명글이 상설로 쌓였고 말투·크기가 제각각이 됐다.
 *   한 곳에서 「첫 사용에만 · 같은 글자 모양(AppType.body + textSub)」으로 통일한다. (§12 두 벌 금지)
 *
 * 쓰는 법:
 * ```
 * val hint = rememberFirstRunHint("estimate_sheet")   // key 는 화면/시트마다 고유
 * Row(verticalAlignment = Alignment.CenterVertically) {
 *     Text("접수서 보내기", ...)          // 제목
 *     HintInfoDot(hint)                  // 제목 옆 작은 ⓘ (항상 있음 → 언제든 다시 보기)
 * }
 * FirstRunHintText(hint, "항목을 고르면, 고객이 링크에서 주소·날짜를 적어요")
 * ```
 * - 저장: AppPreferences.hintSeen 한 칸. (key 가 들어 있으면 "이미 봤다")
 * - 글자 모양은 이 부품이 고정한다 — 호출부는 **문구만** 넘긴다(크기·색 통일).
 */
@Stable
class FirstRunHintState internal constructor(
    /** 이 화면을 처음 여는 것인지(= hintSeen 에 없었는지). */
    val firstRun: Boolean,
) {
    /** 지금 설명글이 보이는지. 처음엔 false → 첫 사용이면 LaunchedEffect 가 true 로(스르륵 등장). */
    var visible by mutableStateOf(false)
        internal set

    /** 제목 옆 ⓘ 를 눌렀을 때 — 다시 보여준다(hintSeen 과 무관). */
    fun show() { visible = true }
}

/**
 * 화면/시트 머리에서 한 번 부른다. key 가 hintSeen 에 없으면 "첫 사용"으로 보고
 * 설명글을 스르륵 띄우고 **본 것으로 기록**(다음부턴 자동으로 안 뜸).
 */
@Composable
fun rememberFirstRunHint(key: String): FirstRunHintState {
    val context = LocalContext.current
    val prefs = remember(context) {
        (context.applicationContext as CallFollowCrmApplication).container.preferences
    }
    val state = remember(key) { FirstRunHintState(firstRun = !prefs.hintSeen.contains(key)) }
    LaunchedEffect(key) {
        if (state.firstRun) {
            // 처음 본 것으로 기록 → 다음에 열면 안 뜬다. (ⓘ 로는 여전히 볼 수 있음)
            if (!prefs.hintSeen.contains(key)) prefs.hintSeen = prefs.hintSeen + key
            state.visible = true   // false → true 로 바뀌며 AnimatedVisibility 가 스르륵 띄움
        }
    }
    return state
}

/** 제목 옆에 두는 작은 ⓘ — 언제든 눌러 설명글을 다시 띄운다. */
@Composable
fun HintInfoDot(state: FirstRunHintState, modifier: Modifier = Modifier) {
    Icon(
        imageVector = Icons.Outlined.Info,
        contentDescription = "설명 보기",
        tint = AppTheme.colors.textHint,
        modifier = modifier
            .size(30.dp)            // 터치 영역
            .clip(CircleShape)
            .clickable { state.show() }
            .padding(7.dp)          // 아이콘 자체는 16
    )
}

/** 제목 밑 설명글 — 첫 사용일 때만(또는 ⓘ 를 눌렀을 때) 스르륵 보인다. 글자 모양은 여기서 고정. */
@Composable
fun FirstRunHintText(state: FirstRunHintState, text: String, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = state.visible,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        HintBody(text, modifier)
    }
}

/**
 * 항상 보이는 설명글 — **숨기면 안 되는** 경고·빈 화면·결과 안내용.
 * 첫 사용 힌트([FirstRunHintText])와 **똑같은 모양**(AppType.body + textHint)이라, 화면마다
 * 제각각이던 색(Tertiary/Secondary/Info)·크기(11~13.5sp)를 한 모양으로 통일한다. (사장님 2026-10-23)
 */
@Composable
fun HelpText(text: String, modifier: Modifier = Modifier) {
    HintBody(text, modifier)
}

/** 설명글 글자 모양 한 곳 — 첫사용 힌트와 항상-표시가 같은 모양이 되도록 여기서만 정한다.
 *   크기는 디자인시스템이 "도움말"로 정한 caption(11.5sp), 색은 가장 흔한 흐린 회색 textHint(=TossTextTertiary). */
@Composable
private fun HintBody(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = AppType.caption,
        color = AppTheme.colors.textHint,
        modifier = modifier,
    )
}
