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

    @Insert
    suspend fun insert(entity: JobCrewEntity): Long

    @Query("DELETE FROM job_crew WHERE id = :id")
    suspend fun deleteById(id: Long)
}
