package com.detailline.callfollowcrm.presentation.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.detailline.callfollowcrm.data.AppContainer
import com.detailline.callfollowcrm.data.local.entity.MessageTemplateEntity
import com.detailline.callfollowcrm.data.local.entity.CustomerEntity
import com.detailline.callfollowcrm.util.DateTimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
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

    /** 서버 §21 내 말투 프로필 (학습률·특징·before/after). null=아직 안 불러옴. tone 화면 진입 시 1회 로드. */
    private val _toneProfile = MutableStateFlow<com.detailline.callfollowcrm.ai.ToneProfile?>(null)
    val toneProfile: StateFlow<com.detailline.callfollowcrm.ai.ToneProfile?> = _toneProfile.asStateFlow()
    private var toneProfileLoaded = false

    fun loadToneProfile() {
        if (toneProfileLoaded) return
        toneProfileLoaded = true
        viewModelScope.launch {
            container.phaseOneApiRepository.fetchToneProfile(container.preferences.deviceId)
                .onSuccess { _toneProfile.value = it }
                .onFailure { toneProfileLoaded = false }  // 실패 시 재시도 허용
        }
    }

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
     * "지금 동기화"(말투 업로드) 결과 안내 — 화면에서 토스트로 띄우고 consume.
     *   조용한 실패 제거(2026-06-30): 예전엔 onFailure 가 비어 있어 눌러도 아무 반응 없었음("변동 없음"의 정체).
     */
    private val _toneSyncMessage = MutableStateFlow<String?>(null)
    val toneSyncMessage: StateFlow<String?> = _toneSyncMessage.asStateFlow()
    fun consumeToneSyncMessage() { _toneSyncMessage.value = null }

    /** 마지막 동기화 때 폰에 있던 보낸문자 개수 — "대기"를 '그 이후 새 문자'로 계산하기 위함. (2026-06-30) */
    private val _toneSyncedUpTo = MutableStateFlow(container.preferences.toneSyncedUpToAvailable)
    val toneSyncedUpTo: StateFlow<Int> = _toneSyncedUpTo.asStateFlow()

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

    /**
     * cowork §16 — bge-m3 + sqlite-vec install 여부. false 면 사장님 안내 표시
     * ("Mac mini 에 pip install 필요"). null = 한 번도 upload 안 함.
     */
    private val _toneRagEmbeddingsAvailable = MutableStateFlow<Boolean?>(null)
    val toneRagEmbeddingsAvailable: StateFlow<Boolean?> = _toneRagEmbeddingsAvailable.asStateFlow()

    /**
     * 더보기 상단 "막내 비서" 카드 (프로토 agent-card). 전부 실제 카운트 기반:
     *   - 함께한 상담 = 전체 고객 수, 시공 완료 = 지난 시공일 가진 고객 수.
     *   - 말투 % = 사장님 SMS 업로드(RAG) 진행 (목표 500건 = 100%). 게이미피케이션 임계값.
     *   - 레벨 = 상담 건수 구간. 다음 레벨까지 = 다음 구간 경계까지.
     */
    val agentCard: StateFlow<AgentCardState> =
        combine(container.customerRepository.observeAll(), _toneRagUploadedCount, _toneRagAvailable) { customers, toneUploaded, sentCount ->
            buildAgentCard(customers, toneUploaded, sentCount)
        }.onEach {
            // 단계 변경 시 전역 막내 변신 상태 + prefs 갱신(앱 곳곳 Mascot 반영).
            com.detailline.callfollowcrm.presentation.component.MascotTierState.set(it.tier)
            container.preferences.agentTier = it.tier
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AgentCardState())

    private fun buildAgentCard(customers: List<CustomerEntity>, toneUploaded: Int, sentCount: Int): AgentCardState {
        val todayStart = DateTimeUtils.startOfDay(System.currentTimeMillis())
        val consult = customers.size
        val doneJobs = customers.count { it.scheduledWorkDate?.let { d -> d < todayStart } == true }
        val tonePct = ((toneUploaded * 100.0) / TONE_TARGET).toInt().coerceIn(0, 100)

        // 경험치 = 핑퐁(고객에게 보낸 문자 수) + 계약 성사 보너스. 레벨/칭호/변신 단계 산출.
        val contracted = customers.count { it.scheduledWorkDate != null }
        val xp = sentCount + contracted * CONTRACT_BONUS_XP
        val level = levelForXp(xp)
        val tier = ((level - 1) / 10).coerceIn(0, AGENT_TITLES.lastIndex)
        val title = AGENT_TITLES[tier]
        val toNext = if (level < MAX_LEVEL) (xpForLevel(level + 1) - xp).coerceAtLeast(0) else 0

        val line = when {
            tonePct >= 80 -> "사장님 말투, 이제 거의 다 외웠어요!"
            tonePct >= 40 -> "사장님 말투를 부지런히 배우고 있어요"
            tonePct >= 1 -> "사장님 말투를 막 배우기 시작했어요"
            else -> "문자를 더 주고받으면 제가 말투를 배워요"
        }
        return AgentCardState(
            level = level, title = title, line = line,
            tonePct = tonePct, consultCount = consult, doneJobs = doneJobs,
            toNextLevel = toNext, xp = xp, tier = tier
        )
    }

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
                if (messages.isEmpty()) {
                    _toneSyncMessage.value = "올릴 보낸 문자가 아직 없어요."
                    return@launch
                }
                val before = _toneRagUploadedCount.value
                val deviceId = container.preferences.deviceId  // 폰별 분리 (2026-06-17)
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
                        _toneRagEmbeddingsAvailable.value = ok.embeddingsAvailable
                        // 방금 올린 available 수를 "동기화 완료선"으로 기록 → "대기"는 이후 새 문자만(유령갭 닫힘).
                        container.preferences.toneSyncedUpToAvailable = messages.size
                        _toneSyncedUpTo.value = messages.size
                        // 성공 피드백 — 늘었으면 "학습", 그대로면 "이미 최신"(서버가 빈/중복 문자를 걸러 안 늘 수 있음).
                        _toneSyncMessage.value =
                            if (totalCount > before) "✓ 최신 말투까지 배웠어요 (총 ${totalCount}건)"
                            else "이미 최신 상태예요 (총 ${totalCount}건)"
                    },
                    onFailure = {
                        // 조용한 실패 제거(2026-06-30) — 예전엔 여기가 비어 있어 눌러도 아무 반응 없었음.
                        _toneSyncMessage.value = "동기화를 못 했어요 — 인터넷·서버 연결을 확인하고 잠시 후 다시 시도해주세요."
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

/** 더보기 막내 비서 카드 (프로토 agent-card). */
data class AgentCardState(
    val level: Int = 1,
    val title: String = "새내기",
    val line: String = "문자를 더 주고받으면 제가 말투를 배워요",
    val tonePct: Int = 0,
    val consultCount: Int = 0,
    val doneJobs: Int = 0,
    val toNextLevel: Int = 10,  // 다음 레벨까지 남은 XP
    val xp: Int = 0,
    val tier: Int = 0           // 0~9 (10레벨마다 변신)
)

private const val TONE_TARGET = 500

// ── 막내 레벨 = 경험치 기반 (2026-06-14 사장님 재설계) ──
//   XP = 핑퐁(고객에게 보낸 문자 = 막내가 배우는 코퍼스) + 계약 성사 보너스. 단순 견적문의(핑퐁 적음)는 XP 적게.
private const val MAX_LEVEL = 100
private const val CONTRACT_BONUS_XP = 30
/** 레벨 L 도달 누적 XP = 2*(L-1)*L. 초반 빠르고 뒤로 갈수록 천천히. L2=4, L10=180, L50=4900, L100=19800. */
private fun xpForLevel(level: Int): Int = 2 * (level - 1) * level
private fun levelForXp(xp: Int): Int {
    var lv = 1
    while (lv < MAX_LEVEL && xpForLevel(lv + 1) <= xp) lv++
    return lv
}
/** 10단계 칭호 — 10레벨 구간마다 하나(캐릭터 변신과 동일 구간). */
private val AGENT_TITLES = listOf(
    "새내기", "수습", "일잘러", "베테랑", "에이스", "능력자", "달인", "고수", "마스터", "레전드"
)

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
