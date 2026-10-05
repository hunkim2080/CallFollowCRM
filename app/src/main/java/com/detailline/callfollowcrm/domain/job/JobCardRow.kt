package com.detailline.callfollowcrm.domain.job

import com.detailline.callfollowcrm.data.local.entity.CustomerEntity
import com.detailline.callfollowcrm.data.local.entity.JobEntity

/**
 * 🧾 **한 건(JobEntity)을 '그 건의 얼굴'인 고객 카드 사본으로** 만든다.
 *   일정 탭·달력·홈 히어로처럼 "대표/그 건을 고객 카드 모양으로 그리는" 자리가 전부 이걸 쓴다.
 *
 *   ⚠️ **돈·완료는 반드시 그 건(job) 것을 싣는다.** 고객 카드(c)의 돈을 그대로 두면
 *   2차 날짜에 **1차 돈·완납·완료가 묻어나는 「짬뽕 전표」** 버그가 난다.
 *   (2026-10-05 사장님 신고 + Fable 감사 — recomputeMirror 가 일정만 미러하고 돈은 안 옮기기 때문.)
 *
 *   주소만 비었을 때 고객 카드 주소로 폴백한다(건마다 주소를 따로 안 적었을 수 있음).
 *   순수 함수 — DB·안드로이드에 안 기댄다. 단위 테스트가 전부 본다([JobCardRowTest]).
 */
object JobCardRow {
    fun rowOf(c: CustomerEntity, j: JobEntity): CustomerEntity = c.copy(
        scheduledWorkDate = j.scheduledWorkDate,
        scheduledWorkMinutes = j.scheduledWorkMinutes,
        scheduledWorkDays = j.scheduledWorkDays.coerceAtLeast(1),
        address = j.address?.takeIf { it.isNotBlank() } ?: c.address,
        totalAmount = j.totalAmount,
        depositAmount = j.depositAmount,
        depositPaidAt = j.depositPaidAt,
        balanceAmount = j.balanceAmount,
        balancePaidAt = j.balancePaidAt,
        workCompletedAt = j.workCompletedAt
    )
}
