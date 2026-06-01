package com.detailline.callfollowcrm.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.detailline.callfollowcrm.data.local.entity.MessageHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageHistoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(history: MessageHistoryEntity): Long

    @Update
    suspend fun update(history: MessageHistoryEntity)

    @Query("SELECT * FROM message_histories WHERE id = :id")
    suspend fun findById(id: Long): MessageHistoryEntity?

    @Query("SELECT * FROM message_histories WHERE customerId = :customerId ORDER BY createdAt DESC")
    fun observeByCustomer(customerId: Long): Flow<List<MessageHistoryEntity>>

    @Query("SELECT * FROM message_histories WHERE phoneNumber = :phone ORDER BY createdAt DESC")
    fun observeByPhone(phone: String): Flow<List<MessageHistoryEntity>>

    /**
     * "사장님이 이 번호에 후속 처리를 한 적이 있는가" 의 빠른 체크.
     * 실패/취소/SKIPPED 는 제외. 실제로 의미 있는 처리(자동/인라인 발송, 수동 마크, 드래프트 오픈)만 count.
     */
    @Query("""
        SELECT COUNT(*) FROM message_histories
        WHERE phoneNumber = :phone
          AND status IN ('AUTO_SENT','INLINE_SENT','MANUAL_MARK_SENT','DRAFT_OPENED')
    """)
    suspend fun countHandledForPhone(phone: String): Int

    /**
     * 최근 자동답장(부재중 첫 응답) 기록 — 홈 "자동답장" 카드용 (2026-06-01).
     *   AUTO_SENT/AUTO_FAILED 만 (AUTO_CANCELLED = 사장님 본인이 취소 → 카드로 안 보여줌).
     *   sinceMs 이후, 최신순 limit 개.
     */
    @Query("""
        SELECT * FROM message_histories
        WHERE status IN ('AUTO_SENT','AUTO_FAILED')
          AND createdAt >= :sinceMs
        ORDER BY createdAt DESC
        LIMIT :limit
    """)
    fun observeRecentAutoReplies(sinceMs: Long, limit: Int): Flow<List<MessageHistoryEntity>>
}
