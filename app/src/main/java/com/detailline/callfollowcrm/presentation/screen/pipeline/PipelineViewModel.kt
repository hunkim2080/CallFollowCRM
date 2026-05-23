package com.detailline.callfollowcrm.presentation.screen.pipeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.detailline.callfollowcrm.data.AppContainer
import com.detailline.callfollowcrm.data.local.entity.CustomerEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class PipelineViewModel(
    container: AppContainer,
    statusLabel: String
) : ViewModel() {

    val customers: StateFlow<List<CustomerEntity>> =
        // CustomerDao.observeByStatus 가 status 라벨 String 으로 직접 조회.
        container.customerRepository.observeByStatusLabel(statusLabel)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
