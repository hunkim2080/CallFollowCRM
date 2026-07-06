package com.detailline.callfollowcrm.data.repository

import com.detailline.callfollowcrm.data.local.dao.IssuedDocDao
import com.detailline.callfollowcrm.data.local.entity.IssuedDocEntity
import kotlinx.coroutines.flow.Flow

/**
 * 발행 문서 이력 저장소 (2026-07-07) — 견적서/시공접수서를 발행하는 순간 record(),
 *   고객상세·채팅이 observe() 로 구독. [IssuedDocEntity]
 */
class IssuedDocRepository(private val dao: IssuedDocDao) {

    fun observeByCustomer(customerId: Long): Flow<List<IssuedDocEntity>> =
        dao.observeByCustomer(customerId)

    fun observeBySuffix(phoneSuffix: String): Flow<List<IssuedDocEntity>> =
        dao.observeBySuffix(phoneSuffix)

    suspend fun record(doc: IssuedDocEntity): Long = dao.insert(doc)

    suspend fun delete(id: Long) = dao.delete(id)
}
