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
import kotlinx.coroutines.withContext

/**
 * 🎬 만화 영상 **미리보기 = 정지 포스터 한 장**. (2026-11-09 사장님 "정지 포스터만, 타사앱처럼")
 *
 * 왜 정지인가: ① 자동으로 빨리 돌리면(플립북) 끊기고 거슬린다 ② **라이브 재생은 이 폰 WebView 가
 *   캔버스를 화면에 합성 못 해 까만 화면**(임베드·팝업 둘 다). 진짜 움직임·소리는 **저장 영상**으로 본다
 *   — 타사앱(정산일기)도 미리보기는 정지 카드다. 포스터는 저장과 같은 렌더러 한 컷(§12).
 */
@Composable
fun CartoonReelPreview(
    dots: List<RegionDot>,
    month: Int,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    var poster by remember { mutableStateOf<Bitmap?>(null) }
    var loading by remember { mutableStateOf(true) }

    DisposableEffect(Unit) { onDispose { poster?.let { runCatching { it.recycle() } } } }

    LaunchedEffect(dots, month) {
        val old = poster
        loading = true; poster = null
        val made = withContext(Dispatchers.Default) {
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
            val big = try { web.renderStill(0.33f) } finally { web.close() }   // 달리는 장면 한 컷(현장·사진 보임)
            big?.let {
                val small = runCatching { Bitmap.createScaledBitmap(it, 360, 640, true) }.getOrNull()
                runCatching { it.recycle() }
                small
            }
        }
        old?.let { runCatching { it.recycle() } }   // 새 포스터 준비 뒤 옛것 해제(그리던 걸 먼저 지우지 않음)
        poster = made
        loading = false
    }

    Box(modifier, contentAlignment = Alignment.Center) {
        val b = poster
        when {
            b != null -> Image(
                bitmap = b.asImageBitmap(),
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
