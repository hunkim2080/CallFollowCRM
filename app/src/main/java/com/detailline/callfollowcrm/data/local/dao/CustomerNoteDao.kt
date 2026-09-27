package com.detailline.callfollowcrm.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.detailline.callfollowcrm.data.local.entity.CustomerNoteEntity
import kotlinx.coroutines.flow.Flow

/**
 * 📝 쪽지 읽고 쓰기.
 *
 * 정렬 규칙은 **한 곳에서만** 정한다 — 📌 먼저, 그다음 **최근 것부터**.
 *   화면마다 따로 정렬하면 「맨 윗줄」이 화면마다 달라진다.
 */
@Dao
interface CustomerNoteDao {

    /** 👤 손님 쪽지 — 현장이 바뀌어도 그대로인 것. */
    @Query(
        """SELECT * FROM customer_notes
           WHERE customerId = :customerId AND jobId IS NULL
           ORDER BY pinned DESC, createdAt DESC, id DESC"""
    )
    fun observeForCustomer(customerId: Long): Flow<List<CustomerNoteEntity>>

    /** 📍 그 건에서만 쓰는 쪽지. */
    @Query(
        """SELECT * FROM customer_notes
           WHERE jobId = :jobId
           ORDER BY pinned DESC, createdAt DESC, id DESC"""
    )
    fun observeForJob(jobId: Long): Flow<List<CustomerNoteEntity>>

    @Query(
        """SELECT * FROM customer_notes
           WHERE customerId = :customerId AND jobId IS NULL
           ORDER BY pinned DESC, createdAt DESC, id DESC"""
    )
    suspend fun forCustomerOnce(customerId: Long): List<CustomerNoteEntity>

    @Query(
        """SELECT * FROM customer_notes
           WHERE jobId = :jobId
           ORDER BY pinned DESC, createdAt DESC, id DESC"""
    )
    suspend fun forJobOnce(jobId: Long): List<CustomerNoteEntity>

    @Insert
    suspend fun insert(note: CustomerNoteEntity): Long

    @Update
    suspend fun update(note: CustomerNoteEntity)

    @Query("DELETE FROM customer_notes WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM customer_notes WHERE id = :id")
    suspend fun byId(id: Long): CustomerNoteEntity?

    /** 고객을 합칠 때 — 없어지는 쪽 쪽지를 남는 쪽으로 옮긴다. 하나도 안 잃게. */
    @Query("UPDATE customer_notes SET customerId = :keepId WHERE customerId = :fromId")
    suspend fun moveCustomer(fromId: Long, keepId: Long)

    /** 손님을 지울 때 함께. */
    @Query("DELETE FROM customer_notes WHERE customerId = :customerId")
    suspend fun deleteForCustomer(customerId: Long)
}
