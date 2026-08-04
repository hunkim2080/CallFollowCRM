package com.detailline.callfollowcrm.presentation.screen.pricing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.detailline.callfollowcrm.data.AppContainer
import com.detailline.callfollowcrm.data.local.entity.PricingItemEntity
import com.detailline.callfollowcrm.data.repository.PricingCategory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 설정 → 가격표 관리 화면 ViewModel.
 * 시드된 항목 + 사장님이 추가한 항목 모두 표시. isActive=false 도 표시 (편집 모드).
 */
class PricingItemsViewModel(private val container: AppContainer) : ViewModel() {

    val items = container.pricingItemRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun add(title: String, price: Long, unit: String, category: PricingCategory) {
        if (title.isBlank() || price <= 0) return
        viewModelScope.launch {
            container.pricingItemRepository.insert(
                title = title,
                price = price,
                category = category.name,
                displayOrder = (items.value.maxOfOrNull { it.displayOrder } ?: 0) + 1,
                unit = unit
            )
        }
    }

    fun update(item: PricingItemEntity, title: String, price: Long, unit: String, category: PricingCategory) {
        if (title.isBlank() || price <= 0) return
        viewModelScope.launch {
            container.pricingItemRepository.update(
                item.copy(title = title.trim(), price = price, unit = unit, category = category.name)
            )
        }
    }

    fun toggleActive(item: PricingItemEntity) {
        viewModelScope.launch {
            container.pricingItemRepository.setActive(item.id, !item.isActive)
        }
    }

    fun delete(item: PricingItemEntity) {
        viewModelScope.launch {
            container.pricingItemRepository.deleteById(item.id)
        }
    }

    /** 선택한 여러 항목을 한 번에 삭제. (2026-08-04 사장님 — 하나씩 삭제가 번거로움) */
    fun deleteMany(ids: List<Long>) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            container.pricingItemRepository.deleteByIds(ids)
        }
    }
}
