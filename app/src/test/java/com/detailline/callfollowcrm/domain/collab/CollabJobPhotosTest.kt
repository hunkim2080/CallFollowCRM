package com.detailline.callfollowcrm.domain.collab

import org.junit.Assert.assertEquals
import org.junit.Test

class CollabJobPhotosTest {

    @Test fun `그 건 shareId 순서대로 업체별 묶음 — 사진 없는 share 는 뺀다`() {
        val photos = mapOf(
            "s1" to listOf("a", "b"),
            "s2" to emptyList(),          // 사진 없는 업체 → 제외
            "s3" to listOf("c"),
        )
        val names = mapOf("s1" to "탄성 파인", "s3" to "하우스픽")
        val g = CollabJobPhotos.group(listOf("s1", "s2", "s3"), photos, names)
        assertEquals(listOf("s1", "s3"), g.map { it.shareId })
        assertEquals(listOf("탄성 파인", "하우스픽"), g.map { it.partnerName })
        assertEquals(3, CollabJobPhotos.total(g))
    }

    @Test fun `이름이 비면 「협업」으로`() {
        val g = CollabJobPhotos.group(listOf("s1"), mapOf("s1" to listOf("x")), emptyMap())
        assertEquals("협업", g.single().partnerName)
    }

    @Test fun `이 건에 안 걸린 share 는 아예 안 들어온다`() {
        // jobShareIds 에 s9 가 없으면 사진이 있어도 제외 — 1차 사진이 2차 탭에 안 섞인다.
        val g = CollabJobPhotos.group(listOf("s1"), mapOf("s1" to listOf("x"), "s9" to listOf("y", "z")), emptyMap())
        assertEquals(listOf("s1"), g.map { it.shareId })
        assertEquals(1, CollabJobPhotos.total(g))
    }

    @Test fun `shareId 중복은 한 번만`() {
        val g = CollabJobPhotos.group(listOf("s1", "s1"), mapOf("s1" to listOf("x")), emptyMap())
        assertEquals(1, g.size)
    }

    @Test fun `협업 없으면 빈 목록 · 총수 0`() {
        val g = CollabJobPhotos.group<String>(emptyList(), emptyMap(), emptyMap())
        assertEquals(0, g.size)
        assertEquals(0, CollabJobPhotos.total(g))
    }
}
