package com.detailline.callfollowcrm.presentation.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.detailline.callfollowcrm.data.AppContainer
import com.detailline.callfollowcrm.data.local.entity.MessageTemplateEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    // v1: 설정은 영속화 단순. 토글 상태는 AppPreferences 에서 즉시 읽고 쓰기.
    private val _state = MutableStateFlow(
        SettingsUiState(
            afterCallBehavior = AfterCallBehavior.NOTIFY,
            autoFirstReplyEnabled = container.preferences.autoFirstReplyEnabled,
            firstReplyIncomingTemplateId = container.preferences.firstReplyIncomingTemplateId,
            firstReplyMissedTemplateId = container.preferences.firstReplyMissedTemplateId,
            quickActionTemplateId1 = container.preferences.quickActionTemplateId1,
            quickActionTemplateId2 = container.preferences.quickActionTemplateId2,
            quickActionTemplateId3 = container.preferences.quickActionTemplateId3,
            incomingSmsNotifyEnabled = container.preferences.incomingSmsNotifyEnabled,
            defaultNavAppKey = container.preferences.defaultNavAppKey
        )
    )
    val state = _state.asStateFlow()

    /** 자동응답 템플릿 드롭다운에 보여줄 목록 (활성 템플릿만). */
    val templates = container.messageTemplateRepository.observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList<MessageTemplateEntity>())

    /** AI 서버 살아있음 표시 (●). null=아직 모름, true=정상, false=죽음. */
    val serverAlive: StateFlow<Boolean?> = container.serverHealth.alive

    /** 사장님 톤 학습용 보낸 SMS 샘플 개수. 설정 진입 시 한 번 계산. */
    private val _ownerToneSampleCount = MutableStateFlow(0)
    val ownerToneSampleCount: StateFlow<Int> = _ownerToneSampleCount.asStateFlow()

    /**
     * 토큰 사용량 통계 (2026-05-27). 서버 §12 endpoint 결과.
     *   null = 아직 fetch 안 함 / Result.failure = 서버 미구현/오류
     *   사장님이 새로고침 버튼 누르면 다시 fetch.
     */
    private val _usageStats = MutableStateFlow<Result<com.detailline.callfollowcrm.ai.UsageStatsRepository.UsageStats>?>(null)
    val usageStats: StateFlow<Result<com.detailline.callfollowcrm.ai.UsageStatsRepository.UsageStats>?> = _usageStats.asStateFlow()

    private val _usageLoading = MutableStateFlow(false)
    val usageLoading: StateFlow<Boolean> = _usageLoading.asStateFlow()

    /**
     * 2026-05-29 킬러콘텐츠 3단계 후속 — 추천 답변 채택률 통계.
     * suggestion_events 테이블 (DB v17) 에서 기간별 집계.
     * null = 아직 load 안 함, total=0 = 데이터 없음 (사용자가 chip 한 번도 안 봤거나 아직 안 보냄).
     */
    private val _suggestionStats = MutableStateFlow<com.detailline.callfollowcrm.data.repository.SuggestionEventRepository.Stats?>(null)
    val suggestionStats: StateFlow<com.detailline.callfollowcrm.data.repository.SuggestionEventRepository.Stats?> = _suggestionStats.asStateFlow()

    /** 기간 = 이번 주 (default). 한국 시각 자정 기준은 다음 sprint, 일단 millis 7일. */
    private val _suggestionStatsPeriodDays = MutableStateFlow(7)
    val suggestionStatsPeriodDays: StateFlow<Int> = _suggestionStatsPeriodDays.asStateFlow()

    // 2026-05-29 킬러콘텐츠 6단계 — 자동 학습 루프.
    private val _scenarioBreakdown = MutableStateFlow<List<com.detailline.callfollowcrm.data.repository.SuggestionEventRepository.ScenarioBreakdown>>(emptyList())
    val scenarioBreakdown: StateFlow<List<com.detailline.callfollowcrm.data.repository.SuggestionEventRepository.ScenarioBreakdown>> = _scenarioBreakdown.asStateFlow()

    private val _intentBreakdown = MutableStateFlow<List<com.detailline.callfollowcrm.data.repository.SuggestionEventRepository.IntentBreakdown>>(emptyList())
    val intentBreakdown: StateFlow<List<com.detailline.callfollowcrm.data.repository.SuggestionEventRepository.IntentBreakdown>> = _intentBreakdown.asStateFlow()

    // 2026-05-29 킬러콘텐츠 4단계 — Tone RAG upload 상태.
    private val _toneRagUploading = MutableStateFlow(false)
    val toneRagUploading: StateFlow<Boolean> = _toneRagUploading.asStateFlow()

    private val _toneRagProgress = MutableStateFlow<Pair<Int, Int>?>(null)  // sent / total
    val toneRagProgress: StateFlow<Pair<Int, Int>?> = _toneRagProgress.asStateFlow()

    private val _toneRagAvailable = MutableStateFlow(0)  // 폰에 있는 sent SMS 개수 (학습 가능 대상)
    val toneRagAvailable: StateFlow<Int> = _toneRagAvailable.asStateFlow()

    // pref-mirrored (구독자 가 read 가능하게 StateFlow 로 래핑)
    private val _toneRagConsented = MutableStateFlow(container.preferences.toneUploadConsented)
    val toneRagConsented: StateFlow<Boolean> = _toneRagConsented.asStateFlow()

    private val _toneRagUploadedCount = MutableStateFlow(container.preferences.toneTotalUploadedCount)
    val toneRagUploadedCount: StateFlow<Int> = _toneRagUploadedCount.asStateFlow()

    private val _toneRagLastUploadedAt = MutableStateFlow(container.preferences.toneLastUploadedAtMs)
    val toneRagLastUploadedAt: StateFlow<Long> = _toneRagLastUploadedAt.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val n = runCatching {
                container.smsRepository.querySentMessages(limit = 50).size
            }.getOrDefault(0)
            _ownerToneSampleCount.value = n
        }
        // 설정 진입 시 자동으로 한 번 fetch — 사장님이 토큰 상황 즉시 확인.
        loadUsageStats(com.detailline.callfollowcrm.ai.UsageStatsRepository.Period.TODAY)
        // 추천 답변 채택률 — 이번 주 default.
        loadSuggestionStats(days = 7)
        // Tone RAG 가용 메시지 수 카운트 (폰에 있는 sent SMS — 학습 후보 풀).
        viewModelScope.launch(Dispatchers.IO) {
            val available = runCatching {
                container.smsRepository.querySentMessagesWithTimestamp(limit = 50000).size
            }.getOrDefault(0)
            _toneRagAvailable.value = available
        }
    }

    /**
     * 2026-05-29 킬러콘텐츠 4단계 — 사장님 sent SMS 풀 batch upload.
     *
     * consent=true 이면 동의 + upload, false 면 동의 없이 upload (이미 동의된 상태에서 재동기화).
     * 성공 시 prefs 에 lastUploaded / totalCount 박음 (서버 응답 기준).
     */
    fun uploadOwnerTone(consentNow: Boolean) {
        if (_toneRagUploading.value) return
        viewModelScope.launch {
            _toneRagUploading.value = true
            _toneRagProgress.value = 0 to 0
            try {
                if (consentNow) {
                    container.preferences.toneUploadConsented = true
                    _toneRagConsented.value = true
                }
                // 폰의 sent SMS 풀 가져오기 (최대 50000건 — 그 이상은 의미 없음).
                val messages = withContext(Dispatchers.IO) {
                    runCatching {
                        container.smsRepository.querySentMessagesWithTimestamp(limit = 50000)
                            .map {
                                com.detailline.callfollowcrm.ai.OwnerToneUploadRepository.TimestampedText(
                                    text = it.body,
                                    timestampMs = it.dateMs
                                )
                            }
                    }.getOrDefault(emptyList())
                }
                if (messages.isEmpty()) return@launch
                val deviceId = "owner-anon"  // multi-device 대응은 다음 sprint
                val result = container.ownerToneUploadRepository.batchUpload(
                    deviceId = deviceId,
                    messages = messages,
                    chunkSize = 500,
                    onProgress = { sent, total ->
                        _toneRagProgress.value = sent to total
                    }
                )
                result.fold(
                    onSuccess = { ok ->
                        val totalCount = if (ok.totalInPool > 0) ok.totalInPool else ok.stored
                        container.preferences.toneTotalUploadedCount = totalCount
                        container.preferences.toneLastUploadedAtMs = System.currentTimeMillis()
                        _toneRagUploadedCount.value = totalCount
                        _toneRagLastUploadedAt.value = container.preferences.toneLastUploadedAtMs
                    },
                    onFailure = {
                        // 사장님은 토스트로 안내 — UI 가 SettingsScreen 의 LocalContext 라 별도 트리거.
                        // 단순화 — 사장님이 다음번에 재시도. 진행 상태만 reset.
                    }
                )
            } finally {
                _toneRagUploading.value = false
                _toneRagProgress.value = null
            }
        }
    }

    /**
     * 추천 답변 채택률 통계 load. days=1 (오늘) / 7 (이번 주) / 30 (이번 달) 등.
     */
    fun loadSuggestionStats(days: Int) {
        _suggestionStatsPeriodDays.value = days
        viewModelScope.launch(Dispatchers.IO) {
            val sinceMs = System.currentTimeMillis() - days * 24L * 60 * 60 * 1000
            _suggestionStats.value = runCatching {
                container.suggestionEventRepository.statsSince(sinceMs)
            }.getOrNull()
            _scenarioBreakdown.value = runCatching {
                container.suggestionEventRepository.scenarioBreakdown(sinceMs)
            }.getOrDefault(emptyList())
            _intentBreakdown.value = runCatching {
                container.suggestionEventRepository.intentBreakdown(sinceMs)
            }.getOrDefault(emptyList())
        }
    }

    fun loadUsageStats(period: com.detailline.callfollowcrm.ai.UsageStatsRepository.Period) {
        if (_usageLoading.value) return
        viewModelScope.launch(Dispatchers.IO) {
            _usageLoading.value = true
            _usageStats.value = container.usageStatsRepository.fetch(period)
            _usageLoading.value = false
        }
    }

    fun setBehavior(b: AfterCallBehavior) { _state.value = _state.value.copy(afterCallBehavior = b) }

    fun setAutoFirstReplyEnabled(enabled: Boolean) {
        container.preferences.autoFirstReplyEnabled = enabled
        _state.value = _state.value.copy(autoFirstReplyEnabled = enabled)
    }

    fun setIncomingTemplate(id: Long) {
        container.preferences.firstReplyIncomingTemplateId = id
        _state.value = _state.value.copy(firstReplyIncomingTemplateId = id)
    }

    fun setMissedTemplate(id: Long) {
        container.preferences.firstReplyMissedTemplateId = id
        _state.value = _state.value.copy(firstReplyMissedTemplateId = id)
    }

    fun setIncomingSmsNotifyEnabled(enabled: Boolean) {
        container.preferences.incomingSmsNotifyEnabled = enabled
        _state.value = _state.value.copy(incomingSmsNotifyEnabled = enabled)
    }

    /**
     * 카드 펼침 [📍 길찾기] 가 사용할 외부 네비 앱.
     * 사장님이 처음 길찾기 탭할 때 자동 다이얼로그 → 여기 저장 → 다음부터 1탭.
     * key 는 com.detailline.callfollowcrm.util.NavApp.key 문자열.
     */
    fun setDefaultNavApp(key: String?) {
        container.preferences.defaultNavAppKey = key
        _state.value = _state.value.copy(defaultNavAppKey = key)
    }

    fun setQuickAction(slot: Int, id: Long) {
        when (slot) {
            1 -> { container.preferences.quickActionTemplateId1 = id
                   _state.value = _state.value.copy(quickActionTemplateId1 = id) }
            2 -> { container.preferences.quickActionTemplateId2 = id
                   _state.value = _state.value.copy(quickActionTemplateId2 = id) }
            3 -> { container.preferences.quickActionTemplateId3 = id
                   _state.value = _state.value.copy(quickActionTemplateId3 = id) }
        }
    }
}

data class SettingsUiState(
    val afterCallBehavior: AfterCallBehavior,
    val autoFirstReplyEnabled: Boolean = false,
    val firstReplyIncomingTemplateId: Long = -1L,
    val firstReplyMissedTemplateId: Long = -1L,
    val quickActionTemplateId1: Long = -1L,
    val quickActionTemplateId2: Long = -1L,
    val quickActionTemplateId3: Long = -1L,
    val incomingSmsNotifyEnabled: Boolean = true,
    /** NavApp.key 문자열. null = 미선택 (첫 길찾기 탭 시 다이얼로그). */
    val defaultNavAppKey: String? = null
)

enum class AfterCallBehavior(val label: String) {
    NOTIFY("알림 표시"),
    NONE("아무것도 안 함")
}
