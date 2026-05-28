package com.detailline.callfollowcrm.util

import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony

/**
 * 2026-05-29 Phase A 1단계 — Default SMS 앱 전환/조회 헬퍼.
 *
 * **현재 (1단계) Settings 카드는 disabled (회색) 표시 — 사용자는 호출 못 함.**
 * 2단계 (MMS 본격 구현) 끝난 후 토글 enable → 사장님이 launch 가능.
 *
 * 사용처 (2단계 이후):
 *   - isCurrentDefault(context) → 토글 ON/OFF 상태
 *   - createRequestIntent(activity) → 다이얼로그 launch (사용자가 "RING-GO 를 기본 메시지 앱으로?" 동의)
 *   - createReleaseIntent(activity) → 토글 OFF 시 시스템이 "다른 앱으로?" 다이얼로그 띄움
 *
 * API:
 *   - Android 10+ (Q) : RoleManager.ROLE_SMS — 표준 API. 다이얼로그 → 사용자 선택.
 *   - Android 9 이하 : Telephony.Sms.getDefaultSmsPackage + ACTION_CHANGE_DEFAULT — 옛 API.
 *                       minSdk 가 9 이하라면 분기 필요. 1단계는 양쪽 다 코드 박아둠.
 */
object DefaultSmsAppHelper {

    /** 현재 RING-GO 가 기본 SMS 앱인지. */
    fun isCurrentDefault(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val rm = context.getSystemService(RoleManager::class.java) ?: return false
            return rm.isRoleHeld(RoleManager.ROLE_SMS)
        }
        val defPkg = Telephony.Sms.getDefaultSmsPackage(context) ?: return false
        return defPkg == context.packageName
    }

    /** "RING-GO 를 기본 메시지 앱으로?" 다이얼로그 Intent. activity.startActivityForResult 로 launch. */
    fun createRequestIntent(activity: Activity): Intent? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val rm = activity.getSystemService(RoleManager::class.java) ?: return null
            if (!rm.isRoleAvailable(RoleManager.ROLE_SMS)) return null
            if (rm.isRoleHeld(RoleManager.ROLE_SMS)) return null
            return rm.createRequestRoleIntent(RoleManager.ROLE_SMS)
        }
        @Suppress("DEPRECATION")
        return Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT).apply {
            putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, activity.packageName)
        }
    }

    /**
     * Default 해제 — Android 는 직접 해제 API 없음. "다른 앱을 기본으로" 다이얼로그를 띄워서
     * 사용자가 갤메시지 등 선택하면 자동으로 RING-GO 가 default 에서 빠짐.
     *
     * Android 10+ : Settings 의 default-apps 화면으로 보냄 (RoleManager 가 빠지는 API 미제공).
     * Android 9 이하 : 옛 ACTION_CHANGE_DEFAULT 다이얼로그 (다른 pkg 지정).
     */
    fun createReleaseIntent(activity: Activity): Intent {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return Intent(android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
        }
        @Suppress("DEPRECATION")
        return Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
    }
}
