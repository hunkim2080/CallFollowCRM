package com.detailline.callfollowcrm.presentation.screen.stylelearning

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.detailline.callfollowcrm.data.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class StyleLearningUiState(
    val periodMonths: Int = 1,
    val progress: Int = 0,
    val sampleCount: Int = 0,
    val kindness: Int = 0,
    val avgLength: Int = 0,
    val emojiPerMessage: Double = 0.0,
    val loading: Boolean = false,
    val enabled: Boolean = true,
    val examplesCount: Int = 0,
    val signature: String = "",
    val analyzed: Boolean = false,
    val toast: String? = null
)

class StyleLearningViewModel(
    private val container: AppContainer
) : ViewModel() {
    private val _state = MutableStateFlow(
        StyleLearningUiState(
            enabled = container.preferences.toneLearnEnabled,
            examplesCount = container.preferences.toneExamples.size,
            signature = container.preferences.toneSignature
        )
    )
    val state = _state.asStateFlow()

    fun selectPeriod(months: Int) { _state.value = _state.value.copy(periodMonths = months) }
    fun consumeToast() { _state.value = _state.value.copy(toast = null) }

    fun setEnabled(on: Boolean) {
        container.preferences.toneLearnEnabled = on
        _state.value = _state.value.copy(
            enabled = on,
            toast = if (on) "말투 학습을 켰어요 ✨" else "말투 학습을 껐어요"
        )
    }

    fun addExample(text: String) {
        val t = text.trim()
        if (t.isBlank()) return
        container.preferences.toneExamples = container.preferences.toneExamples + t
        _state.value = _state.value.copy(
            examplesCount = container.preferences.toneExamples.size,
            toast = "새 예문을 배웠어요! ✨"
        )
    }

    fun setSignature(text: String) {
        container.preferences.toneSignature = text.trim()
        _state.value = _state.value.copy(signature = text.trim(), toast = "시그니처 인사를 저장했어요")
    }

    fun learnFromSamples() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, progress = 25, toast = null)
            val samples = listOf(
                "안녕하세요 문의주셔서 감사합니다.",
                "현장 사진 보내주시면 정확 견적 드릴게요.",
                "이번 주 목요일 오후 가능합니다.",
                "계약금 30% 먼저 부탁드려요.",
                "AS는 바로 일정 잡아 처리해드릴게요."
            )
            _state.value = _state.value.copy(progress = 70)
            val result = container.phaseOneApiRepository.learnStyle(samples)
            _state.value = result.fold(
                onSuccess = {
                    _state.value.copy(
                        loading = false,
                        progress = 100,
                        analyzed = true,
                        sampleCount = it.sampleCount,
                        kindness = it.kindness,
                        avgLength = it.avgLength,
                        emojiPerMessage = it.emojiPerMessage,
                        toast = "학습 완료"
                    )
                },
                onFailure = {
                    _state.value.copy(loading = false, progress = 0, toast = "학습 실패")
                }
            )
        }
    }
}
