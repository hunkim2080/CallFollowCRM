package com.detailline.callfollowcrm.presentation.screen.reminder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.detailline.callfollowcrm.data.AppContainer
import com.detailline.callfollowcrm.data.local.entity.RecurringLogEntity
import com.detailline.callfollowcrm.domain.reminder.ReminderItem
import com.detailline.callfollowcrm.domain.reminder.ReminderKind
import com.detailline.callfollowcrm.domain.reminder.ScheduleReminderCalc
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
data class ReminderRow(val item: ReminderItem, val body: String)

/** 시공 D-1 / 도착 안내 리마인드 (2026-06-01). 자동발송 X — 확인 후 발송. */
class ScheduleReminderViewModel(private val container: AppContainer) : ViewModel() {

    private val todayStart = DateTimeUtils.startOfDay(System.currentTimeMillis())
    private val bizName = container.preferences.bizName

    val rows: StateFlow<List<ReminderRow>> = combine(
        container.customerRepository.observeAll(),
        container.recurringMessageRepository.observeLogs()
    ) { customers, logs ->
        val keys = logs.map { Triple(it.ruleId, it.customerId, it.occurrenceDayStartMs) }.toSet()
        // D-1 토글 OFF 면 이 리마인드 화면에도 D-1 안 띄움(토글 일관성). arrival 은 기존대로(여기선 미노출). (2026-06-18 사장님)
        // + 설정 시각(d1SendHour) 전엔 D-1 숨김 — 홈 카드와 동일 기준(자정 갑툭 방지). (2026-06-18 사장님)
        ScheduleReminderCalc.compute(
            customers, keys, todayStart,
            d1Enabled = container.preferences.d1AutoEnabled,
            d1TimeReached = java.util.Calendar.getInstance()
                .get(java.util.Calendar.HOUR_OF_DAY) >= container.preferences.d1SendHour
        ).map { ReminderRow(it, renderBody(it)) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private fun renderBody(item: ReminderItem): String {
        val name = item.customerName?.takeIf { it.isNotBlank() } ?: "고객"
        val biz = bizName.takeIf { it.isNotBlank() }?.let { " $it" } ?: ""
        val fallback = when (item.kind) {
            ReminderKind.D1 -> {
                val date = DateTimeUtils.formatKoreanDate(item.scheduledDayStartMs)
                "${name}님, 안녕하세요$biz 😊 내일 $date 시공 예정입니다. 시간 맞춰 방문드릴게요. 변동 있으시면 편히 말씀 주세요!"
            }
            ReminderKind.ARRIVAL ->
                "${name}님, 오늘 시공 위해 곧 출발합니다!$biz 도착 전 미리 연락드릴게요 😊"
        }
        val raw = when (item.kind) {
            ReminderKind.D1 -> container.preferences.d1AutoText
            ReminderKind.ARRIVAL -> container.preferences.arrivalAutoText
        }.ifBlank { fallback }
        return raw
            .replace("{고객명}", name)
            .replace("{이름}", name)
            .replace("{상호}", container.preferences.bizName)
            .replace("{시공일}", DateTimeUtils.formatKoreanDate(item.scheduledDayStartMs))
    }

    /** 보내기 — 채팅 composer prefill + SENT 로그(목록 제외). 화면이 이어서 onOpenChat 호출. */
    fun prepareSend(row: ReminderRow) {
        container.chatDraftStore.set(row.item.phone, row.body)
        log(row, RecurringLogEntity.STATUS_SENT)
    }

    /** 넘기기 — DISMISSED 로그. */
    fun dismiss(row: ReminderRow) = log(row, RecurringLogEntity.STATUS_DISMISSED)

    private fun log(row: ReminderRow, status: String) = viewModelScope.launch {
        withContext(Dispatchers.IO + NonCancellable) {
            container.recurringMessageRepository.logOccurrence(
                ruleId = ScheduleReminderCalc.ruleIdOf(row.item.kind),
                customerId = row.item.customerId,
                occurrenceDayStartMs = row.item.scheduledDayStartMs,
                status = status
            )
        }
    }
}
