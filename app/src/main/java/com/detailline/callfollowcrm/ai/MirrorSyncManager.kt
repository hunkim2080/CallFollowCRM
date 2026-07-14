package com.detailline.callfollowcrm.ai

import com.detailline.callfollowcrm.data.preferences.AppPreferences
import com.detailline.callfollowcrm.data.repository.CustomerRepository
import com.detailline.callfollowcrm.data.repository.ManualCashRepository
import com.detailline.callfollowcrm.domain.settlement.CashFlowCalc
import com.detailline.callfollowcrm.domain.settlement.SettlementCalc
import com.detailline.callfollowcrm.util.DateTimeUtils
import com.detailline.callfollowcrm.util.PhoneNumberFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 본폰 미러 링크 — 일정/돈 스냅샷을 서버로 자동 전송(2026-07-13). [MirrorRepository]
 *   설계대로: observeAll 구독 → **30초 디바운스 + 해시 비교 시에만 push** + ReminderWorker(~3h) 백업.
 *   돈 요약(오늘 입금/미수금)은 정산과 같은 계산([CashFlowCalc]/[SettlementCalc]) 재사용 = 홈/정산과 항상 일치.
 *
 * 옵트인 — mirrorEnabled && mirrorToken 있을 때만 동작. 꺼져 있으면 조용히 skip(비용 0).
 */
class MirrorSyncManager(
    private val repo: MirrorRepository,
    private val prefs: AppPreferences,
    private val customerRepository: CustomerRepository,
    private val manualCashRepository: ManualCashRepository
) {
    private val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.KOREA)

    /**
     * 라이브 전송 — 일정/직접현금이 바뀌면 30초 잠잠해진 뒤 1회 push.
     *   collectLatest = 새 변경이 오면 대기 중이던 30초를 취소·재시작 → 자연스러운 디바운스.
     */
    fun start(scope: CoroutineScope) {
        scope.launch {
            combine(
                customerRepository.observeAll(),
                manualCashRepository.observeAll()
            ) { _, _ -> Unit }.collectLatest {
                if (!isActive()) return@collectLatest
                delay(30_000)               // 디바운스: 다음 변경이 오면 이 대기가 취소됨
                runCatching { pushNow(force = false) }
            }
        }
    }

    private fun isActive(): Boolean =
        prefs.mirrorEnabled && !prefs.mirrorToken.isNullOrBlank()

    /**
     * 스냅샷 1회 전송. force=false 면 직전 전송과 내용이 같을 때 skip(해시 비교).
     *   전송 성공했을 때만 해시/시각 저장 → 실패하면 다음 기회(변경·워커)에 재시도.
     *   @return 실제로 보냈으면 true.
     */
    suspend fun pushNow(force: Boolean): Boolean {
        if (!isActive()) return false
        val ownerPhone = prefs.bizPhone.trim()
        if (ownerPhone.filter { it.isDigit() }.length < 9) return false

        val now = System.currentTimeMillis()
        val todayStart = DateTimeUtils.startOfDay(now)
        val customers = runCatching { customerRepository.allOnce() }.getOrDefault(emptyList())
        val manual = runCatching { manualCashRepository.observeAll().first() }.getOrDefault(emptyList())

        // 일정 — 시공일이 잡힌 현장 전부(본폰은 사장님 본인 폰 → 전화·메모까지 포함, 처리방침 확정).
        val items = customers
            .filter { (it.scheduledWorkDate ?: 0L) > 0L }
            .sortedBy { it.scheduledWorkDate }
            .map { c ->
                val day = DateTimeUtils.startOfDay(c.scheduledWorkDate!!)
                MirrorRepository.MirrorItem(
                    date = dateFmt.format(Date(day)),
                    time = c.scheduledWorkMinutes?.let { "%02d:%02d".format(it / 60, it % 60) },
                    days = c.scheduledWorkDays.coerceAtLeast(1),
                    name = c.name?.takeIf { it.isNotBlank() } ?: "현장",
                    address = c.address?.takeIf { it.isNotBlank() },
                    // 고객 전화 = 하이픈 포함으로(사장님 요청). tel: 링크는 하이픈 있어도 동작.
                    phone = c.phoneNumber.takeIf { it.isNotBlank() }?.let { PhoneNumberFormatter.format(it) },
                    memo = c.memo.takeIf { it.isNotBlank() },
                    completed = c.workCompletedAt != null,
                    // 총금액 = 정산과 같은 계산(총액 없으면 계약금+잔금). 0=미입력 → 뷰어가 숨김.
                    total = SettlementCalc.rowOf(c).total
                )
            }

        // 돈 — 오늘 입금(확정 수입, 오늘자) / 미수금(받을 돈 남은 합계 + 건수). 정산과 동일 계산.
        val cashItems = CashFlowCalc.buildItems(customers, manual, emptyList(), todayStart)
        val todayIn = cashItems
            .filter { it.isIncome && it.isDone && it.dayStartMs == todayStart }
            .sumOf { it.amount }
        val rows = customers.filter { SettlementCalc.hasMoney(it) }.map { SettlementCalc.rowOf(it) }
        val unpaid = rows.sumOf { it.outstanding }
        val unpaidCount = rows.count { it.outstanding > 0L }

        val label = prefs.mirrorLabel.ifBlank { prefs.bizName.ifBlank { "내 일정" } }
        val hash = buildHash(label, items, todayIn, unpaid, unpaidCount)
        if (!force && hash == prefs.mirrorLastHash) return false

        val res = repo.pushSnapshot(ownerPhone, label, items, todayIn, unpaid, unpaidCount)
        return if (res.isSuccess) {
            prefs.mirrorLastHash = hash
            prefs.mirrorLastPushMs = System.currentTimeMillis()
            true
        } else {
            false
        }
    }

    /** 내용 지문 — 바뀐 게 없으면 재전송 안 하려고. items 순서·필드 + 돈 요약 전부 반영. */
    private fun buildHash(
        label: String,
        items: List<MirrorRepository.MirrorItem>,
        todayIn: Long,
        unpaid: Long,
        unpaidCount: Int
    ): String {
        val sb = StringBuilder()
        sb.append(label).append('|').append(todayIn).append('|').append(unpaid).append('|').append(unpaidCount)
        for (it in items) {
            sb.append('¶')
                .append(it.date).append('~').append(it.time ?: "").append('~').append(it.days)
                .append('~').append(it.name).append('~').append(it.address ?: "")
                .append('~').append(it.phone ?: "").append('~').append(it.memo ?: "")
                .append('~').append(it.completed).append('~').append(it.total)
        }
        return sb.toString().hashCode().toString()
    }
}
