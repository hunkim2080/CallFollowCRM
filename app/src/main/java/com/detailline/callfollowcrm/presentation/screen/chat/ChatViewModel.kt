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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
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
        // 카톡식 읽음 처리 (2026-06-08): 채팅 열면 그 대화를 "읽음"으로 → 홈 "최근 대화" 파란 점 사라짐.
        //   답장 안 해도 열기만 하면 해제. (모든 진입 경로가 ChatViewModel 을 거치므로 여기 한 곳.)
        if (phoneNumber.isNotBlank()) {
            container.readStateStore.markRead(phoneNumber)
        }
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

    /** 문구 넣기 시트에서 바로 문구 삭제 (2026-06-07). */
    fun deleteTemplate(id: Long) = viewModelScope.launch {
        runCatching { container.messageTemplateRepository.deleteById(id) }
        _toast.value = "문구를 지웠어요"
    }

    /** 입력창에 쓴 내용을 새 문구(템플릿)로 바로 저장 — 키보드 없이, 채팅 흐름 그대로. (2026-06-07) */
    fun saveTextAsTemplate(text: String) = viewModelScope.launch {
        val body = text.trim()
        if (body.isBlank()) { _toast.value = "입력창에 문구를 먼저 쓰세요"; return@launch }
        val now = System.currentTimeMillis()
        val title = body.replace("\n", " ").trim().take(13).ifBlank { "새 문구" }   // 제목 13자 제한(SYNC 추가81b)
        runCatching {
            container.messageTemplateRepository.insert(
                MessageTemplateEntity(
                    title = title, body = body,
                    category = com.detailline.callfollowcrm.domain.model.TemplateCategory.CUSTOM.name,
                    isDefault = false, isActive = true, createdAt = now, updatedAt = now
                )
            )
        }.onSuccess { _toast.value = "문구로 저장했어요 ✓" }.onFailure { _toast.value = "저장 실패" }
    }

    /**
     * 채팅 중 "내 일정 확인" 시트용 — 시공일이 잡힌 모든 고객 (여러 날 시공 포함).
     *   고객과 대화하며 빈 날·가까운 현장을 바로 확인해 약속 잡기 좋게. (2026-06-01)
     */
    val scheduledJobs: StateFlow<List<CustomerEntity>> =
        container.customerRepository.observeAll()
            .map { list -> list.filter { it.scheduledWorkDate != null && it.scheduledWorkDate > 0L } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 견적서 작성기용 가격표. 사장님이 설정 → 가격표 관리 에서 CRUD 한 결과. */
    val pricingItems = container.pricingItemRepository.observeActive()
        .stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5_000),
            emptyList<com.detailline.callfollowcrm.data.local.entity.PricingItemEntity>()
        )

    /**
     * 견적 만들기에서 항목 가격을 그 자리에서 고침 → 가격표(단일 기준)에 바로 저장.
     *   마지막으로 바꾼 값이 다음에도 그대로 뜸(가격표=한 곳에서만 관리). (2026-06-25 사장님)
     *   priceWon=원 단위(이미 *10000 된 값). 0 이하면 무시.
     */
    fun updateItemPrice(id: Long, priceWon: Long) {
        if (id <= 0L || priceWon <= 0L) return
        viewModelScope.launch {
            val item = container.pricingItemRepository.findById(id) ?: return@launch
            if (item.price == priceWon) return@launch
            container.pricingItemRepository.update(item.copy(price = priceWon))
        }
    }

    /** ⭐ 별표된 메시지 목록 (이 번호 한정). */
    val starred: kotlinx.coroutines.flow.StateFlow<List<ImportantMessageEntity>> =
        container.importantMessageRepository.observeByPhone(phoneNumber)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _messages = MutableStateFlow<List<SmsRepository.SmsMessage>>(emptyList())
    val messages = _messages.asStateFlow()

    /**
     * 채팅 통화 구간 (2026-06-01) — 이 번호와 주고받은 통화 기록. 메시지와 시간순으로 병합 표시.
     *   suffix 매칭 (저장 포맷 차이 흡수). 통화 0건이면 빈 리스트 → 화면엔 안 보임.
     *   요약 가져오기(에이닷/서버)는 별개 — 여기선 통화 자체(유형·시간·길이) 노출까지.
     */
    val callRecords: StateFlow<List<com.detailline.callfollowcrm.data.local.entity.CallRecordEntity>> = run {
        val suffix = phoneNumber.filter { it.isDigit() }.takeLast(8)
        if (suffix.length < 7) kotlinx.coroutines.flow.flowOf(emptyList())
        else container.callRecordRepository.observeByPhoneSuffix(suffix)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 통화 녹음 첨부 (2026-06-16) — 통화 카드에 재생 플레이어를 띄우기 위함. suffix 매칭. */
    val recordings: StateFlow<List<com.detailline.callfollowcrm.data.local.entity.RecordingAttachmentEntity>> = run {
        val suffix = phoneNumber.filter { it.isDigit() }.takeLast(8)
        if (suffix.length < 7) kotlinx.coroutines.flow.flowOf(emptyList())
        else container.recordingRepository.observeByPhoneSuffix(suffix)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * 통화요약 (2026-06-08) — 에이닷 txt 자동 import 또는 붙여넣기로 저장된 CallSummary.
     *   통화기록과 같은 suffix 매칭. 통화 카드(CallSegment)가 시각으로 짝지어 "AI 요약됨" 상태로 표시.
     */
    val callSummaries: StateFlow<List<com.detailline.callfollowcrm.data.local.entity.CallSummaryEntity>> = run {
        val suffix = phoneNumber.filter { it.isDigit() }.takeLast(8)
        if (suffix.length < 7) kotlinx.coroutines.flow.flowOf(emptyList())
        else container.callSummaryRepository.observeByPhoneSuffix(suffix)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * 지금 서버에서 요약 중인 통화의 녹음시각(recordedAt) 집합 — 이 채팅 번호 한정.
     *   통화카드(CallSegment)가 자기 통화 시간대와 겹치면 "요약 중…" 스피너를 띄운다.
     *   (녹음 공유 → 서버 요약은 백그라운드라 전역 [CallSummaryProgress] 로만 진행 상태가 보임.)
     */
    val summarizingRecordedAt: StateFlow<Set<Long>> =
        com.detailline.callfollowcrm.recording.CallSummaryProgress.inProgress
            .map { keys ->
                val suffix = phoneNumber.filter { it.isDigit() }.takeLast(8)
                keys.mapNotNull { k ->
                    val parts = k.split("@")
                    if (parts.size == 2 && parts[0] == suffix) parts[1].toLongOrNull() else null
                }.toSet()
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /**
     * 통화가 끝나면 곧 자동요약 워커가 도는 상황인지(설정 ON + 녹음폴더 연결). 방금 끝난 통화 카드에
     *   미리 "요약 중…"을 띄워, 정적 '요약하기' 버튼만 보이던 문제를 없앤다. (2026-06-20 사장님)
     */
    val autoSummaryActive: Boolean
        get() = container.preferences.autoSummaryEnabled &&
            com.detailline.callfollowcrm.recording.AdotFolderScanner.isConnected(container.appContext)

    /**
     * 통화 카드 탭 → 그 통화 한 건을 연결된 녹음 폴더에서 찾아 즉시 요약. (2026-06-14 사장님)
     *   에이닷 들어가 '공유' 안 해도 됨. 진행 중엔 CallSummaryProgress 로 스피너, 결과는 토스트.
     */
    fun summarizeCall(record: com.detailline.callfollowcrm.data.local.entity.CallRecordEntity, context: android.content.Context) {
        val phone = record.phoneNumber
        val at = record.startedAt ?: record.endedAt
        // 탭 즉시(메인 스레드) 스피너 ON — "통화 내용 요약 중…"이 바로 떠 반응이 빠르게 느껴지게. (2026-06-18 사장님)
        //   begin 은 StateFlow 갱신뿐이라 메인에서도 즉시·안전. 실제 무거운 작업은 아래 IO 에서.
        com.detailline.callfollowcrm.recording.CallSummaryProgress.begin(phone, at)
        // Dispatchers.IO 필수 — summarizeCallNow 가 SAF 폴더 스캔(tree.listFiles)·DB 를 하므로 메인에서 돌면
        //   "통화 요약하기 눌러도 반응 없음 + 앱 꺼짐(ANR)". (2026-06-18 사장님 B폰). viewModelScope 기본=Main.immediate.
        viewModelScope.launch(Dispatchers.IO) {
            val res = runCatching {
                // callRecordId 전달 — 찾은 요약을 '탭한 그 통화'에 직접 연결해 카드에 즉시 표시(시각 드리프트로 안 보이던 버그 fix).
                com.detailline.callfollowcrm.recording.AdotFolderScanner.summarizeCallNow(context, container, phone, at, callRecordId = record.id)
            }.getOrDefault(com.detailline.callfollowcrm.recording.AdotFolderScanner.SummarizeResult.FAILED)  // 호출 자체가 throw = 실패(파일 없음 아님)
            com.detailline.callfollowcrm.recording.CallSummaryProgress.end(phone, at)
            _toast.value = when (res) {
                com.detailline.callfollowcrm.recording.AdotFolderScanner.SummarizeResult.OK -> "통화 내용을 요약했어요 ✨"
                com.detailline.callfollowcrm.recording.AdotFolderScanner.SummarizeResult.ALREADY -> "이미 요약돼 있어요"
                com.detailline.callfollowcrm.recording.AdotFolderScanner.SummarizeResult.NO_FOLDER -> "통화 녹음 폴더를 먼저 연결해주세요 (설정 → 통화 자동 요약)"
                com.detailline.callfollowcrm.recording.AdotFolderScanner.SummarizeResult.NO_FILE -> "이 통화의 녹음 파일을 못 찾았어요. 통화 녹음이 켜져 있는지 확인해주세요."
                com.detailline.callfollowcrm.recording.AdotFolderScanner.SummarizeResult.FAILED -> "통화 요약을 끝내지 못했어요. 잠시 후 다시 시도해주세요. (계속되면 알려주세요)"
            }
        }
    }

    /** 통화 요약을 사장님이 직접 고침(잘못된 요약 정정). summaryText 만 교체 → 채팅·고객정보·미리보기 모든 화면 자동 반영. (2026-06-23 사장님) */
    fun updateCallSummary(summary: com.detailline.callfollowcrm.data.local.entity.CallSummaryEntity, newText: String) {
        viewModelScope.launch(Dispatchers.IO) {
            container.callSummaryRepository.update(
                summary.copy(summaryText = newText.trim(), updatedAt = System.currentTimeMillis())
            )
            _toast.value = "통화 요약을 수정했어요 ✓"
        }
    }

    /**
     * 시공접수서 제출 이벤트 (2026-06-05) — 고객이 접수서를 작성 완료하면 IntakeSyncManager 가 기록.
     *   채팅 타임라인에 통화 카드처럼 "📋 접수서 작성 완료" 이벤트 카드로 표시(제출 시각 기준).
     */
    val intakeEvents: StateFlow<List<com.detailline.callfollowcrm.data.local.entity.IntakeEventEntity>> = run {
        val suffix = phoneNumber.filter { it.isDigit() }.takeLast(8)
        if (suffix.length < 7) kotlinx.coroutines.flow.flowOf(emptyList())
        else container.intakeEventRepository.observe(suffix)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * 변경/처리 이력 이벤트 (2026-06-30 사장님) — 고객 상세에서 일정/금액/잔금을 바꾸면
     *   채팅 타임라인에 "📅 일정 변경 / 💰 금액 변경 / 💵 잔금 받음" 카드로 표시(처리 시각 기준).
     */
    val timelineEvents: StateFlow<List<com.detailline.callfollowcrm.data.local.entity.TimelineEventEntity>> = run {
        val suffix = phoneNumber.filter { it.isDigit() }.takeLast(8)
        if (suffix.length < 7) kotlinx.coroutines.flow.flowOf(emptyList())
        else container.timelineEventRepository.observe(suffix)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * P0/P1/P2 AI 요약 — ChatScreen 상단 박스와 AI 제안 박스가 구독.
     * Room observe — 서버 응답이 캐시되면 자동 emit.
     * 서버 미구현이면 영구히 null → 화면에 아무 박스도 안 보임 (조용히 숨김).
     */
    val aiSummary: StateFlow<com.detailline.callfollowcrm.data.local.entity.AiSummaryEntity?> = run {
        val suffix = phoneNumber.filter { it.isDigit() }.takeLast(8)
        if (suffix.length < 7) {
            kotlinx.coroutines.flow.flowOf(null)
        } else {
            container.conversationAiRepository.observe(suffix)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _toast = MutableStateFlow<String?>(null)
    val toast = _toast.asStateFlow()
    fun consumeToast() { _toast.value = null }

    /**
     * Composer 임시저장 (2026-05-27 사장님 통점 — 뒤로갔다 재진입 시 입력 날아감).
     *   AppContainer 의 ChatDraftStore 에 phone 별 보관. 앱 살아있는 동안만.
     *   ChatScreen 이 init 시 loadDraft(), 입력 변경마다 saveDraft(), 전송 성공 시 clearDraft().
     */
    fun loadDraft(): String = container.chatDraftStore.get(phoneNumber)
    fun saveDraft(text: String) { container.chatDraftStore.set(phoneNumber, text) }
    fun clearDraft() { container.chatDraftStore.clear(phoneNumber) }

    // AI 다듬기 ✨ 진행 중 여부. ChatScreen 의 ✨ 버튼이 이 값을 구독해서 로딩 인디케이터 표시.
    // 첫 호출은 모델 로드 ~10초 + 추론 3~5초까지 걸릴 수 있어 시각 피드백 필수.
    private val _aiPolishing = MutableStateFlow(false)
    val aiPolishing = _aiPolishing.asStateFlow()

    // ─── 2026-05-29 킬러콘텐츠 3단계 — chip 행동 시그널 capture ────────────────────
    // 사장님 chip 탭 → set. send 성공 시 SENT_AS_IS/EDITED/REFINED_THEN_SENT 판정 기준.
    // onCleared 시 picked != null && !pickedActioned 면 DISMISSED.
    private var pickedChoice: com.detailline.callfollowcrm.ai.ReplyChoice? = null
    // chip 탭 직후 ✨ 다듬기 호출 했는지 (pickedRefined → REFINED_THEN_SENT 판정).
    private var pickedRefined: Boolean = false
    // chip 탭 후 send 됐는지 (true 면 onCleared 에서 DISMISSED 안 박음).
    private var pickedActioned: Boolean = false
    // chip 탭 시점의 ReplySuggestions snapshot (scenario / scenario_confidence 추출용).
    private var pickedSuggestionsSnapshot: ReplySuggestions? = null

    /**
     * SMS/MMS 발송 중 — Composer 의 ▶ 자리에 spinner 표시.
     *   2026-05-27 진행감 fix: 사장님이 ▶ 누른 후 "보내는 중" 시각 피드백.
     */
    private val _isSending = MutableStateFlow(false)
    val isSending = _isSending.asStateFlow()

    /**
     * 대화 요약 / 카드 요약 갱신 중 — 헤더에 spinner 표시.
     *   2026-05-27 사장님 보고 fix: aiSummary 가 옛 cache 면서 새 LLM 호출 중일 때
     *   "갱신 중" 시각 표시 없어 사장님이 헤더 먹통으로 오인 → spinner 추가.
     */
    private val _isSummaryRefreshing = MutableStateFlow(false)
    val isSummaryRefreshing = _isSummaryRefreshing.asStateFlow()

    // 요약 생성 실패 — 서버 장애/타임아웃/크레딧 등. true 면 "요약을 못 만들었어요 · 다시" 표시
    //   (옛 무한 shimmer 방지 — 추천 영역의 _suggestionsFailed 와 동일 UX). (2026-06-30)
    private val _summaryFailed = MutableStateFlow(false)
    val summaryFailed = _summaryFailed.asStateFlow()

    // 답변 추천 (Phase 1). 맥미니 캐시에서 가져온 마지막 ReplySuggestions.
    // ChatScreen 진입 시 loadSuggestions 로 채워짐. ↻ 누르면 regenerateSuggestions.
    //
    // 초기값 = AppContainer 의 SuggestionsCacheStore 에서 phone 으로 즉시 복원 (2026-05-28).
    //   재진입 시 chips 가 잠시 사라졌다 다시 채워지는 끊김을 0ms 로 단축.
    //   낡은 chips 위험은 effectiveSuggestions 의 stale 차단 (basedOnReceivedAtMs < latest.dateMs) 이 처리.
    private val _suggestions = MutableStateFlow<ReplySuggestions?>(
        container.suggestionsCacheStore.get(phoneNumber)
    )

    // _suggestions 변경 → 자동 cache put. 다음 재진입 때 instant 복원.
    private val cachePersistJob = viewModelScope.launch {
        _suggestions.collect { container.suggestionsCacheStore.put(phoneNumber, it) }
    }

    // 표시용 effective suggestions:
    //  - 가장 최신 메시지가 고객 수신 메시지여야 함 (사장님이 마지막 발신 = 추천 숨김)
    //  - 2026-05-28 사장님 통점 fix: 기존 stale 차단 (basedOnReceivedAtMs < latest.dateMs) 제거.
    //    이유: 알림에서 본 답변이 ChatScreen 진입 시 사라짐. "왜 사라지냐" 일관성 통점.
    //    대안: stale 여부를 별도 [suggestionsAreStale] 로 노출 → ChatScreen 에서 "📨 새 메시지 도착,
    //    ↻ 갱신" 안내. 사장님이 보고 직접 ↻ 결정 (자동 호출 X = 사장님 명시 의도 존중).
    val effectiveSuggestions: StateFlow<ReplySuggestions?> =
        combine(_suggestions, _messages) { sug, msgs ->
            val latest = msgs.firstOrNull() ?: return@combine null
            if (latest.sent) return@combine null
            sug
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * 답변 추천이 stale 인지 — 마지막 고객 메시지 시각보다 추천이 더 오래된 경우.
     * UI 는 chip 자체는 그대로 보여주되 작은 인디케이터로 "📨 새 메시지 도착" 안내.
     */
    val suggestionsAreStale: StateFlow<Boolean> =
        combine(_suggestions, _messages) { sug, msgs ->
            val latest = msgs.firstOrNull() ?: return@combine false
            if (latest.sent) return@combine false
            if (sug == null) return@combine false
            sug.basedOnReceivedAtMs < latest.dateMs
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /**
     * 마지막 대화가 '통화'로 끝났는지 — 마지막 문자보다 더 최근에 통화가 있었던 경우.
     *   true 면 답할 문자가 없음 → 추천을 준비하지 않고(스피너 X), ↻ 눌러도 "문자가 오면 준비할게요" 안내만.
     *   (2026-06-17 사장님: "통화로 끝났는데도 막내가 답변을 준비한다고 나오네")
     */
    val lastActivityIsCall: StateFlow<Boolean> =
        combine(_messages, callRecords) { msgs, calls ->
            val latestMsgMs = msgs.firstOrNull()?.dateMs ?: 0L
            val latestCallMs = calls.maxOfOrNull { it.endedAt } ?: 0L
            latestCallMs > 0L && latestCallMs > latestMsgMs
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    // ↻ 재생성 진행 중. ChatScreen 이 ↻ 버튼 자리 로딩 인디케이터에 사용.
    private val _suggestionsLoading = MutableStateFlow(false)
    val suggestionsLoading = _suggestionsLoading.asStateFlow()

    // 추천 생성 실패 — 서버 장애/타임아웃/크레딧 등. true 면 "못 만들었어요 — 다시 시도" 표시(옛것만 계속 보이지 않게). (2026-06-27 추천 정합성 개선)
    private val _suggestionsFailed = MutableStateFlow(false)
    val suggestionsFailed = _suggestionsFailed.asStateFlow()

    // stale 자동 새로고침 무한루프/중복 방지 — 이 '최신 고객 메시지 시각'으로 이미 자동 시도했으면 다시 안 함. (수동 ↻ 는 별개)
    private var autoRefreshAttemptedForMs = 0L

    /**
     * 추천이 stale(최신 고객 메시지보다 옛 기준)이면 (A 정책) 자동으로 새 추천 생성.
     *   - 이미 생성 중이거나, 이 메시지로 이미 자동 시도했으면 안 함(중복 폭탄 방지).
     *   - 수동 ↻(regenerateSuggestions)는 이 가드와 무관하게 항상 동작.
     * @return 자동 새로고침을 실제로 시작했으면 true.
     */
    fun refreshIfStale(): Boolean {
        if (lastActivityIsCall.value || _suggestionsLoading.value) return false
        val latestCustomer = _messages.value.firstOrNull { !it.sent } ?: return false
        val cur = _suggestions.value ?: return false
        if (cur.basedOnReceivedAtMs >= latestCustomer.dateMs) return false   // 이미 최신
        if (autoRefreshAttemptedForMs == latestCustomer.dateMs) return false // 이 메시지로 이미 자동 시도
        autoRefreshAttemptedForMs = latestCustomer.dateMs
        regenerateSuggestions(auto = true)
        return true
    }

    /** 화면의 옛 추천이 지금 고객 메시지 기준 몇 개를 못 담았나 — "고객 새 문자 N개 안 반영" 표시용. */
    val unreflectedCustomerCount: StateFlow<Int> =
        combine(_suggestions, _messages) { sug, msgs ->
            val latest = msgs.firstOrNull() ?: return@combine 0
            if (latest.sent || sug == null) return@combine 0
            msgs.count { !it.sent && it.dateMs > sug.basedOnReceivedAtMs }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    // ── 원칙 발견 (Phase 2, 2026-06-17 사장님) ────────────────────────────────
    //   추천 ≠ 사장님 실제 답이 확실할 때 서버가 추론한 '원칙 후보'. null 이면 카드 없음.
    //   초기값 = 영속된 미결정 발견 복원(뒤로가기로 사라졌던 것). 선언 시점 계산이라 init 순서 NPE 없음
    //   (constructor 파라미터 phoneNumber/container 만 읽음). (2026-06-18 사장님)
    private val _principleDiscovery = MutableStateFlow(computeInitialPendingPrinciple())
    val principleDiscovery: StateFlow<PrincipleDiscovery?> = _principleDiscovery.asStateFlow()

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
    /**
     * @param fullScan true(첫 진입) = MMS 전역 2000행 스캔 후 authoritative replace(삭제까지 반영).
     *   false(화면 대기 중 provider 변화로 재조회) = 최근 소량만 얕게 스캔 → 캐시에 merge.
     *   전체 스캔은 2000행 각각 주소조회라 사장님 폰(MMS 8900건)에서 수 초 걸림 → 재조회마다 돌면
     *   "사진 하나 보냈는데 오래 걸림". 캐시가 옛 사진을 이미 갖고 있으니 재조회는 얕게. (2026-07-02 사장님)
     */
    fun loadMessages(fullScan: Boolean = true) {
        // SMS 권한은 onboarding 에서 기본 승인 (PermissionHelper.requiredPermissions()).
        // 그래도 시스템 설정에서 사장님이 끄면 silent skip.
        if (!container.smsRepository.hasReadPermission()) {
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

            // 앱에서 보냈지만 시스템 문자함엔 없는 발신 보존본(기본 문자앱이 아닐 때). provider 결과에 합쳐 유지.
            val localSent = runCatching {
                container.cachedMessageRepository.loadLocalSent(suffix)
            }.getOrDefault(emptyList())

            // stage 2: SMS 만 빠르게 새로고침. MMS 는 캐시값(정확) 그대로 유지하고 합쳐서 emit.
            val freshSms = runCatching {
                container.smsRepository.querySmsOnly(phoneNumber)
            }.getOrDefault(emptyList())
            val cachedMmsOnly = runCatching {
                container.cachedMessageRepository.loadMmsOnly(suffix)
            }.getOrDefault(emptyList())
            if (freshSms.isNotEmpty() || cached.isNotEmpty()) {
                _messages.value = mergeWithLocalSent(freshSms, localSent, cachedMmsOnly)
                runCatching {
                    container.cachedMessageRepository.replaceSmsOnlyForSuffix(suffix, freshSms)
                }
            }

            // stage 3: MMS — fullScan(첫 진입)=깊게 2000, 아니면 얕게(재조회). 둘 다 캐시에 '누적(merge)'만 하고
            //   화면엔 항상 '캐시 전체'를 보여준다.
            //   ⚠️ 예전엔 첫 진입에서 replace(clear+insert)로 덮었는데, 사장님 폰(MMS 8900건 + 계속 전송)에서 2000 스캔이
            //      provider 경합으로 '불완전'(일부 사진 누락)하게 돌아오면 좋은 캐시를 부수어, 그 불완전한 결과가 화면·캐시를
            //      덮어써 "재진입하면 사진 싹 없어졌다가 시간지나면 다시 뜸" 버그가 났다. 빈-결과 가드는 '완전 빈 것'만 막고
            //      '일부만 온 것'은 못 막았음. → merge 는 절대 캐시를 줄이지 않으므로 스캔이 불완전해도 사진이 안 사라진다.
            //      화면도 freshMms(그때그때 스캔) 말고 mergedMms(누적 캐시)를 보여줘 항상 전체 유지.
            //      (삭제 즉시반영은 포기 — 사진 보존이 우선. 캐시는 다음 마일스톤에 trimOldest 로 상한 관리 가능.) (2026-07-02 사장님)
            val mmsScan = if (fullScan) 2000 else RECENT_MMS_SCAN_LIMIT
            val freshMms = runCatching {
                container.smsRepository.queryMmsOnly(phoneNumber, scanLimit = mmsScan)
            }.getOrDefault(emptyList())
            if (freshMms.isNotEmpty()) {
                runCatching { container.cachedMessageRepository.mergeMmsForSuffix(suffix, freshMms) }
            }
            val mergedMms = runCatching {
                container.cachedMessageRepository.loadMmsOnly(suffix)
            }.getOrDefault(cachedMmsOnly)
            _messages.value = mergeWithLocalSent(freshSms, localSent, mergedMms)
        }
    }

    /**
     * provider 결과 + 로컬 발신 보존본 + (MMS) 를 합쳐 최신순 정렬.
     * 중복 방지: provider 가 이미 같은 발신(같은 본문 ±2분)을 가졌으면 로컬 보존본은 제외
     *   — RING-GO 가 기본 문자앱이 되는 미래엔 provider 가 정본이라 로컬본이 군더더기가 됨.
     */
    private fun mergeWithLocalSent(
        providerSms: List<SmsRepository.SmsMessage>,
        localSent: List<SmsRepository.SmsMessage>,
        other: List<SmsRepository.SmsMessage>
    ): List<SmsRepository.SmsMessage> {
        val filteredLocal = localSent.filter { ls ->
            providerSms.none { p ->
                p.sent && p.body == ls.body && kotlin.math.abs(p.dateMs - ls.dateMs) < 120_000L
            }
        }
        return (providerSms + filteredLocal + other).sortedByDescending { it.dateMs }
    }

    /**
     * 본문 전송. 성공 시 optimistic 으로 _messages 에 prepend.
     * Customer 없으면 NEW_INQUIRY 로 자동 생성 (첫 발송이 = 첫 후속 처리이므로).
     */
    /**
     * 접수서 회신 카드에서 사장님이 [확인했어요] → 고객에게 확인 문자 발송 + 확인 시각 기록(버튼 1회). (2026-06-28 사장님)
     *   발송은 일반 문자 발송(sendMessage) 재사용 → 대화 타임라인에도 보낸 문자로 그대로 남음.
     *   시공일정·시공내용·시공현장주소 3개 필수 포함.
     */
    /** 접수서 확인 → 고객 안내 문자 본문(미리보기 + 발송 공용). */
    fun intakeConfirmBody(event: com.detailline.callfollowcrm.data.local.entity.IntakeEventEntity): String = buildString {
        append("✅ 사장님이 접수서를 확인했어요!\n")
        append("-\n")
        append("시공일정: ${event.dateLabel?.takeIf { it.isNotBlank() } ?: "협의 예정"}\n")
        append("시공내용: ${event.itemsText?.takeIf { it.isNotBlank() } ?: "협의 예정"}\n")
        append("시공 현장 주소: ${event.address?.takeIf { it.isNotBlank() } ?: "미입력"}\n")
        append("-\n")
        append("감사합니다.")
    }

    fun confirmIntake(context: Context, event: com.detailline.callfollowcrm.data.local.entity.IntakeEventEntity) {
        if (event.confirmedAt != null) return  // 이미 확인·발송함 — 중복 방지
        val body = intakeConfirmBody(event)
        sendMessage(context, body) { ok ->
            // 확인 발송 기록은 채팅 닫혀도(VM 취소) 반드시 커밋 — applicationScope. 안 그러면 재발송→고객 중복 문자. (2026-06-30 점검)
            if (ok) container.applicationScope.launch {
                runCatching { container.intakeEventRepository.markConfirmed(event.token, System.currentTimeMillis()) }
            }
        }
    }

    /**
     * 변경/처리 이력 카드의 [고객에게 알리기] — 사장님이 누르면 고객에게 안내 문자(확인 후 발송). 1회. (2026-06-30 사장님)
     *   이유(reason)는 사장님 기억용이라 고객 문자엔 안 실음.
     */
    /** 변경 이력 → 고객 안내 문자 본문(미리보기 + 발송 공용). null = 보낼 게 없음. */
    fun eventNotifyBody(event: com.detailline.callfollowcrm.data.local.entity.TimelineEventEntity): String? = when (event.type) {
        "schedule" -> buildString {
            append("📅 시공일정을 안내드려요\n")
            append("-\n")
            append("시공일정: ${event.newValue?.takeIf { it.isNotBlank() } ?: "협의 예정"}\n")
            append("-\n")
            append("위 일정으로 진행하겠습니다. 감사합니다.")
        }
        "amount" -> buildString {
            append("💰 시공금액을 안내드려요\n")
            append("-\n")
            append("시공금액: ${event.newValue?.takeIf { it.isNotBlank() } ?: "협의 예정"}\n")
            append("-\n")
            append("감사합니다.")
        }
        "balance_paid" -> buildString {
            append("💵 잔금 입금을 확인했습니다")
            event.newValue?.takeIf { it.isNotBlank() }?.let { append(" ($it)") }
            append("\n시공 마무리까지 잘 챙기겠습니다. 감사합니다!")
        }
        else -> null
    }

    fun notifyEvent(context: Context, event: com.detailline.callfollowcrm.data.local.entity.TimelineEventEntity) {
        if (event.notifiedAt != null) return  // 이미 알림 보냄 — 중복 방지
        val body = eventNotifyBody(event) ?: return
        sendMessage(context, body) { ok ->
            // "알림 보냄" 기록은 채팅 닫혀도(VM 취소) 반드시 커밋 — applicationScope. 안 그러면 재발송→고객 중복 문자. (2026-06-30 점검)
            if (ok) container.applicationScope.launch {
                runCatching { container.timelineEventRepository.markNotified(event.id, System.currentTimeMillis()) }
            }
        }
    }

    fun sendMessage(context: Context, body: String, onResult: (Boolean) -> Unit) {
        container.journeyEventRepository.track("button_click", screen = "chat", target = "send")
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
            _isSending.value = true
            try {
            // 1) Customer 보장. status 인자 생략 → 기존 customer 면 보존, 신규면 NEW_INQUIRY 기본.
            val c = withContext(Dispatchers.IO + NonCancellable) {
                container.customerRepository.upsertByPhone(phoneNumber = phoneNumber)
            }
            _customerId.value = c.id

            // 2) 실제 발송 — 로컬 보존은 여기(VM)서 동기로 직접(아래). SmsSender 비동기 보존은 끔(race·중복 방지).
            val ok = withContext(Dispatchers.IO) {
                SmsSender.sendDirect(context, phoneNumber, trimmed, persistLocalOnFail = false)
            }

            // 3) optimistic UI: 보낸 메시지 즉시 리스트 맨 앞에. id 충돌 피하려 음수 임시 id.
            //    ⚠️ 보존본(localSent)을 *동기로 먼저* 저장한 뒤 화면에 올린다 — 직후 loadMessages 가 돌아도 안 지워지게.
            //    (예전 버그: SmsSender 가 보존을 applicationScope 비동기 저장 → 그 사이 reload 가 발신을 지웠다가
            //     몇 초 뒤 다시 뜸. 그동안 OX 발견카드만 보여 "문자 안 가고 OX부터" 처럼 보임. 2026-06-18 사장님 보고)
            if (ok) {
                val nowMs = System.currentTimeMillis()
                val optimistic = SmsRepository.SmsMessage(
                    id = -nowMs,
                    address = phoneNumber,
                    body = trimmed,
                    dateMs = nowMs,
                    sent = true
                )
                withContext(Dispatchers.IO + NonCancellable) {
                    runCatching {
                        val digits = phoneNumber.filter { it.isDigit() }
                        val suffix = if (digits.length >= 8) digits.takeLast(8) else digits
                        if (suffix.length >= 7) container.cachedMessageRepository.persistLocalSent(suffix, optimistic)
                    }
                }
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

            // 5) 2026-05-28: sms_contacts_cache 즉시 upsert → HomeScreen 카드 lastBody/lastSent/hasOwnerReply 즉시 갱신.
            if (ok) {
                val digits = phoneNumber.filter { it.isDigit() }
                val suffix = if (digits.length >= 8) digits.takeLast(8) else digits
                val nowMs = System.currentTimeMillis()
                withContext(Dispatchers.IO + NonCancellable) {
                    runCatching {
                        container.smsContactCacheRepository.upsertOne(
                            SmsRepository.SmsContact(
                                address = phoneNumber,
                                normalizedSuffix = suffix,
                                lastBody = trimmed,
                                lastDateMs = nowMs,
                                lastSent = true,
                                hasOwnerReply = true,  // 사장님 발신 = 답장한 적 있음 true
                                firstDateMsInScan = nowMs
                            )
                        )
                    }
                }
            }

            // 2026-05-29 킬러콘텐츠 3단계 — 사장님 행동 시그널 capture.
            //   ok 든 ok 아니든 user intent (보내려 했음) 자체는 발생 → 발송 실패면 안 박음 (잡음).
            if (ok) {
                captureSendSignal(trimmed)
                maybeInferPrinciple(trimmed)  // (Phase 2) 추천과 다르게 보냈으면 '원칙' 추론. (2026-06-17 사장님)
            }

            _toast.value = if (ok) "보냈어요" else "발송 실패"
            onResult(ok)
            } finally {
                _isSending.value = false
            }
        }
    }

    /**
     * 2026-05-29 킬러콘텐츠 3단계 — chip 행동 시그널 DB record.
     *
     * 호출 시점: sendMessage 성공 직후.
     * 분류:
     *   - pickedChoice != null:
     *       sentText == picked.text → SENT_AS_IS
     *       pickedRefined           → REFINED_THEN_SENT
     *       else                    → EDITED + Levenshtein 거리
     *   - pickedChoice == null + suggestions 노출됐었음 → IGNORED
     *   - 둘 다 아님 → 시그널 안 박음 (잡음)
     *
     * DB 박은 후 pickedActioned = true 로 set → onCleared 에서 DISMISSED 중복 차단.
     */
    private fun captureSendSignal(sentText: String) {
        val picked = pickedChoice
        val sugs = pickedSuggestionsSnapshot ?: _suggestions.value
        val phoneSuffix = phoneNumber.filter { it.isDigit() }.takeLast(8)
        val nowMs = System.currentTimeMillis()
        val event = when {
            picked != null -> {
                val action = when {
                    sentText == picked.text -> com.detailline.callfollowcrm.data.local.entity.SuggestionEventAction.SENT_AS_IS
                    pickedRefined -> com.detailline.callfollowcrm.data.local.entity.SuggestionEventAction.REFINED_THEN_SENT
                    else -> com.detailline.callfollowcrm.data.local.entity.SuggestionEventAction.EDITED
                }
                val editDist = if (action == com.detailline.callfollowcrm.data.local.entity.SuggestionEventAction.EDITED) {
                    levenshtein(picked.text, sentText)
                } else null
                com.detailline.callfollowcrm.data.local.entity.SuggestionEventEntity(
                    phoneSuffix = phoneSuffix,
                    scenario = sugs?.scenario,
                    scenarioConfidence = sugs?.scenarioConfidence,
                    intentKey = picked.intentKey,
                    intentLabel = picked.label,
                    suggestionText = picked.text,
                    action = action,
                    finalSentText = if (action == com.detailline.callfollowcrm.data.local.entity.SuggestionEventAction.SENT_AS_IS) null else sentText,
                    editDistance = editDist,
                    createdAtMs = nowMs
                )
            }
            sugs != null && sugs.suggestions.isNotEmpty() -> {
                // IGNORED — chip 보였지만 사장님이 안 누르고 직접 typing → 전송.
                com.detailline.callfollowcrm.data.local.entity.SuggestionEventEntity(
                    phoneSuffix = phoneSuffix,
                    scenario = sugs.scenario,
                    scenarioConfidence = sugs.scenarioConfidence,
                    intentKey = null,
                    intentLabel = null,
                    suggestionText = null,
                    action = com.detailline.callfollowcrm.data.local.entity.SuggestionEventAction.IGNORED,
                    finalSentText = sentText,
                    editDistance = null,
                    createdAtMs = nowMs
                )
            }
            else -> null
        }
        if (event != null) {
            viewModelScope.launch(Dispatchers.IO) {
                runCatching { container.suggestionEventRepository.record(event) }
            }
        }
        if (picked != null) {
            pickedActioned = true
        }
    }

    // ════════════════════════ 원칙 발견 (Phase 2, 2026-06-17 사장님) ════════════════════════
    /**
     * 사장님이 추천과 '다르게' 보냈고, 그 추천이 확신 있는 추천이었을 때만 서버 LLM 에
     *   "왜 이렇게 했는지" 한 줄 원칙 추론을 요청 → 후보가 나오면 발견 카드(⭕/❌/나중에)를 띄운다.
     *   콜드스타트 자동 억제(확신 게이트) + 하루 한도 + 이미 거절/보유한 원칙 제외.
     *   호출 시점: sendMessage 성공 직후(captureSendSignal 다음) — picked/snapshot 아직 살아있음.
     */
    private fun maybeInferPrinciple(sentText: String) {
        val sugs = pickedSuggestionsSnapshot ?: _suggestions.value ?: return
        if (sugs.suggestions.isEmpty()) return
        val conf = sugs.scenarioConfidence ?: return                 // 확신 점수 없으면 패스
        if (conf < PRINCIPLE_MIN_CONFIDENCE) return                  // 확신 있을 때만(사례 적으면 자연히 안 물음)
        val aiText = (pickedChoice?.text ?: sugs.suggestions.firstOrNull()?.text)?.trim().orEmpty()
        if (aiText.isBlank()) return
        val mine = sentText.trim()
        if (mine == aiText) return                                   // 그대로 보냄 = 의도 없음
        if (levenshtein(aiText, mine) < PRINCIPLE_MIN_EDIT_DISTANCE) return  // 거의 같으면 패스
        val customerMsg = sugs.basedOnMessage.takeIf { it.isNotBlank() }
            ?: _messages.value.firstOrNull { !it.sent }?.body ?: return
        if (!canAskPrincipleToday()) return                          // 하루 한도
        viewModelScope.launch(Dispatchers.IO) {
            val existing = runCatching { container.principleRepository.enabledTexts() }.getOrDefault(emptyList())
            val candidate = runCatching {
                container.suggestionRepository.inferPrinciple(
                    customerMessage = customerMsg,
                    aiSuggestion = aiText,
                    ownerReply = mine,
                    scenario = sugs.scenario,
                    existingPrinciples = existing,
                    deviceId = container.preferences.deviceId,
                    ownerTrade = container.preferences.ownerTrades.firstOrNull()
                ).getOrNull()
            }.getOrNull() ?: return@launch
            val text = candidate.principle.trim()
            if (text.isBlank()) return@launch
            // 이미 ❌ 했거나 보유 중인 원칙이면 다시 안 띄움.
            if (text in container.preferences.dismissedPrincipleCandidates) return@launch
            if (existing.any { it.equals(text, ignoreCase = true) }) return@launch
            markPrincipleAsked()
            val disc = PrincipleDiscovery(principle = text, question = candidate.question)
            _principleDiscovery.value = disc
            persistPendingPrinciple(disc)  // 뒤로가기로 사라져도 ⭕/❌/나중에 고를 때까지 다시 뜨게. (2026-06-18 사장님)
        }
    }

    /** 발견 카드 ⭕ — 원칙 저장(막내가 이제 이렇게 생각). 카드는 '기억했어요'로 잠깐 바뀐 뒤 자동 사라짐. */
    fun acceptPrinciple() {
        val d = _principleDiscovery.value ?: return
        _principleDiscovery.value = d.copy(resolved = PrincipleResolved.OK)
        clearPendingPrinciple()  // 선택 완료 → 영속본 제거
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { container.principleRepository.add(d.principle, source = "discovered") }
        }
    }

    /** ❌ — 잊고 다시 안 물음(후보 기록). */
    fun rejectPrinciple() {
        val d = _principleDiscovery.value ?: return
        container.preferences.dismissedPrincipleCandidates =
            container.preferences.dismissedPrincipleCandidates + d.principle
        _principleDiscovery.value = d.copy(resolved = PrincipleResolved.NO)
        clearPendingPrinciple()  // 선택 완료 → 영속본 제거
    }

    /** 나중에 — 그냥 닫기(거절 아님, 다음에 또 보이면 물음). */
    fun laterPrinciple() {
        val d = _principleDiscovery.value ?: return
        _principleDiscovery.value = d.copy(resolved = PrincipleResolved.LATER)
        clearPendingPrinciple()  // 선택 완료(나중에도 결정) → 영속본 제거
    }

    /** resolved 메시지를 잠깐 보여준 뒤 카드 완전 해제 (카드 composable 이 호출). */
    fun clearPrincipleDiscovery() { _principleDiscovery.value = null }

    // ── 대기 중 발견 카드 영속화 (2026-06-18 사장님: 뒤로가기로 화면 나가도 ⭕/❌/나중에 고를 때까지 유지) ──
    //   항목 = 번호 끝 8자리(고정폭) + 원칙. 구분자 없이 앞 8자리=번호 키. (질문은 복원 시 카드가 템플릿으로 재생성)
    private val principlePhoneKey: String get() = phoneNumber.filter { it.isDigit() }.takeLast(8)

    /** 발견 카드 띄울 때 같이 저장 — 화면 나갔다 와도(VM 재생성) 그대로 복원. 같은 번호 기존 항목은 교체. */
    private fun persistPendingPrinciple(d: PrincipleDiscovery) {
        val key = principlePhoneKey
        if (key.length != 8) return
        val kept = container.preferences.pendingPrincipleDiscoveries.filterNot { it.take(8) == key }
        container.preferences.pendingPrincipleDiscoveries = (kept + (key + d.principle)).toSet()
    }

    /** ⭕/❌/나중에 결정되면(선택 완료) 영속본 제거 — 더는 복원 안 함. */
    private fun clearPendingPrinciple() {
        val key = principlePhoneKey
        if (key.length != 8) return
        container.preferences.pendingPrincipleDiscoveries =
            container.preferences.pendingPrincipleDiscoveries.filterNot { it.take(8) == key }.toSet()
    }

    /**
     * VM 생성 시 _principleDiscovery 초기값 — 이 번호의 미결정 발견이 있으면 복원(뒤로가기로 사라졌던 것).
     *   _principleDiscovery 를 안 만지고 값만 반환 → 선언 초기화 시점에 안전(init 순서 NPE 없음).
     *   constructor 파라미터(phoneNumber 기반 getter, container)만 읽음.
     */
    private fun computeInitialPendingPrinciple(): PrincipleDiscovery? {
        val key = principlePhoneKey
        if (key.length != 8) return null
        val entry = container.preferences.pendingPrincipleDiscoveries.firstOrNull { it.take(8) == key } ?: return null
        val principle = entry.drop(8).takeIf { it.isNotBlank() } ?: return null
        return PrincipleDiscovery(principle = principle)
    }

    /** 오늘 원칙을 더 물어도 되는지 (하루 한도). 새 날이면 리셋. */
    private fun canAskPrincipleToday(): Boolean {
        val today = com.detailline.callfollowcrm.util.DateTimeUtils.startOfDay(System.currentTimeMillis())
        val p = container.preferences
        if (p.principleAskDayStart != today) return true
        return p.principleAskCountToday < PRINCIPLE_MAX_PER_DAY
    }

    private fun markPrincipleAsked() {
        val today = com.detailline.callfollowcrm.util.DateTimeUtils.startOfDay(System.currentTimeMillis())
        val p = container.preferences
        if (p.principleAskDayStart != today) {
            p.principleAskDayStart = today
            p.principleAskCountToday = 1
        } else {
            p.principleAskCountToday = p.principleAskCountToday + 1
        }
    }

    /**
     * 2026-05-29 킬러콘텐츠 3단계 — Levenshtein 거리 (편집 거리).
     * 짧은 SMS 문장 (< 200자) 기준이라 단순 DP 충분. 길이 max 1000 cutoff 안전망.
     */
    private fun levenshtein(a: String, b: String): Int {
        val s1 = a.take(1000)
        val s2 = b.take(1000)
        if (s1.isEmpty()) return s2.length
        if (s2.isEmpty()) return s1.length
        val prev = IntArray(s2.length + 1) { it }
        val curr = IntArray(s2.length + 1)
        for (i in 1..s1.length) {
            curr[0] = i
            for (j in 1..s2.length) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                curr[j] = minOf(
                    curr[j - 1] + 1,        // insertion
                    prev[j] + 1,            // deletion
                    prev[j - 1] + cost      // substitution
                )
            }
            System.arraycopy(curr, 0, prev, 0, curr.size)
        }
        return prev[s2.length]
    }

    /**
     * 2026-05-29 킬러콘텐츠 3단계 — ChatScreen 의 chip 탭 콜백.
     * chip 의 ReplyChoice 와 현재 suggestions snapshot 저장. composer 채움은 호출자 책임.
     */
    fun onSuggestionTapped(choice: com.detailline.callfollowcrm.ai.ReplyChoice) {
        pickedChoice = choice
        pickedRefined = false
        pickedActioned = false
        pickedSuggestionsSnapshot = _suggestions.value
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
     * 백엔드 = 맥미니 공개 서버 api.si0in.kr POST /api/refine (RemoteRefineRepository).
     * aiPolishing StateFlow 가 true 동안 ChatScreen 이 로딩 표시.
     * 실패의 절대다수는 인터넷 끊김 / 서버 일시 다운 (ConnectException/UnknownHost). 사장님 입장 단순화 위해 한 메시지로 통합.
     */
    fun aiPolish(rawBody: String, onPolished: (String) -> Unit) {
        if (rawBody.isBlank()) {
            _toast.value = "다듬을 본문이 비어 있어요"
            return
        }
        if (_aiPolishing.value) return
        // 2026-05-29 킬러콘텐츠 3단계 — picked 가 set 된 상태에서 다듬기 호출 →
        //   send 시 REFINED_THEN_SENT 로 분류. picked 가 null 이면 사장님이 직접 친 거 다듬는 거라 무관.
        if (pickedChoice != null) pickedRefined = true
        _aiPolishing.value = true
        viewModelScope.launch {
            // 2026-05-28 사장님 결정: ✨ 다듬기에도 컨텍스트 전송 → "사장님 톤 + 흐름 맞춤".
            //   - recent_messages: 최근 20건 (AI chips 와 동일 규모)
            //   - owner_tone_samples: 다른 고객들에게 보낸 SMS 50건 (톤 학습 코퍼스)
            //   - customer: 이름 + 메모 (호칭 + 맥락)
            //   서버 endpoint (/api/refine, cowork 작업) 가 Gemini 2.5 Flash 로 처리.
            val history = _messages.value
                .take(20)
                .map { sms ->
                    com.detailline.callfollowcrm.ai.HistoryMessage(
                        role = if (sms.sent) "owner" else "customer",
                        body = sms.body,
                        timestampMs = sms.dateMs
                    )
                }
                .reversed()
            val tone = runCatching {
                container.smsRepository.querySentMessages(limit = 50)
            }.getOrDefault(emptyList())
            val c = customer.value
            val ctx = com.detailline.callfollowcrm.ai.RefineContext(
                recentMessages = history,
                ownerToneSamples = tone,
                customerName = c?.name,
                customerMemo = c?.memo?.takeIf { it.isNotBlank() }
            )
            val result = container.refineRepository.refine(rawBody, ctx)
            _aiPolishing.value = false
            result.fold(
                onSuccess = { polished -> onPolished(polished) },
                onFailure = { _toast.value = "AI 서버에 잠깐 연결이 안 돼요 — 인터넷 확인 후 잠시 뒤 다시 해보세요" }
            )
        }
    }

    /**
     * ChatScreen 진입 시 한 번 호출. 맥미니 캐시에서 기존 추천 답변 가져옴.
     * SMS 수신 즉시 SmsReceiver 가 prepare 트리거했으면 보통 READY 상태.
     *
     * 2026-05-26 사장님 보고 fix:
     *   사장님이 알림 polling 완료 전에 ChatScreen 진입하면 server cache 가 GENERATING →
     *   기존엔 한 번 fetch 실패 후 끝 → "↻ 눌러서 받기" placeholder.
     *   이제 polling (2초 × 5회 = 10초) 으로 자동 wait → READY 되면 즉시 채움.
     *   사장님이 ↻ 안 눌러도 됨. _suggestionsLoading=true 가 ChatScreen 의 스피너 트리거.
     */
    /** 사용자 여정 이벤트 적재(채팅 화면). 30초 배치로 서버 전송. (2026-06-23 사장님) */
    fun trackJourney(eventName: String, target: String? = null) =
        container.journeyEventRepository.track(eventName, screen = "chat", target = target)

    fun loadSuggestions() = viewModelScope.launch {
        // 통화로 끝난 대화면 답할 문자가 없음 → 추천 준비 안 함(스피너 X). (2026-06-17 사장님)
        if (lastActivityIsCall.value) return@launch
        // (A 정책) 캐시가 옛 메시지 기준이면 자동으로 새 추천 생성 — 옛것은 화면에 흐리게 남고 스르륵 교체됨. (2026-06-27)
        if (refreshIfStale()) return@launch
        container.journeyEventRepository.track("llm_use", screen = "chat", target = "suggestions")
        // 2026-05-28 사장님 통점: "알림엔 2개, ChatScreen 들어가면 3개" = LLM 점진 생성 race.
        //   첫 fetch 가 size >= 3 면 그대로. 부족하면 polling 으로 더 기다림. 알림 정책과 일관.
        val first = container.suggestionRepository.fetch(phoneNumber).getOrNull()
        if (first?.status == SuggestionStatus.READY && (first.suggestions?.suggestions?.size ?: 0) >= 3) {
            _suggestions.value = first.suggestions
            return@launch
        }
        // 부족 (0/1/2개) 또는 GENERATING / MISSING — polling 으로 3개 채워질 때까지 대기.
        _suggestionsLoading.value = true
        // 마지막 fallback: 5번 polling 후에도 3개 못 받으면 부분이라도 사용.
        var lastPartial: com.detailline.callfollowcrm.ai.ReplySuggestions? =
            first?.suggestions?.takeIf { (it.suggestions?.size ?: 0) > 0 }
        try {
            repeat(5) {
                delay(2_000)
                val fetch = container.suggestionRepository.fetch(phoneNumber).getOrNull()
                if (fetch?.status == SuggestionStatus.READY && fetch.suggestions != null) {
                    val size = fetch.suggestions.suggestions?.size ?: 0
                    if (size >= 3) {
                        _suggestions.value = fetch.suggestions
                        return@launch
                    }
                    // 부분 결과 기억 — fallback 용
                    if (size > 0) lastPartial = fetch.suggestions
                }
            }
            // 10초 안에 3개 못 받음 → 부분이라도 있으면 표시 (없는 것보단 나음).
            if (lastPartial != null) {
                _suggestions.value = lastPartial
            }
        } finally {
            _suggestionsLoading.value = false
        }
    }

    /**
     * ChatScreen 진입 시 호출 — 상단 요약 박스 + AI 제안 박스 데이터 ensure.
     * 서버 (RINGGO_SERVER_P0P1P2_UPGRADE.md) 가 구현되어야 실제 결과 옴.
     * 미구현 시 silent — 박스 안 보임. 기존 캐시 있으면 그대로 표시.
     */
    fun loadFullSummary() = viewModelScope.launch(Dispatchers.IO) {
        val digits = phoneNumber.filter { it.isDigit() }
        if (digits.length < 7) return@launch
        val suffix = digits.takeLast(8)

        val msgs = runCatching { container.cachedMessageRepository.load(suffix, limit = 20) }
            .getOrDefault(emptyList())
        if (msgs.isEmpty()) return@launch

        _isSummaryRefreshing.value = true
        _summaryFailed.value = false   // 새 시도 시작 — 실패 표시 초기화
        try {
        val latestTs = msgs.maxOf { it.dateMs }
        val c = runCatching { container.customerRepository.findByPhone(phoneNumber) }.getOrNull()
        val tone = runCatching { container.smsRepository.querySentMessages(limit = 50) }.getOrDefault(emptyList())

        // P3 — 다른 시공 일정 (현재 고객 제외, 14일 내). 일정 답변 + AI 제안 근거.
        val otherSchedules = runCatching {
            container.customerRepository.getOtherUpcomingScheduleDates(phoneNumber)
        }.getOrDefault(emptyList())

        val ctx = com.detailline.callfollowcrm.ai.SummaryContext(
            phone = phoneNumber,
            phoneSuffix = suffix,
            customerName = c?.name,
            customerMemo = c?.memo?.takeIf { it.isNotBlank() },
            leadHeat = c?.leadHeat,
            depositPaid = (c?.depositAmount ?: 0L) > 0L,
            scheduledWorkDate = c?.scheduledWorkDate,
            recentMessages = msgs.map { sms ->
                HistoryMessage(
                    role = if (sms.sent) "owner" else "customer",
                    body = sms.body,
                    timestampMs = sms.dateMs
                )
            }.reversed(),
            ownerToneSamples = tone,
            otherUpcomingSchedulesMs = otherSchedules,
            latestMessageTimestampMs = latestTs
        )
        // 결과가 실패면(또는 row 가 끝내 안 써져 aiSummary 가 계속 null 이면) 실패 표시 —
        //   안 그러면 "✨ 대화 요약 작성 중…" shimmer 가 영원히 돈다(추천 영역 _suggestionsFailed 와 동일 처리).
        val summaryResult = runCatching {
            container.conversationAiRepository.ensureFullSummary(ctx).getOrThrow()
        }
        if (summaryResult.isFailure && aiSummary.value == null) {
            _summaryFailed.value = true
        }

        // 갤메시지 식 카테고리 자동 분류 (휴리스틱 1차) — 사장님이 이미 분류한 고객은 보존.
        //   서버 endpoint 정식 도입 전 임시 substring 매칭. 점수 0 이면 미분류 유지.
        if (c != null && c.categoryId == null) {
            val cats = runCatching {
                container.categoryRepository.observeAll().first()
            }.getOrDefault(emptyList())
            if (cats.isNotEmpty()) {
                // 2026-06-07 사장님 통점: "신규 고객이 '일당'으로 분류됨".
                //   원인 = 사장님 발신 서명("직영팀만 시공 (외주/일당 절대 X)")까지 분류 본문에 들어가 "일당" 카테고리에 매칭.
                //   해결: 분류는 고객이 보낸 말(수신, !sent)만 본다. 사장님 보낸 정형문구/서명은 분류 신호에서 제외.
                val text = msgs.filter { !it.sent }.joinToString(" ") { it.body }
                // 2026-06-07 사장님 통점: "상담만 해도 시공대기로 분류됨".
                //   원인 = 키워드 분류기가 "시공 대기" 를 ["시공","대기"] 로 쪼개 → 상담문 "시공 문의…" 의 "시공" 에 매칭.
                //   해결: 상태 카테고리("시공 대기"/"시공 완료")는 키워드 분류 대상에서 제외(이건 날짜·입금으로만 자동).
                val systemNames = setOf(
                    com.detailline.callfollowcrm.data.local.seed.DefaultCategories.NAME_PENDING_WORK,
                    com.detailline.callfollowcrm.data.local.seed.DefaultCategories.NAME_DONE_WORK
                )
                val matched = com.detailline.callfollowcrm.category.CategoryAutoClassifier
                    .classify(text, cats.filter { it.name !in systemNames })
                if (matched != null) {
                    runCatching {
                        container.categoryRepository.assignCustomer(c.id, matched.id)
                    }
                }
            }
        }
        } finally {
            _isSummaryRefreshing.value = false
        }
    }

    /**
     * ↻ 재생성. 사장님이 명시적으로 누름.
     * 흐름: PrepareContext 구성 → POST /prepare-reply → 2초 간격 폴링 (최대 10초) → READY 면 표시.
     * 컨텍스트 구성은 SmsReceiver 와 같은 로직 (최근 20건 + customer hint).
     */
    fun regenerateSuggestions(auto: Boolean = false) {
        // 통화로 끝난 대화 — 답할 문자가 없으니 준비하지 않고 안내만. (2026-06-17 사장님)
        if (lastActivityIsCall.value) {
            if (!auto) _toast.value = "통화로 끝난 대화예요 — 고객이 문자를 보내면 추천 답변을 준비할게요"
            return
        }
        val latestReceived = _messages.value.firstOrNull { !it.sent } ?: run {
            if (!auto) _toast.value = "고객 마지막 메시지가 없어요"
            return
        }
        if (_suggestionsLoading.value) return
        _suggestionsLoading.value = true
        _suggestionsFailed.value = false   // 새 시도 시작 — 실패 표시 초기화
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
                        depositPaid = (it.depositAmount ?: 0L) > 0L,
                        scheduledWorkDateMs = it.scheduledWorkDate
                    )
                }
                val ownerToneSamples = withContext(Dispatchers.IO) {
                    runCatching {
                        container.smsRepository.querySentMessages(limit = 50)
                    }.getOrDefault(emptyList())
                }
                // P3 — 다른 시공 일정. 일정 질문 답변 근거.
                val otherSchedules = withContext(Dispatchers.IO) {
                    runCatching {
                        container.customerRepository.getOtherUpcomingScheduleDates(phoneNumber)
                    }.getOrDefault(emptyList())
                }
                // 2026-05-30 사장님 #2 통점 fix: 마지막 사장님 발신 이후 모든 고객 메시지 묶음.
                //   regenerate 케이스라 newIncomingBody = null (history 가 이미 모두 포함).
                val mergedLatest = com.detailline.callfollowcrm.ai.PrepareContextHelpers
                    .joinCustomerStreakAfterLastOwner(history, newIncomingBody = null)
                val ctx = PrepareContext(
                    phone = phoneNumber,
                    latestMessage = mergedLatest.ifBlank { latestReceived.body },
                    latestMessageReceivedAtMs = latestReceived.dateMs,
                    recentHistory = history,
                    customer = hint,
                    ownerToneSamples = ownerToneSamples,
                    otherUpcomingSchedulesMs = otherSchedules,
                    deviceId = container.preferences.deviceId,
                    ownerTrade = container.preferences.ownerTrades.firstOrNull(),
                    priceList = withContext(Dispatchers.IO) {
                        runCatching { container.pricingItemRepository.priceListText() }.getOrDefault("")
                    }.takeIf { it.isNotBlank() },
                    principles = withContext(Dispatchers.IO) {
                        runCatching { container.principleRepository.enabledTexts() }.getOrDefault(emptyList())
                    }.takeIf { it.isNotEmpty() }
                )
                // 새로고침은 손해 X (2026-06-29 사장님): 기존에 잘 나온 답변은 절대 안 지움.
                //   실패/시간초과여도 기존 답변 유지 → 실패 플래그/에러토스트는 "보여줄 게 아무것도 없을 때"만.
                val hadSuggestions = _suggestions.value?.suggestions?.isNotEmpty() == true

                val prep = container.suggestionRepository.requestPrepare(ctx)
                if (prep.isFailure) {
                    if (hadSuggestions) {
                        // 기존 답변 그대로 두고 조용히 안내만 (수동일 때만). 실패 플래그 X = 칩 안 흐려지고 에러 안 뜸.
                        if (!auto) _toast.value = "연결이 잠깐 안 돼요 — 기존 답변은 그대로 둘게요"
                    } else {
                        _suggestionsFailed.value = true   // 보여줄 게 없을 때만 "못 만들었어요"
                        if (!auto) _toast.value = "AI 서버에 잠깐 연결이 안 돼요 — 인터넷 확인 후 잠시 뒤 다시 해보세요"
                    }
                    return@launch
                }
                // 2초 간격 폴링 — 최대 10회 (20초). LLM(gemini/sonnet) 생성이 10초 넘기 쉬워 창을 넓힘. (2026-06-29)
                repeat(10) {
                    delay(2_000)
                    val fetch = container.suggestionRepository.fetch(phoneNumber).getOrNull()
                    if (fetch?.status == SuggestionStatus.READY && fetch.suggestions != null) {
                        _suggestions.value = fetch.suggestions   // 준비되면 살짝 바꿔치기
                        _suggestionsFailed.value = false
                        return@launch
                    }
                }
                // 시간 초과: 기존 답변 있으면 그대로 유지(무서운 에러 X), 없을 때만 실패 표시.
                if (hadSuggestions) {
                    if (!auto) _toast.value = "아직 만드는 중이에요 — 기존 답변은 그대로 둘게요. 잠시 후 ↻ 다시"
                } else {
                    _suggestionsFailed.value = true
                    if (!auto) _toast.value = "추천 답변 생성 시간 초과"
                }
            } finally {
                _suggestionsLoading.value = false
            }
        }
    }

    /**
     * 견적 작성/공유 시점 기록 (2026-06-01) — 견적 회신 리마인드의 기준 시각.
     *   Customer 보장 후 MessageHistory 에 ESTIMATE_SENT 마커. 발송 여부와 무관(준비=강한 의도).
     */
    fun recordEstimateSent(body: String) = viewModelScope.launch {
        val cid = ensureCustomerId()
        withContext(Dispatchers.IO + NonCancellable) {
            runCatching { container.messageHistoryRepository.recordEstimateSent(phoneNumber, cid, body) }
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

    /**
     * 시공접수서 링크 발급 (맥미니 §19). 성공 시 SMS 본문(안내 + URL) 반환, 실패 시 null.
     *   자동발송 X — 호출부가 composer 에 prefill → 사장님이 ▶ 직접 발송.
     */
    suspend fun issueIntakeForm(): String? {
        val name = customer.value?.name?.takeIf { it.isNotBlank() }
        val issued = container.intakeFormRepository.issue(
            phone = phoneNumber,
            customerName = name
        ).getOrNull() ?: return null
        return "아래 링크에서 주소·시공 범위를 확인 부탁드려요 😊\n${issued.url}"
    }

    /**
     * 시공접수서 발급 v2 (맥미니 §19.2, 2026-06-03) — 견적 시트 [시공접수서 링크 보내기].
     *   견적 데이터 + 사업자정보(AppPreferences) → POST /api/quote/issue → smsDraft 반환.
     *   자동발송 X — 호출부가 composer 에 prefill → 사장님이 ▶.
     */
    fun issueQuoteIntake(
        items: List<com.detailline.callfollowcrm.ai.IntakeFormRepository.QuoteIssueItem>,
        total: Int, workYear: Int, workMonth: Int, workDay: Int, workDays: Int,
        depositMode: String, depositValue: Int,
        ownerMemo: String,
        vatIncluded: Boolean,
        onResult: (Result<String>) -> Unit
    ) = viewModelScope.launch {
        val prefs = container.preferences
        val name = customer.value?.name?.takeIf { it.isNotBlank() } ?: phoneNumber
        // 서버 /api/quote/issue 계약: depositValue 는 fixed=원, ratio=%. 앱 depVal 은 만원 단위라
        //   fixed 는 원으로 환산해 보낸다. 안 하면 서버가 계약금 10(만원)을 '10원'으로 렌더 → 고객 접수서 오표기. (2026-07-04 사장님 보고)
        val depositValueForServer = if (depositMode == "fixed") depositValue * 10000 else depositValue
        val res = container.intakeFormRepository.issueQuote(
            customerName = name, customerPhone = phoneNumber,
            items = items, total = total,
            workYear = workYear, workMonth = workMonth, workDay = workDay, workDays = workDays,
            depositMode = depositMode, depositValue = depositValueForServer,
            bizName = prefs.bizName, bizOwner = prefs.bizOwner, bizNo = prefs.bizNo,
            bizAddr = prefs.bizAddr, bizPhone = prefs.displayPhone, bizSeal = prefs.bizSeal,   // 접수서 표시=대표번호 (2026-07-04)
            bizValidDays = prefs.bizQuoteValidDays,
            devicePhone = prefs.bizPhone, deviceId = prefs.deviceId,
            ownerMemo = ownerMemo, vatIncluded = vatIncluded
        )
        onResult(res.map { it.smsDraft.ifBlank { "시공접수서 링크를 보냈어요." } })
    }

    /**
     * AI 제안 박스의 [시공일 등록] 액션 (action_type=register_schedule) hookup.
     * Customer 없으면 upsert 후 등록. 다음 캐시 갱신 때 nextActionJson 도 다음 단계로 자동 전환됨.
     */
    fun setScheduledWorkDate(timestampMs: Long) = viewModelScope.launch {
        val id = ensureCustomerId()
        withContext(Dispatchers.IO + NonCancellable) {
            runCatching {
                container.customerRepository.updateScheduledWorkDate(id, timestampMs)
            }
        }
        // 캘린더 등록 KPI — 채팅 AI제안 [시공일 등록]도 한 건으로 집계. (2026-06-25 cowork 요청)
        container.journeyEventRepository.track(
            "schedule_create", screen = "chat", target = "…" + phoneNumber.filter { it.isDigit() }.takeLast(4)
        )
        _toast.value = "시공일을 등록했어요"
    }

    /** 시공 시간(자정부터 분, null=미정) 설정 — 시공일 등록 후 시간 칩에서 호출. (2026-06-23 사장님) */
    fun setScheduledWorkMinutes(minutes: Int?) = viewModelScope.launch {
        val id = ensureCustomerId()
        withContext(Dispatchers.IO + NonCancellable) {
            runCatching { container.customerRepository.updateScheduledWorkMinutes(id, minutes) }
        }
    }

    /**
     * 2026-05-29 킬러콘텐츠 3단계 — 화면 떠날 때 DISMISSED 시그널.
     *
     * pickedChoice 있고 사장님이 send 안 하고 떠남 → 그 답변 가치 없다고 판단했다는 시그널.
     * viewModelScope 가 cancel 된 시점이라 applicationScope 로 비동기 박기.
     */
    override fun onCleared() {
        super.onCleared()
        val picked = pickedChoice
        if (picked != null && !pickedActioned) {
            val sugs = pickedSuggestionsSnapshot ?: _suggestions.value
            val phoneSuffix = phoneNumber.filter { it.isDigit() }.takeLast(8)
            val event = com.detailline.callfollowcrm.data.local.entity.SuggestionEventEntity(
                phoneSuffix = phoneSuffix,
                scenario = sugs?.scenario,
                scenarioConfidence = sugs?.scenarioConfidence,
                intentKey = picked.intentKey,
                intentLabel = picked.label,
                suggestionText = picked.text,
                action = com.detailline.callfollowcrm.data.local.entity.SuggestionEventAction.DISMISSED,
                finalSentText = null,
                editDistance = null,
                createdAtMs = System.currentTimeMillis()
            )
            container.applicationScope.launch {
                runCatching { container.suggestionEventRepository.record(event) }
            }
        }
    }

    companion object {
        /** 원칙 추론 게이트 — 추천 확신(scenario_confidence)이 이 이상일 때만. 사례 적으면 자연히 안 물음. */
        private const val PRINCIPLE_MIN_CONFIDENCE = 0.6
        /** 추천과 사장님 답의 편집거리가 이 미만이면 "거의 같음"으로 보고 안 물음. */
        private const val PRINCIPLE_MIN_EDIT_DISTANCE = 12
        /** 하루 최대 원칙 묻기 횟수 — 귀찮게 안 하기. */
        private const val PRINCIPLE_MAX_PER_DAY = 2

        /**
         * 화면 대기 중 재조회(fullScan=false)의 MMS 얕은 스캔 상한. 방금 보낸/받은 MMS 는 전역에서도
         *   최근이라 이 안에 들어옴. 전체 2000 스캔(행마다 주소조회)을 피해 즉시 표시. (2026-07-02 사장님)
         */
        private const val RECENT_MMS_SCAN_LIMIT = 80
    }
}

/** (Phase 2) 발견 카드 상태. resolved=null 이면 질문 중, 아니면 결과 메시지 잠깐 보여준 뒤 자동 해제. */
data class PrincipleDiscovery(
    val principle: String,
    val question: String? = null,
    val resolved: PrincipleResolved? = null
)

enum class PrincipleResolved { OK, NO, LATER }
