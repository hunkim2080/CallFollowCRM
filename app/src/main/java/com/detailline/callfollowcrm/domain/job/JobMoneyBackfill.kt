package com.detailline.callfollowcrm.domain.job

import com.detailline.callfollowcrm.data.local.entity.CustomerEntity
import com.detailline.callfollowcrm.data.local.entity.JobEntity

/**
 * 🩹 **고객 카드에만 있는 돈·받음·완료를 대표 건으로 끌어올리는 규칙** — 한 곳. (2026-10-15)
 *
 *   JobRepository.repairMoneyMirror(시작 시 치유)가 쓰던 셈을 순수 함수로 뗐다([JobMoneyBackfillTest]).
 *
 *   왜 손봤나 — 2026-10-15 사장님: "완납버튼 누르지 않았는데도 완료처리 되는 건 버그인듯?"
 *     고객 카드엔 200만·완납, 건엔 돈이 없는데 **받음·완료만** 건으로 끌어올려
 *     카드가 「금액 아직 입력 안 함」 + 「완료」 딱지라는 모순을 보였다(총액은 안 끌어올렸다 — §13④ 돈 흩어짐).
 *
 *   규칙(빈 칸만 채운다 · 멱등):
 *     ① 건에 돈·받음·완료가 **하나도 없으면** 카드 것을 통째로.
 *     ② 건에 뭔가 있으면 **카드에만 받음(완납·계약금)이 있을 때만** — 그때 **총액·계약금·잔금 액수도 같이** 끌어온다.
 *     ③ 🛡️ 건에 총액이 있는데 카드 총액이 **다르면** 카드 돈은 다른 건(또는 옛) 것 — 아무것도 안 끌어온다.
 *     ④ 🛡️ 돈이 **어디에도 없으면** 「받음」은 안 만든다(0원 완납 금지). 명시한 완료일만 옮긴다.
 *     ⑤ 전액 완납 = 시공 완료(사장님 확정) — 완료일이 없으면 잔금 받은 날로.
 *
 * @return 바뀐 건. **바꿀 게 없으면 null** (호출부가 저장·카운트를 건너뛴다).
 */
object JobMoneyBackfill {

    private fun JobEntity.hasAny(): Boolean =
        totalAmount != null || depositAmount != null || balanceAmount != null ||
            depositPaidAt != null || balancePaidAt != null || workCompletedAt != null

    private fun CustomerEntity.hasAny(): Boolean =
        totalAmount != null || depositAmount != null || balanceAmount != null ||
            depositPaidAt != null || balancePaidAt != null || workCompletedAt != null

    /** 이 건에 돈·받음·완료가 하나라도 있나. (B1 가드: '대표는 비었는데 다른 건에 돈 있나' 판정에 호출부가 쓴다) */
    fun hasMoneyOrStamp(job: JobEntity): Boolean = job.hasAny()

    /**
     * @param othersHaveMoney 이 고객의 **대표 아닌 다른 건**에 돈·받음·완료가 있나(호출부가 셈).
     *   true 이고 대표 건이 비어 있으면 — 카드 돈은 그 다른(지난) 건 것이라 **빈 대표로 안 끌어온다**(B1).
     */
    fun merge(rep: JobEntity, c: CustomerEntity, othersHaveMoney: Boolean = false): JobEntity? {
        if (!c.hasAny()) return null
        val jobEmpty = !rep.hasAny()
        // 🛡️ B1 (2026-10-26 보안감사): 대표 건이 비었는데 **다른 건에 돈이 있으면** 카드 돈은 그 건(지난 1차) 것이다.
        //   빈 미래 건으로 끌어올리면 미래가 완납·완료로 뜬다(매출 이중계산·거짓 완납). → 아무것도 안 끌어온다.
        //   카드 스테일은 호출부가 recomputeMirror(건→카드)로 바로잡는다.
        if (jobEmpty && othersHaveMoney) return null
        val pullPaid = (rep.balancePaidAt == null && c.balancePaidAt != null) ||
            (rep.depositPaidAt == null && c.depositPaidAt != null)
        if (!jobEmpty && !pullPaid) return null
        // ③ 스테일 가드 — 총액이 서로 다르면 카드 돈은 이 건 것이 아니다.
        if (rep.totalAmount != null && c.totalAmount != null && rep.totalAmount != c.totalAmount) return null

        val total = rep.totalAmount ?: c.totalAmount
        val deposit = rep.depositAmount ?: c.depositAmount
        val balance = rep.balanceAmount ?: c.balanceAmount
        // ④ 돈이 어디에도 없으면 '받음'은 뜻이 없다 — 완료만.
        val anyMoney = (total ?: 0L) > 0L || (deposit ?: 0L) > 0L || (balance ?: 0L) > 0L
        val depositPaidAt = if (anyMoney) rep.depositPaidAt ?: c.depositPaidAt else rep.depositPaidAt
        val balancePaidAt = if (anyMoney) rep.balancePaidAt ?: c.balancePaidAt else rep.balancePaidAt
        // ⑤ 전액 완납 = 완료.
        val workCompletedAt = rep.workCompletedAt ?: c.workCompletedAt ?: balancePaidAt

        val out = rep.copy(
            totalAmount = total,
            depositAmount = deposit,
            balanceAmount = balance,
            depositPaidAt = depositPaidAt,
            balancePaidAt = balancePaidAt,
            workCompletedAt = workCompletedAt
        )
        return if (out == rep) null else out
    }
}
