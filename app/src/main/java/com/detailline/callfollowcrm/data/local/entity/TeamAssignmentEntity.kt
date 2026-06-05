package com.detailline.callfollowcrm.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 팀원 현장 배정 (2026-06-05, DB v29) — 시공(고객)에 팀원을 배정한 기록.
 *   · 일정 화면 배정 줄에서 팀원을 고르면 행 생성.
 *   · 저장 시 그 팀원의 모든 배정을 모아 서버 schedule-snapshot 으로 push → 팀원 웹뷰에 표시.
 *   memberName 은 denormalize(팀원 이름 바뀌어도 표시 안 깨지게). memberId = 서버 team_members.member_id.
 *   일당(JobCrew)과 별개 — 이건 앱 설치 X 팀원에게 현장 공유용.
 */
@Entity(
    tableName = "team_assignments",
    indices = [
        Index(value = ["customerId"], name = "idx_team_assignments_customerId"),
        Index(value = ["memberId"], name = "idx_team_assignments_memberId")
    ]
)
data class TeamAssignmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val memberId: String,
    val memberName: String,
    val customerId: Long,
    val dayStartMs: Long,
    val createdAt: Long,
    /** 사장님이 이 현장에서 직원에게 전달할 메모 (예: 현관 비번, 사다리차 필요). 현장당 1개 → 배정된 모든 팀원 행에 동일 저장. DB v30. */
    val teamMemo: String? = null
)
