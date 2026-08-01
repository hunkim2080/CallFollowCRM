package com.detailline.callfollowcrm.presentation.screen.collab

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.detailline.callfollowcrm.ai.SharedSiteRepository
import com.detailline.callfollowcrm.ai.SharedSiteRepository.DirectionAgg
import com.detailline.callfollowcrm.ai.SharedSiteRepository.MonthlyRecord
import com.detailline.callfollowcrm.ai.SharedSiteRepository.PartnerMonth
import com.detailline.callfollowcrm.ai.SharedSiteRepository.SharedSite
import com.detailline.callfollowcrm.ai.SharedSiteRepository.SiteRow
import com.detailline.callfollowcrm.ai.siteDisplayName
import com.detailline.callfollowcrm.data.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 협업 기록 — 협업 사장별·월별 집계(받은/준). 기록·세금용. (2026-08-01 사장님, 승인 목업 design-preview/collab_record_mockup.html)
 *
 * 데이터: 서버 `GET /api/shared/monthly`(docs/SERVER_HANDOFF_collab_monthly.md) 우선.
 *   서버 미구현/실패 시 → `withMe`(받은)+`byMe`(준) 를 앱에서 월·파트너로 그룹핑하는 **로컬 폴백**(단 입금여부는 서버만 알아 "—").
 *   서버 나오면 자동으로 정확본(paid 포함)으로 승격.
 */
