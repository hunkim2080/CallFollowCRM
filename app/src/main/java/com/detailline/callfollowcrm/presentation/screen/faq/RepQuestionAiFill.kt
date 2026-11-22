package com.detailline.callfollowcrm.presentation.screen.faq

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.detailline.callfollowcrm.presentation.component.AppSheet
import com.detailline.callfollowcrm.presentation.component.SheetTextField
import com.detailline.callfollowcrm.presentation.component.TossBadge
import com.detailline.callfollowcrm.presentation.component.TossPrimaryButton
import com.detailline.callfollowcrm.presentation.theme.AppShape
import com.detailline.callfollowcrm.presentation.theme.AppSpace
import com.detailline.callfollowcrm.presentation.theme.AppTheme
import com.detailline.callfollowcrm.presentation.theme.AppType
import com.detailline.callfollowcrm.presentation.theme.ShimmerLine

/**
 * ✨ **AI로 대표질문 채우기.** (2026-11-22 사장님 「가」 · 프로토 FPXyBo3f3ePwzPHaM35BSn)
 *
 *   관리 화면 진입 카드([AiFillEntryCard]) → 시트([AiFillSheet]): "문자 읽는 중" 스켈레톤 → AI 초안 체크리스트
 *   (분류·질문·답, 가격/날짜엔 「확인 필요」 딱지) → 골라 고치고 「N개 등록하기」.
 *   셈·네트워크는 [RepQuestionListViewModel]/`FaqPublishRepository.suggest` 한 곳(§12) — 여긴 그림만.
 */
@Composable
fun AiFillEntryCard(onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .clip(AppShape.lg)
            .background(AppTheme.colors.primaryBg)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(34.dp).clip(CircleShape).background(AppTheme.colors.primary),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.AutoAwesome, null, tint = AppTheme.colors.textOnPrimary, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("AI로 대표질문 채우기", style = AppType.headline, fontWeight = FontWeight.Bold, color = AppTheme.colors.text)
            Text("문자를 읽고 자주 묻는 질문·답을 뽑아줘요", style = AppType.caption, color = AppTheme.colors.primaryText)
        }
        Text("›", style = AppType.title, color = AppTheme.colors.primaryText)
    }
}

@Composable
fun AiFillSheet(viewModel: RepQuestionListViewModel) {
    val open by viewModel.aiOpen.collectAsState()
    // early return 금지(compose_guard) — 조건부 emit.
    if (open) AiFillSheetBody(viewModel)
}

@Composable
private fun AiFillSheetBody(viewModel: RepQuestionListViewModel) {
    val loading by viewModel.aiLoading.collectAsState()
    val saving by viewModel.aiSaving.collectAsState()
    val drafts by viewModel.aiDrafts.collectAsState()
    val selectedCount = drafts.count { it.selected && it.question.isNotBlank() }

    AppSheet(onDismiss = { if (!saving) viewModel.closeAi() }) {
        Text("AI로 대표질문 채우기", style = AppType.headline, fontWeight = FontWeight.ExtraBold, color = AppTheme.colors.text)
        Spacer(Modifier.height(AppSpace.s8))
        // 경고 — 숨기지 않고 항상(가격·날짜 틀리면 손님에게 바로 나간다 §13).
        Box(
            Modifier.fillMaxWidth().clip(AppShape.md).background(AppTheme.colors.cautionBg)
                .padding(horizontal = 12.dp, vertical = 9.dp)
        ) {
            Text("FAQ 공개 문구로 저장돼요 — 가격·날짜는 꼭 확인하고 등록하세요", style = AppType.caption, color = AppTheme.colors.cautionText)
        }
        Spacer(Modifier.height(AppSpace.s12))

        if (loading) {
            Text("문자 읽는 중…", style = AppType.body, color = AppTheme.colors.textSub)
            Spacer(Modifier.height(AppSpace.s12))
            repeat(3) {
                Column(
                    Modifier.fillMaxWidth().clip(AppShape.lg).border(1.dp, AppTheme.colors.line, AppShape.lg)
                        .padding(14.dp)
                ) {
                    ShimmerLine(Modifier.fillMaxWidth(0.5f), height = 16.dp)
                    Spacer(Modifier.height(10.dp))
                    ShimmerLine(Modifier.fillMaxWidth(), height = 12.dp)
                    Spacer(Modifier.height(6.dp))
                    ShimmerLine(Modifier.fillMaxWidth(0.8f), height = 12.dp)
                }
                Spacer(Modifier.height(AppSpace.s12))
            }
        } else {
            drafts.forEachIndexed { i, d ->
                DraftRow(
                    d = d,
                    onToggle = { viewModel.toggleDraft(i) },
                    onQuestion = { viewModel.editDraftQuestion(i, it) },
                    onAnswer = { viewModel.editDraftAnswer(i, it) }
                )
                Spacer(Modifier.height(AppSpace.s12))
            }
            TossPrimaryButton(
                text = if (selectedCount > 0) "${selectedCount}개 등록하기" else "등록할 질문을 골라주세요",
                onClick = viewModel::registerSelectedDrafts,
                enabled = selectedCount > 0 && !saving
            )
        }
    }
}

