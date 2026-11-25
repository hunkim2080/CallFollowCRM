package com.detailline.callfollowcrm

import com.detailline.callfollowcrm.data.local.entity.SitePhotoEntity
import com.detailline.callfollowcrm.data.repository.SitePhotoRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 대표 사진 고르기 — **그 범위 안에서만** 본다.
 * 2026-11-26 버그: 전역 첫 대표를 집어 1·2·3번 현장이 전부 같은 사진이 됐다.
 */
class SitePhotoRepresentativeTest {
    private fun photo(
        id: Long, customerId: Long, jobId: Long?, rep: Boolean = false
    ) = SitePhotoEntity(
        id = id, customerId = customerId, jobId = jobId,
        filePath = "/p/$id.jpg", createdAt = id, isRepresentative = rep
    )

    /** 🔴 핵심 재현: A 현장 사진을 대표로 지정해도, B 현장은 **자기 사진**을 받아야 한다. */
    @Test fun globalRepresentativeDoesNotBleedToOtherSites() {
        val photos = listOf(
            photo(1, customerId = 10, jobId = 100, rep = true), // A 현장 대표
            photo(2, customerId = 20, jobId = 200),             // B 현장 자기 사진
            photo(3, customerId = 30, jobId = 300),             // C 현장 자기 사진
        )
        assertEquals(1L, SitePhotoRepository.representativeOf(photos, 10, 100)?.id)
        assertEquals(2L, SitePhotoRepository.representativeOf(photos, 20, 200)?.id) // 1 아님!
        assertEquals(3L, SitePhotoRepository.representativeOf(photos, 30, 300)?.id) // 1 아님!
    }

    /** 그 건 안에서는 대표가 아무 것보다 우선. */
    @Test fun jobRepresentativeWinsWithinJob() {
        val photos = listOf(
            photo(1, 10, 100),
            photo(2, 10, 100, rep = true),
        )
        assertEquals(2L, SitePhotoRepository.representativeOf(photos, 10, 100)?.id)
    }

    /** 대표가 없으면 그 건의 **맨 먼저 올린** 사진. */
    @Test fun firstOfJobWhenNoRepresentative() {
        val photos = listOf(photo(1, 10, 100), photo(2, 10, 100))
        assertEquals(1L, SitePhotoRepository.representativeOf(photos, 10, 100)?.id)
    }

    /** 그 건 사진이 없으면 건 없는 옛 사진(그 고객). */
    @Test fun fallsBackToOldCustomerPhotos() {
        val photos = listOf(photo(1, 10, jobId = null))
        assertEquals(1L, SitePhotoRepository.representativeOf(photos, 10, 999)?.id)
    }

    /** 마지막 수단 — 그 고객의 아무 사진(다른 건 것이라도). */
    @Test fun lastResortAnyCustomerPhoto() {
        val photos = listOf(photo(1, 10, jobId = 500))
        assertEquals(1L, SitePhotoRepository.representativeOf(photos, 10, 999)?.id)
    }

    /** 그 고객 사진이 하나도 없으면 null. (jobId 는 건마다 유일 — 남의 건 사진은 안 걸린다) */
    @Test fun nullWhenNoCustomerPhoto() {
        val photos = listOf(photo(1, customerId = 99, jobId = 999, rep = true))
        assertNull(SitePhotoRepository.representativeOf(photos, 10, 100))
    }
}
