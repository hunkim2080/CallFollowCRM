package com.detailline.callfollowcrm.presentation.screen.faq

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.detailline.callfollowcrm.data.AppContainer
import com.detailline.callfollowcrm.domain.faq.RepQuestionRules
import com.detailline.callfollowcrm.presentation.util.SaveGuard
import com.detailline.callfollowcrm.util.TemplatePhotoStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 💬 대표질문 등록·수정 폼. (프로토 EYKUs11cVT7JW71yVrVoBA ③)
 *
 *   [questionId] 가 null 이면 새로 등록, 있으면 그 줄을 고친다.
 *   사진은 고르는 즉시 **앱 안으로 복사**한다(`TemplatePhotoStore` — 문구 사진과 같은 창고).
 *   갤러리 주소를 그대로 두면 며칠 뒤 권한이 풀려 답장에 사진이 안 딸려온다.
 */
class RepQuestionEditViewModel(
    private val container: AppContainer,
    private val questionId: Long?
) : ViewModel() {

    data class UiState(
        val question: String = "",
        val answer: String = "",
        val imageUri: String? = null,
        /** 고치기일 때 원래 글을 다 읽었나. 읽기 전엔 입력을 막는다(읽힌 값이 쓰던 글을 덮지 않게). */
        val loaded: Boolean = false,
        val saving: Boolean = false,
        val pickingImage: Boolean = false,
        /** 고칠 줄이 이미 지워졌다 — 화면이 말하고 닫는다. */
        val missing: Boolean = false
    ) {
        val canSave: Boolean get() = loaded && !saving && !pickingImage && RepQuestionRules.canSave(question, answer)
    }

    val isEdit: Boolean get() = questionId != null

    private val _state = MutableStateFlow(UiState(loaded = questionId == null))
    val state = _state.asStateFlow()

    // 🗣 저장·사진 복사가 실패하면 말하는 통로.
    private val _toast = MutableStateFlow<String?>(null)
    val toast = _toast.asStateFlow()
    fun consumeToast() { _toast.value = null }

    /** 처음 모습 — 「저장 안 하고 나갈까요?」 판단용. */
    private var initial = Triple("", "", null as String?)

    /** DB 에 저장돼 있던 사진. 저장 전까지는 지우지 않는다(취소하면 그대로 남아야 한다). */
    private var savedImage: String? = null

    /** 이번에 새로 복사한 사진들 — 저장 안 된 건 나갈 때 치운다. */
    private val freshCopies = mutableListOf<String>()

    init {
        if (questionId != null) {
            viewModelScope.launch {
                val row = try {
                    container.repQuestionRepository.findById(questionId)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    android.util.Log.e("RepQuestion", "load failed", e); null
                }
                if (row == null) {
                    _toast.value = "대표질문을 불러오지 못했어요"
                    _state.update { it.copy(missing = true) }
                } else {
                    savedImage = row.imageUri
                    initial = Triple(row.question, row.answer, row.imageUri)
                    _state.update { it.copy(question = row.question, answer = row.answer, imageUri = row.imageUri, loaded = true) }
                }
            }
        }
    }

    val isDirty: Boolean
        get() = _state.value.let { Triple(it.question, it.answer, it.imageUri) != initial }

    fun setQuestion(v: String) = _state.update { it.copy(question = RepQuestionRules.clampQuestion(v)) }
    fun setAnswer(v: String) = _state.update { it.copy(answer = RepQuestionRules.clampAnswer(v)) }

    /** 고른 사진을 앱 안으로 복사해 붙인다(1장). 실패하면 **말한다** — 조용히 빈칸으로 두지 않는다. */
    fun pickImage(src: Uri) {
        if (_state.value.pickingImage) return
        _state.update { it.copy(pickingImage = true) }
        viewModelScope.launch {
            val copied = withContext(Dispatchers.IO) {
                try {
                    TemplatePhotoStore.copyToAppStorage(container.appContext, src)
                } catch (e: Throwable) {
                    android.util.Log.e("RepQuestion", "image copy failed", e); null
                }
            }
            if (copied == null) {
                _toast.value = "사진을 불러오지 못했어요 — 다시 골라주세요"
                _state.update { it.copy(pickingImage = false) }
            } else {
                val before = _state.value.imageUri
                freshCopies += copied.uri
                _state.update { it.copy(imageUri = copied.uri, pickingImage = false) }
                // 방금 전에 골랐다가 바꾼 사진(아직 저장 안 된 것)은 바로 치운다.
                if (before != null && before != savedImage) dropFresh(before)
            }
        }
    }

    fun removeImage() {
        val before = _state.value.imageUri ?: return
        _state.update { it.copy(imageUri = null) }
        if (before != savedImage) dropFresh(before)
    }

    private fun dropFresh(uri: String) {
        freshCopies -= uri
        container.repQuestionRepository.discardImage(uri)
    }

    /** 등록/수정. 연타해도 한 번만 — [UiState.saving] 이 켜진 동안은 무시한다. */
    fun save(onDone: () -> Unit) {
        val s = _state.value
        if (!s.canSave) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            // 🗣 사장님이 **쓴 질문·답** — 조용히 사라지면 다시 써야 한다. 실패하면 말하고, 쓴 글은 화면에 그대로 둔다.
            val ok = SaveGuard.run("대표질문", _toast) {
                if (questionId == null) container.repQuestionRepository.add(s.question, s.answer, s.imageUri)
                else container.repQuestionRepository.edit(questionId, s.question, s.answer, s.imageUri)
                true
            }
            if (ok == true) {
                freshCopies -= s.imageUri.orEmpty()
                onDone()
            } else {
                _state.update { it.copy(saving = false) }
            }
        }
    }

    /** 이 줄을 지운다(수정 화면의 「삭제」). */
    fun delete(onDone: () -> Unit) {
        val id = questionId ?: return
        if (_state.value.saving) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                container.repQuestionRepository.findById(id)?.let { container.repQuestionRepository.delete(it) }
                onDone()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                android.util.Log.e("RepQuestion", "delete failed", e)
                _toast.value = "대표질문을 지우지 못했어요 — 다시 한 번 눌러주세요"
                _state.update { it.copy(saving = false) }
            }
        }
    }

    override fun onCleared() {
        // 저장 안 하고 나갔으면 이번에 복사한 사진은 쓸 데가 없다 — 치운다(지워도 되는 것).
        freshCopies.toList().forEach { container.repQuestionRepository.discardImage(it) }
        super.onCleared()
    }
}