@Composable
private fun DraftRow(
    d: RepQuestionListViewModel.AiDraft,
    onToggle: () -> Unit,
    onQuestion: (String) -> Unit,
    onAnswer: (String) -> Unit
) {
    Column(
        Modifier.fillMaxWidth().clip(AppShape.lg)
            .border(
                1.dp,
                if (d.selected) AppTheme.colors.primary else AppTheme.colors.line,
                AppShape.lg
            )
            .background(AppTheme.colors.surface)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 체크 동그라미 — 누르면 선택/해제(44dp 터치). 고른 것만 등록.
            Box(
                Modifier.size(44.dp).clickable(
                    interactionSource = remember { MutableInteractionSource() }, indication = null
                ) { onToggle() },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier.size(24.dp).clip(CircleShape)
                        .background(if (d.selected) AppTheme.colors.primary else AppTheme.colors.neutralBg)
                        .border(
                            1.5.dp,
                            if (d.selected) AppTheme.colors.primary else AppTheme.colors.neutral,
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (d.selected) Icon(Icons.Default.Check, null, tint = AppTheme.colors.textOnPrimary, modifier = Modifier.size(15.dp))
                }
            }
            Spacer(Modifier.width(8.dp))
            if (d.category.isNotBlank()) {
                TossBadge(d.category)
                Spacer(Modifier.width(6.dp))
            }
            Spacer(Modifier.weight(1f))
            // 가격·날짜 들어간 답 — 꼭 확인하라는 딱지.
            if (d.needsReview) {
                TossBadge("확인 필요", color = AppTheme.colors.cautionText, background = AppTheme.colors.cautionBg)
            }
        }
        // 선택 안 한 줄은 흐리게 — 등록 안 됨이 눈에 보이게.
        Column(Modifier.alpha(if (d.selected) 1f else 0.45f)) {
            Spacer(Modifier.height(10.dp))
            Text("질문", style = AppType.caption, fontWeight = FontWeight.Bold, color = AppTheme.colors.textHint)
            Spacer(Modifier.height(4.dp))
            SheetTextField(value = d.question, onValueChange = onQuestion, placeholder = "질문", singleLine = true)
            Spacer(Modifier.height(10.dp))
            Text("답", style = AppType.caption, fontWeight = FontWeight.Bold, color = AppTheme.colors.textHint)
            Spacer(Modifier.height(4.dp))
            SheetTextField(
                value = d.answer,
                onValueChange = onAnswer,
                placeholder = "답을 적어주세요",
                singleLine = false,
                minHeightDp = 56
            )
        }
    }
}
