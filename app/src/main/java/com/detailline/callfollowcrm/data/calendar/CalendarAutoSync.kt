package com.detailline.callfollowcrm.data.calendar

import com.detailline.callfollowcrm.data.local.entity.CustomerEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import com.detailline.callfollowcrm.data.local.entity.SimpleEventEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * 구글 캘린더 **자동** 동기화. (2026-09-16 사장님)
 *
 *   "새 일정이 등록되거나 하면 알아서 동기화되어야하는거 아닌가 싶네. 내가 눌러주는게 맞나…
 *    고객메모가 바뀌던지, 캘린더에 들어갈 내용에 수정이 있으면 캘린더도 자동으로 동기화되어야할듯"
 *
 * 맞는 말이다. 그동안은 **앱을 켤 때 1번** + **사장님이 [지금 동기화]를 누를 때**만 올라갔다.
 *   쓰는 도중 일정을 고치면 그대로 멈춰 있었고, 사장님이 그걸 기억해서 눌러야 했다.
 *
 * 🔑 왜 이제서야 되나 — 예전엔 동기화 한 번이 **46초**였다(일정 있는 고객 전원에게 매번 재전송).
 *   그걸 자동으로 돌리면 앱이 계속 버벅였을 것이다. 오늘 "안 바뀐 건 건너뛰기"를 넣어
 *   **바뀐 것만** 올라가게 되면서 자동 동기화가 감당 가능해졌다.
 *
 * 설계:
 *   · 고객 표에서 **캘린더에 실제로 들어가는 값**만 지문으로 뽑아 지켜본다.
 *     (문자 수신·읽음 표시처럼 캘린더와 무관한 변화로는 안 돈다 — 그런 변화가 훨씬 잦다)
 *   · 값이 바뀌면 [QUIET_MS] 만큼 조용해지길 기다렸다 한 번만 올린다.
 *     (금액을 타이핑하는 동안 글자마다 올리지 않게)
 *   · 연결 안 돼 있으면 아무것도 안 한다.
 */
class CalendarAutoSync(
    private val connected: () -> Boolean,
    private val customers: Flow<List<CustomerEntity>>,
    private val syncAll: suspend () -> Unit,
    /** 간단 일정도 캘린더에 올라간다 → 여기도 지켜봐야 한다. (2026-09-16) */
    private val simpleEvents: Flow<List<SimpleEventEntity>>,
) {

    @OptIn(FlowPreview::class)
    fun start(scope: CoroutineScope) {
        scope.launch {
            combine(customers, simpleEvents) { cs, es -> calendarFingerprint(cs) + simpleFingerprint(es) }
                .distinctUntilChanged()
                // 첫 방출은 '지금 상태'일 뿐 변화가 아니다. 앱 시작 동기화가 이미 담당한다.
                .drop(1)
                .debounce(QUIET_MS)
                .collect {
                    if (!connected()) return@collect
                    runCatching { syncAll() }
                }
        }
    }

    companion object {
        /** 이만큼 조용해지면 올린다. 타이핑·연속 수정 중엔 안 돈다. */
        const val QUIET_MS = 4_000L

        /**
         * 캘린더에 **실제로 들어가는 값**만 모은 지문. 이게 그대로면 캘린더도 그대로다.
         *
         * ⚠️ 여기 빠진 값을 캘린더 본문([CalendarSyncManager.buildEvent])에 새로 넣으면,
         *    그 값을 고쳐도 자동 동기화가 안 돈다. **둘을 같이 고칠 것.**
         *    (제목=금액·지역 / 본문=연락처·시공내용·메모·금액 / 시간=날짜·시각·일수)
         */
        /** 간단 일정의 지문 — 제목·날짜·시각·메모가 그대로면 안 올린다. */
        fun simpleFingerprint(list: List<SimpleEventEntity>): String {
            val sb = StringBuilder(list.size * 16)
            sb.append('|')
            for (e in list) {
                sb.append(e.id).append(':')
                    .append(e.dayStartMs).append(',')
                    .append(e.minutes ?: -1).append(',')
                    .append(e.title).append(',')
                    .append(e.memo).append(';')
            }
            return sb.toString()
        }

        fun calendarFingerprint(list: List<CustomerEntity>): String {
            val sb = StringBuilder(list.size * 24)
            for (c in list) {
                // 일정이 없고 올려둔 이벤트도 없는 고객은 캘린더와 무관 — 지문에서 제외(잡음 감소).
                if (c.scheduledWorkDate == null && c.asScheduledDate == null &&
                    c.workCalendarEventId == null && c.asCalendarEventId == null
                ) continue
                sb.append(c.id).append(':')
                    .append(c.scheduledWorkDate ?: 0).append(',')
                    .append(c.scheduledWorkMinutes ?: -1).append(',')
                    .append(c.scheduledWorkDays).append(',')
                    .append(c.asScheduledDate ?: 0).append(',')
                    .append(c.asScheduledDays).append(',')
                    .append(c.address.orEmpty()).append(',')
                    .append(c.name.orEmpty()).append(',')
                    .append(c.memo.orEmpty()).append(',')
                    .append(c.totalAmount ?: 0).append(',')
                    .append(c.depositAmount ?: 0).append(',')
                    .append(c.balanceAmount ?: 0).append(',')
                    .append(c.balancePaidAt ?: 0).append(',')
                    .append(c.workCompletedAt ?: 0).append(';')
            }
            return sb.toString()
        }
    }
}
