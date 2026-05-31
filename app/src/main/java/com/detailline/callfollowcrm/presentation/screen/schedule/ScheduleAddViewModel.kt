package com.detailline.callfollowcrm.presentation.screen.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.detailline.callfollowcrm.data.AppContainer
import com.detailline.callfollowcrm.util.DateTimeUtils
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    fun submit(
        name: String,
        phone: String,
        dayMs: Long,
        address: String,
        totalAmount: Long?,
        depositAmount: Long?,
        depositPaid: Boolean,
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
                if (address.isNotBlank()) container.customerRepository.updateAddress(id, address)
                if (totalAmount != null) container.customerRepository.updateTotalAmount(id, totalAmount)
                if (depositAmount != null) container.customerRepository.updateDepositAmount(id, depositAmount)
                if (depositPaid && (depositAmount ?: 0L) > 0L) {
                    container.customerRepository.updateDepositPaidAt(id, System.currentTimeMillis())
                }
                runCatching { container.autoCategoryClassifier.reclassify(id) }
            }
            _saving.value = false
            _toast.value = "일정 등록 완료"
            onDone()
        }
    }
}
