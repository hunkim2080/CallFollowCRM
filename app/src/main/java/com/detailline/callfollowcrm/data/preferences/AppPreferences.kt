package com.detailline.callfollowcrm.data.preferences

import android.content.Context
import android.content.SharedPreferences

/**
 * 간단한 키-값 영속화. v1 에서는 DataStore 도입 부담을 피하고 SharedPreferences 직접 사용.
 */
class AppPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("call_follow_crm", Context.MODE_PRIVATE)

    /** 에이닷 통화녹음 폴더(TPhoneCallRecords) URI. 사용자가 OpenDocumentTree 로 한 번 선택. */
    var adotRecordingFolderUri: String?
        get() = prefs.getString(KEY_ADOT_FOLDER_URI, null)
        set(value) = prefs.edit().putString(KEY_ADOT_FOLDER_URI, value).apply()

    /** 마지막 자동 스캔 시각. 너무 자주 돌지 않게 throttle 용. */
    var lastScanAt: Long
        get() = prefs.getLong(KEY_LAST_SCAN_AT, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_SCAN_AT, value).apply()

    /**
     * "받은 문자 표시" 기능 토글. 사용자가 Settings 에서 켜야 READ_SMS 권한 요청이 뜬다.
     * 끄면 권한 보유 여부와 무관하게 CustomerDetail 에서 받은 문자를 조회하지 않는다.
     */
    var receivedSmsEnabled: Boolean
        get() = prefs.getBoolean(KEY_RECEIVED_SMS_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_RECEIVED_SMS_ENABLED, value).apply()

    /**
     * "처음 연락온 고객 자동 응답 문자" 토글. 사장님 명시 허용 + SEND_SMS 권한 후에만 동작.
     * 켜져 있고 권한도 있으면 첫 통화 종료 후 [firstReplyIncomingTemplateId] 또는
     * [firstReplyMissedTemplateId] 의 본문이 10초 카운트다운 뒤 자동 발송.
     */
    var autoFirstReplyEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_FIRST_REPLY, false)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_FIRST_REPLY, value).apply()

    /** 수신 통화 첫 응대용 템플릿 ID. -1L = 미지정(해당 케이스 자동 발송 X). */
    var firstReplyIncomingTemplateId: Long
        get() = prefs.getLong(KEY_FIRST_REPLY_INCOMING_TPL, -1L)
        set(value) = prefs.edit().putLong(KEY_FIRST_REPLY_INCOMING_TPL, value).apply()

    /** 부재중 통화 첫 응대용 템플릿 ID. -1L = 미지정. */
    var firstReplyMissedTemplateId: Long
        get() = prefs.getLong(KEY_FIRST_REPLY_MISSED_TPL, -1L)
        set(value) = prefs.edit().putLong(KEY_FIRST_REPLY_MISSED_TPL, value).apply()

    /**
     * 후속 처리 알림 (두 번째 통화부터)의 빠른 액션 버튼 3개에 표시할 템플릿 ID.
     * -1L = 해당 슬롯 사용 안 함 (그 자리 액션 버튼이 안 뜸).
     * 알림에 액션 버튼은 최대 3개 — Android 시스템 제약.
     */
    var quickActionTemplateId1: Long
        get() = prefs.getLong(KEY_QUICK_ACTION_TPL_1, -1L)
        set(value) = prefs.edit().putLong(KEY_QUICK_ACTION_TPL_1, value).apply()

    var quickActionTemplateId2: Long
        get() = prefs.getLong(KEY_QUICK_ACTION_TPL_2, -1L)
        set(value) = prefs.edit().putLong(KEY_QUICK_ACTION_TPL_2, value).apply()

    var quickActionTemplateId3: Long
        get() = prefs.getLong(KEY_QUICK_ACTION_TPL_3, -1L)
        set(value) = prefs.edit().putLong(KEY_QUICK_ACTION_TPL_3, value).apply()

    /**
     * 수신 SMS 가 오면 RING-GO 자체 알림 표시 (갤메시지 대체 시도).
     * 기본 ON — 사장님 결정 2026-05-25. 사장님이 갤메시지 알림은 시스템 설정에서 따로 끔.
     */
    var incomingSmsNotifyEnabled: Boolean
        get() = prefs.getBoolean(KEY_INCOMING_SMS_NOTIFY, true)
        set(value) = prefs.edit().putBoolean(KEY_INCOMING_SMS_NOTIFY, value).apply()

    companion object {
        private const val KEY_ADOT_FOLDER_URI = "adot_folder_uri"
        private const val KEY_LAST_SCAN_AT = "last_scan_at"
        private const val KEY_RECEIVED_SMS_ENABLED = "received_sms_enabled"
        private const val KEY_AUTO_FIRST_REPLY = "auto_first_reply_enabled"
        private const val KEY_FIRST_REPLY_INCOMING_TPL = "first_reply_incoming_tpl_id"
        private const val KEY_FIRST_REPLY_MISSED_TPL = "first_reply_missed_tpl_id"
        private const val KEY_QUICK_ACTION_TPL_1 = "quick_action_tpl_id_1"
        private const val KEY_QUICK_ACTION_TPL_2 = "quick_action_tpl_id_2"
        private const val KEY_QUICK_ACTION_TPL_3 = "quick_action_tpl_id_3"
        private const val KEY_INCOMING_SMS_NOTIFY = "incoming_sms_notify_enabled"
    }
}
