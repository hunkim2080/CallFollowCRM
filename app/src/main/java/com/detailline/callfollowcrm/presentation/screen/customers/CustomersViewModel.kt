package com.detailline.callfollowcrm.presentation.screen.customers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.detailline.callfollowcrm.data.AppContainer
import com.detailline.callfollowcrm.data.local.entity.CustomerEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * 고객 관리 목록 (2026-06-01 전면 리뉴얼, 프로토 s-customers) — 전체 고객을 한눈에.
 *   이름 있는 고객 먼저(가나다), 그다음 번호만 있는 고객을 최근 순. DB 변경 없음.
 */
class CustomersViewModel(container: AppContainer) : ViewModel() {

    val customers: StateFlow<List<CustomerEntity>> =
        container.customerRepository.observeAll()
            .map { list ->
                list.sortedWith(
                    compareBy<CustomerEntity> { it.name.isNullOrBlank() }
                        .thenBy { it.name?.takeIf { n -> n.isNotBlank() } ?: "" }
                        .thenByDescending { it.createdAt }
                )
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
