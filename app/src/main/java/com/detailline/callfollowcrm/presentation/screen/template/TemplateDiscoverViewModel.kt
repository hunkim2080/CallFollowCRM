package com.detailline.callfollowcrm.presentation.screen.template

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.detailline.callfollowcrm.data.AppContainer
import com.detailline.callfollowcrm.data.local.entity.MessageTemplateEntity
import com.detailline.callfollowcrm.domain.model.TemplateCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 문자 템플릿 자동 발굴(2026-07-02 사장님) — 사장님이 반복해서 보낸 문자를 찾아 "템플릿으로 저장" 제안.
 *   서버 불필요. SmsRepository.queryFrequentSentTemplates() 로 폰에서 빈도 집계 → 이미 저장된 건 제외.
 *   가격추출과 같은 '문자 읽기' 파이프라인 재활용. [[project_price_extract_from_sms]] 스핀오프.
 */
class TemplateDiscoverViewModel(private val container: AppContainer) : ViewModel() {

    enum class Phase { LOADING, LIST, EMPTY }

    data class Row(val key: Long, val body: String, val count: Int, val saved: Boolean = false)

    data class UiState(val phase: Phase = Phase.LOADING, val rows: List<Row> = emptyList())

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    init { load() }

    fun load() {
        _ui.value = UiState(Phase.LOADING)
        viewModelScope.launch {
            val existing = runCatching { container.messageTemplateRepository.observeAll().first() }
                .getOrDefault(emptyList())
            val existingKeys = existing.map { normKey(it.body) }.toHashSet()
            val cands = withContext(Dispatchers.IO) {
                container.smsRepository.queryFrequentSentTemplates()
            }.filter { normKey(it.body) !in existingKeys }   // 이미 템플릿으로 있는 건 안 보여줌
            _ui.value = if (cands.isEmpty()) UiState(Phase.EMPTY)
            else UiState(Phase.LIST, cands.mapIndexed { i, c -> Row(i.toLong(), c.body, c.count) })
        }
    }

    fun save(key: Long) {
        val row = _ui.value.rows.firstOrNull { it.key == key } ?: return
        if (row.saved) return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            container.messageTemplateRepository.insert(
                MessageTemplateEntity(
                    title = autoTitle(row.body),
                    body = row.body,
                    category = TemplateCategory.CUSTOM.name,
                    isDefault = false,
                    isActive = true,
                    createdAt = now,
                    updatedAt = now
                )
            )
            _ui.value = _ui.value.copy(
                rows = _ui.value.rows.map { if (it.key == key) it.copy(saved = true) else it }
            )
        }
    }

    private fun normKey(body: String) = body.trim().replace(Regex("\\s+"), " ").lowercase()

    /** 제목 = 한글이 충분히 든 첫 줄(날짜 줄 건너뜀)에서 14자. 사장님이 목록에서 언제든 이름 수정 가능. */
    private fun autoTitle(body: String): String {
        val line = body.trim().lineSequence().map { it.trim() }
            .firstOrNull { l -> l.count { ch -> ch in '가'..'힣' } >= 6 }
            ?: body.trim().replace(Regex("\\s+"), " ")
        return line.take(14).ifBlank { "자주 쓰는 문자" }
    }
}
