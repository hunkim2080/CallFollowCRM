package com.detailline.callfollowcrm.presentation.screen.template

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.room.withTransaction
import com.detailline.callfollowcrm.data.local.AppDatabase
import com.detailline.callfollowcrm.domain.faq.RepQuestionRules
import com.detailline.callfollowcrm.presentation.util.SaveGuard
import kotlinx.coroutines.flow.first
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.detailline.callfollowcrm.data.AppContainer
import com.detailline.callfollowcrm.data.local.entity.MessageTemplateEntity
import com.detailline.callfollowcrm.data.local.entity.TemplateAttachmentEntity
import com.detailline.callfollowcrm.domain.model.TemplateCategory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TemplateEditViewModel(
    private val container: AppContainer,
    private val templateId: Long?
) : ViewModel() {

    private val _state = MutableStateFlow(
        TemplateEditUiState(
            title = "",
            body = "",
            isActive = true,
            isDefault = false,
            category = TemplateCategory.CUSTOM.name
        )
    )
    val state = _state.asStateFlow()
    private var initialState = _state.value
    private val _loading = MutableStateFlow(templateId != null)
    val loading = _loading.asStateFlow()
    private val _saving = MutableStateFlow(false)
    val saving = _saving.asStateFlow()
    fun hasChanges() = _state.value != initialState || _pending.value.isNotEmpty()

    /** 아직 DB 에 저장 안 된 첨부. 새 템플릿 작성 중 또는 기존 템플릿에 추가 중. */
    // 🗣 사장님이 쓴 문구 저장이 실패하면 말하는 통로.
    private val _toast = MutableStateFlow<String?>(null)
    val toast = _toast.asStateFlow()
    fun consumeToast() { _toast.value = null }

    private val _pending = MutableStateFlow<List<PendingAttachment>>(emptyList())

    /** 이미 저장된 첨부(기존 템플릿 편집 시). 신규 작성이면 항상 빈 리스트. */
    private val savedFlow = if (templateId != null) {
        container.templateAttachmentRepository.observeByTemplate(templateId)
    } else flowOf(emptyList())

    /** UI 에 보여줄 첨부 리스트 (저장됨 + 추가 중). */
    val attachments = combine(savedFlow, _pending) { saved, pending ->
        saved.map { AttachmentItem.Saved(it) } + pending.map { AttachmentItem.Pending(it) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        templateId?.let {
            viewModelScope.launch {
                val loaded = SaveGuard.run("문구 불러오기", _toast) {
                    val t = checkNotNull(container.messageTemplateRepository.findById(it))
                    _state.value = TemplateEditUiState(t.title, t.body, t.isActive, t.isDefault, t.category, t.isPublic)
                    initialState = _state.value
                    true
                }
                if (loaded == true) _loading.value = false
            }
        }
    }

    // 공개 질문은 기존 FAQ 규칙 재사용. 이사한 긴 제목은 비공개여도 잘라 버리지 않는다.
    fun setTitle(v: String) = _state.update { it.copy(title = if (it.isPublic) RepQuestionRules.clampQuestion(v) else v) }
    fun setPublic(v: Boolean) = _state.update {
        it.copy(isPublic = v, category = if (v && it.category == TemplateCategory.CUSTOM.name) "" else it.category)
    }
    fun setCategory(v: String) = _state.update { it.copy(category = RepQuestionRules.clampCategory(v)) }
    fun setBody(v: String) = _state.update { it.copy(body = v) }
    fun setActive(v: Boolean) = _state.update { it.copy(isActive = v) }

    /**
     * 갤러리(PickVisualMedia)로 고른 사진을 첨부에 추가. (2026-07-17 사장님)
     *   ★ 예전엔 SAF(OpenDocument) URI 를 그대로 저장 → (a)파일 탐색기가 떠 갤러리 못 찾음 (b)나중에 그 문구 쓸 때
     *     권한이 풀려 사진이 안 딸려오던 문제. 이제 **앱 내부에 복사**해 몇 주 뒤에도 확실히 읽히게 한다.
     *   FileProvider(${applicationId}.fileprovider) URI 로 보관 → 발송(SmsSender.decodeMmsBitmap)·미리보기 모두 앱이 읽음.
     */
    fun addAttachment(context: Context, uri: Uri) {
        val appCtx = context.applicationContext
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            SaveGuard.run("사진", _toast) {
                val copied = checkNotNull(com.detailline.callfollowcrm.util.TemplatePhotoStore.copyToAppStorage(appCtx, uri))
                _pending.update { current ->
                    if (current.any { it.uri == copied.uri }) current
                    else current + PendingAttachment(copied.uri, copied.displayName, copied.mimeType)
                }
            }
        }
    }

    fun removePendingAttachment(uri: String) {
        _pending.update { it.filterNot { p -> p.uri == uri } }
        // 방금 복사한 앱 내부 파일도 정리(pending 은 항상 우리가 복사한 것).
        runCatching { com.detailline.callfollowcrm.util.TemplatePhotoStore.fileFor(container.appContext, uri)?.delete() }
    }

    fun removeSavedAttachment(context: Context, entity: TemplateAttachmentEntity) {
        viewModelScope.launch {
            SaveGuard.run("사진 삭제", _toast) {
                // 원본 대표질문이 참조하는 사진은 백업의 일부라 파일도 보존한다.
                val usedByBackup = container.repQuestionRepository.observeAll().first()
                    .any { it.imageUri == entity.fileUri }
                container.templateAttachmentRepository.remove(entity.id)
                if (!usedByBackup) {
                    runCatching { com.detailline.callfollowcrm.util.TemplatePhotoStore.fileFor(context, entity.fileUri)?.delete() }
                    runCatching {
                        context.contentResolver.releasePersistableUriPermission(
                            Uri.parse(entity.fileUri), Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    }
                }
            }
        }
    }

    fun save(onDone: () -> Unit) {
        val s = _state.value
        if (_saving.value || _loading.value || s.title.isBlank() || s.body.isBlank()) return
        _saving.value = true
        val pending = _pending.value
        viewModelScope.launch {
            try {
                val saved = SaveGuard.run("문구", _toast) {
                    AppDatabase.getInstance(container.appContext).withTransaction {
                        val now = System.currentTimeMillis()
                        val effectiveId = if (templateId == null) {
                            container.messageTemplateRepository.insert(
                                MessageTemplateEntity(
                                    title = s.title, body = s.body, category = s.category,
                                    isDefault = false, isActive = s.isActive, isPublic = s.isPublic,
                                    createdAt = now, updatedAt = now
                                )
                            )
                        } else {
                            val existing = checkNotNull(container.messageTemplateRepository.findById(templateId))
                            container.messageTemplateRepository.update(
                                existing.copy(title = s.title, body = s.body, category = s.category,
                                    isActive = s.isActive, isPublic = s.isPublic)
                            )
                            templateId
                        }
                        pending.forEach { p ->
                            container.templateAttachmentRepository.add(effectiveId, p.uri, p.displayName, p.mimeType)
                        }
                    }
                    true
                }
                if (saved == true) {
                    _pending.update { it - pending.toSet() }
                    onDone()
                }
            } finally { _saving.value = false }
        }
    }
}

data class TemplateEditUiState(
    val title: String,
    val body: String,
    val isActive: Boolean,
    val isDefault: Boolean,
    val category: String,
    val isPublic: Boolean = false
)

data class PendingAttachment(
    val uri: String,
    val displayName: String,
    val mimeType: String
)

sealed class AttachmentItem {
    abstract val uri: String
    abstract val displayName: String

    data class Saved(val entity: TemplateAttachmentEntity) : AttachmentItem() {
        override val uri: String get() = entity.fileUri
        override val displayName: String get() = entity.displayName
    }

    data class Pending(val pending: PendingAttachment) : AttachmentItem() {
        override val uri: String get() = pending.uri
        override val displayName: String get() = pending.displayName
    }
}
