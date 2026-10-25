package com.detailline.callfollowcrm.presentation.screen.customer

import com.detailline.callfollowcrm.data.AppContainer
import com.detailline.callfollowcrm.data.local.entity.SitePhotoEntity
import com.detailline.callfollowcrm.data.repository.SitePhotoRepository.MarkResult
import com.detailline.callfollowcrm.domain.ui.MarkStroke
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * ✏️🔙 **현장 사진에 펜 표시 → 그걸로 바꾸고 갤러리 저장, 그리고 되돌리기.** (2026-10-25 사장님 "현장사진펜 되돌리기 있어야해")
 *
 *   사장님: 크게 보기에서 펜으로 그리고 완료하면 **그 사진이 현장 사진으로 갈아끼워지고** 갤러리에도 남는다.
 *   진짜 원본은 갤러리에 있고, 앱 안에도 **.orig 로 백업** → 잘못 그렸으면 **되돌리기**로 원본 복구.
 *
 *   · 바꾸기([save]) — 합성·교체·갤러리 저장은 `SitePhotoRepository.markReplaceAndSave` 한 곳(§12).
 *     성공하면 [markedUndo] 에 바뀐 사진 id 를 담아 화면이 되돌리기 스낵바를 띄우게 한다.
 *   · 되돌리기([undo]) — .orig 로 복구(`SitePhotoRepository.restoreOriginal`). 사장님이 누른 저장이라 결과를 화면에 말한다(§13①).
 *
 *   CustomerDetailViewModel 이 들고 부른다 — 화면 VM 이 커지지 않게 여기로 뗐다(size_guard, [CustomerHoldFlow] 와 같은 길).
 */
internal class CustomerPhotoMarkFlow(
    private val container: AppContainer,
    private val toast: MutableStateFlow<String?>,
    private val sitePhotosNow: () -> List<SitePhotoEntity>
) {
    // 🔙 펜으로 바꾼 뒤 "되돌리기" — 교체된 사진 id. 화면이 이걸 보고 되돌리기 스낵바를 띄운다.
    private val _markedUndo = MutableStateFlow<Long?>(null)
    val markedUndo = _markedUndo.asStateFlow()
    fun clearUndo() { _markedUndo.value = null }

    /** 펜 그린 걸로 그 현장 사진을 바꾸고 갤러리에도 저장. 성공하면 [markedUndo] 에 담아 되돌리기 스낵바를 띄우게 한다. */
    suspend fun save(sourcePhotoId: Long, marks: List<MarkStroke>) {
        if (marks.isEmpty()) return
        val src = sitePhotosNow().firstOrNull { it.id == sourcePhotoId }
        if (src == null) { toast.value = "원본 사진을 찾지 못했어요"; return }
        toast.value = "표시한 사진 저장 중…"
        when (withContext(NonCancellable) {
            container.sitePhotoRepository.markReplaceAndSave(sourcePhotoId, src.filePath, marks)
        }) {
            MarkResult.NO_RENDER -> toast.value = "사진을 준비하지 못했어요"
            MarkResult.REPLACE_FAIL -> toast.value = "현장 사진을 바꾸지 못했어요"
            // 교체 성공(갤러리 실패여도 교체는 됨) → 화면이 "표시한 사진으로 바꿨어요 · 되돌리기" 스낵바를 띄운다.
            MarkResult.GALLERY_FAIL, MarkResult.OK -> _markedUndo.value = sourcePhotoId
        }
    }

    /** 🔙 되돌리기 — 원본(.orig)으로 복구. 되돌리기 스낵바에서 부른다. */
    suspend fun undo(photoId: Long) {
        val ok = withContext(NonCancellable) { container.sitePhotoRepository.restoreOriginal(photoId) }
        toast.value = if (ok) "원본으로 되돌렸어요" else "되돌리지 못했어요"
    }
}
