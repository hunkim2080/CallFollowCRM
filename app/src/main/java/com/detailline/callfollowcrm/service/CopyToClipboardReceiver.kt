package com.detailline.callfollowcrm.service

import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast

/**
 * 알림 '복사' 액션 → 클립보드에 넣기. (2026-09-01 사장님 '인증번호 원탭 복사')
 * 문자 알림에 인증번호가 감지되면 [NotificationHelper.showIncomingSms] 가 이 리시버로 보내는 '복사' 버튼을 단다.
 */
class CopyToClipboardReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val text = intent.getStringExtra(EXTRA_TEXT)?.takeIf { it.isNotBlank() } ?: return
        val label = intent.getStringExtra(EXTRA_LABEL) ?: "복사"
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(context, "$label 복사됐어요", Toast.LENGTH_SHORT).show()
    }

    companion object {
        const val ACTION_COPY = "com.detailline.callfollowcrm.ACTION_COPY_CLIP"
        const val EXTRA_TEXT = "extra_copy_text"
        const val EXTRA_LABEL = "extra_copy_label"
    }
}
