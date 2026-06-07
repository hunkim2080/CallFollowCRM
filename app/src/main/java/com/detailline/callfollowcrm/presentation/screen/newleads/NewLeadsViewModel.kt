package com.detailline.callfollowcrm.presentation.screen.newleads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.detailline.callfollowcrm.data.AppContainer
import com.detailline.callfollowcrm.util.DateTimeUtils
import com.detailline.callfollowcrm.util.PhoneNumberFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * 신규 고객 · 날짜별 (프로토 `s-newleads` / renderNewLeads).
 *
 * 2026-06-06 재설계 (사장님 "실제 문의 전부"): 고객 카드 생성된 것만 보던 것 → **실제 들어온 문의 전부**.
 *   - 소스 = SMS/MMS 캐시(sms_contacts_cache, MMS 포함) ∪ 받은 통화(inbound). phone suffix 로 합침.
 *   - 신규 = 아직 시공일 안 잡힌 번호(고객 카드 있으면 scheduledWorkDate==null, 없으면 그냥 포함).
 *   - 날짜 그룹 = **마지막 연락 시각**(통화/문자/MMS 중 최신) 기준 오늘/어제/N일 전. (고객 생성시각 아님)
 *   - 답장함/미답장 = 고객 messageHistory 응대 OR 그 번호로 사장님이 답장한 SMS(hasOwnerReply) 있는지.
 *   - 이름/메모 = 고객 카드 있으면 거기서, 없으면 번호 + "신규 문의".
 * 허위 숫자 없음 — 전부 로컬 집계. (재연락 발송은 화면에서 채팅으로, 자동발송 X)
 */
class NewLeadsViewModel(container: AppContainer) : ViewModel() {

    private val todayStart = DateTimeUtils.startOfDay(System.currentTimeMillis())
    private val sinceMs = todayStart - 180L * DateTimeUtils.DAY_MS
    private val spamPrefixes = container.preferences.spamPrefixes

    private val smsContacts = container.smsContactCacheRepository.observeAll(limit = 500)
    private val inbound = container.callRecordRepository.observeInboundSince(sinceMs)
    private val customers = container.customerRepository.observeAll()
    private val categories = container.categoryRepository.observeAll()
    private val repliedIds = container.messageHistoryRepository.observeRepliedCustomerIds()

    private val unreadOnly = MutableStateFlow(false)
    fun setUnreadOnly(v: Boolean) { unreadOnly.value = v }

    // 고객/카테고리/응대기록 3개를 미리 묶어 combine 5개 한도 회피.
    private val custCtx = combine(customers, categories, repliedIds) { c, cat, rep -> Triple(c, cat, rep) }

    private class Acc(var phone: String, var contactMs: Long, var hasOwnerReply: Boolean)

