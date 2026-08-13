package com.detailline.callfollowcrm.presentation.screen.recurring

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.detailline.callfollowcrm.data.AppContainer
import com.detailline.callfollowcrm.data.local.entity.RecurringLogEntity
import com.detailline.callfollowcrm.domain.recurring.DueItem
import com.detailline.callfollowcrm.domain.recurring.RecurringDueCalc
import com.detailline.callfollowcrm.util.DateTimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** "정기문자 보낼 때 됐어요" 목록 (2026-06-01). 자동발송 X — 사장님 확인 후 보냄/넘김. */
class RecurringDueViewModel(private val container: AppContainer) : ViewModel() {

    val dueItems: StateFlow<List<DueItem>> = combine(
        container.recurringMessageRepository.observeEnabledRules(),
        container.customerRepository.observeAll(),
        container.recurringMessageRepository.observeLogs()
    ) { rules, customers, logs ->
        val todayStart = DateTimeUtils.startOfDay(System.currentTimeMillis())  // 매번 현재 기준 (stale-day fix)
        val keys = logs.map { Triple(it.ruleId, it.customerId, it.occurrenceDayStartMs) }.toSet()
        RecurringDueCalc.computeDue(rules, customers, keys, todayStart)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * 보내기 — 채팅 composer 에 본문 prefill(ChatDraftStore) + SENT 로그(목록에서 제외).
     *   실제 발송은 채팅에서 사장님이 ▶ (정책: 자동발송 X). 화면이 이어서 onOpenChat 호출.
     */
    fun prepareSend(item: DueItem) {
        container.chatDraftStore.set(item.phone, item.renderedBody)
        viewModelScope.launch(Dispatchers.IO + NonCancellable) {
            container.recurringMessageRepository.logOccurrence(
                item.ruleId, item.customerId, item.occurrenceDayStartMs, RecurringLogEntity.STATUS_SENT
            )
        }
    }

    /** 넘기기 — DISMISSED 로그 → 이번 회차는 목록에서 제외(다음 회차 오면 다시 노출). */
    fun dismiss(item: DueItem) = viewModelScope.launch {
        withContext(Dispatchers.IO + NonCancellable) {
            container.recurringMessageRepository.logOccurrence(
                item.ruleId, item.customerId, item.occurrenceDayStartMs, RecurringLogEntity.STATUS_DISMISSED
            )
        }
    }
}
