package com.detailline.callfollowcrm.presentation.screen.estimate

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.detailline.callfollowcrm.data.AppContainer
import com.detailline.callfollowcrm.data.local.entity.RecurringLogEntity
import com.detailline.callfollowcrm.domain.estimate.EstimateFollowupCalc
import com.detailline.callfollowcrm.domain.estimate.EstimateFollowupItem
import com.detailline.callfollowcrm.util.DateTimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 화면 표시용 — 리마인드 한 건 + 미리 렌더된 문구. */
data class FollowupRow(val item: EstimateFollowupItem, val body: String)

/** 견적 회신 리마인드 (2026-06-01). 자동발송 X — 확인 후 발송. */
class EstimateFollowupViewModel(private val container: AppContainer) : ViewModel() {

    private val bizName = container.preferences.bizName

    val rows: StateFlow<List<FollowupRow>> = combine(
        container.messageHistoryRepository.observeEstimateSends(),
        container.customerRepository.observeAll(),
        container.recurringMessageRepository.observeLogs()
    ) { sends, customers, logs ->
        val todayStart = DateTimeUtils.startOfDay(System.currentTimeMillis())  // 매번 현재 기준 (stale-day fix)
        val estDays = sends.filter { it.customerId != null }
            .groupBy { it.customerId!! }
            .mapValues { (_, list) -> DateTimeUtils.startOfDay(list.maxOf { it.createdAt }) }
        val keys = logs.map { Triple(it.ruleId, it.customerId, it.occurrenceDayStartMs) }.toSet()
        EstimateFollowupCalc.compute(estDays, customers, keys, todayStart)
            .map { FollowupRow(it, renderBody(it)) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private fun renderBody(item: EstimateFollowupItem): String {
        val name = item.customerName?.takeIf { it.isNotBlank() } ?: "고객"
        val biz = bizName.takeIf { it.isNotBlank() }?.let { " $it" } ?: ""
        return "${name}님, 안녕하세요$biz 😊 지난번 보내드린 견적 잘 보셨어요? 궁금한 점이나 조정할 부분 있으시면 편하게 말씀 주세요!"
    }

    fun prepareSend(row: FollowupRow) {
        container.chatDraftStore.set(row.item.phone, row.body)
        log(row, RecurringLogEntity.STATUS_SENT)
    }

    fun dismiss(row: FollowupRow) = log(row, RecurringLogEntity.STATUS_DISMISSED)

    private fun log(row: FollowupRow, status: String) = viewModelScope.launch {
        withContext(Dispatchers.IO + NonCancellable) {
            container.recurringMessageRepository.logOccurrence(
                ruleId = EstimateFollowupCalc.RULE_ESTIMATE,
                customerId = row.item.customerId,
                occurrenceDayStartMs = row.item.estimateDayStartMs,
                status = status
            )
        }
    }
}
