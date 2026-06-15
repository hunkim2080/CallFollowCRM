package com.detailline.callfollowcrm.data.local.seed

import com.detailline.callfollowcrm.data.local.entity.MessageTemplateEntity
import com.detailline.callfollowcrm.data.repository.MessageTemplateRepository
import com.detailline.callfollowcrm.domain.model.TemplateCategory

object DefaultTemplates {

    suspend fun seedIfEmpty(repo: MessageTemplateRepository) {
        if (repo.count() > 0) return
        val now = System.currentTimeMillis()
        defaults(now).forEach { repo.insert(it) }
    }

    private fun defaults(now: Long): List<MessageTemplateEntity> = listOf(
        MessageTemplateEntity(
            title = "사진 요청",
            body = """
                안녕하세요.
                방금 상담드린 내용 확인했습니다.
                정확한 견적을 위해 시공 공간 사진을 보내주시면 상태 확인 후 안내드리겠습니다.
            """.trimIndent(),
            category = TemplateCategory.PHOTO_REQUEST.name,
            isDefault = true, isActive = true, createdAt = now, updatedAt = now
        ),
        MessageTemplateEntity(
            title = "견적 안내",
            body = """
                안녕하세요.
                방금 상담드린 내용 기준으로 안내드립니다.
                정확한 견적은 시공 공간, 기존 메지 상태, 자재 선택에 따라 달라질 수 있습니다.
                사진 확인 후 자세히 안내드리겠습니다.
            """.trimIndent(),
            category = TemplateCategory.ESTIMATE.name,
            isDefault = true, isActive = true, createdAt = now, updatedAt = now
        ),
        MessageTemplateEntity(
            title = "예약 안내",
            body = """
                안녕하세요.
                상담드린 일정으로 예약을 원하시면 가능 날짜와 시공 공간을 다시 한번 확인 부탁드립니다.
                확인 후 예약 절차 안내드리겠습니다.
            """.trimIndent(),
            category = TemplateCategory.RESERVATION.name,
            isDefault = true, isActive = true, createdAt = now, updatedAt = now
        ),
        MessageTemplateEntity(
            title = "시공 전 안내",
            body = """
                안녕하세요.
                시공 전 안내드립니다.
                시공 공간의 짐은 미리 이동해주시고, 바닥은 가능한 건조된 상태가 좋습니다.
                현장 상태에 따라 추가 안내드리겠습니다.
            """.trimIndent(),
            category = TemplateCategory.PRE_WORK.name,
            isDefault = true, isActive = true, createdAt = now, updatedAt = now
        ),
        MessageTemplateEntity(
            title = "부재중 안내",
            body = """
                안녕하세요.
                전화드렸으나 연결이 어려워 문자 남깁니다.
                편하실 때 답장 주시면 확인 후 안내드리겠습니다.
            """.trimIndent(),
            category = TemplateCategory.MISSED_CALL.name,
            isDefault = true, isActive = true, createdAt = now, updatedAt = now
        )
    )
}
