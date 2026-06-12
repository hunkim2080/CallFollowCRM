package com.detailline.callfollowcrm.presentation.screen.sharedsite

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.detailline.callfollowcrm.ai.SharedSiteRepository
import com.detailline.callfollowcrm.data.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 협업 현장(B = 협업자) 화면 ViewModel.
 *   내(bizPhone)가 공유받은 현장 목록을 서버에서 가져오고, 수락/거절·진행(출발/도착/완료)을 보냄.
 *   완료 시 [AppPreferences] 의 입금 계좌를 같이 실어 보냄 → 주인 사장에게 계좌 전달.
 *
 * 서버 endpoint 미구현 동안엔 withMe 가 빈 목록 → 화면은 "공유받은 현장 없음" 안내.
 * (docs/SERVER_HANDOFF_collab_sites.md)
 */
class SharedSiteViewModel(private val container: AppContainer) : ViewModel() {

    private val repo: SharedSiteRepository = container.sharedSiteRepository
    private val myPhone: String = container.preferences.bizPhone.filter { it.isDigit() }

    private val _sites = MutableStateFlow<List<SharedSiteRepository.SharedSite>>(emptyList())
    val sites = _sites.asStateFlow()

    /** 업체별(§B) 서버 집계 — 비어 있으면 화면이 로컬 그룹핑으로 폴백. */
    private val _partners = MutableStateFlow<List<SharedSiteRepository.Partner>>(emptyList())
    val partners = _partners.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()

    /** true = 사업자 전화 미등록 → 협업 받을 수 없음(안내). */
    val noBizPhone: Boolean get() = myPhone.length < 9

    private val _toast = MutableStateFlow<String?>(null)
    val toast = _toast.asStateFlow()
    fun consumeToast() { _toast.value = null }

    fun load() {
        if (noBizPhone) return
        _loading.value = true
        viewModelScope.launch {
            _sites.value = repo.withMe(myPhone).getOrDefault(emptyList())
            // 업체별 집계(§B) — 서버 없거나 실패하면 빈 목록 → 화면이 로컬 그룹핑으로 폴백.
            _partners.value = repo.partners(myPhone).getOrDefault(emptyList())
            _loading.value = false
        }
    }

    fun respond(site: SharedSiteRepository.SharedSite, accept: Boolean) {
        viewModelScope.launch {
            repo.respond(site.shareId, myPhone, accept)
                .onSuccess {
                    _toast.value = if (accept) "협업 현장에 들어왔어요" else "거절했어요"
                    load()
                }
                .onFailure { _toast.value = "처리 못했어요 — 잠시 후 다시" }
        }
    }

    fun updateProgress(site: SharedSiteRepository.SharedSite, step: SharedSiteRepository.Progress) {
        viewModelScope.launch {
            val withAccount = step == SharedSiteRepository.Progress.COMPLETED
            val res = repo.progress(
                shareId = site.shareId,
                partnerPhone = myPhone,
                step = step,
                bank = if (withAccount) container.preferences.bizBank.takeIf { it.isNotBlank() } else null,
                accountNo = if (withAccount) container.preferences.bizAccountNo.takeIf { it.isNotBlank() } else null,
                holder = if (withAccount) {
                    container.preferences.bizAccountHolder.takeIf { it.isNotBlank() }
                        ?: container.preferences.bizOwner.takeIf { it.isNotBlank() }
                } else null
            )
            res.onSuccess {
                _toast.value = when (step) {
                    SharedSiteRepository.Progress.DEPARTED -> "출발 알렸어요"
                    SharedSiteRepository.Progress.ARRIVED -> "도착 알렸어요"
                    SharedSiteRepository.Progress.COMPLETED -> "완료 알렸어요 — 주인 사장님께 계좌가 전달돼요"
                    else -> "알렸어요"
                }
                load()
            }.onFailure { _toast.value = "전송 실패 — 잠시 후 다시" }
        }
    }

    /** 완료 알리기 전, 입금 계좌가 등록돼 있는지. 없으면 화면이 등록 유도. */
    fun hasAccount(): Boolean = container.preferences.bizAccountNo.isNotBlank()
}
