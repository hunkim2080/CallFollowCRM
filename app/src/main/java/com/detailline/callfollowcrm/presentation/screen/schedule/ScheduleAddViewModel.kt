package com.detailline.callfollowcrm.presentation.screen.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.detailline.callfollowcrm.data.AppContainer
import com.detailline.callfollowcrm.data.local.entity.CustomerEntity
import com.detailline.callfollowcrm.data.local.entity.NotebookContactEntity
import com.detailline.callfollowcrm.util.DateTimeUtils
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 셀프 일정 등록 (2026-06-01) — 전화·문자 없이 사장님이 직접 시공 일정 추가.
 *   고객명·번호·시공일·주소·총금액·계약금(+받음여부). 기존 CustomerEntity 필드만 사용 (DB 변경 없음).
 *   같은 번호가 있으면 그 고객 재사용(upsertByPhone).
 */
class ScheduleAddViewModel(private val container: AppContainer) : ViewModel() {

    private val _toast = MutableStateFlow<String?>(null)
    val toast = _toast.asStateFlow()
    fun consumeToast() { _toast.value = null }

    private val _saving = MutableStateFlow(false)
    val saving = _saving.asStateFlow()

    /** 수첩의 일당 목록 — 일정 등록 시 배정용. */
    val workers: StateFlow<List<NotebookContactEntity>> =
        container.notebookRepository.observeWorkers()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 수첩의 거래처 목록 — 프로토 "거래처 일감" 모드 탭 선택용(VENDOR). */
    val vendors: StateFlow<List<NotebookContactEntity>> =
        container.notebookRepository.observeVendors()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 통화·문자한 고객 — 프로토 "불러오기" picker. 최근 갱신 순. */
    val recentContacts: StateFlow<List<CustomerEntity>> =
        container.customerRepository.observeAll()
            .map { list -> list.sortedByDescending { it.updatedAt } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 번호별 ✨AI 한줄요약 — 불러오기 목록에서 "번호만 보고 누군지 모름" 해소 힌트. (suffix→요약) */
    val contactHints: StateFlow<Map<String, String>> =
        container.conversationAiRepository.observeAll()
            .map { list ->
                list.mapNotNull { s ->
                    s.cardSummary?.trim()?.takeIf { it.isNotEmpty() && !it.equals("null", true) }
                        ?.let { s.phoneSuffix to it }
                }.toMap()
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** 프로토 addPartner — 새 거래처를 수첩(VENDOR)에 추가. */
    fun addVendor(name: String, phone: String, onDone: () -> Unit) {
        val nm = name.trim()
        if (nm.isBlank()) return
        viewModelScope.launch {
            container.notebookRepository.add(NotebookContactEntity.KIND_VENDOR, nm, phone.trim(), "", "")
            _toast.value = "거래처를 수첩에 추가했어요"
            onDone()
        }
    }

    fun submit(
        name: String,
        phone: String,
        dayMs: Long,
        workMinutes: Int?,
        workDays: Int,
        address: String,
        totalAmount: Long?,
        depositAmount: Long?,
        depositPaid: Boolean,
        crewWorkers: List<NotebookContactEntity>,
        crewWage: Long,
        onDone: () -> Unit
    ) {
        val digits = phone.filter { it.isDigit() }
        if (digits.length < 8) {
            _toast.value = "전화번호를 정확히 입력해주세요"
            return
        }
        if (_saving.value) return
        _saving.value = true
        viewModelScope.launch {
            withContext(NonCancellable) {
                val customer = container.customerRepository.upsertByPhone(
                    phoneNumber = phone.trim(),
                    name = name.trim().takeIf { it.isNotBlank() }
                )
                val id = customer.id
                container.customerRepository.updateScheduledWorkDate(id, DateTimeUtils.startOfDay(dayMs))
                container.customerRepository.updateScheduledWorkTiming(id, workMinutes, workDays)
                if (address.isNotBlank()) container.customerRepository.updateAddress(id, address)
                if (totalAmount != null) container.customerRepository.updateTotalAmount(id, totalAmount)
                if (depositAmount != null) container.customerRepository.updateDepositAmount(id, depositAmount)
                if (depositPaid && (depositAmount ?: 0L) > 0L) {
                    container.customerRepository.updateDepositPaidAt(id, System.currentTimeMillis())
                }
                // 일당 배정 — 일정↔정산 연결(자동 차감) + 함께한 현장 기록.
                if (crewWorkers.isNotEmpty() && crewWage > 0L) {
                    for (w in crewWorkers) {
                        container.jobCrewRepository.assign(w.id, w.name, id, dayMs, crewWage)
                    }
                }
                runCatching { container.autoCategoryClassifier.reclassify(id) }
            }
            // 캘린더 등록은 서버 미전송(로컬 CustomerEntity only) → admin KPI 측정용 여정 이벤트만 발사. (2026-06-25 cowork 요청)
            val label = name.trim().takeIf { it.isNotBlank() } ?: ("…" + digits.takeLast(4))
            container.journeyEventRepository.track("schedule_create", screen = "schedule", target = label)
            _saving.value = false
            _toast.value = "일정 등록 완료"
            onDone()
        }
    }
}
