package com.detailline.callfollowcrm.recording

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withTimeoutOrNull

/**
 * "이미 처리된 통화내용입니다. 다시 요약해드릴까요?" 프롬프트 조정기.
 *
 * 녹음 요약([CallAudioSummarizer])은 백그라운드라 UI 다이얼로그를 직접 못 띄운다.
 * → 백그라운드가 [ask] 로 프롬프트를 켜고 사장님 답(예/아니오)을 suspend 로 기다린다.
 *   ChatScreen 이 [pending] 를 구독해 다이얼로그를 띄우고 [answer] 로 응답한다.
 *   사장님이 그 채팅에 없거나 무응답이면 60초 후 false(=아니오, 기존 유지)로 자동 종료.
 *
 * 한 번에 하나만 처리(통화 녹음 공유는 순차) — 단일 deferred 로 충분.
 */
object CallSummaryReprompt {

    data class Pending(val phone: String, val recordedAt: Long)

    private val _pending = MutableStateFlow<Pending?>(null)
    val pending: StateFlow<Pending?> = _pending

    @Volatile
    private var deferred: CompletableDeferred<Boolean>? = null

    /** 백그라운드에서 호출 — 프롬프트를 띄우고 사장님 답을 기다린다. 무응답 60초 → false. */
    suspend fun ask(phone: String, recordedAt: Long): Boolean {
        val d = CompletableDeferred<Boolean>()
        deferred = d
        _pending.value = Pending(phone, recordedAt)
        return try {
            withTimeoutOrNull(60_000) { d.await() } ?: false
        } finally {
            _pending.value = null
            deferred = null
        }
    }

    /** ChatScreen 다이얼로그에서 호출 — 예(true)/아니오(false). */
    fun answer(redo: Boolean) {
        deferred?.complete(redo)
        _pending.value = null
    }
}
