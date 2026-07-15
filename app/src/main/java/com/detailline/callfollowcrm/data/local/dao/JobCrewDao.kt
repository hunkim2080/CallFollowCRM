package com.detailline.callfollowcrm.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.detailline.callfollowcrm.data.local.entity.JobCrewEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface JobCrewDao {
    @Query("SELECT * FROM job_crew ORDER BY dayStartMs DESC, id DESC")
    fun observeAll(): Flow<List<JobCrewEntity>>

    @Query("SELECT * FROM job_crew WHERE workerId = :workerId ORDER BY dayStartMs DESC")
    fun observeByWorker(workerId: Long): Flow<List<JobCrewEntity>>

    /** 이 시공(고객+날짜)에 배정된 일당들 — 배정 화면이 '지금 누가 붙어있나'를 그린다. */
    @Query("SELECT * FROM job_crew WHERE customerId = :customerId AND dayStartMs = :dayStartMs ORDER BY id")
    fun observeByJob(customerId: Long, dayStartMs: Long): Flow<List<JobCrewEntity>>

    /** 같은 사람·같은 시공·같은 날 배정이 이미 있나 — 중복 배정(=일당 이중 차감) 방지용. */
    @Query("SELECT * FROM job_crew WHERE workerId = :workerId AND customerId = :customerId AND dayStartMs = :dayStartMs LIMIT 1")
    suspend fun findAssignment(workerId: Long, customerId: Long, dayStartMs: Long): JobCrewEntity?

    @Query("UPDATE job_crew SET wage = :wage WHERE id = :id")
    suspend fun updateWage(id: Long, wage: Long)

    @Insert
    suspend fun insert(entity: JobCrewEntity): Long

    @Query("DELETE FROM job_crew WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM job_crew WHERE workerId = :workerId AND customerId = :customerId AND dayStartMs = :dayStartMs")
    suspend fun deleteAssignment(workerId: Long, customerId: Long, dayStartMs: Long)
}
