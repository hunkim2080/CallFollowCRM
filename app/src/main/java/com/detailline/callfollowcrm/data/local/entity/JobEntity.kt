package com.detailline.callfollowcrm.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 시공 "건(job)" — 한 고객이 여러 번(다른 날·다른 장소) 시공받는 경우의 **이력 보관**용. DB v42 (2026-07-20).
 *
 * 배경: 기존엔 시공 정보가 전부 CustomerEntity 단일 컬럼(scheduledWorkDate/address/금액/workCompletedAt)에만 있어,
 *   같은 고객에 두 번째 시공을 잡으면 첫 번째가 덮여 사라졌다. (프로토는 원래 "건 중심" — jobs[day] 배열)
 *
 * Phase 1(최소·안전): 이 테이블은 **완료된 지난 시공을 보관**한다. 현재 진행/예정 건은 여전히 CustomerEntity 가 들고 있고
 *   (캘린더·정산은 그대로 CustomerEntity 를 읽음), "완료된 고객에 새 일정 등록" 시 완료 건을 여기로 옮겨 담고
 *   CustomerEntity 를 새 건용으로 리셋한다 → 첫 시공이 유실되지 않고 "지난 시공 N건"으로 보인다.
 *   컬럼 구성은 CustomerEntity 의 시공 관련 필드와 1:1.
 */
@Entity(
    tableName = "jobs",
    indices = [
        Index(value = ["customerId"], name = "index_jobs_customerId"),
        Index(value = ["scheduledWorkDate"], name = "index_jobs_scheduledWorkDate")
    ]
)
data class JobEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val customerId: Long,
    val scheduledWorkDate: Long? = null,
    val scheduledWorkMinutes: Int? = null,
    val scheduledWorkDays: Int = 1,
    val address: String? = null,
    val totalAmount: Long? = null,
    val depositAmount: Long? = null,
    val depositPaidAt: Long? = null,
    val balanceAmount: Long? = null,
    val balancePaidAt: Long? = null,
    val workCompletedAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long
)
