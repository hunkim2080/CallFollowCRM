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
     *   잔금 칸이 비어 있으면 (총액 − 계약금)으로 채운다 — 안 채우면 고객 정보에
     *   「잔금 받음」이 뜨는데 금액이 0 이라 정산이 이상해진다.
     */
    suspend fun setCompletedAndPaid(customerId: Long, now: Long = System.currentTimeMillis()) {
        val c = customerRepository.findById(customerId)
        setCompleted(customerId, now, now)
        if (c != null) {
            val bal = c.balanceAmount
                ?: ((c.totalAmount ?: 0L) - (c.depositAmount ?: 0L)).coerceAtLeast(0L)
            if (c.balanceAmount == null && bal > 0L) {
                customerRepository.updateBalanceAmount(customerId, bal)
            }
            customerRepository.updateBalancePaidAt(customerId, now)
        }
    }
}
