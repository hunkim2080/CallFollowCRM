package com.detailline.callfollowcrm.presentation.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.detailline.callfollowcrm.presentation.theme.Pretendard
import com.detailline.callfollowcrm.presentation.theme.TossBlue
import com.detailline.callfollowcrm.presentation.theme.TossBlueSoft
import com.detailline.callfollowcrm.presentation.theme.TossDivider
import com.detailline.callfollowcrm.presentation.theme.TossGrayBg
import com.detailline.callfollowcrm.presentation.theme.TossTextPrimary
import com.detailline.callfollowcrm.presentation.theme.TossTextSecondary
import com.detailline.callfollowcrm.presentation.theme.TossTextTertiary

/** Toss 스타일 큰 메인 버튼: 56dp 높이, full width, 굵은 글씨. */
@Composable
fun TossPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(56.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = TossBlue,
            contentColor = Color.White,
            disabledContainerColor = TossDivider,
            disabledContentColor = TossTextTertiary
        )
    ) {
        Text(text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * Toss 스타일 보조 버튼: 흰색 fill + 얇은 회색 border.
 * 하단 바 배경(TossGrayBg) 위에 올라가도 또렷하게 보이도록 흰색으로 변경.
 * (이전 회색 fill 은 같은 색 배경 위에서 사라져 보였음)
 */
@Composable
fun TossSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(56.dp),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, TossDivider),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.White,
            contentColor = TossTextPrimary,
            disabledContainerColor = Color.White,
            disabledContentColor = TossTextTertiary
        )
    ) {
        Text(text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Toss 스타일 텍스트 버튼 (서브 액션용). */
@Composable
fun TossTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        colors = ButtonDefaults.textButtonColors(contentColor = TossTextSecondary)
    ) {
        Text(text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

/** 흰색 카드 + 18dp 라운드 + 그림자 없음. 토스 시그니처 섹션. (프로토 .card = padding 16 / radius 18) */
@Composable
fun TossCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val base = modifier
        .fillMaxWidth()
        .background(Color.White, RoundedCornerShape(18.dp))
    val clickable = if (onClick != null) base.clickable(onClick = onClick) else base
    Box(modifier = clickable.padding(contentPadding)) {
        content()
    }
}

/** 큰 숫자 + 라벨 (홈 상단 통계 카드). */
@Composable
fun TossStatCard(
    label: String,
    value: Int,
    modifier: Modifier = Modifier,
    valueColor: Color = TossTextPrimary
) {
    TossCard(modifier = modifier, contentPadding = PaddingValues(20.dp)) {
        Column {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = TossTextTertiary
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "${value}건",
                style = MaterialTheme.typography.headlineMedium,
                color = valueColor
            )
        }
    }
}

/** 토스 칩: 둥근 알약 모양. */
@Composable
fun TossChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bg = if (selected) TossBlueSoft else Color.White
    val fg = if (selected) TossBlue else TossTextSecondary
    val border = if (selected) TossBlue else TossDivider
    Surface(
        modifier = modifier
            .height(38.dp)
            .clickable(onClick = onClick),
        shape = CircleShape,
        color = bg,
        border = BorderStroke(1.dp, border)
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text,
                color = fg,
                fontSize = 14.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
            )
        }
    }
}

/** 작은 상태 배지 (미처리/처리완료 등). */
@Composable
fun TossBadge(
    text: String,
    color: Color = TossBlue,
    background: Color = TossBlueSoft,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = background
    ) {
        Text(
            text,
            color = color,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

/** 섹션 헤더 (작은 카테고리 라벨용). */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier,
        style = MaterialTheme.typography.titleSmall,
        color = TossTextSecondary
    )
}

/** 프로토 .sheet-label — 입력칸 위 작은 라벨 (12px w700 t3). */
@Composable
fun SheetFieldLabel(text: String) {
    Text(
        text, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TossTextTertiary,
        modifier = Modifier.padding(start = 2.dp, top = 4.dp, bottom = 7.dp)
    )
}

/** 프로토 .sheet-input 박스 modifier (공용). */
private fun Modifier.sheetInputBox(minHeightDp: Int): Modifier = this
    .fillMaxWidth()
    .clip(RoundedCornerShape(12.dp))
    .background(TossGrayBg)
    .border(1.5.dp, TossDivider, RoundedCornerShape(12.dp))
    .let { if (minHeightDp > 0) it.heightIn(min = minHeightDp.dp) else it }
    .padding(horizontal = 14.dp, vertical = 13.dp)

