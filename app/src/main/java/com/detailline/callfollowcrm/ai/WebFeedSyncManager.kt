package com.detailline.callfollowcrm.ai

import com.detailline.callfollowcrm.data.local.entity.CallSummaryEntity
import com.detailline.callfollowcrm.data.local.entity.CustomerEntity
import com.detailline.callfollowcrm.data.preferences.AppPreferences
import com.detailline.callfollowcrm.data.repository.CachedMessageRepository
import com.detailline.callfollowcrm.data.repository.CallSummaryRepository
import com.detailline.callfollowcrm.data.repository.CategoryRepository
import com.detailline.callfollowcrm.data.repository.CustomerRepository
import com.detailline.callfollowcrm.util.DateTimeUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 시공막내 웹 사진 캘린더 — 뷰어용 스케줄 피드 자동 전송. [WebFeedRepository]
 *   미러와 독립: 웹 로그인하면 켜짐(webViewerActive). 미러 안 켜도 웹 뷰어는 됨.
 *   일정(고객) 변경 → 30초 디바운스 → 해시 비교 시에만 push (미러와 같은 절약 패턴).
 *   서버 web_schedule_feed 를 '덮어쓰기' → 서버가 이걸로 웹 달력/현장목록을 그린다.
 */
class WebFeedSyncManager(
    private val repo: WebFeedRepository,
    private val prefs: AppPreferences,
    private val customerRepository: CustomerRepository,
    private val categoryRepository: CategoryRepository,
    private val cachedMessageRepository: CachedMessageRepository,
    private val callSummaryRepository: CallSummaryRepository
) {
    private val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.KOREA)

    /** 라이브 전송 — 고객이 바뀌면 30초 잠잠해진 뒤 1회 push(웹 로그인 상태일 때만). */
    fun start(scope: CoroutineScope) {
        scope.launch {
            customerRepository.observeAll().collectLatest {
                if (!prefs.webViewerActive) return@collectLatest
                delay(30_000)               // 디바운스: 다음 변경이 오면 이 대기가 취소됨
                runCatching { pushNow(force = false) }
            }
        }
    }

    /**
     * 피드 1회 전송.
     *   force=false → webViewerActive 꺼져있거나 직전과 같은 내용이면 skip(해시).
     *   force=true(웹 로그인 직후) → 게이트·해시 무시하고 즉시 최신 push.
     *   @return 실제로 보냈으면 true.
     */
    suspend fun pushNow(force: Boolean): Boolean {
        if (!force && !prefs.webViewerActive) return false
        val ownerPhone = prefs.bizPhone.trim()
        if (ownerPhone.filter { it.isDigit() }.length < 9) return false

        // 고객 읽기가 예외로 실패하면(빈 결과 ≠ 진짜 0건) 빈 피드를 밀지 않고 skip — 웹 달력이 순간 비는 착시 방지.
        val customers = runCatching { customerRepository.allOnce() }.getOrNull() ?: return false
        val catNames = runCatching {
            categoryRepository.observeAll().first().associate { it.id to it.name }
        }.getOrDefault(emptyMap())

        // 협업번호 지도 — collabAssignments("customerId|파트너폰|파트너이름|shareId") → 고객별 share_id 목록.
        //   직원(협업 사장)이 올린 사진(customer_phone=NULL·share_id=X)을 그 고객 현장에 잇도록 서버에 알려줌.
        val shareIdsByCustomer: Map<Long, List<String>> = runCatching {
            prefs.collabAssignments.mapNotNull { e ->
                val parts = e.split("|")
                val cid = parts.getOrNull(0)?.toLongOrNull()
                val sid = parts.getOrNull(3)?.trim()?.takeIf { it.isNotBlank() }
                if (cid != null && sid != null) cid to sid else null
            }.groupBy({ it.first }, { it.second })
        }.getOrDefault(emptyMap())

        val items = customers
            .filter { (it.scheduledWorkDate ?: 0L) > 0L }
            .mapNotNull { c ->
                val digits = c.phoneNumber.filter { ch -> ch.isDigit() }
                if (digits.length < 9) return@mapNotNull null   // 조인 키(전화) 없으면 제외(사진과 못 이음)
                val day = DateTimeUtils.startOfDay(c.scheduledWorkDate!!)
                WebFeedRepository.FeedItem(
                    customerDigits = digits,
                    name = c.name?.takeIf { it.isNotBlank() } ?: "현장",
                    apartment = c.address?.takeIf { it.isNotBlank() } ?: "",
                    dongHo = "",   // 앱엔 동/호 분리 필드 없음 — 주소에 포함(§0: 지어내지 않음)
                    workDate = dateFmt.format(Date(day)),
                    category = c.categoryId?.let { catNames[it] } ?: "",
                    completed = c.isWorkDone,   // 잔금 받으면=완료 (사장님 통일 2026-08-18) — 웹 '진행중' 오표기 + 블로그 재료 미전송 해결
                    shareIds = shareIdsByCustomer[c.id]?.distinct() ?: emptyList(),
                    memo = c.memo   // 웹 '글 만들기' 재료 — 메모 저장 시 observeAll→자동 push (2026-08-15)
                )
            }

        val hash = items.joinToString("|") {
            "${it.customerDigits},${it.workDate},${it.completed},${it.category},${it.apartment},${it.name},${it.shareIds.sorted().joinToString(":")},${it.memo}"
        }.hashCode().toString()
        // 달력(feed)은 변경 시에만 push. 하지만 문자대화·통화요약 재료는 feed 와 무관하게 바뀔 수 있음
        //   (시공 후 '감사 문자' 등 = 최고의 블로그 재료) → feed 해시 그대로여도 항상 시도한다(F-4 유실 방지).
        val feedChanged = force || hash != prefs.webFeedLastHash
        if (feedChanged) {
            if (!repo.pushFeed(ownerPhone, items).isSuccess) return false
            prefs.webFeedLastHash = hash
        }
        // 자체 webContentHashes dedup 으로 안 바뀐 고객은 skip → 네트워크 낭비 없음.
        runCatching { pushConversationsForCompleted(customers, ownerPhone) }
        return true
    }

    /**
     * 완료 고객의 문자 대화(원문)를 웹 '글 만들기' 재료로 전송. (완료게이트 별도 엔드포인트 — 코워크 동의)
     *   변경분만: 고객별 대화 해시가 바뀐 것만 push([AppPreferences.webContentHashes]). 최초엔 백필.
     *   최근 완료 60명 상한 — 메시지 로딩 비용 제한(나머지는 이미 전송됨). conversation_text = "손님: …\n나: …" 시간순.
     */
    private suspend fun pushConversationsForCompleted(customers: List<CustomerEntity>, ownerPhone: String) {
        val completed = customers
            .filter { it.isWorkDone && it.phoneNumber.filter { ch -> ch.isDigit() }.length >= 9 }
            .sortedByDescending { it.doneAtMs ?: 0L }
            .take(60)
        if (completed.isEmpty()) return

        val stored = prefs.webContentHashes.split(";").mapNotNull { e ->
            val kv = e.split("=", limit = 2)
            if (kv.size == 2 && kv[0].isNotBlank()) kv[0] to kv[1] else null
        }.toMap().toMutableMap()

        for (c in completed) {
            val digits = c.phoneNumber.filter { it.isDigit() }
            val suffix = digits.takeLast(8)
            val msgs = runCatching { cachedMessageRepository.load(suffix, 500) }.getOrNull().orEmpty()
            val text = msgs
                .sortedBy { it.dateMs }
                // 발신이 로컬보존(systemId<0)+provider 로 이중 캐시돼 재료가 2배 되던 것 제거(2026-08-17 사장님 지적).
                //   두 복사본이 개행/공백만 달라도(near-dup) 잡도록 공백 전부 정규화한 키로 비교(표시는 원문 유지).
                .distinctBy { it.sent to it.body.replace(Regex("\\s+"), " ").trim() }
                .mapNotNull { m -> m.body.trim().takeIf { it.isNotBlank() }?.let { (if (m.sent) "나: " else "손님: ") + it } }
                .joinToString("\n")
            // 통화요약도 글 재료로 — 통화 많은 고객은 문자가 아예 없을 수 있어(문자 유무로 skip 안 함). owner-scoped push.
            val callSummary = runCatching { buildCallSummaryText(callSummaryRepository.listByCustomer(c.id)) }.getOrDefault("")
            if (text.isBlank() && callSummary.isBlank()) continue   // 문자·통화 둘 다 없으면 skip
            val h = "${text.length}:${text.hashCode()}:${callSummary.length}:${callSummary.hashCode()}"
            if (stored[digits] == h) continue        // 문자·통화 둘 다 안 바뀌면 재전송 X
            val ok = repo.pushCustomerContent(ownerPhone, digits, text, callSummary.takeIf { it.isNotBlank() }).isSuccess
            if (ok) stored[digits] = h
        }
        prefs.webContentHashes = stored.entries.joinToString(";") { "${it.key}=${it.value}" }
    }

    /** 통화요약들을 글 재료용 한 덩어리 텍스트로. 최근 5건 · title+요약본문 · 총 3000자 상한. */
    private fun buildCallSummaryText(summaries: List<CallSummaryEntity>): String {
        if (summaries.isEmpty()) return ""
        return summaries.take(5).mapNotNull { s ->
            val bodyText = s.summaryText?.takeIf { it.isNotBlank() }
                ?: s.customerNeed?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            val date = s.recordedAt?.let { dateFmt.format(Date(it)) }
            val head = listOfNotNull(date?.let { "[통화 $it]" }, s.title?.takeIf { it.isNotBlank() }).joinToString(" ")
            (if (head.isNotBlank()) "$head\n" else "") + bodyText.trim()
        }.joinToString("\n\n").take(3000)
    }
}
