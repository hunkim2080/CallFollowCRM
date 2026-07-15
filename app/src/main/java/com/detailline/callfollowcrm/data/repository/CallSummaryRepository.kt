package com.detailline.callfollowcrm.data.repository

import com.detailline.callfollowcrm.data.local.dao.CallSummaryDao
import com.detailline.callfollowcrm.data.local.entity.CallSummaryEntity
import kotlinx.coroutines.flow.Flow

class CallSummaryRepository(private val dao: CallSummaryDao) {

    fun observeByCustomer(customerId: Long): Flow<List<CallSummaryEntity>> =
        dao.observeByCustomer(customerId)

    /** 채팅 화면용 — 번호 끝 8자리로 통화요약 관찰(통화기록과 같은 suffix 매칭). */
    fun observeByPhoneSuffix(suffix: String): Flow<List<CallSummaryEntity>> =
        dao.observeByPhoneSuffix(suffix)

    /** 상담함 한줄 미리보기용 — 전체 통화요약(최신순). */
    fun observeAll(): Flow<List<CallSummaryEntity>> = dao.observeAll()

    suspend fun upsert(summary: CallSummaryEntity): Long = dao.insert(summary)

    suspend fun update(summary: CallSummaryEntity) = dao.update(summary)

    suspend fun findById(id: Long): CallSummaryEntity? = dao.findById(id)

    /**
     * 같은 번호 + 같은 녹음 시각(±20초) 의 요약이 이미 있으면 그 row 를 반환.
     * 에이닷에서 같은 통화의 요약을 두 번 공유했을 때 중복 저장을 막는다(같은 파일=같은 시각이라 정확히 잡힘).
     *
     * ±2분이던 것을 ±20초로 좁힘 (2026-06-18 사장님 010-6647-9114 버그): 같은 번호로 28초 간격의
     *   두 통화(11초 / 5:49)가 있을 때, 5:49 통화의 녹음이 11초 통화 요약을 "이미 있음"으로 오인해
     *   자동스캔이 건너뛰고 탭하면 거짓 "이미 요약됨"이 떴음. 요약.recordedAt 은 녹음 파일 시각과
     *   정확히 같으므로(±0초), 좁은 창이 같은 녹음 재공유는 그대로 막고 '다른 통화'는 구분한다.
     */
    suspend fun findExistingNear(phoneNumber: String, recordedAt: Long): CallSummaryEntity? {
        val window = 20 * 1000L
        dao.findByPhoneAndTime(phoneNumber, recordedAt - window, recordedAt + window)?.let { return it }
        // 레거시 row 구제 (2026-07-15) — 2026-07-15 이전 텍스트 경로는 **본문 시각(분 단위, 초=0)** 으로 저장했다.
        //   조회는 파일명 시각(초까지)이라 최대 59초가 어긋나 위 ±20초 창으로는 못 찾고,
        //   → 이미 요약한 옛 통화를 다시 요약(돈) + "통화요약 완료" 알림이 계속 떴다.
        //   그 '분'의 정각(초=0)에 딱 걸린 row 는 같은 통화의 레거시 기록으로 본다.
        //   (그 시절 저장 방식이 분 안을 구분하지 못했으므로 어차피 같은 분의 통화는 한 row 로 합쳐져 있다.)
        val minute = minuteFloor(recordedAt)
        if (minute == recordedAt) return null   // 초=0 통화는 위 창에서 이미 확인됨
        return dao.findByPhoneAndTime(phoneNumber, minute, minute)
    }

    companion object {
        /** 그 시각이 속한 '분'의 정각(초·밀리초 버림). 레거시(분 단위) 요약 row 를 찾을 때 쓴다. */
        fun minuteFloor(ms: Long): Long = ms - Math.floorMod(ms, 60_000L)
    }

    /** 같은 번호의 orphan 요약을 customerId 로 일괄 연결. */
    suspend fun linkOrphansToCustomer(phoneNumber: String, customerId: Long): Int =
        dao.linkOrphansToCustomer(phoneNumber, customerId)
}
