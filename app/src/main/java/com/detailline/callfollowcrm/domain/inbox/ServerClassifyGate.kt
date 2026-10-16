package com.detailline.callfollowcrm.domain.inbox

import com.detailline.callfollowcrm.data.local.entity.ThreadBucketEntity

/**
 * 「서버(Haiku)에 물어볼까」 판단 — 순수 함수. (2026-10-15 Phase 2 앱 연결)
 *
 *   돈이 드는 호출이라 **문을 좁게** 연다. 통화요약 신규설치 burst(설치 직후 수백 콜) 교훈:
 *   - 로컬 1차가 **애매(UNSURE)** 할 때만. CONSULT/GENERAL 로 확정이면 0원.
 *   - **수신 그 1건**에만. 스캔/백필(CallFollowCrmApplication.classifyCachedContacts)은 이 문을 아예 안 지난다.
 *   - 같은 번호에 이미 바구니(행)가 있으면 안 묻는다 — 서버가 이미 답했거나 사장님이 고정한 것.
 *   - 저장 고객 · 사장님이 답장한 적 있는 번호 · 사장님이 「고객 아님」 찍은 번호는 안 묻는다(답이 정해져 있다).
 *   - 같은 번호 연타는 쿨다운([COOLDOWN_MS]) — 한 사람이 열 통 보내도 열 번 안 묻는다.
 *
 *   실패·거절은 전부 **상담함 유지** — 이 판단이 틀려도 고객을 숨기진 않는다.
 */
object ServerClassifyGate {

    /** 같은 번호를 다시 묻기까지 기다리는 시간. 서버도 내용 해시로 캐시하지만, 내용이 조금씩 다른 연타를 막는다. */
    const val COOLDOWN_MS: Long = 10 * 60 * 1000L

    /** 서버에 보내는 최근 메시지 수(시간순 마지막 N). 서버 `_classify_one` 도 뒤에서 5개만 본다. */
    const val MAX_MESSAGES = 5

    /** 서버로 보내는 한 줄 — 누가 보냈나(사장님/상대) · 본문 · 시각(ms). */
    data class Msg(val fromOwner: Boolean, val body: String, val ts: Long)

    enum class Decision {
        /** 물어본다. */
        ASK,
        /** 사장님이 「AI 자동 거르기」 를 껐다(AppPreferences.aiSpamFilterEnabled) — 0원. (2026-10-16) */
        SKIP_FILTER_OFF,
        /** 로컬이 이미 확정(CONSULT/GENERAL) — 0원. */
        SKIP_LOCAL_DECIDED,
        /** 이미 바구니 행이 있다(서버가 답했거나 사장님 고정) — 다시 안 묻는다. */
        SKIP_ALREADY_BUCKETED,
        /** 저장 고객 · 답장 이력 · 사장님이 「고객 아님」 찍음 — 답이 정해져 있다. */
        SKIP_KNOWN_ANSWER,
        /** 보낼 본문이 없다 — 서버도 consult 로 돌려보낸다. */
        SKIP_NO_BODY,
        /** 같은 번호를 방금 물었다. */
        SKIP_COOLDOWN
    }

    /**
     * @param local            로컬 1차 판정(InboxClassifier)
     * @param existing         이 번호의 바구니 행(없으면 null = 상담함 기본)
     * @param isSavedCustomer  저장 고객인가
     * @param hasOwnerReply    호출부가 아는 답장 이력(수신 경로는 false 로 온다 — 메시지에서 다시 센다)
     * @param ownerMarkedNonCustomer 사장님이 「고객 아님」 으로 찍은 번호인가(AppPreferences.isNonCustomer)
     * @param messages         서버에 보낼 메시지(시간순). 사장님이 보낸 줄이 있으면 = 답장 이력.
     * @param lastAskedAt      이 번호를 마지막으로 물어본 시각(ms, 없으면 null)
     * @param filterEnabled    「AI 자동 거르기」 스위치(AppPreferences.aiSpamFilterEnabled). 꺼져 있으면 무조건 안 묻는다.
     */
    fun decide(
        local: InboxClassifier.Verdict,
        existing: ThreadBucketEntity?,
        isSavedCustomer: Boolean,
        hasOwnerReply: Boolean,
        ownerMarkedNonCustomer: Boolean,
        messages: List<Msg>,
        lastAskedAt: Long?,
        now: Long,
        filterEnabled: Boolean = true
    ): Decision {
        if (!filterEnabled) return Decision.SKIP_FILTER_OFF
        if (local != InboxClassifier.Verdict.UNSURE) return Decision.SKIP_LOCAL_DECIDED
        if (existing != null) return Decision.SKIP_ALREADY_BUCKETED
        if (isSavedCustomer || hasOwnerReply || ownerMarkedNonCustomer) return Decision.SKIP_KNOWN_ANSWER
        if (messages.any { it.fromOwner }) return Decision.SKIP_KNOWN_ANSWER
        if (messages.none { it.body.isNotBlank() }) return Decision.SKIP_NO_BODY
        if (lastAskedAt != null && now - lastAskedAt < COOLDOWN_MS) return Decision.SKIP_COOLDOWN
        return Decision.ASK
    }

    /**
     * 서버에 보낼 메시지 묶음 — 폰에서 읽은 최근 대화(순서 무관)에 방금 받은 그 문자를 **반드시 포함**시키고,
     * 시간순으로 정렬해 마지막 [MAX_MESSAGES] 개만.
     *   (SMS_RECEIVED 경로는 provider 에 아직 안 들어갔을 수 있어 current 를 따로 받는다.
     *    이미 들어가 있으면(본문 같음) 두 번 넣지 않는다.)
     */
    fun payloadMessages(recent: List<Msg>, current: Msg): List<Msg> {
        val merged = if (recent.any { !it.fromOwner && it.body == current.body }) recent else recent + current
        return merged
            .filter { it.body.isNotBlank() }
            .sortedBy { it.ts }
            .takeLast(MAX_MESSAGES)
    }
}
