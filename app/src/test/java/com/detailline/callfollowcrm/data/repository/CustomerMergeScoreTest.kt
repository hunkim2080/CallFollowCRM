package com.detailline.callfollowcrm.data.repository

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 갈라진 손님을 합칠 때 **어느 쪽을 남길지** 고르는 잣대. (2026-09-23 사장님)
 *   잘못 고르면 돈·일정이 붙은 쪽이 지워지므로, 규칙을 여기 못 박아 둔다.
 */
class CustomerMergeScoreTest {

    private fun s(
        name: Boolean = false, addr: Boolean = false, memo: Boolean = false,
        money: Boolean = false, cal: Boolean = false, jobs: Int = 0, id: Int = 100
    ) = CustomerMergeScore.of(name, addr, memo, money, cal, jobs, -id)

    @Test
    fun `일정이 붙은 쪽이 이긴다 - 돈과 약속이 달려 있다`() {
        assertTrue(s(jobs = 1, id = 300) > s(name = true, addr = true, memo = true, id = 10))
    }

    @Test
    fun `일정이 같으면 내용이 더 찬 쪽`() {
        assertTrue(s(jobs = 1, addr = true, money = true, id = 200) > s(jobs = 1, id = 50))
    }

    @Test
    fun `구글 캘린더에 걸린 쪽이 살아있는 쪽이다`() {
        assertTrue(s(jobs = 1, cal = true, id = 300) > s(jobs = 1, id = 10))
    }

    @Test
    fun `내용이 똑같으면 먼저 만들어진 쪽 - id 가 작은 쪽`() {
        assertTrue(s(name = true, id = 41) > s(name = true, id = 169))
        assertTrue(s(id = 11) > s(id = 31))
    }

    @Test
    fun `빈 껍데기는 어떤 내용에도 진다`() {
        assertTrue(s(name = true, id = 999) > s(id = 1))
        assertTrue(s(money = true, id = 999) > s(id = 1))
    }

    @Test
    fun `실제 5247 손님 - 10월6일 쪽이 남아야 한다`() {
        // id208: 주소·메모·일정·금액·캘린더 전부 있음 / id226: 일정만, 나머지 빈칸
        val keep = s(addr = true, memo = true, money = true, cal = true, jobs = 1, id = 208)
        val drop = s(jobs = 1, money = true, id = 226)
        assertTrue(keep > drop)
    }
}
