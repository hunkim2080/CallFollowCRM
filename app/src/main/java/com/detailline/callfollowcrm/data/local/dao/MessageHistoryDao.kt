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

    /**
     * 견적 보낸 기록 — 견적 회신 리마인드용 (2026-06-01). status='ESTIMATE_SENT'.
     *   customerId 별 최신 1건을 VM 에서 추려 "N일째 답 없음" 판정.
     */
    @Query("SELECT * FROM message_histories WHERE status = 'ESTIMATE_SENT' ORDER BY createdAt DESC")
    fun observeEstimateSends(): Flow<List<MessageHistoryEntity>>

    /**
     * 통계 "보낸 답장" — 실제 발송한 기록만 count (DRAFT_OPENED·취소·실패 제외). 기간 [from, to).
     */
    @Query("""
        SELECT COUNT(*) FROM message_histories
        WHERE status IN ('AUTO_SENT','INLINE_SENT','MANUAL_MARK_SENT','ESTIMATE_SENT')
          AND createdAt >= :from AND createdAt < :to
    """)
    fun observeSentCountBetween(from: Long, to: Long): Flow<Int>

    /**
     * 신규 재연락 목록 "답장함" 판정 — 사장님이 한 번이라도 보낸(또는 작성한) 고객 id 집합.
     *   AUTO_SENT(막내 자동인사) 포함 — 고객 입장에선 답이 간 상태. DRAFT_OPENED 도 "응대 시작"으로 봄.
     */
    @Query("""
        SELECT DISTINCT customerId FROM message_histories
        WHERE customerId IS NOT NULL
          AND status IN ('AUTO_SENT','INLINE_SENT','MANUAL_MARK_SENT','ESTIMATE_SENT','DRAFT_OPENED')
    """)
    fun observeRepliedCustomerIds(): Flow<List<Long>>
}
