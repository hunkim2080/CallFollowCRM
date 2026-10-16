package com.detailline.callfollowcrm.presentation.screen.customer

import com.detailline.callfollowcrm.data.AppContainer
import com.detailline.callfollowcrm.data.local.entity.CustomerEntity
import com.detailline.callfollowcrm.presentation.util.SaveGuard
import com.detailline.callfollowcrm.util.DateTimeUtils
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ⏸️ **일정 보류 흐름** — 고객 상세에서 「일정 보류」를 눌렀을 때 하는 일 한 묶음. (2026-10-16 사장님, 프로토 artifact/D4EwQSrXk4TFPzc9gjW6Cs)
 *
 *   사장님: *"시공 계약금까지 다 받았는데, 고객이 와이프가 아프다고 일정을 추후에 본인이 다시 연락하겠다고 함.
 *   그럼 이 사람을 어디다 둬야 하지?"*
 *
 *   · 보류 찍기([hold]) — 잡혀 있던 날짜는 **달력에서만 뺀다**(JobRepository.unscheduleJob — 돈·기록은 그대로).
 *     ⚠️ 「시공 예약 취소」(cancelJob)를 쓰면 안 된다 — 그건 **계약금까지 백지**로 만든다(2026-08-28 결정).
 *     보류는 **돈 낸 손님을 잊지 않으려는 칸**이라 돈을 지우면 뜻이 없다.
 *     그 날 팀원 배정·일당·협업은 취소 경로와 **같은 정리**를 한다 — 날짜가 없는데 배정만 남으면 안 된다.
 *   · 풀기([release]) — 잘못 눌렀으면 방금 뺀 날짜로 되돌린다. 날짜를 새로 잡으면 보류는 **자동으로** 풀린다(OnHold.afterDateSet).
 *   · 협업 끝내기([endCollab]) — 예약 취소(CustomerDetailViewModel.updateScheduledWorkDate null)와 보류가 **같은 길**(§12).
 *
 *   사장님이 누른 저장이라 실패하면 화면에 말한다(SaveGuard, §13①). 연타는 [inFlight] 로 한 번만(§13③).
 *   CustomerDetailViewModel 이 들고 부른다 — 화면 파일이 커지지 않게 여기로 뗐다(size_guard).
 */
internal class CustomerHoldFlow(
    private val container: AppContainer,
    private val customerId: Long,
    private val toast: MutableStateFlow<String?>
) {
    /** 보류로 보내며 달력에서 뺀 건 — (건 id, 빠지기 전 날짜). 되돌리기용. 화면이 살아 있는 동안만. */
    private var lastUnscheduled: Pair<Long, Long>? = null
    private var inFlight = false

    /** 되돌릴 날짜가 있나 — 방금 이 화면에서 보류하며 달력에서 뺀 날짜. */
    val restorableDate: Long? get() = lastUnscheduled?.second

    /**
     * 보류 찍기. 이유는 안 적어도 된다(빈 값 → null).
     *   @param onHeldWithDate 날짜가 있던 손님을 보류했을 때 — (옛 날짜, 옛 시간, 이유) 로 변경 이력을 남기라고 부른다.
     */
    suspend fun hold(c: CustomerEntity?, reason: String, onHeldWithDate: (oldDate: Long, oldMinutes: Int?, reason: String?) -> Unit) {
        if (inFlight || c == null) return
        inFlight = true
        try {
            val now = System.currentTimeMillis()
            val oldDate = c.scheduledWorkDate
            val day = oldDate?.let { DateTimeUtils.startOfDay(it) }
            val cleaned = reason.trim().takeIf { it.isNotBlank() }
            withContext(NonCancellable) {
                SaveGuard.run("일정 보류", toast) {
                    container.customerRepository.setOnHold(customerId, now, cleaned)
                    if (day != null) {
                        val job = runCatching { container.jobRepository.jobAt(customerId, day) }.getOrNull()
                        if (job != null) {
                            container.jobRepository.unscheduleJob(job.id, now)
                            lastUnscheduled = job.id to day
                        } else {
                            // 건 행이 없는 옛 데이터 — 고객 카드 날짜만 비운다(돈은 mutate 가 안 건드린다).
                            container.customerRepository.updateScheduledWorkDate(customerId, null)
                            lastUnscheduled = null
                        }
                        container.teamAssignmentRepository.deleteForCustomerAndDay(customerId, day)
                        container.jobCrewRepository.deleteByCustomerAndDay(customerId, day)
                    }
                } ?: return@withContext
                if (day != null) endCollab()
                toast.value = if (day != null) "보류로 옮겼어요 — 달력에서 빠지고 계약금은 그대로예요"
                              else "보류로 옮겼어요 — 상담함 [보류] 칸에서 챙길 수 있어요"
            }
            if (oldDate != null) onHeldWithDate(oldDate, c.scheduledWorkMinutes, cleaned)
        } finally {
            inFlight = false
        }
    }

    /** 보류 풀기. `restoreDate` = 방금 보류하며 달력에서 뺀 날짜를 되돌린다(잘못 눌렀을 때). */
    suspend fun release(restoreDate: Boolean) {
        val now = System.currentTimeMillis()
        withContext(NonCancellable) {
            SaveGuard.run("보류 풀기", toast) {
                container.customerRepository.setOnHold(customerId, null, null)
                val restore = lastUnscheduled
                if (restoreDate && restore != null) {
                    container.jobRepository.rescheduleJob(restore.first, restore.second, now)
                    lastUnscheduled = null
                }
            } ?: return@withContext
            toast.value = "보류를 풀었어요"
        }
    }

    /** 보류 이유 메모만 고치기 — 사장님이 적은 글이라 실패하면 말한다. */
    suspend fun updateReason(reason: String) {
        withContext(NonCancellable) {
            SaveGuard.run("보류 메모", toast) { container.customerRepository.updateOnHoldReason(customerId, reason) }
        }
    }

    /**
     * 🤝 이 고객의 협업 배정을 전부 끝낸다 — 내 목록(prefs)에서 빼고 **협업 사장님들께도 "이 협업 끝났어요"** 를 보낸다. (2026-10-13 사장님)
     *   전엔 내 목록에서만 지우고 서버/상대엔 안 보내, 상대 일정에 유령처럼 남았다
     *   (바꾸기=notifyCollabReschedule 는 전파되는데 취소만 빠져 있었다).
     *   보내기(cancel_then_end)·상대 알림·상대 일정 삭제는 서버/핸들러가 이미 다 한다 — removeCollabAssignment 와 같은 길.
     */
    fun endCollab() {
        val before = container.preferences.collabAssignments
        val after = before.filterNot { it.split('|').getOrNull(0)?.toLongOrNull() == customerId }.toSet()
        if (after.size == before.size) return
        container.preferences.collabAssignments = after
        val owner = container.preferences.bizPhone.filter { it.isDigit() }
        if (owner.length < 9) return
        val endedShareIds = (before - after).mapNotNull { raw ->
            com.detailline.callfollowcrm.domain.collab.CollabAssignmentLine.parse(raw)
                ?.shareId?.trim()?.takeIf { it.isNotBlank() }
        }.distinct()
        if (endedShareIds.isEmpty()) return
        container.applicationScope.launch {
            endedShareIds.forEach { sid ->
                runCatching {
                    container.outbox.enqueue(
                        com.detailline.callfollowcrm.domain.outbox.OutboxKind.COLLAB_END,
                        sid,
                        org.json.JSONObject().put("mode", "cancel_then_end").toString()
                    )
                }
            }
            container.outbox.tryNow()
        }
    }
}
