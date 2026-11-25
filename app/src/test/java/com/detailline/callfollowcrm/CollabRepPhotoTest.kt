package com.detailline.callfollowcrm

import com.detailline.callfollowcrm.ai.SharedSiteRepository.SharedPhoto
import com.detailline.callfollowcrm.domain.collab.CollabRepPhoto
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class CollabRepPhotoTest {
    private fun photo(id: Long) = SharedPhoto(id, null, null, null, "partner", "업체", 0L)
    private val first = photo(20L)
    private val chosen = photo(10L)
    private val photos = listOf(first, chosen)

    @Test fun emptyWithoutChoice() { assertNull(CollabRepPhoto.pick(emptyList(), null)) }
    @Test fun emptyWithChoice() { assertNull(CollabRepPhoto.pick(emptyList(), 10L)) }
    @Test fun noChoiceUsesListOrder() { assertSame(first, CollabRepPhoto.pick(photos, null)) }
    @Test fun explicitChoiceWins() { assertSame(chosen, CollabRepPhoto.pick(photos, 10L)) }
    @Test fun deletedChoiceFallsBack() { assertSame(first, CollabRepPhoto.pick(photos, 99L)) }
    @Test fun repeatedChoiceIsStable() {
        repeat(2) { assertSame(chosen, CollabRepPhoto.pick(photos, 10L)) }
    }
}
