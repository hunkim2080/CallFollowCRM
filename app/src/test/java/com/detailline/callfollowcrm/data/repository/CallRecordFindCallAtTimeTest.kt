package com.detailline.callfollowcrm.data.repository

import com.detailline.callfollowcrm.data.local.dao.CallRecordDao
import com.detailline.callfollowcrm.data.local.entity.CallRecordEntity
import com.detailline.callfollowcrm.domain.model.CallType
import com.detailline.callfollowcrm.domain.model.HandledStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub

/**
 * CallRecordRepository.findCallAtTime 단위테스트 (2026-07-16 사장님 현장).
 *
 * 왜 있나: 폰에 따라 통화녹음 파일명에 **번호 대신 연락처 이름**이 들어간다(`통화 남이편_260716_112558.m4a`).
 *   그럼 번호를 파일명에서 못 얻으니 "그 시각에 하던 통화"로 되찾는데 — 여기서 틀리면
 *   **엉뚱한 고객 통화에 남의 요약이 붙는다**. 그게 못 찾는 것보다 훨씬 나쁘므로,
 *   "조금이라도 애매하면 null(안 붙임)" 규칙을 이 테스트가 지킨다.
 */
class CallRecordFindCallAtTimeTest {

    private val t0 = 1_752_000_000_000L   // 기준 시각 (고정값 — 테스트 재현성)
    private val min = 60_000L

    private fun call(
        id: Long,
        phone: String,
        startedAt: Long,
        durationSec: Long,
        type: CallType = CallType.INCOMING
    ) = CallRecordEntity(
        id = id,
        phoneNumber = phone,
        callType = type.name,
        duration = durationSec,
        startedAt = startedAt,
        endedAt = startedAt + durationSec * 1000,
        handledStatus = HandledStatus.SAVED.name
    )

    private fun repoWith(rows: List<CallRecordEntity>): CallRecordRepository {
        val dao = mock<CallRecordDao>().stub {
            onBlocking { findEndedBetween(any(), any()) }.thenReturn(rows)
        }
        return CallRecordRepository(dao)
    }

    /** 통화 중에 녹음이 시작됐다 = 그 통화 것. */
    @Test fun findsCallCoveringTheRecordingTime() = runTest {
        val repo = repoWith(listOf(call(7, "010-8005-2080", t0, 300)))
        val found = repo.findCallAtTime(t0 + 5_000)          // 통화 시작 5초 뒤 녹음 시작
        assertEquals(7L, found?.id)
    }

    /** 녹음이 통화 끝난 한참 뒤 = 그 통화 것이 아님. */
    @Test fun nullWhenRecordingIsOutsideTheCall() = runTest {
        val repo = repoWith(listOf(call(7, "01080052080", t0, 60)))
        assertNull(repo.findCallAtTime(t0 + 30 * min))
    }

    /** 부재중/거절은 녹음 자체가 없다 → 절대 붙이지 않는다. */
    @Test fun ignoresMissedAndRejectedCalls() = runTest {
        val repo = repoWith(
            listOf(
                call(1, "01011112222", t0, 0, CallType.MISSED),
                call(2, "01033334444", t0, 0, CallType.REJECTED)
            )
        )
        assertNull(repo.findCallAtTime(t0))
    }

    /** ★ 서로 다른 번호의 통화가 그 시각에 겹치면(통화 중 대기 등) 판단 포기 — 엉뚱한 고객 방지. */
    @Test fun nullWhenTwoDifferentNumbersOverlap() = runTest {
        val repo = repoWith(
            listOf(
                call(1, "01011112222", t0, 600),
                call(2, "01033334444", t0 + min, 60)
            )
        )
        assertNull(repo.findCallAtTime(t0 + 90_000))
    }

    /** 같은 통화가 번호 형식 차이로 2 row 인 건(중복 sync) 한 통화로 본다 → 포기하지 않는다. */
    @Test fun sameCallStoredTwiceInDifferentFormatsStillResolves() = runTest {
        val repo = repoWith(
            listOf(
                call(1, "010-8005-2080", t0, 300),
                call(2, "+821080052080", t0, 300)
            )
        )
        val found = repo.findCallAtTime(t0 + 5_000)
        assertEquals("중복 row 는 같은 통화 → 붙여야 함", true, found != null)
        assertEquals(true, found!!.phoneNumber.filter { it.isDigit() }.endsWith("80052080"))
    }

    /** 녹음이 통화 시작보다 살짝 이르거나(벨~응답 오차) 종료 직후여도 slack(기본 2분) 안이면 그 통화. */
    @Test fun toleratesSmallSkewAroundCallEdges() = runTest {
        val repo = repoWith(listOf(call(9, "01080052080", t0, 120)))
        assertEquals(9L, repo.findCallAtTime(t0 - min)?.id)                 // 시작 1분 전
        assertEquals(9L, repo.findCallAtTime(t0 + 120_000 + min)?.id)       // 종료 1분 뒤
        assertNull(repo.findCallAtTime(t0 - 5 * min))                       // 5분 전 = 남
    }

    /** 그 시각에 통화가 아예 없었다 = 통화녹음이 아닌 오디오(음성메모 등) → 안 붙인다. */
    @Test fun nullWhenNoCallAtAll() = runTest {
        val repo = repoWith(emptyList())
        assertNull(repo.findCallAtTime(t0))
    }

    /** 여러 통화가 있어도 그 시각을 감싸는 통화만 고른다. */
    @Test fun picksOnlyTheCallCoveringThatMoment() = runTest {
        val repo = repoWith(
            listOf(
                call(1, "01011112222", t0 - 60 * min, 300),
                call(2, "01033334444", t0, 300),
                call(3, "01055556666", t0 + 60 * min, 300)
            )
        )
        assertEquals(2L, repo.findCallAtTime(t0 + 10_000)?.id)
    }
}
