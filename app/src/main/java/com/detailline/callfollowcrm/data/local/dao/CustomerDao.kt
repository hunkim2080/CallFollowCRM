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

    /**
     * 계약금 이중곱 오염 self-heal (2026-07-09) — 2026-07-04~ intake fixed 계약금이 서버왕복 후 ×10000 두 번
     *   적용돼 원값이 10만원→10억(1e9)으로 저장된 row 보정. 오탐0 조건: 만원 배수 + 1억(1e8) 이상(이 도메인
     *   계약금으로 비현실적=이중곱 산물만 도달) + 총액 초과(진짜 계약금은 총액 못 넘음). 보정=÷10000.
     *   같은 row 컬럼만 비교하는 단순 UPDATE(서브쿼리 없음) → 기기 SQLite 에서 안전. 정상값은 미달로 미트리거,
     *   보정 후 값도 1e8 미만이라 재실행해도 idempotent.
     */
    @Query("""
        UPDATE customers SET depositAmount = depositAmount / 10000
        WHERE depositAmount IS NOT NULL
          AND depositAmount % 10000 = 0
          AND depositAmount >= 100000000
          AND totalAmount IS NOT NULL AND depositAmount > totalAmount
    """)
    suspend fun healDoubleMultipliedDeposits(): Int

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
