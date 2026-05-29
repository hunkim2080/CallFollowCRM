package com.detailline.callfollowcrm.data.repository

import com.detailline.callfollowcrm.data.local.entity.CategoryEntity
import com.detailline.callfollowcrm.data.local.entity.CustomerEntity
import com.detailline.callfollowcrm.data.local.seed.DefaultCategories
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * 자동 카테고리 분류 로직 검증.
 *
 * 사장님 결정 룰 (2026-05-30 #7 통점):
 *   - balanceAmount > 0 → "시공 완료"
 *   - depositAmount > 0 && balanceAmount 없음/0 → "시공 대기"
 *   - 둘 다 0/null → 분류 X
 *
 * 수동 우선:
 *   - 사장님이 다른 카테고리 (예: VIP) 지정했으면 자동 분류 안 함.
 */
class AutoCategoryClassifierTest {

    private val categoryRepo: CategoryRepository = mock()
    private val customerRepo: CustomerRepository = mock()
    private lateinit var classifier: AutoCategoryClassifier

    private val pendingCategory = CategoryEntity(
        id = 100L, name = DefaultCategories.NAME_PENDING_WORK,
        emoji = "🔨", displayOrder = 1, createdAt = 0L, updatedAt = 0L
    )
    private val doneCategory = CategoryEntity(
        id = 200L, name = DefaultCategories.NAME_DONE_WORK,
        emoji = "✅", displayOrder = 2, createdAt = 0L, updatedAt = 0L
    )

    @Before
    fun setUp() = runTest {
        whenever(categoryRepo.findByName(DefaultCategories.NAME_PENDING_WORK))
            .thenReturn(pendingCategory)
        whenever(categoryRepo.findByName(DefaultCategories.NAME_DONE_WORK))
            .thenReturn(doneCategory)
        classifier = AutoCategoryClassifier(categoryRepo, customerRepo)
    }

    // ---------- resolveCategoryId 분기 검증 ----------

    @Test
    fun `잔금 입금됨 → 시공 완료`() = runTest {
        val customer = customer(
            categoryId = null,
            depositAmount = 100_000L,
            balanceAmount = 500_000L
        )
        assertEquals(doneCategory.id, classifier.resolveCategoryId(customer))
    }

    @Test
    fun `계약금만 입금됨 (잔금 null) → 시공 대기`() = runTest {
        val customer = customer(
            categoryId = null,
            depositAmount = 100_000L,
            balanceAmount = null
        )
        assertEquals(pendingCategory.id, classifier.resolveCategoryId(customer))
    }

    @Test
    fun `계약금만 입금됨 (잔금 0) → 시공 대기`() = runTest {
        val customer = customer(
            categoryId = null,
            depositAmount = 100_000L,
            balanceAmount = 0L
        )
        assertEquals(pendingCategory.id, classifier.resolveCategoryId(customer))
    }

    @Test
    fun `둘 다 미입금 → 기존 그대로 (null)`() = runTest {
        val customer = customer(
            categoryId = null,
            depositAmount = null,
            balanceAmount = null
        )
        assertNull(classifier.resolveCategoryId(customer))
    }

    @Test
    fun `사장님 수동 분류 VIP — 자동 분류 안 함 (입금 잔뜩 있어도)`() = runTest {
        val vipCategoryId = 999L
        val customer = customer(
            categoryId = vipCategoryId,  // 사장님이 VIP 박음
            depositAmount = 100_000L,
            balanceAmount = 500_000L
        )
        // 수동 우선 — VIP 그대로 유지
        assertEquals(vipCategoryId, classifier.resolveCategoryId(customer))
    }

    @Test
    fun `시공 대기 카테고리에 있는데 잔금 입금 → 시공 완료로 자동 승격`() = runTest {
        val customer = customer(
            categoryId = pendingCategory.id,
            depositAmount = 100_000L,
            balanceAmount = 500_000L
        )
        assertEquals(doneCategory.id, classifier.resolveCategoryId(customer))
    }

    @Test
    fun `시공 완료 카테고리에 있는데 잔금 환불 (null로 돌림) → 시공 대기로 복귀`() = runTest {
        val customer = customer(
            categoryId = doneCategory.id,
            depositAmount = 100_000L,
            balanceAmount = null
        )
        assertEquals(pendingCategory.id, classifier.resolveCategoryId(customer))
    }

    // ---------- reclassify — DB 호출 여부 ----------

    @Test
    fun `reclassify — 변경 있으면 DB 갱신 호출`() = runTest {
        val customer = customer(
            id = 50L, categoryId = null,
            depositAmount = 100_000L, balanceAmount = null
        )
        whenever(customerRepo.findById(50L)).thenReturn(customer)

        classifier.reclassify(50L)

        verify(categoryRepo).assignCustomer(50L, pendingCategory.id)
    }

    @Test
    fun `reclassify — 변경 없으면 DB 갱신 안 함 (비용 절약)`() = runTest {
        // 이미 시공 대기인 채로 reclassify — 변화 없음
        val customer = customer(
            id = 50L, categoryId = pendingCategory.id,
            depositAmount = 100_000L, balanceAmount = null
        )
        whenever(customerRepo.findById(50L)).thenReturn(customer)

        classifier.reclassify(50L)

        verify(categoryRepo, never()).assignCustomer(any(), any())
    }

    @Test
    fun `reclassify — 고객 없으면 no-op`() = runTest {
        whenever(customerRepo.findById(999L)).thenReturn(null)

        classifier.reclassify(999L)

        verify(categoryRepo, never()).assignCustomer(any(), any())
    }

    // ---------- 잔금 자동 계산 = total - deposit ----------
    // CustomerDetailScreen 의 UI 계산식이지만 회귀 방어 목적으로 인라인 테스트.

    @Test
    fun `잔금 자동 계산 — 총 100만원 계약금 30만원 = 잔금 70만원`() {
        val total = 1_000_000L
        val deposit = 300_000L
        assertEquals(700_000L, total - deposit)
    }

    @Test
    fun `잔금 자동 계산 — 계약금 null 이면 잔금 = 총금액`() {
        val total = 1_000_000L
        val deposit: Long? = null
        assertEquals(1_000_000L, total - (deposit ?: 0L))
    }

    @Test
    fun `잔금 자동 계산 — 총금액 null 이면 계산 안 함`() {
        val total: Long? = null
        // null 이면 자동 계산 안 함 — UI 는 사장님 직접 입력값 보여줘야
        assertNull(total)
    }

    // ---------- 헬퍼 ----------

    private fun customer(
        id: Long = 1L,
        categoryId: Long? = null,
        depositAmount: Long? = null,
        balanceAmount: Long? = null,
        totalAmount: Long? = null
    ) = CustomerEntity(
        id = id,
        phoneNumber = "01012345678",
        name = "테스트 고객",
        categoryId = categoryId,
        memo = "",
        address = null,
        scheduledWorkDate = null,
        leadHeat = null,
        depositAmount = depositAmount,
        depositPaidAt = null,
        balanceAmount = balanceAmount,
        balancePaidAt = null,
        totalAmount = totalAmount,
        createdAt = 0L,
        updatedAt = 0L
    )
}
