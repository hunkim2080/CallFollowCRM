package com.detailline.callfollowcrm.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.detailline.callfollowcrm.CallFollowCrmApplication
import com.detailline.callfollowcrm.MainActivity
import com.detailline.callfollowcrm.R
import com.detailline.callfollowcrm.data.local.AppDatabase
import com.detailline.callfollowcrm.data.local.entity.ScheduledSmsEntity
import com.detailline.callfollowcrm.domain.model.MessageStatus
import com.detailline.callfollowcrm.util.DefaultSmsAppHelper
import com.detailline.callfollowcrm.util.SmsSender

/**
 * 🕐 예약 문자 **실제 발송** — 예약 시각에 [ScheduledSmsReceiver] 가, 밀린 건 [ScheduledSmsScheduler.rearmAll] 이 부른다.
 *   발송 경로는 사장님이 직접 보낼 때와 **같은 공용**([SmsSender]) 을 쓴다 — 두 벌 금지(§12).
 *
 *   - **글**: `SmsSender.sendDirect` — 기본 문자앱 아니어도 보내진다. 예약의 믿는 경로.
 *   - **사진**: `SmsSender.sendMms` — **기본 문자앱일 때만**. 아니면 글만 보내고 "사진 못 갔어요" 알림(§13① 조용히 안 삼킴).
 */
object ScheduledSmsSender {

    private const val TAG = "ScheduledSmsSender"
    private const val CH_DONE = "scheduled_sms_done"   // 조용한 '보냈어요'
    private const val CH_FAIL = "scheduled_sms_fail"   // '못 갔어요' 알림

    /** 한 건을 지금 보낸다. 이미 처리/취소된 건 조용히 건너뜀. (IO 스레드에서 호출) */
    suspend fun sendNow(context: Context, rowId: Long) {
        val app = context.applicationContext
        val dao = AppDatabase.getInstance(app).scheduledSmsDao()
        val row = dao.getById(rowId) ?: return
        if (row.status != ScheduledSmsEntity.STATUS_PENDING) return

        if (!SmsSender.hasPermission(app)) {
            dao.markResult(rowId, ScheduledSmsEntity.STATUS_FAILED, "문자 권한이 없어 못 보냈어요")
            notifyFail(app, row, "예약 문자를 못 보냈어요", "문자 보내기 권한을 켜주세요")
            return
        }

        val photos = row.photoUriList().mapNotNull { runCatching { Uri.parse(it) }.getOrNull() }
        var textOk: Boolean
        var photoNote: String? = null

        if (photos.isEmpty()) {
            textOk = SmsSender.sendDirect(app, row.phoneNumber, row.body, persistLocalOnFail = true)
        } else if (DefaultSmsAppHelper.isCurrentDefault(app)) {
            // 기본앱 → MMS(글+사진 한 번에). 요청 접수만 ok — 실제 전달 실패는 MmsSentReceiver 가 따로 알림.
            val mmsOk = SmsSender.sendMms(app, row.phoneNumber, row.body, photos)
            if (mmsOk) {
                textOk = true
            } else {
                // MMS 요청 자체 실패 → 글만이라도 보낸다.
                textOk = SmsSender.sendDirect(app, row.phoneNumber, row.body, persistLocalOnFail = true)
                photoNote = "사진은 못 갔어요"
            }
        } else {
            // 기본 문자앱이 아니면 사진 자동발송 불가 → 글만 보내고 알린다.
            textOk = SmsSender.sendDirect(app, row.phoneNumber, row.body, persistLocalOnFail = true)
            photoNote = "사진은 자동으로 못 보냈어요"
        }

        // 보낸 기록 — 사장님이 직접 보낸 것과 같은 이력에 남긴다.
        runCatching {
            (app as? CallFollowCrmApplication)?.container?.messageHistoryRepository?.recordAutoSend(
                phoneNumber = row.phoneNumber,
                customerId = null,
                templateId = null,
                body = if (row.body.isBlank() && photos.isNotEmpty()) "사진 ${photos.size}장" else row.body,
                status = MessageStatus.INLINE_SENT
            )
        }.onFailure { Log.w(TAG, "recordAutoSend failed", it) }

        when {
            textOk && photoNote == null -> {
                dao.markResult(rowId, ScheduledSmsEntity.STATUS_SENT, null)
                notifyDone(app, row)
            }
            textOk && photoNote != null -> {
                // 글은 갔으니 보냄 처리하되, 사진 실패는 note + 알림으로 분명히 남긴다.
                dao.markResult(rowId, ScheduledSmsEntity.STATUS_SENT, photoNote)
                notifyFail(app, row, photoNote, "글은 보냈어요. 사진은 눌러서 보내주세요")
            }
            else -> {
                dao.markResult(rowId, ScheduledSmsEntity.STATUS_FAILED, "문자를 못 보냈어요")
                notifyFail(app, row, "예약 문자를 못 보냈어요", "눌러서 다시 보내주세요")
            }
        }
    }

    // ── 알림 ──────────────────────────────────────────────

    private fun openChatPending(context: Context, row: ScheduledSmsEntity, notifId: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = MainActivity.ACTION_CHAT
            putExtra(MainActivity.EXTRA_PHONE_NUMBER, row.phoneNumber)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context, notifId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val m = context.getSystemService(NotificationManager::class.java) ?: return
        if (m.getNotificationChannel(CH_DONE) == null) {
            m.createNotificationChannel(
                NotificationChannel(CH_DONE, "예약 문자 보냄", NotificationManager.IMPORTANCE_LOW)
            )
        }
        if (m.getNotificationChannel(CH_FAIL) == null) {
            m.createNotificationChannel(
                NotificationChannel(CH_FAIL, "예약 문자 알림", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
    }

    private fun post(context: Context, notifId: Int, channel: String, title: String, text: String, pending: PendingIntent) {
        ensureChannels(context)
        val n = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(notifId, n) }
    }

    private fun notifyDone(context: Context, row: ScheduledSmsEntity) {
        val notifId = 930000 + (row.id % 10000).toInt()
        val who = row.recipientName.ifBlank { row.phoneNumber }
        val preview = row.body.take(30).ifBlank { "사진" }
        post(context, notifId, CH_DONE, "예약 문자 보냈어요", "$who · $preview", openChatPending(context, row, notifId))
    }

    private fun notifyFail(context: Context, row: ScheduledSmsEntity, title: String, text: String) {
        val notifId = 931000 + (row.id % 10000).toInt()
        post(context, notifId, CH_FAIL, title, text, openChatPending(context, row, notifId))
    }
}
