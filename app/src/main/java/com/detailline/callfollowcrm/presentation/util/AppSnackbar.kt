package com.detailline.callfollowcrm.presentation.util

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.detailline.callfollowcrm.domain.ui.SnackbarTiming
import com.detailline.callfollowcrm.presentation.theme.AppShape
import com.detailline.callfollowcrm.presentation.theme.AppSpace
import com.detailline.callfollowcrm.presentation.theme.AppTheme
import com.detailline.callfollowcrm.presentation.theme.AppType
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 🍞 **확인 띠(스낵바) — 앱 전체가 이 한 벌을 쓴다.** (2026-10-19 사장님)
 *
 * 사장님: 확인 안내 띠가 **화면을 가리고 ~4초나 떠서 다음 동작을 막는다.**
 * 그래서 세 가지를 한 곳에서 바꾼다 —
 *   1. **짧게** — 얼마나 두나는 [SnackbarTiming] 한 곳(확인 1.5초 · 실패/되돌리기 3초).
 *   2. **가볍게** — 화면 폭을 다 덮는 검정 띠 대신 **작은 알약**(좌우 여백 넉넉, 한 줄 위주).
 *   3. **눌러서 사라지게** — 알약을 탭하면 즉시 치운다.
 *
 * 쓰는 법 — 화면마다 두 군데만 바꾼다:
 *   · `Scaffold(snackbarHost = { AppSnackbarHost(state) })`
 *   · `state.showSnackbar(msg)` → `state.showBrief(msg)`  (되돌리기는 `showBrief(msg, "되돌리기")`)
 *
 * ❌ 화면에서 `SnackbarHost(state)` 를 직접 쓰거나 `showSnackbar(…, duration = Short)` 를 직접 부르지 않는다 —
 *    그러면 그 화면만 또 4초짜리 큰 띠가 된다(두 벌). 빌드의 `dup_guard: snackbar-raw` 가 막는다.
 *
 * ⚠️ 일정 화면(ScheduleScreen)은 2026-10-19 현재 미커밋 변경 중이라 **아직 옛 모양**이다 — M 정리 뒤 같이 바꾼다.
 */
@Composable
fun AppSnackbarHost(state: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(hostState = state, modifier = modifier) { data -> AppSnackbarPill(data) }
}

/**
 * 🍞 **짧게 띄운다.** `showSnackbar` 자리에 그대로 들어간다(suspend).
 *
 * 어떻게 짧아지나 — Material 의 `Short` 는 4초로 못 박혀 있어 못 줄인다.
 *   그래서 **`Indefinite` 로 띄워 두고 우리가 시간을 재서 끊는다**(`withTimeoutOrNull`).
 *   시간이 되면 코루틴이 취소되고, `showSnackbar` 는 취소되면 띠를 내린다 — 그게 곧 「사라짐」이다.
 *
 * 먼저 떠 있던 띠는 **새 말이 오면 바로 내린다**(최신 것이 이긴다).
 *   안 그러면 둘째 말이 첫째가 끝나길 기다리느라 **자기 시간을 다 써 버리고 깜빡 하고 만다.**
 *
 * @return 「되돌리기」를 눌렀으면 [SnackbarResult.ActionPerformed], 아니면 [SnackbarResult.Dismissed].
 */
suspend fun SnackbarHostState.showBrief(message: String, actionLabel: String? = null): SnackbarResult {
    currentSnackbarData?.dismiss()
    val ms = SnackbarTiming.snackbarDurationMs(message, hasAction = actionLabel != null)
    return withTimeoutOrNull(ms) {
        showSnackbar(message = message, actionLabel = actionLabel, duration = SnackbarDuration.Indefinite)
    } ?: SnackbarResult.Dismissed
}

/** 알약 하나 — 어두운 글자색 바탕에 밝은 글자. 탭 = 닫기, 「되돌리기」 글자 = 실행. */
@Composable
private fun AppSnackbarPill(data: SnackbarData) {
    val c = AppTheme.colors
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpace.s24, vertical = AppSpace.s12),
        contentAlignment = Alignment.Center
    ) {
        Row(
            Modifier
                .clip(AppShape.pill)
                .background(c.text)
                .clickable { data.dismiss() }
                .padding(horizontal = AppSpace.s16, vertical = AppSpace.s12)
                .semantics { liveRegion = LiveRegionMode.Polite },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                data.visuals.message,
                style = AppType.body,
                color = c.surface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            data.visuals.actionLabel?.let { label ->
                Spacer(Modifier.width(AppSpace.s8))
                Text(
                    label,
                    style = AppType.label,
                    color = c.primary,
                    maxLines = 1,
                    modifier = Modifier
                        .clip(AppShape.pill)
                        .clickable { data.performAction() }
                        .padding(horizontal = AppSpace.s8, vertical = AppSpace.s4)
                )
            }
        }
    }
}
