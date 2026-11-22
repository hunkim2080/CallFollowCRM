package com.detailline.callfollowcrm.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 💬 **저장한 말(문구) — 대표질문도 이 테이블.** (2026-11-22 사장님 「하나로 합치기」)
 *   [isPublic] 켜면 **손님 FAQ 페이지에도** 나간다(기본 꺼짐 — 공개는 일부러). 공개일 때
 *   [title]=손님 질문 · [body]=답 · [category]=분류 · 첨부 사진이 FAQ 답 사진. 발행은 `FaqPublishRepository`.
 */
@Entity(tableName = "message_templates")
data class MessageTemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val body: String,
    val category: String,
    val isDefault: Boolean,
    val isActive: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    /** 손님 FAQ 페이지 공개 여부 — 기본 비공개. 켜야만 발행에 포함된다. (DB v78) */
    @ColumnInfo(defaultValue = "0") val isPublic: Boolean = false
)
