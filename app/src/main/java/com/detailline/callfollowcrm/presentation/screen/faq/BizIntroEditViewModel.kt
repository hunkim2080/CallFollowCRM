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
 * 🏷️ **업체 소개 편집** — 손님 공개 FAQ 페이지 상단(슬로건·소개글·대표 사진+설명·경력·건수). (2026-11-21 · 프로토 7U7dnG1GFEoD2RMmd7gRmk ②)
 *
 *   업체명·지역은 **사업자정보(bizName·ownerRegions) 재사용** — 읽어서 보여주기만.
 *   **느낌(톤)은 사장님이 정한다** — 고정 문구를 박지 않는다. [TONE_PRESETS] 로 슬로건을 채워 넣고 고쳐 쓸 수 있다(프리셋+직접수정).
 *   대표 사진은 [COVER_MAX] 장, 사진마다 **설명(캡션)** 을 직접 쓴다(비우면 손님 페이지에 캡션 안 나옴). 사진↔캡션은 index 정렬.
 *   사진은 고르는 즉시 앱 안으로 복사(`TemplatePhotoStore`). 저장 실패는 `SaveGuard` 로 말한다(§13①).
 */
class BizIntroEditViewModel(private val container: AppContainer) : ViewModel() {

    companion object {
        const val COVER_MAX = 5
        const val INTRO_MAX = 200
        const val SHORT_MAX = 20
        const val SLOGAN_MAX = 24
        const val CAPTION_MAX = 24

        /** 톤 프리셋 — (이름, 슬로건). 누르면 슬로건에 채워지고, 사장님이 고쳐 쓸 수 있다. 「직접 쓰기」는 프리셋 없이 빈칸. */
        val TONE_PRESETS = listOf(
            "장인·정성" to "작은 디테일까지, 정성스럽게",
            "절대고수" to "맡기신 일, 끝을 봅니다",
            "경력·믿음" to "한 길만 걸었습니다, 믿고 맡기세요",
            "깔끔·완벽" to "티 하나 없이, 새것처럼",
        )
    }

    data class UiState(
        val slogan: String = "",
        val intro: String = "",
        val career: String = "",
        val count: String = "",
        val photos: List<String> = emptyList(),
        /** photos 와 index 정렬된 사진 설명. */
        val captions: List<String> = emptyList(),
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
        val photos = p.faqCoverPhotos
        _state.value = UiState(
            slogan = p.faqSlogan,
            intro = p.faqIntro,
            career = p.faqCareer,
            count = p.faqCount,
            photos = photos,
            captions = alignCaptions(p.faqCoverCaptions, photos.size),
            bizName = p.bizName,
            region = p.ownerRegions.joinToString(" · ")
        )
        initial = snapshot()
    }

    /** 캡션 개수를 사진 개수에 맞춘다(모자라면 빈칸, 넘치면 자름). */
    private fun alignCaptions(caps: List<String>, n: Int): List<String> = List(n) { i -> caps.getOrElse(i) { "" } }

    /** 사업자 정보(상호·지역)만 다시 읽는다 — 사업자 정보 화면 다녀온 뒤 ON_RESUME 에 부른다(사장님 편집분은 안 건드림). */
    fun reloadBizInfo() {
        val p = container.preferences
        _state.update { it.copy(bizName = p.bizName, region = p.ownerRegions.joinToString(" · ")) }
    }

    private fun snapshot(): List<String> = _state.value.let {
        listOf(it.slogan, it.intro, it.career, it.count, it.photos.joinToString("|"), it.captions.joinToString("\u0001"))
    }

    val isDirty: Boolean get() = snapshot() != initial

    fun setSlogan(v: String) = _state.update { it.copy(slogan = v.replace('\n', ' ').take(SLOGAN_MAX)) }
    fun applyPreset(slogan: String) = setSlogan(slogan)
    fun setIntro(v: String) = _state.update { it.copy(intro = v.take(INTRO_MAX)) }
    fun setCareer(v: String) = _state.update { it.copy(career = v.replace('\n', ' ').take(SHORT_MAX)) }
    fun setCount(v: String) = _state.update { it.copy(count = v.replace('\n', ' ').take(SHORT_MAX)) }
    fun setCaption(index: Int, v: String) = _state.update { s ->
        if (index !in s.captions.indices) s
        else s.copy(captions = s.captions.toMutableList().also { it[index] = v.replace('\n', ' ').take(CAPTION_MAX) })
    }

    /** 대표 사진 **여러 장** 추가(합쳐서 최대 [COVER_MAX]). 캡션은 빈칸으로 같이 늘어난다. */
    fun addPhotos(uris: List<Uri>) {
        if (uris.isEmpty() || _state.value.pickingImage) return
        if (_state.value.photos.size >= COVER_MAX) { _toast.value = "대표 사진은 최대 ${COVER_MAX}장이에요"; return }
        _state.update { it.copy(pickingImage = true) }
        viewModelScope.launch {
            val room = (COVER_MAX - _state.value.photos.size).coerceAtLeast(0)
            val take = uris.take(room)
            val droppedForCap = uris.size - take.size
            val copied = withContext(Dispatchers.IO) {
                take.mapNotNull { src -> runCatching { TemplatePhotoStore.copyToAppStorage(container.appContext, src) }.getOrNull()?.uri }
            }
            freshCopies += copied
            _state.update { it.copy(photos = it.photos + copied, captions = it.captions + copied.map { "" }, pickingImage = false) }
            when {
                droppedForCap > 0 -> _toast.value = "대표 사진은 최대 ${COVER_MAX}장이에요"
                copied.size < take.size -> _toast.value = "일부 사진을 불러오지 못했어요 — 다시 골라주세요"
            }
        }
    }

    fun removePhotoAt(index: Int) {
        _state.update { s ->
            if (index !in s.photos.indices) return@update s
            val uri = s.photos[index]
            if (uri in freshCopies) {
                freshCopies -= uri
                runCatching { TemplatePhotoStore.fileFor(container.appContext, uri)?.delete() }
            }
            s.copy(
                photos = s.photos.toMutableList().also { it.removeAt(index) },
                captions = s.captions.toMutableList().also { if (index in it.indices) it.removeAt(index) }
            )
        }
    }

    fun save(onDone: () -> Unit) {
        if (_state.value.saving) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val s = _state.value
            val removedOld = initialPhotos().filter { it !in s.photos }
            val ok = SaveGuard.run("업체 소개", _toast) {
                container.preferences.faqSlogan = s.slogan.trim()
                container.preferences.faqIntro = s.intro.trim()
                container.preferences.faqCareer = s.career.trim()
                container.preferences.faqCount = s.count.trim()
                container.preferences.faqCoverPhotos = s.photos
                container.preferences.faqCoverCaptions = s.captions.map { it.trim() }
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
        initial.getOrNull(4)?.split("|")?.filter { it.isNotBlank() } ?: emptyList()

    override fun onCleared() {
        freshCopies.toList().forEach { runCatching { TemplatePhotoStore.fileFor(container.appContext, it)?.delete() } }
        super.onCleared()
    }
}
