package com.detailline.callfollowcrm.domain.schedule

import com.detailline.callfollowcrm.domain.schedule.SameDayGuard.Existing
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 🖐 하루 두 현장은 정상이고, 연타만 막는다. (2026-09-28)
 *
 * 이 테스트가 지키는 약속 — 사장님이 아침 현장·오후 현장을 같은 날 같은 번호로 넣어도
 * **둘 다 남는다.** 전엔 둘째가 말없이 사라졌고 화면은 「일정 등록 완료」라고 했다.
 */
class SameDayGuardTest {

    private val now = 1_700_000_000_000L
    /** 방금 누른 게 아니라, 아까(한참 전) 만들어진 건. */
    private val long_ago = now - 60 * 60 * 1000L

    @Test fun `그 날 아무것도 없으면 만든다`() {
        assertFalse(SameDayGuard.isRetap(emptyList(), "수원시 영통구 1", now))
    }

    @Test fun `하루 두 현장 - 주소가 다르면 만든다`() {
        val had = listOf(Existing("수원시 영통구 1", long_ago))
        assertFalse(
            "아침 현장과 오후 현장은 다른 일이다",
            SameDayGuard.isRetap(had, "용인시 기흥구 2", now)
        )
    }

    @Test fun `같은 날 같은 주소면 막는다`() {
        val had = listOf(Existing("수원시 영통구 1", long_ago))
        assertTrue(SameDayGuard.isRetap(had, "수원시 영통구 1", now))
    }

    @Test fun `주소 앞뒤 공백은 같은 것으로 본다`() {
        val had = listOf(Existing(" 수원시 영통구 1 ", long_ago))
        assertTrue(SameDayGuard.isRetap(had, "수원시 영통구 1", now))
    }

    @Test fun `연타 - 주소가 없어도 몇 초 안이면 막는다`() {
        val had = listOf(Existing(null, now - 1_500L))
        assertTrue("손가락이 두 번 닿은 것", SameDayGuard.isRetap(had, null, now))
    }

    @Test fun `연타 시간이 지났고 주소가 비었으면 만든다`() {
        // 주소를 나중에 적는 사장님도 있다. 비었다고 영영 못 넣게 하면 안 된다.
        val had = listOf(Existing(null, long_ago))
        assertFalse(SameDayGuard.isRetap(had, null, now))
    }

    @Test fun `한쪽 주소만 있으면 다른 현장으로 본다`() {
        // 잘못 막으면 일정이 사라지고(되돌릴 길이 없다), 잘못 만들면 지우면 된다.
        val had = listOf(Existing("수원시 영통구 1", long_ago))
        assertFalse(SameDayGuard.isRetap(had, null, now))
        assertFalse(SameDayGuard.isRetap(listOf(Existing(null, long_ago)), "수원시 영통구 1", now))
    }

    @Test fun `여러 건 중 하나라도 같은 현장이면 막는다`() {
        val had = listOf(
            Existing("용인시 기흥구 2", long_ago),
            Existing("수원시 영통구 1", long_ago)
        )
        assertTrue(SameDayGuard.isRetap(had, "수원시 영통구 1", now))
    }

    @Test fun `폰 시계가 뒤로 간 기록은 연타로 안 본다`() {
        // 만든 시각이 지금보다 미래면(시계 조정·복원) 음수가 된다. 그걸 연타로 세면 안 된다.
        val had = listOf(Existing(null, now + 5_000L))
        assertFalse(SameDayGuard.isRetap(had, null, now))
    }
}
