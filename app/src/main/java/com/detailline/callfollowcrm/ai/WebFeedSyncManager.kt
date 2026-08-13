package com.detailline.callfollowcrm.ai

import com.detailline.callfollowcrm.data.preferences.AppPreferences
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
    private val categoryRepository: CategoryRepository
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
                    completed = c.workCompletedAt != null,
                    shareIds = shareIdsByCustomer[c.id]?.distinct() ?: emptyList()
                )
            }

        val hash = items.joinToString("|") {
            "${it.customerDigits},${it.workDate},${it.completed},${it.category},${it.apartment},${it.name},${it.shareIds.sorted().joinToString(":")}"
        }.hashCode().toString()
        if (!force && hash == prefs.webFeedLastHash) return false

        val res = repo.pushFeed(ownerPhone, items)
        return if (res.isSuccess) {
            prefs.webFeedLastHash = hash
            true
        } else {
            false
        }
    }
}
