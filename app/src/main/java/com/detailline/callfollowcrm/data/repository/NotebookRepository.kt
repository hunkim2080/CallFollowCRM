package com.detailline.callfollowcrm.data.repository

import com.detailline.callfollowcrm.data.local.dao.NotebookContactDao
import com.detailline.callfollowcrm.data.local.entity.NotebookContactEntity
import kotlinx.coroutines.flow.Flow

/** 수첩 (일당/거래처) — 정산/일정과 별개의 사장님 인맥 수첩. */
class NotebookRepository(private val dao: NotebookContactDao) {

    fun observeWorkers(): Flow<List<NotebookContactEntity>> =
        dao.observeByKind(NotebookContactEntity.KIND_WORKER)

    fun observeVendors(): Flow<List<NotebookContactEntity>> =
        dao.observeByKind(NotebookContactEntity.KIND_VENDOR)

    suspend fun add(
        kind: String, name: String, phone: String, tag: String, memo: String,
        wage: Long? = null, wageType: String = NotebookContactEntity.WAGE_DAILY
    ): Long {
        val now = System.currentTimeMillis()
        return dao.insert(
            NotebookContactEntity(
                kind = kind, name = name.trim(), phone = phone.trim(),
                tag = tag.trim(), memo = memo.trim(), wage = wage, wageType = wageType,
                createdAt = now, updatedAt = now
            )
        )
    }

    suspend fun update(
        id: Long, name: String, phone: String, tag: String, memo: String,
        wage: Long? = null, wageType: String = NotebookContactEntity.WAGE_DAILY
    ) {
        val e = dao.findById(id) ?: return
        dao.update(
            e.copy(name = name.trim(), phone = phone.trim(), tag = tag.trim(),
                memo = memo.trim(), wage = wage, wageType = wageType,
                updatedAt = System.currentTimeMillis())
        )
    }

    suspend fun delete(id: Long) = dao.deleteById(id)
}
