package com.detailline.callfollowcrm.data.repository

/**
 * ✅ **「이 시공 끝났다」를 찍는 단 한 곳.** (2026-09-28)
 *
 * 왜 모았나 — 완료를 찍으려면 **두 군데**를 같이 고쳐야 한다:
 *   ① 고객 카드(customers.workCompletedAt)
 *   ② 그 손님의 **대표 건**(jobs.workCompletedAt)
 * 하나만 찍으면 건 탭의 「완료」 표시가 틀린다(2026-09-18 에 실제로 그랬다).
 *
 * 그런데 이 두 줄이 홈 화면 뷰모델에만 있었다. 채팅 화면에서도 완료를 찍게 되면서
 * **같은 두 줄을 또 적을 뻔했다.** 그러면 나중에 한쪽만 고쳐지고, 또 「화면마다 답이 다른」
 * 그 병이 된다(2026-09-28 사장님: "같은 물음에 각자 답하는 거 더 있니").
 *
 * 그래서 완료를 찍고 되돌리는 길은 앞으로 **여기 하나**다. 새 화면도 이걸 부른다.
 *
 * 💵 **「잔금 받음」도 여기 하나**([setBalancePaid]). (2026-10-15 사장님 "완납 안 눌렀는데 완료로 뜸")
 *   전엔 홈 미수 카드·정산·완료 팝업·채팅이 **고객 카드에만** 잔금 받음을 찍고 건은 안 건드렸다(§13④).
 *   그래서 카드는 「200만 · 다 받았어요」인데 건은 「금액 미입력」 + (완료 버튼의) 「완료」 — 모순으로 보였다.
 */
class WorkCompletionManager(
    private val customerRepository: CustomerRepository,
    private val jobRepository: JobRepository,
    /**
     * 완료/되돌리기 직후 불린다 — '오늘의 현장' 상시 알림을 바로 다시 그려(완료된 현장은 내림).
     *   왜 여기냐: 완료를 찍는 유일한 길목이라, 홈·채팅 어느 화면에서 눌러도 한 번에 반영된다
     *   (화면마다 또 적으면 두 벌). 알림 실패가 완료 저장을 막지 않게 부르는 쪽이 runCatching 으로 감싼다.
     *   (2026-10-07 사장님 "완료 누르면 바로 사라져야")
     */
    private val onChanged: suspend () -> Unit = {}
) {

    /**
     * 시공 완료를 찍거나 되돌린다.
     * @param at 완료 시각. **null 이면 되돌리기**(완료 취소).
     */
    suspend fun setCompleted(customerId: Long, at: Long?, now: Long = System.currentTimeMillis()) {
        customerRepository.updateWorkCompletedAt(customerId, at)
        // 건에도 같이 찍는다 — 고객 카드에만 찍으면 건 탭이 거짓말을 한다.
        jobRepository.representativeJobId(customerId, now)
            ?.let { jobRepository.setWorkCompleted(it, at, now) }
        onChanged()
    }

    /**
     * 완료 + **잔금까지 다 받음**. (완료 팝업의 「잔금 다 받았어요」)
     *   잔금 도장은 [setBalancePaid] 한 곳 — 카드와 대표 건 둘 다 찍힌다.
     */
    suspend fun setCompletedAndPaid(customerId: Long, now: Long = System.currentTimeMillis()) {
        setCompleted(customerId, now, now)
        setBalancePaid(customerId, now, jobId = null, now = now)
    }

    /**
     * 💵 **잔금 받음/되돌리기 도장 — 카드와 건을 같이 찍는다.**
     *   @param at 받은 시각. **null 이면 되돌리기**(완납 취소).
     *   @param jobId 그 건에만 찍을 때(건 카드·정산의 건별 줄). null = 고객 경로(대표 건에 찍는다).
     *
     *   고객 경로 순서가 중요하다:
     *     ① 카드에 찍고 → ② 카드에만 있던 돈(총액·계약금)을 대표 건으로 끌어올린 뒤([JobRepository.repairMoneyMirrorOf])
     *     → ③ 대표 건에 찍는다. ③ 안의 미러(건→카드)가 ② 없이 돌면 **카드 돈이 건의 빈 값으로 덮여 사라진다.**
     *   잔금 액수가 비어 있으면 (총액 − 계약금)으로 채운다 — 안 채우면 「잔금 받음」인데 0원이라 정산이 이상해진다.
     */
    suspend fun setBalancePaid(customerId: Long, at: Long?, jobId: Long? = null, now: Long = System.currentTimeMillis()) {
        if (jobId != null) {
            // 🔴 **그 건에만** 찍는다 — 옆 건 돈을 건드리면 안 된다. (2026-09-18) 건의 미러가 대표 건이면 카드도 따라간다.
            jobRepository.setBalancePaid(jobId, at, now)
            if (jobRepository.representativeJobId(customerId, now) == jobId) {
                customerRepository.updateBalancePaidAt(customerId, at)
            }
            return
        }
        val c = customerRepository.findById(customerId)
        if (at != null && c != null && c.balanceAmount == null) {
            val bal = ((c.totalAmount ?: 0L) - (c.depositAmount ?: 0L)).coerceAtLeast(0L)
            if (bal > 0L) customerRepository.updateBalanceAmount(customerId, bal)
        }
        customerRepository.updateBalancePaidAt(customerId, at)
        jobRepository.repairMoneyMirrorOf(customerId, now)
        jobRepository.representativeJobId(customerId, now)
            ?.let { jobRepository.setBalancePaid(it, at, now) }
    }
}
