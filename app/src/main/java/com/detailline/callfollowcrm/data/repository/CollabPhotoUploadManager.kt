package com.detailline.callfollowcrm.data.repository

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.detailline.callfollowcrm.R
import com.detailline.callfollowcrm.ai.SharedSiteRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 📸 협업 현장 증거사진 업로드 — **앱 수명 매니저**(화면/ViewModel 보다 오래 산다).
 *
 *   왜: 업로드를 화면 scope(rememberCoroutineScope)·viewModelScope 에 두면 **협업 현장 화면을 나가는 순간
 *       ViewModel 이 소멸**돼 끊긴다 (2026-11-03 사장님 "그 페이지 나가니까 업로드도 안 되더라").
 *       → applicationScope 에서 돌리고, **진행 알림**(ongoing)으로 "올리는 중"을 남긴다(삼성 갤러리·카톡처럼).
 *
 *   ⚠️ **포그라운드 서비스는 안 쓴다** — FGS 권한이 Play 업로드 검문을 깨운다(매니페스트 2026-08-31, #142).
 *      대신 앱 수명 코루틴 + 일반 진행 알림. 앱 프로세스가 완전히 죽으면 멈추지만, **화면 이동엔 끄떡없다.**
 *
 *   ⚠️ OWNER 현장사진(OwnerPhotoUploadManager·우체통 SitePhotoHandler)과는 **다른 기능**이다 —
 *      서버 엔드포인트가 다르다(협업 공유현장 vs 내 현장). 함께 바뀌지 않으므로 합치지 않는다(§12-I).
 */
class CollabPhotoUploadManager(
    private val appContext: Context,
    private val repo: SharedSiteRepository,
    private val scope: CoroutineScope
) {
    /** 올리는 중인 사진 한 장 — 고른 즉시 썸네일로 보이고, 올라가면 목록에서 빠진다(실패면 failed 로 남는다). */
    data class Pending(val shareId: String, val id: Int, val uri: Uri, val failed: Boolean = false)

    private val _pending = MutableStateFlow<List<Pending>>(emptyList())
    /** 모든 현장의 올리는 중 사진. 화면은 자기 현장 것(shareId)만 걸러 쓴다. */
    val pending: StateFlow<List<Pending>> = _pending.asStateFlow()

    /** 한 장이 서버에 올라갔다는 신호(shareId). 그 현장이 열려 있으면 화면이 받아 사진을 새로고침한다. */
    private val _uploaded = MutableSharedFlow<String>(extraBufferCapacity = 32)
    val uploaded: SharedFlow<String> = _uploaded.asSharedFlow()

    private val mutex = Mutex()   // 배치끼리 썸네일 목록이 꼬이지 않게 직렬화(§13③)
    private var nextId = 0

    /**
     * 사진 여러 장을 올린다. encode 는 Context 가 필요해 화면이 준 람다로 받는다(여긴 Context 가 있지만
     * Uri→base64 변환은 호출부의 ImageEncoder 를 그대로 쓰도록 람다로 넘긴다).
     */
    fun enqueue(shareId: String, ownerPhone: String, uris: List<Uri>, encode: suspend (Uri) -> String?) {
        if (shareId.isBlank() || ownerPhone.isBlank() || uris.isEmpty()) return
        scope.launch { mutex.withLock { runBatch(shareId, ownerPhone, uris, encode) } }
    }

    private suspend fun runBatch(
        shareId: String, ownerPhone: String, uris: List<Uri>, encode: suspend (Uri) -> String?
    ) {
        // 이번에 다시 골랐으니 이 현장의 지난 '못 올림' 썸네일은 치운다.
        _pending.value = _pending.value.filterNot { it.shareId == shareId && it.failed }
        val items = uris.map { Pending(shareId, nextId++, it) }
        _pending.value = _pending.value + items
        val total = items.size
        var done = 0
        var failed = 0
        notifyProgress(0, total)
        for (item in items) {
            val b64 = withContext(Dispatchers.IO) { runCatching { encode(item.uri) }.getOrNull() }
            val ok = !b64.isNullOrBlank() && repo.uploadPhoto(shareId, ownerPhone, b64).isSuccess
            if (ok) {
                done++
                _pending.value = _pending.value.filterNot { it.id == item.id }
                _uploaded.tryEmit(shareId)
            } else {
                failed++
                // 실패한 칸은 "못 올림"으로 남긴다(조용히 안 삼킴 §13①). 다음에 다시 고르면 지워진다.
                _pending.value = _pending.value.map { if (it.id == item.id) it.copy(failed = true) else it }
            }
            notifyProgress(done + failed, total)
        }
        finish(done, failed, total)
    }

    // ── 진행 알림 (ongoing) ──

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = appContext.getSystemService(NotificationManager::class.java) ?: return
            if (nm.getNotificationChannel(CHANNEL) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL, "협업 사진 올리기", NotificationManager.IMPORTANCE_LOW).apply {
                        setShowBadge(false); setSound(null, null); enableVibration(false)
                    }
                )
            }
        }
    }

    private fun notifyProgress(progress: Int, total: Int) {
        ensureChannel()
        val n = NotificationCompat.Builder(appContext, CHANNEL)
            .setSmallIcon(R.drawable.ic_noti_collab)
            .setContentTitle("협업 사진 올리는 중")
            .setContentText("$progress / $total")
            .setProgress(total, progress, false)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .build()
        runCatching { NotificationManagerCompat.from(appContext).notify(NOTIF_ID, n) }
    }

    private fun finish(done: Int, failed: Int, total: Int) {
        val nm = NotificationManagerCompat.from(appContext)
        runCatching { nm.cancel(NOTIF_ID) }
        // 다 올라갔으면 조용히 끝(갤러리처럼 알림이 사라지는 게 '완료'). 일부 실패면 알려준다.
        if (failed > 0) {
            ensureChannel()
            val n = NotificationCompat.Builder(appContext, CHANNEL)
                .setSmallIcon(R.drawable.ic_noti_collab)
                .setContentTitle("사진 일부를 못 올렸어요")
                .setContentText("$done / $total 만 올라갔어요 — 앱에서 다시 올려주세요")
                .setAutoCancel(true)
                .setSilent(true)
                .build()
            runCatching { nm.notify(NOTIF_ID + 1, n) }
        }
    }

    private companion object {
        const val CHANNEL = "collab_photo_upload"
        const val NOTIF_ID = 90210
    }
}