    val uiState: StateFlow<NewLeadsUiState> = combine(
        smsContacts, inbound, custCtx, unreadOnly
    ) { sms, calls, ctx, onlyUnread ->
        val (custs, cats, replied) = ctx
        val catName = cats.associate { it.id to it.name }
        val repliedSet = replied.toHashSet()
        val custBySuffix = custs.associateBy { phoneSuffix(it.phoneNumber) }

        // suffix 별로 문자/MMS/통화 합쳐 마지막 연락 시각 집계.
        val bySuffix = LinkedHashMap<String, Acc>()
        for (s in sms) {
            val suf = s.normalizedSuffix.ifBlank { phoneSuffix(s.address) }
            if (suf.isBlank()) continue
            val a = bySuffix.getOrPut(suf) { Acc(s.address, 0L, false) }
            if (s.lastDateMs > a.contactMs) { a.contactMs = s.lastDateMs; if (s.address.isNotBlank()) a.phone = s.address }
            a.hasOwnerReply = a.hasOwnerReply || s.hasOwnerReply
        }
        for (c in calls) {
            val suf = phoneSuffix(c.phoneNumber)
            if (suf.isBlank()) continue
            val a = bySuffix.getOrPut(suf) { Acc(c.phoneNumber, 0L, false) }
            if (c.endedAt > a.contactMs) { a.contactMs = c.endedAt; if (a.phone.isBlank()) a.phone = c.phoneNumber }
        }

        val leads = bySuffix.entries.mapNotNull { (suf, a) ->
            if (a.contactMs <= 0L) return@mapNotNull null
            // 스팸 앞자리(070 등) 제외. (2026-06-07)
            if (com.detailline.callfollowcrm.util.SpamPrefix.isSpam(a.phone.ifBlank { suf }, spamPrefixes)) return@mapNotNull null
            val cust = custBySuffix[suf]
            // 2026-06-07 사장님 B안: 계약(시공일 등록)된 고객도 목록에 남기되 "계약완료" 배지로 표시.
            //   (이전엔 시공일 잡히면 목록에서 제외했음 — 사장님이 한눈에 계약 여부 보고 싶다고 변경)
            val contracted = (cust?.scheduledWorkDate ?: 0L) > 0L
            val phone = a.phone.ifBlank { suf }
            val replyDone = contracted || (cust?.id?.let { it in repliedSet } == true) || a.hasOwnerReply
            NewLeadUi(
                customerId = cust?.id ?: 0L,
                phone = phone,
                displayName = cust?.name?.takeIf { it.isNotBlank() } ?: PhoneNumberFormatter.format(phone),
                timeLabel = relativeTime(a.contactMs),
                memo = cust?.memo?.takeIf { it.isNotBlank() }
                    ?: cust?.categoryId?.let { catName[it] }?.takeIf { it.isNotBlank() }
                    ?: "신규 문의",
                replied = replyDone,
                contracted = contracted,
                dayStart = DateTimeUtils.startOfDay(a.contactMs)
            ) to a.contactMs
        }.sortedByDescending { it.second }.map { it.first }

        val total = leads.size
        val unread = leads.count { !it.replied }
        val shown = if (onlyUnread) leads.filter { !it.replied } else leads

        val groups = shown
            .groupBy { it.dayStart }
            .toSortedMap(compareByDescending { it })
            .map { (dayStart, list) ->
                NewLeadGroup(dateLabel = dateLabel(dayStart), count = list.size, leads = list)
            }

        NewLeadsUiState(
            groups = groups,
            totalCount = total,
            unreadCount = unread,
            unreadOnly = onlyUnread
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NewLeadsUiState())

    private fun phoneSuffix(phone: String): String {
        val d = phone.filter { it.isDigit() }
        return if (d.length >= 8) d.takeLast(8) else d
    }

    /** createdAt → 프로토 nl-t 시각 라벨 ("방금"/"N분 전"/"N시간 전"/날짜+시각). */
    private fun relativeTime(ms: Long): String {
        val diff = System.currentTimeMillis() - ms
        val min = diff / 60_000
        return when {
            min < 1 -> "방금"
            min < 60 -> "${min}분 전"
            DateTimeUtils.startOfDay(ms) == todayStart -> "${min / 60}시간 전"
            else -> DateTimeUtils.formatShort(ms)
        }
    }

    /** dayStart → 프로토 nl-date 라벨 (오늘/어제/N일 전/M월 D일). */
    private fun dateLabel(dayStart: Long): String {
        val days = ((todayStart - dayStart) / DateTimeUtils.DAY_MS).toInt()
        return when {
            days <= 0 -> "오늘"
            days == 1 -> "어제"
            days in 2..6 -> "${days}일 전"
            else -> DateTimeUtils.formatDateOnly(dayStart)
        }
    }
}

/** 신규 재연락 한 명. */
data class NewLeadUi(
    val customerId: Long,
    val phone: String,
    val displayName: String,
    val timeLabel: String,
    val memo: String,
    val replied: Boolean,
    /** 계약(시공일 등록) 완료 — 목록에 "계약완료" 배지로 남김. */
    val contracted: Boolean = false,
    val dayStart: Long
)

/** 날짜 그룹 (오늘 · N통). */
data class NewLeadGroup(
    val dateLabel: String,
    val count: Int,
    val leads: List<NewLeadUi>
)

data class NewLeadsUiState(
    val groups: List<NewLeadGroup> = emptyList(),
    val totalCount: Int = 0,
    val unreadCount: Int = 0,
    val unreadOnly: Boolean = false
)
