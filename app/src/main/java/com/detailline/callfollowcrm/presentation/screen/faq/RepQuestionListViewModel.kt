package com.detailline.callfollowcrm.presentation.screen.faq

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

    // 🗣 순서 바꾸기·지우기가 실패하면 말하는 통로.
    private val _toast = MutableStateFlow<String?>(null)
    val toast = _toast.asStateFlow()
    fun consumeToast() { _toast.value = null }

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
