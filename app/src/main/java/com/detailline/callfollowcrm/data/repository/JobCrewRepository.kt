package com.detailline.callfollowcrm.data.repository

import com.detailline.callfollowcrm.data.local.dao.JobCrewDao
import com.detailline.callfollowcrm.data.local.entity.JobCrewEntity
import com.detailline.callfollowcrm.util.DateTimeUtils
import kotlinx.coroutines.flow.Flow

/** 일당 배정 — 시공(고객+날짜)에 일당 배정. 함께한 현장 + 일당 자동차감의 단일 출처. */
class JobCrewRepository(private val dao: JobCrewDao) {

    fun observeAll(): Flow<List<JobCrewEntity>> = dao.observeAll()
    fun observeByWorker(workerId: Long): Flow<List<JobCrewEntity>> = dao.observeByWorker(workerId)

    suspend fun assign(workerId: Long, workerName: String, customerId: Long, dayMs: Long, wage: Long): Long {
        return dao.insert(
            JobCrewEntity(
                workerId = workerId,
                workerName = workerName,
                customerId = customerId,
                dayStartMs = DateTimeUtils.startOfDay(dayMs),
                wage = wage.coerceAtLeast(0L),
                createdAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun remove(id: Long) = dao.deleteById(id)
}