class CollabRecordViewModel(private val container: AppContainer) : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val ym: String = "",
        val monthLabel: String = "",
        val availableMonths: List<String> = emptyList(),
        val canPrev: Boolean = false,   // 더 예전 달 있음
        val canNext: Boolean = false,   // 더 최근 달 있음
        val received: DirectionAgg = EMPTY_AGG,
        val given: DirectionAgg = EMPTY_AGG,
        val paidUnknown: Boolean = false, // 로컬 폴백 = 입금여부 미표시
        val hasAny: Boolean = false
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    // 로컬 폴백용 원본 캐시(한 번 받아두고 월 바꿀 때 재계산).
    private var cachedWithMe: List<SharedSite>? = null
    private var cachedByMe: List<SharedSite>? = null

    init { load(null) }

    fun prevMonth() = shiftMonth(+1)   // 예전 = availableMonths(desc)에서 index+1
    fun nextMonth() = shiftMonth(-1)

    private fun shiftMonth(delta: Int) {
        val s = _state.value
        val idx = s.availableMonths.indexOf(s.ym)
        if (idx < 0) return
        val target = s.availableMonths.getOrNull(idx + delta) ?: return
        load(target)
    }

    /** ym=null → 가장 최근(데이터 있는) 달. */
    fun load(ym: String?) {
        val phone = container.preferences.bizPhone.filter { it.isDigit() }
        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            val server = container.sharedSiteRepository.monthly(phone, ym).getOrNull()
            if (server != null) {
                applyRecord(server, paidUnknown = false)
                return@launch
            }
            // 로컬 폴백 — withMe/byMe 한 번만 받아 캐시.
            if (cachedWithMe == null || cachedByMe == null) {
                cachedWithMe = container.sharedSiteRepository.withMe(phone, 0L, 200).getOrDefault(emptyList())
                cachedByMe = container.sharedSiteRepository.byMe(phone, 200).getOrDefault(emptyList())
            }
            val local = buildLocalMonthly(cachedWithMe.orEmpty(), cachedByMe.orEmpty(), ym)
            applyRecord(local, paidUnknown = true)
        }
    }

    private fun applyRecord(r: MonthlyRecord, paidUnknown: Boolean) {
        val months = r.availableMonths
        val ym = r.ym.ifBlank { months.firstOrNull().orEmpty() }
        val idx = months.indexOf(ym)
        _state.update {
            it.copy(
                loading = false,
                ym = ym,
                monthLabel = monthLabel(ym),
                availableMonths = months,
                canPrev = idx in 0 until (months.size - 1),   // 더 예전 있음
                canNext = idx > 0,                             // 더 최근 있음
                received = r.received,
                given = r.given,
                paidUnknown = paidUnknown,
                hasAny = r.received.count > 0 || r.given.count > 0 || months.isNotEmpty()
            )
        }
    }

    companion object {
        val EMPTY_AGG = DirectionAgg(0, 0, 0, emptyList())
        private val KST = java.time.ZoneId.of("Asia/Seoul")

        /** epoch ms → "yyyy-MM" (KST 기준). */
        fun ymOf(ms: Long): String {
            val d = java.time.Instant.ofEpochMilli(ms).atZone(KST).toLocalDate()
            return "%04d-%02d".format(d.year, d.monthValue)
        }

        /** epoch ms → "7/3(목)" (KST). */
        fun dayLabel(ms: Long): String {
            val d = java.time.Instant.ofEpochMilli(ms).atZone(KST).toLocalDate()
            val dow = arrayOf("월", "화", "수", "목", "금", "토", "일")[d.dayOfWeek.value - 1]
            return "${d.monthValue}/${d.dayOfMonth}($dow)"
        }

        /** "2026-07" → "2026년 7월". 빈값이면 "". */
        fun monthLabel(ym: String): String {
            val parts = ym.split("-")
            if (parts.size != 2) return ""
            val y = parts[0]; val m = parts[1].toIntOrNull() ?: return ""
            return "${y}년 ${m}월"
        }

        /**
         * 로컬 폴백 집계(순수함수 — 단위테스트 대상). withMe=받은(수입), byMe=준(지출).
         *   거절(declined)은 제외. wage=dailyWage(만원), paid=null(로컬은 입금여부 모름).
         *   ym=null → 데이터 있는 가장 최근 달.
         */
        fun buildLocalMonthly(withMe: List<SharedSite>, byMe: List<SharedSite>, ym: String?): MonthlyRecord {
            // 기록엔 '실제로 한(수락된)' 협업만. 취소(ended)·거절(declined)·아직 수락 전(pending)은 제외.
            //   (CollabSites 화면의 gone=setOf("declined","ended") 와 같은 정의 + pending 도 미확정이라 뺌.) (2026-08-01 사장님: 취소한 게 그대로 뜸)
            val rcvSites = withMe.filter { it.status == "accepted" }
            val givSites = byMe.filter { it.status == "accepted" }

            // 데이터 있는 달(양방향 합쳐) — 최신순.
            val months = (rcvSites + givSites)
                .map { ymOf(it.scheduledAtMs) }
                .distinct()
                .sortedDescending()
            val sel = ym?.takeIf { it.isNotBlank() && months.contains(it) } ?: months.firstOrNull().orEmpty()

            val received = aggregate(rcvSites.filter { ymOf(it.scheduledAtMs) == sel }) { s ->
                PartnerKey(s.ownerPhone.filter { c -> c.isDigit() }, s.ownerName.ifBlank { "사장님" })
            }
            val given = aggregate(givSites.filter { ymOf(it.scheduledAtMs) == sel }) { s ->
                // by-me 는 상대 번호가 SharedSite 에 없어 이름으로 그룹.
                PartnerKey("", s.partnerName?.takeIf { n -> n.isNotBlank() } ?: "협업 사장님")
            }
            return MonthlyRecord(ym = sel, availableMonths = months, received = received, given = given)
        }

        private data class PartnerKey(val phone: String, val name: String)

        private fun aggregate(sites: List<SharedSite>, keyOf: (SharedSite) -> PartnerKey): DirectionAgg {
            val groups = sites.groupBy(keyOf)
            val partners = groups.map { (key, list) ->
                val sorted = list.sortedBy { it.scheduledAtMs }
                PartnerMonth(
                    partnerPhone = key.phone,
                    partnerName = key.name,
                    count = list.size,
                    totalWage = list.sumOf { it.dailyWage ?: 0 },
                    paidTotal = 0,
                    lastAtMs = list.maxOf { it.scheduledAtMs },
                    sites = sorted.map { s ->
                        SiteRow(
                            shareId = s.shareId,
                            atMs = s.scheduledAtMs,
                            title = siteDisplayName(s),
                            wage = s.dailyWage ?: 0,
                            paid = null
                        )
                    }
                )
            }.sortedByDescending { it.lastAtMs }
            return DirectionAgg(
                count = sites.size,
                totalWage = sites.sumOf { it.dailyWage ?: 0 },
                paidTotal = 0,
                partners = partners
            )
        }
    }
}
