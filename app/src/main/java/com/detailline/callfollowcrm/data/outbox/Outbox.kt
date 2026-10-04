package com.detailline.callfollowcrm.data.outbox

import com.detailline.callfollowcrm.data.local.dao.OutboxDao
import com.detailline.callfollowcrm.data.local.entity.OutboxEntity
import com.detailline.callfollowcrm.domain.outbox.OutboxKind
import com.detailline.callfollowcrm.domain.outbox.OutboxRow
import com.detailline.callfollowcrm.domain.outbox.OutboxRules
import com.detailline.callfollowcrm.domain.outbox.RoundAction
import com.detailline.callfollowcrm.domain.outbox.Verdict
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import java.util.UUID

/**
 * 📮 **우체통 본체** — 넣고(enqueue) · 바로 보내보고(tryNow) · 모아 보낸다(drain). 설계 §4.
 *
 *   셈(순서·판정·백오프·합치기)은 전부 **OutboxRules** 한 곳에 있다. 여기선 Room·네트워크를 붙일 뿐이다.
 *   drain 은 OutboxRules.simulateRound 를 **그대로** 쓴다 — 테스트가 보는 로직과 도는 로직이 같다(§12).
 */
class Outbox(
    private val dao: OutboxDao,
    private val network: com.detailline.callfollowcrm.util.NetworkWatch,
    private val ownerPhoneProvider: () -> String,
    handlers: List<OutboxHandler>,
    private val scope: CoroutineScope
) {
    private val handlerByWire: Map<String, OutboxHandler> = handlers.associateBy { it.kind.wire }
    private val drainMutex = Mutex()

    init {
        // 📡 인터넷 돌아오면 자동으로 비운다(§4-B 네트워크 복구).
        network.onAvailable { kick() }
    }

    /**
     * 넣기 — 로컬 저장과 **같은 호흡**(SaveGuard 블록 안). 실패하면 던진다(SaveGuard 가 말함).
     *   같은 현장에 겹치면 OutboxRules.coalescePlan 대로 합친다(알림 한 번·끝낸 현장 정리).
     */
    suspend fun enqueue(
        kind: OutboxKind,
        targetKey: String,
        payloadJson: String,
        opKey: String = UUID.randomUUID().toString(),
        /** 비워두면 지금 로그인 번호. 사진은 "" 로 — 기기 것이라 번호 바뀌어도 올려야 한다(§1-b). */
        ownerPhone: String? = null
    ) {
        require(targetKey.isNotBlank()) { "targetKey 비어있음" }
        val owner = (ownerPhone ?: ownerPhoneProvider()).filter { it.isDigit() }
        val now = System.currentTimeMillis()
        val existing = dao.pendingForTarget(targetKey).map { it.toRow() }
        val plan = OutboxRules.coalescePlan(kind, existing)
        if (plan.skipInsert) return                    // KEEP_EXISTING — 이미 있으면 그대로(백오프 유지)
        if (plan.deleteIds.isNotEmpty()) dao.deleteAll(plan.deleteIds)
        val reuse = plan.reuseId
        if (reuse != null) {
            dao.swapPayload(reuse, payloadJson)
        } else {
            dao.insert(
                OutboxEntity(
                    kind = kind.wire, targetKey = targetKey, opKey = opKey,
                    ownerPhone = owner, payloadJson = payloadJson, createdAtMs = now
                )
            )
        }
    }

    /** 넣은 직후 한 번 바로 보내본다(온라인이면 지금과 체감 동일). */
    fun tryNow() = kick()

    /** 비우기 신호 — 앱 시작·화면 복귀·60초 루프·네트워크 복구·워커가 전부 이걸 부른다(§4-B). */
    fun kick() {
        scope.launch { drain() }
    }

    suspend fun drain() {
        if (!drainMutex.tryLock()) return      // 연타·겹침 막기(§13③)
        try {
            if (!network.isOnline()) return     // 오프라인이면 네트워크·타임아웃 낭비 안 함
            val owner = ownerPhoneProvider().filter { it.isDigit() }
            if (owner.length < 9) return
            val now = System.currentTimeMillis()
            dao.purgeOldDead(now - OutboxRules.DEAD_KEEP_MS)
            val due = dao.dueNow(now)
            if (due.isEmpty()) return
            val byId = due.associateBy { it.id }
            val rows = due.map { it.toRow() }
            val actions = OutboxRules.simulateRound(rows, owner, now, { Math.random() }) { row ->
                val e = byId[row.id]
                when {
                    e == null -> Verdict.DEAD to 400
                    else -> {
                        val h = handlerByWire[e.kind]
                        if (h == null) Verdict.DEAD to 400
                        else runCatching { h.send(e.payloadJson, e.ownerPhone, e.targetKey, e.opKey) }
                            .getOrElse { Verdict.RETRY to null }
                    }
                }
            }
            applyActions(actions)
        } finally {
            drainMutex.unlock()
        }
    }

    private suspend fun applyActions(actions: List<RoundAction>) {
        for (a in actions) when (a) {
            is RoundAction.Done -> dao.delete(a.id)
            is RoundAction.Retry -> dao.markRetry(a.id, a.attempts, a.nextAttemptAtMs, a.error)
            is RoundAction.Dead -> dao.markDead(a.id, a.reason, a.reason)
            is RoundAction.Skipped -> Unit
        }
    }

    /** 사장님께 보일 것(dead + 하루 넘긴 pending). 2단계 홈 띠·목록이 구독한다. */
    fun observeTrouble(now: Long = System.currentTimeMillis()): Flow<List<OutboxEntity>> =
        dao.observeTrouble(now - OutboxRules.STALE_MS)

    /** 진단 본문용(한 번 읽기) — 「미전송 N건·사유」. */
    suspend fun troubleNow(): List<OutboxEntity> =
        dao.troubleNow(System.currentTimeMillis() - OutboxRules.STALE_MS)

    /** 이 종류로 이미 우체통에 든 targetKey 들(pending·dead). 사진 feeder 의 중복·되살아남 방지. */
    suspend fun targetsForKind(wire: String): List<String> = dao.targetsForKind(wire)

    private fun OutboxEntity.toRow() =
        OutboxRow(id, kind, targetKey, ownerPhone, attempts, createdAtMs)
}
