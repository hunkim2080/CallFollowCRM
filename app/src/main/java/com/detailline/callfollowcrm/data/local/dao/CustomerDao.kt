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

    @Query("SELECT * FROM customers WHERE status = :status ORDER BY updatedAt DESC")
    fun observeByStatus(status: String): Flow<List<CustomerEntity>>

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
