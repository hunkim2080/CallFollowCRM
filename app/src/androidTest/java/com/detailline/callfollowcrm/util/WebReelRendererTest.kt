package com.detailline.callfollowcrm.util

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * 🎬 만화 영상 파이프라인 **눈으로 검증** — 웹뷰가 프로토 `record_cartoon.html` 을
 *   오프스크린에서 **빈 칸 없이** 한 컷씩 뽑아주는지(가장 위험한 자리)를 폰에서 확인한다.
 *   네 장면(오프닝·달리기·피니쉬·사진벽)을 PNG 로 떨궈 `adb pull` 로 본다.
 *   확인용이라 단정은 "렌더러가 열렸나 + 비트맵이 왔나" 만.
 */
@RunWith(AndroidJUnit4::class)
class WebReelRendererTest {
    @Test
    fun 만화_네_장면_PNG_뽑기() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val sampleSites = (1..8).map { WebReelRenderer.Site("동네$it", "브랜드$it", null) }
        val web = runBlocking { WebReelRenderer.open(ctx, sampleSites, 10) }
        assertNotNull("만화 렌더러가 열려야 한다(웹뷰 준비 실패면 null)", web)
        web!!

        val dir = File(ctx.getExternalFilesDir(null), "reeltest").apply { mkdirs() }
        val shots = linkedMapOf(
            "01_opening" to 0.03f,
            "02_driving" to 0.35f,
            "03_finish" to 0.62f,
            "04_photowall" to 0.90f,
        )
        for ((name, t) in shots) {
            val bmp = Bitmap.createBitmap(
                WebReelRenderer.RENDER_W, WebReelRenderer.RENDER_H, Bitmap.Config.ARGB_8888
            )
            val c = Canvas(bmp)
            c.drawColor(android.graphics.Color.BLACK)
            web.drawFrame(c, t, WebReelRenderer.RENDER_W, WebReelRenderer.RENDER_H)
            File(dir, "cartoon_$name.png").outputStream().use {
                bmp.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            bmp.recycle()
        }
        android.util.Log.i(
            "WebReelRendererTest",
            "길이=${web.durationSec}s · PNG 네 장 저장: ${dir.absolutePath}"
        )
        web.close()
        Thread.sleep(300)
    }
}
