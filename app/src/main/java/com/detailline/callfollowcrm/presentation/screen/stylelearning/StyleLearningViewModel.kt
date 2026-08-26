package com.detailline.callfollowcrm.presentation.screen.stylelearning

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.detailline.callfollowcrm.data.AppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    val examples: List<String> = emptyList(),
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
            examples = container.preferences.toneExamples.sorted(),
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
            examples = container.preferences.toneExamples.sorted(),
            toast = "새 예문을 배웠어요! ✨"
        )
    }

    /** 가르친 예문 삭제. */
    fun removeExample(text: String) {
        container.preferences.toneExamples = container.preferences.toneExamples - text
        _state.value = _state.value.copy(
            examplesCount = container.preferences.toneExamples.size,
            examples = container.preferences.toneExamples.sorted(),
            toast = "예문을 지웠어요"
        )
    }

    /** 가르친 예문 수정(옛 문장 → 새 문장). 빈 문장이면 삭제. */
    fun updateExample(oldText: String, newText: String) {
        val t = newText.trim()
        if (t.isBlank()) { removeExample(oldText); return }
        if (t == oldText) return
        container.preferences.toneExamples = container.preferences.toneExamples - oldText + t
        _state.value = _state.value.copy(
            examplesCount = container.preferences.toneExamples.size,
            examples = container.preferences.toneExamples.sorted(),
            toast = "예문을 고쳤어요 ✨"
        )
    }

    fun setSignature(text: String) {
        container.preferences.toneSignature = text.trim()
        _state.value = _state.value.copy(signature = text.trim(), toast = "시그니처 인사를 저장했어요")
    }

    fun learnFromSamples() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, progress = 20, toast = null)
            // 실제 사장님이 "고객에게 보낸 문자"로 분석. (querySentMessagesWithTimestamp 는 고객 번호로 자동 필터)
            //   선택한 기간(최근 1/3/6개월) 안의 것만, 분석은 최대 300건.
            val months = _state.value.periodMonths
            val cutoff = System.currentTimeMillis() - months * 30L * 24 * 60 * 60 * 1000
            val samples = withContext(Dispatchers.IO) {
                runCatching {
                    container.smsRepository.querySentMessagesWithTimestamp(limit = 3000)
                        .filter { it.dateMs >= cutoff }
                        .map { it.body }
                        .take(300)
                }.getOrDefault(emptyList())
            }
            if (samples.isEmpty()) {
                _state.value = _state.value.copy(
                    loading = false, progress = 0,
                    toast = "분석할 고객 문자가 아직 없어요 (고객에게 보낸 문자가 쌓이면 배워요)"
                )
                return@launch
            }
            _state.value = _state.value.copy(progress = 60, sampleCount = samples.size)
            val result = container.phaseOneApiRepository.learnStyle(samples)
            _state.value = result.fold(
                onSuccess = {
                    _state.value.copy(
                        loading = false,
                        progress = 100,
                        analyzed = true,
                        // 실제 분석에 쓴 문자 수(서버 sampleCount 가 0이면 우리가 보낸 개수).
                        sampleCount = it.sampleCount.takeIf { n -> n > 0 } ?: samples.size,
                        kindness = it.kindness,
                        avgLength = it.avgLength,
                        emojiPerMessage = it.emojiPerMessage,
                        toast = "학습 완료 · ${samples.size}개 문자 분석"
                    )
                },
                onFailure = {
                    _state.value.copy(loading = false, progress = 0, toast = "학습 실패")
                }
            )
        }
    }
}
