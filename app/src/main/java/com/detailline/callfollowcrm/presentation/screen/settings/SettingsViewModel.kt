package com.detailline.callfollowcrm.presentation.screen.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.detailline.callfollowcrm.data.AppContainer
import com.detailline.callfollowcrm.data.local.entity.MessageTemplateEntity
import com.detailline.callfollowcrm.data.local.entity.CustomerEntity
import com.detailline.callfollowcrm.util.DataBackup
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
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AgentCardState())

    private fun buildAgentCard(customers: List<CustomerEntity>, toneUploaded: Int, sentCount: Int): AgentCardState {
        val todayStart = DateTimeUtils.startOfDay(System.currentTimeMillis())
        val consult = customers.size
        val doneJobs = customers.count { it.scheduledWorkDate?.let { d -> d < todayStart } == true }
        val tonePct = ((toneUploaded * 100.0) / TONE_TARGET).toInt().coerceIn(0, 100)

        // 손발 = 막내 혼자의 등급이 아니라 **둘 사이의 상태**. (브랜드 북 v5, 2026-09-21 사장님)
        //   레벨·XP 는 "막내가 점점 똑똑해진다"는 뜻이라 인격("이미 똑똑한 막내")과 어긋나서 폐기.
        val stage = when {
            tonePct >= 80 -> 2   // 척하면 척
            tonePct >= 1 -> 1    // 손발 맞는 중
            else -> 0            // 첫날
        }

        // "손발 맞춘 지 N개월" = 첫 고객이 생긴 날부터. 새 저장소를 만들지 않고 이미 있는 값으로 센다.
        val since = customers.minOfOrNull { it.createdAt } ?: 0L
        val months = if (since <= 0L) 0
        else ((System.currentTimeMillis() - since) / (30L * 24 * 3600 * 1000)).toInt().coerceAtLeast(0)

        // 첫날에도 **못 한다고 말하지 않는다** — 실력은 처음부터 높고, 모르는 건 '사장님 스타일' 하나뿐.
        val line = when {
            tonePct >= 80 -> "사장님 말투, 이제 거의 다 외웠어요!"
            tonePct >= 40 -> "사장님 말투를 부지런히 익히는 중이에요"
            tonePct >= 1 -> "사장님 말투를 막 익히기 시작했어요"
            else -> "상담이랑 일정은 오늘부터 제가 할게요"
        }
        return AgentCardState(
            stage = stage, stageLabel = MAKNE_STAGES[stage], line = line,
            tonePct = tonePct, consultCount = consult, doneJobs = doneJobs,
            togetherMonths = months
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
                    // 선택(품질 향상) 동의를 서버에 기록 — optional_quality. 게이트/설정 어디서 켜든 남김. (추가97 2026-07-06)
                    runCatching { container.authRepository.postConsent(container.preferences.bizPhone, "optional_quality", true) }
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

    // ─────────── 내 데이터 내보내기 / 가져오기 (데이터 안전 1단계, 2026-08-10 사장님) ───────────
    //   재설치·기기변경·데이터삭제 시 통째 소실을 사장님이 직접 방어(사본을 카톡/드라이브에 보관 → 새 폰서 복원).

    private val _lastBackupAt = MutableStateFlow(DataBackup.lastBackupAt(container.appContext))
    val lastBackupAt: StateFlow<Long> = _lastBackupAt.asStateFlow()

    private val _backupBusy = MutableStateFlow(false)
    val backupBusy: StateFlow<Boolean> = _backupBusy.asStateFlow()

    /** 화면에서 토스트로 띄우고 consume. */
    private val _backupMessage = MutableStateFlow<String?>(null)
    val backupMessage: StateFlow<String?> = _backupMessage.asStateFlow()
    fun consumeBackupMessage() { _backupMessage.value = null }

    /** 내보내기 성공 → 화면이 안드로이드 공유 시트를 띄우도록 요청. consume 후 null. */
    private val _shareRequest = MutableStateFlow<DataBackup.ExportResult?>(null)
    val shareRequest: StateFlow<DataBackup.ExportResult?> = _shareRequest.asStateFlow()
    fun consumeShareRequest() { _shareRequest.value = null }

    /** 복원 완료 → "앱을 다시 켜세요" 안내를 띄우도록. consume 후 false. */
    private val _restartNeeded = MutableStateFlow(false)
    val restartNeeded: StateFlow<Boolean> = _restartNeeded.asStateFlow()
    fun consumeRestartNeeded() { _restartNeeded.value = false }

    fun exportData() {
        if (_backupBusy.value) return
        viewModelScope.launch(Dispatchers.IO) {
            _backupBusy.value = true
            try {
                val result = DataBackup.export(container.appContext)
                _lastBackupAt.value = System.currentTimeMillis()
                _shareRequest.value = result
            } catch (e: Exception) {
                _backupMessage.value = "백업을 만들지 못했어요 — 잠시 후 다시 시도해주세요."
            } finally {
                _backupBusy.value = false
            }
        }
    }

    fun importData(uri: Uri) {
        if (_backupBusy.value) return
        viewModelScope.launch(Dispatchers.IO) {
            _backupBusy.value = true
            try {
                val r = DataBackup.import(container.appContext, uri)
                _backupMessage.value = "복원 완료! 고객 ${r.customers}명 · ${r.tables}종 · ${r.rows}건을 되살렸어요."
                _restartNeeded.value = true
            } catch (e: DataBackup.NewerBackupException) {
                _backupMessage.value = "이 백업은 더 최신 버전에서 만들었어요. 앱을 업데이트한 뒤 가져와주세요."
            } catch (e: DataBackup.EmptyBackupException) {
                _backupMessage.value = "백업 파일을 읽지 못했어요 — 시공막내 백업(zip) 파일이 맞는지 확인해주세요."
            } catch (e: Exception) {
                _backupMessage.value = "가져오기에 실패했어요 — 파일을 확인하고 다시 시도해주세요."
            } finally {
                _backupBusy.value = false
            }
        }
    }

    /** 서버에 백업(데이터 안전 2단계) — 텍스트 덤프를 서버에 저장. 지워도/폰 바꿔도 복원 가능. (2026-08-21 사장님) */
    fun serverBackup() {
        if (_backupBusy.value) return
        viewModelScope.launch(Dispatchers.IO) {
            _backupBusy.value = true
            try {
                val bytes = DataBackup.serverBlobBytes(container.appContext)
                val b64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                val ok = container.backupRepository.push(b64)
                if (ok) {
                    _lastBackupAt.value = System.currentTimeMillis()
                    _backupMessage.value = "☁️ 서버에 백업했어요! (${bytes.size / 1024}KB) 이제 폰을 바꿔도 안전해요."
                } else {
                    _backupMessage.value = "서버 백업에 실패했어요 — 인터넷을 확인하고 다시 시도해주세요."
                }
            } catch (e: Throwable) {
                // ⚠️ Exception 만 잡으면 OutOfMemoryError(=Error) 가 새어나가 **앱이 통째로 꺼진다.**
                //   (2026-09-15 사장님: "서버에 백업하기 누르면 막 꺼져" — 실제 크래시 로그 확인)
                android.util.Log.e("Backup", "서버 백업 실패", e)
                _backupMessage.value =
                    if (e is OutOfMemoryError) "사진이 너무 많아 백업을 못 만들었어요 — 개발자에게 알려주세요."
                    else "서버 백업 중 문제가 생겼어요 — 잠시 후 다시 시도해주세요."
            } finally {
                _backupBusy.value = false
            }
        }
    }

    /** 서버에서 복원 — 서버 최신 백업을 내려받아 되살림(안전 upsert, 안 지움). */
    fun serverRestore() {
        if (_backupBusy.value) return
        viewModelScope.launch(Dispatchers.IO) {
            _backupBusy.value = true
            try {
                val b64 = container.backupRepository.pull()
                if (b64.isNullOrBlank()) {
                    _backupMessage.value = "서버에 백업이 없어요. 먼저 '서버에 백업'을 눌러주세요."
                } else {
                    val bytes = android.util.Base64.decode(b64, android.util.Base64.NO_WRAP)
                    val r = DataBackup.importBytes(container.appContext, bytes)
                    _backupMessage.value = "☁️ 서버에서 복원 완료! 고객 ${r.customers}명 · ${r.rows}건을 되살렸어요."
                    _restartNeeded.value = true
                }
            } catch (e: DataBackup.NewerBackupException) {
                _backupMessage.value = "서버 백업이 더 최신 버전이에요. 앱을 업데이트한 뒤 복원해주세요."
            } catch (e: Exception) {
                _backupMessage.value = "서버 복원에 실패했어요 — 잠시 후 다시 시도해주세요."
            } finally {
                _backupBusy.value = false
            }
        }
    }

    /** 카테고리·태그만 복원 — 서버 백업에서 카테고리+태그만 되살림(금액·메모·일정 등 다른 데이터 안 건드림). (2026-09-01 사장님) */
    fun serverRestoreCategoriesOnly() {
        if (_backupBusy.value) return
        viewModelScope.launch(Dispatchers.IO) {
            _backupBusy.value = true
            try {
                val b64 = container.backupRepository.pull()
                if (b64.isNullOrBlank()) {
                    _backupMessage.value = "서버에 백업이 없어요. 먼저 '서버에 백업'을 눌러주세요."
                } else {
                    val bytes = android.util.Base64.decode(b64, android.util.Base64.NO_WRAP)
                    val r = DataBackup.importCategoriesOnly(container.appContext, bytes)
                    _backupMessage.value = "☁️ 카테고리 복원 완료! 카테고리 ${r.categories}개 · 태그 ${r.tagged}명을 되살렸어요."
                    _restartNeeded.value = true
                }
            } catch (e: DataBackup.NewerBackupException) {
                _backupMessage.value = "서버 백업이 더 최신 버전이에요. 앱을 업데이트한 뒤 복원해주세요."
            } catch (e: Exception) {
                _backupMessage.value = "카테고리 복원에 실패했어요 — 잠시 후 다시 시도해주세요."
            } finally {
                _backupBusy.value = false
            }
        }
    }
}

/** 더보기 '우리 막내' 카드. */
data class AgentCardState(
    val stage: Int = 0,                     // 0 첫날 / 1 손발 맞는 중 / 2 척하면 척
    val stageLabel: String = "첫날",
    val line: String = "상담이랑 일정은 오늘부터 제가 할게요",
    val tonePct: Int = 0,
    val consultCount: Int = 0,
    val doneJobs: Int = 0,
    val togetherMonths: Int = 0             // 사장님이랑 손발 맞춘 지 N개월
)

private const val TONE_TARGET = 500

/**
 * 손발 3단계 — 현장 말로. (브랜드 북 v5, 2026-09-21 사장님 "손발")
 *
 * ⚠️ 레벨·XP·칭호 10개("일잘러·레전드")로 돌아가지 말 것.
 *   ① 막내는 **이미 똑똑하다**(사장님 확정 인격). 레벨업은 "점점 똑똑해진다"는 뜻이라 어긋난다.
 *   ② 이건 막내 혼자의 등급이 아니라 **사장님과 맞아가는 정도**다. 그래서 게임 어휘를 안 쓴다.
 *   ③ 미수금·매출 옆에 "일잘러 Lv.29" 가 있으면 안 된다.
 */
private val MAKNE_STAGES = listOf("첫날", "손발 맞는 중", "척하면 척")

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
