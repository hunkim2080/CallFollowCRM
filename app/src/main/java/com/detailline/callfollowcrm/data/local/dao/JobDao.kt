package com.detailline.callfollowcrm.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.detailline.callfollowcrm.data.local.entity.JobEntity
import kotlinx.coroutines.flow.Flow

/** 시공 건(이력) DAO. DB v42. [JobEntity] 참고. */
@Dao
interface JobDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(job: JobEntity): Long

    /** 한 고객의 지난 시공 이력 — 최근 시공일 순. */
    @Query("SELECT * FROM jobs WHERE customerId = :customerId ORDER BY scheduledWorkDate DESC, id DESC")
    fun observeByCustomer(customerId: Long): Flow<List<JobEntity>>

    @Query("SELECT * FROM jobs WHERE customerId = :customerId ORDER BY scheduledWorkDate DESC, id DESC")
    suspend fun byCustomerOnce(customerId: Long): List<JobEntity>

    /** 지난 시공 N건 카운트(구독). */
    @Query("SELECT COUNT(*) FROM jobs WHERE customerId = :customerId")
    fun observeCountByCustomer(customerId: Long): Flow<Int>

    /** 모든 지난 시공(이력) — 매출 집계(정산·리포트·브리핑·현금흐름)가 CustomerEntity 와 함께 합산해야
     *   재방문 이관된 완료 건의 매출이 증발하지 않음. (2026-08-11 돈 정확성 감사 rank1) */
    @Query("SELECT * FROM jobs")
    fun observeAll(): Flow<List<JobEntity>>

    // ── Phase 2 Stage A (DB v49) — jobs 가 '예정 건'까지 들고 있는 일정 SoT 가 됨.
    //    (한 고객이 여러 날짜에 시공받는 인테리어 업체 케이스. 2026-09-11 사장님)

    /** 완료된 지난 시공만 — 고객상세 "지난 시공 N건". 예정 건이 jobs 에 들어와도 안 섞이게. */
    @Query("SELECT * FROM jobs WHERE customerId = :customerId AND workCompletedAt IS NOT NULL ORDER BY scheduledWorkDate DESC, id DESC")
    fun observeCompletedByCustomer(customerId: Long): Flow<List<JobEntity>>

    /** 시공일이 잡힌 모든 건 — 캘린더/일정 화면의 SoT. */
    @Query("SELECT * FROM jobs WHERE scheduledWorkDate IS NOT NULL ORDER BY scheduledWorkDate ASC, id ASC")
    fun observeScheduled(): Flow<List<JobEntity>>

    /** 한 고객의 '시공일 있는' 건 스냅샷 — 대표 건(미러) 재계산용. */
    @Query("SELECT * FROM jobs WHERE customerId = :customerId AND scheduledWorkDate IS NOT NULL ORDER BY scheduledWorkDate ASC, id ASC")
    suspend fun scheduledByCustomerOnce(customerId: Long): List<JobEntity>

    @Query("SELECT * FROM jobs WHERE id = :id")
    suspend fun findById(id: Long): JobEntity?

    @Update
    suspend fun update(job: JobEntity)

    @Query("DELETE FROM jobs WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** 같은 고객·같은 시공일 건이 이미 있나 — 중복 등록 가드. */
    @Query("SELECT COUNT(*) FROM jobs WHERE customerId = :customerId AND scheduledWorkDate = :dayMs")
    suspend fun countByCustomerAndDate(customerId: Long, dayMs: Long): Int

    /** 그 고객·그 날의 건 1개 — 일정 카드(밀어서 빼기)가 '어느 건'인지 특정할 때. */
    @Query("SELECT * FROM jobs WHERE customerId = :customerId AND scheduledWorkDate = :dayMs ORDER BY id ASC LIMIT 1")
    suspend fun jobAt(customerId: Long, dayMs: Long): JobEntity?
}
