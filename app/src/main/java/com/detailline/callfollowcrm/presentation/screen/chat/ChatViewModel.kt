package com.detailline.callfollowcrm.presentation.screen.chat

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.detailline.callfollowcrm.ai.CustomerHint
import com.detailline.callfollowcrm.ai.HistoryMessage
import com.detailline.callfollowcrm.ai.PrepareContext
import com.detailline.callfollowcrm.ai.ReplySuggestions
import com.detailline.callfollowcrm.ai.SuggestionStatus
import com.detailline.callfollowcrm.data.AppContainer
import com.detailline.callfollowcrm.data.local.entity.CustomerEntity
import com.detailline.callfollowcrm.data.local.entity.ImportantMessageEntity
import com.detailline.callfollowcrm.data.local.entity.MessageTemplateEntity
import com.detailline.callfollowcrm.data.repository.SmsRepository
import com.detailline.callfollowcrm.domain.model.MessageStatus
import com.detailline.callfollowcrm.util.SmsSender
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 대시보드에서 번호 클릭 시 진입하는 채팅 화면의 ViewModel.
 *
 * 책임:
 *  - 해당 번호로 주고받은 시스템 SMS/MMS 로드 + observe (수동 갱신)
 *  - 사장님이 입력한 본문을 SEND_SMS 로 직접 발송 (optimistic UI)
 *  - 등록된 메시지 템플릿 목록 제공 (가로 알약 칩)
 *  - [ⓘ] 정보 버튼 → Customer 없으면 upsert 후 id 반환 (CustomerDetail 진입용)
 *
 * 정책 (project-ringo):
 *  - SEND_SMS 는 명시 액션 경로 (사장님이 ▶ 누름). 자동 발송 아님.
 *  - 번호만 알고 Customer 없는 경우도 화면 표시 가능. 사장님이 처음 발송/[ⓘ] 누를 때 upsert.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel(
    private val container: AppContainer,
    val phoneNumber: String,
    initialCustomerId: Long?
) : ViewModel() {

    // customer id 의 single source of truth. 처음엔 인자 또는 phone lookup 으로 채워짐.
    // 발송 / [ⓘ] 탭 시 upsert 결과 id 가 여기로 들어오면서 customer flow 가 자동 재구독됨.
    private val _customerId = MutableStateFlow<Long?>(initialCustomerId)

    init {
        // initialCustomerId 가 없으면 phone 으로 한 번 lookup (없으면 그대로 null 유지).
        if (initialCustomerId == null && phoneNumber.isNotBlank()) {
            viewModelScope.launch {
                val found = container.customerRepository.findByPhone(phoneNumber)
                if (found != null) _customerId.value = found.id
            }
        }
    }

    /** id 변경되면 자동으로 다시 observe. id null 이면 null flow. */
    val customer: kotlinx.coroutines.flow.StateFlow<CustomerEntity?> = _customerId
        .flatMapLatest { id ->
            if (id == null) flowOf(null)
            else container.customerRepository.observeById(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val templates = container.messageTemplateRepository.observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList<MessageTemplateEntity>())

    /** ⭐ 별표된 메시지 목록 (이 번호 한정). */
    val starred: kotlinx.coroutines.flow.StateFlow<List<ImportantMessageEntity>> =
        container.importantMessageRepository.observeByPhone(phoneNumber)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _messages = MutableStateFlow<List<SmsRepository.SmsMessage>>(emptyList())
    val messages = _messages.asStateFlow()

    private val _toast = MutableStateFlow<String?>(null)
    val toast = _toast.asStateFlow()
    fun consumeToast() { _toast.value = null }

    // AI 다듬기 ✨ 진행 중 여부. ChatScreen 의 ✨ 버튼이 이 값을 구독해서 로딩 인디케이터 표시.
    // 첫 호출은 모델 로드 ~10초 + 추론 3~5초까지 걸릴 수 있어 시각 피드백 필수.
    private val _aiPolishing = MutableStateFlow(false)
    val aiPolishing = _aiPolishing.asStateFlow()

    // 답변 추천 (Phase 1). 맥미니 캐시에서 가져온 마지막 ReplySuggestions.
    // ChatScreen 진입 시 loadSuggestions 로 채워짐. ↻ 누르면 regenerateSuggestions.
    private val _suggestions = MutableStateFlow<ReplySuggestions?>(null)

    // 표시용 effective suggestions:
    //  - 가장 최신 메시지가 고객 수신 메시지여야 함 (사장님이 마지막 발신 = 추천 숨김)
    //  - suggestions 의 basedOnReceivedAtMs 가 그 메시지보다 오래되지 않아야 함 (stale 차단)
    val effectiveSuggestions: StateFlow<ReplySuggestions?> =
        combine(_suggestions, _messages) { sug, msgs ->
            val latest = msgs.firstOrNull() ?: return@combine null
            if (latest.sent) return@combine null
            if (sug == null) return@combine null
            if (sug.basedOnReceivedAtMs < latest.dateMs) return@combine null
            sug
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // ↻ 재생성 진행 중. ChatScreen 이 ↻ 버튼 자리 로딩 인디케이터에 사용.
    private val _suggestionsLoading = MutableStateFlow(false)
    val suggestionsLoading = _suggestionsLoading.asStateFlow()

    /**
     * 화면 진입 또는 새로고침 시 호출. READ_SMS 권한 + 토글 모두 OK 여야 실제 조회.
     *
     * 3-stage 로드 (체감 즉시 + 점진 최신화):
     *   stage 1: Room 캐시 통째 즉시 표시 — prefetch 가 돌고 있어 보통 채워져 있음
     *   stage 2: 시스템 SMS 만 빠르게 (~100ms) → SMS 부분 캐시 교체 + UI emit (기존 MMS 유지)
     *   stage 3: 시스템 MMS 백그라운드 (~수초) → MMS 부분 캐시 교체 + UI emit (전체 합쳐서)
     *
     * 효과:
     *  - 첫 진입 (prefetch 안 끝났으면 빈 캐시) → stage 2 의 SMS 가 가장 먼저 보임 (사진은 늦게)
     *  - 두 번째 진입 → stage 1 캐시로 즉시 모두 보임 → stage 2/3 가 조용히 갱신
     */
    fun loadMessages() {
        val enabled = container.preferences.receivedSmsEnabled
        if (!enabled || !container.smsRepository.hasReadPermission()) {
            _messages.value = emptyList()
            return
        }
        val digits = phoneNumber.filter { it.isDigit() }
        if (digits.length < 7) {
            _messages.value = emptyList()
            return
        }
        val suffix = digits.takeLast(8)

        viewModelScope.launch(Dispatchers.IO) {
            // stage 1: 캐시 즉시 표시 (SMS + MMS 모두 포함)
            val cached = runCatching {
                container.cachedMessageRepository.load(suffix)
            }.getOrDefault(emptyList())
            if (cached.isNotEmpty()) {
                _messages.value = cached
            }

            // stage 2: SMS 만 빠르게 새로고침. MMS 는 캐시값(정확) 그대로 유지하고 합쳐서 emit.
            val freshSms = runCatching {
                container.smsRepository.querySmsOnly(phoneNumber)
            }.getOrDefault(emptyList())
            val cachedMmsOnly = runCatching {
                container.cachedMessageRepository.loadMmsOnly(suffix)
            }.getOrDefault(emptyList())
            if (freshSms.isNotEmpty() || cached.isNotEmpty()) {
                _messages.value = (freshSms + cachedMmsOnly).sortedByDescending { it.dateMs }
                runCatching {
                    container.cachedMessageRepository.replaceSmsOnlyForSuffix(suffix, freshSms)
                }
            }

            // stage 3: MMS 백그라운드. 끝나면 SMS + MMS 합쳐서 다시 emit + MMS 캐시 교체.
            val freshMms = runCatching {
                container.smsRepository.queryMmsOnly(phoneNumber)
            }.getOrDefault(emptyList())
            _messages.value = (freshSms + freshMms).sortedByDescending { it.dateMs }
            runCatching {
                container.cachedMessageRepository.replaceMmsOnlyForSuffix(suffix, freshMms)
            }
        }
    }

    /**
     * 본문 전송. 성공 시 optimistic 으로 _messages 에 prepend.
     * Customer 없으면 NEW_INQUIRY 로 자동 생성 (첫 발송이 = 첫 후속 처리이므로).
     */
    fun sendMessage(context: Context, body: String, onResult: (Boolean) -> Unit) {
        val trimmed = body.trim()
        if (trimmed.isEmpty()) { onResult(false); return }
        if (phoneNumber.isBlank()) {
            _toast.value = "고객 번호가 없어요"
            onResult(false); return
        }
        if (!SmsSender.hasPermission(context)) {
            _toast.value = "SMS 발송 권한이 필요해요"
            onResult(false); return
        }
        viewModelScope.launch {
            // 1) Customer 보장. status 인자 생략 → 기존 customer 면 보존, 신규면 NEW_INQUIRY 기본.
            val c = withContext(Dispatchers.IO + NonCancellable) {
                container.customerRepository.upsertByPhone(phoneNumber = phoneNumber)
            }
            _customerId.value = c.id

            // 2) 실제 발송
            val ok = withContext(Dispatchers.IO) {
                SmsSender.sendDirect(context, phoneNumber, trimmed)
            }

            // 3) optimistic UI: 보낸 메시지 즉시 리스트 맨 앞에. id 충돌 피하려 음수 임시 id.
            if (ok) {
                val optimistic = SmsRepository.SmsMessage(
                    id = -System.currentTimeMillis(),
                    address = phoneNumber,
                    body = trimmed,
                    dateMs = System.currentTimeMillis(),
                    sent = true
                )
                _messages.value = listOf(optimistic) + _messages.value
            }

            // 4) MessageHistory 기록 (성공/실패 둘 다 — 통계 + handled-check 용)
            withContext(Dispatchers.IO + NonCancellable) {
                runCatching {
                    container.messageHistoryRepository.recordAutoSend(
                        phoneNumber = phoneNumber,
                        customerId = c.id,
                        templateId = null,
                        body = trimmed,
                        status = if (ok) MessageStatus.INLINE_SENT else MessageStatus.INLINE_FAILED
                    )
                }
            }

            _toast.value = if (ok) "보냈어요" else "발송 실패"
            onResult(ok)
        }
    }

    /**
     * [ⓘ] 정보 버튼용. Customer 없으면 NEW_INQUIRY 로 upsert 후 id 반환 → CustomerDetail 진입.
     * 항상 non-null id 반환.
     */
    /**
     * 사진 첨부 메시지 발송 — klinker MMS 직접 발송 ((c)안의 1차 시도).
     *
     * 성공 시: optimistic 으로 _messages 에 prepend + Customer upsert + MessageHistory 기록.
     * 실패 시: onResult(false) — 호출자가 SmsIntentHelper 갤럭시 메시지 fallback 으로 빠짐.
     */
    fun sendMessageWithPhotos(
        context: android.content.Context,
        body: String,
        uris: List<android.net.Uri>,
        onResult: (Boolean) -> Unit
    ) {
        if (phoneNumber.isBlank()) {
            _toast.value = "고객 번호가 없어요"
            onResult(false); return
        }
        if (uris.isEmpty()) {
            // 사진 없으면 그냥 텍스트 경로
            sendMessage(context, body, onResult); return
        }
        viewModelScope.launch {
            // Customer 보장 (status 보존)
            val c = withContext(Dispatchers.IO + NonCancellable) {
                container.customerRepository.upsertByPhone(phoneNumber = phoneNumber)
            }
            _customerId.value = c.id

            val ok = withContext(Dispatchers.IO) {
                com.detailline.callfollowcrm.util.SmsSender.sendMms(
                    context = context,
                    phoneNumber = phoneNumber,
                    body = body,
                    uris = uris
                )
            }

            if (ok) {
                // optimistic UI — 사진 첨부도 imageUris 로 즉시 화면에 표시
                val optimistic = SmsRepository.SmsMessage(
                    id = -System.currentTimeMillis(),
                    address = phoneNumber,
                    body = body,
                    dateMs = System.currentTimeMillis(),
                    sent = true,
                    imageUris = uris
                )
                _messages.value = listOf(optimistic) + _messages.value

                withContext(Dispatchers.IO + NonCancellable) {
                    runCatching {
                        container.messageHistoryRepository.recordAutoSend(
                            phoneNumber = phoneNumber,
                            customerId = c.id,
                            templateId = null,
                            body = if (body.isBlank()) "📷 사진 ${uris.size}장" else body,
                            status = MessageStatus.INLINE_SENT
                        )
                    }
                }
                _toast.value = "사진 보냈어요"
            }
            // 실패 시 토스트 안 띄움 — 호출자가 fallback 다이얼로그 띄울 거라 노이즈 방지
            onResult(ok)
        }
    }

    /**
     * 메시지 ⭐ 토글. 채팅 말풍선 길게 누름 시 호출.
     * 같은 (dateMs, sent) 가 있으면 해제, 없으면 등록.
     */
    fun toggleStar(messageBody: String, messageDateMs: Long, sent: Boolean) = viewModelScope.launch {
        withContext(Dispatchers.IO + NonCancellable) {
            container.importantMessageRepository.toggle(
                phone = phoneNumber,
                customerId = customer.value?.id,
                messageBody = messageBody,
                messageDateMs = messageDateMs,
                sent = sent
            )
        }
    }

    /**
     * 사장님이 입력한 본문을 AI 가 사장님 톤으로 다듬어 돌려줌.
     *
     * 백엔드 = 맥미니 Ollama Tailnet (RINGGO_BACKEND_BRIEF.md).
     * 첫 호출은 모델 로드까지 ~10초. aiPolishing StateFlow 가 true 동안 ChatScreen 이 로딩 표시.
     * 실패의 절대다수는 Tailscale 미연결 (ConnectException/UnknownHost). 사장님 입장 단순화 위해 한 메시지로 통합.
     */
    fun aiPolish(rawBody: String, onPolished: (String) -> Unit) {
        if (rawBody.isBlank()) {
            _toast.value = "다듬을 본문이 비어 있어요"
            return
        }
        if (_aiPolishing.value) return
        _aiPolishing.value = true
        viewModelScope.launch {
            val result = container.refineRepository.refine(rawBody)
            _aiPolishing.value = false
            result.fold(
                onSuccess = { polished -> onPolished(polished) },
                onFailure = { _toast.value = "AI 서버 연결 실패 — Tailscale 확인하세요" }
            )
        }
    }

    /**
     * ChatScreen 진입 시 한 번 호출. 맥미니 캐시에서 기존 추천 답변 가져옴.
     * SMS 수신 즉시 SmsReceiver 가 prepare 트리거했으면 보통 READY 상태.
     * 실패/MISSING/GENERATING 은 silent — 사장님이 ↻ 누르면 재생성.
     */
    fun loadSuggestions() = viewModelScope.launch {
        val result = container.suggestionRepository.fetch(phoneNumber).getOrNull() ?: return@launch
        if (result.status == SuggestionStatus.READY) {
            _suggestions.value = result.suggestions
        }
    }

    /**
     * ↻ 재생성. 사장님이 명시적으로 누름.
     * 흐름: PrepareContext 구성 → POST /prepare-reply → 2초 간격 폴링 (최대 10초) → READY 면 표시.
     * 컨텍스트 구성은 SmsReceiver 와 같은 로직 (최근 20건 + customer hint).
     */
    fun regenerateSuggestions() {
        val latestReceived = _messages.value.firstOrNull { !it.sent } ?: run {
            _toast.value = "고객 마지막 메시지가 없어요"
            return
        }
        if (_suggestionsLoading.value) return
        _suggestionsLoading.value = true
        viewModelScope.launch {
            try {
                val history = _messages.value
                    .take(20)
                    .map { sms ->
                        HistoryMessage(
                            role = if (sms.sent) "owner" else "customer",
                            body = sms.body,
                            timestampMs = sms.dateMs
                        )
                    }
                    .reversed()
                val c = customer.value
                val hint = c?.let {
                    CustomerHint(
                        name = it.name,
                        memo = it.memo.takeIf { m -> m.isNotBlank() },
                        leadHeat = it.leadHeat,
                        depositPaid = (it.depositAmount ?: 0L) > 0L
                    )
                }
                val ownerToneSamples = withContext(Dispatchers.IO) {
                    runCatching {
                        container.smsRepository.querySentMessages(limit = 50)
                    }.getOrDefault(emptyList())
                }
                val ctx = PrepareContext(
                    phone = phoneNumber,
                    latestMessage = latestReceived.body,
                    latestMessageReceivedAtMs = latestReceived.dateMs,
                    recentHistory = history,
                    customer = hint,
                    ownerToneSamples = ownerToneSamples
                )
                val prep = container.suggestionRepository.requestPrepare(ctx)
                if (prep.isFailure) {
                    _toast.value = "AI 서버 연결 실패 — Tailscale 확인하세요"
                    return@launch
                }
                // 2초 간격 폴링 — 최대 5회 (10초). gpt-oss 첫 로드 후엔 3~5초면 충분.
                repeat(5) {
                    delay(2_000)
                    val fetch = container.suggestionRepository.fetch(phoneNumber).getOrNull()
                    if (fetch?.status == SuggestionStatus.READY && fetch.suggestions != null) {
                        _suggestions.value = fetch.suggestions
                        return@launch
                    }
                }
                _toast.value = "추천 답변 생성 시간 초과"
            } finally {
                _suggestionsLoading.value = false
            }
        }
    }

    suspend fun ensureCustomerId(): Long {
        customer.value?.let { return it.id }
        val c = withContext(Dispatchers.IO + NonCancellable) {
            container.customerRepository.upsertByPhone(phoneNumber = phoneNumber)
        }
        _customerId.value = c.id
        return c.id
    }
}
