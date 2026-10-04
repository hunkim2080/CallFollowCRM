package com.detailline.callfollowcrm.domain.outbox

/**
 * 우체통(Outbox)의 **순수 규칙** — 네트워크·Room 없이 셈만 한다. 단위 테스트 대상.
 * 설계: docs/DESIGN_offline_outbox.md §3·§4·§6.
 *
 * ⚠️ 여기 있는 건 전부 **한 곳**이다. 같은 셈(백오프·판정·순서)을 화면이나 Outbox 본체에
 *    다시 적지 않는다(§12 두 벌 금지). Outbox.drain 도 simulateRound 를 그대로 쓴다 —
 *    테스트가 보는 로직과 실제로 도는 로직이 **같다.**
 */

/** 한 행을 서버에 보낸 결과 판정. */
enum class Verdict { DONE, RETRY, DEAD }

/** 같은 (kind,targetKey) 가 이미 pending 일 때 어떻게 합치나. */
enum class Coalesce {
    LATEST_WINS,     // payload 갈아끼움(알림 한 번) — 일정·주소
    APPEND,          // 늘 새 행 — 댓글
    TERMINAL,        // 같은 target 앞선 pending 전부 지움 — 해제
    KEEP_EXISTING    // 이미 있으면 **아무것도 안 함**(백오프 유지) — 사진(payload 가 안 바뀜)
}

/**
 * 우체통에 들어가는 종류.
 * `wire` = DB 에 저장되는 문자열(바꾸면 옛 행을 못 읽는다 — 절대 바꾸지 말 것).
 */
enum class OutboxKind(val wire: String, val coalesce: Coalesce) {
    COLLAB_RESCHEDULE("collab_reschedule", Coalesce.LATEST_WINS),
    COLLAB_ADDRESS("collab_address", Coalesce.LATEST_WINS),
    COLLAB_END("collab_end", Coalesce.TERMINAL),
    SITE_PHOTO("site_photo", Coalesce.KEEP_EXISTING);  // payload({photoId})가 안 바뀜 → 이미 있으면 그대로 둔다

    companion object {
        fun fromWire(w: String): OutboxKind? = entries.firstOrNull { it.wire == w }
    }
}

/** drain 한 라운드를 셈하기 위한 **최소** 투영(id·종류·대상·주인·시도횟수·처음넣은시각). */
data class OutboxRow(
    val id: Long,
    val kindWire: String,
    val targetKey: String,
    val ownerPhone: String,
    val attempts: Int,
    val createdAtMs: Long
)

/** enqueue 때 합치기 계획. Outbox.enqueue 가 이대로 DAO 를 고친다. */
data class CoalescePlan(
    val reuseId: Long?,               // 이 pending 행의 payload 만 갈아끼운다(LATEST_WINS). null=새 행
    val deleteIds: List<Long>,        // 지울 앞선 pending 행들(TERMINAL)
    val skipInsert: Boolean = false   // true=아무것도 안 함(KEEP_EXISTING, 이미 있을 때)
)

/** 라운드 한 행의 결정. Outbox 가 이걸 보고 DAO 를 고친다. */
sealed class RoundAction {
    abstract val id: Long
    data class Done(override val id: Long) : RoundAction()
    data class Retry(override val id: Long, val attempts: Int, val nextAttemptAtMs: Long, val error: String) : RoundAction()
    data class Dead(override val id: Long, val reason: String) : RoundAction()
    data class Skipped(override val id: Long) : RoundAction()    // 같은 현장 앞엣것이 실패해 이번 라운드는 건너뜀
}

object OutboxRules {

    // 백오프 계단: 0 → 30초 → 2분 → 10분 → 30분 → 1시간 → 3시간(상한).
    //   attempts = **이미 실패한 횟수**. 첫 시도(0)는 즉시.
    private val STEPS_MS = longArrayOf(
        0L, 30_000L, 120_000L, 600_000L, 1_800_000L, 3_600_000L, 10_800_000L
    )

