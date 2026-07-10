package com.detailline.callfollowcrm.presentation.navigation

object Destinations {
    const val LOGIN = "login"
    /** 회원가입(폰 인증번호) — Play 공개 첫 화면. (2026-07-04) */
    const val SIGNUP = "signup"
    /** 개인정보 동의 게이트 — 진입 1회(필수+선택). (추가97 2026-07-06) */
    const val CONSENT = "consent"
    const val ONBOARDING = "onboarding"
    const val PERMISSIONS = "permissions"
    const val HOME = "home"
    const val FOLLOW_UP = "follow_up"
    const val FOLLOW_UP_WITH_ARG = "follow_up?phone={phone}&callRecordId={callRecordId}&templateId={templateId}"
    fun followUp(phone: String? = null, callRecordId: Long? = null, templateId: Long? = null): String {
        val p = phone?.let { "phone=$it" }.orEmpty()
        val c = callRecordId?.let { "callRecordId=$it" }.orEmpty()
        val t = templateId?.let { "templateId=$it" }.orEmpty()
        val query = listOf(p, c, t).filter { it.isNotEmpty() }.joinToString("&")
        return if (query.isEmpty()) "follow_up" else "follow_up?$query"
    }

    const val CUSTOMER_DETAIL_WITH_ARG = "customer/{customerId}"
    fun customerDetail(customerId: Long) = "customer/$customerId"

    /**
     * 메인 채팅 화면. 번호는 필수, customerId 는 있으면 빠른 로드용 (없으면 phone 으로 lookup).
     * customerId = -1 sentinel = 없음.
     * editIssuedId = 발행 이력 id — 채팅 열면서 그 접수서를 "수정하기"로 바로 EstSheet 재오픈. -1=없음. (2026-07-10 사장님)
     */
    const val CHAT_WITH_ARG = "chat?phone={phone}&customerId={customerId}&editIssuedId={editIssuedId}"
    fun chat(phone: String, customerId: Long? = null, editIssuedId: Long? = null): String {
        val safePhone = phone.ifBlank { "" }
        val cid = customerId ?: -1L
        val eid = editIssuedId ?: -1L
        return "chat?phone=$safePhone&customerId=$cid&editIssuedId=$eid"
    }

    const val TEMPLATE_LIST = "templates"
    /** 자주 쓰는 문자 찾기 — 반복 발송 문자를 템플릿으로 저장 제안. 서버無, 폰 빈도집계. (2026-07-02) */
    const val TEMPLATE_DISCOVER = "template_discover"
    const val TEMPLATE_EDIT_WITH_ARG = "template_edit?id={id}"
    fun templateEdit(id: Long?) = if (id == null) "template_edit" else "template_edit?id=$id"

    const val SETTINGS = "settings"
    /** 더보기 → 자동 문자(부재중 자동 응답 펼친 상태). 상담함 자동답장 알림 길게누름 진입. */
    const val SETTINGS_AUTOSMS = "settings_autosms"
    /** 더보기 → 알림 소리 — 알림 종류별 소리 고르기 + 미리듣기. (2026-07-10 사장님) */
    const val SOUND_SETTINGS = "sound_settings"
    /** 마감 브리핑 — 오늘 신규/입금/내일 시공 정리 화면. 마감 브리핑 알림 탭 진입. */
    const val CLOSING_BRIEF = "closing_brief"
    const val PRICING_ITEMS = "pricing_items"
    /** 문자에서 가격 불러오기 — 2단계 확인 화면(문자 기반 가격 추출). 가격표 관리에서 진입. (2026-07-02) */
    const val PRICING_EXTRACT = "pricing_extract"
    const val SCHEDULE = "schedule"
    /** 일정 탭 — day(ms) 주면 그 날 선택해서 진입(홈 "다음 시공" 카드). 인자 없으면 오늘. */
    const val SCHEDULE_WITH_ARG = "schedule?day={day}"
    fun schedule(dayMs: Long) = "schedule?day=$dayMs"
    const val SCHEDULE_ADD = "schedule_add"
    /** 직접 등록 — 일정에서 누른 날(ms)을 시공일 기본값으로. 인자 없으면/<=0 이면 오늘. */
    const val SCHEDULE_ADD_WITH_ARG = "schedule_add?day={day}"
    fun scheduleAdd(dayMs: Long?) = if (dayMs == null || dayMs <= 0L) SCHEDULE_ADD else "schedule_add?day=$dayMs"
    const val SETTLEMENT = "settlement"
    const val STATS = "stats"
    /** 통계 "다녀온 현장" 셀 탭 → 이번 달 시공 현장 목록(프로토 s-visited). */
    const val VISITED = "visited"
    const val SEARCH = "search"
    const val CUSTOMERS = "customers"
    const val NEW_LEADS = "new_leads"
    const val BUSINESS_INFO = "business_info"
    const val NOTEBOOK = "notebook"
    const val TEAM = "team"
    const val COLLAB_SITES = "collab_sites"
    /** 협업 현장 — share 로 그 현장 자동 열기, tab="shared" 면 "내가 공유한 현장" 탭부터. 더보기 진입은 인자 없음. */
    const val COLLAB_SITES_WITH_ARG = "collab_sites?share={share}&tab={tab}"
    fun collabSites(shareId: String? = null, tab: String? = null): String {
        val s = shareId?.takeIf { it.isNotBlank() }?.let { "share=$it" }.orEmpty()
        val t = tab?.takeIf { it.isNotBlank() }?.let { "tab=$it" }.orEmpty()
        val q = listOf(s, t).filter { it.isNotEmpty() }.joinToString("&")
        return if (q.isEmpty()) COLLAB_SITES else "collab_sites?$q"
    }
    const val REPORT = "report"
    const val TRADE_SELECT = "trade_select"
    const val RECURRING = "recurring"
    const val RECURRING_DUE = "recurring_due"
    const val SCHEDULE_REMINDER = "schedule_reminder"
    const val ESTIMATE_FOLLOWUP = "estimate_followup"
    const val AI_MESSAGE = "ai_message"
    const val STYLE_LEARNING = "style_learning"
    const val PRINCIPLES = "principles"
    /** 더보기 → 스팸 차단 번호 목록(잘못 등록 시 복구). (2026-06-23 사장님) */
    const val SPAM_LIST = "spam_list"
    /** 더보기 → 사생활 번호 목록(업무폰=개인폰 겸용 — 링고 제외). (2026-06-23 사장님) */
    const val PERSONAL_LIST = "personal_list"

    /** 통화 정리해서 보내기 — 음성/붙여넣기/직접 → AI 요약 → 고객용 문자 초안 → 발송. */
    const val CALL_SUMMARY_WITH_ARG = "call_summary?phone={phone}&name={name}"
    fun callSummary(phone: String, name: String? = null): String {
        val n = name?.takeIf { it.isNotBlank() }?.let { "&name=${android.net.Uri.encode(it)}" }.orEmpty()
        return "call_summary?phone=${android.net.Uri.encode(phone)}$n"
    }

    // 2026-05-25: PIPELINE 라우트 폐기 — CustomerStatus enum 제거 + 카테고리 시스템으로 통일.
}
