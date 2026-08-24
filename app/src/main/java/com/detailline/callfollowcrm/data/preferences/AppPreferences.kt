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

    // 통화 후 문자 보내기 — 새 번호와 통화 끝나면 "문자 보낼까요?" + 템플릿 3개 선택. (2026-07-12 사장님)
    //   템플릿 탭 = 바로 발송(2026-07-12 사장님 "복붙 말고 바로 전송"). 업체 소개 등 매번 보내는 문자를 한 번에.
    var postCallPickerEnabled: Boolean
        get() = prefs.getBoolean("postcall_picker_on", false)
        set(value) = prefs.edit().putBoolean("postcall_picker_on", value).apply()

    /** 연락처 이름 반영(READ_CONTACTS)을 기존 사용자에게 1회만 요청했는지. 신규는 온보딩 배치에서 요청. (2026-07-21) */
    var contactsPermissionAsked: Boolean
        get() = prefs.getBoolean("contacts_permission_asked", false)
        set(value) = prefs.edit().putBoolean("contacts_permission_asked", value).apply()

    /** 박람회 현장 목록(로컬, Phase 1). "|||" 로 구분해 저장(현장명엔 안 쓰는 구분자). 추후 서버/Room 이관. (2026-07-21) */
    var expoSites: List<String>
        get() = prefs.getString("expo_sites", "").orEmpty().split("|||").filter { it.isNotBlank() }
        set(value) = prefs.edit().putString("expo_sites", value.joinToString("|||")).apply()

    var postCallTemplate1: String
        get() = prefs.getString("postcall_tpl_1", "") ?: ""
        set(value) = prefs.edit().putString("postcall_tpl_1", value).apply()
    var postCallTemplate2: String
        get() = prefs.getString("postcall_tpl_2", "") ?: ""
        set(value) = prefs.edit().putString("postcall_tpl_2", value).apply()
    var postCallTemplate3: String
        get() = prefs.getString("postcall_tpl_3", "") ?: ""
        set(value) = prefs.edit().putString("postcall_tpl_3", value).apply()
    // 통화 후 템플릿별 첨부 사진 URI(선택). 빈 값 = 사진 없음. (2026-07-12 사장님)
    // 사진은 템플릿당 최대 5장 — 내부적으로 "\n" 조인 저장(URI엔 개행 없음). (2026-07-12 사장님)
    private var postCallPhoto1Raw: String
        get() = prefs.getString("postcall_photo_1", "") ?: ""
        set(value) = prefs.edit().putString("postcall_photo_1", value).apply()
    private var postCallPhoto2Raw: String
        get() = prefs.getString("postcall_photo_2", "") ?: ""
        set(value) = prefs.edit().putString("postcall_photo_2", value).apply()
    private var postCallPhoto3Raw: String
        get() = prefs.getString("postcall_photo_3", "") ?: ""
        set(value) = prefs.edit().putString("postcall_photo_3", value).apply()
    private fun photosOf(raw: String): List<String> = raw.split("\n").map { it.trim() }.filter { it.isNotBlank() }
    var postCallPhotos1: List<String>
        get() = photosOf(postCallPhoto1Raw)
        set(v) { postCallPhoto1Raw = v.distinct().take(5).joinToString("\n") }
    var postCallPhotos2: List<String>
        get() = photosOf(postCallPhoto2Raw)
        set(v) { postCallPhoto2Raw = v.distinct().take(5).joinToString("\n") }
    var postCallPhotos3: List<String>
        get() = photosOf(postCallPhoto3Raw)
        set(v) { postCallPhoto3Raw = v.distinct().take(5).joinToString("\n") }
    /** 설정한 통화 후 템플릿(텍스트, 사진 URI 목록 최대 5) — 텍스트나 사진 하나라도 있으면 포함. (2026-07-12 사장님) */
    val postCallItems: List<Pair<String, List<String>>>
        get() = listOf(
            postCallTemplate1 to postCallPhotos1,
            postCallTemplate2 to postCallPhotos2,
            postCallTemplate3 to postCallPhotos3
        ).filter { it.first.isNotBlank() || it.second.isNotEmpty() }
    /** 설정한 통화 후 템플릿(빈 것 제외) — 텍스트만. */
    val postCallTemplates: List<String>
        get() = listOf(postCallTemplate1, postCallTemplate2, postCallTemplate3).filter { it.isNotBlank() }

    /** 수신 통화 첫 응대용 템플릿 ID. -1L = 미지정(해당 케이스 자동 발송 X). */
    var firstReplyIncomingTemplateId: Long
        get() = prefs.getLong(KEY_FIRST_REPLY_INCOMING_TPL, -1L)
        set(value) = prefs.edit().putLong(KEY_FIRST_REPLY_INCOMING_TPL, value).apply()

    /** 부재중 통화 첫 응대용 템플릿 ID. -1L = 미지정. */
    var firstReplyMissedTemplateId: Long
        get() = prefs.getLong(KEY_FIRST_REPLY_MISSED_TPL, -1L)
        set(value) = prefs.edit().putLong(KEY_FIRST_REPLY_MISSED_TPL, value).apply()

    // ── 자동 문자 — 프로토 자동문자 시트(인라인 텍스트). 비면 위 템플릿ID fallback. (2026-06-03) ──
    /** 부재중 자동응답 — 처음 연락한 고객(신규). */
    var autoMissedNewText: String
        get() = healedAutoText("auto_missed_new_text",
            "안녕하세요 😊 지금 시공 중이라 전화를 못 받았어요. 어떤 시공 문의신지 문자로 남겨주시면 바로 견적 안내드릴게요!")
        set(value) = prefs.edit().putString("auto_missed_new_text", value).apply()
    /** 부재중 자동응답 — 다시 연락한 고객(단골·기존). */
    var autoMissedReturnText: String
        get() = healedAutoText("auto_missed_return_text",
            "고객님, 전화 못 받아 죄송해요! 지금 시공 중이라 마치는 대로 바로 연락드릴게요. 급하시면 문자로 남겨주세요 😊")
        set(value) = prefs.edit().putString("auto_missed_return_text", value).apply()
    /** 시공 D-1 안내 on/off (알림으로 "보낼까요?" 물어봄). */
    var d1AutoEnabled: Boolean
        get() = prefs.getBoolean("d1_auto_enabled", true)
        set(value) = prefs.edit().putBoolean("d1_auto_enabled", value).apply()
    /** 통화 자동 요약 on/off — 통화 끝나면 에이닷 폴더(녹음/텍스트)를 스캔해 자동 요약. 기본 ON. (2026-06-14 사장님) */
    var autoSummaryEnabled: Boolean
        get() = prefs.getBoolean("auto_summary_enabled", true)
        set(value) = prefs.edit().putBoolean("auto_summary_enabled", value).apply()
    /** 릴리스에서 민감화면 스크린샷·화면녹화 차단(FLAG_SECURE). 베타엔 버그 캡처가 필요 → 기본 OFF(캡처 허용). (2026-08-20 사장님) */
    var blockScreenCapture: Boolean
        get() = prefs.getBoolean("block_screen_capture", false)
        set(value) = prefs.edit().putBoolean("block_screen_capture", value).apply()
    /**
     * 전화 오는 순간 "상대 정보 카드"(둥둥 뜨는 오버레이) on/off. **기본 OFF** (2026-07-12 사장님).
     *   전화벨 울릴 때 번호로 고객·시공일정·최근 대화를 즉시 조회해 화면 위에 카드로 띄운다. (2026-07-01 사장님)
     *   실제로 뜨려면 "다른 앱 위에 표시"(SYSTEM_ALERT_WINDOW) 권한 필요 → 토글 켜면 바로 승인 창을 띄운다.
     *   기본 ON 이면 권한 없는데 켜진 것처럼 보여 혼란 → 기본 OFF + 켤 때 권한 요청으로 변경.
     */
    var incomingCallerCardEnabled: Boolean
        get() = prefs.getBoolean("incoming_caller_card_enabled", false)
        set(value) = prefs.edit().putBoolean("incoming_caller_card_enabled", value).apply()
    /** 막내 비서 현재 단계(0~9, 10레벨마다). 앱 시작 시 Mascot 변신 복원용. (2026-06-14) */
    var agentTier: Int
        get() = prefs.getInt("agent_tier", 0)
        set(value) = prefs.edit().putInt("agent_tier", value).apply()

    /**
     * 폰(설치)별 고유 식별자. 말투 학습/추천을 **폰마다 분리**하기 위함. (2026-06-17 사장님)
     *   - 베타: 모든 폰이 "owner-anon" 공용 풀을 쓰면 테스터 A 말투가 B 추천에 섞임(개인정보·정확도 사고).
     *     이 id 로 서버 owner_tone 풀 + prepare-reply RAG retrieval 을 폰별로 격리.
     *   - 최초 1회 UUID 생성·영속. 앱 삭제/데이터 삭제 시 새로 생성(그땐 재업로드).
     */
    val deviceId: String
        get() {
            var id = prefs.getString("device_id", null)
            if (id.isNullOrBlank()) {
                id = "dev-" + java.util.UUID.randomUUID().toString()
                prefs.edit().putString("device_id", id).apply()
            }
            return id
        }
    /** 길찾기 기본 지도앱(tmap/kakao/naver/default). 비면 처음 길찾기 때 선택받음. (2026-06-14) */
    var navApp: String
        get() = prefs.getString("nav_app", "") ?: ""
        set(value) = prefs.edit().putString("nav_app", value).apply()
    /** D-1 안내 묻는 시각(시, 0~23). 기본 오전 9시. */
    var d1SendHour: Int
        get() = prefs.getInt("d1_send_hour", 9)
        set(value) = prefs.edit().putInt("d1_send_hour", value).apply()

    // ── 방해금지 시간대 (밤엔 소리·진동 없이 조용히) 2026-08-04 사장님 ──
    /** 켜면 quietStartHour~quietEndHour 사이 알림은 소리·진동 없이 조용히 온다(놓치진 않음). */
    var quietHoursEnabled: Boolean
        get() = prefs.getBoolean("quiet_hours_enabled", false)
        set(value) = prefs.edit().putBoolean("quiet_hours_enabled", value).apply()
    /** 방해금지 시작 시각(시, 0~23). 기본 오후 10시. */
    var quietStartHour: Int
        get() = prefs.getInt("quiet_start_hour", 22)
        set(value) = prefs.edit().putInt("quiet_start_hour", value).apply()
    /** 방해금지 끝 시각(시, 0~23). 기본 오전 7시. */
    var quietEndHour: Int
        get() = prefs.getInt("quiet_end_hour", 7)
        set(value) = prefs.edit().putInt("quiet_end_hour", value).apply()
    /** D-1 안내 문자 본문. */
    var d1AutoText: String
        get() = healedAutoText("d1_auto_text",
            "고객님, 내일 시공 예정입니다 😊 현장 정리(가구·물건 비움) 부탁드리고, 주차 가능 여부만 미리 알려주세요.")
        set(value) = prefs.edit().putString("d1_auto_text", value).apply()

    /**
     * 인코딩 손상 자동문자 pref 를 제거 → 기본값(멀쩡한 소스 상수)으로 복구. (2026-07-22 베타 D-1 문자 깨짐)
     *   증상: 저장된 자동문자가 바이트 손상돼 깨져 표시됨(예: UTF-8→EUC-KR mojibake "怒졸컬..誘몃━.."). 소스 기본값·표시경로는 정상 → 저장값만 문제.
     *   키를 remove 하면 다음 getString 이 기본값을 반환. 앱 시작 시 1회(동기) 호출.
     *   ⚠️ 그물 넓힘(2026-07-22 2차): U+FFFD 뿐 아니라 한자(漢字) mojibake·깨진 서로게이트도 잡음 — 우리 기본 문구엔 한자가 없음.
     *     (1차 U+FFFD-only 는 한자 위주 mojibake 를 놓쳐서 1081 에서도 안 고쳐졌던 원인.)
     */
    fun healCorruptedAutoTexts() {
        for (key in listOf("d1_auto_text", "auto_missed_new_text", "auto_missed_return_text", "arrival_auto_text")) {
            val v = prefs.getString(key, null)
            if (v != null && looksEncodingCorrupted(v)) prefs.edit().remove(key).apply()
        }
    }

    /**
     * 자동문자 값이 인코딩 손상으로 깨졌는지 판정. 우리 기본/정상 문구는 한글·이모지·기본 문장부호뿐이라
     * 아래 신호가 있으면 손상으로 본다:
     *   - U+FFFD(�) 치환문자 → 확정 손상
     *   - 짝 없는 서로게이트(깨진 이모지) → 확정 손상
     *   - CJK 한자(漢字, 우리 문구엔 안 씀)가 2자 이상 → UTF-8 을 EUC-KR 로 잘못 읽은 mojibake 신호
     */
    private fun looksEncodingCorrupted(s: String): Boolean {
        var hanja = 0
        var i = 0
        while (i < s.length) {
            val ch = s[i]
            val c = ch.code
            when {
                c == 0xFFFD -> return true
                ch.isHighSurrogate() -> {
                    val next = if (i + 1 < s.length) s[i + 1] else null
                    if (next == null || !next.isLowSurrogate()) return true
                    i++ // 정상 이모지 쌍은 통째로 건너뜀
                }
                ch.isLowSurrogate() -> return true
                c in 0x4E00..0x9FFF || c in 0x3400..0x4DBF -> hanja++
            }
            i++
        }
        return hanja >= 2
    }

    /**
     * 자동문자 **읽기 시점 방어** — 저장값이 인코딩 손상(mojibake)이면 손상값 대신 멀쩡한 기본값을 돌려준다.
     * 시작 시 healCorruptedAutoTexts 가 어떤 이유로든 놓쳐도(타이밍·재오염 등) 발송·표시·진단 **어디로도 깨진 값이 안 나감**.
     * (2026-07-23 베타 진단 직송: 0.2.1119 에서도 D-1 이 깨져 들어옴 → 시작 heal 만으로 부족 → 읽기 시점 방어 추가.)
     */
    private fun healedAutoText(key: String, default: String): String {
        val v = prefs.getString(key, default) ?: default
        if (looksEncodingCorrupted(v)) {
            prefs.edit().remove(key).apply()   // 손상값 영구 제거(자기복구) → 다음부터 기본값
            return default
        }
        return v
    }
    /** 현장 도착 안내(위치 기반) on/off. 트리거는 추후. */
    var arrivalAutoEnabled: Boolean
        get() = prefs.getBoolean("arrival_auto_enabled", false)
        set(value) = prefs.edit().putBoolean("arrival_auto_enabled", value).apply()
    var arrivalAutoText: String
        get() = healedAutoText("arrival_auto_text",
            "고객님, 30분 뒤 도착 예정입니다 😊 잠시 후 뵐게요!")
        set(value) = prefs.edit().putString("arrival_auto_text", value).apply()

    /** 새 버전 체크 마지막 시각(ms) — 하루 1회 throttle. (2026-06-16) */
    var lastUpdateCheckMs: Long
        get() = prefs.getLong("last_update_check_ms", 0L)
        set(value) = prefs.edit().putLong("last_update_check_ms", value).apply()
    /** 마지막 체크에서 새 버전 감지 여부 — 다음 체크 전까지 배너 유지용. */
    var updateAvailable: Boolean
        get() = prefs.getBoolean("update_available", false)
        set(value) = prefs.edit().putBoolean("update_available", value).apply()
    /** 마지막 체크에서 서버가 알려준 최신 versionCode (0 = 모름). */
    var latestVersionCode: Int
        get() = prefs.getInt("latest_version_code", 0)
        set(value) = prefs.edit().putInt("latest_version_code", value).apply()
    /** 최신 버전 변경 내역(서버가 주면). 배너에 "무엇이 바뀌었나" 표시용. 줄바꿈으로 이어붙여 저장. (2026-07-18 사장님) */
    var latestReleaseNotes: List<String>
        get() = prefs.getString("latest_release_notes", "")?.split("\n")?.filter { it.isNotBlank() } ?: emptyList()
        set(value) = prefs.edit().putString("latest_release_notes", value.joinToString("\n")).apply()
    /** 최신 버전 서버 업로드(빌드) 시각(ms) — 배너에 "8월 24일 새 버전" 날짜 표시용. (2026-08-24 사장님) */
    var latestVersionMtimeMs: Long
        get() = prefs.getLong("latest_version_mtime_ms", 0L)
        set(value) = prefs.edit().putLong("latest_version_mtime_ms", value).apply()
    /** '새로워졌어요' 시트를 이미 띄운 versionCode — 같은 버전엔 다시 안 뜨게(한 번만 짠). 0 = 아직 없음. (2026-07-18 사장님) */
    var updateSheetShownForCode: Int
        get() = prefs.getInt("update_sheet_shown_code", 0)
        set(value) = prefs.edit().putInt("update_sheet_shown_code", value).apply()

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
     * AI 답변 준비 on/off — 기본 ON. (2026-07-16 사장님: "받은 문자 알림"과 별개인 걸 몰라 이 스위치를 찾고 있었음)
     *   ON: 고객 문자가 오면 백그라운드에서 서버가 추천 답변을 미리 만들어둠(문자방 열면 바로 보임 + 마스코트 '준비 중' 애니).
     *   OFF: 아예 안 만듦 — 마스코트 로딩·홈 "AI 답변 준비 중"·서버 호출 전부 없음. 완전 수동.
     *   ※ "받은 문자 알림"(incomingSmsNotifyEnabled)은 알림창만 담당 — 이것과 완전 별개.
     */
    var aiReplyPrepEnabled: Boolean
        get() = prefs.getBoolean(KEY_AI_REPLY_PREP, true)
        set(value) = prefs.edit().putBoolean(KEY_AI_REPLY_PREP, value).apply()

    // ── '이 사람 고객 아님' (2026-07-18 사장님) — 협업사장·거래처·지인 등을 고객 상담으로 오인하는 AI 방지 ──
    //   '고객 아님' 표시된 번호는 고객상담 AI(페르소나·추천답변)를 안 만든다. 통화요약 같은 중립 기능은 유지.
    //   기본 = 고객(안전). 모르는 새 번호가 한 번 오간 뒤 대화방 조용한 줄로 물어 본다.
    private fun suffixOf(phone: String): String = phone.filter { it.isDigit() }.takeLast(8)

    /** '고객 아님'으로 표시된 번호(끝 8자리) 집합. */
    var nonCustomerSuffixes: Set<String>
        get() = prefs.getStringSet(KEY_NON_CUSTOMER, emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet(KEY_NON_CUSTOMER, value).apply()

    /** 이 번호가 '고객 아님'인가. */
    fun isNonCustomer(phone: String): Boolean {
        val s = suffixOf(phone)
        return s.length >= 7 && s in nonCustomerSuffixes
    }

    /** '고객?' 질문에 답한(고객/아님) 번호 — 다시 안 물음. */
    var customerAskedSuffixes: Set<String>
        get() = prefs.getStringSet(KEY_CUSTOMER_ASKED, emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet(KEY_CUSTOMER_ASKED, value).apply()

    /** 사장님이 대화방에서 답함. non=true → 고객 아님(AI 끔), false → 고객(기본, 태그만 해제). 양쪽 다 '물음 완료' 기록. */
    fun answerCustomerAsk(phone: String, isNonCustomer: Boolean) {
        val s = suffixOf(phone); if (s.length < 7) return
        nonCustomerSuffixes = if (isNonCustomer) nonCustomerSuffixes + s else nonCustomerSuffixes - s
        customerAskedSuffixes = customerAskedSuffixes + s
    }

    /** 사장님이 '맨 위에 고정'한 거래처 번호(끝 8자리) 집합 — 카톡식 상단 고정. (2026-08-24 사장님)
     *   commit: 직접 큐레이션한 목록이라 유실 방지. 키명이 백업 denylist(token/fcm/folder) 밖이라 설정칸 백업에 자동 포함 → 재설치해도 살아남. */
    var pinnedSuffixes: Set<String>
        get() = prefs.getStringSet("pinned_suffixes", emptySet()) ?: emptySet()
        set(value) { prefs.edit().putStringSet("pinned_suffixes", value).commit() }

    /** 이 번호가 상단 고정인가. */
    fun isPinned(phone: String): Boolean {
        val s = suffixOf(phone)
        return s.length >= 7 && s in pinnedSuffixes
    }

    /** 고정 토글. 반환 = 토글 후 상태(true=고정됨 / false=해제됨). */
    fun togglePinned(phone: String): Boolean {
        val s = suffixOf(phone); if (s.length < 7) return false
        val now = pinnedSuffixes
        val pinned = s !in now
        pinnedSuffixes = if (pinned) now + s else now - s
        return pinned
    }

    /** 스팸/사생활 등으로 목록에서 뺄 때 고정도 함께 해제(좀비 핀 방지). */
    fun unpin(phone: String) {
        val s = suffixOf(phone); if (s.length < 7) return
        if (s in pinnedSuffixes) pinnedSuffixes = pinnedSuffixes - s
    }

    /**
     * 마지막으로 '수신 MMS 알림'을 띄운 시각(ms). 이 이후 도착한 inbox MMS 만 새로 알림.
     *   MMS(사진)는 SmsReceiver·WAP_PUSH 로 못 잡아 ContentObserver 로 감지→알림하는데, 중복/과거분 방지용 마커.
     *   0 = 미설정 → 앱 시작 시 now 로 기준선 잡음(설치 전 과거 MMS 는 알림 안 함). (2026-07-03 사장님)
     */
    var lastNotifiedMmsMs: Long
        get() = prefs.getLong("last_notified_mms_ms", 0L)
        set(value) { prefs.edit().putLong("last_notified_mms_ms", value).commit() }  // commit: 종료 시 유실→MMS 재알림 방지. (2026-08-11 알림 감사)

    /**
     * 이미 '수신 MMS 알림'을 띄운 MMS _id 들(최근 80개만). 삼성이 MMS 를 2단계(받음표시=내용無 → 다운로드완료=내용有)로
     *   저장하며 date 가 바뀌어도 같은 문자를 두 번 알리지 않게, date 마커와 별개로 _id 로 1회 보장. (2026-07-30 버그감사: 알림 두 번)
     */
    var notifiedMmsIds: Set<String>
        get() = prefs.getStringSet("notified_mms_ids", emptySet()) ?: emptySet()
        set(value) { prefs.edit().putStringSet("notified_mms_ids", value).commit() }  // commit: 종료 시 유실→MMS 재알림 방지. (2026-08-11 알림 감사)

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

    /**
     * 개인정보 필수 동의를 완료한 문서 버전. "" = 미동의. [AppConfig.CONSENT_VERSION] 과 다르면
     *   진입 시 동의 게이트(ConsentScreen) 1회 노출. (추가97 2026-07-06 cowork)
     */
    var consentAgreedVersion: String
        get() = prefs.getString("consent_agreed_version", "") ?: ""
        set(value) = prefs.edit().putString("consent_agreed_version", value).apply()

    /** 마지막 batch upload 완료 시각. 0 = 한 번도 안 함. */
    var toneLastUploadedAtMs: Long
        get() = prefs.getLong(KEY_TONE_LAST_UPLOADED_AT, 0L)
        set(value) = prefs.edit().putLong(KEY_TONE_LAST_UPLOADED_AT, value).apply()

    /** 누적 upload 된 SMS 개수 (서버 응답 기준). */
    var toneTotalUploadedCount: Int
        get() = prefs.getInt(KEY_TONE_TOTAL_UPLOADED, 0)
        set(value) = prefs.edit().putInt(KEY_TONE_TOTAL_UPLOADED, value).apply()

    /**
     * 마지막 동기화 때 폰에 있던 보낸문자(available) 개수. (2026-06-30)
     *   "N건 대기" 표시는 (현재 available − 이 값) 으로 계산 = **마지막 동기화 이후 새로 온 문자**.
     *   서버가 빈/중복 문자를 걸러 저장수(uploadedCount)가 안 늘어도, 한 번 동기화하면 "대기"가 0이 되게 한다.
     *   (옛 버그: 대기 = available − uploadedCount 라, 걸러진 문자만큼 "24건 대기"가 영영 안 닫혀 "동기화해도 변동 없음"으로 보였음.)
     */
    var toneSyncedUpToAvailable: Int
        get() = prefs.getInt("tone_synced_up_to_available", 0)
        set(value) = prefs.edit().putInt("tone_synced_up_to_available", value).apply()

    /**
     * 말투 사례집 자동 동기화 마지막 시도 시각(throttle 용). (2026-06-17 사장님)
     *   한 번 동의(toneUploadConsented)했으면, 앱 켤 때 하루 1번 새 문자만 조용히 자동 업로드.
     *   → 사장님이 '동기화'를 다시 안 눌러도 사례집이 최신 유지.
     */
    var toneLastAutoSyncMs: Long
        get() = prefs.getLong("tone_last_auto_sync_ms", 0L)
        set(value) = prefs.edit().putLong("tone_last_auto_sync_ms", value).apply()

    /**
     * 2026-05-30 #7 — 사장님 옛 고객들 1회 일괄 자동 분류 완료 여부.
     * Application.onCreate 에서 false 일 때만 AutoCategoryClassifier.backfillAll 호출 후 true.
     */
    var autoCategoryBackfilled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_CATEGORY_BACKFILLED, false)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_CATEGORY_BACKFILLED, value).apply()

    /**
     * 2026-06-07 — 카테고리 규칙 수정(날짜 등록=시공대기, 상담만=미분류) 후 1회 재정리 완료 여부.
     * 옛 버그로 "상담만 했는데 시공대기"가 된 고객들을 새 규칙으로 한 번 더 분류해 정리.
     */
    var autoCategoryRebuiltV2: Boolean
        get() = prefs.getBoolean("auto_category_rebuilt_v2", false)
        set(value) = prefs.edit().putBoolean("auto_category_rebuilt_v2", value).apply()

    /**
     * 2026-06-16 — 첫 실행 시 최근 7일 통화기록 1회 "따라잡기" 완료 여부.
     *   새 사장님이 깔면 상담함이 텅 비어 보이는 문제(전화로만 연락한 고객 누락) 보완.
     *   READ_CALL_LOG 권한 있을 때만 import 하고 true 로. 권한 없으면 false 유지 → 온보딩 grant 후 재시도.
     *   서버 추천 생성은 하지 않음(비용 0) — 통화 기록만.
     */
    var initialCallLogImported: Boolean
        get() = prefs.getBoolean("initial_call_log_imported", false)
        set(value) = prefs.edit().putBoolean("initial_call_log_imported", value).apply()

    /** 2026-06-07 — 견적 기록 버그 수정 전 잘못 쌓인 ESTIMATE_SENT 1회 정리 완료 여부. */
    var estimateSentLegacyCleaned: Boolean
        get() = prefs.getBoolean("estimate_sent_legacy_cleaned", false)
        set(value) = prefs.edit().putBoolean("estimate_sent_legacy_cleaned", value).apply()

    /** 2026-06-07 — 발신 서명("외주/일당 절대 X")이 분류기에 섞여 고객이 '일당' 카테고리로 잘못 분류된 것 1회 정리. */
    var dailyWageCategoryCleanedV1: Boolean
        get() = prefs.getBoolean("daily_wage_category_cleaned_v1", false)
        set(value) { prefs.edit().putBoolean("daily_wage_category_cleaned_v1", value).commit() }

    /**
     * 2026-06-07 — "지금 답장 기다려요"에서 밀어서 '정리'한 번호(suffix).
     *   미확인 목록에서만 숨김. 스팸 아님 — 신규/최근대화엔 그대로 보이는 정상 고객.
     *   (이전엔 밀기=스팸 마킹이라 진짜 고객이 스팸으로 빠지던 버그를 대체)
     */
    var dismissedUnconfirmedSuffixes: Set<String>
        get() = prefs.getStringSet("dismissed_unconfirmed_suffixes", emptySet()) ?: emptySet()
        set(value) { prefs.edit().putStringSet("dismissed_unconfirmed_suffixes", value).commit() }

    /** 2026-06-07 — 옛 '밀어서 정리=스팸' 으로 잘못 빠진 번호를 '정리됨'으로 1회 이관(스팸 해제). */
    var spamSweptToDismissedV1: Boolean
        get() = prefs.getBoolean("spam_swept_to_dismissed_v1", false)
        set(value) { prefs.edit().putBoolean("spam_swept_to_dismissed_v1", value).commit() }

    /**
     * 2026-06-26 — schedule_create 여정이벤트 추적 도입 전부터 로컬에 있던 시공일 일정들을
     *   admin KPI 에 1회 백필 완료 여부. false 일 때만 scheduledWorkDate 있는 고객마다
     *   track("schedule_create", screen="backfill", extra={backfilled:true}) 발사 후 true. (cowork 요청)
     *   ⚠️ V2: V1 은 '버퍼에 쌓고 flush 전에 앱이 죽어 유실 + flag 만 박힘' 버그가 있어 키를 올려 강제 재실행.
     *      이제 전송 성공해야만 flag(아래 Application 백필 로직).
     */
    var scheduleBackfilledV2: Boolean
        get() = prefs.getBoolean("schedule_backfilled_v2", false)
        set(value) { prefs.edit().putBoolean("schedule_backfilled_v2", value).commit() }

    /** 2026-06-07 — 스팸/광고 번호 앞자리. 이 앞자리로 시작하면 자동답장·AI 준비 X + 목록 제외.
     *   2026-06-20 사장님: 기본값을 추천 앞자리(070·050·0507·지역번호) 전부로 — 사람들이 직접 선택할 줄 몰라서.
     *   (설정에서 안 건드린 사용자에게만 기본 적용. 직접 편집했으면 그 값 유지.) */
    var spamPrefixes: Set<String>
        get() = prefs.getStringSet("spam_prefixes", com.detailline.callfollowcrm.util.SpamPrefix.SUGGESTED.toSet())
            ?: com.detailline.callfollowcrm.util.SpamPrefix.SUGGESTED.toSet()
        set(value) { prefs.edit().putStringSet("spam_prefixes", value).commit() }

    /** "이건 광고 아냐" 로 사장님이 되살린 번호(끝8자리) — AdHeuristics 자동 광고분류 예외. (2026-07-08 사장님) */
    var adAllowlistSuffixes: Set<String>
        get() = prefs.getStringSet("ad_allowlist_suffixes", emptySet()) ?: emptySet()
        set(value) { prefs.edit().putStringSet("ad_allowlist_suffixes", value).commit() }

    // ── 막내 팁 (상담함 기능 발견 카드 + 안내판, 2026-07-04 사장님) ──
    /** 팁 카드 전체 on/off. */
    var makneTipsEnabled: Boolean
        get() = prefs.getBoolean("makne_tips_enabled", true)
        set(value) { prefs.edit().putBoolean("makne_tips_enabled", value).apply() }
    /** 눌러본(=이미 써본) 팁 key — 더는 광고 안 함(안 써본 것 우선). */
    var makneTipUsed: Set<String>
        get() = prefs.getStringSet("makne_tip_used", emptySet()) ?: emptySet()
        set(value) { prefs.edit().putStringSet("makne_tip_used", value).apply() }
    /** ×로 닫은 팁 key. */
    var makneTipDismissed: Set<String>
        get() = prefs.getStringSet("makne_tip_dismissed", emptySet()) ?: emptySet()
        set(value) { prefs.edit().putStringSet("makne_tip_dismissed", value).apply() }
    /** 안내판을 이미 본 팁 key — 기능마다 처음 한 번만. */
    var makneGuideSeen: Set<String>
        get() = prefs.getStringSet("makne_guide_seen", emptySet()) ?: emptySet()
        set(value) { prefs.edit().putStringSet("makne_guide_seen", value).apply() }
    /** 팁별 노출 횟수 — "여러 번 봐도 안 누르면 은퇴"(피로도 방지)용. "key:count;key:count" 인코딩. (2026-07-05 사장님) */
    var makneTipImpressions: Map<String, Int>
        get() = (prefs.getString("makne_tip_impr", "") ?: "").split(";")
            .mapNotNull { e -> e.split(":").takeIf { it.size == 2 }?.let { it[0] to (it[1].toIntOrNull() ?: 0) } }
            .toMap()
        set(value) { prefs.edit().putString("makne_tip_impr", value.entries.joinToString(";") { "${it.key}:${it.value}" }).apply() }
    /** 마지막으로 노출 카운트를 올린 시각 — 세션(스로틀)당 1회만 세려고. */
    var lastTipImpressionMs: Long
        get() = prefs.getLong("makne_tip_impr_ms", 0L)
        set(value) { prefs.edit().putLong("makne_tip_impr_ms", value).apply() }

    // ── 회원가입(폰 인증번호, 2026-07-04) — docs/ANDROID_HANDOFF_signup_auth.md ──
    /** 인증됐지만 대기열(waitlisted) 상태 — 다음 실행에도 대기 화면부터. 등업되면 false. */
    var pendingWaitlist: Boolean
        get() = prefs.getBoolean("signup_pending_waitlist", false)
        set(value) { prefs.edit().putBoolean("signup_pending_waitlist", value).apply() }
    /** 무료 체험 만료 시각(ms) — 가입/등업 + 무료기간. 앱 내 "무료 D-xx" 표시용(추후). */
    var signupFreeUntilMs: Long
        get() = prefs.getLong("signup_free_until_ms", 0L)
        set(value) { prefs.edit().putLong("signup_free_until_ms", value).apply() }

    // ── 원칙 발견 (Phase 2, 2026-06-17) — 너무 자주 안 묻게 하루 한도 + 거절 후보 기억 ──
    /** 원칙 묻기 한도 추적용 — 마지막으로 카운트한 '날(자정 ms)'. */
    var principleAskDayStart: Long
        get() = prefs.getLong("principle_ask_day", 0L)
        set(value) { prefs.edit().putLong("principle_ask_day", value).apply() }
    /** 그 날 이미 원칙을 몇 번 물었는지. */
    var principleAskCountToday: Int
        get() = prefs.getInt("principle_ask_count", 0)
        set(value) { prefs.edit().putInt("principle_ask_count", value).apply() }
    /** 사장님이 ❌(아니에요)한 원칙 후보 — 다시 안 묻게 보관. */
    var dismissedPrincipleCandidates: Set<String>
        get() = prefs.getStringSet("principle_dismissed", emptySet()) ?: emptySet()
        set(value) { prefs.edit().putStringSet("principle_dismissed", value).apply() }
    /**
     * 아직 ⭕/❌/나중에 안 누른 '대기 중 원칙 발견'. 뒤로가기로 채팅 나갔다 와도(VM 재생성) 다시 떠야 함
     *   — 선택할 때까지 유지. 항목 = "번호끝8자리  원칙  질문". (2026-06-18 사장님)
     */
    var pendingPrincipleDiscoveries: Set<String>
        get() = prefs.getStringSet("principle_pending", emptySet()) ?: emptySet()
        set(value) { prefs.edit().putStringSet("principle_pending", value).apply() }

    /**
     * 2026-06-01 전면 리뉴얼 — 로그인 화면을 한 번 봤는지. 첫 실행에만 로그인 화면 노출.
     * 어떤 소셜 버튼/둘러보기든 누르면 true → 이후 바로 권한/홈으로.
     * (실제 소셜 OAuth 연동은 서버 작업으로 별도. 지금은 화면 + 진입 흐름만.)
     */
    var hasSeenLogin: Boolean
        get() = prefs.getBoolean(KEY_HAS_SEEN_LOGIN, false)
        set(value) = prefs.edit().putBoolean(KEY_HAS_SEEN_LOGIN, value).apply()

    /** 전면 리뉴얼 온보딩(스토리텔링 + 업종 + 상호·지역 + 막내 비서 탄생) 완료 여부. */
    var hasOnboarded: Boolean
        get() = prefs.getBoolean(KEY_HAS_ONBOARDED, false)
        set(value) = prefs.edit().putBoolean(KEY_HAS_ONBOARDED, value).apply()

    /** 활동 지역 (온보딩에서 선택, 여러 곳). "|" join. AI 답장·길찾기 컨텍스트. */
    var ownerRegions: List<String>
        get() = prefs.getString(KEY_OWNER_REGIONS, "")?.split("|")?.filter { it.isNotBlank() } ?: emptyList()
        set(value) = prefs.edit().putString(KEY_OWNER_REGIONS, value.joinToString("|")).apply()

    // ── 사업자정보 (2026-06-01) — 견적서/시공접수서 발행용. 모두 사장님 직접 입력. ──
    var bizName: String
        get() = prefs.getString(KEY_BIZ_NAME, "") ?: ""
        set(value) = prefs.edit().putString(KEY_BIZ_NAME, value.trim()).apply()
    var bizOwner: String
        get() = prefs.getString(KEY_BIZ_OWNER, "") ?: ""
        set(value) = prefs.edit().putString(KEY_BIZ_OWNER, value.trim()).apply()
    var bizNo: String
        get() = prefs.getString(KEY_BIZ_NO, "") ?: ""
        set(value) = prefs.edit().putString(KEY_BIZ_NO, value.trim()).apply()
    var bizAddr: String
        get() = prefs.getString(KEY_BIZ_ADDR, "") ?: ""
        set(value) = prefs.edit().putString(KEY_BIZ_ADDR, value.trim()).apply()
    var bizPhone: String
        get() = prefs.getString(KEY_BIZ_PHONE, "") ?: ""
        set(value) = prefs.edit().putString(KEY_BIZ_PHONE, value.trim()).apply()
    /** 대표 전화번호 — 고객 문서(견적서·접수서)에 표시. 비면 로그인 번호(bizPhone) 사용. 개인 휴대폰 노출 방지. (2026-07-04 사장님) */
    var bizRepPhone: String
        get() = prefs.getString("biz_rep_phone", "") ?: ""
        set(value) = prefs.edit().putString("biz_rep_phone", value.trim()).apply()
    /** 고객 문서에 표시할 전화 — 대표번호 있으면 그것, 없으면 로그인 번호. */
    val displayPhone: String get() = bizRepPhone.ifBlank { bizPhone }
    /** 직인 문구 — 견적서 도장(.qd-seal)에 들어갈 글자. 프로토 bizInfo.seal. */
    var bizSeal: String
        get() = prefs.getString(KEY_BIZ_SEAL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_BIZ_SEAL, value.trim()).apply()
    /** 견적 유효기간 = 발행일로부터 N일. 기본 14. */
    var bizQuoteValidDays: Int
        get() = prefs.getInt(KEY_BIZ_VALID_DAYS, 14)
        set(value) = prefs.edit().putInt(KEY_BIZ_VALID_DAYS, value).apply()

    // ── 입금 계좌 (2026-06-08) — 협업 현장 완료 시 요청 사장에게 전달 + 견적서 활용. ──
    /** 은행명 (예: 국민은행). */
    var bizBank: String
        get() = prefs.getString(KEY_BIZ_BANK, "") ?: ""
        set(value) = prefs.edit().putString(KEY_BIZ_BANK, value.trim()).apply()
    /** 계좌번호 (하이픈 포함 가능). */
    var bizAccountNo: String
        get() = prefs.getString(KEY_BIZ_ACCOUNT_NO, "") ?: ""
        set(value) = prefs.edit().putString(KEY_BIZ_ACCOUNT_NO, value.trim()).apply()
    /** 예금주. 비우면 대표자 이름 사용. */
    var bizAccountHolder: String
        get() = prefs.getString(KEY_BIZ_ACCOUNT_HOLDER, "") ?: ""
        set(value) = prefs.edit().putString(KEY_BIZ_ACCOUNT_HOLDER, value.trim()).apply()

    // ── 시공접수서 제출 동기화 (2026-06-03) — 이미 임포트한 token / 마지막 동기화 시각. ──
    var intakeImportedTokens: Set<String>
        get() = prefs.getStringSet("intake_imported_tokens", emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet("intake_imported_tokens", value).apply()
    var intakeSyncSinceMs: Long
        get() = prefs.getLong("intake_sync_since_ms", 0L)
        set(value) = prefs.edit().putLong("intake_sync_since_ms", value).apply()

    /** 마감 브리핑 알림을 탭한 순간 그 브리핑의 '날짜'(startOfDay). 자정 넘겨 열어도 그날 기준으로 보이게. VM 이 읽고 0 으로 지움(일회성). (2026-07-30 버그감사) */
    var pendingBriefDayMs: Long
        get() = prefs.getLong("pending_brief_day_ms", 0L)
        set(value) = prefs.edit().putLong("pending_brief_day_ms", value).apply()

    // ── 시간 기반 알림(D-1·잔금·브리핑) 중복방지 — 이미 보낸 키(예 "d1:{id}:{dateMs}"). ──
    var reminderNotifiedKeys: Set<String>
        get() = prefs.getStringSet("reminder_notified_keys", emptySet()) ?: emptySet()
        // commit(): 백그라운드 워커가 apply() 로 저장하면 flush 전 프로세스 종료 시 키가 유실돼 같은 알림이
        //   재발사(특히 마감브리핑 2회)되던 것. 즉시 동기 저장(IO 스레드라 안전). (2026-08-11 알림 감사)
        set(value) { prefs.edit().putStringSet("reminder_notified_keys", value).commit() }

    /**
     * 도착 안내 5km 진입 기록 — 지오펜스 ENTER 시 "arrival:{id}:{dayStart}" 적립.
     * 홈 "오늘 시공·도착 안내" 카드는 토글 ON + 이 키가 있어야(=실제 5km 진입) 노출.
     * 그냥 시공일이라고 무조건 뜨던 버그(위치·토글 무시) 방지. commit()=백그라운드 수신기에서 즉시 보존.
     */
    var arrivalEnteredKeys: Set<String>
        get() = prefs.getStringSet("arrival_entered_keys", emptySet()) ?: emptySet()
        set(value) { prefs.edit().putStringSet("arrival_entered_keys", value).commit() }

    /** 홈 "부재중 자동답장 보냄" 배너에서 밀어서 정리(dismiss)한 message_history id 들.
     *  2026-06-06: apply()→commit() — S9 에서 앱이 백그라운드 종료되면 비동기 저장이 날아가
     *  정리한 배너가 다시 뜨던 버그. 즉시 동기 저장으로 보장. */
    var dismissedAutoReplyIds: Set<String>
        get() = prefs.getStringSet("dismissed_auto_reply_ids", emptySet()) ?: emptySet()
        set(value) { prefs.edit().putStringSet("dismissed_auto_reply_ids", value).commit() }

    // ── 팀원 진행 알림(2026-06-06) — 마지막으로 알림 보낸 이벤트 시각(출발/도착/완료 공통, 중복 방지). ──
    var teamEventLastSeenMs: Long
        get() = prefs.getLong("team_depart_last_seen_ms", 0L)
        set(value) { prefs.edit().putLong("team_depart_last_seen_ms", value).commit() }  // commit: 종료 시 유실→재알림 방지. (2026-08-11 알림 감사)
    /** 상담함 "팀원 진행" 배너 밀어서 정리한 event_id 들. commit() = 즉시 저장(백그라운드 종료에도 보존). */
    var dismissedTeamDepartIds: Set<String>
        get() = prefs.getStringSet("dismissed_team_depart_ids", emptySet()) ?: emptySet()
        set(value) { prefs.edit().putStringSet("dismissed_team_depart_ids", value).commit() }

    // ── 협업 진행 알림(2026-06-09) — 상대 사장 출발/도착/완료 owner-events 중복 방지. ──
    var collabEventLastSeenMs: Long
        get() = prefs.getLong("collab_event_last_seen_ms", 0L)
        set(value) { prefs.edit().putLong("collab_event_last_seen_ms", value).commit() }  // commit: 종료 시 유실→재알림 방지. (2026-08-11 알림 감사)
    /** 상담함 "협업 진행" 배너 밀어서 정리한 event_id 들. */
    var dismissedCollabEventIds: Set<String>
        get() = prefs.getStringSet("dismissed_collab_event_ids", emptySet()) ?: emptySet()
        set(value) { prefs.edit().putStringSet("dismissed_collab_event_ids", value).commit() }

    /** 일정 화면에서 밀어서 숨긴 협업 현장 share_id 들. 서버 삭제가 아니라 내 일정 뷰에서만 제외(되돌리기 가능). */
    var hiddenCollabShareIds: Set<String>
        get() = prefs.getStringSet("hidden_collab_share_ids", emptySet()) ?: emptySet()
        set(value) { prefs.edit().putStringSet("hidden_collab_share_ids", value).commit() }

    /** 협업 요청(받는 쪽 pending) 알림 중복 방지 — 이미 알림 띄운 share_id 들. */
    var seenCollabInviteShareIds: Set<String>
        get() = prefs.getStringSet("seen_collab_invite_share_ids", emptySet()) ?: emptySet()
        set(value) { prefs.edit().putStringSet("seen_collab_invite_share_ids", value).commit() }
    /** 협업 요청 알림 첫 폴 시드 완료 여부 — 설치/업데이트 직후 옛 대기 요청 블라스트 방지. */
    var collabInviteSeeded: Boolean
        get() = prefs.getBoolean("collab_invite_seeded", false)
        set(value) = prefs.edit().putBoolean("collab_invite_seeded", value).apply()

    /** 받은 협업 요청을 처음 본 시각(ms) — share_id → firstSeenMs.
     *  수락 유효시간(12h) 계산 앵커. 서버 created_at_ms 가 0(미echo)이면 이걸로 폴백 →
     *  앵커가 항상 ≥ 실제 보낸 시각이라 "보자마자 만료" 같은 잘못된 즉시 만료를 막는다. (2026-06-14 사장님) */
    var collabInviteFirstSeen: Map<String, Long>
        get() = runCatching {
            val raw = prefs.getString("collab_invite_first_seen", null) ?: return@runCatching emptyMap<String, Long>()
            val o = org.json.JSONObject(raw)
            buildMap { o.keys().forEach { k -> put(k, o.optLong(k)) } }
        }.getOrDefault(emptyMap())
        set(value) {
            val o = org.json.JSONObject()
            value.forEach { (k, v) -> o.put(k, v) }
            prefs.edit().putString("collab_invite_first_seen", o.toString()).apply()
        }

    /** 현재 pending 집합과 동기화 — 새 요청은 nowMs 로 첫 관측 기록, 사라진(응답한) 건은 제거. */
    fun syncCollabInviteFirstSeen(pendingShareIds: Set<String>, nowMs: Long) {
        val cur = collabInviteFirstSeen
        val next = HashMap<String, Long>(pendingShareIds.size)
        for (id in pendingShareIds) next[id] = cur[id] ?: nowMs
        if (next != cur) collabInviteFirstSeen = next
    }

    fun collabInviteFirstSeenMs(shareId: String): Long = collabInviteFirstSeen[shareId] ?: 0L

    /** A가 협업 사장에게 배정 요청한 현장 — 일정 카드 "🤝 이름" 표시용. "customerId|이름" Set. (2026-06-13) */
    var collabAssignments: Set<String>
        get() = prefs.getStringSet("collab_assignments", emptySet()) ?: emptySet()
        set(value) { prefs.edit().putStringSet("collab_assignments", value).commit() }

    /** 전문가 배정에서 협업 요청 시 마지막으로 고른 출근시간(24h). 다음 요청 때 미리 선택됨. 기본 오전 9시. (2026-06-14) */
    var lastCollabStartHour: Int
        get() = prefs.getInt("last_collab_start_hour", 9)
        set(value) { prefs.edit().putInt("last_collab_start_hour", value).apply() }

    /** 협업 현장(더보기→협업현장) 목록에서 밀어서 휴지통에 넣은 share_id 들. 서버 삭제 아님 — 목록에서 빼고 휴지통 보관(되살리기 가능). */
    var trashedSharedSiteIds: Set<String>
        get() = prefs.getStringSet("trashed_shared_site_ids", emptySet()) ?: emptySet()
        set(value) { prefs.edit().putStringSet("trashed_shared_site_ids", value).commit() }

    /** 홈 "견적 회신 챙기기" 배너 밀어서 정리한 날(dayStart). 그 날 하루 숨김 — 다음날 다시. */
    var estimateFollowupDismissedDay: Long
        get() = prefs.getLong("estimate_followup_dismissed_day", 0L)
        set(value) { prefs.edit().putLong("estimate_followup_dismissed_day", value).commit() }
    /** 홈 "오늘 보낼 정기 문자" 배너 밀어서 정리한 날(dayStart). 그 날 하루 숨김. */
    var recurringDueDismissedDay: Long
        get() = prefs.getLong("recurring_due_dismissed_day", 0L)
        set(value) { prefs.edit().putLong("recurring_due_dismissed_day", value).commit() }

    // ── 내 말투 학습 (프로토 renderTone). 학습률·before/after 는 서버 API 대기. ──
    var toneLearnEnabled: Boolean
        get() = prefs.getBoolean("tone_learn_enabled", true)
        set(value) = prefs.edit().putBoolean("tone_learn_enabled", value).apply()
    /** 직접 가르친 예문 ("이런 상황엔 이렇게 답해줘"). */
    var toneExamples: Set<String>
        get() = prefs.getStringSet("tone_examples", emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet("tone_examples", value).apply()
    /** 답장 끝에 붙일 시그니처 인사. */
    var toneSignature: String
        get() = prefs.getString("tone_signature", "") ?: ""
        set(value) = prefs.edit().putString("tone_signature", value).apply()

    /**
     * 정산 — 이번 달 목표 매출(만원). 프로토 settleGoal 기본 500.
     * 사장님이 정산 상단 다크카드 "목표 수정" 으로 직접 변경.
     */
    var monthlyGoalManwon: Int
        get() = prefs.getInt(KEY_MONTHLY_GOAL, 500)
        set(value) = prefs.edit().putInt(KEY_MONTHLY_GOAL, value).apply()

    /**
     * 내 업종 (최대 3개, 첫 번째=대표). 업종이 AI지식·가격표·시나리오를 바꿈(해자).
     * "|" 로 join 해서 한 문자열로 저장. 빈 리스트 = 미선택.
     */
    var ownerTrades: List<String>
        get() = prefs.getString(KEY_OWNER_TRADES, "")?.split("|")?.filter { it.isNotBlank() } ?: emptyList()
        set(value) = prefs.edit().putString(KEY_OWNER_TRADES, value.take(3).joinToString("|")).apply()

    /** 수첩 일당/거래처용 자주 쓰는 문구. 구분자  (SMS 본문에 안 나오는 제어문자). */
    /**
     * 오늘 시공 히어로 카드 수동 순서 (고객 ID). 사장님이 꾹 눌러 트렐로식으로 끌어 바꾼 순서.
     * 오늘 목록에 없는 ID(지난 날 것)는 적용 시 무시 → 자동 정리. 비면 시간순 기본.
     */
    var todayHeroOrder: List<Long>
        get() = prefs.getString("today_hero_order", "")?.split(",")
            ?.mapNotNull { it.trim().toLongOrNull() } ?: emptyList()
        set(value) { prefs.edit().putString("today_hero_order", value.joinToString(",")).commit() }

    var workerSmsPhrases: List<String>
        get() = readPhrases(KEY_WORKER_PHRASES, DEFAULT_WORKER_PHRASES)
        set(value) = writePhrases(KEY_WORKER_PHRASES, value)
    var vendorSmsPhrases: List<String>
        get() = readPhrases(KEY_VENDOR_PHRASES, DEFAULT_VENDOR_PHRASES)
        set(value) = writePhrases(KEY_VENDOR_PHRASES, value)

    // ── 본폰 "일정 미러 링크" (더보기 → 본폰에서 일정 보기, 2026-07-13) — docs/ANDROID_HANDOFF_mirror_app.md ──
    //   ⚠️ 옵트인 필수. 기본 꺼짐 = 사장님이 직접 켤 때만 발급/전송(처리방침 §2-2-2). 자동 활성화 금지.
    /** 미러 링크 켜짐 여부. token 이 있고 이 값이 true 일 때만 스냅샷 push. */
    var mirrorEnabled: Boolean
        get() = prefs.getBoolean("mirror_enabled", false)
        set(value) { prefs.edit().putBoolean("mirror_enabled", value).apply() }
    /** 발급받은 링크 토큰(폐기·활성 판단용). null/빈값 = 아직 발급 안 함. */
    var mirrorToken: String?
        get() = prefs.getString("mirror_token", null)?.takeIf { it.isNotBlank() }
        set(value) { prefs.edit().putString("mirror_token", value).apply() }
    /** 본폰에 공유할 링크 URL. */
    var mirrorUrl: String?
        get() = prefs.getString("mirror_url", null)?.takeIf { it.isNotBlank() }
        set(value) { prefs.edit().putString("mirror_url", value).apply() }
    /** 이 업무폰(사업장) 라벨 — 뷰어 칩·색점 이름. 비면 상호(bizName) 또는 "내 일정". */
    var mirrorLabel: String
        get() = prefs.getString("mirror_label", "") ?: ""
        set(value) { prefs.edit().putString("mirror_label", value.trim()).apply() }
    /** 이 업무폰 색 index(뷰어 색점). 발급=0, 합류=1 기본. */
    var mirrorTint: Int
        get() = prefs.getInt("mirror_tint", 0)
        set(value) { prefs.edit().putInt("mirror_tint", value).apply() }
    /** 마지막 스냅샷 전송 시각(ms). "마지막 전송 n분 전" 표시. */
    var mirrorLastPushMs: Long
        get() = prefs.getLong("mirror_last_push_ms", 0L)
        set(value) { prefs.edit().putLong("mirror_last_push_ms", value).apply() }
    /** 마지막으로 전송한 스냅샷 내용 해시 — 바뀐 것 없으면 다시 안 보냄(디바운스+절약). */
    var mirrorLastHash: String?
        get() = prefs.getString("mirror_last_hash", null)
        set(value) { prefs.edit().putString("mirror_last_hash", value).apply() }

    // ── 시공막내 웹 사진 캘린더 뷰어 (2026-08-13) — 미러와 독립. 웹 로그인(QR 승인)하면 켜짐. ──
    /** 웹 뷰어 사용 중 여부. QR 승인 시 true, '웹 로그아웃' 시 false. true 일 때만 스케줄 피드 자동 전송(개인정보·비용 절약). */
    var webViewerActive: Boolean
        get() = prefs.getBoolean("web_viewer_active", false)
        set(value) { prefs.edit().putBoolean("web_viewer_active", value).apply() }
    /** 마지막으로 전송한 스케줄 피드 해시 — 안 바뀌면 다시 안 보냄. */
    var webFeedLastHash: String?
        get() = prefs.getString("web_feed_last_hash", null)
        set(value) { prefs.edit().putString("web_feed_last_hash", value).apply() }
    /** 완료고객 문자대화(customer-content) dedup — "digits=hash;digits=hash" 직렬화. 안 바뀌면 재전송 X. */
    var webContentHashes: String
        get() = prefs.getString("web_content_hashes", "") ?: ""
        set(value) { prefs.edit().putString("web_content_hashes", value).apply() }
    /** v2: 이 업무폰의 고정 공유 코드(서버 부여, 표시용 캐시). 본폰이 이 코드로 공유 신청. */
    var mirrorCode: String?
        get() = prefs.getString("mirror_code", null)?.takeIf { it.isNotBlank() }
        set(value) { prefs.edit().putString("mirror_code", value).apply() }
    /** v2: QR에 실을 URL(서버가 자동수락 시크릿 포함해 내려줌). 없으면 앱이 homeUrl?code= 로 폴백. */
    var mirrorQrUrl: String?
        get() = prefs.getString("mirror_qr_url", null)?.takeIf { it.isNotBlank() }
        set(value) { prefs.edit().putString("mirror_qr_url", value).apply() }
    /** v2: 이미 알림 띄운 공유 신청 share_id 들 — 중복 알림 방지. */
    var mirrorSeenShareIds: Set<String>
        get() = prefs.getStringSet("mirror_seen_share_ids", emptySet()) ?: emptySet()
        set(value) { prefs.edit().putStringSet("mirror_seen_share_ids", value).commit() }

    // ── 알림 종류별 소리 (더보기 → 알림 소리, 2026-07-10) ──
    /** 알림 슬롯 key(new_inquiry/reply/…)에 고른 소리(raw 리소스명 or "silent"). 없으면 default. */
    fun notificationSound(key: String, default: String): String =
        prefs.getString("ntf_snd_$key", default) ?: default
    fun setNotificationSound(key: String, value: String) {
        prefs.edit().putString("ntf_snd_$key", value).apply()
    }
    // (옛 "ntf_snd_ver_*" 버전 카운터는 제거됨 — 채널 id 가 소리 리소스 번호를 직접 품어서(NotificationHelper
    //  channelForSlot) 버전을 따로 셀 필요가 없다. 남은 옛 키는 무해하게 방치.)

    private fun readPhrases(key: String, default: List<String>): List<String> {
        val raw = prefs.getString(key, null) ?: return default
        return raw.split(PHRASE_SEP).filter { it.isNotBlank() }
    }
    private fun writePhrases(key: String, value: List<String>) =
        prefs.edit().putString(key, value.joinToString(PHRASE_SEP)).apply()

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
        private const val KEY_AI_REPLY_PREP = "ai_reply_prep_enabled"
        private const val KEY_NON_CUSTOMER = "non_customer_suffixes"
        private const val KEY_CUSTOMER_ASKED = "customer_asked_suffixes"
        private const val KEY_DEFAULT_NAV_APP = "default_nav_app_key"
        private const val KEY_MANUAL_MMSC_URL = "manual_mmsc_url"
        private const val KEY_MANUAL_MMSC_PROXY = "manual_mmsc_proxy"
        private const val KEY_MANUAL_MMSC_PORT = "manual_mmsc_port"
        private const val KEY_TONE_UPLOAD_CONSENTED = "tone_upload_consented"
        private const val KEY_TONE_LAST_UPLOADED_AT = "tone_last_uploaded_at"
        private const val KEY_TONE_TOTAL_UPLOADED = "tone_total_uploaded"
        private const val KEY_AUTO_CATEGORY_BACKFILLED = "auto_category_backfilled"
        private const val KEY_HAS_SEEN_LOGIN = "has_seen_login"
        private const val KEY_HAS_ONBOARDED = "has_onboarded"
        private const val KEY_OWNER_REGIONS = "owner_regions"
        private const val KEY_BIZ_NAME = "biz_name"
        private const val KEY_BIZ_OWNER = "biz_owner"
        private const val KEY_BIZ_NO = "biz_no"
        private const val KEY_BIZ_ADDR = "biz_addr"
        private const val KEY_BIZ_PHONE = "biz_phone"
        private const val KEY_BIZ_SEAL = "biz_seal"
        private const val KEY_BIZ_BANK = "biz_bank"
        private const val KEY_BIZ_ACCOUNT_NO = "biz_account_no"
        private const val KEY_BIZ_ACCOUNT_HOLDER = "biz_account_holder"
        private const val KEY_BIZ_VALID_DAYS = "biz_quote_valid_days"
        private const val KEY_MONTHLY_GOAL = "monthly_goal_manwon"
        private const val KEY_OWNER_TRADES = "owner_trades"
        private const val KEY_WORKER_PHRASES = "worker_sms_phrases"
        private const val KEY_VENDOR_PHRASES = "vendor_sms_phrases"
        private const val PHRASE_SEP = ""
        private val DEFAULT_WORKER_PHRASES = listOf(
            "내일 현장 가능하세요? 시간 맞춰 연락드릴게요.",
            "오늘 수고 많으셨어요! 일당 입금해드릴게요 😊"
        )
        private val DEFAULT_VENDOR_PHRASES = listOf(
            "자재 주문 부탁드려요.",
            "외상 잔액 확인 부탁드립니다."
        )
    }
}
