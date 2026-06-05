package com.detailline.callfollowcrm.data

import android.content.Context
import com.detailline.callfollowcrm.ai.AiSummaryRepository
import com.detailline.callfollowcrm.ai.ConversationAiRepository
import com.detailline.callfollowcrm.ai.NoOpAiSummaryRepository
import com.detailline.callfollowcrm.ai.OllamaRefineRepository
import com.detailline.callfollowcrm.ai.RemoteRefineRepository
import com.detailline.callfollowcrm.ai.PhaseOneApiRepository
import com.detailline.callfollowcrm.ai.RefineRepository
import com.detailline.callfollowcrm.ai.ServerHealthMonitor
import com.detailline.callfollowcrm.ai.ServerSuggestionRepository
import com.detailline.callfollowcrm.ai.SuggestionRepository
import com.detailline.callfollowcrm.data.local.AppDatabase
import com.detailline.callfollowcrm.data.preferences.AppPreferences
import com.detailline.callfollowcrm.data.repository.CachedMessageRepository
import com.detailline.callfollowcrm.data.repository.CallRecordRepository
import com.detailline.callfollowcrm.data.repository.CallSummaryRepository
import com.detailline.callfollowcrm.data.repository.CategoryRepository
import com.detailline.callfollowcrm.data.repository.CustomerRepository
import com.detailline.callfollowcrm.data.repository.ImportantMessageRepository
import com.detailline.callfollowcrm.data.repository.MessageHistoryRepository
import com.detailline.callfollowcrm.data.repository.MessageTemplateRepository
import com.detailline.callfollowcrm.data.repository.PricingItemRepository
import com.detailline.callfollowcrm.data.repository.RecordingRepository
import com.detailline.callfollowcrm.data.repository.SmsRepository
import com.detailline.callfollowcrm.data.repository.TemplateAttachmentRepository
import com.detailline.callfollowcrm.presentation.navigation.NavEvents
import com.detailline.callfollowcrm.recording.NoOpServerUploadRepository
import com.detailline.callfollowcrm.recording.ServerUploadRepository

/**
 * 단순한 manual DI 컨테이너. Hilt 없이 Application이 보유한다.
 */
class AppContainer(context: Context) {
    private val db = AppDatabase.getInstance(context.applicationContext)

    val customerRepository = CustomerRepository(
        db.customerDao(),
        recordingDao = db.recordingAttachmentDao(),
        callSummaryDao = db.callSummaryDao()
    )
    val callRecordRepository = CallRecordRepository(db.callRecordDao())
    val messageTemplateRepository = MessageTemplateRepository(db.messageTemplateDao())
    val messageHistoryRepository = MessageHistoryRepository(db.messageHistoryDao())
    val recordingRepository = RecordingRepository(db.recordingAttachmentDao())
    // 현장 사진(로컬) — 사장님이 고객 현장에 올린 사진. 2026-06-04.
    val sitePhotoRepository = com.detailline.callfollowcrm.data.repository.SitePhotoRepository(
        context.applicationContext, db.sitePhotoDao()
    )
    // 시공접수서 제출 이벤트 — 채팅 타임라인에 "접수서 작성 완료" 카드로 표시. 2026-06-05.
    val intakeEventRepository = com.detailline.callfollowcrm.data.repository.IntakeEventRepository(
        db.intakeEventDao()
    )
    // 현장 사진 서버 조회(§25) — 고객 카드에 팀원+사장님 사진 표시. 2026-06-05.
    val sitePhotoServerRepository = com.detailline.callfollowcrm.ai.SitePhotoServerRepository()
    val callSummaryRepository = CallSummaryRepository(db.callSummaryDao())
    val templateAttachmentRepository = TemplateAttachmentRepository(db.templateAttachmentDao())
    val smsRepository = SmsRepository(context.applicationContext)
    val importantMessageRepository = ImportantMessageRepository(db.importantMessageDao())
    val cachedMessageRepository = CachedMessageRepository(db.cachedMessageDao())
    val conversationAiRepository = ConversationAiRepository(db.aiSummaryDao())
    val pricingItemRepository = PricingItemRepository(db.pricingItemDao())
    val categoryRepository = CategoryRepository(db.categoryDao(), db.customerDao())
    // 2026-05-30 #7 — 입금 상태 기반 자동 카테고리 분류 (시공 대기 / 시공 완료).
    val autoCategoryClassifier = com.detailline.callfollowcrm.data.repository.AutoCategoryClassifier(
        categoryRepository = categoryRepository,
        customerRepository = customerRepository
    )
    val spamPhoneRepository = com.detailline.callfollowcrm.data.repository.SpamPhoneRepository(db.spamPhoneDao())
    // 정산 Phase 2 (DB v20) — 직접 현금 기록.
    val manualCashRepository = com.detailline.callfollowcrm.data.repository.ManualCashRepository(db.manualCashDao())
    // 수첩 (DB v21) — 일당/거래처.
    val notebookRepository = com.detailline.callfollowcrm.data.repository.NotebookRepository(db.notebookContactDao())
    // 일당 배정 (DB v23) — 함께한 현장 + 일당 자동차감.
    val jobCrewRepository = com.detailline.callfollowcrm.data.repository.JobCrewRepository(db.jobCrewDao())
    // 정기문자 (DB v25) — 시공 후 주기 안부/점검. 포그라운드 계산, 자동발송 X.
    val recurringMessageRepository = com.detailline.callfollowcrm.data.repository.RecurringMessageRepository(db.recurringMessageDao())
    val smsContactCacheRepository = com.detailline.callfollowcrm.data.repository.SmsContactCacheRepository(db.smsContactCacheDao())
    // 2026-05-29 킬러콘텐츠 3단계 — chip 행동 시그널 저장 (DB v17).
    val suggestionEventRepository = com.detailline.callfollowcrm.data.repository.SuggestionEventRepository(db.suggestionEventDao())
    val usageStatsRepository = com.detailline.callfollowcrm.ai.UsageStatsRepository()

