package com.detailline.callfollowcrm.service

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 두 번 다 깨졌을 때 뜨는 알림을 **눈으로 보려고** 한 번 쏜다. (2026-09-20)
 * 확인용이라 단정(assert) 이 없다 — 화면을 보는 게 목적.
 */
@RunWith(AndroidJUnit4::class)
class NotifyBrokenPreviewTest {
    @Test
    fun 깨짐_알림_한_번_띄워보기() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        NotificationHelper.showMmsBroken(ctx)
        Thread.sleep(1500)
    }
}
