package com.detailline.callfollowcrm.presentation.screen.template

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.detailline.callfollowcrm.data.AppContainer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TemplateListViewModel(private val container: AppContainer) : ViewModel() {

    val templates = container.messageTemplateRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun toggleActive(id: Long, active: Boolean) = viewModelScope.launch {
        val t = container.messageTemplateRepository.findById(id) ?: return@launch
        container.messageTemplateRepository.update(t.copy(isActive = active))
    }

    /** 이름 수정 (프로토 editTemplateName). */
    fun rename(id: Long, name: String) = viewModelScope.launch {
        val t = container.messageTemplateRepository.findById(id) ?: return@launch
        container.messageTemplateRepository.update(t.copy(title = name.trim()))
    }

    fun delete(id: Long) = viewModelScope.launch {
        container.messageTemplateRepository.deleteById(id)
    }
}
