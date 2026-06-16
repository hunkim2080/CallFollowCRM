package com.detailline.callfollowcrm.presentation.screen.principle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.detailline.callfollowcrm.data.AppContainer
import com.detailline.callfollowcrm.data.local.entity.PrincipleEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** "막내가 알아낸 사장님 원칙" 관리 (보기·켜고끄기·수정·삭제·직접 추가). (2026-06-17) */
class PrincipleManageViewModel(
    private val container: AppContainer
) : ViewModel() {

    val principles: StateFlow<List<PrincipleEntity>> =
        container.principleRepository.observeAll()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun add(text: String) = viewModelScope.launch {
        container.principleRepository.add(text, source = "manual")
    }

    fun updateText(id: Long, text: String) = viewModelScope.launch {
        container.principleRepository.updateText(id, text)
    }

    fun setEnabled(id: Long, enabled: Boolean) = viewModelScope.launch {
        container.principleRepository.setEnabled(id, enabled)
    }

    fun delete(id: Long) = viewModelScope.launch {
        container.principleRepository.delete(id)
    }
}
