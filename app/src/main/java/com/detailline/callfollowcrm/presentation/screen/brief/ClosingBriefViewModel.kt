package com.detailline.callfollowcrm.presentation.screen.brief

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.detailline.callfollowcrm.data.AppContainer
import com.detailline.callfollowcrm.data.local.entity.CustomerEntity
import com.detailline.callfollowcrm.util.DateTimeUtils
import com.detailline.callfollowcrm.util.PhoneNumberFormatter
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * 마감 브리핑 화면 (2026-06-06 신설).
 *   기존엔 "오늘 하루 마감 브리핑 🌙" 알림을 눌러도 홈만 열렸음 → 전용 화면 연결.
 *   내용 = 마감 알림(ReminderWorker.checkDailyBrief)과 같은 소스: 오늘 신규 / 오늘 입금 / 내일 시공.
 *   모든 수치는 로컬 집계 — 허위 숫자 없음.
 */
class ClosingBriefViewModel(container: AppContainer) : ViewModel() {

    private val now = System.currentTimeMillis()
    private val todayStart = DateTimeUtils.startOfDay(now)
    private val todayEnd = todayStart + DateTimeUtils.DAY_MS
    private val tomorrowStart = todayEnd
    private val tomorrowEnd = tomorrowStart + DateTimeUtils.DAY_MS

    val state: StateFlow<ClosingBriefState> =
        container.customerRepository.observeAll()
            .map { build(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ClosingBriefState())

    private fun build(cs: List<CustomerEntity>): ClosingBriefState {
        val newCount = cs.count { it.createdAt in todayStart until todayEnd }

        val depositToday = cs.filter { c ->
            (c.depositPaidAt?.let { it in todayStart until todayEnd } == true) ||
                (c.balancePaidAt?.let { it in todayStart until todayEnd } == true)
        }
        var paidSum = 0L
        for (c in depositToday) {
            if (c.depositPaidAt?.let { it in todayStart until todayEnd } == true) paidSum += (c.depositAmount ?: 0L)
            if (c.balancePaidAt?.let { it in todayStart until todayEnd } == true) {
                val bal = c.balanceAmount ?: ((c.totalAmount ?: 0L) - (c.depositAmount ?: 0L)).coerceAtLeast(0L)
                paidSum += bal
            }
        }

        val jobs = cs.filter { it.scheduledWorkDate?.let { d -> DateTimeUtils.startOfDay(d) in tomorrowStart until tomorrowEnd } == true }
            .sortedBy { it.scheduledWorkMinutes ?: Int.MAX_VALUE }
            .map { c ->
                BriefJob(
                    customerId = c.id,
                    phone = c.phoneNumber,
                    name = c.name?.takeIf { it.isNotBlank() } ?: PhoneNumberFormatter.format(c.phoneNumber),
                    timeLabel = c.scheduledWorkMinutes?.let { DateTimeUtils.formatWorkMinutes(it) } ?: "시간 미정"
                )
            }

        return ClosingBriefState(
            dateLabel = DateTimeUtils.formatDateOnly(todayStart),
            newCount = newCount,
            depositCount = depositToday.size,
            paidSumLabel = if (paidSum > 0L) "약 ${formatMoney(paidSum)}원" else null,
            jobs = jobs,
            loaded = true
        )
    }

    private fun formatMoney(won: Long): String {
        // 만원 단위로 읽기 쉽게. 1,200,000 → "120만", 50,000 → "5만", 5,000 → "5,000".
        return when {
            won >= 10_000 && won % 10_000 == 0L -> "${won / 10_000}만"
            won >= 10_000 -> "${"%,d".format(won)}"
            else -> "%,d".format(won)
        }
    }
}

data class BriefJob(
    val customerId: Long,
    val phone: String,
    val name: String,
    val timeLabel: String
)

data class ClosingBriefState(
    val dateLabel: String = "",
    val newCount: Int = 0,
    val depositCount: Int = 0,
    val paidSumLabel: String? = null,
    val jobs: List<BriefJob> = emptyList(),
    val loaded: Boolean = false
)
