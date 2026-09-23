package com.detailline.callfollowcrm.presentation.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.detailline.callfollowcrm.presentation.theme.AppShape
import com.detailline.callfollowcrm.presentation.theme.AppType
import com.detailline.callfollowcrm.presentation.theme.AppTheme
import com.detailline.callfollowcrm.util.joinDongHo

/**
 * 현장 주소 받는 칸 — **앱 안 모든 주소 입력이 이 한 부품을 쓴다.** (2026-09-23 사장님)
 *
 * 사장님: "주소를 받는창은 전부 이 형식을 지키게 해줘"
 *
 * 전엔 네 군데가 다 달랐다:
 *   · 고객 상세 — 검색 있음 + "동·호수 (선택)" 자유입력
 *   · 일정 추가 — 회색칸 "눌러서 주소 찾기" + 자유입력
 *   · 협업 배정 시트 — **주소 검색이 아예 없어 통째로 손타이핑** (오타 나면 상대 사장님이 못 찾아감)
 *   · 시공접수서(서버) — 회색칸 + 자유입력
 *
 * 이 부품이 정하는 것:
 *   ① 주소는 **반드시 검색으로** — 파란 버튼, 고르면 초록. 직접 타이핑 칸은 없다.
 *   ② 동·호수는 **[동] [호] 두 칸** — 뭘 적든 저장은 늘 "103동 1103호" 한 모양.
 *      (빈 칸 하나면 103동 1103호 / 103-1103 / 103/1103 / 1103 이 제각각 들어온다)
 *   ③ 주소를 고르기 전엔 동·호수를 안 띄운다 — 순서대로 한 가지씩.
 *
 * @param address 검색으로 고른 도로명 주소 (비면 아직 안 고른 것)
 * @param preview 밑에 "기사님이 받는 주소" 한 줄을 띄울지. 고객에게 나가는 화면에서만 켠다.
 */
@Composable
fun SiteAddressField(
    address: String,
    dong: String,
    ho: String,
    onSearch: () -> Unit,
    onDong: (String) -> Unit,
    onHo: (String) -> Unit,
    modifier: Modifier = Modifier,
    preview: Boolean = false,
) {
    val filled = address.isNotBlank()
    Column(modifier.fillMaxWidth()) {
        // ① 주소 — 이 화면의 주인공이라 파란 버튼. 전엔 메모칸과 똑같은 회색이었다.
        Row(
            Modifier
                .fillMaxWidth()
                .clip(AppShape.md)
                .background(if (filled) AppTheme.colors.doneBg else AppTheme.colors.primaryBg)
                .border(
                    1.5.dp,
                    if (filled) AppTheme.colors.done else AppTheme.colors.primary,
                    AppShape.md,
                )
                .clickable(onClick = onSearch)
                .padding(horizontal = 14.dp, vertical = 13.dp)
                .defaultMinSize(minHeight = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (filled) Icons.Default.Check else Icons.Default.Search,
                null,
                tint = if (filled) AppTheme.colors.doneText else AppTheme.colors.primaryText,
                modifier = Modifier.size(17.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                address.ifBlank { "주소 찾기" },
                style = AppType.headline,
                color = if (filled) AppTheme.colors.doneText else AppTheme.colors.primaryText,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (!filled) {
                Spacer(Modifier.width(8.dp))
                Text(
                    "찾기",
                    style = AppType.label,
                    color = AppTheme.colors.textOnPrimary,
                    modifier = Modifier
                        .clip(AppShape.sm)
                        .background(AppTheme.colors.primary)
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                )
            } else {
                Spacer(Modifier.width(8.dp))
                Text(
                    "다시 찾기",
                    style = AppType.label,
                    color = AppTheme.colors.doneText,
                )
            }
        }

        // ② 동·호수 — 주소를 고른 뒤에만. 순서대로 한 가지씩 묻는다.
        if (filled) {
            Spacer(Modifier.height(9.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                UnitBox(dong, "103", "동", onDong, Modifier.weight(1f))
                UnitBox(ho, "1103", "호", onHo, Modifier.weight(1f))
            }
            Spacer(Modifier.height(7.dp))
            Text(
                "단독·빌라라 동이 없으면 호만 적어주세요",
                style = AppType.caption,
                color = AppTheme.colors.textHint,
            )
            if (preview) {
                Spacer(Modifier.height(10.dp))
                val tail = joinDongHo(dong, ho)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(AppShape.md)
                        .background(AppTheme.colors.surface)
                        .border(1.5.dp, AppTheme.colors.line, AppShape.md)
                        .padding(horizontal = 13.dp, vertical = 11.dp),
                ) {
                    Column {
                        Text(
                            "기사님이 받는 주소",
                            style = AppType.micro,
                            color = AppTheme.colors.textHint,
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            buildAnnotatedString {
                                append(address)
                                if (tail.isNotBlank()) {
                                    append(" ")
                                    append(tail)
                                } else {
                                    withStyle(SpanStyle(color = AppTheme.colors.textHint)) {
                                        append(" · 동/호수 없음")
                                    }
                                }
                            },
                            style = AppType.body.copy(fontWeight = FontWeight.Bold),
                            color = AppTheme.colors.text,
                        )
                    }
                }
            }
        }
    }
}

/** [동] [호] 한 칸 — 숫자 키패드가 바로 뜨고, 단위 글자가 칸 안에 붙어 있다. */
@Composable
private fun UnitBox(
    value: String,
    hint: String,
    unit: String,
    onValue: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier
            .clip(AppShape.md)
            .background(if (focused) AppTheme.colors.surface else AppTheme.colors.surfaceMuted)
            .border(
                1.5.dp,
                if (focused) AppTheme.colors.primary else AppTheme.colors.line,
                AppShape.md,
            )
            .padding(horizontal = 13.dp)
            .height(50.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) {
                Text(hint, style = AppType.headline, color = AppTheme.colors.textHint)
            }
            BasicTextField(
                value = value,
                // 단위 글자는 칸에 이미 있다 — 사용자가 "동"까지 적어도 한 번만 붙게 걸러낸다.
                onValueChange = { onValue(it.filter { c -> c != '동' && c != '호' }) },
                singleLine = true,
                textStyle = AppType.headline.copy(color = AppTheme.colors.text),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { focused = it.isFocused },
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(unit, style = AppType.label, color = AppTheme.colors.textSub)
    }
}
