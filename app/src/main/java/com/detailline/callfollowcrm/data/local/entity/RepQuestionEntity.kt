package com.detailline.callfollowcrm.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 💬 **대표질문(FAQ)** — 자주 묻는 질문 + 답 + 사진(0/1). (2026-11-21 사장님 「가」 · 프로토 EYKUs11cVT7JW71yVrVoBA)
 *
 *   채팅 ⊕ → 「대표질문」 → 탭하면 **답이 답장 입력칸에 들어간다**(전송 아님). 사진이 있으면 첨부에 같이 담긴다.
 *   「자주 쓰는 문구」(message_templates)와 **별개**다 — 저쪽은 문구 하나, 이쪽은 질문↔답 한 쌍.
 *   폰 안에만 저장한다(서버 X). 백업은 `DataBackup` 이 테이블째 담는다.
 *
 *   [imageUri] = 앱 내부 복사본의 FileProvider 주소(`TemplatePhotoStore`). 없으면 null.
 *   [sortOrder] = 관리 화면에서 끌어 정한 순서(작을수록 위).
 */
@Entity(tableName = "rep_questions")
data class RepQuestionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val question: String,
    val answer: String,
    val imageUri: String? = null,
    @ColumnInfo(defaultValue = "0") val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
