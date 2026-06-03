package com.detailline.callfollowcrm.presentation.navigation

object Destinations {
    const val LOGIN = "login"
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
     */
    const val CHAT_WITH_ARG = "chat?phone={phone}&customerId={customerId}"
    fun chat(phone: String, customerId: Long? = null): String {
        val safePhone = phone.ifBlank { "" }
        val cid = customerId ?: -1L
        return "chat?phone=$safePhone&customerId=$cid"
    }

    const val TEMPLATE_LIST = "templates"
    const val TEMPLATE_EDIT_WITH_ARG = "template_edit?id={id}"
    fun templateEdit(id: Long?) = if (id == null) "template_edit" else "template_edit?id=$id"

    const val SETTINGS = "settings"
    const val PRICING_ITEMS = "pricing_items"
    const val SCHEDULE = "schedule"
    const val SCHEDULE_ADD = "schedule_add"
    const val SETTLEMENT = "settlement"
    const val STATS = "stats"
    const val SEARCH = "search"
    const val CUSTOMERS = "customers"
    const val NEW_LEADS = "new_leads"
    const val BUSINESS_INFO = "business_info"
    const val NOTEBOOK = "notebook"
    const val REPORT = "report"
    const val TRADE_SELECT = "trade_select"
    const val RECURRING = "recurring"
    const val RECURRING_DUE = "recurring_due"
    const val SCHEDULE_REMINDER = "schedule_reminder"
    const val ESTIMATE_FOLLOWUP = "estimate_followup"
    const val AI_MESSAGE = "ai_message"
    const val STYLE_LEARNING = "style_learning"

    /** 통화 정리해서 보내기 — 음성/붙여넣기/직접 → AI 요약 → 고객용 문자 초안 → 발송. */
    const val CALL_SUMMARY_WITH_ARG = "call_summary?phone={phone}&name={name}"
    fun callSummary(phone: String, name: String? = null): String {
        val n = name?.takeIf { it.isNotBlank() }?.let { "&name=${android.net.Uri.encode(it)}" }.orEmpty()
        return "call_summary?phone=${android.net.Uri.encode(phone)}$n"
    }

    // 2026-05-25: PIPELINE 라우트 폐기 — CustomerStatus enum 제거 + 카테고리 시스템으로 통일.
}
