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
import java.util.Calendar

/**
 * 마감 브리핑 화면 (2026-06-06 신설 · 2026-07-03 의미있게 재구성).
 *   형식적 숫자 3개 → 오늘 성취 + 이번 달 목표 진행 + 내일 준비(주소·받을돈) + 못 받은 돈 챙김.
 *   모든 수치는 CustomerEntity 리스트 로컬 집계 — 허위 숫자 없음. (SMS 소스는 이 화면 VM엔 없어 미포함)
 */
class ClosingBriefViewModel(container: AppContainer) : ViewModel() {

    private val now = System.currentTimeMillis()
    private val todayStart = DateTimeUtils.startOfDay(now)
    private val todayEnd = todayStart + DateTimeUtils.DAY_MS
    private val tomorrowStart = todayEnd
    private val tomorrowEnd = tomorrowStart + DateTimeUtils.DAY_MS
    private val monthStart = Calendar.getInstance().apply {
        timeInMillis = now
        set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    private val goalWon = container.preferences.monthlyGoalManwon.toLong() * 10_000L

    val state: StateFlow<ClosingBriefState> =
        container.customerRepository.observeAll()
            .map { build(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ClosingBriefState())

    /** 이 고객이 지금까지 실제로 낸 돈(계약금 paidAt + 잔금 paidAt). */
    private fun paidOf(c: CustomerEntity): Long {
        var p = 0L
        if (c.depositPaidAt != null) p += (c.depositAmount ?: 0L)
        if (c.balancePaidAt != null)
            p += (c.balanceAmount ?: ((c.totalAmount ?: 0L) - (c.depositAmount ?: 0L)).coerceAtLeast(0L))
        return p
    }

    /** 이 고객이 내야 할 총액(견적). */
    private fun owedOf(c: CustomerEntity): Long =
        c.totalAmount ?: ((c.depositAmount ?: 0L) + (c.balanceAmount ?: 0L))

    /** [from, until) 안에 입금된 금액(계약금/잔금 각각 그 시각이 범위 안일 때). */
    private fun paidInRange(c: CustomerEntity, from: Long, until: Long): Long {
        var p = 0L
        if (c.depositPaidAt?.let { it in from until until } == true) p += (c.depositAmount ?: 0L)
        if (c.balancePaidAt?.let { it in from until until } == true)
            p += (c.balanceAmount ?: ((c.totalAmount ?: 0L) - (c.depositAmount ?: 0L)).coerceAtLeast(0L))
        return p
    }

    private fun build(cs: List<CustomerEntity>): ClosingBriefState {
        val newCount = cs.count { it.createdAt in todayStart until todayEnd }
        val completedCount = cs.count { it.workCompletedAt?.let { d -> d in todayStart until todayEnd } == true }

        val paidSum = cs.sumOf { paidInRange(it, todayStart, todayEnd) }
        val depositCount = cs.count { paidInRange(it, todayStart, todayEnd) > 0L }
        val monthPaidSum = cs.sumOf { paidInRange(it, monthStart, now + 1) }

        // 오늘 성취 한 줄 (0 인 항목은 자연스럽게 빠짐).
        val parts = buildList {
            if (newCount > 0) add("새 문의 ${newCount}건")
            if (completedCount > 0) add("시공 ${completedCount}곳 마무리")
            if (paidSum > 0) add("${formatMoney(paidSum)}원 입금")
        }
        val achievementLine = if (parts.isNotEmpty()) "오늘 ${parts.joinToString(", ")}." else null

        // 이번 달 목표 진행.
        val progress = if (goalWon > 0) monthPaidSum.toFloat() / goalWon.toFloat() else 0f
        val goalReached = goalWon > 0 && monthPaidSum >= goalWon
        val progressLabel = when {
            goalWon <= 0 -> null
            goalReached -> "이번 달 목표를 넘었어요! 🎉"
            else -> "목표까지 ${formatMoney(goalWon - monthPaidSum)}원 남았어요"
        }

        // 내일 시공 (주소 + 받을 돈 강화).
        val jobs = cs.filter { it.scheduledWorkDate?.let { d -> DateTimeUtils.startOfDay(d) in tomorrowStart until tomorrowEnd } == true }
            .sortedBy { it.scheduledWorkMinutes ?: Int.MAX_VALUE }
            .map { c ->
                val receivable = (owedOf(c) - paidOf(c)).coerceAtLeast(0L)
                BriefJob(
                    customerId = c.id,
                    phone = c.phoneNumber,
                    name = c.name?.takeIf { it.isNotBlank() } ?: PhoneNumberFormatter.format(c.phoneNumber),
                    timeLabel = c.scheduledWorkMinutes?.let { DateTimeUtils.formatWorkMinutes(it) } ?: "시간 미정",
                    addressLabel = c.address?.trim()?.takeIf { it.isNotBlank() },
                    receivableLabel = if (receivable > 0L) "받을 돈 ${formatMoney(receivable)}" else null
                )
            }

        // 못 받은 돈 — 시공 완료했는데 아직 안 들어온 잔금.
        val outstandingList = cs.filter { it.workCompletedAt != null && (owedOf(it) - paidOf(it)) > 0L }
        val outstandingSum = outstandingList.sumOf { (owedOf(it) - paidOf(it)).coerceAtLeast(0L) }
        val outstandingExample = outstandingList.maxByOrNull { it.workCompletedAt ?: 0L }?.let { c ->
            val nm = c.name?.takeIf { it.isNotBlank() } ?: PhoneNumberFormatter.format(c.phoneNumber)
            "$nm ${formatMoney((owedOf(c) - paidOf(c)).coerceAtLeast(0L))}원"
        }

        return ClosingBriefState(
            dateLabel = DateTimeUtils.formatDateOnly(todayStart),
            achievementLine = achievementLine,
            newCount = newCount,
            completedCount = completedCount,
            depositCount = depositCount,
            paidSumLabel = if (paidSum > 0L) "약 ${formatMoney(paidSum)}원" else null,
            monthPaidLabel = "${formatMoney(monthPaidSum)}원",
            goalLabel = if (goalWon > 0) "${formatMoney(goalWon)}원" else "",
            progressFraction = progress.coerceIn(0f, 1f),
            progressPct = (progress * 100).toInt(),
            progressLabel = progressLabel,
            todayContribLabel = if (paidSum > 0L) "오늘 +${formatMoney(paidSum)}원" else null,
            jobs = jobs,
            outstandingCount = outstandingList.size,
            outstandingSumLabel = if (outstandingSum > 0L) "${formatMoney(outstandingSum)}원" else null,
            outstandingExample = outstandingExample,
            loaded = true
        )
    }

    private fun formatMoney(won: Long): String {
        // 만원 단위로 읽기 쉽게. 1,200,000 → "120만", 50,000 → "5만", 5,000 → "5,000".
        return when {
            won >= 10_000 && won % 10_000 == 0L -> "${won / 10_000}만"
            else -> "%,d".format(won)
        }
    }
}

data class BriefJob(
    val customerId: Long,
    val phone: String,
    val name: String,
    val timeLabel: String,
    val addressLabel: String? = null,
    val receivableLabel: String? = null
)

data class ClosingBriefState(
    val dateLabel: String = "",
    val achievementLine: String? = null,
    val newCount: Int = 0,
    val completedCount: Int = 0,
    val depositCount: Int = 0,
    val paidSumLabel: String? = null,
    // 이번 달 목표
    val monthPaidLabel: String = "",
    val goalLabel: String = "",
    val progressFraction: Float = 0f,
    val progressPct: Int = 0,
    val progressLabel: String? = null,
    val todayContribLabel: String? = null,
    // 내일
    val jobs: List<BriefJob> = emptyList(),
    // 못 받은 돈
    val outstandingCount: Int = 0,
    val outstandingSumLabel: String? = null,
    val outstandingExample: String? = null,
    val loaded: Boolean = false
)
