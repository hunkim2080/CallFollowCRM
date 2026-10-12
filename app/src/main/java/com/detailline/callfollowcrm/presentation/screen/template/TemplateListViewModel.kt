package com.detailline.callfollowcrm.presentation.screen.template

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.detailline.callfollowcrm.data.AppContainer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TemplateListViewModel(private val container: AppContainer) : ViewModel() {

    val templates = container.messageTemplateRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 문구ID → 첫 사진 URI. 채팅 피커와 **같은 공용 셈** — 관리 화면에도 등록한 사진 썸네일을 띄운다.
     *   (2026-10-12 사장님: 채팅엔 보이는데 이 화면엔 안 보이던 것) */
    val photoByTemplate: StateFlow<Map<Long, String>> =
        container.templateAttachmentRepository.observeFirstImageUriByTemplate()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

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
