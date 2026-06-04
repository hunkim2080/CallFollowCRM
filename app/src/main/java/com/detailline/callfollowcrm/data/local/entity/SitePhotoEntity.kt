package com.detailline.callfollowcrm.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 현장 사진 (로컬 전용, 2026-06-04) — 사장님이 고객 현장에 올린 사진.
 *   파일은 앱 내부 저장소(filesDir/site_photos/)에 복사 후 경로만 보관.
 *   팀원↔사장님 공유(서버)는 별개 — docs/SERVER_HANDOFF 의 team_site_photos 보강 후 연동 예정.
 */
@Entity(
    tableName = "site_photos",
    indices = [Index("customerId")]
)
data class SitePhotoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val customerId: Long,
    /** 앱 내부 저장소에 복사한 파일 절대 경로. */
    val filePath: String,
    /** '시공 전'|'시공 중'|'시공 후'|'추가' — 현재 미사용(확장 대비, null). */
    val label: String? = null,
    val createdAt: Long
)