/** TextFieldValue 버전 — 커서 제어가 필요한 입력칸(전화번호 등)용. */
@Composable
fun SheetTextField(
    value: androidx.compose.ui.text.input.TextFieldValue,
    onValueChange: (androidx.compose.ui.text.input.TextFieldValue) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    // 키보드 '완료' 키 처리 — 누르면 편집칸 닫힘/저장. 지정 시 IME 액션을 Done 으로 요청. (2026-07-13 사장님)
    imeAction: androidx.compose.ui.text.input.ImeAction = androidx.compose.ui.text.input.ImeAction.Default,
    onImeAction: (() -> Unit)? = null
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = TextStyle(fontFamily = Pretendard, fontSize = 15.sp, color = TossTextPrimary),
        cursorBrush = SolidColor(TossBlue),
        singleLine = singleLine,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        keyboardActions = if (onImeAction != null)
            androidx.compose.foundation.text.KeyboardActions(onDone = { onImeAction() })
        else androidx.compose.foundation.text.KeyboardActions.Default,
        modifier = modifier.sheetInputBox(0),
        decorationBox = { inner ->
            if (value.text.isEmpty()) Text(placeholder, color = TossTextTertiary, fontSize = 15.sp)
            inner()
        }
    )
}

/**
 * 프로토 .sheet-input — 폼 입력칸. 회색 채움 + 1.5dp 테두리 + radius12 + 15sp.
 *   Material OutlinedTextField(아웃라인/플로팅 라벨) 대체 — 프로토 폼 1:1.
 */
/**
 * 자동 하이픈(전화번호/사업자번호)이 들어가는 입력칸 — 커서 순서 꼬임 방지 공용 컴포넌트.
 *   String 상태를 onValueChange 에서 재포맷하면 갤S9 등에서 커서가 엉켜 숫자가 순서대로 안 들어감.
 *   내부에서 TextFieldValue 로 커서를 항상 맨 뒤에 고정. 상태는 여전히 포맷된 String (호출부 단순).
 *   (2026-06-11 사장님 전수 보고)
 * @param format 입력 raw → 표시 문자열 (예: PhoneNumberFormatter::formatProgressive)
 */
@Composable
fun FormattedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    format: (String) -> String,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Number
) {
    // 저장된 raw 값(예: "01080056674")도 항상 format() 거쳐 표시 → 전화=하이픈, 돈=콤마가 기본으로 보임.
    val shown = format(value)
    var tfv by remember { mutableStateOf(TextFieldValue(shown, TextRange(shown.length))) }
    if (tfv.text != shown) tfv = TextFieldValue(shown, TextRange(shown.length))
    SheetTextField(
        value = tfv,
        onValueChange = { newTfv ->
            val f = format(newTfv.text)
            tfv = TextFieldValue(f, TextRange(f.length))
            onValueChange(f)
        },
        placeholder = placeholder,
        keyboardType = keyboardType,
        modifier = modifier
    )
}

@Composable
fun SheetTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    minHeightDp: Int = 0,
    enabled: Boolean = true,
    visualTransformation: androidx.compose.ui.text.input.VisualTransformation =
        androidx.compose.ui.text.input.VisualTransformation.None
) {
    val boxMod = modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(12.dp))
        .background(TossGrayBg)
        .border(1.5.dp, TossDivider, RoundedCornerShape(12.dp))
        .let { if (minHeightDp > 0) it.heightIn(min = minHeightDp.dp) else it }
        .padding(horizontal = 14.dp, vertical = 13.dp)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        textStyle = TextStyle(fontFamily = Pretendard, fontSize = 15.sp, color = TossTextPrimary),
        cursorBrush = SolidColor(TossBlue),
        singleLine = singleLine,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = visualTransformation,
        modifier = boxMod,
        decorationBox = { inner ->
            if (value.isEmpty()) Text(placeholder, color = TossTextTertiary, fontSize = 15.sp)
            inner()
        }
    )
}

