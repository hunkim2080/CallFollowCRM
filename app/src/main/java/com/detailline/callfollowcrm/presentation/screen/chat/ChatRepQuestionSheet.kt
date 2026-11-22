package com.detailline.callfollowcrm.presentation.screen.chat

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.detailline.callfollowcrm.data.local.entity.RepQuestionEntity
import com.detailline.callfollowcrm.presentation.component.AppSheet
import com.detailline.callfollowcrm.presentation.screen.faq.RepQuestionEmptyState
import com.detailline.callfollowcrm.presentation.theme.AppShape
import com.detailline.callfollowcrm.presentation.theme.AppSpace
import com.detailline.callfollowcrm.presentation.theme.AppTheme
import com.detailline.callfollowcrm.presentation.theme.AppType
import com.detailline.callfollowcrm.util.TemplatePhotoStore

/**
 * 💬 **대표질문 — 채팅 ⊕ 「대표질문」.** (2026-11-21 사장님 「가」 · 프로토 EYKUs11cVT7JW71yVrVoBA ①)
 *
 *   ⊕ → 시트(등록한 질문 목록) → 질문을 탭 → **그 답이 답장 입력칸에 들어간다**(전송 아님 — 사장님이 보고 ▶).
 *   답에 사진이 붙어 있으면 **첨부에 같이 담긴다**.
 *
 *   새로 만든 건 **이 시트 한 장**이다 — 나머지는 있는 걸 쓴다(§12):
 *     시트 틀 `AppSheet` · 글 넣기 `ComposerInsert.append`(문구 넣기와 같은 방식 — ChatScreen 이 부른다) ·
 *     사진 담기 `attachCapped`(같은 사진 한 번만 · 전체 5장 한도 `PickLimit`) · 빈 화면 `RepQuestionEmptyState`.
 */
@Composable
internal fun ChatRepQuestionHost(
    open: Boolean,
    onClose: () -> Unit,
    questions: List<RepQuestionEntity>,
    attached: List<Uri>,
    /** (넣을 답, 새 첨부 목록 전체). 입력칸에 붙이는 건 부른 쪽이 한다. */
    onInsert: (answer: String, photos: List<Uri>) -> Unit,
    onManage: () -> Unit,
    onAdd: () -> Unit,
) {
    val ctx = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val attachedNow by rememberUpdatedState(attached)

    if (open) {
        // 키보드가 떠 있으면 내린다 — 안 그러면 시트가 키보드 위에 얹혀 목록이 반만 보인다.
        LaunchedEffect(Unit) { keyboard?.hide() }
        AppSheet(onDismiss = onClose) {
            // 머리: 대표질문 ……… 관리
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "대표질문", style = AppType.headline, fontWeight = FontWeight.ExtraBold,
                    color = AppTheme.colors.text, modifier = Modifier.weight(1f)
                )
                Text(
                    "관리", style = AppType.label, color = AppTheme.colors.primaryText,
                    modifier = Modifier.clip(AppShape.sm).clickable { onClose(); onManage() }
                        .padding(horizontal = AppSpace.s8, vertical = AppSpace.s8)
                )
            }
            Spacer(Modifier.height(AppSpace.s8))

            // 분류 칩 — 많아지면 한 탭으로 그 분류만 본다. 손님 페이지와 같은 거르기. (분류가 하나도 없으면 안 보임)
            val categories = remember(questions) { questions.map { it.category }.filter { it.isNotBlank() }.distinct() }
            var selectedCat by remember(questions) { mutableStateOf("") }
            if (categories.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(AppSpace.s8)
                ) {
                    CatFilterChip("전체", selectedCat.isBlank()) { selectedCat = "" }
                    categories.forEach { c -> CatFilterChip(c, selectedCat == c) { selectedCat = c } }
                }
                Spacer(Modifier.height(AppSpace.s12))
            }
            val shown = if (selectedCat.isBlank()) questions else questions.filter { it.category == selectedCat }

            if (questions.isEmpty()) RepQuestionEmptyState()
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                shown.forEach { q ->
                    QuestionCard(q) {
                        // 시트를 먼저 닫는다 — 같은 질문을 연달아 눌러 두 번 들어가는 일이 없다.
                        onClose()
                        val uri = q.imageUri?.let { runCatching { Uri.parse(it) }.getOrNull() }
                        // 사진 파일이 사라졌으면(백업 복원 등) 죽은 주소를 담지 않고 **말한다**.
                        val alive = q.imageUri != null && TemplatePhotoStore.fileFor(ctx, q.imageUri) != null
                        if (q.imageUri != null && !alive) {
                            Toast.makeText(ctx, "사진을 찾지 못해 글만 넣었어요", Toast.LENGTH_SHORT).show()
                        }
                        val photos = if (uri != null && alive) attachCapped(ctx, attachedNow, listOf(uri)) else attachedNow
                        onInsert(q.answer, photos)
                    }
                }
                AddRow { onClose(); onAdd() }
            }
        }
    }
}

