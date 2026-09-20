package com.detailline.callfollowcrm.service

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.detailline.callfollowcrm.util.ImageNoiseCheck
import com.google.android.mms.pdu_alt.PduBody
import com.google.android.mms.pdu_alt.PduPart
import com.google.android.mms.pdu_alt.RetrieveConf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 📷 **깨진 사진 알아보기를 진짜 폰 위에서 검산한다.** (2026-09-20 사장님 "해봐")
 *
 * 손님께 "사진 다시 보내주세요" 라고 할 수는 없다. 그래서 **2026-09-20 12:25 에 실제로
 * 깨져서 들어왔던 그 사진 바이트**를 폰에 올려놓고, **폰의 진짜 JPEG 해독기**로 돌린다.
 *
 * PC 에서 같은 계산을 해봤을 때는 이렇게 갈렸다 —
 *   깨진 것 **54.0 / 55.3 / 54.9** · 정상 **3.9 ~ 12.7** (기준 [ImageNoiseCheck.THRESHOLD] = 40)
 * 그 결과가 **폰에서도 같은지**가 여기서 볼 것이다. PC 의 해독기와 폰의 해독기는 다른 물건이라
 * "PC 에서 됐으니 폰에서도 되겠지" 는 확인이 아니다.
 *
 * ⚠️ 시험 사진은 **손님 자료라 repo 에 넣지 않는다**(app/src/androidTest/assets/ 는 .gitignore).
 *    자료가 없으면 이 시험은 **조용히 건너뛴다**(assumeTrue) — 없다고 실패로 뜨면 거짓말이 된다.
 */
@RunWith(AndroidJUnit4::class)
class MmsImageIntegrityTest {

    private fun asset(name: String): ByteArray? = runCatching {
        InstrumentationRegistry.getInstrumentation().context.assets.open(name).use { it.readBytes() }
    }.getOrNull()

    /** 실제로 깨져서 왔던 사진 3장 — 하나도 빠짐없이 걸러내야 한다. */
    @Test
    fun 실제로_깨져서_온_사진을_폰이_알아본다() {
        val names = listOf("broken1.jpg", "broken2.jpg", "broken3.jpg")
        val files = names.mapNotNull { n -> asset(n)?.let { n to it } }
        assumeTrue("시험 사진이 없어 건너뜀", files.isNotEmpty())

        for ((name, bytes) in files) {
            val score = ImageNoiseCheck.scoreOfJpeg(bytes)
            assertNotNull("$name 을 폰이 아예 못 읽었다", score)
            assertTrue(
                "$name 점수 $score — 기준 ${ImageNoiseCheck.THRESHOLD} 를 넘어야 '깨짐'으로 걸린다",
                ImageNoiseCheck.isNoisy(bytes)
            )
        }
    }

    /** 🔑 **멀쩡한 사진을 깨진 걸로 보면 안 된다.** 잘못 걸러 다시 받는 건 손님 사진을 잃을 위험. */
    @Test
    fun 멀쩡한_사진은_건드리지_않는다() {
        val files = listOf("good1.jpg", "good2.jpg").mapNotNull { n -> asset(n)?.let { n to it } }
        assumeTrue("시험 사진이 없어 건너뜀", files.isNotEmpty())

        for ((name, bytes) in files) {
            val score = ImageNoiseCheck.scoreOfJpeg(bytes)
            assertNotNull("$name 을 폰이 아예 못 읽었다", score)
            assertTrue(
                "$name 점수 $score — 기준 아래여야 멀쩡한 걸로 둔다",
                !ImageNoiseCheck.isNoisy(bytes)
            )
        }
    }

    /**
     * 사진 조각을 **PDU 에서 제대로 꺼내오는가.** 여기가 틀리면 검사 자체가 헛돈다 —
     * 엉뚱한 조각(글·smil)을 보거나 빈손으로 돌아와 **깨진 걸 못 잡는다.**
     */
    @Test
    fun PDU_에서_사진_조각만_원본_그대로_꺼낸다() {
        val broken = asset("broken1.jpg")
        assumeTrue("시험 사진이 없어 건너뜀", broken != null)

        val body = PduBody()
        body.addPart(PduPart().apply {
            contentType = "application/smil".toByteArray()
            data = "<smil></smil>".toByteArray()
        })
        body.addPart(PduPart().apply {
            contentType = "text/plain".toByteArray()
            data = "여기 사진 보냅니다".toByteArray()
        })
        body.addPart(PduPart().apply {
            contentType = "image/jpeg".toByteArray()
            data = broken
        })

        val retrieve = RetrieveConf()
        retrieve.body = body

        val imgs = MmsDownloadedReceiver().imageBytesOf(retrieve)

        assertEquals("사진 조각은 딱 1개여야 한다(글·smil 을 세면 안 된다)", 1, imgs.size)
        assertTrue("꺼내온 바이트가 원본과 달라졌다", imgs[0].contentEquals(broken!!))
        assertTrue("꺼내온 조각이 '깨짐'으로 걸리지 않았다", ImageNoiseCheck.isNoisy(imgs[0]))
    }
}
