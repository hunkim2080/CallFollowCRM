package com.detailline.callfollowcrm.data.draft

import com.detailline.callfollowcrm.ai.ReplySuggestions
import java.util.concurrent.ConcurrentHashMap

/**
 * ChatScreen 의 AI 추천 답변 chips 의 phone 별 임시 캐시.
 *
 * 사장님 통점 (2026-05-28): 메시지 치다가 [뒤로] → 재진입 시 chips 가 빈 상태로 잠시 보였다가
 * 서버 재호출 후 다시 채워짐. 사장님은 그 짧은 끊김을 "사라짐" 으로 인식.
 *
 * 해결: AppContainer 가 보유하는 in-memory Map. ChatViewModel.init 에서 read 로 _suggestions 즉시
 *   hydrate (재진입 시 0ms 복원), 이후 _suggestions 변경마다 자동 write.
 *
 * 낡은 chips 위험: 그 사이 새 메시지가 들어왔다면 ChatViewModel.effectiveSuggestions 의 stale 차단
 *   로직 (`sug.basedOnReceivedAtMs < latest.dateMs`) 이 자동으로 null 반환. 백그라운드 loadSuggestions
 *   가 새 chips 받아오면 _suggestions 갱신 + 캐시 put → UI 자동 교체.
 *
 * 영속 (앱 재시작 후) 까지는 미구현. 사장님 통점이 "재진입 시" 만이라 in-memory 로 충분.
 */
class SuggestionsCacheStore {
    private val cache = ConcurrentHashMap<String, ReplySuggestions>()

    fun get(phone: String): ReplySuggestions? = cache[phone]

    fun put(phone: String, value: ReplySuggestions?) {
        if (value == null) cache.remove(phone) else cache[phone] = value
    }

    fun clear(phone: String) {
        cache.remove(phone)
    }
}
