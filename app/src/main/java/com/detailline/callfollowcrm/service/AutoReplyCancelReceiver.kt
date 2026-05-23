package com.detailline.callfollowcrm.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 자동 응답 알림의 "취소" 버튼을 누르면 호출되는 receiver.
 * AutoReplyScheduler 의 pending 맵에서 해당 callRecordId 를 cancelled 로 마크.
 */
class AutoReplyCancelReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_CANCEL) return
        val callRecordId = intent.getLongExtra(EXTRA_CALL_RECORD_ID, -1L)
        if (callRecordId <= 0) return
        AutoReplyScheduler.cancel(callRecordId)
    }

    companion object {
        const val ACTION_CANCEL = "com.detailline.callfollowcrm.AUTO_REPLY_CANCEL"
        const val EXTRA_CALL_RECORD_ID = "callRecordId"
    }
}
