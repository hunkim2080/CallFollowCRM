package com.detailline.callfollowcrm.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
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
}
