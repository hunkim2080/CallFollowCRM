package com.detailline.callfollowcrm.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.detailline.callfollowcrm.data.local.entity.SitePhotoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SitePhotoDao {
    @Query("SELECT * FROM site_photos WHERE customerId = :customerId ORDER BY createdAt DESC")
    fun observeByCustomer(customerId: Long): Flow<List<SitePhotoEntity>>

    @Insert
    suspend fun insert(photo: SitePhotoEntity): Long

    @Query("SELECT filePath FROM site_photos WHERE id = :id")
    suspend fun filePathOf(id: Long): String?

    @Query("DELETE FROM site_photos WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** 아직 서버(웹 뷰어)에 안 올린 로컬 사진 — 오래된 것부터(업로드 순서=원본 순서 유지 → 전/후 자동추정 보존). */
    @Query("SELECT * FROM site_photos WHERE serverUploadedAt IS NULL ORDER BY createdAt ASC LIMIT :limit")
    suspend fun pendingUpload(limit: Int): List<SitePhotoEntity>

    /** 서버 업로드 완료 표시(ts) 또는 파일없음 표식(-1) — 다음 백필서 제외. */
    @Query("UPDATE site_photos SET serverUploadedAt = :ts WHERE id = :id")
    suspend fun markUploaded(id: Long, ts: Long)
}
