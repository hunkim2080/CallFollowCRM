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

    /**
     * 🤝 협업 현장 **사진** 알림 — 같은 현장의 **안 읽은** 사진 알림이 최근(창 안)에 있으면
     *   새 줄을 만들지 않고 **「사진 N장」으로 개수만 올려** 한 줄로 묶는다. (2026-10-15 사장님:
     *   "사진 올리면 총 몇 장 올라왔습니다로 떠야" — 장당 하나씩 알림이 도배되던 걸 묶음.)
     *   @return 이 현장에 쌓인 사진 수(= 보여줄 N). 시스템 알림에도 같은 N 을 쓴다.
     */
    suspend fun bumpCollabPhoto(who: String, where: String, shareId: String): Int {
        val now = System.currentTimeMillis()
        val title = "협업 현장 새 사진"
        val existing = dao.latestUnreadCollab(title, shareId, now - BATCH_WINDOW_MS)
        val newCount = (if (existing != null) parsePhotoCount(existing.body) else 0) + 1
        // 1장이면 숫자 없이(자연스럽게), 2장부터 「사진 N장」. 시스템 알림(showCollabPhoto)과 같은 문구.
        val body = if (newCount <= 1) "${who}님이 '${where}'에 사진을 올렸어요"
                   else "${who}님이 '${where}'에 사진 ${newCount}장을 올렸어요"
        if (existing != null) {
            dao.bumpBody(existing.id, body, now)
        } else {
            dao.insert(
                AppNotificationEntity(
                    type = "collab", title = title, body = body,
                    navTarget = "collab", navShareId = shareId, createdAt = now
                )
            )
            runCatching { dao.trimTo(KEEP_MAX) }
        }
        return newCount
    }

    /** 본문에서 「사진 N장」의 N 을 읽는다. 옛 줄(개수 없음)은 1 로 본다. */
    private fun parsePhotoCount(body: String?): Int =
        body?.let { Regex("사진 (\\d+)장").find(it)?.groupValues?.getOrNull(1)?.toIntOrNull() } ?: 1

    companion object {
        private const val KEEP_MAX = 500

        /** 같은 현장 사진을 한 줄로 묶는 시간 창 — 이 안에 또 오면 개수만 올린다(12시간). */
        private const val BATCH_WINDOW_MS = 12L * 60 * 60 * 1000
    }
}