/** 프로토 .faqcard — 「Q. 질문」 굵게 / 답 두 줄까지 / 사진이 있으면 작은 딱지. */
@Composable
private fun QuestionCard(q: RepQuestionEntity, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(AppShape.lg).border(1.dp, AppTheme.colors.line, AppShape.lg)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 13.dp)
    ) {
        if (q.category.isNotBlank()) {
            Text(
                q.category, style = AppType.caption, color = AppTheme.colors.primaryText,
                modifier = Modifier.clip(AppShape.sm).background(AppTheme.colors.primaryBg)
                    .padding(horizontal = AppSpace.s8, vertical = 2.dp)
            )
            Spacer(Modifier.height(6.dp))
        }
        Row {
            Text("Q.", style = AppType.headline, fontWeight = FontWeight.Black, color = AppTheme.colors.primary)
            Spacer(Modifier.width(6.dp))
            Text(
                q.question, style = AppType.headline, fontWeight = FontWeight.ExtraBold,
                color = AppTheme.colors.text, modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            // 강조 표식(**)은 떼고 — 문자엔 평문으로 나간다.
            com.detailline.callfollowcrm.domain.faq.RepQuestionRules.stripEmphasis(q.answer),
            style = AppType.body, color = AppTheme.colors.textSub,
            maxLines = 2, overflow = TextOverflow.Ellipsis
        )
        if (q.imageUri != null) {
            Spacer(Modifier.height(AppSpace.s8))
            Row(
                Modifier.clip(AppShape.sm).background(AppTheme.colors.primaryBg)
                    .padding(horizontal = AppSpace.s8, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Image, null, tint = AppTheme.colors.primaryText, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(AppSpace.s4))
                Text("사진 첨부", style = AppType.caption, color = AppTheme.colors.primaryText)
            }
        }
    }
}

/** 분류 거르기 칩(알약). 고른 칩은 파랗게. 「전체」 + 등록된 분류들. */
@Composable
private fun CatFilterChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(CircleShape)
            .background(if (selected) AppTheme.colors.primary else AppTheme.colors.surface)
            .border(1.dp, if (selected) AppTheme.colors.primary else AppTheme.colors.line, CircleShape)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text, style = AppType.label,
            color = if (selected) AppTheme.colors.textOnPrimary else AppTheme.colors.textSub
        )
    }
}

/** 프로토 .addrow — 점선 테두리 「＋ 대표질문 추가」. 누르면 등록 폼으로. */
@Composable
private fun AddRow(onClick: () -> Unit) {
    val lineColor = AppTheme.colors.line
    Row(
        Modifier.fillMaxWidth().clip(AppShape.lg)
            .drawBehind {
                val w = 1.5.dp.toPx()
                drawRoundRect(
                    color = lineColor,
                    topLeft = androidx.compose.ui.geometry.Offset(w / 2, w / 2),
                    size = androidx.compose.ui.geometry.Size(size.width - w, size.height - w),
                    cornerRadius = CornerRadius(16.dp.toPx()),
                    style = Stroke(width = w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())))
                )
            }
            .clickable { onClick() }
            .padding(13.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("＋ 대표질문 추가", style = AppType.label, color = AppTheme.colors.primaryText)
    }
}
