package com.detailline.callfollowcrm.domain.estimate

import com.detailline.callfollowcrm.data.local.entity.CustomerEntity

/** "견적 보냈는데 답 없는" 고객 한 명 (한 번 더 여쭤볼 후보). */
data class EstimateFollowupItem(
    val customerId: Long,
    val customerName: String?,
    val phone: String,
    val estimateDayStartMs: Long,
    val daysSince: Int
)

/**
 * 견적 회신 리마인드 계산 — 순수 함수 (android 비의존, 테스트 가능).
 *
 * 사장님 통점: "견적 보냈는데 답 없는 고객을 놓침". 견적 보낸 지 N일 지났는데
 *   아직 시공일이 안 잡혔으면(=진행 안 됨) 홈에서 "한 번 더 여쭤볼까요?" 제안.
 * 자동발송 X — 사장님이 보내기/넘기기. dedupe 는 recurring_message_log 음수 sentinel(RULE_ESTIMATE).
 *
 * 시공일(scheduledWorkDate)이 잡힌 고객은 제외(이미 계약 진행 → 독촉 불필요).
 */
object EstimateFollowupCalc {

    /** 로그 재사용용 sentinel ruleId (정기문자 양수, D1/도착 -1/-2 와도 구분). */
    const val RULE_ESTIMATE = -4L

    private const val DAY_MS = 24L * 60 * 60 * 1000

    /**
     * @param estimateDaysByCustomer customerId → 가장 최근 견적 보낸 날(자정 ms, 호출자 정규화)
     * @param loggedKeys 이미 처리된 (RULE_ESTIMATE, customerId, estimateDayStartMs)
     * @param delayDays 며칠 지나야 리마인드 (기본 2일)
     */
    fun compute(
        estimateDaysByCustomer: Map<Long, Long>,
        customers: List<CustomerEntity>,
        loggedKeys: Set<Triple<Long, Long, Long>>,
        todayStartMs: Long,
        delayDays: Int = 2
    ): List<EstimateFollowupItem> {
        val byId = customers.associateBy { it.id }
        val out = ArrayList<EstimateFollowupItem>()
        for ((cid, estDay) in estimateDaysByCustomer) {
            val c = byId[cid] ?: continue
            if ((c.scheduledWorkDate ?: 0L) > 0L) continue       // 시공일 잡힘 = 진행됨 → 제외
            val days = ((todayStartMs - estDay) / DAY_MS).toInt()
            if (days < delayDays) continue
            val key = Triple(RULE_ESTIMATE, cid, estDay)
            if (key in loggedKeys) continue
            out += EstimateFollowupItem(cid, c.name, c.phoneNumber, estDay, days)
        }
        return out.sortedByDescending { it.daysSince }
    }
}
