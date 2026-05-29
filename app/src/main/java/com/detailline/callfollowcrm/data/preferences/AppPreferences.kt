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

    /**
     * 기본 네비 앱 (카드 펼침 [📍 길찾기] 가 사용할 외부 앱).
     * NavApp.key 문자열 저장. null = 아직 미선택 (첫 길찾기 탭 시 다이얼로그 띄움).
     * 사장님 결정 2026-05-27: 사용자마다 손에 익은 네비가 달라서 선택 가능하게.
     */
    var defaultNavAppKey: String?
        get() = prefs.getString(KEY_DEFAULT_NAV_APP, null)
        set(value) = prefs.edit().putString(KEY_DEFAULT_NAV_APP, value).apply()

    /**
     * 2026-05-29 Phase A 2단계 Day 5 — MMS 자동 추출 실패 시 사장님이 직접 박는 MMSC URL.
     * 예: http://mmsc.ktfwing.com:9082 (KT). null = 자동 추출 사용 (기본). 14명 중 알뜰/특수 SIM 1명 안전망.
     */
    var manualMmscUrl: String?
        get() = prefs.getString(KEY_MANUAL_MMSC_URL, null)?.takeIf { it.isNotBlank() }
        set(value) = prefs.edit().putString(KEY_MANUAL_MMSC_URL, value?.trim()).apply()

    /**
     * MMSC proxy 호스트 (옵션, SKT 일부 케이스). null = proxy 없음.
     */
    var manualMmscProxy: String?
        get() = prefs.getString(KEY_MANUAL_MMSC_PROXY, null)?.takeIf { it.isNotBlank() }
        set(value) = prefs.edit().putString(KEY_MANUAL_MMSC_PROXY, value?.trim()).apply()

    /**
     * MMSC proxy port (옵션). null 또는 0 이하 = 기본 (80).
     */
    var manualMmscPort: Int
        get() = prefs.getInt(KEY_MANUAL_MMSC_PORT, 0)
        set(value) = prefs.edit().putInt(KEY_MANUAL_MMSC_PORT, value).apply()

    /**
     * 2026-05-29 킬러콘텐츠 4단계 — 사장님 톤 RAG.
     * 사장님이 Settings 에서 명시 동의 = 사장님 보낸 SMS 풀을 Mac mini 에 batch upload → 임베딩 → retrieval.
     * 본인 폰 본인 데이터라 자체 서버라 안전. 그래도 명시 동의 필수.
     */
    var toneUploadConsented: Boolean
        get() = prefs.getBoolean(KEY_TONE_UPLOAD_CONSENTED, false)
        set(value) = prefs.edit().putBoolean(KEY_TONE_UPLOAD_CONSENTED, value).apply()

    /** 마지막 batch upload 완료 시각. 0 = 한 번도 안 함. */
    var toneLastUploadedAtMs: Long
        get() = prefs.getLong(KEY_TONE_LAST_UPLOADED_AT, 0L)
        set(value) = prefs.edit().putLong(KEY_TONE_LAST_UPLOADED_AT, value).apply()

    /** 누적 upload 된 SMS 개수 (서버 응답 기준). */
    var toneTotalUploadedCount: Int
        get() = prefs.getInt(KEY_TONE_TOTAL_UPLOADED, 0)
        set(value) = prefs.edit().putInt(KEY_TONE_TOTAL_UPLOADED, value).apply()

    /**
     * 2026-05-30 #7 — 사장님 옛 고객들 1회 일괄 자동 분류 완료 여부.
     * Application.onCreate 에서 false 일 때만 AutoCategoryClassifier.backfillAll 호출 후 true.
     */
    var autoCategoryBackfilled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_CATEGORY_BACKFILLED, false)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_CATEGORY_BACKFILLED, value).apply()

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
        private const val KEY_DEFAULT_NAV_APP = "default_nav_app_key"
        private const val KEY_MANUAL_MMSC_URL = "manual_mmsc_url"
        private const val KEY_MANUAL_MMSC_PROXY = "manual_mmsc_proxy"
        private const val KEY_MANUAL_MMSC_PORT = "manual_mmsc_port"
        private const val KEY_TONE_UPLOAD_CONSENTED = "tone_upload_consented"
        private const val KEY_TONE_LAST_UPLOADED_AT = "tone_last_uploaded_at"
        private const val KEY_TONE_TOTAL_UPLOADED = "tone_total_uploaded"
        private const val KEY_AUTO_CATEGORY_BACKFILLED = "auto_category_backfilled"
    }
}
