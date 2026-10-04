package com.detailline.callfollowcrm.presentation.screen.outbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.detailline.callfollowcrm.data.AppContainer
import com.detailline.callfollowcrm.data.local.entity.OutboxEntity
import com.detailline.callfollowcrm.domain.outbox.OutboxKind
import com.detailline.callfollowcrm.domain.outbox.OutboxRules
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 📮 「아직 못 보낸 것」 목록 화면. 우체통의 **말썽 난 것만**(dead + 하루 넘긴 pending) 보여준다.
 *   다시 보내기·그만 보내기. 설계 docs/DESIGN_offline_outbox.md §7.
 */
class OutboxTroubleViewModel(private val container: AppContainer) : ViewModel() {

    val items: StateFlow<List<OutboxEntity>> =
        container.outbox.observeTrouble()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 죽었든 미뤘든 지금 바로 다시 시도. */
    fun retry(id: Long) = viewModelScope.launch { container.outbox.retry(id) }

    /** 그만 보내기 — 우체통에서 뺀다. 사진이면 다음 스캔에 또 뜨지 않게 도장(-1)도 찍는다. */
    fun dismiss(item: OutboxEntity) = viewModelScope.launch {
        if (item.kind == OutboxKind.SITE_PHOTO.wire) {
            item.targetKey.toLongOrNull()?.let {
                runCatching { container.sitePhotoRepository.markGivenUp(it) }
            }
        }
        container.outbox.dismiss(item.id)
    }

    /** 이 줄이 「죽은 것」(서버 거절)인가 — 버튼을 보일지 가른다. */
    fun isDead(item: OutboxEntity) = item.status == OutboxEntity.STATUS_DEAD

    fun kindOf(item: OutboxEntity): OutboxKind? = OutboxKind.fromWire(item.kind)
    fun daysStuck(item: OutboxEntity, now: Long) = OutboxRules.daysStuck(item.createdAtMs, now)
}
