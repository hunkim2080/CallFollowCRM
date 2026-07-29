package com.detailline.callfollowcrm.presentation.screen.pricing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.detailline.callfollowcrm.data.AppContainer
import com.detailline.callfollowcrm.data.repository.PricingItemRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 문자 기반 가격 추출(2026-07-02) — 사장님이 보낸 견적 문자를 읽어 가격표를 자동으로 채우는 흐름.
 *
 * 단계(사장님 요청 그대로): 문자 싹 읽기 → ①"이런 항목 파시는 거 맞아요?"(이름 확인/수정/삭제)
 *   → ②"제가 분석한 가격 봐주세요"(가격+기준 확인/수정) → 저장.
 * 서버: docs/SERVER_HANDOFF_extract_pricing.md (POST /extract-pricing). 없으면 ERROR 로 친절 안내.
 * 프라이버시: 본인이 '보낸' 문자만 전송. 동의(toneUploadConsented) 후에만 서버 호출.
 */
class PricingExtractViewModel(private val container: AppContainer) : ViewModel() {

    enum class Phase { LOADING, CONSENT, ITEMS, PRICES, EMPTY, ERROR, DONE }

    data class Row(
        val key: Long,
        val title: String,
        val priceWon: Long,
        val unit: String,
        val category: String,
        val basisText: String
    )

    data class UiState(
        val phase: Phase = Phase.LOADING,
        val rows: List<Row> = emptyList(),
        val message: String = "",
        val candidateCount: Int = 0,
        val savedCount: Int = 0
    )

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    init { start() }

    fun start() {
        if (!container.preferences.toneUploadConsented) {
            _ui.value = UiState(phase = Phase.CONSENT)
        } else {
            runExtraction()
        }
    }

    fun agreeAndStart() {
        container.preferences.toneUploadConsented = true
        runExtraction()
    }

    private fun runExtraction() {
        _ui.value = UiState(phase = Phase.LOADING, message = "보낸 문자에서 견적을 찾는 중…")
        viewModelScope.launch {
            val candidates = withContext(Dispatchers.IO) {
                container.smsRepository.querySentPricingCandidates()
            }
            if (candidates.isEmpty()) {
                _ui.value = UiState(
                    phase = Phase.EMPTY,
                    message = "최근 보낸 문자에서 견적으로 보이는 내용을 못 찾았어요.\n가격표에 직접 추가해 주세요."
                )
                return@launch
            }
            _ui.value = _ui.value.copy(message = "AI가 가격을 분석하는 중…", candidateCount = candidates.size)
            val trade = container.preferences.ownerTrades.firstOrNull()
            container.pricingExtractRepository.extract(
                deviceId = container.preferences.deviceId,
                ownerTrade = trade,
                candidates = candidates
            ).onSuccess { items ->
                if (items.isEmpty()) {
                    _ui.value = UiState(
                        phase = Phase.EMPTY,
                        message = "분석했지만 자동으로 뽑을 만한 견적 항목이 없었어요.\n가격표에 직접 추가해 주세요."
                    )
                } else {
                    _ui.value = UiState(
                        phase = Phase.ITEMS,
                        candidateCount = candidates.size,
                        rows = items.mapIndexed { i, it ->
                            Row(
                                key = i.toLong(),
                                title = it.title,
                                priceWon = it.priceWon,
                                unit = it.unit,
                                category = it.category,
                                basisText = it.basisText.orEmpty()
                            )
                        }
                    )
                }
            }.onFailure {
                _ui.value = UiState(
                    phase = Phase.ERROR,
                    message = "지금은 자동 분석이 잠시 어려워요.\n잠시 후 다시 시도하거나 가격표에 직접 추가해 주세요."
                )
            }
        }
    }

    // ── 1단계: 항목 확인 ──
    fun editTitle(key: Long, title: String) = mutate(key) { it.copy(title = title) }

    fun removeRow(key: Long) {
        _ui.value = _ui.value.copy(rows = _ui.value.rows.filterNot { it.key == key })
    }

    fun goToPrices() {
        val kept = _ui.value.rows.filter { it.title.isNotBlank() }
        if (kept.isEmpty()) return
        _ui.value = _ui.value.copy(phase = Phase.PRICES, rows = kept)
    }

    fun backToItems() { _ui.value = _ui.value.copy(phase = Phase.ITEMS) }

    // ── 2단계: 가격+기준 확인 ──
    fun editPrice(key: Long, won: Long) = mutate(key) { it.copy(priceWon = won) }
    fun editBasis(key: Long, basis: String) = mutate(key) { it.copy(basisText = basis) }

    fun save() {
        val rows = _ui.value.rows.filter { it.title.isNotBlank() && it.priceWon > 0 }
        if (rows.isEmpty()) return
        viewModelScope.launch {
            container.pricingItemRepository.upsertEstimated(
                rows.map {
                    PricingItemRepository.ExtractedPricing(
                        title = it.title,
                        priceWon = it.priceWon,
                        unit = it.unit,
                        category = it.category,
                        basisText = it.basisText.ifBlank { null }
                    )
                }
            )
            _ui.value = _ui.value.copy(phase = Phase.DONE, savedCount = rows.size)
        }
    }

    private fun mutate(key: Long, f: (Row) -> Row) {
        _ui.value = _ui.value.copy(rows = _ui.value.rows.map { if (it.key == key) f(it) else it })
    }
}
