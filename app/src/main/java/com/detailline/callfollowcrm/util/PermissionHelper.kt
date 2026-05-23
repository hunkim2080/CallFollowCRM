package com.detailline.callfollowcrm.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat

object PermissionHelper {

    /** v1 필수: 통화 종료 감지/번호 조회/알림 표시. SEND_SMS는 절대 포함하지 않음. */
    fun requiredPermissions(): Array<String> {
        val list = mutableListOf(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CALL_LOG
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list += Manifest.permission.POST_NOTIFICATIONS
        }
        return list.toTypedArray()
    }

    fun isGranted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun hasPhoneState(context: Context) = isGranted(context, Manifest.permission.READ_PHONE_STATE)
    fun hasCallLog(context: Context) = isGranted(context, Manifest.permission.READ_CALL_LOG)
    fun hasNotifications(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            isGranted(context, Manifest.permission.POST_NOTIFICATIONS)
        else true

    fun allMissingNonNotification(context: Context): List<String> = listOf(
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.READ_CALL_LOG
    ).filter { !isGranted(context, it) }

    /**
     * SYSTEM_ALERT_WINDOW (다른 앱 위에 표시) 권한 보유 여부.
     * 일반 런타임 권한이 아니라 사용자가 설정 화면에서 직접 토글해야 함.
     */
    fun hasOverlay(context: Context): Boolean = Settings.canDrawOverlays(context)
}
