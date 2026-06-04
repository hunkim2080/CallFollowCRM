package com.detailline.callfollowcrm.data.repository

import com.detailline.callfollowcrm.data.local.dao.TeamAssignmentDao
import com.detailline.callfollowcrm.data.local.entity.TeamAssignmentEntity
import kotlinx.coroutines.flow.Flow

/**
 * 팀원 현장 배정 저장소 (2026-06-05). 일정 화면 배정 줄 ↔ 서버 schedule-snapshot push 사이.
 */
class TeamAssignmentRepository(private val dao: TeamAssignmentDao) {

    fun observeAll(): Flow<List<TeamAssignmentEntity>> = dao.observeAll()

    suspend fun forCustomer(customerId: Long): List<TeamAssignmentEntity> = dao.forCustomer(customerId)

    suspend fun forMember(memberId: String): List<TeamAssignmentEntity> = dao.forMember(memberId)

    /** 한 고객(현장)의 팀원 배정을 통째로 교체. */
    suspend fun replaceForCustomer(
        customerId: Long,
        dayStartMs: Long,
        members: List<Pair<String, String>>,   // (memberId, memberName)
        nowMs: Long
    ) {
        dao.deleteForCustomer(customerId)
        if (members.isNotEmpty()) {
            dao.insertAll(members.map { (id, name) ->
                TeamAssignmentEntity(
                    memberId = id, memberName = name,
                    customerId = customerId, dayStartMs = dayStartMs, createdAt = nowMs
                )
            })
        }
    }
}
