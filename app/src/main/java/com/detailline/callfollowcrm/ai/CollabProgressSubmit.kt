package com.detailline.callfollowcrm.ai

import com.detailline.callfollowcrm.data.AppContainer
import com.detailline.callfollowcrm.domain.outbox.OutboxKind
import org.json.JSONObject

/**
 * 🤝 협업 진행(출발·도착·완료) **서버 전송 한 경로** — 화면(SharedSiteViewModel)과 알림(완료 버튼)이 **같이** 쓴다(§12).
 *
 *   하는 일: ① 낙관적 로컬 반영(updateLocalProgress) → ② Outbox(COLLAB_PROGRESS) 보관·전송 → ③ 완료/되돌리기면 미러 push.
 *   payload(step·내 번호·완료 시 계좌·상호)는 **여기 한 곳**에서 만든다 — 화면과 알림이 다른 payload 를 보내면 두 벌.
 *
 *   ⚠️ 토스트·소리·화면 state(_sites)는 각 호출부가(화면은 UI, 알림은 소리 없음) — 여긴 **전송 셈만**.
 */
object CollabProgressSubmit {

    /**
     * @param auto §E 지오펜스 **자동** 도착(3km)이면 true → 서버가 'A 거의 도착'으로 구분. 사람이 누른 건 false.
     * @param localProgress 화면(홈 띠·협업)에 낙관 반영할지 — 사람이 누른 건 true, 자동 이벤트는 false(원래대로).
     * @param mirror 미러(본폰 뷰어)에 즉시 밀지 — 완료/되돌리기는 true. 출발·도착은 false.
     * @return Outbox 넣기 성공 여부. false 면 호출부가 사장님께 알린다(조용히 넘기면 전달이 영영 안 됨 §13①).
     */
    suspend fun submit(
        container: AppContainer,
        shareId: String,
        step: SharedSiteRepository.Progress,
        auto: Boolean = false,
        localProgress: Boolean = true,
        mirror: Boolean = step == SharedSiteRepository.Progress.COMPLETED
    ): Boolean {
        if (localProgress) container.collabEventCenter.updateLocalProgress(shareId, step)
        val p = container.preferences
        val myPhone = p.bizPhone.filter { it.isDigit() }
        val bizName = p.bizName.takeIf { it.isNotBlank() } ?: p.bizOwner
        val payload = JSONObject().apply {
            put("step", step.name.lowercase())
            put("partnerPhone", myPhone)
            if (auto) put("auto", true)   // §E 3km geofence 자동 도착
            if (step == SharedSiteRepository.Progress.COMPLETED) {
                // 완료 시 입금 계좌를 실어 보냄 → 주인 사장(A)에게 계좌 전달.
                p.bizBank.takeIf { it.isNotBlank() }?.let { put("bank", it) }
                p.bizAccountNo.takeIf { it.isNotBlank() }?.let { put("accountNo", it) }
                (p.bizAccountHolder.takeIf { it.isNotBlank() } ?: p.bizOwner.takeIf { it.isNotBlank() })
                    ?.let { put("holder", it) }
            }
            bizName.takeIf { it.isNotBlank() }?.let { put("partnerName", it) }
        }
        val queued = runCatching {
            container.outbox.enqueue(OutboxKind.COLLAB_PROGRESS, shareId, payload.toString())
        }.isSuccess
        if (!queued) return false
        container.outbox.tryNow()
        if (mirror) runCatching { container.mirrorSyncManager.pushNow(force = true) }
        return true
    }
}
