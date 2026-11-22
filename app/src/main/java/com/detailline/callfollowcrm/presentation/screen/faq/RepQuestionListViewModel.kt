package com.detailline.callfollowcrm.presentation.screen.faq

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.detailline.callfollowcrm.AppConfig
import com.detailline.callfollowcrm.ai.RefineQuotaException
import com.detailline.callfollowcrm.data.AppContainer
import com.detailline.callfollowcrm.data.local.entity.MessageTemplateEntity
import com.detailline.callfollowcrm.data.local.AppDatabase
import androidx.room.withTransaction
import com.detailline.callfollowcrm.presentation.util.SaveGuard
import com.detailline.callfollowcrm.util.isLikelyAd
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 손님 FAQ 관리 — 공개 문구 목록·지우기·페이지 발행. */
class RepQuestionListViewModel(private val container: AppContainer) : ViewModel() {

    /** null = 아직 읽는 중(빈 화면을 잠깐 잘못 보여주지 않게). */
    val questions: StateFlow<List<MessageTemplateEntity>?> =
        container.messageTemplateRepository.observePublic()
            .map<List<MessageTemplateEntity>, List<MessageTemplateEntity>?> { it }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val photoByTemplate = container.templateAttachmentRepository.observeFirstImageUriByTemplate()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    // 지우기·공개가 실패하면 말하는 통로.
    private val _toast = MutableStateFlow<String?>(null)
    val toast = _toast.asStateFlow()
    fun consumeToast() { _toast.value = null }

    // ── 손님 공개 FAQ 페이지 (Phase 2) ──
    private val _published = MutableStateFlow(container.preferences.faqPublished)
    val published: StateFlow<Boolean> = _published.asStateFlow()
    private val _faqUrl = MutableStateFlow(faqUrlOrEmpty())
    val faqUrl: StateFlow<String> = _faqUrl.asStateFlow()
    private val _publishing = MutableStateFlow(false)
    val publishing: StateFlow<Boolean> = _publishing.asStateFlow()

    private fun faqUrlOrEmpty(): String {
        val slug = container.preferences.faqSlug
        return if (slug.isBlank()) "" else AppConfig.BASE_URL.trimEnd('/') + "/faq/" + slug
    }

    /** 공개 켜기/끄기 — 서버에 올린다. 실패하면 되돌리고 **말한다**(§13①). 연타 막음. */
    fun setPublished(on: Boolean) {
        if (_publishing.value) return
        // 상호가 없으면 이름 없는 공개 페이지가 된다 — 먼저 사업자 정보를 등록하게 막는다.
        if (on && container.preferences.bizName.isBlank()) {
            _toast.value = "상호를 먼저 등록해주세요 — 업체 소개 → 사업자 정보"
            return
        }
        _publishing.value = true
        _published.value = on   // 낙관적 반영
        viewModelScope.launch {
            val r = container.faqPublishRepository.publish(on)
            _publishing.value = false
            r.onSuccess { url ->
                _faqUrl.value = url.ifBlank { faqUrlOrEmpty() }
                _toast.value = if (on) "손님에게 공개했어요" else "공개를 숨겼어요"
            }.onFailure {
                _published.value = !on   // 서버 실패 → 토글 되돌림
                _toast.value = "서버 연결에 실패했어요 — 잠시 후 다시 해주세요"
            }
        }
    }

    /** 공개 중일 때 바뀐 질문·소개를 최신으로 다시 올린다. */
    fun republish() {
        if (_publishing.value || !_published.value) return
        _publishing.value = true
        viewModelScope.launch {
            val r = container.faqPublishRepository.publish(true)
            _publishing.value = false
            r.onSuccess { url -> _faqUrl.value = url.ifBlank { faqUrlOrEmpty() }; _toast.value = "최신 내용으로 올렸어요" }
                .onFailure { _toast.value = "서버 연결에 실패했어요 — 잠시 후 다시 해주세요" }
        }
    }

    fun delete(entity: MessageTemplateEntity) {
        viewModelScope.launch {
            SaveGuard.run("문구 삭제", _toast) {
                container.messageTemplateRepository.deleteById(entity.id)
            }
        }
    }

    // ───────────────────────── ✨ AI 로 대표질문 채우기 ─────────────────────────
    //   문자 기록(손님 질문 + 사장님 답)을 서버 LLM 에 보내 {분류·질문·답} 초안을 받아 **검토 체크리스트**로.
    //   코퍼스는 서버에 저장 안 함. 문자가 적으면 기본 질문으로 시작. (2026-11-22 사장님 「가」 · 프로토 FPXyBo3f3ePwzPHaM35BSn)

    private val _aiOpen = MutableStateFlow(false)
    val aiOpen: StateFlow<Boolean> = _aiOpen.asStateFlow()
    private val _aiLoading = MutableStateFlow(false)
    val aiLoading: StateFlow<Boolean> = _aiLoading.asStateFlow()
    private val _aiSaving = MutableStateFlow(false)
    val aiSaving: StateFlow<Boolean> = _aiSaving.asStateFlow()
    private val _aiDrafts = MutableStateFlow<List<AiDraft>>(emptyList())
    val aiDrafts: StateFlow<List<AiDraft>> = _aiDrafts.asStateFlow()

