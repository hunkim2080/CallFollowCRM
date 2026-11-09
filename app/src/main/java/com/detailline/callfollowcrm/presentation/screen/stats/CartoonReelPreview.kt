package com.detailline.callfollowcrm.presentation.screen.stats

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.detailline.callfollowcrm.util.WebReelRenderer

/**
 * 🎬 만화 영상 **미리보기(한 컷)** — 저장되는 그 영상의 **실제 한 장면**을 보여준다.
 *
 * 왜 정지 이미지인가: 라이브 재생(WebView rAF)을 AndroidView 로 띄우면 이 기기(갤S9)에서
 *   2D 캔버스가 **합성이 안 돼 빈 화면**이 된다(하드웨어=안 뜸·소프트웨어=안 갱신). 그래서
 *   **저장과 똑같은 렌더러**([WebReelRenderer])로 **한 컷만** 뽑아 정지로 보여준다
 *   — 그림 코드는 한 소스(프로토 HTML)·검증된 경로(§12). 움직임·소리는 저장하면 나온다(안내는 부르는 쪽).
 */
@Composable
fun CartoonReelPreview(
    sites: List<WebReelRenderer.Site>,
    month: Int,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    var bmp by remember { mutableStateOf<Bitmap?>(null) }
    DisposableEffect(Unit) { onDispose { bmp?.recycle() } }
    LaunchedEffect(sites, month) {
        bmp?.recycle(); bmp = null
        val web = runCatching { WebReelRenderer.open(ctx, sites, month, withAudio = false) }.getOrNull()
        if (web != null) {
            bmp = runCatching { web.renderStill(0.42f) }.getOrNull()   // 달리는 장면 즈음(현장·사진 보임)
            web.close()
        }
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        val b = bmp
        if (b != null) {
            Image(
                bitmap = b.asImageBitmap(),
                contentDescription = "만화 영상 미리보기",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            CircularProgressIndicator()
        }
    }
}
