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

    /** 🔔 딱지 숫자 = 쌓인 알림 수(탭하면 줄어듦). */
    @Query("SELECT COUNT(*) FROM app_notifications")
    fun observeCount(): Flow<Int>

    /** 탭한 한 줄 삭제(사장님: "클릭 한번 하면 없어져야지"). */
    @Query("DELETE FROM app_notifications WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** [모두 지우기]. */
    @Query("DELETE FROM app_notifications")
    suspend fun clearAll()

    /** 자동삭제는 안 하지만(사장님) 테이블이 무한정 커지지 않게 상한만 — 최신 keep 개만 남기고 옛것 정리. */
    @Query("DELETE FROM app_notifications WHERE id NOT IN (SELECT id FROM app_notifications ORDER BY createdAt DESC LIMIT :keep)")
    suspend fun trimTo(keep: Int)
}
