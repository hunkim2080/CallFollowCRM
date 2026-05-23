package com.detailline.callfollowcrm.presentation.navigation

object Destinations {
    const val ONBOARDING = "onboarding"
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
    const val SCHEDULE = "schedule"
    const val AI_MESSAGE = "ai_message"
    const val STYLE_LEARNING = "style_learning"

    const val PIPELINE_WITH_ARG = "pipeline/{statusName}"
    /** statusName 은 CustomerStatus enum name (예: ESTIMATE_PENDING). 한글 라벨이 아니라서 URL 경로 안전. */
    fun pipeline(statusName: String): String = "pipeline/$statusName"
}
