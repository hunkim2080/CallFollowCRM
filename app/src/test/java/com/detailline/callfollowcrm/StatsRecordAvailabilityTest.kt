package com.detailline.callfollowcrm

import com.detailline.callfollowcrm.presentation.screen.stats.MyRecordState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StatsRecordAvailabilityTest {
    @Test fun collabOnlyMonthCanMakeVideoAndShot() {
        assertTrue(MyRecordState(lastNo = 0, monthSites = 2).canMakeRecord)
    }
    @Test fun existingOwnRecordStaysAvailable() {
        assertTrue(MyRecordState(lastNo = 7, monthSites = 0).canMakeRecord)
    }
    @Test fun emptyRecordCannotExport() {
        assertFalse(MyRecordState().canMakeRecord)
    }
}
