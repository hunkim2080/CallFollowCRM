package com.detailline.callfollowcrm.data.repository

import com.detailline.callfollowcrm.data.local.dao.ManualCashDao
import com.detailline.callfollowcrm.data.local.entity.ManualCashEntity
import com.detailline.callfollowcrm.util.DateTimeUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** 직접 현금 기록 (정산 Phase 2). 날짜는 항상 자정으로 정규화해서 저장. */
class ManualCashRepository(private val dao: ManualCashDao) {

    // 한 현금 기록의 읽기-수정-쓰기 직렬화 — 금액 수정과 완료 토글이 동시에 오면 한쪽이 덮여 유실되던 것 방지. (2026-08-11 돈감사)
    private val writeMutex = Mutex()

    fun observeAll(): Flow<List<ManualCashEntity>> = dao.observeAll()

    suspend fun add(
        dayMs: Long,
        amount: Long,
        isIncome: Boolean,
        isDone: Boolean,
        label: String
    ): Long {
        val now = System.currentTimeMillis()
        return dao.insert(
            ManualCashEntity(
                dayStartMs = DateTimeUtils.startOfDay(dayMs),
                amount = amount.coerceAtLeast(0L),
                isIncome = isIncome,
                isDone = isDone,
                label = label.trim(),
                createdAt = now,
                updatedAt = now
            )
        )
    }

    suspend fun setAmount(id: Long, amount: Long) = writeMutex.withLock {
        val e = dao.findById(id) ?: return@withLock
        dao.update(e.copy(amount = amount.coerceAtLeast(0L), updatedAt = System.currentTimeMillis()))
    }

    /** 예정 ↔ 완료 토글. */
    suspend fun setDone(id: Long, done: Boolean) = writeMutex.withLock {
        val e = dao.findById(id) ?: return@withLock
        dao.update(e.copy(isDone = done, updatedAt = System.currentTimeMillis()))
    }

    suspend fun delete(id: Long) = dao.deleteById(id)
}
