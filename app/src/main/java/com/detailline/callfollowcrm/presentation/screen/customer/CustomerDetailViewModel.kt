package com.detailline.callfollowcrm.presentation.screen.customer

import com.detailline.callfollowcrm.presentation.util.SaveGuard
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.detailline.callfollowcrm.data.AppContainer
import com.detailline.callfollowcrm.data.local.entity.CallRecordEntity
import com.detailline.callfollowcrm.data.local.entity.CallSummaryEntity
import com.detailline.callfollowcrm.data.local.entity.CustomerEntity
import com.detailline.callfollowcrm.data.local.entity.MessageHistoryEntity
import com.detailline.callfollowcrm.data.local.entity.RecordingAttachmentEntity
import com.detailline.callfollowcrm.data.repository.SmsRepository
import com.detailline.callfollowcrm.domain.model.RecordingSourceType
import com.detailline.callfollowcrm.recording.AdotSummaryImporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.detailline.callfollowcrm.util.PhoneKey

@OptIn(ExperimentalCoroutinesApi::class)
class CustomerDetailViewModel(
    private val container: AppContainer,
    private val customerId: Long
) : ViewModel() {

    val customer = container.customerRepository.observeById(customerId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)


    // ── 📝 쪽지 메모 (2026-09-27 사장님) ──────────────────────────────
    /** 👤 이 손님 쪽지 — 📌 먼저, 그다음 최근 것부터. 차례는 DAO 한 곳에서만 정한다. */
    val notes = container.customerNoteRepository.observeForCustomer(customerId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun addNote(body: String) = viewModelScope.launch {
        container.customerNoteRepository.add(customerId, null, body)
    }

    /** 📍 그 **건**에서만 쓰는 쪽지. 건을 바꿔 보면 그 건 것만 보인다. */
    fun jobNotes(jobId: Long) = container.customerNoteRepository.observeForJob(jobId)

    fun addJobNote(jobId: Long, body: String) = viewModelScope.launch {
        container.customerNoteRepository.add(customerId, jobId, body)
    }

    fun editNote(id: Long, body: String) = viewModelScope.launch {
        container.customerNoteRepository.edit(id, body)
    }

    fun togglePinNote(id: Long) = viewModelScope.launch {
        container.customerNoteRepository.togglePin(id)
    }

    fun deleteNote(id: Long) = viewModelScope.launch {
        container.customerNoteRepository.remove(id)
    }

    /**
     * 🗑 취소/빈 건 완전 삭제 — 주소만 든 날짜 미정 건(문자 주소로 잘못 생긴 2차)도. (2026-10-06 · 2026-10-17 사장님)
     *   돈·날짜·받음 도장 있는 건은 repository 가 막는다(JobDeleteRule 한 곳).
     */
    fun deletePastJob(jobId: Long) = viewModelScope.launch {
        val ok = SaveGuard.run("건 삭제", _toast) {
            container.jobRepository.deleteJob(jobId, System.currentTimeMillis())
        }
        _toast.value = when (ok) {
            true -> "이 건을 지웠어요"
            false -> "금액·일정이 있는 건은 지울 수 없어요"
            else -> return@launch   // 저장 실패는 SaveGuard 가 이미 알림
        }
    }

    val callRecords: kotlinx.coroutines.flow.StateFlow<List<CallRecordEntity>> =
        container.customerRepository.observeById(customerId)
            .flatMapLatest { c ->
                if (c == null) emptyFlow() else container.callRecordRepository.observeByPhone(c.phoneNumber)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val messageHistory: kotlinx.coroutines.flow.StateFlow<List<MessageHistoryEntity>> =
        container.messageHistoryRepository.observeByCustomer(customerId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val recordings: kotlinx.coroutines.flow.StateFlow<List<RecordingAttachmentEntity>> =
        container.recordingRepository.observeByCustomer(customerId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val summaries: kotlinx.coroutines.flow.StateFlow<List<CallSummaryEntity>> =
        container.callSummaryRepository.observeByCustomer(customerId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // 현장 사진(로컬) — 이 고객 현장에 올린 사진. 2026-06-04.
    val sitePhotos: kotlinx.coroutines.flow.StateFlow<List<com.detailline.callfollowcrm.data.local.entity.SitePhotoEntity>> =
        container.sitePhotoRepository.observe(customerId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // 🤝 협업 **수락 대기중**인 요청 — shareId → 공유 시각(createdAtMs). by-me 에서 상대가 아직 수락 안 한 것(status=="pending").
    //   협업 칩이 **클릭 없이** 「수락 대기중 · N시간 남음」/「만료됨」을 보여주는 데 쓴다(만료 셈은 CollabInviteExpiry). (사장님 2026-10-20)
    //   수락되면(accepted) 빠진다. init + 협업 바뀔 때(loadCollabPending) 다시 읽는다. 서버 실패면 그대로(빈 = 아무 말 안 함).
    private val _collabPendingCreatedAt = MutableStateFlow<Map<String, Long>>(emptyMap())
    val collabPendingCreatedAt = _collabPendingCreatedAt.asStateFlow()

    fun loadCollabPending() = viewModelScope.launch {
        val myPhone = container.preferences.bizPhone.filter { it.isDigit() }
        if (myPhone.isBlank()) return@launch
        container.sharedSiteRepository.byMe(myPhone).onSuccess { sites ->
            _collabPendingCreatedAt.value = sites
                .filter { it.status == "pending" && it.shareId.isNotBlank() }
                .associate { it.shareId to it.createdAtMs }
        }
    }

    // 발행 이력 — 이 고객에게 발행한 견적서/시공접수서. 2026-07-07 사장님.
    /**
     * 📋 **고객이 채운 접수서** — 토큰으로 카드와 이어 붙인다.
     *
     * 카드(`issued_docs`)는 **보낸 순간**으로 굳어 있고,
     * 고객이 채운 주소·날짜는 `intake_events` 에 따로 쌓인다.
     * 둘은 **같은 토큰**을 쓰므로 여기서 엮어 카드에 같이 보여준다.
     * (2026-09-26 사장님 "고객이 작성하면 여기 탭에 생기는거니?")
     */
    val intakeByToken: kotlinx.coroutines.flow.StateFlow<Map<String, com.detailline.callfollowcrm.data.local.entity.IntakeEventEntity>> =
        container.customerRepository.observeById(customerId)
            .flatMapLatest { c ->
                if (c == null) emptyFlow<List<com.detailline.callfollowcrm.data.local.entity.IntakeEventEntity>>()
                else container.intakeEventRepository
                    .observe(PhoneKey.of(c.phoneNumber))
            }
            .map { list -> list.associateBy { it.token } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val issuedDocs: kotlinx.coroutines.flow.StateFlow<List<com.detailline.callfollowcrm.data.local.entity.IssuedDocEntity>> =
        container.issuedDocRepository.observeByCustomer(customerId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // 지난 시공 이력 — 재방문/추가 시공 시 보관된 완료 건(DB v42). "지난 시공 N건"으로 표시. (2026-07-20 사장님)
    val pastJobs: kotlinx.coroutines.flow.StateFlow<List<com.detailline.callfollowcrm.data.local.entity.JobEntity>> =
        container.jobRepository.observeCompletedByCustomer(customerId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * 이 고객의 **모든 시공 건** — 지난 것도, 앞으로 잡힌 것도. 건 탭이 읽는다. (2026-09-17)
     *   전엔 완료된 건(pastJobs)만 봐서, 예정 건을 두 개 잡아도 탭에 한 개만 보였다.
     */
    val allJobs: kotlinx.coroutines.flow.StateFlow<List<com.detailline.callfollowcrm.data.local.entity.JobEntity>> =
        container.jobRepository.observeByCustomer(customerId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * 새 시공 건 추가 — **기존 건은 그대로 두고** 한 줄 더 쌓는다. (2026-09-17 사장님:
     * "새시공 눌러서 뭔가해보려는데 날짜부터 체크하는데 저장이안되네? 새탭이 안생겨")
     *   전엔 [＋ 새 시공] 이 날짜 고르기만 열고, 고른 날짜를 **지금 건에 덮어썼다.**
     *   1차 시공 날짜가 조용히 바뀌는 셈이라 더 나빴다.
     */
    /**
     * 이 **건**의 현장 메모. (2026-09-18 확정 프로토 — 👤손님 메모 / 📍현장 메모 분리)
     *   손님 메모(customers.memo)와 달리 현장이 바뀌면 같이 바뀐다.
     */
    fun updateJobMemo(jobId: Long, memo: String) {
        viewModelScope.launch {
            // 💾 조용히 삼키지 않는다 — 현관 비번·주차 메모가 그렇게 사라지면
            //   다음 날 현장 앞에서 못 들어간다. (2026-10-02)
            SaveGuard.run("현장 메모", _toast) { container.jobRepository.updateMemo(jobId, memo) }
        }
    }

    // ── 고른 **건**에 직접 쓰기 (2026-09-18 확정 프로토 ④) ─────────────────────
    //   지난 건을 골라도 금액·계약금·잔금·주소를 고칠 수 있어야 한다.
    //   jobId 가 null 이면 '지금 건'(=고객 카드 경로)이라 기존 함수를 그대로 쓴다.

    /**
     * 이 건의 **총액·계약금을 한 번에** 저장. (2026-10-15 사장님 — 금액 창이 계약금 → 총액 → 잔금 안내형으로)
     *   전엔 총액·계약금이 각각 다른 launch 였다 — 둘을 연달아 쓰면 **읽고-쓰기가 겹쳐 한쪽이 지워질 수 있다**(lost update).
     *   잔금 칸은 비운다(총액이 바뀌면 SettlementCalc 가 총액−계약금으로 다시 센다 — stale 잔금 방지).
     */
    fun setJobAmounts(jobId: Long, totalWon: Long?, depositWon: Long?) = viewModelScope.launch {
        withContext(NonCancellable) {
            val stamped = SaveGuard.run("금액", _toast) {
                val j = container.jobRepository.findById(jobId) ?: return@run false
                val now = System.currentTimeMillis()
                // 💵 계약금을 적으면 받은 것(Q3) — 규칙은 SettlementCalc.depositPaidAfterSave 한 곳. 잔금 받음은 안 건드린다.
                val depositPaidAt = com.detailline.callfollowcrm.domain.settlement.SettlementCalc
                    .depositPaidAfterSave(depositWon, j.depositPaidAt, now)
                container.jobRepository.updateMoney(
                    jobId = jobId,
                    totalAmount = totalWon,
                    depositAmount = depositWon,
                    depositPaidAt = depositPaidAt,
                    balanceAmount = null,          // 총액이 바뀌면 잔금은 다시 계산되게 비운다
                    balancePaidAt = j.balancePaidAt,
                    now = now
                )
                // (updateMoney 안에서 고객 카드(공유칸)도 대표 건 기준으로 같이 맞춰진다 — 한 방향 jobs→customers.)
                depositPaidAt != null && j.depositPaidAt == null
            } ?: false
            if (stamped) _toast.value = "계약금 받음으로 기록했어요"
            container.autoCategoryClassifier.reclassify(customerId)
        }
    }

    /**
     * 건이 없는 고객(상담 단계)의 총액·계약금 — 금액 창의 고객 경로. 계약금 받음 규칙은 건 경로와 **같은 한 곳**(depositPaidAfterSave).
     */
    fun setCustomerAmounts(totalWon: Long?, depositWon: Long?) = viewModelScope.launch {
        withContext(NonCancellable) {
            val stamped = SaveGuard.run("금액", _toast) {
                val c = container.customerRepository.findById(customerId) ?: return@run false
                val now = System.currentTimeMillis()
                val depositPaidAt = com.detailline.callfollowcrm.domain.settlement.SettlementCalc
                    .depositPaidAfterSave(depositWon, c.depositPaidAt, now)
                if (totalWon != c.totalAmount) container.customerRepository.updateTotalAmount(customerId, totalWon)
                if (depositWon != c.depositAmount) container.customerRepository.updateDepositAmount(customerId, depositWon)
                if (depositPaidAt != c.depositPaidAt) container.customerRepository.updateDepositPaidAt(customerId, depositPaidAt)
                depositPaidAt != null && c.depositPaidAt == null
            } ?: false
            if (stamped) _toast.value = "계약금 받음으로 기록했어요"
            container.autoCategoryClassifier.reclassify(customerId)
        }
    }

    fun setJobDepositPaid(jobId: Long, paid: Boolean) = viewModelScope.launch {
        withContext(NonCancellable) {
            SaveGuard.run("계약금 받음", _toast) {
                // 고객 카드(요약) 동기화는 setDepositPaid 안에서 자동(도장 기계). (2026-10-13)
                container.jobRepository.setDepositPaid(jobId, if (paid) System.currentTimeMillis() else null)
            }
            container.autoCategoryClassifier.reclassify(customerId)
        }
    }

    /** 잔금 받음 — **완료도 자동**(JobRepository.setBalancePaid 안에서). */
    fun setJobBalancePaid(jobId: Long, paid: Boolean) = viewModelScope.launch {
        withContext(NonCancellable) {
            SaveGuard.run("잔금 받음", _toast) {
                // 🔒 고객 카드(요약)·배지 동기화는 setBalancePaid 안에서 자동(도장 기계) — 전엔 여기서 빠져
                //   고객정보는 "완납"인데 배지는 "잔금미수"로 남았다. (2026-10-13 사장님)
                container.jobRepository.setBalancePaid(jobId, if (paid) System.currentTimeMillis() else null)
            }
            container.autoCategoryClassifier.reclassify(customerId)
        }
    }

    fun setJobAddress(jobId: Long, address: String?) = viewModelScope.launch {
        withContext(NonCancellable) {
            val job = SaveGuard.run("현장 주소", _toast) {
                container.jobRepository.updateAddress(jobId, address)
                container.jobRepository.findById(jobId)
            }
            // 🤝 **그 건(날짜)의 협업 사장에게만** 새 주소 전파 — 전엔 건별 저장은 전파를 안 해 B 폰이 옛 주소 그대로였다.
            //   (2026-10-15 사장님 실기: 2차 현장 주소 바꿨는데 하우스픽 폰에 알림도 주소도 안 감) 1차 협업엔 안 간다(타깃팅 = 카드 협업 칩과 같은 forJob).
            val jobDayStart = job?.scheduledWorkDate?.let { com.detailline.callfollowcrm.util.DateTimeUtils.startOfDay(it) }
            if (jobDayStart != null) propagateAddressToCollab(address, jobDayStart)
        }
    }

    /**
     * **그 건**에 시공 날짜+기간 저장 — 대표 건이 아니어도 그 건에 쓴다. (2026-10-14 사장님: 2차 날짜 등록 안 됨)
     *   돈은 건별(setJobAmounts 등)인데 날짜만 고객(대표) 경로였다 → 2차를 보며 잡으면 1차로 갔다.
     */
    fun setJobSchedule(jobId: Long, startMs: Long, days: Int) = viewModelScope.launch {
        withContext(NonCancellable) {
            SaveGuard.run("시공 예약", _toast) {
                val day = com.detailline.callfollowcrm.util.DateTimeUtils.startOfDay(startMs)
                container.jobRepository.setJobDateAndDays(jobId, day, days, System.currentTimeMillis())
            }
        }
    }

    /** **그 건**에 시공 시간(분)+기간 저장. (2026-10-14) */
    fun setJobTiming(jobId: Long, minutes: Int?, days: Int) = viewModelScope.launch {
        withContext(NonCancellable) {
            SaveGuard.run("시공 시간", _toast) {
                container.jobRepository.setJobTiming(jobId, minutes, days, System.currentTimeMillis())
            }
        }
    }

    /** 문자에서 잡힌 주소로 **새 건**을 만든다(날짜 미정). (2026-09-18 프로토 ⑤) */
    fun addJobWithAddress(address: String, onDone: (Long) -> Unit = {}) = viewModelScope.launch {
        // 🤫 전엔 실패하면 조용히 0 을 돌려줬다 — 화면은 '만들었다' 고 믿고 0번 건으로 넘어갔다. (2026-10-02)
        val id = withContext(NonCancellable) {
            SaveGuard.run("새 현장", _toast) { container.jobRepository.addDraftJob(customerId, address) } ?: 0L
        }
        if (id <= 0L) return@launch
        onDone(id)
    }

    /**
     * 새 건 쌓기 — 프로토 `s_newjob`(시공일 + 현장 주소 + 총 금액). 지금 건은 **손대지 않는다.** (2026-09-17 / 2026-10-15 주소·금액 추가)
     *   주소·총액은 **그 새 건에** 들어간다(고객 공유칸 아님 — 2차 주소가 1차로 새지 않게).
     */
    fun addNewJob(
        startMs: Long, days: Int, address: String? = null, totalWon: Long? = null, depositWon: Long? = null,
        onDone: (Long) -> Unit = {}
    ) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val day = com.detailline.callfollowcrm.util.DateTimeUtils.startOfDay(startMs)
            val deposit = depositWon?.takeIf { it > 0L }
            // 🤫 실패를 삼키지 않는다 — 새 건이 안 생겼는데 "추가했어요"가 뜨면 안 된다. (§13①)
            val id = SaveGuard.run("새 시공", _toast) {
                container.jobRepository.addJob(
                    customerId = customerId,
                    scheduledWorkDate = day,
                    scheduledWorkMinutes = null,
                    scheduledWorkDays = days.coerceAtLeast(1),
                    address = address?.trim()?.takeIf { it.isNotBlank() },   // 현장이 다를 수 있으니 고객 주소를 안 물려준다 — 건마다 따로
                    totalAmount = totalWon?.takeIf { it > 0L },
                    depositAmount = deposit,
                    // 💵 계약금을 적으면 받은 것(Q3) — 금액 창과 같은 규칙(SettlementCalc.depositPaidAfterSave).
                    depositPaidAt = com.detailline.callfollowcrm.domain.settlement.SettlementCalc.depositPaidAfterSave(deposit, null, now),
                    now = now
                )
            } ?: return@launch
            // 0 = 안 만들어짐(그 날 그 현장이 이미 있음). 조용히 넘어가지 않는다. (2026-09-28)
            if (id <= 0L) _toast.value = "그 날은 이미 잡혀 있어요 — 일정에서 확인해주세요"
            onDone(id)
        }
    }

    /** 발행 이력 1건 삭제(잘못 발행/정리용). */
    fun deleteIssuedDoc(id: Long) {
        viewModelScope.launch {
            SaveGuard.run("문서 지우기", _toast) { container.issuedDocRepository.delete(id) }
        }
    }

    /** 팀원+사장님이 서버에 올린 현장 사진(§25). 고객 상세 열 때 가져옴. 팀원 건은 파란 이름표. */
    private val _teamPhotos = MutableStateFlow<List<com.detailline.callfollowcrm.ai.SitePhotoServerRepository.RemotePhoto>>(emptyList())
    val teamPhotos = _teamPhotos.asStateFlow()

    /** 한 현장 최대 사진 수 (사장님 요청 2026-06-05). 로컬 + 서버 합산 기준. */
    val sitePhotoMax = 20

    fun refreshTeamPhotos() = viewModelScope.launch {
        val owner = container.preferences.bizPhone.trim()
        val cust = customer.value?.phoneNumber?.trim().orEmpty()
        if (owner.isBlank() || cust.isBlank()) return@launch
        // 사장님 본인 사진(isOwner)은 이미 로컬(sitePhotos)로 보여줌 → 웹 백필로 서버에도 올라가면 여기서 중복 표시·중복 카운트됨.
        //   그래서 서버 목록에선 '남이 올린 것'(팀원·협업 사장)만 남긴다. (2026-08-13 웹 백필 도입)
        container.sitePhotoServerRepository.fetch(owner, cust).onSuccess { list ->
            _teamPhotos.value = list.filter { !it.isOwner }
            // 🛟 **내가 올렸는데 폰엔 없는 사진** — 재설치하면 이렇게 된다. (2026-09-28 사장님)
            //   서버엔 살아 있으니 되살릴 수 있다. 로컬에 이미 있으면 굳이 안 센다.
            // 🩹 **DB 줄이 아니라 진짜 파일을 센다.** (2026-09-28)
            //   전엔 sitePhotos 가 비었는지만 봤다. 그런데 백업을 되돌린 폰은 **줄은 있고 파일은 없다** —
            //   사진 칸은 깨진 자리로 남는데 「되살리기」는 영영 안 뜬다. 직원 폰이 딱 그랬다.
            val aliveOnPhone = sitePhotos.value.count {
                runCatching { java.io.File(it.filePath).exists() }.getOrDefault(false)
            }
            _restorable.value =
                if (aliveOnPhone == 0) list.filter { it.isOwner } else emptyList()
        }
    }

    /**
     * 🔄 사진을 돌려 저장한다. **크게 보기를 닫을 때 한 번만** 부른다 —
     *   누를 때마다 쓰면 그만큼 화질이 깎인다. 0 도면 아무것도 안 한다.
     */
    fun rotatePhoto(photoId: Long, degrees: Int) = viewModelScope.launch {
        if (degrees % 360 == 0) return@launch
        val ok = withContext(NonCancellable) {
            runCatching { container.sitePhotoRepository.rotate(photoId, degrees) }.getOrDefault(false)
        }
        if (!ok) _toast.value = "사진을 돌리지 못했어요"
    }

    /** 꾹 눌러 고른 사진을 대표로. 같은 건의 다른 사진(siblingIds)은 해제. (2026-10-15 사장님) */
    fun setRepresentativePhoto(photoId: Long, siblingIds: List<Long>) = viewModelScope.launch {
        withContext(NonCancellable) {
            SaveGuard.run("대표 사진", _toast) {
                container.sitePhotoRepository.setRepresentative(photoId, siblingIds)
            }
        }
        _toast.value = "대표 사진으로 정했어요"
    }

    /** 🛟 서버에만 있는 내 사진 — 되살릴 수 있는 것들. 폰에 사진이 하나도 없을 때만 찬다. */
    private val _restorable =
        MutableStateFlow<List<com.detailline.callfollowcrm.ai.SitePhotoServerRepository.RemotePhoto>>(emptyList())
    val restorable = _restorable.asStateFlow()

    /**
     * 🛟 서버 사진을 폰으로 되살린다. 보기만 되던 것을 **기록으로** 돌려놓는다 —
     *   내 기록·영상·인증샷·백업이 전부 로컬 사진을 본다.
     */
    fun restorePhotosFromServer() = viewModelScope.launch {
        val list = _restorable.value
        if (list.isEmpty()) return@launch
        _toast.value = "사진을 되살리는 중…"
        var ok = 0
        withContext(NonCancellable) {
            val jid = runCatching { container.jobRepository.representativeJobId(customerId) }.getOrNull()
            // 올린 순서대로 — 그래야 「대표 사진」이 원래 것과 같아진다.
            list.sortedBy { it.uploadedAtMs }.forEach { p ->
                val bmp = p.bitmap ?: return@forEach
                if (container.sitePhotoRepository.addFromBitmap(customerId, bmp, jid, p.uploadedAtMs)) ok++
            }
        }
        _restorable.value = emptyList()
        _toast.value = if (ok > 0) "사진 ${ok}장을 되살렸어요" else "되살리지 못했어요"
    }

    /** 팀원이 이 고객(현장)에 남긴 현장 메모(특이사항). 고객 상세 열 때 가져옴. */
    private val _teamNotes = MutableStateFlow<List<com.detailline.callfollowcrm.ai.SitePhotoServerRepository.RemoteNote>>(emptyList())
    val teamNotes = _teamNotes.asStateFlow()

    fun refreshTeamNotes() = viewModelScope.launch {
        val owner = container.preferences.bizPhone.trim()
        val cust = customer.value?.phoneNumber?.trim().orEmpty()
        if (owner.isBlank() || cust.isBlank()) return@launch
        container.sitePhotoServerRepository.fetchNotes(owner, cust).onSuccess { _teamNotes.value = it }
    }

    /** 팀원 현장 메모에 답글 → 서버 저장 후 목록 새로고침(팀원 링크 화면에 보임). */
    fun replyToTeamNote(eventId: Long, text: String) = viewModelScope.launch {
        val owner = container.preferences.bizPhone.trim()
        val body = text.trim()
        if (owner.isBlank() || body.isEmpty()) return@launch
        container.sitePhotoServerRepository.replyNote(owner, eventId, body)
            .onSuccess { refreshTeamNotes(); _toast.value = "답글을 보냈어요" }
            .onFailure { _toast.value = "답글 전송 실패" }
    }

    /** 갤러리에서 고른 사진들 추가(내부 저장소 복사). 한 현장 최대 [sitePhotoMax]장(로컬+서버 합산). */
    fun addSitePhotos(uris: List<android.net.Uri>, jobId: Long? = null) = viewModelScope.launch {
        if (uris.isEmpty()) return@launch
        // 🔴 **어느 건의 사진인지** 붙인다. 안 붙이면 1차·2차 사진이 안 갈린다.
        //   지금 보고 있는 건(없으면 대표 건). (2026-09-18 사장님: "현장사진도 1차 2차 개별로")
        val repId = runCatching { container.jobRepository.representativeJobId(customerId) }.getOrNull()
        val jid = jobId ?: repId
        // 📸 한도는 **내 사진만** 센다 — 협업 사장 사진(_teamPhotos)은 그 사장의 20장(각 사장마다 따로).
        //   (2026-10-15 사장님: "나 20장, 그 사장 20장. 각 사장마다 20장." 전엔 내 것+협업 것을 **합쳐** 20이라,
        //    둘이 합쳐 20을 넘으면 내 사진을 못 올렸다 — "19장 중 5장만" 신고의 진짜 원인.)
        //   그리고 **이 현장(건)** 것만("한 현장 20장") — 다른 차수 사진은 안 센다. (옛 사진=jobId 없음 → 대표 건에 포함)
        val ownOnJob = sitePhotos.value.count { it.jobId == jid || (it.jobId == null && jid != null && jid == repId) }
        val room = (sitePhotoMax - ownOnJob).coerceAtLeast(0)
        if (room == 0) {
            _toast.value = "내 사진은 한 현장에 ${sitePhotoMax}장까지예요"
            return@launch
        }
        val toAdd = uris.take(room)
        val capped = uris.size - toAdd.size   // 내 20장이 차서 아예 안 넣은 수
        var ok = 0
        withContext(NonCancellable) {
            toAdd.forEach { if (container.sitePhotoRepository.addFromUri(customerId, it, jid)) ok++ }
        }
        // 🐞 복사가 실패한 수 — 전엔 성공 장수만 세고 **실패 장수를 안 알려서** 사진이 조용히 사라졌다.
        //   (2026-10-15 신고) 이제 **실패 장수를 화면에 또렷이 말한다** + 실패 사유는 로그로 남긴다(SitePhotoRepository). §13①
        val failed = toAdd.size - ok
        // 웹 로그인 상태면 방금 추가한 사진을 서버로도 바로 올림(PC 웹에 즉시 반영). 아니면 매니저가 조용히 skip.
        if (ok > 0) container.ownerPhotoUploadManager.kick(viewModelScope)
        _toast.value = when {
            ok == 0 -> "사진을 못 올렸어요 — 잠시 후 다시 시도해주세요"
            failed > 0 && capped > 0 -> "${ok}장 추가 · ${failed}장 실패 · ${capped}장은 내 ${sitePhotoMax}장 초과"
            failed > 0 -> "${toAdd.size}장 중 ${ok}장만 올라갔어요 · ${failed}장 실패 (다시 시도해주세요)"
            capped > 0 -> "내 사진은 ${sitePhotoMax}장까지 — ${ok}장 추가 (${capped}장은 자리 없음)"
            else -> "현장 사진 ${ok}장 추가했어요"
        }
    }

    fun deleteSitePhoto(id: Long) = viewModelScope.launch {
        withContext(NonCancellable) { container.sitePhotoRepository.delete(id) }
        _toast.value = "사진을 삭제했어요"
    }

    /**
     * ✏️ **펜으로 그린 사진을 고객에게 보낸다.** (2026-10-24 사장님 "펜 완료 = 고객에게 보내기만")
     *   원본에 획을 얹어(PhotoMarkRenderer) 캐시에 저장 → 메시지 앱을 고객 번호로 연다(SmsIntentHelper, 사진 첨부).
     *   현장 저장은 안 한다(그건 사진 올리기로). 못 열면 화면에 말한다.
     */
    fun sendMarkedPhoto(sourcePhotoId: Long, marks: List<com.detailline.callfollowcrm.domain.ui.MarkStroke>) = viewModelScope.launch {
        if (marks.isEmpty()) return@launch
        val src = sitePhotos.value.firstOrNull { it.id == sourcePhotoId }
        if (src == null) { _toast.value = "원본 사진을 찾지 못했어요"; return@launch }
        val phone = customer.value?.phoneNumber?.trim().orEmpty()
        _toast.value = "보낼 사진 준비 중…"
        val uri = withContext(NonCancellable) {
            com.detailline.callfollowcrm.util.PhotoMarkRenderer.renderToShareUri(container.appContext, src.filePath, marks)
        }
        if (uri == null) { _toast.value = "사진을 준비하지 못했어요"; return@launch }
        val res = com.detailline.callfollowcrm.util.SmsIntentHelper.openSmsComposeWithAttachments(
            container.appContext, phone, "", listOf(uri)
        )
        if (res is com.detailline.callfollowcrm.util.SmsIntentHelper.Result.Failed) {
            _toast.value = "메시지 앱을 열지 못했어요"
        }
    }

    /** 팀원이 올린(서버) 현장 사진을 사장님이 삭제 — 퇴사한 팀원 사진도 정리 가능. (2026-06-07) */
    fun deleteTeamPhoto(photoId: Long) = viewModelScope.launch {
        val owner = container.preferences.bizPhone.trim()
        if (owner.isBlank()) { _toast.value = "사업자 전화번호가 없어요"; return@launch }
        val cust = customer.value?.phoneNumber?.trim().orEmpty()
        val res = withContext(NonCancellable) {
            container.sitePhotoServerRepository.deletePhotoAsOwner(owner, photoId)
        }
        res.onSuccess {
            _teamPhotos.value = _teamPhotos.value.filterNot { it.photoId == photoId }
            _toast.value = "사진을 삭제했어요"
            if (cust.isNotBlank()) refreshTeamPhotos()
        }.onFailure { _toast.value = "지우지 못했어요 — 잠시 후 다시 해주세요" }
    }

    // 2026-05-29 킬러콘텐츠 5단계 — 고객 페르소나 (cowork prepare-reply 가 자동 생성).
    //   안드는 GET /api/customer-persona/{phone} 으로 cache 조회만.
    //   customer flow 받으면 phone 으로 fetch — 자동 갱신.
    private val _persona = kotlinx.coroutines.flow.MutableStateFlow<com.detailline.callfollowcrm.ai.CustomerPersona?>(null)
    val persona: kotlinx.coroutines.flow.StateFlow<com.detailline.callfollowcrm.ai.CustomerPersona?> = _persona

    init {
        loadCollabPending()   // 협업 수락 대기중 shareId — 칩이 "수락 대기중"을 바로 보여주게.
        // customer 의 phone 이 들어오면 (보통 즉시) persona fetch. 서버 404 면 null 그대로.
        viewModelScope.launch {
            customer.collect { c ->
                if (c?.phoneNumber.isNullOrBlank()) {
                    _persona.value = null
                    return@collect
                }
                val phone = c?.phoneNumber ?: return@collect
                _persona.value = container.customerPersonaRepository.fetch(phone).getOrNull()
            }
        }
    }

    /**
     * P1/P2 AI 대화 요약 — ChatScreen 의 박스와 같은 데이터 (server 응답이 캐시되면 자동 emit).
     * 사장님 요청 (2026-05-24): "고객 상세에 대화 요약 있으면 문자 다시 검토 안 해도 됨".
     * Room observe — phoneNumber 가 바뀌면 (보통은 안 바뀜) 새 suffix 로 자동 재구독.
     * 서버 미구현이면 영구 null → 박스 안 보임 (silent).
     */
    val aiSummary: kotlinx.coroutines.flow.StateFlow<com.detailline.callfollowcrm.data.local.entity.AiSummaryEntity?> =
        container.customerRepository.observeById(customerId)
            .flatMapLatest { c ->
                val phone = c?.phoneNumber.orEmpty()
                val suffix = PhoneKey.of(phone)
                if (suffix.length < 7) kotlinx.coroutines.flow.flowOf(null)
                else container.conversationAiRepository.observe(suffix)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * 시스템 SMS 에서 이 고객 번호와 주고받은 모든 문자 (송+수신).
     * Settings 토글이 켜져 있고 READ_SMS 권한이 있을 때만 채워짐.
     * 실시간 observe 는 안 함 — 화면 진입 시 / 새로고침 시 로드.
     *
     * 갤럭시 메시지로 보낸 문자도 여기에 포함되므로, MessageHistory 와 별도로
     * 타임라인의 단일 출처(single source of truth)로 사용.
     */
    private val _systemSms = MutableStateFlow<List<SmsRepository.SmsMessage>>(emptyList())
    val systemSms = _systemSms.asStateFlow()

    /**
     * 추출된 시공 현장 주소 — 사장님 통점 (2026-05-25): 시공자는 주소를 가장 자주 복사.
     * 시스템 SMS + cached_messages 둘 다 보고 한국 주소 패턴 추출 ([AddressExtractor]).
     * 다음 세션: 서버 conversation-summary endpoint 가 LLM 으로 정확도 ↑ 추출 예정.
     *
     * 최신 메시지부터 훑어 첫 매칭. 없으면 null → UI 측에서 "아직 인식 X" 빈 상태 표시.
     */
    private val _cachedSms = MutableStateFlow<List<com.detailline.callfollowcrm.data.repository.SmsRepository.SmsMessage>>(emptyList())
    val extractedAddress: kotlinx.coroutines.flow.StateFlow<String?> =
        kotlinx.coroutines.flow.combine(_systemSms, _cachedSms) { sys, cached ->
            val all = (sys + cached).distinctBy { it.dateMs to it.body }
            com.detailline.callfollowcrm.util.AddressExtractor.extractFromMessages(
                all.sortedByDescending { it.dateMs }.map { it.body }
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * 주고받은 문자 — CustomerDetail 의 "📩 문자" 접이식 섹션이 구독 (2026-05-27 사장님 요청).
     *   시스템 SMS + cached_messages 합쳐서 중복 제거 + 최신순. ChatScreen 진입 안 해도
     *   사장님이 대화 흐름 빠르게 검토.
     */
    val mergedMessages: kotlinx.coroutines.flow.StateFlow<List<SmsRepository.SmsMessage>> =
        kotlinx.coroutines.flow.combine(_systemSms, _cachedSms) { sys, cached ->
            (sys + cached)
                .distinctBy { it.dateMs to it.body }
                .sortedByDescending { it.dateMs }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ─────────────────────────────────────────────────────────────────────
    // 저장 메서드들은 모두 NonCancellable 로 감싼다. 이유:
    //   사용자가 저장 버튼 누른 직후 뒤로가기 등으로 화면이 닫히면 viewModelScope 가 cancel 되어
    //   짧은 DB write 가 중도에 끊길 수 있다. withContext(NonCancellable) 안의 작업은
    //   parent scope cancel 후에도 끝까지 완료된다.
    // ─────────────────────────────────────────────────────────────────────

    // 2026-05-25: updateStatus 제거 — 갤메시지 식 카테고리 시스템으로 통일.

    /** 사장님이 직접 카테고리 박음. null = 미분류로 돌림. */
    fun setCategory(categoryId: Long?) = viewModelScope.launch {
        withContext(NonCancellable) {
            container.categoryRepository.assignCustomer(customerId, categoryId)
            markTodayCallsAsHandled()
        }
    }

    /**
     * CustomerDetail picker 에서 "+ 새 카테고리" 누름 → 이름 받아서 생성 + 이 고객에게 즉시 할당.
     * 같은 이름이 이미 있으면 그 카테고리에 박음 (upsert idempotent).
     */
    fun addCategoryAndAssign(name: String, emoji: String? = null) = viewModelScope.launch {
        if (name.isBlank()) return@launch
        withContext(NonCancellable) {
            // 🤫 **사장님이 직접 적어 만드는 분류**다. 전엔 실패하면 조용히 돌아가서
            //   창은 닫히는데 **분류가 안 생겼다** — 사장님은 생긴 줄 안다. (2026-10-02)
            val entity = SaveGuard.run("분류 만들기", _toast) {
                // 이모지는 만들 때 고른 것(없으면 이름 보고 앱이 붙인 것). (2026-09-19 사장님)
                container.categoryRepository.upsert(
                    name.trim(),
                    emoji ?: com.detailline.callfollowcrm.util.CategoryEmoji.forName(name)
                )
            } ?: return@withContext
            container.categoryRepository.assignCustomer(customerId, entity.id)
            markTodayCallsAsHandled()
        }
    }

    /** 카테고리 목록 — pill 선택 다이얼로그 표시. */
    val categories = container.categoryRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * 분류마다 손님이 몇 명인지 — 고르는 창 타일에 같이 보여준다. (2026-09-19 사장님)
     *   null 키 = 미분류. "거래처가 몇이나 됐지?" 를 그 자리에서 알 수 있다.
     */
    val categoryCounts: kotlinx.coroutines.flow.StateFlow<Map<Long?, Int>> =
        container.customerRepository.observeAll()
            .map { list -> list.groupingBy { it.categoryId }.eachCount() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun updateMemo(memo: String) = viewModelScope.launch {
        withContext(NonCancellable) {
            SaveGuard.run("손님 메모", _toast) {
                container.customerRepository.updateMemo(customerId, memo)
                markTodayCallsAsHandled()
            }
        }
    }

    fun updateName(name: String) = viewModelScope.launch {
        withContext(NonCancellable) {
            SaveGuard.run("이름", _toast) {
                container.customerRepository.updateName(customerId, name.takeIf { it.isNotBlank() })
                markTodayCallsAsHandled()
            }
        }
    }

    fun updateLeadHeat(leadHeat: com.detailline.callfollowcrm.domain.model.LeadHeat?) = viewModelScope.launch {
        withContext(NonCancellable) {
            container.customerRepository.updateLeadHeat(customerId, leadHeat)
        }
    }

    // ─── 입금 관련 (계약금/잔금) ─────────────────────────────────────
    // 체크박스 토글 → "지금" 받음/안 받음 으로 시각 기록.
    // 금액 입력 → 별도 필드. 두 개 분리해서 사장님이 "금액만 정함 / 받기까진 안 함" 케이스도 가능.

    fun setDepositPaid(paid: Boolean) = viewModelScope.launch {
        withContext(NonCancellable) {
            container.customerRepository.updateDepositPaidAt(
                customerId,
                if (paid) System.currentTimeMillis() else null
            )
        }
    }

    fun setDepositAmount(amount: Long?) = viewModelScope.launch {
        withContext(NonCancellable) {
            SaveGuard.run("계약금", _toast) {
                container.customerRepository.updateDepositAmount(customerId, amount)
                // 2026-05-30 #7 — 입금 변경 후 자동 카테고리 갱신.
                container.autoCategoryClassifier.reclassify(customerId)
            }
        }
    }

    fun setBalancePaid(paid: Boolean) = viewModelScope.launch {
        withContext(NonCancellable) {
            // 🤫 **여기가 Fable 1순위였다.** 고객 카드엔 '받음'이 찍히는데 건 미러가 조용히
            //   실패하면 건 전표는 '미수' 로 남아 다음 날 잘못된 미수 알람이 뜬다. (2026-10-02)
            // 💵 도장은 WorkCompletionManager.setBalancePaid 한 곳 — 카드·대표 건을 같이 찍고, 되돌릴 때도 같이. (2026-10-15)
            SaveGuard.run("잔금 받음", _toast) {
                container.workCompletionManager.setBalancePaid(customerId, if (paid) System.currentTimeMillis() else null)
            }
            container.autoCategoryClassifier.reclassify(customerId)
        }
        // 잔금 받음 처리 = 그 시점을 챗 타임라인에 카드로. 취소(false)면 그 카드도 삭제 — 실수 처리 후 '받음' 잔상 방지. (2026-06-30 / 2026-08-28 사장님)
        if (paid) {
            val c = customer.value
            // 잔금은 정산 단일 출처 — stale balanceAmount 안 쓴다. (2026-10-07)
            val balanceWon = c?.let { com.detailline.callfollowcrm.domain.settlement.SettlementCalc.rowOf(it).balanceAmount } ?: 0L
            recordTimelineEvent(type = "balance_paid", oldValue = null, newValue = wonLabel(balanceWon))
        } else {
            deleteTimelineEventsOfType("balance_paid")
        }
    }

    fun setBalanceAmount(amount: Long?) = viewModelScope.launch {
        withContext(NonCancellable) {
            SaveGuard.run("잔금", _toast) {
                container.customerRepository.updateBalanceAmount(customerId, amount)
                container.autoCategoryClassifier.reclassify(customerId)
            }
        }
    }

    /**
     * 변경/처리 이력을 챗 타임라인 카드로 기록 (2026-06-30 사장님) — 일정/금액/잔금이 바뀐 "시점"을 남김.
     *   phoneSuffix = ChatViewModel 매칭과 동일(끝 8자리). 고객 없으면(suffix < 7) 조용히 skip.
     */
    private fun recordTimelineEvent(type: String, oldValue: String?, newValue: String?, reason: String? = null) {
        val suffix = PhoneKey.of(customer.value?.phoneNumber) ?: return
        if (suffix.length < 7) return
        viewModelScope.launch {
            withContext(NonCancellable) {
                runCatching {
                    container.timelineEventRepository.record(
                        com.detailline.callfollowcrm.data.local.entity.TimelineEventEntity(
                            phoneSuffix = suffix,
                            type = type,
                            oldValue = oldValue,
                            newValue = newValue,
                            reason = reason,
                            createdAt = System.currentTimeMillis()
                        )
                    )
                }
            }
        }
    }

    /** 특정 타입 타임라인 카드 삭제 — 잔금 '받음 처리' 취소 시 그 카드 제거(잔상 방지). (2026-08-28 사장님) */
    private fun deleteTimelineEventsOfType(type: String) {
        val suffix = PhoneKey.of(customer.value?.phoneNumber) ?: return
        if (suffix.length < 7) return
        viewModelScope.launch {
            withContext(NonCancellable) {
                runCatching { container.timelineEventRepository.deleteByType(suffix, type) }
            }
        }
    }

    /** 만원 라벨 ("8만원"). 0 이하면 null. */
    private fun wonLabel(won: Long?): String? =
        (won ?: 0L).takeIf { it > 0 }?.let { com.detailline.callfollowcrm.util.MoneyFormatter.manwonShort(it) }

    /** 시공일정 라벨 "7/3(금) 오전 8시" (시간 없으면 날짜만). null = 일정 없음. */
    private fun scheduleLabel(date: Long?, minutes: Int?): String? {
        if (date == null) return null
        val d = com.detailline.callfollowcrm.util.DateTimeUtils.formatDateLabel(date)
        val t = minutes?.let { com.detailline.callfollowcrm.util.DateTimeUtils.formatWorkMinutes(it) }
        return if (t != null) "$d $t" else d
    }

    /**
     * 시공금액 변경 이력 카드 기록 (옛→새 + 선택 이유) — CustomerDetailScreen 이유 입력 후 호출. (2026-06-30 사장님)
     *   이유는 사장님 본인 기억용(고객 알림엔 안 실음). 안 적으면 reason=null 로 그냥 기록.
     */
    fun recordAmountChange(oldWon: Long?, newWon: Long?, reason: String?) {
        if ((oldWon ?: 0L) == (newWon ?: 0L)) return
        recordTimelineEvent(
            type = "amount",
            oldValue = wonLabel(oldWon),
            newValue = wonLabel(newWon),
            reason = reason?.trim()?.takeIf { it.isNotBlank() }
        )
    }

    /**
     * 2026-05-30 사장님 #4 통점 — 총금액 설정.
     * 잔금 자동 계산 (UI 측에서 totalAmount - depositAmount 으로 표시) 기준.
     */
    fun setTotalAmount(amount: Long?) = viewModelScope.launch {
        withContext(NonCancellable) {
            SaveGuard.run("총금액", _toast) {
                container.customerRepository.updateTotalAmount(customerId, amount)
            }
        }
    }

    /** 시공 시간(자정부터 분, null=미정) 설정 — 날짜 선택 후 시간 칩에서 호출. 기간 보존. (2026-06-23 사장님) */
    fun updateScheduledWorkMinutes(minutes: Int?) = viewModelScope.launch {
        withContext(NonCancellable) {
            container.customerRepository.updateScheduledWorkMinutes(customerId, minutes)
            // 일정 탭·달력은 jobs 를 본다 (2026-09-15 사장님). 조용히 실패하면 **달력만 옛 시간**이 된다. (2026-10-02)
            SaveGuard.run("달력 일정", _toast) { container.jobRepository.syncRepresentativeFromCustomer(customerId, System.currentTimeMillis()) }
        }
        // 방금 만든 일정 카드(날짜만)에 시간 채워넣기 — 날짜→시간 2단계라 시간은 여기서 옴. (2026-06-30 사장님)
        val suffix = PhoneKey.of(customer.value?.phoneNumber)
        val date = customer.value?.scheduledWorkDate
        if (suffix != null && suffix.length >= 7 && date != null) {
            val full = scheduleLabel(date, minutes)
            if (full != null) withContext(NonCancellable) {
                runCatching { container.timelineEventRepository.updateLatestScheduleNewValue(suffix, full) }
            }
        }
        // 협업 사장(B)에게도 새 시간 전파 — 예전엔 '날짜' 변경 때만 보내서 시간만 바꾸면 B가 옛 시간대로 옴(2026-08-08 stale 감사).
        //   날짜는 그대로라 old==new; minutes 를 명시해 stale 방지. 협업 없으면 notifyCollabReschedule 이 알아서 무시.
        date?.let { d -> notifyCollabReschedule(d, d, minutes) }
    }

    /** 시공 시간 + 기간(며칠) 동시 설정 — 날짜 선택 후 시간·기간 칩에서 호출. DB v24. (2026-08-01 사장님) */
    fun updateScheduledWorkTiming(minutes: Int?, days: Int) = viewModelScope.launch {
        withContext(NonCancellable) {
            container.customerRepository.updateScheduledWorkTiming(customerId, minutes, days)
            // 시공 기간을 고쳐도 달력이 안 바뀌던 것 (2026-09-15 사장님) — 실패하면 말한다. (2026-10-02)
            SaveGuard.run("달력 일정", _toast) { container.jobRepository.syncRepresentativeFromCustomer(customerId, System.currentTimeMillis()) }
        }
        val suffix = PhoneKey.of(customer.value?.phoneNumber)
        val date = customer.value?.scheduledWorkDate
        if (suffix != null && suffix.length >= 7 && date != null) {
            val full = scheduleLabel(date, minutes)
            if (full != null) withContext(NonCancellable) {
                runCatching { container.timelineEventRepository.updateLatestScheduleNewValue(suffix, full) }
            }
        }
        date?.let { d -> notifyCollabReschedule(d, d, minutes) }   // 협업 사장에게 새 시간 전파 (2026-08-08 stale 감사)
    }

    /** A/S 예약(시공과 별개, 무료) 설정/취소 — date=null 이면 A/S 지움. 기간(며칠) 함께. DB v43. (2026-08-01 사장님) */
    /** 🔧 A/S 해줘야 하는 손님으로 찍는다. 상담함 [A/S] 칩에 뜨고 다른 일 칩에서는 빠진다. */
    fun markAsPending() = viewModelScope.launch {
        container.customerRepository.setAsPending(customerId, System.currentTimeMillis())
    }

    /**
     * 🔧 A/S 다녀왔다 — **태그가 사라지고 쪽지 한 장이 남는다.** (2026-09-27 사장님)
     *   "사라지면서 고객 메모에 몇날며칠 as 처리 완료된 고객이라고 자동으로 메모가 남는 거지"
     *   사라지기만 하면 나중에 「저번에 AS 갔었나?」를 알 길이 없다. 기록이 대신 남는다.
     */
    fun markAsDone() = viewModelScope.launch {
        val now = System.currentTimeMillis()
        container.customerRepository.setAsPending(customerId, null)
        // A/S 처리 쪽지를 남긴다 — 사장님이 나중에 보는 기록이니 안 적혔으면 말한다. (2026-10-02)
        SaveGuard.run("A/S 처리 쪽지", _toast) {
            container.customerNoteRepository.add(
                customerId = customerId, jobId = null,
                body = com.detailline.callfollowcrm.util.DateTimeUtils.formatKoreanMonthDay(now) + " A/S 처리 완료",
                now = now
            )
        }
    }

    /** 되돌리기 — 잘못 눌렀을 때. 쪽지는 손으로 지우실 수 있게 그대로 둔다. */
    fun undoAsDone() = viewModelScope.launch {
        container.customerRepository.setAsPending(customerId, System.currentTimeMillis())
    }

    /**
     * 🔧 **찍은 걸 없던 일로.** 쪽지를 남기지 않는다. (2026-09-27 사장님 "취소할수가없네")
     *   [markAsDone] 과 다른 점이 여기 전부다 — 저건 **갔다 온 것**이고 이건 **잘못 누른 것**이다.
     *   무르는 길이 없으면, 잘못 누른 사람이 「다녀왔어요」를 눌러 **안 간 A/S 를 갔다고 적게** 된다.
     */
    fun clearAsPending() = viewModelScope.launch {
        container.customerRepository.setAsPending(customerId, null)
    }

    fun updateAsSchedule(date: Long?, days: Int) = viewModelScope.launch {
        withContext(NonCancellable) {
            container.customerRepository.updateAsSchedule(customerId, date, days)
        }
    }

    /**
     * 시공 예약일 설정. epoch ms (자정으로 정규화). 취소는 null.
     * 2026-05-25: 자동 status RESERVATION_CONFIRMED 전환 제거 — 카테고리 시스템으로 이관.
     */
    fun updateScheduledWorkDate(epochMs: Long?) = viewModelScope.launch {
        val normalized = epochMs?.let { com.detailline.callfollowcrm.util.DateTimeUtils.startOfDay(it) }
        val oldDate = customer.value?.scheduledWorkDate           // 변경 이력 카드용 (옛 일정)
        val oldMinutes = customer.value?.scheduledWorkMinutes     // 옛 시공 시간
        // 캘린더 등록 KPI — 고객상세 날짜 픽커로 시공일 잡는 것도 한 건. 취소(null)는 제외. (2026-06-25 cowork 요청)
        if (normalized != null) {
            val c = customer.value
            val label = c?.name?.trim()?.takeIf { it.isNotBlank() }
                ?: ("…" + (c?.phoneNumber?.filter { ch -> ch.isDigit() }?.takeLast(4) ?: ""))
            container.journeyEventRepository.track("schedule_create", screen = "customer_detail", target = label)
        }
        withContext(NonCancellable) {
            // 재방문/추가 시공: 실제 새 일정을 잡을 때(취소 아님), 현재 시공이 이미 '완료'면 완료 건을
            //   이력(jobs)으로 보관하고 고객 필드를 리셋 → 첫 시공이 유실되지 않고 "지난 시공"으로 남는다. (2026-07-20 사장님)
            if (normalized != null) {
                container.jobRepository.archiveCompletedBeforeNewSchedule(customerId, System.currentTimeMillis())
            }
            if (normalized == null) {
                // 🔴 취소는 **그 건 하나만** 지운다. (2026-09-18 연결부 점검)
                //   전엔 고객 카드의 날짜·돈을 지웠는데, 날짜를 비우는 순간 미러가 **다음 건**을
                //   대표로 잡아서 그 "백지" 가 다음 건(예: 1차) 전표에 찍혔다.
                //   → 2차를 취소했는데 1차 잔금 기록이 사라지던 것.
                val cancelDay = oldDate?.let { com.detailline.callfollowcrm.util.DateTimeUtils.startOfDay(it) }
                val target = cancelDay?.let { runCatching { container.jobRepository.jobAt(customerId, it) }.getOrNull() }
                if (target != null) {
                    container.jobRepository.cancelJob(target.id, System.currentTimeMillis())
                } else {
                    // 건 행이 없는 옛 데이터 — 기존 방식으로
                    container.customerRepository.updateScheduledWorkDate(customerId, null)
                }
            } else {
                container.customerRepository.updateScheduledWorkDate(customerId, normalized)
                // jobs 까지 같이 옮긴다 — 일정 탭·달력의 출처가 jobs 라 이걸 안 하면 화면이 안 바뀐다.
                //   (2026-09-15 사장님: "일정을 싹 바꿨는데 캘린더가 안 변해")
                // 🤫 날짜를 바꿨는데 이게 조용히 실패하면 **달력에 옛 날짜가 그대로** 남는다. (2026-10-02)
                SaveGuard.run("달력 일정", _toast) { container.jobRepository.syncRepresentativeFromCustomer(customerId, System.currentTimeMillis()) }
            }
            markTodayCallsAsHandled()
            // 예약(일정) 취소 시 = 그 현장의 전문가 배정(팀원 + 협업 요청)도 전부 정리. 일정 없는데 배정만 남으면 안 됨. (2026-06-15 사장님)
            if (normalized == null) {
                // 배정은 **그 시공일 것만** 지운다 — deleteForCustomer 는 다른 건의 배정까지 지웠다. (2026-09-18)
                oldDate?.let { d -> container.teamAssignmentRepository.deleteForCustomerAndDay(customerId, d) }
                    ?: container.teamAssignmentRepository.deleteForCustomer(customerId)
                // 일당(자동지출)도 정리 — 단 '취소하는 그 시공일'의 일당만. 예전엔 deleteByCustomer 로 모든 날짜를 지워
                //   재방문 고객의 과거(이미 지급한) 일당 지출까지 소거돼 그 달 순이익이 부풀던 것. (2026-08-11 돈 감사 rank5)
                oldDate?.let { d -> container.jobCrewRepository.deleteByCustomerAndDay(customerId, d) }
                endCollabForCustomer()
                // 예약 취소 = 완전 백지 (사장님 2026-08-28 결정): 총액·계약금·잔금·받음표시 전부 삭제.
                //   그 건의 돈은 이미 cancelJob 이 지웠다. 여기서 고객 카드까지 백지로 만드는 건
                //   **남은 건이 하나도 없을 때만.** 다른 건이 남아 있으면 recomputeMirror 가
                //   그 건의 돈을 고객 카드에 넣어둔 상태라, 여기서 지우면 남은 건 돈이 날아간다. (2026-09-18)
                val stillHasJob = runCatching { container.jobRepository.hasScheduledJob(customerId) }.getOrDefault(false)
                if (!stillHasJob) {
                    // 잔금 도장 풀기도 WorkCompletionManager 한 곳(건이 없으니 카드만 풀린다). (2026-10-15 balance-paid-stamp 가드)
                    container.workCompletionManager.setBalancePaid(customerId, null)
                    container.customerRepository.updateDepositPaidAt(customerId, null)
                    container.customerRepository.updateTotalAmount(customerId, null)
                    container.customerRepository.updateDepositAmount(customerId, null)
                    container.customerRepository.updateBalanceAmount(customerId, null)
                }
            }
            // 날짜 등록(계약) = "시공 대기" 자동 분류. 날짜 해제 시 자격 재평가. (2026-06-07 카테고리 규칙)
            container.autoCategoryClassifier.reclassify(customerId)
        }
        // 시공일정 이력 카드 (옛 != 새). oldValue=옛 날짜+시간, newValue=새 날짜(시간은 시간선택 후 채움). 첫 설정/취소도 기록. (2026-06-30 사장님)
        if (oldDate != normalized) {
            recordTimelineEvent(
                type = "schedule",
                oldValue = scheduleLabel(oldDate, oldMinutes),
                newValue = normalized?.let { com.detailline.callfollowcrm.util.DateTimeUtils.formatDateLabel(it) }
            )
            // 이 현장에 협업 사장(B)이 배정돼 있으면 "일정 변경: 옛→새" 알림을 보낸다. (2026-07-16 사장님)
            //   날짜가 실제로 바뀌었고(위 조건) 새 날짜가 있을 때만(취소는 기존 로직이 협업을 이미 정리).
            if (normalized != null && oldDate != null) notifyCollabReschedule(oldDate, normalized)
        }
    }

    // ── ⏸️ 보류 손님 (2026-10-16 사장님, 프로토 artifact/D4EwQSrXk4TFPzc9gjW6Cs) — 하는 일은 CustomerHoldFlow 한 곳 ──
    private val holdFlow by lazy { CustomerHoldFlow(container, customerId, _toast) }   // _toast 가 아래 선언이라 lazy (Kotlin init 순서)

    /** 일정 보류 — 계약금은 받았는데 고객이 날짜를 미룬 손님을 상담함 [보류] 칸으로. 날짜가 있었으면 변경 이력에 「보류」로 남긴다. */
    fun holdSchedule(reason: String) = viewModelScope.launch {
        holdFlow.hold(customer.value, reason) { oldDate, oldMinutes, why ->
            recordTimelineEvent(
                type = "schedule", oldValue = scheduleLabel(oldDate, oldMinutes), newValue = null,
                reason = "보류" + (why?.let { " — $it" } ?: "")
            )
        }
    }

    /** 보류 풀기. `restoreDate` = 방금 달력에서 뺀 날짜로 되돌리기(잘못 눌렀을 때). 날짜를 새로 잡으면 자동으로 풀린다. */
    fun releaseHold(restoreDate: Boolean) = viewModelScope.launch { holdFlow.release(restoreDate) }

    /** 되돌릴 날짜가 있나 — 방금 이 화면에서 보류하며 달력에서 뺀 날짜. */
    fun holdRestorableDate(): Long? = holdFlow.restorableDate

    fun updateHoldReason(reason: String) = viewModelScope.launch { holdFlow.updateReason(reason) }

    /** 협업 끝내기 — 예약 취소·보류가 같은 길(CustomerHoldFlow.endCollab). */
    private fun endCollabForCustomer() = holdFlow.endCollab()

    /** 이 고객의 건 — **DB 에서 한 번** 읽는다(allJobs 의 첫 값은 빈 목록이라 '아직 안 읽힘'과 '없음'을 못 가른다). */
    suspend fun allJobsOnce(): List<com.detailline.callfollowcrm.data.local.entity.JobEntity> =
        container.jobRepository.observeByCustomer(customerId).first()

    /**
     * 이 고객 현장의 협업 사장(들)에게 일정 변경 알림 전송. (2026-07-16 사장님)
     *   collabAssignments("customerId|phone|name|shareId") 에서 이 고객의 살아있는 shareId 를 찾아 서버로.
     *   서버가 shared_sites 갱신 + B 에게 FCM(type=collab_reschedule) push. 서버 미구현이면 조용히 무시.
     *   ⚠️ 서버는 accepted 협업에만 push (거절/종료/pending 은 서버가 skip) — docs/SERVER_HANDOFF_collab_reschedule.md
     */
    private fun notifyCollabReschedule(
        oldAtMs: Long,
        newAtMs: Long,
        minutes: Int? = customer.value?.scheduledWorkMinutes   // 시간 변경 경로는 새 minutes 를 명시해 stale 방지
    ) {
        val owner = container.preferences.bizPhone.filter { it.isDigit() }
        if (owner.length < 9) return
        val oldDay = com.detailline.callfollowcrm.util.DateTimeUtils.startOfDay(oldAtMs)
        val newDay = com.detailline.callfollowcrm.util.DateTimeUtils.startOfDay(newAtMs)
        // 협업은 '그 건(날짜)'에 붙는다 — **바뀌는 그 날(oldDay)을 맡은 협업자에게만** 보낸다(다른 건 1차/2차엔 안 샘).
        //   날짜 없는 떠돌이 줄은 제외(새어나가는 것보다 안전). 타깃팅은 카드 협업 칩과 같은 한 곳(shareIdsForJob). (2026-10-09·10-15)
        val shareIds = com.detailline.callfollowcrm.domain.collab.CollabAssignmentLine.shareIdsForJob(
            container.preferences.collabAssignments, customerId, oldDay
        )
        if (shareIds.isEmpty()) return
        // 옮긴 건의 로컬 날짜도 oldDay→newDay 로 갈아끼운다(배지가 새 날짜로 따라가게).
        if (oldDay != newDay) {
            val cur = container.preferences.collabAssignments
            val updated = cur.map { raw ->
                val l = com.detailline.callfollowcrm.domain.collab.CollabAssignmentLine.parse(raw) ?: return@map raw
                if (l.customerId == customerId && l.shareId in shareIds && l.days.isNotEmpty())
                    l.withDays(l.days - oldDay + newDay).format()
                else raw
            }.toSet()
            if (updated != cur) container.preferences.collabAssignments = updated
        }
        val timeLabel = minutes?.let {
            com.detailline.callfollowcrm.util.DateTimeUtils.formatWorkMinutes(it)
        }
        // 📮 우체통으로 — 지하철에서 바꿔도 길 뚫리면 B 에게 간다(옛날엔 runCatching 에 삼켜져 사라졌다). (설계 §1-A #1)
        container.applicationScope.launch {
            shareIds.forEach { sid ->
                val payload = org.json.JSONObject().apply {
                    put("newAtMs", newAtMs)
                    put("oldAtMs", oldAtMs)
                    timeLabel?.let { put("timeLabel", it) }
                }
                runCatching {
                    container.outbox.enqueue(
                        com.detailline.callfollowcrm.domain.outbox.OutboxKind.COLLAB_RESCHEDULE,
                        sid, payload.toString()
                    )
                }
            }
            container.outbox.tryNow()
        }
    }

    /**
     * 현장 주소 수동 등록 (2026-05-28, DB v15).
     *   사장님이 "📍 현장 주소" 카드 탭 → AddressEditDialog → 저장.
     *   null/빈 문자열 = 미등록 (자동 추출이 fallback). repository 에서 trim 처리.
     */
    // 🤫 **조용해도 되는 이유**: 내 폰의 주소 저장(`updateAddress`)은 덮여 있지 않다.
    //   덮인 건 **협업 상대에게 알리는 것**뿐이고, 서버가 아직 없어도 **내 주소는 이미 바뀌었다**. (2026-10-02)
    fun updateManualAddress(address: String?) = viewModelScope.launch {
        // 🗣 사장님이 **직접 친 주소** — 조용히 사라지면 다음에 못 찾아간다. 실패하면 말한다.
        withContext(NonCancellable) {
            com.detailline.callfollowcrm.presentation.util.SaveGuard.run("주소", _toast) {
                container.customerRepository.updateAddress(customerId, address)
            }
        }
        // 이 현장이 협업 중이면 상대 사장(B)들에게 새 주소 전파(+알림). 안 하면 B는 옛 주소 그대로. (2026-08-02 사장님 버그신고)
        //   서버 미구현(update-address 404)이면 조용히 무시 — 로컬은 이미 바뀜. reschedule 와 같은 best-effort.
        //   고객 주소 = **대표 건** 주소이므로 대표 건 날짜의 협업에게만(전엔 그 고객 shareId 전부 → 다른 차수까지 과전파). (2026-10-15)
        //   대표 건 날짜를 모르면(건 없음·날짜 미정) 협업도 없으니 안 보낸다.
        val repDay = customer.value?.scheduledWorkDate?.let { com.detailline.callfollowcrm.util.DateTimeUtils.startOfDay(it) }
        withContext(NonCancellable) { propagateAddressToCollab(address, repDay) }
    }

    /**
     * **그 건(jobDayStart)에 걸린** 협업 shareId 들에 새 주소를 전파. 타깃팅은 카드 협업 칩과 같은 한 곳
     *   ([com.detailline.callfollowcrm.domain.collab.CollabAssignmentLine.shareIdsForJob]). collabAssignments = "customerId|phone|name|shareId|days".
     */
    private suspend fun propagateAddressToCollab(newAddress: String?, jobDayStart: Long?) {
        val addr = com.detailline.callfollowcrm.util.AddressExtractor.tidyAddress(newAddress.orEmpty())
            .takeIf { it.isNotBlank() } ?: return
        val owner = container.preferences.bizPhone.filter { it.isDigit() }
        if (owner.length < 9) return
        val shareIds = com.detailline.callfollowcrm.domain.collab.CollabAssignmentLine.shareIdsForJob(
            container.preferences.collabAssignments, customerId, jobDayStart
        )
        if (shareIds.isEmpty()) return
        val label = com.detailline.callfollowcrm.util.AddressExtractor.siteLabel(addr)
            .takeIf { it.isNotBlank() }?.let { "$it 현장" }
        // 📮 우체통으로 — 오늘 만든 /api/shared/update-address 로 가고, 못 가면 모아뒀다 보낸다. (설계 §1-A #2)
        for (sid in shareIds) {
            val payload = org.json.JSONObject().apply {
                put("addr", addr)
                label?.let { put("label", it) }
            }
            runCatching {
                container.outbox.enqueue(
                    com.detailline.callfollowcrm.domain.outbox.OutboxKind.COLLAB_ADDRESS,
                    sid, payload.toString()
                )
            }
        }
        container.outbox.tryNow()
    }

    /**
     * 사장님이 CustomerDetail 에서 명시 액션(상태/메모/이름/예약일 변경)을 하면
     * 그 번호의 오늘자 UNHANDLED 통화들을 모두 SAVED 로 정리.
     * 의미: 통화 단위 알림 처리도 함께 클리어 → 홈에서 "후속 N건 미처리" 노이즈 사라짐.
     */
    private suspend fun markTodayCallsAsHandled() {
        val c = customer.value ?: return
        val (start, end) = com.detailline.callfollowcrm.util.DateTimeUtils.todayBounds()
        runCatching {
            container.callRecordRepository.markUnhandledByPhoneToday(
                phoneNumber = c.phoneNumber,
                customerId = customerId,
                from = start,
                to = end
            )
        }
    }

    /** 사용자가 에이닷 요약 텍스트를 클립보드/입력으로 붙여넣을 때 */
    fun importPastedSummary(context: Context, text: String) {
        AdotSummaryImporter.importPasted(
            context = context,
            container = container,
            text = text,
            customerId = customerId
        )
    }

    fun attachManualRecording(context: Context, uri: Uri) {
        try {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: SecurityException) { /* persistable 아님 */ }
        viewModelScope.launch {
            val name = queryName(context, uri) ?: uri.lastPathSegment.orEmpty()
            container.recordingRepository.add(
                fileUri = uri.toString(),
                fileName = name,
                sourceType = RecordingSourceType.MANUAL_PICK,
                customerId = customerId
            )
        }
    }

    /**
     * 화면 열릴 때 호출. 시스템 통화기록(CallLog) + 에이닷 녹음 폴더 둘 다에서
     * 이 고객 번호의 데이터를 우리 DB로 백필. 알림 못 누른 통화/옛 녹음도 채워짐.
     */
    fun backfillFromSystem(context: Context) {
        val c = customer.value ?: return
        viewModelScope.launch {
            runCatching {
                container.callRecordRepository.syncFromCallLog(context, c.phoneNumber, customerId)
            }
            // 에이닷 폴더가 연결돼 있으면 그 번호의 과거 녹음도 백필
            com.detailline.callfollowcrm.recording.AdotFolderScanner.scanByPhone(
                context = context,
                container = container,
                phoneNumber = c.phoneNumber,
                customerId = customerId
            )
        }
        loadSystemSms()
    }

    /** 인라인 발송 결과 toast 용. */
    private val _toast = MutableStateFlow<String?>(null)
    val toast = _toast.asStateFlow()
    fun consumeToast() { _toast.value = null }

    /** AI 다듬기 진행 중 표시 (스피너 등에 사용). */
    private val _aiPolishing = MutableStateFlow(false)
    val aiPolishing = _aiPolishing.asStateFlow()

    /**
     * 사장님이 입력한 본문을 AI 가 사장님 톤으로 다듬어 반환.
     *
     * 현재는 placeholder — 자체 서버 + LLM 마련 전까지는 안내 토스트만 띄움.
     * 추후 구현:
     *   1) 사장님 톤 학습 데이터 (과거 보낸 SMS 샘플) 를 시스템 프롬프트에 포함
     *   2) 자체 서버의 /polish 엔드포인트 호출
     *   3) onPolished(다듬어진 본문) 콜백으로 화면에 반영
     *
     * UI 측은 이 메서드 시그니처를 그대로 유지하므로, 본문만 교체하면 즉시 동작.
     */
    fun aiPolish(rawBody: String, onPolished: (String) -> Unit) {
        if (rawBody.isBlank()) return
        // TODO: 자체 서버 연동 후 실제 호출. 지금은 안내만.
        _toast.value = "AI 다듬기는 자체 서버 마련 후 활성화돼요"
    }

    /**
     * 고객 상세 문자 섹션에서 인라인으로 직접 발송.
     *
     * 정책: SEND_SMS 권한이 있어야만 발송. 자동응답 외에 사장님의 명시 액션으로
     * 발송을 허용하는 두 번째 예외 경로. (project_ringo.md 의 SEND_SMS 정책 확장)
     *
     * UX: optimistic 으로 _systemSms 에 즉시 prepend → 사용자는 보낸 즉시 화면에서 확인.
     * 시스템 SMS 프로바이더 sent 항목에도 자동으로 들어가므로 다음번 loadSystemSms 시 자동 반영.
     */
    fun sendInlineSms(context: Context, body: String, onResult: (Boolean) -> Unit) {
        val c = customer.value
        if (c == null) {
            _toast.value = "고객 정보가 없어요"
            onResult(false); return
        }
        val trimmed = body.trim()
        if (trimmed.isEmpty()) {
            onResult(false); return
        }
        if (!com.detailline.callfollowcrm.util.SmsSender.hasPermission(context)) {
            _toast.value = "SMS 발송 권한이 필요해요"
            onResult(false); return
        }

        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                com.detailline.callfollowcrm.util.SmsSender.sendDirect(context, c.phoneNumber, trimmed)
            }
            if (ok) {
                // optimistic UI: 보낸 메시지를 즉시 리스트 맨 앞에 (정렬은 dateMs DESC 가정).
                val optimistic = SmsRepository.SmsMessage(
                    id = -System.currentTimeMillis(), // 임시 id (음수로 충돌 회피)
                    address = c.phoneNumber,
                    body = trimmed,
                    dateMs = System.currentTimeMillis(),
                    sent = true
                )
                _systemSms.value = listOf(optimistic) + _systemSms.value

                withContext(NonCancellable) {
                    runCatching {
                        container.messageHistoryRepository.recordAutoSend(
                            phoneNumber = c.phoneNumber,
                            customerId = customerId,
                            templateId = null,
                            body = trimmed,
                            status = com.detailline.callfollowcrm.domain.model.MessageStatus.INLINE_SENT
                        )
                    }
                    // 인라인 발송도 명시 액션 → 오늘자 UNHANDLED 정리
                    markTodayCallsAsHandled()
                }
                _toast.value = "보냈어요"
                onResult(true)
            } else {
                withContext(NonCancellable) {
                    runCatching {
                        container.messageHistoryRepository.recordAutoSend(
                            phoneNumber = c.phoneNumber,
                            customerId = customerId,
                            templateId = null,
                            body = trimmed,
                            status = com.detailline.callfollowcrm.domain.model.MessageStatus.INLINE_FAILED
                        )
                    }
                }
                _toast.value = "문자를 보내지 못했어요 — 다시 해주세요"
                onResult(false)
            }
        }
    }

    /**
     * 시스템 SMS 에서 이 고객 번호와 주고받은 모든 문자 로드 (송+수신).
     * READ_SMS 권한 = onboarding 에서 기본 승인됨. 시스템 설정에서 사장님이 끄면 silent skip.
     */
    fun loadSystemSms() {
        val c = customer.value ?: return
        // cached_messages 는 권한 무관하게 우리 DB → 항상 로드. systemSms 는 권한 필요.
        viewModelScope.launch(Dispatchers.IO) {
            val suffix = PhoneKey.of(c.phoneNumber)
            if (suffix.length >= 7) {
                val cached = runCatching {
                    container.cachedMessageRepository.load(suffix, limit = 500)
                }.getOrDefault(emptyList())
                _cachedSms.value = cached
            }
        }
        if (!container.smsRepository.hasReadPermission()) {
            _systemSms.value = emptyList()
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val list = runCatching {
                container.smsRepository.queryByPhone(c.phoneNumber)
            }.getOrDefault(emptyList())
            _systemSms.value = list
        }
    }

    private fun queryName(context: Context, uri: Uri): String? = try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    } catch (_: Exception) { null }
}

data class CustomerDetailUiState(
    val customer: CustomerEntity?,
    val callRecords: List<CallRecordEntity>,
    val messageHistory: List<MessageHistoryEntity>,
    val recordings: List<RecordingAttachmentEntity>
)