    const val STALE_MS = 24L * 3600_000           // 하루 넘게 못 보내면 사장님께 보인다
    const val MAX_AGE_MS = 7L * 24 * 3600_000     // 이레 넘으면 dead(「일주일째 못 보냈어요」)
    const val MAX_ROWS_PER_ROUND = 50             // 한 번에 보낼 상한(배터리·타임아웃) §11
    const val DEAD_KEEP_MS = 30L * 24 * 3600_000  // dead 는 30일 뒤 자동 삭제

    /**
     * 새로 넣을 때 **같은 targetKey 의 pending 행들**을 보고 어떻게 합칠지. §3 합치기 표.
     *   - LATEST_WINS: 같은 (kind,target) pending 이 있으면 **그 행의 payload 만** 갈아끼운다(알림 한 번).
     *   - APPEND: 늘 새 행(댓글처럼 쌓인다).
     *   - TERMINAL: 같은 target 의 **앞선 pending 을 전부 지우고** 새 행(끝낸 현장에 옛 명령 안 보냄).
     */
    fun coalescePlan(newKind: OutboxKind, existingPendingSameTarget: List<OutboxRow>): CoalescePlan =
        when (newKind.coalesce) {
            Coalesce.APPEND -> CoalescePlan(null, emptyList())
            Coalesce.LATEST_WINS -> {
                val same = existingPendingSameTarget.filter { it.kindWire == newKind.wire }.minByOrNull { it.id }
                CoalescePlan(same?.id, emptyList())
            }
            Coalesce.TERMINAL -> CoalescePlan(null, existingPendingSameTarget.map { it.id })
            Coalesce.KEEP_EXISTING -> {
                val exists = existingPendingSameTarget.any { it.kindWire == newKind.wire }
                CoalescePlan(null, emptyList(), skipInsert = exists)   // 이미 있으면 그대로 둔다(백오프 안 깨짐)
            }
        }

    /** 이미 실패한 횟수 → 다음까지 기다릴 기본 ms(지터 전). */
    fun backoffMs(attempts: Int): Long {
        if (attempts <= 0) return STEPS_MS[0]
        return STEPS_MS[attempts.coerceAtMost(STEPS_MS.size - 1)]
    }

    /** ±20% 지터. rnd ∈ [0,1). 0 은 0 그대로(즉시). */
    fun jitter(baseMs: Long, rnd: Double): Long {
        if (baseMs <= 0L) return 0L
        val f = 0.8 + 0.4 * rnd.coerceIn(0.0, 0.999999)   // 0.8 ~ 1.1999…
        return (baseMs * f).toLong().coerceAtLeast(0L)
    }

    /** 다음 시도 시각(ms). now + 지터된 백오프. */
    fun nextAttemptAt(now: Long, attempts: Int, rnd: Double): Long =
        now + jitter(backoffMs(attempts), rnd)

    /**
     * HTTP 코드(네트워크 실패면 null)를 판정으로. §4-D.
     *   글자(detail)가 아니라 **코드로만** 판단한다 — 서버가 말을 바꿔도 안 흔들린다.
     */
    fun verdict(kind: OutboxKind, httpCode: Int?): Verdict {
        if (httpCode == null) return Verdict.RETRY           // 끊김·타임아웃·오프라인
        return when {
            httpCode in 200..299 -> Verdict.DONE
            httpCode == 409 && kind == OutboxKind.COLLAB_END -> Verdict.DONE   // 이미 종료 = 끝난 것
            httpCode == 403 && kind == OutboxKind.SITE_PHOTO -> Verdict.RETRY  // 티어 게이트(기존 동작)
            httpCode == 401 -> Verdict.RETRY                 // 세션 갱신은 인터셉터가. 재로그인 안내는 기존 길
            httpCode == 408 || httpCode == 429 -> Verdict.RETRY
            httpCode in 500..599 -> Verdict.RETRY
            httpCode in 400..499 -> Verdict.DEAD             // 403·404·409·400·413·422 … = 서버가 안 된다고 함
            else -> Verdict.RETRY
        }
    }

