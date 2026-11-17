package com.detailline.callfollowcrm.presentation.screen.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.detailline.callfollowcrm.data.local.entity.PricingItemEntity
import com.detailline.callfollowcrm.domain.search.HangulSearch
import com.detailline.callfollowcrm.presentation.theme.AppShape
import com.detailline.callfollowcrm.presentation.theme.AppSpace
import com.detailline.callfollowcrm.presentation.theme.AppType
import com.detailline.callfollowcrm.presentation.theme.TossBlue
import com.detailline.callfollowcrm.presentation.theme.TossDivider
import com.detailline.callfollowcrm.presentation.theme.TossGrayBg
import com.detailline.callfollowcrm.presentation.theme.TossTextPrimary
import com.detailline.callfollowcrm.presentation.theme.TossTextTertiary

/**
 * 🔍 견적 만들기 — 시공 항목 **검색(초성·글자) + 고른 항목 맨 위 고정.**
 * 프로토 estimate-item-search '검색(맨 위 고정)' 그대로. (2026-11-17 사장님)
 *
 * 왜: 가격표가 길면 견적 줄 때 항목을 일일이 찾기 힘들어 '만나서 정하기'만 쓰게 됨 → "ㅅ"만 쳐도 '실리콘 시공'이.
 * - 빈 검색 = 전체 목록 그대로(지금과 동일).
 * - 검색하면 '✔ 고른 항목'을 맨 위에 고정(검색어와 무관하게 안 사라짐) + '검색 결과' 아래.
 * - 맞춤 셈은 공용 [HangulSearch](domain/search, 단위시험 통과) — 화면에서 초성 변환 새로 안 짠다.
 * - 줄 틀은 [EstimateItemRow] 한 곳(전체·고정·결과 세 자리가 같은 줄). 순서바꾸기는 [EstOrderList].
 */
@Composable
internal fun EstItemSection(
    items: List<PricingItemEntity>,
    selectedQty: MutableMap<Long, Int>,
    orderMode: Boolean,
    onReorder: (List<Long>) -> Unit,
    onEditPrice: (PricingItemEntity, Int) -> Unit,
    onEditTitle: (PricingItemEntity, String) -> Unit
) {
    var itemQuery by remember { mutableStateOf("") }

    // 한 줄 렌더(§12 중복 방지) — 전체목록·고정·검색결과 세 자리가 같은 줄 틀을 쓴다.
    val renderRows: @Composable (List<PricingItemEntity>) -> Unit = { rows ->
        Column(Modifier.fillMaxWidth()) {
            rows.forEachIndexed { idx, item ->
                if (idx > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(TossDivider))
                val isPyeong = item.unit == PricingItemEntity.UNIT_PYEONG
                EstimateItemRow(
                    title = item.title,
                    price = item.price,
                    unit = item.unit,
                    quantity = selectedQty[item.id] ?: 0,
                    onToggle = {
                        val cur = selectedQty[item.id] ?: 0
                        if (cur > 0) selectedQty.remove(item.id)
                        else selectedQty[item.id] = if (isPyeong) 24 else 1
                    },
                    onIncrement = { selectedQty[item.id] = (selectedQty[item.id] ?: 0) + 1 },
                    onDecrement = {
                        val cur = selectedQty[item.id] ?: 0
                        if (cur > 1) selectedQty[item.id] = cur - 1
                    },
                    onEditPrice = { manwon -> onEditPrice(item, manwon) },
                    onEditTitle = { newTitle -> onEditTitle(item, newTitle) }
                )
            }
        }
    }

    // 검색창 — 순서바꾸기 중엔 숨김(그때는 전체 목록을 끌어 옮긴다).
    if (!orderMode) {
        Row(
            Modifier.fillMaxWidth()
                .clip(AppShape.md)
                .background(TossGrayBg)
                .border(1.dp, TossDivider, AppShape.md)
                .padding(horizontal = AppSpace.s12, vertical = AppSpace.s12),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("🔍", style = AppType.headline)
            Spacer(Modifier.width(AppSpace.s8))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (itemQuery.isEmpty()) {
                    Text(
                        "항목 검색 (예: ㅅ · 실리콘)",
                        style = AppType.headline.copy(fontWeight = FontWeight.Normal, color = TossTextTertiary)
                    )
                }
                BasicTextField(
                    value = itemQuery,
                    onValueChange = { itemQuery = it },
                    singleLine = true,
                    textStyle = AppType.headline.copy(fontWeight = FontWeight.Normal, color = TossTextPrimary),
                    cursorBrush = SolidColor(TossBlue),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (itemQuery.isNotEmpty()) {
                Text(
                    "✕", style = AppType.headline, color = TossTextTertiary,
                    modifier = Modifier.clip(AppShape.sm)
                        .clickable { itemQuery = "" }
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
        Spacer(Modifier.height(AppSpace.s8))
    }

    val q = itemQuery.trim()
    when {
        orderMode -> EstOrderList(items = items, onDone = onReorder)
        q.isEmpty() -> renderRows(items)
        else -> {
            // 검색 중 — 고른 항목은 맨 위에 고정(검색어와 무관하게 안 사라짐), 그 아래 검색 결과.
            val selItems = items.filter { (selectedQty[it.id] ?: 0) > 0 }
            val pool = items.filter {
                HangulSearch.matches(it.title, q) && (selectedQty[it.id] ?: 0) <= 0
            }
            if (selItems.isNotEmpty()) {
                EstGroupLabel("✔ 고른 항목 (${selItems.size})", color = TossBlue)
                renderRows(selItems)
            }
            EstGroupLabel(if (pool.isEmpty()) "검색 결과 없음" else "검색 결과", color = TossTextTertiary)
            if (pool.isEmpty()) {
                Text(
                    "\"$q\" 에 맞는 항목이 없어요.\n아래 ＋ 직접 항목 추가 로 넣을 수 있어요.",
                    style = AppType.body, color = TossTextTertiary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = AppSpace.s24)
                )
            } else {
                renderRows(pool)
            }
        }
    }
}

/** 그룹 머리말 — '✔ 고른 항목' / '검색 결과'. 프로토 .grouplabel(작고 굵은 머리말). */
@Composable
private fun EstGroupLabel(text: String, color: Color) {
    Text(
        text, style = AppType.label, color = color,
        modifier = Modifier.padding(start = 2.dp, top = AppSpace.s12, bottom = 2.dp)
    )
}
