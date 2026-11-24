package com.detailline.callfollowcrm.presentation.screen.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.detailline.callfollowcrm.data.local.entity.MessageTemplateEntity
import com.detailline.callfollowcrm.domain.model.TemplateCategory
import com.detailline.callfollowcrm.presentation.component.TossChip
import com.detailline.callfollowcrm.presentation.theme.AppSpace

// 승인된 문구 선택창 순서. 문구가 없는 분류는 표시하지 않는다.
private val templateCategoryOrder = listOf(
    TemplateCategory.ESTIMATE,
    TemplateCategory.RESERVATION,
    TemplateCategory.PHOTO_REQUEST,
    TemplateCategory.PRE_WORK,
    TemplateCategory.MISSED_CALL,
    TemplateCategory.CUSTOM
)

@Composable
internal fun TemplateCategoryChips(
    templates: List<MessageTemplateEntity>,
    selectedCategory: String,
    onSelect: (String) -> Unit
) {
    val categories = remember(templates) {
        val populated = templates.map { it.category }.toSet()
        listOf("") + templateCategoryOrder.map { it.name }.filter { it in populated }
    }
    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(vertical = AppSpace.s12),
        horizontalArrangement = Arrangement.spacedBy(AppSpace.s8)
    ) {
        items(categories, key = { it }) { category ->
            TossChip(
                text = if (category.isEmpty()) "전체" else categoryLabel(category),
                selected = selectedCategory == category || (category.isEmpty() && selectedCategory.isBlank()),
                onClick = { onSelect(category) },
                filled = true
            )
        }
    }
}