    /** dead 사유를 사장님 말로. */
    fun deadReason(httpCode: Int?): String = when (httpCode) {
        403 -> "본인 현장이 아니라고 했어요"
        404 -> "서버가 이 현장을 모른다고 했어요"
        409 -> "이미 끝난 현장이라고 했어요"
        400, 413, 422 -> "내용이 잘못됐다고 했어요"
        null -> "너무 오래 못 보냈어요"
        else -> "서버가 거절했어요(코드 $httpCode)"
    }

    /** 처음 넣은 뒤 며칠째인가(0 = 오늘). */
    fun daysStuck(createdAtMs: Long, now: Long): Int {
        val d = now - createdAtMs
        if (d <= 0) return 0
        return (d / (24L * 3600_000)).toInt()
    }

    /** 하루 넘게 못 보냈나(사장님께 띠로 보여야 하나). */
    fun isStale(createdAtMs: Long, now: Long): Boolean = now - createdAtMs >= STALE_MS

    /** 이레 넘었나(dead 로 넘길 때). */
    fun isExpired(createdAtMs: Long, now: Long): Boolean = now - createdAtMs >= MAX_AGE_MS

    /**
     * drain 한 라운드를 셈한다. **이 함수가 유일한 순서·블록 규칙이다.**
     *   - id(넣은 순서)대로 본다.
     *   - `activeOwner` 와 번호가 다른 행은 건너뛴다(다른 사업자번호로 로그인). §10
     *   - 같은 targetKey 에서 앞이 RETRY/네트워크실패면, 그 뒤 같은 target 은 이번 라운드 **건너뜀**(순서 보존).
     *   - DEAD 는 그 현장을 막지 않는다(끝난 명령이라 뒤엣것은 가도 된다).
     *   - 7일 넘은 pending 은 보내보기 전에 DEAD.
     *   - `send` 는 **주입**된다 — 테스트는 순수 람다, 실제는 네트워크. 로직은 하나.
     */
    suspend fun simulateRound(
        rows: List<OutboxRow>,
        activeOwner: String,
        now: Long,
        rnd: () -> Double,
        send: suspend (OutboxRow) -> Pair<Verdict, Int?>   // 판정 + (dead 사유용) HTTP 코드
    ): List<RoundAction> {
        val blocked = HashSet<String>()    // 이번 라운드에 막힌 targetKey
        val out = ArrayList<RoundAction>(rows.size)
        var sent = 0
        for (r in rows.sortedBy { it.id }) {
            // 번호가 박힌 행(협업)은 지금 로그인과 같아야 보낸다. 빈 번호(사진)는 「아무 주인이나」 = 지금 주인 것.
            if (r.ownerPhone.isNotBlank() && r.ownerPhone != activeOwner) { out += RoundAction.Skipped(r.id); continue }
            if (r.targetKey in blocked) { out += RoundAction.Skipped(r.id); continue }
            if (isExpired(r.createdAtMs, now)) {
                out += RoundAction.Dead(r.id, deadReason(null)); continue
            }
            if (sent >= MAX_ROWS_PER_ROUND) { out += RoundAction.Skipped(r.id); continue }
            sent++
            val (verdict, code) = send(r)
            when (verdict) {
                Verdict.DONE -> out += RoundAction.Done(r.id)
                Verdict.DEAD -> out += RoundAction.Dead(r.id, deadReason(code))   // DEAD 는 target 안 막음
                Verdict.RETRY -> {
                    blocked += r.targetKey
                    out += RoundAction.Retry(
                        r.id, r.attempts + 1,
                        nextAttemptAt(now, r.attempts + 1, rnd()),
                        code?.let { "HTTP $it" } ?: "offline"
                    )
                }
            }
        }
        return out
    }
}
