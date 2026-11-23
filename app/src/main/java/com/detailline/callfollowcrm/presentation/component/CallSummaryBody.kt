package com.detailline.callfollowcrm.presentation.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.text.style.TextDecoration
import com.detailline.callfollowcrm.presentation.theme.AppShape
import com.detailline.callfollowcrm.presentation.theme.AppSpace
import com.detailline.callfollowcrm.presentation.theme.AppTheme
import com.detailline.callfollowcrm.presentation.theme.AppType
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.detailline.callfollowcrm.util.CallSummaryLines

/**
 * 통화 요약 본문 — **시각 · 화자 점 · 문장.**
 *
 * 🔒 **통화 요약을 그리는 곳은 여기 하나다. 새 화면에서도 반드시 이걸 쓴다.**
 *
 * 왜 이 파일이 생겼나 (2026-09-25 사장님: "항상 프로토로 잘 만들고 적용할 때 되면 다 빼먹는거 같네")
 *   2026-09-24 에 「시간 구간으로 요약」을 넣으면서 **통화요약 화면만** 고쳤다.
 *   채팅 통화카드는 `.map { it.text }` 로 **시각과 화자를 버리고** 문장만 그리고 있었다.
 *   읽는 코드([CallSummaryLines])는 한 군데였는데 **그리는 코드가 두 벌**이라,
 *   한쪽만 새 모양이 됐고 사장님이 매일 보는 쪽이 옛 모양으로 남았다.
 *   → 그리는 것도 한 군데로 모은다. **한 군데뿐이면 빼먹을 수가 없다.**
 *
 * @param rawLines 서버가 준 **원본 줄** 목록. `0:00-0:46|나|문장` 또는 옛 모양(`고객: 문장`).
 *   ⚠️ **깎은 걸 넘기지 마라.** `parse(...).map { it.text }` 한 결과를 넘기면 시각·화자가
 *   이미 떨어져 나간 뒤라 여기서 아무리 잘 그려도 시각이 안 나온다. `CallSummaryLines.rawLines()` 를 써라.
 *   (2026-09-25 실사고 — 단위 테스트 CallSummaryRawLinesTest 로 묶어뒀다)
 * @param skipText 제목과 똑같은 줄은 뺀다(같은 말 두 번 방지). 없으면 다 그린다.
 * @param onSeek 시각을 누르면 **그 대목부터 들려준다**(밀리초). (2026-09-25 사장님 "우리도 그렇게하자")
 *   녹음이 없는 통화는 **null 을 넘긴다** — 그러면 알약으로 안 그린다.
 *   못 누르는 걸 누를 수 있는 것처럼 그리면 고장으로 보인다.
 */
