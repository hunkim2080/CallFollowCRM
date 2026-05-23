package com.detailline.callfollowcrm.presentation.screen.aimessage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.detailline.callfollowcrm.data.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AiMessageUiState(
    val customerName: String = "고객",
    val customerPhone: String = "",
    val customerMessage: String = "",
    val intents: List<String> = emptyList(),
    val replies: List<String> = emptyList(),
    val selectedReply: String = "",
    val priceCandidates: List<String> = emptyList(),
    val dateCandidates: List<String> = emptyList(),
    val selectedPrice: String = "",
    val selectedDate: String = "",
    val loading: Boolean = false,
    val error: String? = null
)

class AiMessageViewModel(
    private val container: AppContainer
) : ViewModel() {
    private val _state = MutableStateFlow(AiMessageUiState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch { container.phaseOneApiRepository.warmup() }
    }

    fun onMessageChange(value: String) {
        _state.value = _state.value.copy(customerMessage = value)
    }
    fun onPhoneChange(value: String) { _state.value = _state.value.copy(customerPhone = value) }

    fun loadSuggestions() {
        val message = _state.value.customerMessage.trim()
        if (message.isBlank()) return
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            val result = container.phaseOneApiRepository.suggest(message)
            _state.value = result.fold(
                onSuccess = { res ->
                    _state.value.copy(
                        loading = false,
                        intents = res.intentLabels,
                        replies = res.replies.map { it.text },
                        selectedReply = res.replies.firstOrNull()?.text.orEmpty(),
                        priceCandidates = res.priceCandidates,
                        dateCandidates = res.dateCandidates
                    )
                },
                onFailure = {
                    _state.value.copy(loading = false, error = "답변 생성 실패")
                }
            )
        }
    }

    fun selectReply(value: String) { _state.value = _state.value.copy(selectedReply = value) }
    fun selectPrice(value: String) { _state.value = _state.value.copy(selectedPrice = value) }
    fun selectDate(value: String) { _state.value = _state.value.copy(selectedDate = value) }
    fun onReplyEdit(value: String) { _state.value = _state.value.copy(selectedReply = value) }
}
