package com.detailline.callfollowcrm.domain.job

import com.detailline.callfollowcrm.data.local.entity.JobEntity

/**
 * 🗑 **어떤 건을 완전히 지워도 되나** — 한 곳. (2026-10-17 사장님 실기)
 *
 *   전엔 두 벌이었다(§12-B 로 셈):
 *     · `JobRepository.deleteJob`   — "돈도 날짜도 없는 건만" (저장소 안전선)
 *     · `CustomerDetailJobCard`     — "취소했거나 **완전히 빈** 건만" 지우기 칩을 보여줌
 *   둘이 달라서 **주소만 든 날짜 미정 건**(문자 주소로 잘못 생긴 2차)은 저장소는 지울 수 있는데
 *   카드엔 지우기 칩도 더보기도 안 떴다 — "취소/삭제할 데가 안 보인다".
 *
 * 규칙: **날짜가 없고, 돈(총액·계약금·잔금)도 없고, 받음 도장도 없으면** 지울 수 있다.
 *   주소·메모만 있는 껍데기는 기록이 아니라 자리만 차지하는 것이라 지운다(확인창은 화면이 띄운다).
 *   살아있는 건(날짜·돈·받음 중 하나라도)은 실수로라도 안 지운다 — 취소부터.
 */
object JobDeleteRule {

    fun canDelete(j: JobEntity): Boolean =
        j.scheduledWorkDate == null &&
            (j.totalAmount ?: 0L) <= 0L &&
            (j.depositAmount ?: 0L) <= 0L &&
            (j.balanceAmount ?: 0L) <= 0L &&
            j.depositPaidAt == null &&
            j.balancePaidAt == null
}
