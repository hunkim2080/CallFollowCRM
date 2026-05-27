package com.detailline.callfollowcrm.presentation.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.detailline.callfollowcrm.data.AppContainer
import com.detailline.callfollowcrm.data.local.entity.MessageTemplateEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    // v1: 설정은 영속화 단순. 토글 상태는 AppPreferences 에서 즉시 읽고 쓰기.
    private val _state = MutableStateFlow(
        SettingsUiState(
            afterCallBehavior = AfterCallBehavior.NOTIFY,
            autoFirstReplyEnabled = container.preferences.autoFirstReplyEnabled,
            firstReplyIncomingTemplateId = container.preferences.firstReplyIncomingTemplateId,
            firstReplyMissedTemplateId = container.preferences.firstReplyMissedTemplateId,
            quickActionTemplateId1 = container.preferences.quickActionTemplateId1,
            quickActionTemplateId2 = container.preferences.quickActionTemplateId2,
            quickActionTemplateId3 = container.preferences.quickActionTemplateId3,
            incomingSmsNotifyEnabled = container.preferences.incomingSmsNotifyEnabled,
            defaultNavAppKey = container.preferences.defaultNavAppKey
        )
    )
    val state = _state.asStateFlow()

    /** 자동응답 템플릿 드롭다운에 보여줄 목록 (활성 템플릿만). */
    val templates = container.messageTemplateRepository.observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList<MessageTemplateEntity>())

    /** AI 서버 살아있음 표시 (●). null=아직 모름, true=정상, false=죽음. */
    val serverAlive: StateFlow<Boolean?> = container.serverHealth.alive

    /** 사장님 톤 학습용 보낸 SMS 샘플 개수. 설정 진입 시 한 번 계산. */
    private val _ownerToneSampleCount = MutableStateFlow(0)
    val ownerToneSampleCount: StateFlow<Int> = _ownerToneSampleCount.asStateFlow()

    /**
     * 토큰 사용량 통계 (2026-05-27). 서버 §12 endpoint 결과.
     *   null = 아직 fetch 안 함 / Result.failure = 서버 미구현/오류
     *   사장님이 새로고침 버튼 누르면 다시 fetch.
     */
    private val _usageStats = MutableStateFlow<Result<com.detailline.callfollowcrm.ai.UsageStatsRepository.UsageStats>?>(null)
    val usageStats: StateFlow<Result<com.detailline.callfollowcrm.ai.UsageStatsRepository.UsageStats>?> = _usageStats.asStateFlow()

    private val _usageLoading = MutableStateFlow(false)
    val usageLoading: StateFlow<Boolean> = _usageLoading.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val n = runCatching {
                container.smsRepository.querySentMessages(limit = 50).size
            }.getOrDefault(0)
            _ownerToneSampleCount.value = n
        }
        // 설정 진입 시 자동으로 한 번 fetch — 사장님이 토큰 상황 즉시 확인.
        loadUsageStats(com.detailline.callfollowcrm.ai.UsageStatsRepository.Period.TODAY)
    }

    fun loadUsageStats(period: com.detailline.callfollowcrm.ai.UsageStatsRepository.Period) {
        if (_usageLoading.value) return
        viewModelScope.launch(Dispatchers.IO) {
            _usageLoading.value = true
            _usageStats.value = container.usageStatsRepository.fetch(period)
            _usageLoading.value = false
        }
    }

    fun setBehavior(b: AfterCallBehavior) { _state.value = _state.value.copy(afterCallBehavior = b) }

    fun setAutoFirstReplyEnabled(enabled: Boolean) {
        container.preferences.autoFirstReplyEnabled = enabled
        _state.value = _state.value.copy(autoFirstReplyEnabled = enabled)
    }

    fun setIncomingTemplate(id: Long) {
        container.preferences.firstReplyIncomingTemplateId = id
        _state.value = _state.value.copy(firstReplyIncomingTemplateId = id)
    }

    fun setMissedTemplate(id: Long) {
        container.preferences.firstReplyMissedTemplateId = id
        _state.value = _state.value.copy(firstReplyMissedTemplateId = id)
    }

    fun setIncomingSmsNotifyEnabled(enabled: Boolean) {
        container.preferences.incomingSmsNotifyEnabled = enabled
        _state.value = _state.value.copy(incomingSmsNotifyEnabled = enabled)
    }

    /**
     * 카드 펼침 [📍 길찾기] 가 사용할 외부 네비 앱.
     * 사장님이 처음 길찾기 탭할 때 자동 다이얼로그 → 여기 저장 → 다음부터 1탭.
     * key 는 com.detailline.callfollowcrm.util.NavApp.key 문자열.
     */
    fun setDefaultNavApp(key: String?) {
        container.preferences.defaultNavAppKey = key
        _state.value = _state.value.copy(defaultNavAppKey = key)
    }

    fun setQuickAction(slot: Int, id: Long) {
        when (slot) {
            1 -> { container.preferences.quickActionTemplateId1 = id
                   _state.value = _state.value.copy(quickActionTemplateId1 = id) }
            2 -> { container.preferences.quickActionTemplateId2 = id
                   _state.value = _state.value.copy(quickActionTemplateId2 = id) }
            3 -> { container.preferences.quickActionTemplateId3 = id
                   _state.value = _state.value.copy(quickActionTemplateId3 = id) }
        }
    }
}

data class SettingsUiState(
    val afterCallBehavior: AfterCallBehavior,
    val autoFirstReplyEnabled: Boolean = false,
    val firstReplyIncomingTemplateId: Long = -1L,
    val firstReplyMissedTemplateId: Long = -1L,
    val quickActionTemplateId1: Long = -1L,
    val quickActionTemplateId2: Long = -1L,
    val quickActionTemplateId3: Long = -1L,
    val incomingSmsNotifyEnabled: Boolean = true,
    /** NavApp.key 문자열. null = 미선택 (첫 길찾기 탭 시 다이얼로그). */
    val defaultNavAppKey: String? = null
)

enum class AfterCallBehavior(val label: String) {
    NOTIFY("알림 표시"),
    NONE("아무것도 안 함")
}
