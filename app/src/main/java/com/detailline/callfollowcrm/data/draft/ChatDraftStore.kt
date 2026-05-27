package com.detailline.callfollowcrm.data.draft

import java.util.concurrent.ConcurrentHashMap

/**
 * ChatScreen composer 의 phone 별 임시저장.
 *
 * 사장님 통점 (2026-05-27): 메시지 치다가 [뒤로] 갔다가 다시 들어가면 입력이 다 지워져 있음.
 * → ChatViewModel 의 local state 가 destination pop 시 dispose 되기 때문.
 *
 * 해결: AppContainer 가 보유하는 in-memory Map. ChatViewModel 이 init 시 read, 입력 변경 시 write,
 *   전송 성공 시 clear. 앱 살아있는 동안 모든 navigation 살아남음.
 *
 * 영속 (앱 재시작 후에도 살아남기) 까지는 일단 미구현. 사장님 통점이 "재진입 시" 만 명시했고,
 * 임시 입력을 영구 저장하면 오히려 의도와 어긋날 수 있음 (잘못 친 글이 다음날도 남음).
 */
class ChatDraftStore {
    private val drafts = ConcurrentHashMap<String, String>()

    fun get(phone: String): String = drafts[phone].orEmpty()

    fun set(phone: String, text: String) {
        if (text.isEmpty()) {
            drafts.remove(phone)
        } else {
            drafts[phone] = text
        }
    }

    fun clear(phone: String) {
        drafts.remove(phone)
    }
}
