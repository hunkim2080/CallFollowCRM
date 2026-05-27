package com.detailline.callfollowcrm.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 갤메시지 식 사장님 정의 카테고리. 한 카테고리 = 한 묶음의 고객.
 * 사장님이 직접 만들거나 AI 가 제안 (이름만으로 추론).
 *
 * 1:1 모델 — 한 고객은 한 카테고리에만 속함 (CustomerEntity.categoryId).
 *
 * 시스템 카테고리 (isSystem=true) = 사장님이 못 지움. 자동 분류 규칙으로 채워짐.
 *   예: "미확인" (7일 + 처음 연락 + 답장 X) — 단, 이건 실시간 계산 (categoryId 박지 않음).
 *      현재는 사용자 정의만 entity 로 저장. 미확인은 HomeViewModel 의 derived flag.
 */
@Entity(
    tableName = "categories",
    indices = [Index(value = ["name"], unique = true)]
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** 표시용 이모지. null = 없음. */
    val emoji: String? = null,
    /** 필터 row 에서의 표시 순서. 작을수록 왼쪽. */
    val displayOrder: Int = 0,
    val createdAt: Long,
    val updatedAt: Long
)