@Composable
fun CallSummaryBody(
    rawLines: List<String>,
    textColor: Color,
    timeColor: Color,
    /** `나` 가 말한 줄의 점 색. `손님` 은 [customerDot]. */
    ownerDot: Color,
    customerDot: Color,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 15.sp,
    lineHeight: TextUnit = 22.sp,
    timeFontSize: TextUnit = 12.sp,
    timeWidth: Dp = 66.dp,
    rowGap: Dp = 9.dp,
    /** plain 모드에서 • 불릿 색. 기본은 글자색. */
    bulletColor: Color = textColor,
    /** true = 시간·화자 없이 깔끔한 • 불릿만(요점용). 듣기·범례 없음. (2026-11-19 프로토 ① A안) */
    plain: Boolean = false,
    skipText: String? = null,
    onSeek: ((Long) -> Unit)? = null,
    /** 누를 수 있는 시각 알약의 바탕색. 부르는 쪽 카드 색에 맞춰 넘긴다. */
    timeChipBg: Color = Color.Transparent
) {
    val rows = rawLines.mapNotNull { CallSummaryLines.parseOne(it) }
        .filter { skipText == null || it.text != skipText }
    Column(modifier) {
        if (plain) {
            // 요점 = 깔끔 불릿 — 시간·화자 떼고 • + 문장만. 듣기는 통화 전문에서. (2026-11-19 프로토 ① A안)
            rows.forEachIndexed { i, row ->
                if (i > 0) Spacer(Modifier.height(rowGap))
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        "•", color = bulletColor, fontSize = fontSize,
                        fontWeight = FontWeight.Bold, lineHeight = lineHeight,
                        modifier = Modifier.padding(end = 10.dp)
                    )
                    Text(
                        boldMd(row.text), fontSize = fontSize, color = textColor,
                        lineHeight = lineHeight, fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        } else {
        rows.forEachIndexed { i, row ->
            if (i > 0) Spacer(Modifier.height(rowGap))
            Row(verticalAlignment = Alignment.Top) {
                if (row.time.isBlank() && row.speaker.isBlank()) {
                    // 시각을 모르는 옛 요약 — 점 하나만 찍어 준다.
                    Text("· ", fontSize = fontSize, color = textColor, fontWeight = FontWeight.Bold)
                } else {
                    if (row.time.isNotBlank()) {
                        // 녹음이 있으면 **누를 수 있는 알약** — 에이닷처럼 누르면 그 대목부터 들린다.
                        val startMs = if (onSeek != null) CallSummaryLines.startMsOf(row) else null
                        if (startMs != null) {
                            // ⚠️ 폭을 **고정**하면 "10:00-13:30" 같은 긴 구간이 잘린다.
                            //   최소폭만 잡고, 길면 늘어나게 한다(줄 맞춤은 대부분 유지되고 글자는 안 잘린다).
                            Box(
                                Modifier.padding(end = 6.dp).widthIn(min = timeWidth)
                                    .clip(com.detailline.callfollowcrm.presentation.theme.AppShape.sm)
                                    .background(timeChipBg)
                                    .clickable { onSeek?.invoke(startMs) }
                                    // 👆 세로 2dp 면 높이 20dp — 윗줄/아랫줄이 같이 눌린다.
                                    .padding(horizontal = 6.dp, vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    row.time, fontSize = timeFontSize, color = timeColor,
                                    fontWeight = FontWeight.Bold, maxLines = 1
                                )
                            }
                        } else {
                            Text(
                                row.time, fontSize = timeFontSize, color = timeColor,
                                fontWeight = FontWeight.SemiBold, lineHeight = lineHeight, maxLines = 1,
                                modifier = Modifier.width(timeWidth)
                            )
                        }
                    }
                    if (row.speaker.isNotBlank()) {
                        // 색 점만으론 누가 말했는지 헷갈린다(요약화면은 양쪽 같은 파랑이기도 했다) →
                        //   **글자로** 보여준다. 색은 보조. (2026-11-18 코덱스 리뷰)
                        val isCustomer = row.speaker == "손님"
                        Text(
                            row.speaker,
                            fontSize = timeFontSize,
                            color = if (isCustomer) customerDot else ownerDot,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            modifier = Modifier.padding(top = 1.dp, end = 8.dp)
                        )
                    }
                }
                Text(
                    row.text, fontSize = fontSize, color = textColor, lineHeight = lineHeight,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // ── 점이 무슨 뜻인지 · 그리고 그게 **AI 짐작**이라는 것 ──
        //   전엔 통화요약 화면에만 적혀 있고, 사장님이 매일 보는 채팅 통화카드엔 없었다.
        //   그리는 곳이 하나니 여기 두면 **두 화면이 같이 갖는다.** (2026-09-25 점검)
        //   후속 문자가 고객에게 나가니, 미심쩍으면 전문을 보시라고 적어둔다.
        if (rows.any { it.speaker.isNotBlank() }) {
            // 화자를 줄마다 글자로 보여주니, 범례 점은 뺀다. "AI 짐작" 안내만 남긴다. (2026-11-18)
            Spacer(Modifier.height(rowGap + 2.dp))
            Text("· 손님·나 는 AI 짐작이에요 (통화 전문에서 확인)",
                fontSize = timeFontSize, color = timeColor, maxLines = 1)
        }
        }
    }
}

/**
 * 요점 불릿의 **핵심어 굵게** — 서버가 `**…**` 로 감싼 부분만 Bold. (2026-11-19 프로토 ① A안)
 * 마크가 없으면 그대로(보통 굵기). 서버 AI 프롬프트가 부위·자재·일정·금액을 **…** 로 감싼다.
 */
private fun boldMd(text: String): androidx.compose.ui.text.AnnotatedString =
    androidx.compose.ui.text.buildAnnotatedString {
        val re = Regex("""\*\*(.+?)\*\*""")
        var last = 0
        re.findAll(text).forEach { m ->
            append(text.substring(last, m.range.first))
            pushStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold))
            append(m.groupValues[1])
            pop()
            last = m.range.last + 1
        }
        if (last < text.length) append(text.substring(last))
    }

/** 이 요약에 시각·화자가 들어 있나 — "손님/나 는 AI 짐작" 안내를 띄울지 판단할 때. */
fun hasSpeakerGuess(rawLines: List<String>): Boolean =
    rawLines.any { it.count { c -> c == '|' } >= 2 }

/** 서버 todos JSON 배열 → 문자열 목록(빈 값 제거). 체크리스트(시트)·챗 카드 칩이 **같이 쓴다**. (2026-11-19)
 *   ⚠️ LLM 이 가끔 스키마 필드명(suggested_followup_sms·tags 등)을 todos 배열에 토해낸다 — 할 일처럼 떠버림.
 *     (2026-11-23 사장님) 할 일은 한국어 문장이라, **공백 없는 ascii 소문자 식별자(snake_case)** 는 전부 버린다. */
private val TODO_JUNK_KEY = Regex("^[a-z][a-z0-9_]*$")
fun parseCallTodos(todosJson: String?): List<String> =
    if (todosJson.isNullOrBlank()) emptyList()
    else runCatching {
        val arr = org.json.JSONArray(todosJson)
        (0 until arr.length()).mapNotNull {
            arr.optString(it).trim().takeIf { s -> s.isNotBlank() && !TODO_JUNK_KEY.matches(s) }
        }
    }.getOrDefault(emptyList())

/**
 * 챗 통화카드용 '꼭 챙겨야 할 일' 칩(◻ pill). 탭 = 완료(onComplete → 부르는 쪽이 저장).
 * 시트의 회색 박스(CallTodoChecklist)와 **같은 데이터·parseCallTodos**, 모양만 다름(§12-I). (2026-11-19 사장님 A안)
 * 🔒 챗 카드에서 그리는 곳도 여기 하나 — ChatScreen 이 커지지 않게 component 로 뺐다(§12-F).
 */
@Composable
fun CallTodoChips(todosJson: String?, doneJson: String?, onToggle: (String) -> Unit, modifier: Modifier = Modifier) {
    val todos = parseCallTodos(todosJson)
    if (todos.isEmpty()) return
    val done = remember(doneJson) { parseCallTodos(doneJson).toSet() }
    val c = AppTheme.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        todos.forEach { t ->
            val isDone = t in done
            // 탭 = 완료 표시(취소선). 지우지 않고 남는다 — 다시 탭하면 해제. (2026-11-19 사장님)
            val edge = if (isDone) c.textHint else c.primary
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.widthIn(max = 240.dp).clip(AppShape.pill)
                    .border(1.3.dp, edge.copy(alpha = 0.4f), AppShape.pill)
                    .clickable { onToggle(t) }
                    .padding(horizontal = 9.dp, vertical = 4.dp)
            ) {
                Box(
                    Modifier.size(13.dp).clip(AppShape.sm)
                        .background(if (isDone) c.primary else Color.Transparent)
                        .border(1.5.dp, if (isDone) c.primary else c.primary.copy(alpha = 0.45f), AppShape.sm),
                    contentAlignment = Alignment.Center
                ) { if (isDone) Text("✓", style = AppType.micro, color = c.surface) }
                Spacer(Modifier.width(6.dp))
                Text(
                    t, style = AppType.label, maxLines = 1,
                    color = if (isDone) c.textHint else c.primaryText,
                    textDecoration = if (isDone) TextDecoration.LineThrough else null,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * 📋 **꼭 챙겨야 할 일** — 통화에서 뽑은 할 일(서버 todos) 체크리스트. (2026-11-18 통화요약 개편 프로토)
 *
 * 🔒 통화 할 일을 그리는 곳도 여기 하나다(요약 그리기와 한집 — §12).
 * ⚠️ 체크는 **지금은 보기용**(이 화면 안에서만 줄 그어짐). 고객 카드·홈 '할 일'로의 **연동은 '할 일'
 *   기능을 붙일 때** 한다(어디 붙일지 추후, 사장님). todos 가 비면 **아무것도 안 그린다.**
 *
 * @param todosJson 서버가 준 `["욕실 사진 받기", …]` JSON 배열 문자열(엔티티 `todosJson`). null/빈 = 안 그림.
 */
@Composable
fun CallTodoChecklist(todosJson: String?, doneJson: String? = null, modifier: Modifier = Modifier, onToggle: ((String) -> Unit)? = null) {
    val todos = remember(todosJson) { parseCallTodos(todosJson) }
    if (todos.isEmpty()) return
    val c = AppTheme.colors
    val persistedDone = remember(doneJson) { parseCallTodos(doneJson).toSet() }
    // onToggle 없으면(미리보기) 로컬 토글, 있으면 저장된 완료표시(todosDoneJson)로 취소선
    val localChecked = remember(todosJson) { mutableStateListOf(*Array(todos.size) { false }) }
    Column(modifier) {
        Text("꼭 챙겨야 할 일", style = AppType.label, color = c.textSub)
        todos.forEachIndexed { i, t ->
            Spacer(Modifier.height(AppSpace.s8))
            val on = if (onToggle != null) (t in persistedDone) else localChecked.getOrElse(i) { false }
            // 회색 둥근 박스(프로토 ① .trow). 탭=완료 표시(취소선), 다시 탭하면 해제. (2026-11-19)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(AppShape.md)
                    .background(c.bg)
                    .clickable { if (onToggle != null) onToggle(t) else if (i < localChecked.size) localChecked[i] = !on }
                    .heightIn(min = 48.dp)
                    .padding(horizontal = AppSpace.s12, vertical = AppSpace.s12)
            ) {
                Box(
                    Modifier.size(20.dp).clip(AppShape.sm)
                        .background(if (on) c.primary else c.surface)
                        .border(2.dp, if (on) c.primary else c.textHint, AppShape.sm),
                    contentAlignment = Alignment.Center
                ) {
                    if (on) Text("✓", style = AppType.caption, color = c.surface)
                }
                Spacer(Modifier.width(AppSpace.s12))
                Text(
                    t, style = AppType.headline,
                    color = if (on) c.textHint else c.text,
                    textDecoration = if (on) TextDecoration.LineThrough else null,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
