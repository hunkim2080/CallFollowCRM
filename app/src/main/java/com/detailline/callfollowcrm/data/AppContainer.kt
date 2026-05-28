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
    val callSummaryRepository = CallSummaryRepository(db.callSummaryDao())
    val templateAttachmentRepository = TemplateAttachmentRepository(db.templateAttachmentDao())
    val smsRepository = SmsRepository(context.applicationContext)
    val importantMessageRepository = ImportantMessageRepository(db.importantMessageDao())
    val cachedMessageRepository = CachedMessageRepository(db.cachedMessageDao())
    val conversationAiRepository = ConversationAiRepository(db.aiSummaryDao())
    val pricingItemRepository = PricingItemRepository(db.pricingItemDao())
    val categoryRepository = CategoryRepository(db.categoryDao(), db.customerDao())
    val spamPhoneRepository = com.detailline.callfollowcrm.data.repository.SpamPhoneRepository(db.spamPhoneDao())
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

    // 서버 살아있음 모니터 — HomeScreen 상단 ● indicator 가 구독.
    val serverHealth = ServerHealthMonitor(phaseOneApiRepository).also { it.start() }

    // SMS/MMS 캐시 백그라운드 prefetcher. Application.onCreate 에서 prefetchRecentContacts() 트리거.
    val smsCachePrefetcher = SmsCachePrefetcher(smsRepository, cachedMessageRepository)

    val navEvents = NavEvents()
}