    fun openAiFill() {
        if (_aiLoading.value) return
        _aiOpen.value = true
        _aiDrafts.value = emptyList()
        loadAiDrafts()
    }

    fun closeAi() {
        _aiOpen.value = false
        _aiDrafts.value = emptyList()
    }

    fun toggleDraft(index: Int) = _aiDrafts.update { list ->
        list.mapIndexed { i, d -> if (i == index) d.copy(selected = !d.selected) else d }
    }

    fun editDraftQuestion(index: Int, v: String) = _aiDrafts.update { list ->
        list.mapIndexed { i, d -> if (i == index) d.copy(question = v) else d }
    }

    fun editDraftAnswer(index: Int, v: String) = _aiDrafts.update { list ->
        list.mapIndexed { i, d -> if (i == index) d.copy(answer = v) else d }
    }

    private fun loadAiDrafts() {
        _aiLoading.value = true
        viewModelScope.launch {
            val corpus = withContext(Dispatchers.IO) { buildCorpus() }
            val r = container.faqPublishRepository.suggest(corpus)
            _aiLoading.value = false
            r.onSuccess { res ->
                val drafts = res.drafts.map { AiDraft(it.category, it.question, it.answer, it.needsReview) }
                if (drafts.isEmpty()) {
                    // 못 뽑았으면 빈손으로 두지 않고 기본 질문으로 시작하게 한다(답은 사장님이 채움).
                    _aiDrafts.value = DEFAULT_DRAFTS
                    _toast.value = if (!res.enough)
                        "주고받은 문자가 아직 적어 기본 질문으로 시작해요 — 답을 채워 주세요"
                    else "자주 묻는 질문을 찾지 못했어요 — 기본 질문으로 시작할게요"
                } else {
                    _aiDrafts.value = drafts
                }
            }.onFailure {
                _aiOpen.value = false
                _toast.value = if (it is RefineQuotaException)
                    "오늘 AI 한도를 다 썼어요 — 내일 다시 해주세요"
                else "AI가 질문을 뽑지 못했어요 — 잠시 후 다시 해주세요"
            }
        }
    }

    /** 최근 문자에서 손님 질문(받은, 광고 제외) + 사장님 답(보낸)을 모아 코퍼스 한 덩이로. 서버로만 가고 저장 안 됨. */
    private fun buildCorpus(): String {
        val pair = runCatching { container.smsRepository.queryForTradeGuess(limitEach = 250) }.getOrNull()
        val sent = pair?.first ?: emptyList()
        val inbox = pair?.second ?: emptyList()
        val questions = inbox.asSequence()
            .filterNot { isLikelyAd(it.body, it.address ?: "") }
            .map { it.body.trim() }
            .filter { it.length in 5..300 }
            .distinct().take(150).toList()
        val answers = sent.asSequence()
            .map { it.body.trim() }
            .filter { it.length in 5..400 }
            .distinct().take(120).toList()
        if (questions.isEmpty() && answers.isEmpty()) return ""
        return buildString {
            append("# 손님이 보낸 문자(질문)\n")
            questions.forEach { append(it).append('\n') }
            append("\n# 사장님이 보낸 답\n")
            answers.forEach { append(it).append('\n') }
        }.take(12000)
    }

    fun registerSelectedDrafts() {
        if (_aiSaving.value) return
        val chosen = _aiDrafts.value.filter { it.selected && it.question.isNotBlank() }
        if (chosen.isEmpty()) { _toast.value = "등록할 질문을 골라주세요"; return }
        _aiSaving.value = true
        viewModelScope.launch {
            val ok = SaveGuard.run("대표질문", _toast) {
                AppDatabase.getInstance(container.appContext).withTransaction {
                    val now = System.currentTimeMillis()
                    chosen.forEach {
                        container.messageTemplateRepository.insert(MessageTemplateEntity(
                            title = it.question, body = it.answer, category = it.category,
                            isDefault = false, isActive = true, isPublic = true,
                            createdAt = now, updatedAt = now
                        ))
                    }
                }
                true
            }
            _aiSaving.value = false
            if (ok == true) { _toast.value = "${chosen.size}개 등록했어요"; closeAi() }
        }
    }

    /** 검토 체크리스트 한 줄 — 서버 초안을 화면에서 고르고/고칠 수 있게 selected·편집 가능 상태로 감싼 것. */
    data class AiDraft(
        val category: String,
        val question: String,
        val answer: String,
        val needsReview: Boolean,
        val selected: Boolean = true
    )

    private companion object {
        /** 문자가 적은 새 사용자용 기본 질문(답은 비워 사장님이 채운다). */
        val DEFAULT_DRAFTS = listOf(
            AiDraft("가격", "가격이 어떻게 되나요?", "", false),
            AiDraft("시공시간", "시공은 얼마나 걸리나요?", "", false),
            AiDraft("A/S", "시공 후 A/S가 되나요?", "", false),
            AiDraft("관리법", "시공 후 관리는 어떻게 하나요?", "", false),
        )
    }
}
