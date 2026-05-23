package com.detailline.callfollowcrm.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.detailline.callfollowcrm.data.local.entity.RecordingAttachmentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordingAttachmentDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(attachment: RecordingAttachmentEntity): Long

    @Update
    suspend fun update(attachment: RecordingAttachmentEntity)

    @Query("SELECT * FROM recording_attachments WHERE customerId = :customerId ORDER BY createdAt DESC")
    fun observeByCustomer(customerId: Long): Flow<List<RecordingAttachmentEntity>>

    @Query("SELECT * FROM recording_attachments WHERE customerId IS NULL ORDER BY createdAt DESC")
    fun observeUnlinked(): Flow<List<RecordingAttachmentEntity>>

    @Query("SELECT * FROM recording_attachments WHERE id = :id")
    suspend fun findById(id: Long): RecordingAttachmentEntity?

    @Query("SELECT COUNT(*) FROM recording_attachments WHERE fileUri = :uri")
    suspend fun countByUri(uri: String): Int

    /** sourceType이 일치하는 모든 첨부 삭제. 에이닷 import 일괄 정리용. */
    @Query("DELETE FROM recording_attachments WHERE sourceType = :sourceType")
    suspend fun deleteBySourceType(sourceType: String): Int

    @Query("SELECT COUNT(*) FROM recording_attachments WHERE sourceType = :sourceType")
    suspend fun countBySourceType(sourceType: String): Int

    /**
     * 같은 번호로 들어왔지만 아직 Customer 에 연결 안 된 (orphan) 첨부를 일괄 연결.
     * Customer 가 새로 만들어지는 시점에 호출. 영향 받은 row 수 반환.
     */
    @Query("UPDATE recording_attachments SET customerId = :customerId WHERE customerId IS NULL AND phoneNumber = :phoneNumber")
    suspend fun linkOrphansToCustomer(phoneNumber: String, customerId: Long): Int
}
