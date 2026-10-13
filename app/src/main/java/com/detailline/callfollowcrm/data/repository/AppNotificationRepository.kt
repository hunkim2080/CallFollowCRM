package com.detailline.callfollowcrm.data.repository

import com.detailline.callfollowcrm.data.local.dao.AppNotificationDao
import com.detailline.callfollowcrm.data.local.entity.AppNotificationEntity
import kotlinx.coroutines.flow.Flow

/** 🔔 인앱 알림 센터 저장소. 알림 센터 화면·🔔 딱지가 읽고, [[NotificationLog]] 가 쓴다. */
class AppNotificationRepository(private val dao: AppNotificationDao) {

    fun observeAll(): Flow<List<AppNotificationEntity>> = dao.observeAll()

    /** 🔔 딱지 숫자 = 안 읽은 것만(읽으면 줄어듦). */
    fun observeUnreadCount(): Flow<Int> = dao.observeUnreadCount()

    suspend fun add(e: AppNotificationEntity): Long {
        val id = dao.insert(e)
        // 자동삭제는 안 하지만 테이블 무한 증가 방지 상한만. (사장님: 시간 지난다고 지우지 말 것)
        runCatching { dao.trimTo(KEEP_MAX) }
        return id
    }

    /** 탭한 한 줄 → 읽음(삭제 아님 — 회색으로 남는다). (2026-10-13 사장님) */
    suspend fun markRead(id: Long) = dao.markRead(id, System.currentTimeMillis())

    /** [모두 읽기]. */
    suspend fun markAllRead() = dao.markAllRead(System.currentTimeMillis())

    companion object {
        private const val KEEP_MAX = 500
    }
}
