package com.detailline.callfollowcrm.recording

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * 통화 녹음 → 서버 요약이 "진행 중"인 통화 키 집합.
 *
 * 녹음 공유 처리([RecordingShareHandler] → [CallAudioSummarizer])는 앱 전역 백그라운드 스코프에서
 * 돌기 때문에 채팅 화면(ChatViewModel)과 분리돼 있다. 진행 상태를 공유할 통로가 없으면 ChatScreen 이
 * "요약 중…"을 보여줄 수 없다. → 전역 StateFlow 로 begin/end 를 노출해 채팅 통화카드가 구독한다.
 *
 * key = phoneSuffix(끝 8자리) + "@" + recordedAtMs.
 *   통화카드 매칭은 (전화 일치 + recordedAt 이 통화 [시작-10분 ~ 종료+10분] 안) — CallSummary 매칭과 동일.
 */
object CallSummaryProgress {

    private val _inProgress = MutableStateFlow<Set<String>>(emptySet())
    val inProgress: StateFlow<Set<String>> = _inProgress

    private fun key(phone: String, recordedAtMs: Long): String {
        val suffix = phone.filter { it.isDigit() }.takeLast(8)
        return "$suffix@$recordedAtMs"
    }

    fun begin(phone: String, recordedAtMs: Long) {
        _inProgress.update { it + key(phone, recordedAtMs) }
    }

    fun end(phone: String, recordedAtMs: Long) {
        _inProgress.update { it - key(phone, recordedAtMs) }
    }
}
