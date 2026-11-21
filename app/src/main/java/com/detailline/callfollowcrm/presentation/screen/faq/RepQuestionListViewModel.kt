package com.detailline.callfollowcrm.presentation.screen.faq

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.detailline.callfollowcrm.AppConfig
import com.detailline.callfollowcrm.data.AppContainer
import com.detailline.callfollowcrm.data.local.entity.RepQuestionEntity
import com.detailline.callfollowcrm.presentation.util.SaveGuard
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 💬 대표질문 관리 — 목록·순서·지우기. (프로토 EYKUs11cVT7JW71yVrVoBA ②) */
class RepQuestionListViewModel(private val container: AppContainer) : ViewModel() {

    /** null = 아직 읽는 중(빈 화면을 잠깐 잘못 보여주지 않게). */
    val questions: StateFlow<List<RepQuestionEntity>?> =
        container.repQuestionRepository.observeAll()
            .map<List<RepQuestionEntity>, List<RepQuestionEntity>?> { it }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // 🗣 순서 바꾸기·지우기·공개가 실패하면 말하는 통로.
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

    /** 끌어서 놓은 순서 그대로 적는다. 실패하면 말한다(목록은 DB 가 다시 그려 옛 순서로 돌아온다). */
    fun reorder(orderedIds: List<Long>) {
        viewModelScope.launch {
            SaveGuard.run("대표질문 순서", _toast) { container.repQuestionRepository.reorder(orderedIds) }
        }
    }

    fun delete(entity: RepQuestionEntity) {
        viewModelScope.launch {
            try {
                container.repQuestionRepository.delete(entity)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                android.util.Log.e("RepQuestion", "delete failed", e)
                _toast.value = "대표질문을 지우지 못했어요 — 다시 한 번 눌러주세요"
            }
        }
    }
}
