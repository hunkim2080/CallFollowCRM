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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.detailline.callfollowcrm.presentation.component.RegionDot
import com.detailline.callfollowcrm.util.RecordReel
import com.detailline.callfollowcrm.util.RecordShot
import com.detailline.callfollowcrm.util.WebReelRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/**
 * 🎬 만화 영상 **움직이는 미리보기** — 저장되는 그 영상(프로토 HTML)의 실제 장면을 **플립북**으로 돌린다.
 *
 * 왜 플립북인가: 라이브 재생(WebView rAF)을 AndroidView 로 띄우면 이 기기(갤S9)에서 2D 캔버스가
 *   **합성이 안 돼 빈 화면**이 된다(하드웨어=안 뜸·소프트웨어=안 갱신). 그래서 **저장과 똑같은 렌더러**
 *   ([WebReelRenderer], 검증된 toDataURL 경로)로 여러 컷을 미리 뽑아 **빠르게 넘겨** 움직이게 보여준다.
 *   그림 코드는 한 소스(프로토 HTML)·저장 영상과 같은 규칙([RecordReel.cartoonSites])(§12). 소리는 저장하면 남.
 *
 * 저장·미리보기 **같은 입력**을 받으려고 `dots`·`month` 만 받아 안에서 사진까지 준비한다.
 */
@Composable
fun CartoonReelPreview(
    dots: List<RegionDot>,
    month: Int,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    var frames by remember { mutableStateOf<List<Bitmap>?>(null) }
    var idx by remember { mutableIntStateOf(0) }

    DisposableEffect(Unit) { onDispose { frames?.forEach { runCatching { it.recycle() } } } }

    // 데이터가 바뀌면 다시 뽑는다. 사진 로딩·렌더는 전부 메인 밖에서.
    LaunchedEffect(dots, month) {
        frames?.forEach { runCatching { it.recycle() } }
        frames = null; idx = 0
        val out = withContext(Dispatchers.Default) {
            // 저장과 **같은 규칙**으로 현장·사진(base64) 만든다.
            val photos = LinkedHashMap<String, Bitmap>()
            for (dot in dots.sortedBy { it.order }.take(12)) {
                val p = dot.photoPath ?: continue
                if (photos.containsKey(dot.name)) continue
                RecordShot.loadPhoto(p, 320, 320)?.let { photos[dot.name] = it }
            }
            val sites = RecordReel.cartoonSites(dots, photos)
            photos.values.forEach { runCatching { it.recycle() } }
            if (sites.isEmpty()) return@withContext null
            val web = runCatching { WebReelRenderer.open(ctx, sites, month, withAudio = false) }.getOrNull()
                ?: return@withContext null
            val list = ArrayList<Bitmap>(FRAMES)
            try {
                for (i in 0 until FRAMES) {
                    val t = if (FRAMES == 1) 0f else i / (FRAMES - 1f)
                    val big = web.renderStill(t) ?: continue
                    // 미리보기는 작아도 되니 줄여서 메모리 아낌(12컷×360×640 ≈ 10MB).
                    val small = runCatching { Bitmap.createScaledBitmap(big, 360, 640, true) }.getOrNull()
                    runCatching { big.recycle() }
                    if (small != null) list.add(small)
                }
            } finally { web.close() }
            list.ifEmpty { null }
        }
        frames = out
    }

    // 뽑은 컷을 빠르게 넘긴다(≈9컷/초, 루프). 한 달 전체가 짧게 쓱 돈다.
    LaunchedEffect(frames) {
        val f = frames ?: return@LaunchedEffect
        if (f.size <= 1) return@LaunchedEffect
        while (isActive) {
            delay(110)
            idx = (idx + 1) % f.size
        }
    }

    Box(modifier, contentAlignment = Alignment.Center) {
        val f = frames
        if (f != null && f.isNotEmpty()) {
            Image(
                bitmap = f[idx.coerceIn(0, f.size - 1)].asImageBitmap(),
                contentDescription = "만화 영상 미리보기",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            CircularProgressIndicator()
        }
    }
}

private const val FRAMES = 12
