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

    // 계약금 이중곱 self-heal 은 repository(Kotlin)에서 allOnce()+update() 로 처리 — @Query UPDATE 가 기기에서
    //   조용히 실행 안 되던 문제 우회. (2026-07-10)

    @Query("SELECT * FROM customers WHERE phoneNumber = :phoneNumber LIMIT 1")
    suspend fun findByPhone(phoneNumber: String): CustomerEntity?

    @Query("SELECT * FROM customers WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): CustomerEntity?

    @Query("SELECT * FROM customers WHERE id = :id LIMIT 1")
    fun observeById(id: Long): Flow<CustomerEntity?>

    @Query("SELECT * FROM customers ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<CustomerEntity>>

    /** 2026-05-30 #7 — AutoCategoryClassifier.backfillAll 용 1회 조회. */
    @Query("SELECT * FROM customers")
    suspend fun allOnce(): List<CustomerEntity>

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

    /** A/S 예약이 잡힌 고객 — 시공 예약(scheduledWorkDate)과 **별개**. 캘린더 A/S 주황 마커용. (DB v43) */
    @Query("SELECT * FROM customers WHERE asScheduledDate IS NOT NULL ORDER BY asScheduledDate ASC")
    fun observeAsScheduled(): Flow<List<CustomerEntity>>

    // "고아 고객 삭제(deleteOrphans)" 쿼리 제거 (2026-08-11 데이터안전 감사): 호출부 0건 dead code 인데,
    //   name/memo/문자기록만 보고 지워 '시공일·계약금 있는 고객'까지 삭제할 수 있는 위험 구조였음.
}
