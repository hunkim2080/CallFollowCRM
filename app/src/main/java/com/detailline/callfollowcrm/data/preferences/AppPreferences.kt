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

    // ── 자동 문자 — 프로토 자동문자 시트(인라인 텍스트). 비면 위 템플릿ID fallback. (2026-06-03) ──
    /** 부재중 자동응답 — 처음 연락한 고객(신규). */
    var autoMissedNewText: String
        get() = prefs.getString("auto_missed_new_text",
            "안녕하세요, 디테일라인 줄눈입니다 😊 지금 시공 중이라 전화를 못 받았어요. 어떤 시공 문의신지 문자로 남겨주시면 바로 견적 안내드릴게요!") ?: ""
        set(value) = prefs.edit().putString("auto_missed_new_text", value).apply()
    /** 부재중 자동응답 — 다시 연락한 고객(단골·기존). */
    var autoMissedReturnText: String
        get() = prefs.getString("auto_missed_return_text",
            "고객님, 전화 못 받아 죄송해요! 지금 시공 중이라 마치는 대로 바로 연락드릴게요. 급하시면 문자로 남겨주세요 😊") ?: ""
        set(value) = prefs.edit().putString("auto_missed_return_text", value).apply()
    /** 시공 D-1 안내 on/off (알림으로 "보낼까요?" 물어봄). */
    var d1AutoEnabled: Boolean
        get() = prefs.getBoolean("d1_auto_enabled", true)
        set(value) = prefs.edit().putBoolean("d1_auto_enabled", value).apply()
    /** 통화 자동 요약 on/off — 통화 끝나면 에이닷 폴더(녹음/텍스트)를 스캔해 자동 요약. 기본 ON. (2026-06-14 사장님) */
    var autoSummaryEnabled: Boolean
        get() = prefs.getBoolean("auto_summary_enabled", true)
        set(value) = prefs.edit().putBoolean("auto_summary_enabled", value).apply()
    /** 막내 비서 현재 단계(0~9, 10레벨마다). 앱 시작 시 Mascot 변신 복원용. (2026-06-14) */
    var agentTier: Int
        get() = prefs.getInt("agent_tier", 0)
        set(value) = prefs.edit().putInt("agent_tier", value).apply()
    /** 길찾기 기본 지도앱(tmap/kakao/naver/default). 비면 처음 길찾기 때 선택받음. (2026-06-14) */
    var navApp: String
        get() = prefs.getString("nav_app", "") ?: ""
        set(value) = prefs.edit().putString("nav_app", value).apply()
    /** D-1 안내 묻는 시각(시, 0~23). 기본 오전 9시. */
    var d1SendHour: Int
        get() = prefs.getInt("d1_send_hour", 9)
        set(value) = prefs.edit().putInt("d1_send_hour", value).apply()
    /** D-1 안내 문자 본문. */
    var d1AutoText: String
        get() = prefs.getString("d1_auto_text",
            "고객님, 내일 시공 예정입니다 😊 현장 정리(가구·물건 비움) 부탁드리고, 주차 가능 여부만 미리 알려주세요. 디테일라인 줄눈 드림.") ?: ""
        set(value) = prefs.edit().putString("d1_auto_text", value).apply()
    /** 현장 도착 안내(위치 기반) on/off. 트리거는 추후. */
    var arrivalAutoEnabled: Boolean
        get() = prefs.getBoolean("arrival_auto_enabled", false)
        set(value) = prefs.edit().putBoolean("arrival_auto_enabled", value).apply()
    var arrivalAutoText: String
        get() = prefs.getString("arrival_auto_text",
            "고객님, 30분 뒤 도착 예정입니다 😊 잠시 후 뵐게요! 디테일라인 줄눈 드림.") ?: ""
        set(value) = prefs.edit().putString("arrival_auto_text", value).apply()

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

    /**
     * 2026-06-07 — 카테고리 규칙 수정(날짜 등록=시공대기, 상담만=미분류) 후 1회 재정리 완료 여부.
     * 옛 버그로 "상담만 했는데 시공대기"가 된 고객들을 새 규칙으로 한 번 더 분류해 정리.
     */
    var autoCategoryRebuiltV2: Boolean
        get() = prefs.getBoolean("auto_category_rebuilt_v2", false)
        set(value) = prefs.edit().putBoolean("auto_category_rebuilt_v2", value).apply()

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

    /** 2026-06-07 — 스팸/광고 번호 앞자리. 이 앞자리로 시작하면 자동답장·AI 준비 X + 목록 제외. 기본 070. */
    var spamPrefixes: Set<String>
        get() = prefs.getStringSet("spam_prefixes", setOf("070")) ?: setOf("070")
        set(value) { prefs.edit().putStringSet("spam_prefixes", value).commit() }

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

    // ── 시간 기반 알림(D-1·잔금·브리핑) 중복방지 — 이미 보낸 키(예 "d1:{id}:{dateMs}"). ──
    var reminderNotifiedKeys: Set<String>
        get() = prefs.getStringSet("reminder_notified_keys", emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet("reminder_notified_keys", value).apply()

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
        set(value) = prefs.edit().putLong("team_depart_last_seen_ms", value).apply()
    /** 상담함 "팀원 진행" 배너 밀어서 정리한 event_id 들. commit() = 즉시 저장(백그라운드 종료에도 보존). */
    var dismissedTeamDepartIds: Set<String>
        get() = prefs.getStringSet("dismissed_team_depart_ids", emptySet()) ?: emptySet()
        set(value) { prefs.edit().putStringSet("dismissed_team_depart_ids", value).commit() }

    // ── 협업 진행 알림(2026-06-09) — 상대 사장 출발/도착/완료 owner-events 중복 방지. ──
    var collabEventLastSeenMs: Long
        get() = prefs.getLong("collab_event_last_seen_ms", 0L)
        set(value) = prefs.edit().putLong("collab_event_last_seen_ms", value).apply()
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
