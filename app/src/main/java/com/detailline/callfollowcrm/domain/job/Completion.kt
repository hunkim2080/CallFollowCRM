package com.detailline.callfollowcrm.domain.job

import com.detailline.callfollowcrm.data.local.entity.CustomerEntity
import com.detailline.callfollowcrm.data.local.entity.JobEntity
import com.detailline.callfollowcrm.domain.settlement.SettleRow
import com.detailline.callfollowcrm.domain.settlement.SettlementCalc

/**
 * 한 건(또는 고객 카드)이 **어디까지 끝났나** — 세 단계.
 *   · [IN_PROGRESS] 진행 중 — 아직 안 끝남
 *   · [DONE]        완료   — **작업은 끝**. 돈은 남았을 수도(잔금미수) 없을 수도(금액 미입력) 있다
 *   · [PAID_OFF]    완납   — 작업도 끝나고 **돈까지 다 받음**
 *
 * 용어 확정(2026-10-18 사장님): 작업 끝 = 「완료」, 잔금까지 받음 = 「완납」.
 *   전엔 고객 목록 딱지의 「완료」는 *돈까지 받은 것*이고 건 카드·검색의 「완료」는 *작업만 끝난 것*이라
 *   같은 손님이 카드엔 「완료」, 목록엔 「잔금미수」로 동시에 떠서 "돈 받았나?"가 헷갈렸다.
 */
enum class CompletionStage {
    IN_PROGRESS, DONE, PAID_OFF;

    /** 딱지 글자. 진행 중은 건 카드의 「진행 중」. */
    val label: String
        get() = when (this) {
            IN_PROGRESS -> "진행 중"
            DONE -> "완료"
            PAID_OFF -> "완납"
        }

    /** 작업이 끝났나(완료든 완납이든). */
    val isDone: Boolean get() = this != IN_PROGRESS
}

/**
 * 🔒 **「끝났나」·「완납이냐」 판정은 여기 한 곳.** (2026-10-18)
 *   고객 목록 딱지([customerStatusOf]) · 건 카드 딱지 · 검색 한 줄 · `CustomerEntity.isWorkDone` 이 전부 이걸 부른다.
 *   돈 셈은 [SettlementCalc] 한 자(총액 − 계약금 · 잔금 받음 = 전액).
 *
 * 규칙:
 *   · **완료 도장([JobEntity.workCompletedAt])이 있으면 끝.**
 *   · **잔금 도장([JobEntity.balancePaidAt])도 끝으로 친다** — 잔금 받으면 = 완료(2026-08-18 사장님).
 *     단 **돈이 하나도 없는 건의 잔금 도장은 안 친다** — 0원 완납은 없다(2026-10-15 사장님 "완납 안 눌렀는데 완료로 뜸" ·
 *     `JobMoneyBackfill` ④ 와 같은 규칙). 카드에서 번진 도장만으로 「완료」가 되던 길을 막는다.
 *   · 끝났고 [SettleRow.isPaidOff](총액>0 이고 다 받음)면 **완납**, 아니면 **완료**.
 */
object Completion {

    fun isDone(workCompletedAt: Long?, balancePaidAt: Long?, settle: SettleRow): Boolean =
        workCompletedAt != null || (balancePaidAt != null && settle.total > 0L)

    fun stageOf(workCompletedAt: Long?, balancePaidAt: Long?, settle: SettleRow): CompletionStage = when {
        !isDone(workCompletedAt, balancePaidAt, settle) -> CompletionStage.IN_PROGRESS
        settle.isPaidOff -> CompletionStage.PAID_OFF
        else -> CompletionStage.DONE
    }

    fun stageOf(c: CustomerEntity): CompletionStage =
        stageOf(c.workCompletedAt, c.balancePaidAt, SettlementCalc.rowOf(c))

    fun stageOf(j: JobEntity): CompletionStage =
        stageOf(j.workCompletedAt, j.balancePaidAt, SettlementCalc.rowOf(j))

    fun isDone(c: CustomerEntity): Boolean = stageOf(c).isDone
    fun isDone(j: JobEntity): Boolean = stageOf(j).isDone
}
