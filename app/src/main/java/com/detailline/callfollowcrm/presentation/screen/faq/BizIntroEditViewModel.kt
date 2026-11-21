package com.detailline.callfollowcrm.presentation.screen.faq

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.detailline.callfollowcrm.data.AppContainer
import com.detailline.callfollowcrm.presentation.util.SaveGuard
import com.detailline.callfollowcrm.util.TemplatePhotoStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 🏷️ **업체 소개 편집** — 손님 공개 FAQ 페이지 상단에 들어갈 소개글·대표 사진·경력·건수. (2026-11-21 · 프로토 7U7dnG1GFEoD2RMmd7gRmk ②)
 *
 *   업체명·지역은 **사업자정보(bizName·ownerRegions) 재사용** — 여기서 새로 안 받고 읽어서 보여주기만.
 *   대표 사진은 고르는 즉시 **앱 안으로 복사**(`TemplatePhotoStore` — 문구·대표질문 사진과 같은 창고). 저장 전 취소하면 복사본은 치운다.
 *   저장 실패는 **삼키지 않는다** — `SaveGuard` 로 화면에 말한다(§13①).
 */
class BizIntroEditViewModel(private val container: AppContainer) : ViewModel() {

    companion object {
        const val COVER_MAX = 5
        const val INTRO_MAX = 200
        const val SHORT_MAX = 20
    }

    data class UiState(
        val intro: String = "",
        val career: String = "",
        val count: String = "",
        val photos: List<String> = emptyList(),
        /** 사업자정보에서 읽어 보여주기만(여기서 못 고침). */
        val bizName: String = "",
        val region: String = "",
        val pickingImage: Boolean = false,
        val saving: Boolean = false
    )

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    private val _toast = MutableStateFlow<String?>(null)
    val toast = _toast.asStateFlow()
    fun consumeToast() { _toast.value = null }

    /** 「저장 안 하고 나갈까요?」 판단용 — 처음 모습. */
    private var initial: List<String> = emptyList()

    /** 이번에 새로 복사한 사진 — 저장 안 하고 나가면 치운다. */
    private val freshCopies = mutableListOf<String>()

    init {
        val p = container.preferences
        _state.value = UiState(
            intro = p.faqIntro,
            career = p.faqCareer,
            count = p.faqCount,
            photos = p.faqCoverPhotos,
            bizName = p.bizName,
            region = p.ownerRegions.joinToString(" · ")
        )
        initial = snapshot()
    }

    private fun snapshot(): List<String> = _state.value.let {
        listOf(it.intro, it.career, it.count, it.photos.joinToString("|"))
    }

    val isDirty: Boolean get() = snapshot() != initial

    fun setIntro(v: String) = _state.update { it.copy(intro = v.take(INTRO_MAX)) }
    fun setCareer(v: String) = _state.update { it.copy(career = v.replace('\n', ' ').take(SHORT_MAX)) }
    fun setCount(v: String) = _state.update { it.copy(count = v.replace('\n', ' ').take(SHORT_MAX)) }

    /** 대표 사진 **여러 장** 추가(합쳐서 최대 [COVER_MAX]). 넘치면 앞에서 자르고 말한다. 앱 안으로 복사. */
    fun addPhotos(uris: List<Uri>) {
        if (uris.isEmpty() || _state.value.pickingImage) return
        _state.update { it.copy(pickingImage = true) }
        viewModelScope.launch {
            val room = (COVER_MAX - _state.value.photos.size).coerceAtLeast(0)
            val take = uris.take(room)
            val droppedForCap = uris.size - take.size
            val copied = withContext(Dispatchers.IO) {
                take.mapNotNull { src ->
                    runCatching { TemplatePhotoStore.copyToAppStorage(container.appContext, src) }.getOrNull()?.uri
                }
            }
            freshCopies += copied
            _state.update { it.copy(photos = it.photos + copied, pickingImage = false) }
            when {
                droppedForCap > 0 -> _toast.value = "대표 사진은 최대 ${COVER_MAX}장이에요"
                copied.size < take.size -> _toast.value = "일부 사진을 불러오지 못했어요 — 다시 골라주세요"
            }
        }
    }

    /** 대표 사진 한 장 추가(최대 [COVER_MAX]). 앱 안으로 복사 — 실패하면 **말한다**. */
    fun addPhoto(src: Uri) {
        if (_state.value.pickingImage) return
        if (_state.value.photos.size >= COVER_MAX) {
            _toast.value = "대표 사진은 최대 ${COVER_MAX}장이에요"
            return
        }
        _state.update { it.copy(pickingImage = true) }
        viewModelScope.launch {
            val copied = withContext(Dispatchers.IO) {
                runCatching { TemplatePhotoStore.copyToAppStorage(container.appContext, src) }.getOrNull()
            }
            if (copied == null) {
                _toast.value = "사진을 불러오지 못했어요 — 다시 골라주세요"
                _state.update { it.copy(pickingImage = false) }
            } else {
                freshCopies += copied.uri
                _state.update { it.copy(photos = it.photos + copied.uri, pickingImage = false) }
            }
        }
    }

    fun removePhoto(uri: String) {
        _state.update { it.copy(photos = it.photos - uri) }
        // 이번에 새로 복사한 것(아직 저장 안 됨)만 바로 치운다. 이미 저장돼 있던 건 저장 시 정리.
        if (uri in freshCopies) {
            freshCopies -= uri
            runCatching { TemplatePhotoStore.fileFor(container.appContext, uri)?.delete() }
        }
    }

    fun save(onDone: () -> Unit) {
        if (_state.value.saving) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val s = _state.value
            // 저장에서 빠진(지운) 옛 사진 파일을 치운다 — 저장이 성공해야 지운다.
            val removedOld = initialPhotos().filter { it !in s.photos }
            val ok = SaveGuard.run("업체 소개", _toast) {
                container.preferences.faqIntro = s.intro.trim()
                container.preferences.faqCareer = s.career.trim()
                container.preferences.faqCount = s.count.trim()
                container.preferences.faqCoverPhotos = s.photos
                true
            }
            if (ok == true) {
                removedOld.forEach { runCatching { TemplatePhotoStore.fileFor(container.appContext, it)?.delete() } }
                freshCopies.clear()
                onDone()
            } else {
                _state.update { it.copy(saving = false) }
            }
        }
    }

    private fun initialPhotos(): List<String> =
        initial.getOrNull(3)?.split("|")?.filter { it.isNotBlank() } ?: emptyList()

    override fun onCleared() {
        // 저장 안 하고 나갔으면 이번에 복사한(쓰이지 않은) 사진은 치운다(지워도 되는 것).
        freshCopies.toList().forEach { runCatching { TemplatePhotoStore.fileFor(container.appContext, it)?.delete() } }
        super.onCleared()
    }
}
