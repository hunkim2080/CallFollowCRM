package com.detailline.callfollowcrm.presentation.screen.stats

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.detailline.callfollowcrm.presentation.component.RegionDot
import com.detailline.callfollowcrm.presentation.theme.AppType
import com.detailline.callfollowcrm.presentation.theme.TossTextTertiary
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
 * ⚠️ 비트맵 수명(코덱스 P1): **지금 그려지는 프레임은 새것이 준비된 뒤에 해제**한다(교체-후-해제).
 *   만들다 취소되면 만들던 조각도 바로 해제. 빈/실패는 스피너 말고 글자로(무한 로딩 방지, 코덱스 P2).
 */
@Composable
fun CartoonReelPreview(
    dots: List<RegionDot>,
    month: Int,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    var frames by remember { mutableStateOf<List<Bitmap>?>(null) }
    var loading by remember { mutableStateOf(true) }
    var idx by remember { mutableIntStateOf(0) }

    DisposableEffect(Unit) { onDispose { frames?.forEach { runCatching { it.recycle() } } } }

    LaunchedEffect(dots, month) {
        val old = frames            // 지금 그려지는 프레임 — 아직 recycle 하지 않는다(Image 가 아직 그릴 수 있음)
        loading = true; frames = null; idx = 0
        val out: List<Bitmap>? = withContext(Dispatchers.Default) {
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
                    val small = runCatching { Bitmap.createScaledBitmap(big, 360, 640, true) }.getOrNull()
                    runCatching { big.recycle() }
                    if (small != null) list.add(small)
                }
            } catch (t: Throwable) {
                list.forEach { runCatching { it.recycle() } }   // 만들다 취소/실패 → 조각 바로 해제(누수 방지)
                throw t
            } finally {
                web.close()
            }
            list.ifEmpty { null }
        }
        // 새 프레임이 준비된 뒤에야 옛 프레임 해제 — 렌더에 몇 초 걸려 옛 Image 는 이미 교체됨(크래시 방지).
        old?.forEach { runCatching { it.recycle() } }
        frames = out
        loading = false
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
        when {
            f != null && f.isNotEmpty() -> Image(
                bitmap = f[idx.coerceIn(0, f.size - 1)].asImageBitmap(),
                contentDescription = "만화 영상 미리보기",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
            loading -> CircularProgressIndicator()
            else -> Text(
                "미리보기를 만들 수 없어요",
                style = AppType.caption, color = TossTextTertiary,
                textAlign = TextAlign.Center, modifier = Modifier.padding(12.dp),
            )
        }
    }
}

private const val FRAMES = 12