/**
 * 돈 입력칸 천 단위 콤마 — 상태(raw)는 숫자만 유지하고 화면에만 콤마를 끼움(2,500,000).
 *   저장 로직은 그대로(숫자만 파싱) → 안전. 커서는 끝 고정(돈은 보통 이어 입력). (2026-06-12 사장님: 돈=콤마 기본)
 */
/**
 * 전화번호 입력칸 하이픈 — 상태(raw)는 숫자만, 화면에만 010-0000-0000 하이픈. 커서 끝 고정.
 *   FormattedTextField 를 못 쓰는 plain OutlinedTextField/BasicTextField 전화칸용. (2026-06-12 사장님: 전화=하이픈 기본)
 */
val PhoneHyphenTransformation = androidx.compose.ui.text.input.VisualTransformation { text ->
    val digits = text.text.filter { it.isDigit() }
    if (digits.isEmpty()) return@VisualTransformation androidx.compose.ui.text.input.TransformedText(
        text, androidx.compose.ui.text.input.OffsetMapping.Identity
    )
    val formatted = com.detailline.callfollowcrm.util.PhoneNumberFormatter.formatProgressive(digits)
    androidx.compose.ui.text.input.TransformedText(
        androidx.compose.ui.text.AnnotatedString(formatted),
        object : androidx.compose.ui.text.input.OffsetMapping {
            override fun originalToTransformed(offset: Int): Int = formatted.length
            override fun transformedToOriginal(offset: Int): Int = text.text.length
        }
    )
}

val ThousandsCommaTransformation = androidx.compose.ui.text.input.VisualTransformation { text ->
    val raw = text.text
    if (raw.isEmpty()) return@VisualTransformation androidx.compose.ui.text.input.TransformedText(
        text, androidx.compose.ui.text.input.OffsetMapping.Identity
    )
    val number = raw.toLongOrNull() ?: return@VisualTransformation androidx.compose.ui.text.input.TransformedText(
        text, androidx.compose.ui.text.input.OffsetMapping.Identity
    )
    val formatted = java.text.NumberFormat.getNumberInstance(java.util.Locale.KOREA).format(number)
    androidx.compose.ui.text.input.TransformedText(
        androidx.compose.ui.text.AnnotatedString(formatted),
        object : androidx.compose.ui.text.input.OffsetMapping {
            override fun originalToTransformed(offset: Int): Int = formatted.length
            override fun transformedToOriginal(offset: Int): Int = raw.length
        }
    )
}

/**
 * 위 ThousandsCommaTransformation 과 달리 커서를 중간에 놓고 편집할 수 있는 버전(정확한 OffsetMapping).
 *   기존 것은 커서를 항상 끝으로 보내서(originalToTransformed=length) '새로 입력'엔 OK지만 '중간 수정'이 안 됨.
 *   → 문자 기반 가격추출 확인화면에서 사장님이 이미 있는 금액의 중간을 고칠 수 있어야 해서 추가. (2026-07-02 사장님)
 */
val ThousandsCommaEditTransformation = androidx.compose.ui.text.input.VisualTransformation { text ->
    val raw = text.text
    val number = raw.toLongOrNull()
    if (raw.isEmpty() || number == null) {
        return@VisualTransformation androidx.compose.ui.text.input.TransformedText(
            text, androidx.compose.ui.text.input.OffsetMapping.Identity
        )
    }
    val formatted = java.text.NumberFormat.getNumberInstance(java.util.Locale.KOREA).format(number)
    // 원본(숫자만) index ↔ 포맷(콤마 포함) index 를 정확히 매핑 → 커서가 중간으로 이동 가능.
    val o2t = IntArray(raw.length + 1)
    var oi = 0
    for (j in formatted.indices) {
        if (formatted[j] != ',') { o2t[oi] = j; oi++ }
    }
    o2t[raw.length] = formatted.length
    val t2o = IntArray(formatted.length + 1)
    var count = 0
    for (j in 0..formatted.length) {
        t2o[j] = count
        if (j < formatted.length && formatted[j] != ',') count++
    }
    androidx.compose.ui.text.input.TransformedText(
        androidx.compose.ui.text.AnnotatedString(formatted),
        object : androidx.compose.ui.text.input.OffsetMapping {
            override fun originalToTransformed(offset: Int): Int = o2t[offset.coerceIn(0, raw.length)]
            override fun transformedToOriginal(offset: Int): Int = t2o[offset.coerceIn(0, formatted.length)]
        }
    )
}
