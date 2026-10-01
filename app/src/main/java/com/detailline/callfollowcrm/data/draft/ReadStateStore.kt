package com.detailline.callfollowcrm.data.draft

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.detailline.callfollowcrm.util.PhoneKey

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
        return PhoneKey.of(phone)
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

    /**
     * 📬 **전부 읽음으로** — 여러 대화를 한 번에. (2026-10-01 사장님)
     *
     *   광고 문자 하나 때문에 빨간 숫자가 안 없어지는데, **답장할 수도 없어서**
     *   없앨 방법이 아예 없었다. 하나씩 열어 지우게 하지 않는다.
     *
     *   ⚠️ 지우는 건 **빨간 숫자뿐**이다 — 대화도 고객도 그대로 남는다.
     *     새 문자가 오면 그 시각이 더 최신이라 **다시 안 읽음**이 된다.
     *   화면 한 번 그리는 값을 한 번에 쓴다(번호마다 쓰면 목록이 수십 번 다시 그려진다).
     */
    fun markAllRead(phones: Collection<String>, atMs: Long = System.currentTimeMillis()): Map<String, Long> {
        if (phones.isEmpty()) return emptyMap()
        val next = HashMap(_readStates.value)
        val before = HashMap<String, Long>()
        val edit = prefs.edit()
        for (phone in phones) {
            val suffix = suffixOf(phone)
            if (suffix.isBlank()) continue
            val prev = next[suffix] ?: 0L
            if (atMs <= prev) continue
            before[suffix] = prev          // 0 = 원래 한 번도 안 읽은 것
            edit.putLong(suffix, atMs)
            next[suffix] = atMs
        }
        if (before.isEmpty()) return emptyMap()
        edit.apply()
        _readStates.value = next
        return before
    }

    /**
     * ↩️ [markAllRead] 되돌리기 — 바꾸기 전 값으로 되돌린다. (2026-10-01)
     *   한 번에 백 개를 비우는 일이라 **무를 수 있어야** 한다.
     *   값이 0 이던 것(한 번도 안 읽은 것)은 **칸 자체를 지운다** — 0 을 적어두면
     *   "0 에 읽었다"는 기록이 남아 나중에 헷갈린다.
     */
    fun restoreReadStates(before: Map<String, Long>) {
        if (before.isEmpty()) return
        val next = HashMap(_readStates.value)
        val edit = prefs.edit()
        for ((suffix, prev) in before) {
            if (prev > 0L) { edit.putLong(suffix, prev); next[suffix] = prev }
            else { edit.remove(suffix); next.remove(suffix) }
        }
        edit.apply()
        _readStates.value = next
    }
}
