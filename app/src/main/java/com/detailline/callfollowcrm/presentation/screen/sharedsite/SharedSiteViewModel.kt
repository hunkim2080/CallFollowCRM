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

    /** 내가(A) 공유한 현장 — 서버 by-me. "누구랑"(partnerName)·진행상태 포함. 서버 미구현이면 빈 목록. (2026-06-18 사장님) */
    private val _mySharedSites = MutableStateFlow<List<SharedSiteRepository.SharedSite>>(emptyList())
    val mySharedSites = _mySharedSites.asStateFlow()

    /** 업체별(§B) 서버 집계 — 비어 있으면 화면이 로컬 그룹핑으로 폴백. */
    private val _partners = MutableStateFlow<List<SharedSiteRepository.Partner>>(emptyList())
    val partners = _partners.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()

    /** 현재 연 협업 현장의 증거 사진(§F). 상세 열 때 loadPhotos. */
    private val _photos = MutableStateFlow<List<SharedSiteRepository.SharedPhoto>>(emptyList())
    val photos = _photos.asStateFlow()
    private val _photoBusy = MutableStateFlow(false)
    val photoBusy = _photoBusy.asStateFlow()

    /** 현재 연 협업 현장의 한 줄 댓글(협업 사장끼리 논의). 상세 열 때 loadComments. (2026-07-01 사장님) */
    private val _comments = MutableStateFlow<List<SharedSiteRepository.SiteComment>>(emptyList())
    val comments = _comments.asStateFlow()
    private val _commentBusy = MutableStateFlow(false)
    val commentBusy = _commentBusy.asStateFlow()

    /** 내 사장 번호(숫자만) — 댓글에서 나/상대 구분용. */
    val myPhoneDigits: String get() = myPhone

    /** 휴지통에 넣은 협업 현장 share_id 들(목록에서 제외, 휴지통에 보관·복구 가능). */
    private val _trashed = MutableStateFlow(container.preferences.trashedSharedSiteIds)
    val trashed = _trashed.asStateFlow()

    fun trash(shareId: String) {
        if (shareId.isBlank()) return
        container.preferences.trashedSharedSiteIds = container.preferences.trashedSharedSiteIds + shareId
        _trashed.value = container.preferences.trashedSharedSiteIds
        // 홈/상담함 협업 카드에서도 즉시 제거 (다음 폴 안 기다림). (2026-06-17 사장님)
        container.collabEventCenter.markTrashed(shareId)
        // 휴지통/그만하기로 뺀 현장은 위치 펜스도 정리 — 출발만 누르고 안 가도 남던 펜스가
        //   나중에 엉뚱한 '거의 도착' 자동 알림을 쏘는 걸 막는다(배터리·오알림 방지). (2026-06-20 사장님)
        runCatching { com.detailline.callfollowcrm.service.GeofenceManager.removeCollabArrival(container.appContext, shareId) }
    }

    fun restore(shareId: String) {
        container.preferences.trashedSharedSiteIds = container.preferences.trashedSharedSiteIds - shareId
        _trashed.value = container.preferences.trashedSharedSiteIds
    }

    /** B(협업자)가 협업 그만하기 — 서버 end(by partner) → A 에게 알림 + 기록 보존. 로컬에선 즉시 숨김(서버가 declined 처리). best-effort. */
    fun leaveCollab(site: SharedSiteRepository.SharedSite) {
        trash(site.shareId) // 즉시 목록에서 빠짐
        viewModelScope.launch {
            // 서버 결과 확인 후 토스트 — 실패해도 "알려드렸어요"라 하던 거짓 안심 제거. (2026-07-30)
            val ok = repo.endCollab(site.shareId, myPhone, asOwner = false).isSuccess
            _toast.value = if (ok) "협업을 그만뒀어요 — 사장님께 알려드렸어요"
                else "지금 연결이 안 돼 사장님께 알림을 못 보냈어요 — 잠시 후 다시 시도해주세요"
            // 그만둔 현장은 내 일정이 아니다 → 본폰 미러에서도 즉시 내림(안 그러면 ~3h 동안 남아있음).
            runCatching { container.mirrorSyncManager.pushNow(force = true) }
        }
    }

    /** A(주인)가 '내가 공유한 현장'을 내림/삭제 — 수락 전이면 조용히 취소, 수락 후면 상대에 '해제' 알림 + 기록 보존. (2026-06-23 사장님) */
    fun cancelMyShared(site: SharedSiteRepository.SharedSite) {
        _mySharedSites.value = _mySharedSites.value.filterNot { it.shareId == site.shareId }  // 즉시 목록에서 빠짐
        viewModelScope.launch {
            val ok = repo.endCollab(site.shareId, myPhone, asOwner = true).isSuccess
            container.collabEventCenter.markTrashed(site.shareId)  // 일정/홈 협업 카드에서도 즉시 정리
            _toast.value = when {
                !ok -> "지금 연결이 안 돼 처리를 못 했어요 — 잠시 후 다시 시도해주세요"
                site.status == "pending" -> "공유를 취소했어요"
                else -> "${site.partnerName ?: "상대"} 사장님께 해제 알림을 보냈어요"
            }
            load()
        }
    }

    /** true = 사업자 전화 미등록 → 협업 받을 수 없음(안내). */
    val noBizPhone: Boolean get() = myPhone.length < 9

    private val _toast = MutableStateFlow<String?>(null)
    val toast = _toast.asStateFlow()
    fun consumeToast() { _toast.value = null }

    fun load() {
        if (noBizPhone) return
        _loading.value = true
        viewModelScope.launch {
            val list = repo.withMe(myPhone).getOrDefault(emptyList())
            _sites.value = list
            // 수락 유효시간(12h) 앵커: 화면을 직접 열어 본 시각도 기록(폴링 전이라도) — 서버 created_at_ms 폴백.
            container.preferences.syncCollabInviteFirstSeen(
                list.filter { it.status == "pending" }.map { it.shareId }.toSet(),
                System.currentTimeMillis()
            )
            // 업체별 집계(§B) — 서버 없거나 실패하면 빈 목록 → 화면이 로컬 그룹핑으로 폴백.
            _partners.value = repo.partners(myPhone).getOrDefault(emptyList())
            // 내가 공유한 현장(by-me) — 거절/종료 제외는 화면에서. 서버 미구현이면 빈 목록(graceful).
            _mySharedSites.value = repo.byMe(myPhone).getOrDefault(emptyList())
            _loading.value = false
        }
    }

    /**
     * 받은 협업 요청의 수락 유효시간(12h)이 지났는지. true 면 더는 수락 불가(화면이 "지났어요" 안내).
     *   앵커 = 서버 created_at_ms(>0) 우선, 없으면 로컬 첫 관측 시각. 둘 다 0이면 만료 처리 안 함(안전).
     */
    fun acceptExpired(site: SharedSiteRepository.SharedSite): Boolean {
        if (site.status != "pending") return false
        val anchor = site.createdAtMs.takeIf { it > 0L }
            ?: container.preferences.collabInviteFirstSeenMs(site.shareId)
        if (anchor <= 0L) return false
        return System.currentTimeMillis() - anchor >= ACCEPT_VALID_MS
    }

    /**
     * 받은 협업 요청이 48h 지나 inbox 에서 아예 사라져야 하는지 (2026-07-09 사장님 "48시간 되면 이 카드 없어지는거지").
     *   앵커는 acceptExpired 와 동일(서버 created_at_ms 우선, 없으면 첫 관측). 앵커 0이면 숨기지 않음(안전).
     *   화면(pendingSites)에서 이걸로 걸러 카드 자체를 제거. (12~48h 사이는 "지났어요" 상태로 계속 보임.)
     */
    fun requestFullyExpired(site: SharedSiteRepository.SharedSite): Boolean {
        if (site.status != "pending") return false
        val anchor = site.createdAtMs.takeIf { it > 0L }
            ?: container.preferences.collabInviteFirstSeenMs(site.shareId)
        if (anchor <= 0L) return false
        return System.currentTimeMillis() - anchor >= REQUEST_EXPIRE_MS
    }

    /** 내 사업자명(상호) → A 화면 "OO 사장님" 표기용. 없으면 대표자 이름. (2026-06-14) */
    private fun myBizName(): String =
        container.preferences.bizName.takeIf { it.isNotBlank() }
            ?: container.preferences.bizOwner

    fun respond(site: SharedSiteRepository.SharedSite, accept: Boolean, reason: String? = null) {
        viewModelScope.launch {
            repo.respond(site.shareId, myPhone, accept, partnerName = myBizName(), reason = reason)
                .onSuccess {
                    _toast.value = if (accept) "협업 현장에 들어왔어요" else "거절했어요"
                    // 수락/거절 즉시 상담함 카드·뱃지·알림에서 제거(다음 폴 안 기다리게). (2026-06-14 버그)
                    container.collabEventCenter.markResponded(container.appContext, site.shareId)
                    load()
                    // 본폰 미러도 즉시 갱신 — 수락한 협업 현장은 곧 내 일정이니까. (2026-07-15 사장님)
                    //   미러 자동전송은 '내 고객/현금 변경'만 구독해서 협업 수락엔 안 걸린다
                    //   → 안 찔러주면 ReminderWorker(~3h) 올 때까지 본폰에 안 뜸.
                    //   force=true: 해시가 같아도 무조건 보냄(협업만 바뀐 경우 skip 방지).
                    runCatching { container.mirrorSyncManager.pushNow(force = true) }
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
                } else null,
                partnerName = myBizName()
            )
            res.onSuccess {
                // 완료(COMPLETED) → 도착(ARRIVED) = 되돌리기. 서버도 is_revert 로 처리(재알림 X). (2026-06-21 cowork/사장님)
                val reverting = site.progress == SharedSiteRepository.Progress.COMPLETED &&
                    step == SharedSiteRepository.Progress.ARRIVED
                _toast.value = when {
                    reverting -> "완료를 해제했어요 — 다시 '도착' 상태로 돌렸어요"
                    step == SharedSiteRepository.Progress.DEPARTED -> "출발 알렸어요"
                    step == SharedSiteRepository.Progress.ARRIVED -> "도착 알렸어요"
                    step == SharedSiteRepository.Progress.COMPLETED -> "완료 알렸어요 — 주인 사장님께 계좌가 전달돼요"
                    else -> "알렸어요"
                }
                // 완료를 누른 본인(B) 폰에서도 '됐다' 확인음. (2026-07-16 사장님) 되돌리기(reverting)엔 안 울림.
                //   주인(A)은 서버 FCM 으로 완료 알림음이 따로 울림(별개 경로) — 여긴 B의 로컬 동작 피드백.
                if (step == SharedSiteRepository.Progress.COMPLETED && !reverting) {
                    com.detailline.callfollowcrm.util.LocalCue.play(
                        container.appContext,
                        com.detailline.callfollowcrm.R.raw.sound_collab_completed
                    )
                }
                load()
                // 미러에 협업 현장의 '완료' 표시가 나가므로 완료/되돌리기도 즉시 반영. (수락과 같은 이유)
                if (step == SharedSiteRepository.Progress.COMPLETED || reverting) {
                    runCatching { container.mirrorSyncManager.pushNow(force = true) }
                }
            }.onFailure { _toast.value = "전송 실패 — 잠시 후 다시" }
        }
    }

    /** 완료 알리기 전, 입금 계좌가 등록돼 있는지. 없으면 화면이 등록 유도. */
    fun hasAccount(): Boolean = container.preferences.bizAccountNo.isNotBlank()

    /** 일당 지급(입금) 계좌 — 협업 화면 인라인 확인/등록·수정용. (2026-06-14 사장님) */
    val accountBank: String get() = container.preferences.bizBank
    val accountNo: String get() = container.preferences.bizAccountNo
    val accountHolder: String get() = container.preferences.bizAccountHolder
    fun saveAccount(bank: String, no: String, holder: String) {
        container.preferences.bizBank = bank.trim()
        container.preferences.bizAccountNo = no.trim()
        container.preferences.bizAccountHolder = holder.trim()
    }

    /** 길찾기 기본 지도앱 — 비면 처음 길찾기 때 선택받음. (2026-06-14) */
    fun navApp(): String = container.preferences.navApp
    fun setNavApp(app: String) { container.preferences.navApp = app }

    /** 상세 열 때 그 현장의 증거 사진 로드(§F). 다른 현장으로 바뀌면 비움. */
    fun loadPhotos(shareId: String) {
        if (shareId.isBlank() || noBizPhone) { _photos.value = emptyList(); return }
        viewModelScope.launch { _photos.value = repo.photos(shareId, myPhone).getOrDefault(emptyList()) }
    }

    /** 상세 열 때 그 현장의 한 줄 댓글 로드. 다른 현장으로 바뀌면 비움. */
    fun loadComments(shareId: String) {
        if (shareId.isBlank() || noBizPhone) { _comments.value = emptyList(); return }
        viewModelScope.launch { _comments.value = repo.comments(shareId, myPhone).getOrDefault(emptyList()) }
    }

    /** 한 줄 댓글 작성 → 목록 새로고침. 빈 글/미등록 번호면 무시. */
    fun postComment(shareId: String, body: String) {
        val text = body.trim()
        if (shareId.isBlank() || noBizPhone || text.isBlank()) return
        _commentBusy.value = true
        viewModelScope.launch {
            repo.postComment(shareId, myPhone, myBizName(), text)
                .onSuccess { _comments.value = repo.comments(shareId, myPhone).getOrDefault(_comments.value) }
                .onFailure { _toast.value = "댓글을 못 보냈어요 — 잠시 후 다시" }
            _commentBusy.value = false
        }
    }

    /** 증거 사진 업로드(§F) — base64 는 화면에서 변환해 넘김(VM 은 Context 없음). */
    fun uploadPhotoBase64(shareId: String, base64: String) {
        if (shareId.isBlank() || noBizPhone || base64.isBlank()) return
        _photoBusy.value = true
        viewModelScope.launch {
            repo.uploadPhoto(shareId, myPhone, base64)
                .onSuccess {
                    _toast.value = "현장 사진을 올렸어요"
                    _photos.value = repo.photos(shareId, myPhone).getOrDefault(_photos.value)
                }
                .onFailure { _toast.value = "사진 업로드 실패 — 잠시 후 다시" }
            _photoBusy.value = false
        }
    }

    /** 증거 사진 삭제 — 올린 본인만(서버가 uploader 검증). 성공 시 목록 새로고침. (2026-07-07 사장님) */
    fun deletePhoto(shareId: String, photoId: Long) {
        if (shareId.isBlank() || noBizPhone || photoId <= 0L) return
        viewModelScope.launch {
            repo.deletePhoto(shareId, photoId, myPhone)
                .onSuccess {
                    _toast.value = "사진을 삭제했어요"
                    _photos.value = repo.photos(shareId, myPhone).getOrDefault(_photos.value)
                }
                .onFailure { _toast.value = "사진 삭제 실패 — 잠시 후 다시" }
        }
    }

    companion object {
        /** 협업 요청 수락 유효시간 — 12시간. 그 이후엔 수락 불가("수락 시간이 지났어요"). (2026-06-14 사장님) */
        const val ACCEPT_VALID_MS = 12L * 60 * 60 * 1000
        /** 받은 협업 요청 완전 만료 — 48시간. 그 이후엔 inbox 에서 카드 자체가 사라짐. (2026-07-09 사장님) */
        const val REQUEST_EXPIRE_MS = 48L * 60 * 60 * 1000
    }
}
