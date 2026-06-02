package com.detailline.callfollowcrm.data.repository

import android.content.Context
import com.detailline.callfollowcrm.data.local.dao.CallRecordDao
import com.detailline.callfollowcrm.data.local.entity.CallRecordEntity
import com.detailline.callfollowcrm.domain.model.CallType
import com.detailline.callfollowcrm.domain.model.HandledStatus
import com.detailline.callfollowcrm.util.CallLogHelper
import kotlinx.coroutines.flow.Flow

class CallRecordRepository(private val dao: CallRecordDao) {

    fun observeRecent(limit: Int = 50): Flow<List<CallRecordEntity>> = dao.observeRecent(limit)
    fun observeBetween(from: Long, to: Long): Flow<List<CallRecordEntity>> = dao.observeBetween(from, to)
    fun observeByPhone(phone: String): Flow<List<CallRecordEntity>> = dao.observeByPhone(phone)
    /** 채팅 통화 구간 표시용 — 끝 8자리 suffix 매칭. */
    fun observeByPhoneSuffix(suffix: String): Flow<List<CallRecordEntity>> = dao.observeByPhoneSuffix(suffix)
    fun countUnhandled(from: Long, to: Long): Flow<Int> = dao.countUnhandled(from, to)
    fun countHandled(from: Long, to: Long): Flow<Int> = dao.countHandled(from, to)
    fun observeMissedSince(from: Long): Flow<List<CallRecordEntity>> = dao.observeMissedSince(from)
    /** 들어온 통화(수신·부재중·거절) since — "오늘 신규 문의" 집계용 (발신 제외). */
    fun observeInboundSince(from: Long): Flow<List<CallRecordEntity>> = dao.observeInboundSince(from)
    fun observeDistinctPhonesBefore(before: Long): Flow<List<String>> = dao.observeDistinctPhonesBefore(before)

    suspend fun findById(id: Long): CallRecordEntity? = dao.findById(id)

    /** 이 번호로 통화 기록이 몇 번 있는지. 1 이면 방금 만든 게 처음. */
    suspend fun countByPhone(phone: String): Int = dao.countByPhone(phone)

    suspend fun create(
        phoneNumber: String,
        callType: CallType,
        duration: Long,
        startedAt: Long?,
        endedAt: Long,
        linkedCustomerId: Long? = null,
        handledStatus: HandledStatus = HandledStatus.UNHANDLED
    ): Long {
        // 2026-05-30 #9 통점 fix:
        //   CallStateReceiver (정적 Manifest) + Application.TelephonyCallback (동적 등록) 둘 다
        //   같은 통화 종료 이벤트 받음 → 각각 create() 호출 → 같은 startedAt 으로 2 row INSERT →
        //   HomeViewModel.groupBy(phone, day) 의 list.size = 2 → "오늘 2통" 잘못 표시.
        //   기존 syncFromCallLog 는 이미 dedup 있는데 create() 만 없었음.
        //   해결: 같은 (phone, startedAt) 있으면 기존 id 반환 (INSERT skip).
        //   startedAt == null 케이스 (번호없음/권한 X) 는 dedup 불가 — 그대로 INSERT (rare path).
        if (startedAt != null) {
            val existingId = dao.findIdByPhoneAndStarted(phoneNumber, startedAt)
            if (existingId != null) return existingId
        }
        val entity = CallRecordEntity(
            phoneNumber = phoneNumber,
            callType = callType.name,
            duration = duration,
            startedAt = startedAt,
            endedAt = endedAt,
            handledStatus = handledStatus.name,
            linkedCustomerId = linkedCustomerId
        )
        return dao.insert(entity)
    }

    suspend fun markHandled(id: Long, status: HandledStatus, linkedCustomerId: Long? = null) {
        val r = dao.findById(id) ?: return
        dao.update(r.copy(handledStatus = status.name, linkedCustomerId = linkedCustomerId ?: r.linkedCustomerId))
    }

