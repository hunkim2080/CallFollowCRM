package com.detailline.callfollowcrm.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 일당 배정 (2026-06-01, DB v23) — 시공(고객+날짜)에 수첩의 일당을 배정한 기록.
 *   · 함께한 현장 = workerId 로 조회.
 *   · 일당 자동 차감 = dayStartMs 에 wage 만큼 현금흐름 "지출"로 자동 반영.
 *
 * workerName 은 denormalize — 일당을 수첩에서 수정/삭제해도 과거 배정 표시가 안 깨지게.
 */
@Entity(
    tableName = "job_crew",
    indices = [
        Index(value = ["workerId"], name = "idx_job_crew_workerId"),
        Index(value = ["customerId"], name = "idx_job_crew_customerId"),
        Index(value = ["dayStartMs"], name = "idx_job_crew_dayStartMs")
    ]
)
data class JobCrewEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workerId: Long,
    val workerName: String,
    val customerId: Long,
    val dayStartMs: Long,
    val wage: Long,
    val createdAt: Long
)
