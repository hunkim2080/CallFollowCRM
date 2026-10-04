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
import kotlinx.coroutines.sync.withLock
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
    private val enqueueMutex = Mutex()   // 넣기는 '읽고-합치고-쓰기'라 연타 때 두 줄 생길 수 있다 → 직렬화(Fable #3·§13③)

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
        enqueueMutex.withLock {   // 읽고-합치고-쓰기를 한 번에(연타 때 두 줄 방지)
            val existing = dao.pendingForTarget(targetKey).map { it.toRow() }
            val plan = OutboxRules.coalescePlan(kind, existing)
            if (plan.skipInsert) return                // KEEP_EXISTING — 이미 있으면 그대로(백오프 유지)
            if (plan.deleteIds.isNotEmpty()) dao.deleteAll(plan.deleteIds)
            val reuse = plan.reuseId
            if (reuse != null) {
                // payload 갈 때 opKey 도 새로(Fable #1 — 같은 키면 서버가 옛 응답 캐시로 돌려줌).
                dao.swapPayload(reuse, payloadJson, UUID.randomUUID().toString())
            } else {
                dao.insert(
                    OutboxEntity(
                        kind = kind.wire, targetKey = targetKey, opKey = opKey,
                        ownerPhone = owner, payloadJson = payloadJson, createdAtMs = now
                    )
                )
            }
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
                            .getOrElse { err ->
                                // 네트워크(IOException)만 재시도. 깨진 payload(JSONException 등)는 7일 끌지 말고 바로 죽인다.
                                if (err is java.io.IOException) Verdict.RETRY to null else Verdict.DEAD to 400
                            }
                    }
                }
            }
            applyActions(actions, byId)
        } finally {
            drainMutex.unlock()
        }
    }

    // 쓸 때 **보낼 때 본 opKey 와 같을 때만** 고친다 — 보내는 사이에 payload 가 갈렸으면(새 opKey) 그 행은 안 건드린다.
    private suspend fun applyActions(actions: List<RoundAction>, byId: Map<Long, OutboxEntity>) {
        for (a in actions) {
            val sentKey = byId[a.id]?.opKey ?: continue
            when (a) {
                is RoundAction.Done -> dao.deleteDone(a.id, sentKey)
                is RoundAction.Retry -> dao.markRetryIf(a.id, sentKey, a.attempts, a.nextAttemptAtMs, a.error)
                is RoundAction.Dead -> dao.markDeadIf(a.id, sentKey, a.reason, a.reason)
                is RoundAction.Skipped -> Unit
            }
        }
    }

    /** 홈 띠·목록이 구독 — dead + pending **전부**. 「하루 넘김·주인 거르기」는 읽는 쪽이 지금 시각으로 판단(Fable #7). */
    fun observeTroubleRaw(): Flow<List<OutboxEntity>> = dao.observeDeadAndPending()

    /** 진단 본문용(한 번 읽기) — 「미전송 N건·사유」. */
    suspend fun troubleNow(): List<OutboxEntity> =
        dao.troubleNow(System.currentTimeMillis() - OutboxRules.STALE_MS)

    /** 이 종류로 이미 우체통에 든 targetKey 들(pending·dead). 사진 feeder 의 중복·되살아남 방지. */
    suspend fun targetsForKind(wire: String): List<String> = dao.targetsForKind(wire)

    /** 이 (종류,대상)의 아직 안 보낸 행들. 댓글 「보내는 중」 합치기에 쓴다. */
    suspend fun pendingFor(kind: OutboxKind, targetKey: String): List<OutboxEntity> =
        dao.pendingForKindTarget(kind.wire, targetKey)

    /** 사장님 「다시 보내기」 — 그 행을 지금 바로 다시 시도하게 하고 깨운다. */
    suspend fun retry(id: Long) {
        dao.resetForRetry(id)
        kick()
    }

    /** 사장님 「그만 보내기」 — 그 행을 우체통에서 뺀다(사진이면 다음 스캔에 또 뜨지 않게 도장도 찍는다는 건 호출부가). */
    suspend fun dismiss(id: Long) = dao.delete(id)

    private fun OutboxEntity.toRow() =
        OutboxRow(id, kind, targetKey, ownerPhone, attempts, createdAtMs)
}
