package com.detailline.callfollowcrm.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.detailline.callfollowcrm.data.local.entity.AppNotificationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AppNotificationDao {

    @Insert
    suspend fun insert(e: AppNotificationEntity): Long

    /** 최신 위. 알림 센터 목록. */
    @Query("SELECT * FROM app_notifications ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<AppNotificationEntity>>

    /** 🔔 딱지 숫자 = **안 읽은 것만**(읽으면 줄어듦). 삭제가 아니라 읽음이라 목록엔 회색으로 남는다. (2026-10-13 사장님) */
    @Query("SELECT COUNT(*) FROM app_notifications WHERE readAt IS NULL")
    fun observeUnreadCount(): Flow<Int>

    /** 탭한 한 줄을 **읽음**으로(삭제 아님 — 회색으로 남는다). */
    @Query("UPDATE app_notifications SET readAt = :at WHERE id = :id AND readAt IS NULL")
    suspend fun markRead(id: Long, at: Long)

    /** [모두 읽기] — 안 읽은 것 전부 읽음으로. 삭제 아님. */
    @Query("UPDATE app_notifications SET readAt = :at WHERE readAt IS NULL")
    suspend fun markAllRead(at: Long)

    /** 협업만 남기는 1회 정리 — 센터가 '협업 현장 소식만'으로 바뀌어서 옛 문자·부재중 기록은 뺀다. (2026-10-13) */
    @Query("DELETE FROM app_notifications WHERE type NOT LIKE 'collab%'")
    suspend fun deleteNonCollab()

    /** 자동삭제는 안 하지만(사장님) 테이블이 무한정 커지지 않게 상한만 — 최신 keep 개만 남기고 옛것 정리. */
    @Query("DELETE FROM app_notifications WHERE id NOT IN (SELECT id FROM app_notifications ORDER BY createdAt DESC LIMIT :keep)")
    suspend fun trimTo(keep: Int)
}
