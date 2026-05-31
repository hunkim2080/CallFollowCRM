package com.detailline.callfollowcrm.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 수첩 연락처 (2026-06-01, DB v21) — 일당(WORKER) / 거래처(VENDOR) 통합.
 *   사장님이 같이 일하는 사람·거래하는 곳을 한 수첩에서 관리.
 *   kind 로 일당/거래처 구분 (테이블 하나로 두 종류).
 *
 *  - tag: 분류 (일당="보조/기공/대표", 거래처="자재/협력/장비" 등 자유 입력).
 */
@Entity(
    tableName = "notebook_contacts",
    indices = [Index(value = ["kind"], name = "idx_notebook_contacts_kind")]
)
data class NotebookContactEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** "WORKER" | "VENDOR" */
    val kind: String,
    val name: String,
    val phone: String = "",
    val tag: String = "",
    val memo: String = "",
    val createdAt: Long,
    val updatedAt: Long
) {
    companion object {
        const val KIND_WORKER = "WORKER"
        const val KIND_VENDOR = "VENDOR"
    }
}
