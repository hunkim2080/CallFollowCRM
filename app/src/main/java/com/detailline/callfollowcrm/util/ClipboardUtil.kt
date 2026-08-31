package com.detailline.callfollowcrm.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast

/**
 * 한 탭 복사 — 클립보드에 넣고 짧게 토스트. (2026-09-01 사장님 '원탭 복사')
 * Android 13+ 는 시스템이 자체 '복사됨' 미리보기를 띄우므로 토스트 생략(중복 방지).
 */
fun Context.copyToClip(label: String, text: String) {
    val t = text.trim()
    if (t.isBlank()) return
    val cm = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    cm.setPrimaryClip(ClipData.newPlainText(label, t))
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(this, "$label 복사됐어요", Toast.LENGTH_SHORT).show()
    }
}
