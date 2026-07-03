package com.detailline.callfollowcrm.presentation.screen.template

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
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

    /** 아직 DB 에 저장 안 된 첨부. 새 템플릿 작성 중 또는 기존 템플릿에 추가 중. */
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
                container.messageTemplateRepository.findById(it)?.let { t ->
                    _state.value = TemplateEditUiState(t.title, t.body, t.isActive, t.isDefault, t.category)
                }
            }
        }
    }

    // 제목 13자 제한 — 길면 앱 UI 에서 잘림(SYNC 추가81b, 2026-07-03 사장님 보고). 서버/휴리스틱도 13자 컷.
    fun setTitle(v: String) = _state.update { it.copy(title = v.take(13)) }
    fun setBody(v: String) = _state.update { it.copy(body = v) }
    fun setActive(v: Boolean) = _state.update { it.copy(isActive = v) }

    /**
     * 갤러리/사진 picker 로 선택한 URI 를 첨부에 추가.
     * persistable URI 권한을 즉시 잡아 두어야 다음에 앱 켜도 접근 가능.
     */
    fun addAttachment(context: Context, uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        val (name, mime) = queryFileInfo(context, uri)
        _pending.update { current ->
            // 중복 추가 방지
            if (current.any { it.uri == uri.toString() }) current
            else current + PendingAttachment(uri = uri.toString(), displayName = name, mimeType = mime)
        }
    }

    fun removePendingAttachment(uri: String) {
        _pending.update { it.filterNot { p -> p.uri == uri } }
    }

    fun removeSavedAttachment(context: Context, entity: TemplateAttachmentEntity) {
        viewModelScope.launch {
            container.templateAttachmentRepository.remove(entity.id)
            runCatching {
                context.contentResolver.releasePersistableUriPermission(
                    Uri.parse(entity.fileUri),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
        }
    }

    fun save(onDone: () -> Unit) {
        val s = _state.value
        if (s.title.isBlank() || s.body.isBlank()) return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val effectiveTemplateId: Long = if (templateId == null) {
                container.messageTemplateRepository.insert(
                    MessageTemplateEntity(
                        title = s.title, body = s.body, category = s.category,
                        isDefault = false, isActive = s.isActive,
                        createdAt = now, updatedAt = now
                    )
                )
            } else {
                container.messageTemplateRepository.findById(templateId)?.let { existing ->
                    container.messageTemplateRepository.update(
                        existing.copy(title = s.title, body = s.body, isActive = s.isActive)
                    )
                }
                templateId
            }

            // pending 첨부를 일괄 저장
            _pending.value.forEach { p ->
                container.templateAttachmentRepository.add(
                    templateId = effectiveTemplateId,
                    fileUri = p.uri,
                    displayName = p.displayName,
                    mimeType = p.mimeType
                )
            }
            _pending.value = emptyList()
            onDone()
        }
    }

    private fun queryFileInfo(context: Context, uri: Uri): Pair<String, String> {
        val mime = context.contentResolver.getType(uri) ?: "image/*"
        val name = runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null, null, null
            )?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment.orEmpty()
        return name to mime
    }
}

data class TemplateEditUiState(
    val title: String,
    val body: String,
    val isActive: Boolean,
    val isDefault: Boolean,
    val category: String
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
