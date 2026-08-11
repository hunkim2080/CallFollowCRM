package com.detailline.callfollowcrm.data.repository

import com.detailline.callfollowcrm.data.local.dao.JobCrewDao
import com.detailline.callfollowcrm.data.local.entity.JobCrewEntity
import com.detailline.callfollowcrm.util.DateTimeUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 일당 배정 멱등성 검증 — **돈이 걸린 계산**이라 테스트로 못 박는다.
 *
 * job_crew 엔 (workerId, customerId, dayStartMs) 유니크 인덱스가 없고 DAO 는 @Insert 뿐이라,
 * 두 번 배정하면 행이 2개 생기고 CashFlowCalc 가 일당을 **두 번 지출**로 계산한다(25만 → 50만).
 * 같은 번호·같은 날로 "일정 추가"를 두 번만 해도 재현되는 실제 버그.
 */
class JobCrewRepositoryTest {

    /** 메모리 가짜 DAO — 진짜 Room 과 같은 규칙(유니크 인덱스 없음 = 막 넣으면 중복)으로 동작. */
    private class FakeDao : JobCrewDao {
        val rows = mutableListOf<JobCrewEntity>()
        private var seq = 0L

        override fun observeAll(): Flow<List<JobCrewEntity>> = flowOf(rows.toList())
        override fun observeByWorker(workerId: Long): Flow<List<JobCrewEntity>> =
            flowOf(rows.filter { it.workerId == workerId })
        override fun observeByJob(customerId: Long, dayStartMs: Long): Flow<List<JobCrewEntity>> =
            flowOf(rows.filter { it.customerId == customerId && it.dayStartMs == dayStartMs })

        override suspend fun findAssignment(workerId: Long, customerId: Long, dayStartMs: Long): JobCrewEntity? =
            rows.firstOrNull { it.workerId == workerId && it.customerId == customerId && it.dayStartMs == dayStartMs }

        override suspend fun updateWage(id: Long, wage: Long) {
            val i = rows.indexOfFirst { it.id == id }
            if (i >= 0) rows[i] = rows[i].copy(wage = wage)
        }

        override suspend fun insert(entity: JobCrewEntity): Long {
            val id = ++seq
            rows += entity.copy(id = id)   // 유니크 검사 없음 = 부르는 쪽이 막아야 함
            return id
        }

        override suspend fun deleteById(id: Long) { rows.removeAll { it.id == id } }
        override suspend fun deleteAssignment(workerId: Long, customerId: Long, dayStartMs: Long) {
            rows.removeAll { it.workerId == workerId && it.customerId == customerId && it.dayStartMs == dayStartMs }
        }
        override suspend fun deleteByCustomer(customerId: Long) { rows.removeAll { it.customerId == customerId } }
        override suspend fun deleteByCustomerAndDay(customerId: Long, dayStartMs: Long) {
            rows.removeAll { it.customerId == customerId && it.dayStartMs == dayStartMs }
        }
    }

    private val dao = FakeDao()
    private val repo = JobCrewRepository(dao)
    private val day = DateTimeUtils.startOfDay(1_784_000_000_000L)

    @Test fun `같은 사람을 같은 시공에 두 번 배정해도 기록은 하나 - 일당 이중 차감 방지`() = runBlocking {
        repo.assign(workerId = 7, workerName = "하우스픽", customerId = 3, dayMs = day, wage = 250_000)
        repo.assign(workerId = 7, workerName = "하우스픽", customerId = 3, dayMs = day, wage = 250_000)
        assertEquals("배정이 두 번이면 그 날 일당이 두 배로 빠진다", 1, dao.rows.size)
        assertEquals(250_000L, dao.rows[0].wage)
    }

    @Test fun `예약취소 날짜한정 삭제 - 그 시공일 일당만 지우고 과거 지급 일당은 보존`() = runBlocking {
        // 돈 정확성 감사 rank5: 재방문 고객의 6월(이미 지급)·7월(취소할) 일당이 같이 있을 때
        //   deleteByCustomerAndDay(그 시공일)만 지워야 과거 지출이 안 사라진다.
        val day1 = DateTimeUtils.startOfDay(1_781_000_000_000L)  // 과거(이미 지급)
        val day2 = DateTimeUtils.startOfDay(1_784_000_000_000L)  // 취소할 시공일
        repo.assign(workerId = 1, workerName = "6월일당", customerId = 5, dayMs = day1, wage = 250_000)
        repo.assign(workerId = 2, workerName = "7월일당", customerId = 5, dayMs = day2, wage = 300_000)
        repo.deleteByCustomerAndDay(customerId = 5, dayMs = day2)
        assertEquals("취소한 시공일 일당만 삭제되어야", 1, dao.rows.size)
        assertEquals("과거(day1) 지급 일당은 보존", day1, dao.rows[0].dayStartMs)
    }

    @Test fun `날짜에 시각이 섞여 들어와도 같은 날이면 한 건 - startOfDay 로 정규화`() = runBlocking {
        repo.assign(7, "하우스픽", 3, day + 9 * 60 * 60 * 1000L, 250_000)   // 오전 9시
        repo.assign(7, "하우스픽", 3, day + 15 * 60 * 60 * 1000L, 250_000)  // 오후 3시
        assertEquals(1, dao.rows.size)
        assertEquals(day, dao.rows[0].dayStartMs)
    }

    @Test fun `다시 배정하면 일당 금액이 갱신된다 - 행은 그대로 하나`() = runBlocking {
        repo.assign(7, "하우스픽", 3, day, 250_000)
        repo.assign(7, "하우스픽", 3, day, 220_000)   // 단가 조정
        assertEquals(1, dao.rows.size)
        assertEquals(220_000L, dao.rows[0].wage)
    }

    @Test fun `사람·현장·날짜가 다르면 각각 따로 쌓인다`() = runBlocking {
        repo.assign(7, "하우스픽", 3, day, 250_000)
        repo.assign(8, "해시줄눈", 3, day, 220_000)                   // 다른 사람, 같은 현장
        repo.assign(7, "하우스픽", 4, day, 250_000)                   // 같은 사람, 다른 현장
        repo.assign(7, "하우스픽", 3, day + DateTimeUtils.DAY_MS, 250_000)  // 같은 사람·현장, 다음 날
        assertEquals(4, dao.rows.size)
    }

    @Test fun `배정 빼기 - 그 사람만 빠지고 나머지는 남는다`() = runBlocking {
        repo.assign(7, "하우스픽", 3, day, 250_000)
        repo.assign(8, "해시줄눈", 3, day, 220_000)
        repo.unassign(workerId = 7, customerId = 3, dayMs = day)
        assertEquals(1, dao.rows.size)
        assertEquals(8L, dao.rows[0].workerId)
        assertNull(dao.findAssignment(7, 3, day))
    }

    @Test fun `음수 일당은 0 으로 - 이상 입력이 마이너스 지출로 뒤집히지 않게`() = runBlocking {
        repo.assign(7, "하우스픽", 3, day, -50_000)
        assertEquals(0L, dao.rows[0].wage)
    }
}
