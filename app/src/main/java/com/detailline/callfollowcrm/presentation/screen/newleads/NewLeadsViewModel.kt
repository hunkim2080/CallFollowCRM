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
 * 신규 고객 · 날짜별 (프로토 `s-newleads` / renderNewLeads) 1:1.
 *
 * 프로토는 demo 배열(newLeads)이지만, 여기선 **실데이터**로 매핑:
 *   - 신규 = 아직 시공일이 안 잡힌 고객(= 상담/재연락 단계). scheduledWorkDate == null. createdAt 최신순.
 *   - 답장함/미답장 = messageHistory 에 사장님 응대(보냄/오픈) 기록이 있는 고객 id 인지.
 *   - 메모 = 고객 메모 → 없으면 카테고리명 → 없으면 "신규 문의".
 *   - 날짜 그룹 = createdAt 기준 오늘/어제/N일 전/M월 D일.
 * 허위 숫자 없음 — 전부 로컬 집계. (재연락 발송은 화면에서 채팅으로, 자동발송 X = 정책 유지)
 */
class NewLeadsViewModel(container: AppContainer) : ViewModel() {

    private val todayStart = DateTimeUtils.startOfDay(System.currentTimeMillis())

    private val customers = container.customerRepository.observeAll()
    private val categories = container.categoryRepository.observeAll()
    private val repliedIds = container.messageHistoryRepository.observeRepliedCustomerIds()

    private val unreadOnly = MutableStateFlow(false)
    fun setUnreadOnly(v: Boolean) { unreadOnly.value = v }

    val uiState: StateFlow<NewLeadsUiState> = combine(
        customers, categories, repliedIds, unreadOnly
    ) { custs, cats, replied, onlyUnread ->
        val catName = cats.associate { it.id to it.name }
        val repliedSet = replied.toHashSet()

        // 신규 = 시공일 미등록 고객. createdAt 최신순.
        val leads = custs
            .filter { it.scheduledWorkDate == null }
            .sortedByDescending { it.createdAt }
            .map { c ->
                val replyDone = c.id in repliedSet
                NewLeadUi(
                    customerId = c.id,
                    phone = c.phoneNumber,
                    displayName = c.name?.takeIf { it.isNotBlank() } ?: PhoneNumberFormatter.format(c.phoneNumber),
                    timeLabel = relativeTime(c.createdAt),
                    memo = c.memo.takeIf { it.isNotBlank() }
                        ?: c.categoryId?.let { catName[it] }?.takeIf { it.isNotBlank() }
                        ?: "신규 문의",
                    replied = replyDone,
                    dayStart = DateTimeUtils.startOfDay(c.createdAt)
                )
            }

        val total = leads.size
        val unread = leads.count { !it.replied }
        val shown = if (onlyUnread) leads.filter { !it.replied } else leads

        // 날짜별 그룹 (입력 순서 = createdAt desc 유지).
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
