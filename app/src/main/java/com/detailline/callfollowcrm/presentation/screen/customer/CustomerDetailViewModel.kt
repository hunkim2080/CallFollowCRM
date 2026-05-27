package com.detailline.callfollowcrm.presentation.screen.customer

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.detailline.callfollowcrm.data.AppContainer
import com.detailline.callfollowcrm.data.local.entity.CallRecordEntity
import com.detailline.callfollowcrm.data.local.entity.CallSummaryEntity
import com.detailline.callfollowcrm.data.local.entity.CustomerEntity
import com.detailline.callfollowcrm.data.local.entity.MessageHistoryEntity
import com.detailline.callfollowcrm.data.local.entity.RecordingAttachmentEntity
import com.detailline.callfollowcrm.data.repository.SmsRepository
import com.detailline.callfollowcrm.domain.model.RecordingSourceType
import com.detailline.callfollowcrm.recording.AdotSummaryImporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalCoroutinesApi::class)
class CustomerDetailViewModel(
    private val container: AppContainer,
    private val customerId: Long
) : ViewModel() {

    val customer = container.customerRepository.observeById(customerId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val callRecords: kotlinx.coroutines.flow.StateFlow<List<CallRecordEntity>> =
        container.customerRepository.observeById(customerId)
            .flatMapLatest { c ->
                if (c == null) emptyFlow() else container.callRecordRepository.observeByPhone(c.phoneNumber)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val messageHistory: kotlinx.coroutines.flow.StateFlow<List<MessageHistoryEntity>> =
        container.messageHistoryRepository.observeByCustomer(customerId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val recordings: kotlinx.coroutines.flow.StateFlow<List<RecordingAttachmentEntity>> =
        container.recordingRepository.observeByCustomer(customerId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val summaries: kotlinx.coroutines.flow.StateFlow<List<CallSummaryEntity>> =
        container.callSummaryRepository.observeByCustomer(customerId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * P1/P2 AI 대화 요약 — ChatScreen 의 박스와 같은 데이터 (server 응답이 캐시되면 자동 emit).
     * 사장님 요청 (2026-05-24): "고객 상세에 대화 요약 있으면 문자 다시 검토 안 해도 됨".
     * Room observe — phoneNumber 가 바뀌면 (보통은 안 바뀜) 새 suffix 로 자동 재구독.
     * 서버 미구현이면 영구 null → 박스 안 보임 (silent).
     */
    val aiSummary: kotlinx.coroutines.flow.StateFlow<com.detailline.callfollowcrm.data.local.entity.AiSummaryEntity?> =
        container.customerRepository.observeById(customerId)
            .flatMapLatest { c ->
                val phone = c?.phoneNumber.orEmpty()
                val suffix = phone.filter { it.isDigit() }.takeLast(8)
                if (suffix.length < 7) kotlinx.coroutines.flow.flowOf(null)
                else container.conversationAiRepository.observe(suffix)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * 시스템 SMS 에서 이 고객 번호와 주고받은 모든 문자 (송+수신).
     * Settings 토글이 켜져 있고 READ_SMS 권한이 있을 때만 채워짐.
     * 실시간 observe 는 안 함 — 화면 진입 시 / 새로고침 시 로드.
     *
     * 갤럭시 메시지로 보낸 문자도 여기에 포함되므로, MessageHistory 와 별도로
     * 타임라인의 단일 출처(single source of truth)로 사용.
     */
    private val _systemSms = MutableStateFlow<List<SmsRepository.SmsMessage>>(emptyList())
    val systemSms = _systemSms.asStateFlow()

    /**
     * 추출된 시공 현장 주소 — 사장님 통점 (2026-05-25): 시공자는 주소를 가장 자주 복사.
     * 시스템 SMS + cached_messages 둘 다 보고 한국 주소 패턴 추출 ([AddressExtractor]).
     * 다음 세션: 서버 conversation-summary endpoint 가 LLM 으로 정확도 ↑ 추출 예정.
     *
     * 최신 메시지부터 훑어 첫 매칭. 없으면 null → UI 측에서 "아직 인식 X" 빈 상태 표시.
     */
    private val _cachedSms = MutableStateFlow<List<com.detailline.callfollowcrm.data.repository.SmsRepository.SmsMessage>>(emptyList())
    val extractedAddress: kotlinx.coroutines.flow.StateFlow<String?> =
        kotlinx.coroutines.flow.combine(_systemSms, _cachedSms) { sys, cached ->
            val all = (sys + cached).distinctBy { it.dateMs to it.body }
            com.detailline.callfollowcrm.util.AddressExtractor.extractFromMessages(
                all.sortedByDescending { it.dateMs }.map { it.body }
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * 주고받은 문자 — CustomerDetail 의 "📩 문자" 접이식 섹션이 구독 (2026-05-27 사장님 요청).
     *   시스템 SMS + cached_messages 합쳐서 중복 제거 + 최신순. ChatScreen 진입 안 해도
     *   사장님이 대화 흐름 빠르게 검토.
     */
    val mergedMessages: kotlinx.coroutines.flow.StateFlow<List<SmsRepository.SmsMessage>> =
        kotlinx.coroutines.flow.combine(_systemSms, _cachedSms) { sys, cached ->
            (sys + cached)
                .distinctBy { it.dateMs to it.body }
                .sortedByDescending { it.dateMs }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ─────────────────────────────────────────────────────────────────────
    // 저장 메서드들은 모두 NonCancellable 로 감싼다. 이유:
    //   사용자가 저장 버튼 누른 직후 뒤로가기 등으로 화면이 닫히면 viewModelScope 가 cancel 되어
    //   짧은 DB write 가 중도에 끊길 수 있다. withContext(NonCancellable) 안의 작업은
    //   parent scope cancel 후에도 끝까지 완료된다.
    // ─────────────────────────────────────────────────────────────────────

    // 2026-05-25: updateStatus 제거 — 갤메시지 식 카테고리 시스템으로 통일.

    /** 사장님이 직접 카테고리 박음. null = 미분류로 돌림. */
    fun setCategory(categoryId: Long?) = viewModelScope.launch {
        withContext(NonCancellable) {
            container.categoryRepository.assignCustomer(customerId, categoryId)
            markTodayCallsAsHandled()
        }
    }

    /**
     * CustomerDetail picker 에서 "+ 새 카테고리" 누름 → 이름 받아서 생성 + 이 고객에게 즉시 할당.
     * 같은 이름이 이미 있으면 그 카테고리에 박음 (upsert idempotent).
     */
    fun addCategoryAndAssign(name: String) = viewModelScope.launch {
        if (name.isBlank()) return@launch
        withContext(NonCancellable) {
            val entity = runCatching {
                container.categoryRepository.upsert(name.trim())
            }.getOrNull() ?: return@withContext
            container.categoryRepository.assignCustomer(customerId, entity.id)
            markTodayCallsAsHandled()
        }
    }

    /** 카테고리 목록 — pill 선택 다이얼로그 표시. */
    val categories = container.categoryRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun updateMemo(memo: String) = viewModelScope.launch {
        withContext(NonCancellable) {
            container.customerRepository.updateMemo(customerId, memo)
            markTodayCallsAsHandled()
        }
    }

    fun updateName(name: String) = viewModelScope.launch {
        withContext(NonCancellable) {
            container.customerRepository.updateName(customerId, name.takeIf { it.isNotBlank() })
            markTodayCallsAsHandled()
        }
    }

    fun updateLeadHeat(leadHeat: com.detailline.callfollowcrm.domain.model.LeadHeat?) = viewModelScope.launch {
        withContext(NonCancellable) {
            container.customerRepository.updateLeadHeat(customerId, leadHeat)
        }
    }

    // ─── 입금 관련 (계약금/잔금) ─────────────────────────────────────
    // 체크박스 토글 → "지금" 받음/안 받음 으로 시각 기록.
    // 금액 입력 → 별도 필드. 두 개 분리해서 사장님이 "금액만 정함 / 받기까진 안 함" 케이스도 가능.

    fun setDepositPaid(paid: Boolean) = viewModelScope.launch {
        withContext(NonCancellable) {
            container.customerRepository.updateDepositPaidAt(
                customerId,
                if (paid) System.currentTimeMillis() else null
            )
        }
    }

    /** 받은 날짜만 수정 (사장님이 DatePicker 로 다른 날짜 선택). 자정 normalize 안 함 — 사장님이 정한 그대로. */
    fun setDepositPaidAt(epochMs: Long) = viewModelScope.launch {
        withContext(NonCancellable) {
            container.customerRepository.updateDepositPaidAt(customerId, epochMs)
        }
    }

    fun setDepositAmount(amount: Long?) = viewModelScope.launch {
        withContext(NonCancellable) {
            container.customerRepository.updateDepositAmount(customerId, amount)
        }
    }

    fun setBalancePaid(paid: Boolean) = viewModelScope.launch {
        withContext(NonCancellable) {
            container.customerRepository.updateBalancePaidAt(
                customerId,
                if (paid) System.currentTimeMillis() else null
            )
        }
    }

    fun setBalancePaidAt(epochMs: Long) = viewModelScope.launch {
        withContext(NonCancellable) {
            container.customerRepository.updateBalancePaidAt(customerId, epochMs)
        }
    }

    fun setBalanceAmount(amount: Long?) = viewModelScope.launch {
        withContext(NonCancellable) {
            container.customerRepository.updateBalanceAmount(customerId, amount)
        }
    }

    /**
     * 시공 예약일 설정. epoch ms (자정으로 정규화). 취소는 null.
     * 2026-05-25: 자동 status RESERVATION_CONFIRMED 전환 제거 — 카테고리 시스템으로 이관.
     */
    fun updateScheduledWorkDate(epochMs: Long?) = viewModelScope.launch {
        val normalized = epochMs?.let { com.detailline.callfollowcrm.util.DateTimeUtils.startOfDay(it) }
        withContext(NonCancellable) {
            container.customerRepository.updateScheduledWorkDate(customerId, normalized)
            markTodayCallsAsHandled()
        }
    }

    /**
     * 사장님이 CustomerDetail 에서 명시 액션(상태/메모/이름/예약일 변경)을 하면
     * 그 번호의 오늘자 UNHANDLED 통화들을 모두 SAVED 로 정리.
     * 의미: 통화 단위 알림 처리도 함께 클리어 → 홈에서 "후속 N건 미처리" 노이즈 사라짐.
     */
    private suspend fun markTodayCallsAsHandled() {
        val c = customer.value ?: return
        val (start, end) = com.detailline.callfollowcrm.util.DateTimeUtils.todayBounds()
        runCatching {
            container.callRecordRepository.markUnhandledByPhoneToday(
                phoneNumber = c.phoneNumber,
                customerId = customerId,
                from = start,
                to = end
            )
        }
    }

    /** 사용자가 에이닷 요약 텍스트를 클립보드/입력으로 붙여넣을 때 */
    fun importPastedSummary(context: Context, text: String) {
        AdotSummaryImporter.importPasted(
            context = context,
            container = container,
            text = text,
            customerId = customerId
        )
    }

    fun attachManualRecording(context: Context, uri: Uri) {
        try {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: SecurityException) { /* persistable 아님 */ }
        viewModelScope.launch {
            val name = queryName(context, uri) ?: uri.lastPathSegment.orEmpty()
            container.recordingRepository.add(
                fileUri = uri.toString(),
                fileName = name,
                sourceType = RecordingSourceType.MANUAL_PICK,
                customerId = customerId
            )
        }
    }

    /**
     * 화면 열릴 때 호출. 시스템 통화기록(CallLog) + 에이닷 녹음 폴더 둘 다에서
     * 이 고객 번호의 데이터를 우리 DB로 백필. 알림 못 누른 통화/옛 녹음도 채워짐.
     */
    fun backfillFromSystem(context: Context) {
        val c = customer.value ?: return
        viewModelScope.launch {
            runCatching {
                container.callRecordRepository.syncFromCallLog(context, c.phoneNumber, customerId)
            }
            // 에이닷 폴더가 연결돼 있으면 그 번호의 과거 녹음도 백필
            com.detailline.callfollowcrm.recording.AdotFolderScanner.scanByPhone(
                context = context,
                container = container,
                phoneNumber = c.phoneNumber,
                customerId = customerId
            )
        }
        loadSystemSms()
    }

    /** 인라인 발송 결과 toast 용. */
    private val _toast = MutableStateFlow<String?>(null)
    val toast = _toast.asStateFlow()
    fun consumeToast() { _toast.value = null }

    /** AI 다듬기 진행 중 표시 (스피너 등에 사용). */
    private val _aiPolishing = MutableStateFlow(false)
    val aiPolishing = _aiPolishing.asStateFlow()

    /**
     * 사장님이 입력한 본문을 AI 가 사장님 톤으로 다듬어 반환.
     *
     * 현재는 placeholder — 자체 서버 + LLM 마련 전까지는 안내 토스트만 띄움.
     * 추후 구현:
     *   1) 사장님 톤 학습 데이터 (과거 보낸 SMS 샘플) 를 시스템 프롬프트에 포함
     *   2) 자체 서버의 /polish 엔드포인트 호출
     *   3) onPolished(다듬어진 본문) 콜백으로 화면에 반영
     *
     * UI 측은 이 메서드 시그니처를 그대로 유지하므로, 본문만 교체하면 즉시 동작.
     */
    fun aiPolish(rawBody: String, onPolished: (String) -> Unit) {
        if (rawBody.isBlank()) return
        // TODO: 자체 서버 연동 후 실제 호출. 지금은 안내만.
        _toast.value = "AI 다듬기 ✨ 는 자체 서버 마련 후 활성화돼요"
    }

    /**
     * 고객 상세 문자 섹션에서 인라인으로 직접 발송.
     *
     * 정책: SEND_SMS 권한이 있어야만 발송. 자동응답 외에 사장님의 명시 액션으로
     * 발송을 허용하는 두 번째 예외 경로. (project_ringo.md 의 SEND_SMS 정책 확장)
     *
     * UX: optimistic 으로 _systemSms 에 즉시 prepend → 사용자는 보낸 즉시 화면에서 확인.
     * 시스템 SMS 프로바이더 sent 항목에도 자동으로 들어가므로 다음번 loadSystemSms 시 자동 반영.
     */
    fun sendInlineSms(context: Context, body: String, onResult: (Boolean) -> Unit) {
        val c = customer.value
        if (c == null) {
            _toast.value = "고객 정보가 없어요"
            onResult(false); return
        }
        val trimmed = body.trim()
        if (trimmed.isEmpty()) {
            onResult(false); return
        }
        if (!com.detailline.callfollowcrm.util.SmsSender.hasPermission(context)) {
            _toast.value = "SMS 발송 권한이 필요해요"
            onResult(false); return
        }

        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                com.detailline.callfollowcrm.util.SmsSender.sendDirect(context, c.phoneNumber, trimmed)
            }
            if (ok) {
                // optimistic UI: 보낸 메시지를 즉시 리스트 맨 앞에 (정렬은 dateMs DESC 가정).
                val optimistic = SmsRepository.SmsMessage(
                    id = -System.currentTimeMillis(), // 임시 id (음수로 충돌 회피)
                    address = c.phoneNumber,
                    body = trimmed,
                    dateMs = System.currentTimeMillis(),
                    sent = true
                )
                _systemSms.value = listOf(optimistic) + _systemSms.value

                withContext(NonCancellable) {
                    runCatching {
                        container.messageHistoryRepository.recordAutoSend(
                            phoneNumber = c.phoneNumber,
                            customerId = customerId,
                            templateId = null,
                            body = trimmed,
                            status = com.detailline.callfollowcrm.domain.model.MessageStatus.INLINE_SENT
                        )
                    }
                    // 인라인 발송도 명시 액션 → 오늘자 UNHANDLED 정리
                    markTodayCallsAsHandled()
                }
                _toast.value = "보냈어요"
                onResult(true)
            } else {
                withContext(NonCancellable) {
                    runCatching {
                        container.messageHistoryRepository.recordAutoSend(
                            phoneNumber = c.phoneNumber,
                            customerId = customerId,
                            templateId = null,
                            body = trimmed,
                            status = com.detailline.callfollowcrm.domain.model.MessageStatus.INLINE_FAILED
                        )
                    }
                }
                _toast.value = "발송 실패"
                onResult(false)
            }
        }
    }

    /**
     * 시스템 SMS 에서 이 고객 번호와 주고받은 모든 문자 로드 (송+수신).
     * READ_SMS 권한 = onboarding 에서 기본 승인됨. 시스템 설정에서 사장님이 끄면 silent skip.
     */
    fun loadSystemSms() {
        val c = customer.value ?: return
        // cached_messages 는 권한 무관하게 우리 DB → 항상 로드. systemSms 는 권한 필요.
        viewModelScope.launch(Dispatchers.IO) {
            val suffix = c.phoneNumber.filter { it.isDigit() }.takeLast(8)
            if (suffix.length >= 7) {
                val cached = runCatching {
                    container.cachedMessageRepository.load(suffix, limit = 500)
                }.getOrDefault(emptyList())
                _cachedSms.value = cached
            }
        }
        if (!container.smsRepository.hasReadPermission()) {
            _systemSms.value = emptyList()
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val list = runCatching {
                container.smsRepository.queryByPhone(c.phoneNumber)
            }.getOrDefault(emptyList())
            _systemSms.value = list
        }
    }

    private fun queryName(context: Context, uri: Uri): String? = try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    } catch (_: Exception) { null }
}

data class CustomerDetailUiState(
    val customer: CustomerEntity?,
    val callRecords: List<CallRecordEntity>,
    val messageHistory: List<MessageHistoryEntity>,
    val recordings: List<RecordingAttachmentEntity>
)
