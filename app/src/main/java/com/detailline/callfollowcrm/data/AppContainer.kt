package com.detailline.callfollowcrm.data

import android.content.Context
import com.detailline.callfollowcrm.ai.AiSummaryRepository
import com.detailline.callfollowcrm.ai.NoOpAiSummaryRepository
import com.detailline.callfollowcrm.ai.OllamaRefineRepository
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
import com.detailline.callfollowcrm.data.repository.CustomerRepository
import com.detailline.callfollowcrm.data.repository.ImportantMessageRepository
import com.detailline.callfollowcrm.data.repository.MessageHistoryRepository
import com.detailline.callfollowcrm.data.repository.MessageTemplateRepository
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

    val preferences = AppPreferences(context)

    // Phase 4: 인터페이스만. 실제 호출 없음.
    val aiSummaryRepository: AiSummaryRepository = NoOpAiSummaryRepository()
    val serverUploadRepository: ServerUploadRepository = NoOpServerUploadRepository()

    // 한국어 문장 다듬기 (✨ 버튼). 맥미니 Ollama Tailnet 호출 — RINGGO_BACKEND_BRIEF.md 참조.
    val refineRepository: RefineRepository = OllamaRefineRepository()

    // 답변 추천 (Phase 1). 맥미니 자체 서버 (포트 8000) — RINGGO_SERVER_SPEC.md 참조.
    // SmsReceiver 가 prepare 트리거, ChatViewModel 이 fetch.
    val suggestionRepository: SuggestionRepository = ServerSuggestionRepository()
    val phaseOneApiRepository = PhaseOneApiRepository()

    // 서버 살아있음 모니터 — HomeScreen 상단 ● indicator 가 구독.
    val serverHealth = ServerHealthMonitor(phaseOneApiRepository).also { it.start() }

    val navEvents = NavEvents()
}