    /**
     * 특정 번호의 [from~to] 윈도우 안 UNHANDLED 통화 일괄 SAVED 처리.
     * CustomerDetail 에서 사장님이 후속 액션(상태/메모/이름/예약일 변경)을 했을 때 호출.
     * @return 영향 받은 row 수
     */
    suspend fun markUnhandledByPhoneToday(
        phoneNumber: String,
        customerId: Long?,
        from: Long,
        to: Long
    ): Int = dao.markUnhandledByPhoneToday(phoneNumber, customerId, from, to)

    /**
     * 시스템 통화기록(CallLog)에서 해당 번호의 통화들을 우리 DB에 동기화한다.
     * BroadcastReceiver가 놓쳤거나 사용자가 알림을 못 눌렀어도, 번호만 알면 과거 기록을 채워넣을 수 있다.
     *
     * - 입력 phoneNumber 는 사용자가 입력한 그대로 (예: "010-1234-5678" 또는 "01012345678")
     * - 같은 번호 + 같은 startedAt 레코드가 이미 있으면 스킵 (중복 방지)
     * - linkedCustomerId 를 전달하면 매칭된 CallRecord 에 자동 연결
     *
     * @return 새로 insert된 레코드 개수
     */
    suspend fun syncFromCallLog(
        context: Context,
        phoneNumber: String,
        linkedCustomerId: Long? = null,
        handledStatus: HandledStatus = HandledStatus.SAVED
    ): Int {
        val entries = CallLogHelper.queryByPhone(context, phoneNumber)
        if (entries.isEmpty()) return 0

        var inserted = 0
        for (e in entries) {
            // startedAt 으로 dedup (CallLog DATE = 통화 시작 시각)
            if (dao.countByPhoneAndStarted(phoneNumber, e.date) > 0) continue
            dao.insert(
                CallRecordEntity(
                    phoneNumber = phoneNumber,
                    callType = e.type.name,
                    duration = e.duration,
                    startedAt = e.date,
                    endedAt = e.date + e.duration * 1000,
                    handledStatus = handledStatus.name,
                    linkedCustomerId = linkedCustomerId
                )
            )
            inserted++
        }
        return inserted
    }

    /**
     * 시스템 CallLog 의 최근 N건 → Room sync (2026-05-28 사장님 통점 fix).
     *
     * **왜 필요한가**: Android 12+ / OneUI 에서 정적 Manifest PHONE_STATE BroadcastReceiver 가
     * 누락되는 케이스 多. 그러면 CallStateReceiver.onReceive 가 안 불려 Room INSERT 안 됨 →
     * RING-GO 가 통화를 못 봄. 시스템 CallLog 는 항상 정확하므로 거기서 폴링.
     *
     * 호출 시점:
     *  - HomeScreen 진입 (LaunchedEffect)
     *  - Pull-to-refresh
     *  - 통화 끝났을 가능성 있는 어떤 시점이든
     *
     * dedup: (phoneNumber, startedAt) 기준. 같은 통화 중복 INSERT 안 됨.
     *
     * @return 새로 insert 된 row 수
     */
    suspend fun syncRecentCallLog(context: Context, limit: Int = 30): Int {
        val entries = CallLogHelper.queryRecent(context, limit)
        if (entries.isEmpty()) return 0

        var inserted = 0
        for (e in entries) {
            val phone = e.phoneNumber
            if (phone.isBlank()) continue
            if (dao.countByPhoneAndStarted(phone, e.date) > 0) continue
            dao.insert(
                CallRecordEntity(
                    phoneNumber = phone,
                    callType = e.type.name,
                    duration = e.duration,
                    startedAt = e.date,
                    endedAt = e.date + e.duration * 1000,
                    // 처리 상태는 UNHANDLED (= 미확인). 사장님이 답장/메모 등 액션하면 SAVED 로 전환.
                    // 단, 부재중/수신 외엔 UNHANDLED 의미 약함 → 기존 정책 따름.
                    handledStatus = HandledStatus.UNHANDLED.name,
                    linkedCustomerId = null
                )
            )
            inserted++
        }
        return inserted
    }
}