    val preferences = AppPreferences(context)

    /**
     * ChatScreen composer 의 phone 별 임시저장 (2026-05-27 사장님 통점).
     *   ChatViewModel 는 navigation pop/push 마다 새 인스턴스라 local state 가 날아간다.
     *   AppContainer 는 Application 수명 = 앱 실행 동안만 보관 (재시작 시 비움).
     *   사장님 통점은 "뒤로가기 → 재진입 시 사라짐" 이므로 in-memory 로 충분.
     */
    val chatDraftStore = com.detailline.callfollowcrm.data.draft.ChatDraftStore()

    /**
     * AI 추천 답변 chips 의 phone 별 in-memory 캐시 (2026-05-28 사장님 통점).
     *   ChatScreen 재진입 시 chips 가 잠시 사라졌다 다시 채워지는 끊김을 0ms 로 단축.
     *   stale 차단은 ChatViewModel.effectiveSuggestions 의 기존 로직이 책임.
     */
    val suggestionsCacheStore = com.detailline.callfollowcrm.data.draft.SuggestionsCacheStore()

    // pendingNewSmsContacts 는 2026-05-28 본격 fix (sms_contacts_cache) 로 대체. 제거됨.

    // Phase 4: 인터페이스만. 실제 호출 없음.
    val aiSummaryRepository: AiSummaryRepository = NoOpAiSummaryRepository()
    val serverUploadRepository: ServerUploadRepository = NoOpServerUploadRepository()

    // 한국어 문장 다듬기 (✨ 버튼).
    //   2026-05-28 사장님 결정: Gemini 2.5 Flash + 컨텍스트 전송 (recent_messages + tone + customer hint).
    //   서버 endpoint `POST /api/refine` (cowork 가 박을 것) 안에서 Gemini 호출. API 키는 Mac mini 만.
    //   OllamaRefineRepository 는 rollback 용 코드 유지.
    val refineRepository: RefineRepository = RemoteRefineRepository()

    // 답변 추천 (Phase 1). 맥미니 자체 서버 (포트 8000) — RINGGO_SERVER_SPEC.md 참조.
    // SmsReceiver 가 prepare 트리거, ChatViewModel 이 fetch.
    val suggestionRepository: SuggestionRepository = ServerSuggestionRepository()
    val phaseOneApiRepository = PhaseOneApiRepository()

    // 2026-05-29 킬러콘텐츠 4단계 (Tone RAG) — 사장님 sent SMS 풀 batch upload (Mac mini).
    val ownerToneUploadRepository = com.detailline.callfollowcrm.ai.OwnerToneUploadRepository()

    // 2026-05-29 킬러콘텐츠 5단계 — 고객 페르소나 (Haiku 자동 생성, cowork prepare-reply 가 책임).
    val customerPersonaRepository = com.detailline.callfollowcrm.ai.CustomerPersonaRepository()

    // 2026-06-02 맥미니 §19 — 시공접수서 발급. 채팅 [접수서 링크] → URL 발급 → SMS 본문 prefill.
    val intakeFormRepository = com.detailline.callfollowcrm.ai.IntakeFormRepository()

    // 2026-06-03 §19.2 — 시공접수서 제출 동기화(폴링 → 고객 카드 반영 + 알림).
    val intakeSyncManager = com.detailline.callfollowcrm.ai.IntakeSyncManager(this)

    // 2026-06-05 팀 관리(비즈니스) — 팀원 초대/목록/제외/출발알림. 서버 팀 API.
    val teamRepository = com.detailline.callfollowcrm.ai.TeamRepository()
    /** 팀원 출발 이벤트 폴링/알림 + 상담함 배너 소스 (2026-06-06). preferences 아래에서 생성. */
    val teamEventCenter by lazy {
        com.detailline.callfollowcrm.ai.TeamEventCenter(teamRepository, preferences)
    }
    // 2026-06-05 팀원 현장 배정(로컬 기록 + 서버 snapshot push 의 토대).
    val teamAssignmentRepository = com.detailline.callfollowcrm.data.repository.TeamAssignmentRepository(
        db.teamAssignmentDao()
    )

    // 2026-06-02 맥미니 §18 — 에이닷 통화요약 → Haiku 한 줄+불릿. AdotSummaryImporter 가 best-effort 호출.
    val callSummaryServerRepository = com.detailline.callfollowcrm.ai.CallSummaryServerRepository()

    // 서버 살아있음 모니터 — HomeScreen 상단 ● indicator 가 구독.
    val serverHealth = ServerHealthMonitor(phaseOneApiRepository).also { it.start() }

    // SMS/MMS 캐시 백그라운드 prefetcher. Application.onCreate 에서 prefetchRecentContacts() 트리거.
    val smsCachePrefetcher = SmsCachePrefetcher(smsRepository, cachedMessageRepository)

    val navEvents = NavEvents()

    /**
     * 2026-05-29 — ViewModel scope 가 cancel 된 후 (onCleared) 박는 비동기 작업용.
     * 예: ChatViewModel onCleared 에서 DISMISSED 시그널 DB 박기.
     * Application 수명과 동일. supervisorJob 이라 한 작업 실패가 다른 거 영향 X.
     */
    val applicationScope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO
    )
}
