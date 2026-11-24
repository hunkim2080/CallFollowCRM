package com.detailline.callfollowcrm.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.detailline.callfollowcrm.CallFollowCrmApplication
import java.util.concurrent.TimeUnit

/**
 * 📸 협업 현장 사진 **묶음 알림(5분 디바운스)** — 사진이 올 때마다 바로 울리지 않고,
 *   **더 안 올라온 지 5분**이 지나면 그때 한 번만 상태바 알림. (2026-11-24 사장님:
 *   "한 장씩 울려서 눌러도 한 장뿐 — 다 올라온 뒤 한 번에 '사진 N장'으로.")
 *
 *   흐름: FCM(collab_photo)마다 [NotificationHelper.onCollabPhotoArrived] → [schedule] 호출.
 *     같은 현장이면 `ExistingWorkPolicy.REPLACE` 로 **타이머를 리셋**(계속 올라오면 안 울림).
 *     5분 조용하면 [doWork] 가 그 현장의 쌓인 수(센터 기준 [currentCollabPhotoCount])를 읽어
 *     [NotificationHelper.showCollabPhotoPush] 를 **딱 한 번** 부른다.
 *
 *   센터 한 줄(「사진 N장」)은 도착 즉시 onCollabPhotoArrived 가 조용히 기록한다 — 여긴 **소리(상태바)만** 담당.
 */
class CollabPhotoNotifyWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? CallFollowCrmApplication ?: return Result.success()
        val siteId = inputData.getString(KEY_SITE_ID).orEmpty()
        if (siteId.isBlank()) return Result.success()
        val who = inputData.getString(KEY_WHO).orEmpty().ifBlank { "협업 사장" }
        val where = inputData.getString(KEY_WHERE).orEmpty().ifBlank { "협업 현장" }
        // 묶음 수 — 센터(도착 즉시 묶은 「사진 N장」)와 같은 기준으로 세어 알림 숫자와 목록 숫자가 어긋나지 않게.
        val count = runCatching {
            app.container.appNotificationRepository.currentCollabPhotoCount(siteId)
        }.getOrDefault(0)
        // 대표 사진 1장(펼치면 크게)은 Phase B(서버 썸네일 문) 에서 붙인다 — 지금은 글자만.
        NotificationHelper.showCollabPhotoPush(applicationContext, siteId, who, where, count)
        return Result.success()
    }

    companion object {
        private const val KEY_SITE_ID = "site_id"
        private const val KEY_WHO = "who"
        private const val KEY_WHERE = "where"

        /** 사진이 멎었다고 보는 조용한 시간 — 이 안에 또 오면 알림을 미룬다(타이머 리셋). (2026-11-24 사장님) */
        private const val QUIET_MINUTES = 5L

        /** FCM(collab_photo) 도착마다 호출(진입부) — ① 센터에 「사진 N장」 **즉시 조용히** 묶어 기록
         *   ② 상태바 알림은 **5분 디바운스**(또 오면 리셋, 멎으면 한 번). 한 장씩 울려 눌러도 한 장뿐이던
         *   문제 fix — 다 올라온 뒤 "사진 N장" 한 번. 렌더는 [NotificationHelper.showCollabPhotoPush]. (2026-11-24 사장님) */
        fun onPhotoArrived(context: Context, siteId: String, uploaderName: String, siteTitle: String) {
            if (siteId.isBlank()) return
            val who = uploaderName.ifBlank { "협업 사장" }
            val where = siteTitle.ifBlank { "협업 현장" }
            NotificationLog.bumpCollabPhoto(who, where, siteId) { }   // 센터 한 줄(조용) — 소리는 5분 뒤 푸시가
            schedule(context, siteId, who, where)                     // 상태바 알림은 5분 디바운스
        }

        /** FCM 사진 도착마다 호출 — 같은 현장이면 5분 타이머를 리셋한다(REPLACE). */
        fun schedule(context: Context, siteId: String, who: String, where: String) {
            if (siteId.isBlank()) return
            val req = OneTimeWorkRequestBuilder<CollabPhotoNotifyWorker>()
                .setInitialDelay(QUIET_MINUTES, TimeUnit.MINUTES)
                .setInputData(
                    workDataOf(KEY_SITE_ID to siteId, KEY_WHO to who, KEY_WHERE to where)
                )
                .build()
            runCatching {
                WorkManager.getInstance(context).enqueueUniqueWork(
                    uniqueName(siteId),
                    ExistingWorkPolicy.REPLACE,   // 같은 현장 사진이 또 오면 5분 타이머 리셋
                    req
                )
            }
        }

        private fun uniqueName(siteId: String) = "collab_photo_notify_$siteId"
    }
}
