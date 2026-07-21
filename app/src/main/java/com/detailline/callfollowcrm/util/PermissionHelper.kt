package com.detailline.callfollowcrm.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat

object PermissionHelper {

    /**
     * v1 필수: 통화 종료 감지 / 번호 조회 / 알림 표시 / 주고받은 문자 표시.
     * SEND_SMS 는 절대 포함하지 않음 (사장님 명시 발송 액션에서만 별도 요청).
     *
     * 2026-05-24: READ_SMS + RECEIVE_SMS 도 초기 설치 시 무조건 받음 (사장님 결정).
     *   대화/카드 표시의 핵심 데이터라 토글로 끄는 게 의미 없음.
     */
    fun requiredPermissions(): Array<String> {
        val list = mutableListOf(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.READ_SMS,
            Manifest.permission.RECEIVE_SMS
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list += Manifest.permission.POST_NOTIFICATIONS
        }
        // READ_CONTACTS — 저장된 연락처 이름 반영(선택). 온보딩 배치에 포함해 신규 사용자에게 함께 요청.
        //   거부해도 앱 정상(번호/자체저장 이름). 그래서 allMissingNonNotification(차단 조건)엔 넣지 않음. (2026-07-21)
        list += Manifest.permission.READ_CONTACTS
        return list.toTypedArray()
    }

    /** 저장된 연락처 이름 반영용 READ_CONTACTS 보유 여부(선택 권한). */
    fun hasContacts(context: Context) = isGranted(context, Manifest.permission.READ_CONTACTS)

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
