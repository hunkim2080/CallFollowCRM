package com.detailline.callfollowcrm.domain.recurring

import com.detailline.callfollowcrm.data.local.entity.CustomerEntity
import com.detailline.callfollowcrm.data.local.entity.RecurringMessageEntity

/** "보낼 때 된" 정기문자 한 건 (규칙 × 고객 × 회차일). */
data class DueItem(
    val ruleId: Long,
    val ruleName: String,
    val customerId: Long,
    val customerName: String?,
    val phone: String,
    val occurrenceDayStartMs: Long,
    val intervalDays: Int,
    val renderedBody: String
)

/**
 * 정기문자 "보낼 때 됐어요" 계산 — 순수 함수 (테스트 가능, android 비의존).
 *
 * 정책: 자동 발송 X. 이 목록은 사장님께 "보낼 때 됐다" 고만 알림. 발송은 사장님이 확인 후.
 * 회차: 시공일(scheduledWorkDate, 자정 정규화) + k×intervalDays. today 이하의 가장 최근 회차 1건만
 *   노출(누적 폭주 방지). 이미 보냄/넘김 로그가 있으면 제외.
 */
object RecurringDueCalc {

    private const val DAY_MS = 24L * 60 * 60 * 1000

    /** "{고객명}" 치환. 이름 없으면 "고객"(템플릿이 "{고객명}님" 이면 "고객님" 으로 자연스럽게). */
    fun render(template: String, name: String?): String =
        template.replace("{고객명}", name?.takeIf { it.isNotBlank() } ?: "고객")

    /**
     * @param loggedKeys 이미 처리된 (ruleId, customerId, occurrenceDayStartMs) 집합
     * @param todayStartMs 오늘 자정 ms (호출자가 정규화해서 전달)
     */
    fun computeDue(
        rules: List<RecurringMessageEntity>,
        customers: List<CustomerEntity>,
        loggedKeys: Set<Triple<Long, Long, Long>>,
        todayStartMs: Long
    ): List<DueItem> {
        val out = ArrayList<DueItem>()
        for (rule in rules) {
            if (!rule.enabled || rule.intervalDays <= 0) continue
            val period = rule.intervalDays.toLong() * DAY_MS
            for (c in customers) {
                val base = c.scheduledWorkDate ?: continue
                if (base > todayStartMs) continue                // 시공일이 미래
                if (rule.targetCategoryId != null && c.categoryId != rule.targetCategoryId) continue
                val diff = todayStartMs - base
                if (diff < period) continue                      // 아직 첫 회차 전 (시공 후 N일 미만)
                val k = diff / period                            // 최근 회차 번호 (>=1)
                val occ = base + k * period
                val key = Triple(rule.id, c.id, occ)
                if (key in loggedKeys) continue                  // 이미 보냄/넘김
                out += DueItem(
                    ruleId = rule.id,
                    ruleName = rule.name,
                    customerId = c.id,
                    customerName = c.name,
                    phone = c.phoneNumber,
                    occurrenceDayStartMs = occ,
                    intervalDays = rule.intervalDays,
                    renderedBody = render(rule.bodyTemplate, c.name)
                )
            }
        }
        return out.sortedByDescending { it.occurrenceDayStartMs }
    }
}
