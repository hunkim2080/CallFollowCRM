package com.detailline.callfollowcrm.presentation.screen.newleads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.detailline.callfollowcrm.data.AppContainer
import com.detailline.callfollowcrm.util.DateTimeUtils
import com.detailline.callfollowcrm.util.PhoneNumberFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
    /** ✨AI 한줄요약(cardSummary) — 있으면 "어떻게 끝났는지" 줄에 우선 표시. */
    private val aiSummaries = container.conversationAiRepository.observeAll()

    private val unreadOnly = MutableStateFlow(false)
    fun setUnreadOnly(v: Boolean) { unreadOnly.value = v }

    /** 신규 목록에서 밀어서 정리 = '광고/스팸' 마킹 → 목록·집계에서 제외(상담함 카운트와 일치). (2026-06-08 #3) */
    fun dismissAsSpam(phone: String) = viewModelScope.launch {
        runCatching { spamRepo.mark(phoneSuffix(phone)) }
    }
    /** 되돌리기 — 스팸 마킹 해제. */
    fun undoDismiss(phone: String) = viewModelScope.launch {
        runCatching { spamRepo.unmark(phoneSuffix(phone)) }
    }

    /** 사장님이 직접 '광고/스팸'으로 표시한 번호(suffix). 상담함 카운트와 동일하게 신규목록에서도 제외. 2026-06-07 */
    private val markedSpam = container.spamPhoneRepository.suffixes
    private val spamRepo = container.spamPhoneRepository   // 밀어서 정리(스팸 마킹/해제). (2026-06-08 #3)

    // ── 꾹 눌러 대화 미리보기(peek) — 줄 길게 누르면 들어가지 않고 그 자리에서 문자 기록 모달. ──
    private val cachedMessageRepository = container.cachedMessageRepository
    private val smsRepository = container.smsRepository

    private val _peek = MutableStateFlow<PeekState?>(null)
    val peek: StateFlow<PeekState?> = _peek.asStateFlow()

    /** 줄을 꾹 눌렀을 때 — 그 번호의 최근 문자(읽기 전용)를 모달로. 통화만 있으면 빈 상태 + 결말 라벨. */
    fun openPeek(lead: NewLeadUi) {
        val digits = lead.phone.filter { it.isDigit() }
        val suffix = if (digits.length >= 8) digits.takeLast(8) else digits
        _peek.value = PeekState(
            phone = lead.phone, customerId = lead.customerId, displayName = lead.displayName,
            loading = true, messages = emptyList(), fallbackLine = lead.summaryLine
        )
        viewModelScope.launch(Dispatchers.IO) {
            val cached = runCatching { cachedMessageRepository.load(suffix, 60) }.getOrDefault(emptyList())
            val fresh = if (smsRepository.hasReadPermission())
                runCatching { smsRepository.querySmsOnly(lead.phone) }.getOrDefault(emptyList())
            else emptyList()
            val localSent = runCatching { cachedMessageRepository.loadLocalSent(suffix) }.getOrDefault(emptyList())
            val base = if (fresh.isNotEmpty()) fresh + localSent else cached + localSent
            val merged = base.distinctBy { it.id to it.sent }
                .sortedByDescending { it.dateMs }   // 최신(=어떻게 끝났는지)이 위로
                .take(40)
            _peek.update { cur -> if (cur?.phone == lead.phone) cur.copy(loading = false, messages = merged) else cur }
        }
    }

    fun closePeek() { _peek.value = null }

    // 고객/카테고리/응대기록/마킹스팸 4개를 미리 묶어 combine 5개 한도 회피.
    private val custCtx = combine(customers, categories, repliedIds, markedSpam) { c, cat, rep, spam ->
        CustCtx(c, cat, rep, spam)
    }

    // contactMs = 마지막 연락(시각 라벨·정렬용), firstMs = 처음 연락(날짜 그룹용 — "신규=처음 들어온 날").
    //   lastBody/lastSent = 가장 최근 문자 내용·방향("어떻게 끝났는지" fallback), lastWasCall = 마지막 연락이 통화였나.
    private class Acc(
        var phone: String, var contactMs: Long, var firstMs: Long, var hasOwnerReply: Boolean,
        var lastBody: String = "", var lastSent: Boolean = false, var lastWasCall: Boolean = false,
        var lastCallType: String = "", var lastCallDurationSec: Long = 0L
    )

    private class CustCtx(
        val custs: List<com.detailline.callfollowcrm.data.local.entity.CustomerEntity>,
        val cats: List<com.detailline.callfollowcrm.data.local.entity.CategoryEntity>,
        val replied: List<Long>,
        val spam: Set<String>
    )

    val uiState: StateFlow<NewLeadsUiState> = combine(
        smsContacts, inbound, custCtx, unreadOnly, aiSummaries
    ) { sms, calls, ctx, onlyUnread, summaries ->
        val custs = ctx.custs; val cats = ctx.cats; val replied = ctx.replied
        val spamSet = ctx.spam
        val catName = cats.associate { it.id to it.name }
        val repliedSet = replied.toHashSet()
        val custBySuffix = custs.associateBy { phoneSuffix(it.phoneNumber) }
        // suffix → ✨AI 한줄요약 (빈/null 제외).
        val summaryBySuffix = summaries.mapNotNull { s ->
            s.cardSummary?.trim()?.takeIf { it.isNotEmpty() }?.let { s.phoneSuffix to it }
        }.toMap()

        // suffix 별로 문자/MMS/통화 합쳐 마지막 연락 시각 집계.
        val bySuffix = LinkedHashMap<String, Acc>()
        for (s in sms) {
            val suf = s.normalizedSuffix.ifBlank { phoneSuffix(s.address) }
            if (suf.isBlank()) continue
            val a = bySuffix.getOrPut(suf) { Acc(s.address, 0L, Long.MAX_VALUE, false) }
            if (s.lastDateMs > a.contactMs) {
                a.contactMs = s.lastDateMs
                if (s.address.isNotBlank()) a.phone = s.address
                a.lastBody = s.lastBody; a.lastSent = s.lastSent; a.lastWasCall = false
            }
            if (s.firstDateMsInScan in 1 until a.firstMs) a.firstMs = s.firstDateMsInScan
            a.hasOwnerReply = a.hasOwnerReply || s.hasOwnerReply
        }
        for (c in calls) {
            val suf = phoneSuffix(c.phoneNumber)
            if (suf.isBlank()) continue
            val a = bySuffix.getOrPut(suf) { Acc(c.phoneNumber, 0L, Long.MAX_VALUE, false) }
            if (c.endedAt > a.contactMs) {
                a.contactMs = c.endedAt
                if (a.phone.isBlank()) a.phone = c.phoneNumber
                a.lastWasCall = true; a.lastCallType = c.callType; a.lastCallDurationSec = c.duration
            }
            if (c.endedAt in 1 until a.firstMs) a.firstMs = c.endedAt
        }

        val leads = bySuffix.entries.mapNotNull { (suf, a) ->
            if (a.contactMs <= 0L) return@mapNotNull null
            // 스팸 앞자리(070 등) 제외. (2026-06-07)
            if (com.detailline.callfollowcrm.util.SpamPrefix.isSpam(a.phone.ifBlank { suf }, spamPrefixes)) return@mapNotNull null
            // 사장님이 '광고/스팸'으로 직접 표시한 번호 제외 — 상담함 '오늘 신규' 카운트와 일치. (2026-06-07)
            if (suf in spamSet) return@mapNotNull null
            val cust = custBySuffix[suf]
            // 2026-06-07 사장님 B안: 계약(시공일 등록)된 고객도 목록에 남기되 "계약완료" 배지로 표시.
            //   (이전엔 시공일 잡히면 목록에서 제외했음 — 사장님이 한눈에 계약 여부 보고 싶다고 변경)
            val contracted = (cust?.scheduledWorkDate ?: 0L) > 0L
            val phone = a.phone.ifBlank { suf }
            val replyDone = contracted || (cust?.id?.let { it in repliedSet } == true) || a.hasOwnerReply
            // "어떻게 끝났는지" 한 줄 — ✨AI 요약 우선, 없으면 마지막 문자(방향 표시), 그것도 없으면 통화.
            val aiSum = summaryBySuffix[suf]
            val lastMsg = a.lastBody.replace("\n", " ").trim().takeIf { it.isNotEmpty() }
            val summaryIsAi = aiSum != null
            val summaryLine = aiSum ?: when {
                lastMsg != null -> (if (a.lastSent) "나: " else "고객: ") + lastMsg
                a.lastWasCall -> callEndingLabel(a.lastCallType, a.lastCallDurationSec)
                else -> null
            }
            NewLeadUi(
                customerId = cust?.id ?: 0L,
                phone = phone,
                displayName = cust?.name?.takeIf { it.isNotBlank() } ?: PhoneNumberFormatter.format(phone),
                timeLabel = relativeTime(a.contactMs),
                memo = cust?.memo?.takeIf { it.isNotBlank() }
                    ?: cust?.categoryId?.let { catName[it] }?.takeIf { it.isNotBlank() }
                    ?: "신규 문의",
                summaryLine = summaryLine,
                summaryIsAi = summaryIsAi,
                lastWasCall = a.lastWasCall && lastMsg == null,
                replied = replyDone,
                contracted = contracted,
                // 날짜 그룹 = "처음 들어온 날"(firstMs). 상담함 '오늘 신규(새 번호 기준)'와 같은 정의로 맞춤.
                //   (전엔 마지막 연락일 기준이라, 옛 고객이 오늘 답장만 해도 '오늘'에 잡혀 카운트가 달랐음. 2026-06-07)
                dayStart = DateTimeUtils.startOfDay(if (a.firstMs == Long.MAX_VALUE) a.contactMs else a.firstMs)
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

    /** 통화만 있는 신규의 "어떻게 끝났는지" — 부재중(안 받음)/수신 통화+시간/거절. */
    private fun callEndingLabel(callType: String, durationSec: Long): String = when (callType) {
        "MISSED" -> "부재중 (안 받음)"
        "REJECTED" -> "거절한 통화"
        "INCOMING" -> if (durationSec > 0) "수신 통화 · ${durationLabel(durationSec)}" else "수신 통화"
        else -> "통화"
    }

    /** 초 → "N분 M초" / "M초". */
    private fun durationLabel(sec: Long): String {
        val m = sec / 60; val s = sec % 60
        return if (m > 0) (if (s > 0) "${m}분 ${s}초" else "${m}분") else "${s}초"
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
    /** "어떻게 끝났는지" 한 줄 — ✨AI 요약 또는 마지막 문자. null 이면 memo 로 fallback. */
    val summaryLine: String? = null,
    /** summaryLine 이 ✨AI 요약이면 true(파란 ✨), 마지막 문자면 false(💬). */
    val summaryIsAi: Boolean = false,
    /** 마지막 연락이 통화뿐(문자 본문 없음) — UI 가 📞 아이콘. */
    val lastWasCall: Boolean = false,
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

/** 꾹 눌러 보는 대화 미리보기 상태. messages = 최신순(위가 최근). */
data class PeekState(
    val phone: String,
    val customerId: Long,
    val displayName: String,
    val loading: Boolean,
    val messages: List<com.detailline.callfollowcrm.data.repository.SmsRepository.SmsMessage>,
    /** 문자가 없을 때(통화만) 보여줄 한 줄 — 예 "부재중 (안 받음)". */
    val fallbackLine: String? = null
)
