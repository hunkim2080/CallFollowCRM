package com.detailline.callfollowcrm.data.repository

import android.content.Context
import com.detailline.callfollowcrm.data.local.dao.CallRecordDao
import com.detailline.callfollowcrm.data.local.entity.CallRecordEntity
import com.detailline.callfollowcrm.domain.model.CallType
import com.detailline.callfollowcrm.domain.model.HandledStatus
import com.detailline.callfollowcrm.util.CallLogHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class CallRecordRepository(private val dao: CallRecordDao) {

    // 2026-07-08 사장님 "통화 1건인데 카드 2개" 재발 fix — dedup 검사+삽입을 원자적으로 직렬화.
    //   통화 종료 시 CallStateReceiver(정적) + CallLog ContentObserver(syncRecentCallLog) +
    //   TelephonyCallback(syncFromCallLog) 셋이 각자 delay(1500) 뒤 ~동시에 깨어나 같은 CallLog 를 읽고,
    //   각자 "기존 없음" 확인 → 각자 insert 하는 check-then-insert 경합. exact-startedAt dedup 은 맞지만
    //   원자적이지 않아 겹치면 2 row 가 들어감(카드 2개). Mutex 로 검사~삽입을 한 번에 하나만 실행.
    private val insertLock = Mutex()

    /** 번호를 숫자만 + (82→0) 정규화. 통화 dedup 비교용(형식 무시). */
    private fun normDigits(phone: String): String {
        var d = phone.filter { it.isDigit() }
        if (d.startsWith("82")) d = "0" + d.substring(2)
        return d
    }

    /**
     * 통화 dedup 비교 키 — 8자리 이상이면 끝 8자리(+82/010/하이픈 등 형식차 흡수), 그 미만이면 숫자 전체.
     *   2026-07-09 fix: 예전엔 8자리 미만이면 dedup 을 아예 건너뛰어(null) "114"·"112"·"15xx" 같은 짧은 번호가
     *   sync 마다 재삽입 → 한 통화가 수십~수백 row 로 불어났다(실측: 114 통화 1건이 146 row). 짧은 번호는 전체로 비교.
     */
    private fun dedupKey(phone: String): String {
        val d = normDigits(phone)
        return if (d.length >= 8) d.takeLast(8) else d
    }

    /**
     * 원자적 dedup insert — 같은 통화(dedupKey+startedAt)면 기존 id, 아니면 새로 insert.
     *   검사+삽입 전체를 [insertLock] 안에서 실행해 동시 호출(정적 receiver / observer / callback)의 경합을 없앤다.
     * @return (row id, 새로 넣었으면 true / 기존이면 false)
     */
    private suspend fun insertDeduped(entity: CallRecordEntity): Pair<Long, Boolean> = insertLock.withLock {
        val started = entity.startedAt
        if (started != null) {
            existingIdSameCall(entity.phoneNumber, started)?.let { return@withLock it to false }
        }
        dao.insert(entity) to true
    }

    /**
     * 같은 통화가 이미 있으면 그 id. 번호 형식이 경로마다 달라도(원본/저장형식/+82) dedupKey +
     *   startedAt 으로 비교해 중복 INSERT 를 막는다. 숫자가 아예 없으면(번호없음) dedup 불가 → null.
     */
    private suspend fun existingIdSameCall(phone: String, startedAt: Long): Long? {
        val target = dedupKey(phone)
        if (target.isEmpty()) return null
        return dao.findByStartedAt(startedAt)
            .firstOrNull { dedupKey(it.phoneNumber) == target }
            ?.id
    }

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
    /** 통화로 응대한(수신 응답·발신) since — "답장 기다려요"에서 전화로 처리한 건 빼는 용도. */
    fun observeHandledCallsSince(from: Long): Flow<List<CallRecordEntity>> = dao.observeHandledCallsSince(from)
    fun observeDistinctPhonesBefore(before: Long): Flow<List<String>> = dao.observeDistinctPhonesBefore(before)

    suspend fun findById(id: Long): CallRecordEntity? = dao.findById(id)

    /**
     * **그 시각에 하던 통화 1건** — 파일명에 번호 없이 연락처 이름만 든 녹음
     * (`통화 남이편_260716_112558.m4a`) 을 통화에 되찾아 붙일 때 쓴다. (2026-07-16 사장님 현장)
     *
     * 근거: 폰은 한 번에 한 통화만 한다 → **녹음된 시각이 곧 신원**. 파일명 번호에 의존하지 않는다.
     *   (폰마다 저장 규칙이 달라 번호를 안 넣는 기기가 있음 = "녹음 파일 못 찾음"의 진짜 원인)
     *
     * 안전장치 — 조금이라도 애매하면 **null(= 안 붙임)**. 엉뚱한 고객 통화에 붙는 사고가 더 나쁘다:
     *  - 받은/건 통화 + 통화시간 > 0 만 후보 (부재중·거절은 녹음 자체가 없음)
     *  - 녹음 시각이 [통화 시작 - slack ~ 통화 종료 + slack] 안
     *  - 그 창에 **서로 다른 번호**의 통화가 둘 이상 겹치면(통화 중 대기 등) 판단 포기 → null
     *
     * @param atMs 녹음 시각(파일명 기준)
     * @param slackMs 통화 시각과의 허용 오차. 기본 2분 — 녹음 시작이 응답 직후라 시작보다 조금 늦을 수 있음.
     */
    suspend fun findCallAtTime(atMs: Long, slackMs: Long = 2 * 60 * 1000L): CallRecordEntity? {
        val span = 6 * 60 * 60 * 1000L   // 하루치를 다 읽지 않게 앞뒤 6시간만(긴 통화 여유 포함).
        val rows = runCatching { dao.findEndedBetween(atMs - span, atMs + span) }.getOrDefault(emptyList())
        val cands = rows.filter { r ->
            (r.callType == CallType.INCOMING.name || r.callType == CallType.OUTGOING.name) &&
                r.duration > 0 &&
                run {
                    val s = r.startedAt ?: r.endedAt
                    atMs >= s - slackMs && atMs <= r.endedAt + slackMs
                }
        }
        if (cands.isEmpty()) return null
        // 같은 통화가 형식 차이로 2 row 인 것(중복 sync)은 한 통화로 본다. 진짜 다른 번호가 겹치면 포기.
        if (cands.distinctBy { dedupKey(it.phoneNumber) }.size > 1) return null
        return cands.minByOrNull { kotlin.math.abs((it.startedAt ?: it.endedAt) - atMs) }
    }

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
        //   해결: 같은 통화(끝 8자리+startedAt) 있으면 기존 id 반환 (INSERT skip).
        //   2026-06-10: 번호 형식 무시(normDigits) 로 비교 — CallStateReceiver(원본)와
        //     syncFromCallLog(고객 저장형식)가 형식이 달라 dedup 빗나가 발신통화 2개 되던 것 fix.
        //   startedAt == null 케이스 (번호없음/권한 X) 는 dedup 불가 — 그대로 INSERT (rare path).
        //   2026-07-08: 검사+삽입을 insertDeduped(Mutex) 로 원자화 — 동시 호출 경합으로 카드 2개 되던 것 fix.
        val entity = CallRecordEntity(
            phoneNumber = phoneNumber,
            callType = callType.name,
            duration = duration,
            startedAt = startedAt,
            endedAt = endedAt,
            handledStatus = handledStatus.name,
            linkedCustomerId = linkedCustomerId
        )
        return insertDeduped(entity).first
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
            // startedAt + 끝 8자리 dedup (번호 형식 무시 — CallLog DATE = 통화 시작 시각). insertDeduped 로 원자화.
            val (_, isNew) = insertDeduped(
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
            if (isNew) inserted++
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
    /**
     * 첫 실행 1회 — [sinceMs] 이후 시스템 통화기록을 Room 으로 가져온다 ("최근 7일 따라잡기").
     *   syncRecentCallLog 와 동일한 dedup(끝8자리+startedAt) + UNHANDLED 정책.
     *   단 번호별로 묶지 않고 그 기간 통화를 다 넣어 상담함에 최근 전화 고객이 이력대로 보이게.
     *   부재중·미답장은 자동으로 '미확인'에 잡힘. 서버 추천 생성은 하지 않음(비용 0).
     * @return 새로 insert 된 row 수
     */
    suspend fun importCallLogSince(context: Context, sinceMs: Long): Int {
        val entries = CallLogHelper.queryRecentSince(context, sinceMs)
        if (entries.isEmpty()) return 0

        var inserted = 0
        for (e in entries) {
            val phone = e.phoneNumber
            if (phone.isBlank()) continue
            val (_, isNew) = insertDeduped(
                CallRecordEntity(
                    phoneNumber = phone,
                    callType = e.type.name,
                    duration = e.duration,
                    startedAt = e.date,
                    endedAt = e.date + e.duration * 1000,
                    handledStatus = HandledStatus.UNHANDLED.name,
                    linkedCustomerId = null
                )
            )
            if (isNew) inserted++
        }
        return inserted
    }

    suspend fun syncRecentCallLog(context: Context, limit: Int = 30): Int {
        val entries = CallLogHelper.queryRecent(context, limit)
        if (entries.isEmpty()) return 0

        var inserted = 0
        for (e in entries) {
            val phone = e.phoneNumber
            if (phone.isBlank()) continue
            val (_, isNew) = insertDeduped(
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
            if (isNew) inserted++
        }
        return inserted
    }

    /**
     * 유령 중복 통화기록 정리 — 앱 시작 시 self-heal. race(경합)·짧은번호 dedup 누락으로 이미 샌 중복을 청소.
     *   같은 통화(dedupKey + startedAt) 그룹에서 **요약·녹음이 안 붙은 '유령' row 만** 삭제. 데이터가 붙은 형제
     *   (전부 유령이면 min id)는 남긴다 → 데이터 손실 0, 카드 1개로 수렴. 신규 중복은 insertDeduped 로 원천 차단.
     *   ※ Kotlin 으로 구현(자기참조 DELETE @Query 가 기기 SQLite 에서 실행 안 되던 문제 우회) + 결과 로그.
     * @return 삭제한 row 수
     */
    suspend fun cleanupPhantomDuplicates(): Int {
        val rows = dao.allWithStartedAt()
        if (rows.size < 2) return 0
        val linked = HashSet<Long>().apply {
            addAll(dao.summaryLinkedRecordIds())      // 요약이 붙은 통화기록 id
            addAll(dao.recordingLinkedRecordIds())    // 녹음이 붙은 통화기록 id
        }
        val toDelete = ArrayList<Long>()
        rows.groupBy { dedupKey(it.phoneNumber) to it.startedAt }
            .forEach forEachGroup@{ (_, group) ->
                if (group.size < 2) return@forEachGroup
                val withData = group.filter { it.id in linked }
                val survivors: Set<Long> =
                    if (withData.isNotEmpty()) withData.map { it.id }.toSet()
                    else setOf(group.minOf { it.id })      // 전부 유령이면 min id 하나만 보존
                for (r in group) {
                    if (r.id !in survivors && r.id !in linked) toDelete.add(r.id)
                }
            }
        if (toDelete.isEmpty()) return 0
        var deleted = 0
        toDelete.chunked(400).forEach { deleted += dao.deleteByIds(it) }   // IN(...) 변수 한도 회피
        android.util.Log.i("CallDupCleanup", "removed $deleted phantom duplicate call_records (of ${rows.size})")
        return deleted
    }
}
