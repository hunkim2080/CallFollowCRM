package com.detailline.callfollowcrm.presentation.screen.followup

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.detailline.callfollowcrm.data.AppContainer
import com.detailline.callfollowcrm.data.local.entity.MessageTemplateEntity
import com.detailline.callfollowcrm.data.local.entity.TemplateAttachmentEntity
import com.detailline.callfollowcrm.domain.model.CallType
import com.detailline.callfollowcrm.domain.model.HandledStatus
import com.detailline.callfollowcrm.recording.CustomerBackfiller
import com.detailline.callfollowcrm.service.NotificationHelper
import com.detailline.callfollowcrm.util.CallLogHelper
import com.detailline.callfollowcrm.util.SmsIntentHelper
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalCoroutinesApi::class)
class FollowUpViewModel(
    private val container: AppContainer,
    initialPhone: String,
    private val callRecordId: Long?,
    initialTemplateId: Long? = null
) : ViewModel() {

    val templates = container.messageTemplateRepository.observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _state = MutableStateFlow(
        FollowUpUiState(
            phoneNumber = initialPhone,
            customerName = "",
            memo = "",
            selectedTemplateId = null,
            messageBody = ""
        )
    )
    val state = _state.asStateFlow()

    init {
        // 알림 빠른 액션으로 진입한 경우 그 템플릿 자동 선택 (본문 미리 채움).
        if (initialTemplateId != null && initialTemplateId > 0) {
            viewModelScope.launch {
                val template = container.messageTemplateRepository.findById(initialTemplateId)
                if (template != null) {
                    _state.update {
                        it.copy(selectedTemplateId = template.id, messageBody = template.body)
                    }
                }
            }
        }
    }

    /** 선택된 템플릿에 등록된 첨부 사진. 비어 있으면 단순 SMS, 있으면 MMS 공유 흐름. */
    val attachments = _state
        .map { it.selectedTemplateId }
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList())
            else container.templateAttachmentRepository.observeByTemplate(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _toast = MutableStateFlow<String?>(null)
    val toast = _toast.asStateFlow()
    fun consumeToast() { _toast.value = null }

    /** "최근 통화에서 가져오기" 다이얼로그용. 처음엔 비어 있고, 사용자가 열 때 로딩. */
    private val _recentCalls = MutableStateFlow<List<CallLogHelper.RecentCall>>(emptyList())
    val recentCalls = _recentCalls.asStateFlow()

    fun loadRecentCalls(context: Context) {
        viewModelScope.launch {
            _recentCalls.value = withContext(kotlinx.coroutines.Dispatchers.IO) {
                CallLogHelper.queryRecent(context, limit = 20)
            }
        }
    }

    fun pickRecentCall(number: String) {
        // 다이얼로그에서 가져온 원본(예: "01080052080") 도 입력 즉시 하이픈 포맷 적용.
        setPhone(com.detailline.callfollowcrm.util.PhoneNumberFormatter.formatProgressive(number))
    }

    /**
     * "최근 문자에서 가져오기" 다이얼로그용. 문자만 주고받은 고객 (통화기록 없음) 도 잡을 수 있도록.
     * READ_SMS 권한이 없거나 토글 OFF 면 빈 리스트.
     */
    private val _recentSms = MutableStateFlow<List<com.detailline.callfollowcrm.data.repository.SmsRepository.SmsContact>>(emptyList())
    val recentSms = _recentSms.asStateFlow()

    fun loadRecentSmsContacts() {
        viewModelScope.launch {
            _recentSms.value = withContext(kotlinx.coroutines.Dispatchers.IO) {
                container.smsRepository.queryRecentContacts()
            }
        }
    }

    fun pickRecentSms(number: String) {
        setPhone(com.detailline.callfollowcrm.util.PhoneNumberFormatter.formatProgressive(number))
    }

    fun setPhone(value: String) = _state.update { it.copy(phoneNumber = value) }
    fun setName(value: String) = _state.update { it.copy(customerName = value) }
    fun setMemo(value: String) = _state.update { it.copy(memo = value) }
    fun setBody(value: String) = _state.update { it.copy(messageBody = value) }
    fun setFirstMetAt(value: Long?) = _state.update { it.copy(firstMetAt = value) }

    fun selectTemplate(template: MessageTemplateEntity) = _state.update {
        it.copy(selectedTemplateId = template.id, messageBody = template.body)
    }

    /** 문자앱 열기 + DB 기록. SMS 자동 발송은 절대 하지 않는다. */
    fun sendSms(context: Context, onDone: () -> Unit) {
        val s = _state.value
        if (s.phoneNumber.isBlank()) { _toast.value = "전화번호를 입력해 주세요."; return }
        if (s.messageBody.isBlank()) { _toast.value = "문자 본문을 입력해 주세요."; return }

        viewModelScope.launch {
            val customer = container.customerRepository.upsertByPhone(
                phoneNumber = s.phoneNumber,
                name = s.customerName.ifBlank { null },  // 빈 값이면 기존 이름 보존
                memo = s.memo.takeIf { it.isNotBlank() }
            )

            // 수동 입력(callRecordId 없음)이면 "수동 등록" 통화기록을 만들어
            // 홈에 카드로 보이게 한다. 진짜 통화에서 온 경우는 기존 markHandled 흐름.
            ensureCallRecord(s.phoneNumber, customer.id, HandledStatus.MESSAGE_OPENED)

            // 통화기록 + 에이닷 녹음 한꺼번에 백필 (백그라운드, fire-and-forget)
            CustomerBackfiller.backfill(context, container, s.phoneNumber, customer.id)

            container.messageHistoryRepository.recordDraftOpened(
                phoneNumber = s.phoneNumber,
                customerId = customer.id,
                templateId = s.selectedTemplateId,
                body = s.messageBody
            )

            val attached: List<TemplateAttachmentEntity> = attachments.value
            val result = if (attached.isEmpty()) {
                SmsIntentHelper.openSmsCompose(context, s.phoneNumber, s.messageBody)
            } else {
                SmsIntentHelper.openSmsComposeWithAttachments(
                    context = context,
                    phoneNumber = s.phoneNumber,
                    body = s.messageBody,
                    attachmentUris = attached.map { Uri.parse(it.fileUri) }
                )
            }

            when (result) {
                is SmsIntentHelper.Result.Opened -> {
                    callRecordId?.let { id ->
                        container.callRecordRepository.markHandled(id, HandledStatus.MESSAGE_OPENED, customer.id)
                    }
                    NotificationHelper.cancelFor(context, callRecordId)
                    _toast.value = if (attached.isEmpty()) {
                        "문자앱을 열었어요. 전송 버튼은 직접 눌러주세요."
                    } else {
                        "번호 복사됨 → 메시지 앱의 수신인 검색창에 길게 눌러 붙여넣으세요. (본문·사진은 자동)"
                    }
                    onDone()
                }
                is SmsIntentHelper.Result.Failed -> {
                    _toast.value = "문자앱 열기 실패: ${result.reason}"
                }
            }
        }
    }

    fun saveOnly(context: Context, onDone: () -> Unit) {
        val s = _state.value
        if (s.phoneNumber.isBlank()) { _toast.value = "전화번호를 입력해 주세요."; return }
        viewModelScope.launch {
            val customer = container.customerRepository.upsertByPhone(
                phoneNumber = s.phoneNumber,
                name = s.customerName.ifBlank { null },
                memo = s.memo.takeIf { it.isNotBlank() }
            )

            callRecordId?.let { id ->
                container.callRecordRepository.markHandled(id, HandledStatus.SAVED, customer.id)
            }
            // 수동 입력이면 "수동 등록" 통화기록을 만들어 홈 카드에 노출시킨다.
            // 이게 없으면 customer 만 만들어지고 홈 리스트(=CallRecord 기반)에 안 보임.
            ensureCallRecord(s.phoneNumber, customer.id, HandledStatus.SAVED)

            NotificationHelper.cancelFor(context, callRecordId)

            // 1) 즉시 피드백 — 화면 닫히기 전에 보이도록 먼저 토스트.
            _toast.value = "저장 완료"

            // 2) 백필은 백그라운드 fire-and-forget. 토스트 갱신은 안 함 (화면 닫힌 후이므로).
            CustomerBackfiller.backfill(context, container, s.phoneNumber, customer.id)

            // 3) snackbar 잠깐 보이고 닫히도록 약간 지연.
            kotlinx.coroutines.delay(600)
            onDone()
        }
    }

    /**
     * 진짜 통화에서 온 게(callRecordId 있음) 아니라 수동 입력으로 들어온 경우,
     * 홈/파이프라인 화면에 카드로 보이도록 "수동 등록" 통화기록을 하나 만들어 둔다.
     * 같은 번호의 오늘자 통화기록이 이미 있으면 만들지 않는다 (중복 방지).
     */
    private suspend fun ensureCallRecord(
        phoneNumber: String,
        customerId: Long,
        handledStatus: HandledStatus
    ) {
        if (callRecordId != null) return  // 알림에서 진입한 케이스 — 진짜 통화기록 이미 있음

        // firstMetAt 지정 시 = 옛 고객 등록. 그 날짜에 가짜 record. 미지정 = 지금.
        val firstMet = _state.value.firstMetAt
        val targetTime = firstMet ?: System.currentTimeMillis()

        // 같은 번호 + 같은 날짜의 통화기록이 이미 있으면 중복 안 만듦.
        val (dayStart, dayEnd) = com.detailline.callfollowcrm.util.DateTimeUtils.todayBounds(targetTime)
        val existingThatDay = container.callRecordRepository
            .observeByPhone(phoneNumber)
            .first()
            .any { it.endedAt in dayStart..dayEnd }
        if (existingThatDay) return

        container.callRecordRepository.create(
            phoneNumber = phoneNumber,
            callType = CallType.MANUAL,
            duration = 0,
            startedAt = targetTime,
            endedAt = targetTime,
            linkedCustomerId = customerId,
            handledStatus = handledStatus
        )
    }

    fun postpone(context: Context, onDone: () -> Unit) {
        callRecordId?.let { id ->
            viewModelScope.launch {
                container.callRecordRepository.markHandled(id, HandledStatus.SKIPPED)
                NotificationHelper.cancelFor(context, callRecordId)
                _toast.value = "나중에 처리로 표시했어요."
                onDone()
            }
        } ?: run { _toast.value = "처리 완료"; onDone() }
    }
}

data class FollowUpUiState(
    val phoneNumber: String,
    val customerName: String,
    val memo: String,
    val selectedTemplateId: Long?,
    val messageBody: String,
    /**
     * 첫 만난 날짜 (옛 고객 등록용). null = 오늘. 수동 입력에서 사장님이 DatePicker 로 선택.
     * ensureCallRecord 가 이 시각으로 가짜 CallRecord 의 endedAt 을 설정 → 타임라인의 그 날짜에 표시.
     */
    val firstMetAt: Long? = null
)
