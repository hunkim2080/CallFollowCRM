package com.detailline.callfollowcrm.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.detailline.callfollowcrm.data.local.entity.CustomerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomerDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(customer: CustomerEntity): Long

    @Update
    suspend fun update(customer: CustomerEntity)

    @Query("SELECT * FROM customers WHERE phoneNumber = :phoneNumber LIMIT 1")
    suspend fun findByPhone(phoneNumber: String): CustomerEntity?

    @Query("SELECT * FROM customers WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): CustomerEntity?

    @Query("SELECT * FROM customers WHERE id = :id LIMIT 1")
    fun observeById(id: Long): Flow<CustomerEntity?>

    @Query("SELECT * FROM customers ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<CustomerEntity>>

    // 2026-05-25: observeByStatus 제거 — status 컬럼 v13 마이그레이션에서 drop.

    /** 사장님 카테고리 별 고객 목록. */
    @Query("SELECT * FROM customers WHERE categoryId = :categoryId ORDER BY updatedAt DESC")
    fun observeByCategoryId(categoryId: Long): Flow<List<CustomerEntity>>

    /** categoryId 일괄 갱신 — 카테고리 삭제 시 그 카테고리에 속한 고객들을 미분류로 되돌림. */
    @Query("UPDATE customers SET categoryId = NULL, updatedAt = :now WHERE categoryId = :categoryId")
    suspend fun clearCategoryAssignment(categoryId: Long, now: Long)

    @Query("UPDATE customers SET categoryId = :categoryId, updatedAt = :now WHERE id = :customerId")
    suspend fun setCategoryId(customerId: Long, categoryId: Long?, now: Long)

    @Query("SELECT * FROM customers WHERE scheduledWorkDate IS NOT NULL ORDER BY scheduledWorkDate ASC")
    fun observeScheduled(): Flow<List<CustomerEntity>>

    /**
     * "고아 고객" 삭제: 메모 비어있고, 이름 없고, MessageHistory도 없는 고객.
     * 에이닷 자동 import로만 만들어진 미사용 고객 정리용.
     */
    @Query("""
        DELETE FROM customers
        WHERE (name IS NULL OR name = '')
          AND memo = ''
          AND id NOT IN (SELECT DISTINCT customerId FROM message_histories WHERE customerId IS NOT NULL)
    """)
    suspend fun deleteOrphans(): Int
}
