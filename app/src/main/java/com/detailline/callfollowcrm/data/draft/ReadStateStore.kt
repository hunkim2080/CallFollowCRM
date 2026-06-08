package com.detailline.callfollowcrm.data.draft

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * "최근 대화" 안 읽음(파란 점) 읽음 추적 — 카톡식.
 *
 * 사장님 통점 (2026-06-08): 채팅을 한 번 열어 읽으면 답장 안 해도 파란 점이 사라져야 카톡스럽다.
 *   → 채팅 열 때(ChatViewModel.init) [markRead] 기록. 홈은 [readStates] 를 구독해
 *     "고객 마지막 메시지 시각 > 마지막으로 읽은 시각" 일 때만 안 읽음(점)으로 표시한다.
 *     (답장하면 lastSent=true 라 어차피 안 읽음 아님 — 읽음 추적은 "열기만 해도 해제" 를 담당.)
 *
 * 영속: SharedPreferences (앱 재시작 후에도 유지). key = 번호 끝 8자리, 값 = 마지막으로 연 시각(ms).
 *   대화 수는 수백 규모라 prefs 로 충분. 새 고객 메시지가 그 시각 이후면 다시 안 읽음이 된다.
 */
class ReadStateStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("conversation_read_state", Context.MODE_PRIVATE)

    private val _readStates = MutableStateFlow(loadAll())
    /** suffix(끝 8자리) → 마지막으로 그 대화를 연 시각(ms). 홈이 combine/collect 로 구독. */
    val readStates: StateFlow<Map<String, Long>> = _readStates.asStateFlow()

    private fun loadAll(): Map<String, Long> =
        prefs.all.entries.mapNotNull { (k, v) ->
            val ms = v as? Long ?: return@mapNotNull null
            k to ms
        }.toMap()

    private fun suffixOf(phone: String): String {
        val digits = phone.filter { it.isDigit() }
        return if (digits.length >= 8) digits.takeLast(8) else digits
    }

    /** 이 번호 대화를 지금(또는 atMs)에 읽음으로 표시. 이후 그 시각보다 새 고객 메시지가 없으면 점이 사라진다. */
    fun markRead(phone: String, atMs: Long = System.currentTimeMillis()) {
        val suffix = suffixOf(phone)
        if (suffix.isBlank()) return
        val prev = _readStates.value[suffix] ?: 0L
        if (atMs <= prev) return
        prefs.edit().putLong(suffix, atMs).apply()
        _readStates.value = _readStates.value + (suffix to atMs)
    }
}
