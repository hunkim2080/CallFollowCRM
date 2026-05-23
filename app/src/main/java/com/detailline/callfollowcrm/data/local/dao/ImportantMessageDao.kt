package com.detailline.callfollowcrm.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.detailline.callfollowcrm.data.local.entity.ImportantMessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ImportantMessageDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: ImportantMessageEntity): Long

    /** 토글용 — unique key 매칭해 삭제. 반환값 = 삭제된 row 수 (0 또는 1). */
    @Query("""
        DELETE FROM important_messages
        WHERE phoneNumber = :phone AND messageDateMs = :dateMs AND sent = :sent
    """)
    suspend fun delete(phone: String, dateMs: Long, sent: Boolean): Int

    /** ChatScreen 의 별표 표시용 — 이 번호의 별표된 메시지 키 set 도출. */
    @Query("SELECT * FROM important_messages WHERE phoneNumber = :phone ORDER BY messageDateMs DESC")
    fun observeByPhone(phone: String): Flow<List<ImportantMessageEntity>>
}
