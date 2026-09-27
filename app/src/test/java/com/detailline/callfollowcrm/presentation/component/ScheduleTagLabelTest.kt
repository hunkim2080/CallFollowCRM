package com.detailline.callfollowcrm.presentation.component

import com.detailline.callfollowcrm.data.local.entity.CustomerEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 날짜 태그 문구. (2026-09-16 사장님: "태그는 2종류 — 분류 태그, 날짜 관련 태그. 둘다 쓰면 되지 않나")
 *
 * 왜 테스트가 필요한가:
 *   같은 고객이 화면마다 다른 말로 불리고 있었다 — 고객정보에선 「신규」, 홈에선 「시공 D-DAY」.
 *   계산이 두 벌이었기 때문(customerStatusOf vs HomeScreen.recentStatusTag). 한 벌로 합쳤으니
 *   **이제 어느 화면에서든 같은 말이 나와야 한다.** 그걸 여기서 못 박는다.
 *
 * ⚠️ [customerStatusOf] 의 반환값(예약/잔금미수/완료/신규/미전환)은 **고객관리 화면의 필터 값**이다.
 *    그래서 계산은 그대로 두고, 표시 문구만 '예약 → 시공 D-N' 으로 바꾼다.
 */
class ScheduleTagLabelTest {

    private val DAY = 86_400_000L
    private val today0 = 1_757_980_800_000L   // 임의의 자정
    private val now = today0 + 10 * 3_600_000L

    private fun customer(
        workDate: Long? = null,
        total: Long? = null,
        deposit: Long? = null,
        balancePaidAt: Long? = null,
        completedAt: Long? = null,
        createdAt: Long = now
    ) = CustomerEntity(
        phoneNumber = "01011112222",
        scheduledWorkDate = workDate,
        totalAmount = total,
        depositAmount = deposit,
        balancePaidAt = balancePaidAt,
        workCompletedAt = completedAt,
        createdAt = createdAt,
        updatedAt = createdAt
    )

    private fun label(c: CustomerEntity) = scheduleTagLabel(c, today0, now)

    @Test
    fun `오늘 시공이면 D-DAY`() {
        assertEquals("시공 D-DAY", label(customer(workDate = today0)))
    }

    @Test
    fun `사흘 뒤면 D-3`() {
        assertEquals("시공 D-3", label(customer(workDate = today0 + 3 * DAY)))
    }

    @Test
    fun `내일이면 D-1`() {
        assertEquals("시공 D-1", label(customer(workDate = today0 + DAY)))
    }

    @Test
    fun `시공 지났는데 잔금이 남았으면 잔금미수`() {
        // 돈 받을 게 남은 건 절대 조용히 사라지면 안 된다
        assertEquals("잔금미수", label(customer(workDate = today0 - DAY, total = 1_000_000L, deposit = 200_000L)))
    }

    @Test
    fun `잔금까지 받았으면 완료`() {
        assertEquals("완료", label(customer(
            workDate = today0 - DAY, total = 1_000_000L, deposit = 200_000L, balancePaidAt = now
        )))
    }

    @Test
    fun `오늘 생긴 손님만 신규`() {
        assertEquals("신규", label(customer(createdAt = now)))
    }

    @Test
    fun `하루만 지나도 신규가 아니다`() {
        // 사장님: "왜 신규가 아닌데 자꾸 신규칩이 붙어다니는 거지. 하루만 지나도 없어지게" (2026-09-27)
        //   전엔 14일이라 보름 된 손님까지 「신규」였다 — 딱지가 아무 뜻도 없었다.
        assertEquals("", label(customer(createdAt = today0 - 1)))
    }

    @Test
    fun `오래 조용하면 딱지가 없다`() {
        // 「미전환」을 없앴다 (사장님 "없어도 될 태그인듯"). 그 자리는 **빈 글자**다.
        //   ⚠️ 빈 글자를 딱지로 그리면 글자 없는 상자만 남는다 — 실제로 그렇게 났었다.
        //      (2026-09-27 사장님 "태그가 짤린거같은데.. 뭐지")
        assertEquals("", label(customer(createdAt = now - 30 * DAY)))
    }

    @Test
    fun `빈 글자는 아무 화면에서도 딱지가 아니다`() {
        // 그리는 쪽(ScheduleTag·CustomerStatusTag·CustomerTags)이 전부 이 값을 보고 건너뛴다.
        //   여기서 못 박아 둔다 — 빈 글자가 나오는 길이 있다는 것 자체를.
        val quiet = customer(createdAt = now - 30 * DAY)
        assertTrue(label(quiet).isBlank())
        assertTrue(customerStatusOf(quiet, today0, now).isBlank())
    }

    @Test
    fun `표시 문구를 바꿔도 필터 값은 그대로다`() {
        // 고객관리 필터가 쓰는 값은 '예약' — 표시만 'D-3' 으로 바뀌어야 한다.
        val c = customer(workDate = today0 + 3 * DAY)
        assertEquals("예약", customerStatusOf(c, today0, now))
        assertEquals("시공 D-3", label(c))
    }
}
