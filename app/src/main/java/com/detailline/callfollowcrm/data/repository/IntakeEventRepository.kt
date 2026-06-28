package com.detailline.callfollowcrm.data.repository

import com.detailline.callfollowcrm.data.local.dao.IntakeEventDao
import com.detailline.callfollowcrm.data.local.entity.IntakeEventEntity
import kotlinx.coroutines.flow.Flow

/**
 * 시공접수서 제출 이벤트 저장소 (2026-06-05).
 *   IntakeSyncManager 가 새 제출 감지 시 record() 로 기록, ChatViewModel 이 observe() 로 구독.
 */
class IntakeEventRepository(private val dao: IntakeEventDao) {

    fun observe(phoneSuffix: String): Flow<List<IntakeEventEntity>> =
        dao.observeBySuffix(phoneSuffix)

    suspend fun record(event: IntakeEventEntity): Long = dao.insert(event)

    /** 사장님이 접수서를 확인하고 고객에게 확인 문자를 보낸 시각 기록. (2026-06-28) */
    suspend fun markConfirmed(token: String, ts: Long) = dao.markConfirmed(token, ts)
}
