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
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * 단순한 manual DI 컨테이너. Hilt 없이 Application이 보유한다.
 */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
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
    // 변경/처리 이력 이벤트 — 일정/금액/잔금 변경을 채팅 타임라인 카드로. 2026-06-30.
    val timelineEventRepository = com.detailline.callfollowcrm.data.repository.TimelineEventRepository(
        db.timelineEventDao()
    )
    // 발행 문서 이력 — 발행한 견적서/시공접수서 스냅샷(고객상세 이력 + 채팅 카드). 2026-07-07.
    val issuedDocRepository = com.detailline.callfollowcrm.data.repository.IssuedDocRepository(
        db.issuedDocDao()
    )
    // 현장 사진 서버 조회(§25) — 고객 카드에 팀원+사장님 사진 표시. 2026-06-05.
    val sitePhotoServerRepository = com.detailline.callfollowcrm.ai.SitePhotoServerRepository()
    val callSummaryRepository = CallSummaryRepository(db.callSummaryDao())
    val templateAttachmentRepository = TemplateAttachmentRepository(db.templateAttachmentDao())

    /**
     * 말투 학습용 "고객 번호 끝8자리" in-memory 캐시 (2026-06-14 사장님).
     *   SmsRepository 가 발신문자 중 고객에게 보낸 것만 골라 학습하도록 공급. DB 접근 없이 읽음(메인스레드 안전).
     *   클래스 말미 init 에서 customerRepository.observeAll() 구독으로 채움.
     */
    @Volatile
    private var customerSuffixCache: Set<String> = emptySet()
    val smsRepository = SmsRepository(context.applicationContext) { customerSuffixCache }
    val importantMessageRepository = ImportantMessageRepository(db.importantMessageDao())
    val cachedMessageRepository = CachedMessageRepository(db.cachedMessageDao())
    val conversationAiRepository = ConversationAiRepository(db.aiSummaryDao(), ownerPhone = { preferences.bizPhone })
    val pricingItemRepository = PricingItemRepository(db.pricingItemDao())
    val principleRepository = com.detailline.callfollowcrm.data.repository.PrincipleRepository(db.principleDao())
    val categoryRepository = CategoryRepository(db.categoryDao(), db.customerDao())
    // 2026-05-30 #7 — 입금 상태 기반 자동 카테고리 분류 (시공 대기 / 시공 완료).
    val autoCategoryClassifier = com.detailline.callfollowcrm.data.repository.AutoCategoryClassifier(
        categoryRepository = categoryRepository,
        customerRepository = customerRepository
    )
    val spamPhoneRepository = com.detailline.callfollowcrm.data.repository.SpamPhoneRepository(db.spamPhoneDao())
    // 사용자 여정 이벤트(화면진입·버튼·캡쳐·LLM) — 30초 배치 → POST /api/event. (2026-06-23 사장님)
    val journeyEventRepository = com.detailline.callfollowcrm.ai.JourneyEventRepository()
    // 정산 Phase 2 (DB v20) — 직접 현금 기록.
    val manualCashRepository = com.detailline.callfollowcrm.data.repository.ManualCashRepository(db.manualCashDao())
    // 수첩 (DB v21) — 일당/거래처.
    val notebookRepository = com.detailline.callfollowcrm.data.repository.NotebookRepository(db.notebookContactDao())
    // 일당 배정 (DB v23) — 함께한 현장 + 일당 자동차감.
    val jobCrewRepository = com.detailline.callfollowcrm.data.repository.JobCrewRepository(db.jobCrewDao())
    // 시공 건(이력) (DB v42) — 재방문/추가 시공 시 첫 시공 유실 방지(완료 건을 이력으로 보관). (2026-07-20)
    val jobRepository = com.detailline.callfollowcrm.data.repository.JobRepository(db.jobDao(), db.customerDao())
    // 정기문자 (DB v25) — 시공 후 주기 안부/점검. 포그라운드 계산, 자동발송 X.
    val recurringMessageRepository = com.detailline.callfollowcrm.data.repository.RecurringMessageRepository(db.recurringMessageDao())
    val smsContactCacheRepository = com.detailline.callfollowcrm.data.repository.SmsContactCacheRepository(db.smsContactCacheDao())
    // 상담함/문자함 분류(2026-07-11 사장님) — 기본 문자앱이 되며 고객/일반 문자 분리.
    val threadBucketRepository = com.detailline.callfollowcrm.data.repository.ThreadBucketRepository(db.threadBucketDao())
    // 2026-05-29 킬러콘텐츠 3단계 — chip 행동 시그널 저장 (DB v17).
    val suggestionEventRepository = com.detailline.callfollowcrm.data.repository.SuggestionEventRepository(db.suggestionEventDao())
    val usageStatsRepository = com.detailline.callfollowcrm.ai.UsageStatsRepository()

    val preferences = AppPreferences(context)

    /**
     * 로그인 세션 토큰 저장고(보안 §D-4). verify-code 성공 시 저장 → SessionAuthInterceptor 가 모든
     *   api.si0in.kr 요청에 `Authorization: Bearer` 부착. OTP 로그인 꺼진 동안은 토큰이 없어 무영향.
     *   여기서 만들면 프로세스 전역 INSTANCE 가 세팅돼 인터셉터가 첫 요청부터 읽을 수 있다.
     */
    val sessionTokenStore = com.detailline.callfollowcrm.data.SessionTokenStore.get(context)

    /**
     * ChatScreen composer 의 phone 별 임시저장 (2026-05-27 사장님 통점).
     *   ChatViewModel 는 navigation pop/push 마다 새 인스턴스라 local state 가 날아간다.
     *   AppContainer 는 Application 수명 = 앱 실행 동안만 보관 (재시작 시 비움).
     *   사장님 통점은 "뒤로가기 → 재진입 시 사라짐" 이므로 in-memory 로 충분.
     */
    val chatDraftStore = com.detailline.callfollowcrm.data.draft.ChatDraftStore()

    /**
     * "최근 대화" 안 읽음(파란 점) 읽음 추적 — 카톡식 (2026-06-08 사장님 통점).
     *   채팅 열면(ChatViewModel.init) markRead → 홈이 구독해 "고객 마지막 메시지 > 읽은 시각" 일 때만 점.
     *   SharedPreferences 영속 (앱 재시작에도 유지).
     */
    val readStateStore = com.detailline.callfollowcrm.data.draft.ReadStateStore(context.applicationContext)

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
    val refineRepository: RefineRepository = RemoteRefineRepository(ownerPhone = { preferences.bizPhone })

    // 답변 추천 (Phase 1). 맥미니 자체 서버 (포트 8000) — RINGGO_SERVER_SPEC.md 참조.
    // SmsReceiver 가 prepare 트리거, ChatViewModel 이 fetch.
    val suggestionRepository: SuggestionRepository = ServerSuggestionRepository(ownerPhone = { preferences.bizPhone })
    val phaseOneApiRepository = PhaseOneApiRepository()

    // 2026-05-29 킬러콘텐츠 4단계 (Tone RAG) — 사장님 sent SMS 풀 batch upload (Mac mini).
    val ownerToneUploadRepository = com.detailline.callfollowcrm.ai.OwnerToneUploadRepository()

    // 2026-07-02 문자 기반 가격 추출 — 보낸 견적 문자 → 서버 → 구조화 항목/가격/기준.
    val pricingExtractRepository = com.detailline.callfollowcrm.ai.PricingExtractRepository()

    // 2026-07-02 템플릿 제목 자동 작명 — 본문 → Haiku → 짧은 제목. 실패 시 앱 휴리스틱 유지.
    val templateNameRepository = com.detailline.callfollowcrm.ai.TemplateNameRepository()

    // 2026-05-29 킬러콘텐츠 5단계 — 고객 페르소나 (Haiku 자동 생성, cowork prepare-reply 가 책임).
    val customerPersonaRepository = com.detailline.callfollowcrm.ai.CustomerPersonaRepository()

    // 2026-06-02 맥미니 §19 — 시공접수서 발급. 채팅 [접수서 링크] → URL 발급 → SMS 본문 prefill.
    val intakeFormRepository = com.detailline.callfollowcrm.ai.IntakeFormRepository()

    // 2026-06-03 §19.2 — 시공접수서 제출 동기화(폴링 → 고객 카드 반영 + 알림).
    val intakeSyncManager = com.detailline.callfollowcrm.ai.IntakeSyncManager(this)

    // 2026-06-05 팀 관리(비즈니스) — 팀원 초대/목록/제외/출발알림. 서버 팀 API.
    val teamRepository = com.detailline.callfollowcrm.ai.TeamRepository()

    // 2026-06-08 협업 현장(사장↔사장) — 공유 요청/수락/진행/입금. 서버 endpoint 대기(SERVER_HANDOFF_collab_sites.md).
    val sharedSiteRepository = com.detailline.callfollowcrm.ai.SharedSiteRepository()

    // 2026-07-13 본폰 미러 링크 — 업무폰 일정을 본폰에서 읽기전용으로. 서버 완료(docs/ANDROID_HANDOFF_mirror_app.md).
    val mirrorRepository = com.detailline.callfollowcrm.ai.MirrorRepository()
    /** 일정/돈 스냅샷 자동 전송(30초 디바운스 + 해시). Application.onCreate 에서 start(). 옵트인일 때만 동작. */
    val mirrorSyncManager by lazy {
        com.detailline.callfollowcrm.ai.MirrorSyncManager(
            mirrorRepository, preferences, customerRepository, manualCashRepository, sharedSiteRepository
        )
    }

    // 2026-08-13 시공막내 웹 사진 캘린더(읽기전용 뷰어) — 앱측: 스케줄 피드 push + QR authorize + 웹 로그아웃.
    //   서버 완료(docs/SERVER_HANDOFF_web_photo_calendar_SERVER_DONE.md). 미러와 독립(웹 로그인하면 켜짐).
    val webFeedRepository = com.detailline.callfollowcrm.ai.WebFeedRepository()
    /** 뷰어용 스케줄 피드 자동 전송(30초 디바운스 + 해시). Application.onCreate 에서 start(). 웹 로그인 상태일 때만. */
    val webFeedSyncManager by lazy {
        com.detailline.callfollowcrm.ai.WebFeedSyncManager(
            webFeedRepository, preferences, customerRepository, categoryRepository
        )
    }
    /** 로컬 현장사진을 서버로 백필 업로드(웹 로그인 상태일 때만) → PC 웹에서 사장님 사진 보임. kick() 으로 1회 시도. */
    val ownerPhotoUploadManager by lazy {
        com.detailline.callfollowcrm.ai.OwnerPhotoUploadManager(
            context.applicationContext, db.sitePhotoDao(), customerRepository, sitePhotoServerRepository, preferences
        )
    }

    // 2026-06-21 cowork 요청 — 앱 진입(onResume)마다 /api/beta/check 핑 → admin 대시보드 사용 수/최근 실행 실시간.
    val betaCheckRepository = com.detailline.callfollowcrm.ai.BetaCheckRepository()
    // 2026-07-04 회원가입(폰 인증번호) — docs/ANDROID_HANDOFF_signup_auth.md.
    val authRepository = com.detailline.callfollowcrm.ai.AuthRepository()
    /** 팀원 출발 이벤트 폴링/알림 + 상담함 배너 소스 (2026-06-06). preferences 아래에서 생성. */
    val teamEventCenter by lazy {
        com.detailline.callfollowcrm.ai.TeamEventCenter(teamRepository, preferences)
    }
    /** 협업 현장 진행 이벤트 폴링/알림 + 상담함 배너 소스 (2026-06-09). */
    val collabEventCenter by lazy {
        com.detailline.callfollowcrm.ai.CollabEventCenter(sharedSiteRepository, preferences)
    }
    /** FCM 토큰 서버 등록 — 즉시 푸시(2026-06-12). docs/SERVER_HANDOFF_fcm_push.md */
    val pushRegisterRepository by lazy {
        com.detailline.callfollowcrm.ai.PushRegisterRepository()
    }
    // 2026-06-05 팀원 현장 배정(로컬 기록 + 서버 snapshot push 의 토대).
    val teamAssignmentRepository = com.detailline.callfollowcrm.data.repository.TeamAssignmentRepository(
        db.teamAssignmentDao()
    )

    // 2026-06-02 맥미니 §18 — 에이닷 통화요약 → Haiku 한 줄+불릿. AdotSummaryImporter 가 best-effort 호출.
    val callSummaryServerRepository = com.detailline.callfollowcrm.ai.CallSummaryServerRepository(ownerPhone = { preferences.bizPhone })

    // 2026-06-08 맥미니 §26 — 무료 녹음(m4a) → 로컬 Whisper STT + Haiku 요약. CallAudioSummarizer 가 호출.
    val callAudioSummaryRepository = com.detailline.callfollowcrm.ai.CallAudioSummaryRepository(ownerPhone = { preferences.bizPhone })

    // 서버 살아있음 모니터 — HomeScreen 상단 ● indicator 가 구독. 앱 포그라운드일 때만 ping(배터리). (2026-08-11 성능감사)
    val serverHealth = ServerHealthMonitor(phaseOneApiRepository).also { m ->
        m.start { com.detailline.callfollowcrm.CallFollowCrmApplication.isForeground }
    }

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

    init {
        // 고객 번호 끝8자리 캐시 유지 — 말투 학습이 "고객에게 보낸 문자"만 보도록.
        applicationScope.launch {
            customerRepository.observeAll().collect { list ->
                customerSuffixCache = list
                    .mapNotNull { c ->
                        c.phoneNumber.filter { it.isDigit() }.takeLast(8).takeIf { it.length >= 7 }
                    }
                    .toSet()
            }
        }
    }
}
