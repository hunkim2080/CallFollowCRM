package com.detailline.callfollowcrm.util

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.view.View
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/**
 * **만화 '내 기록 영상'을 — 프로토 HTML 그대로 — 한 컷씩 비트맵으로 내준다.**
 *
 * 왜 이렇게 하나 (§12 두 벌 금지):
 *   만화(930줄짜리 Canvas 그림)를 Kotlin 으로 **다시 그리면** 똑같은 그림이 **두 벌**이 된다.
 *   사장님이 프로토(아티팩트)를 고칠 때마다 앱 쪽은 안 바뀌어 갈라진다 — 우리가 제일 많이 당한 사고.
 *   그래서 **그림 코드는 한 벌만** 둔다: 아티팩트 HTML 을 `assets/reel/record_cartoon.html` 로
 *   **글자 그대로 복사**해 품고, 여기(Kotlin)에서는 **그리기 코드를 하나도 안 건드리고**
 *   `evaluateJavascript` 로만 조종한다(크기 720×1280 고정 · 한 컷씩 `frame()` 호출 · 캔버스를 JPEG 로 뽑기).
 *   → 프로토가 바뀌면 그 파일만 다시 덮어쓰면 된다. (동기화 메모: docs/PROTOTYPES.md)
 *
 * 어떻게 — 오프스크린 WebView 에서 2D 캔버스를 `toDataURL('image/jpeg')` 로 읽는다.
 *   · `LAYER_TYPE_SOFTWARE` — 화면에 안 붙은 WebView 라도 2D 캔버스가 **빈 칸 없이** 뽑히게(가장 안전한 설정).
 *   · 프레임 생산은 **메인 스레드**(WebView 규칙)에서, [VideoMaker] 의 Default 스레드는 래치로 기다린다.
 *   · 그리기는 그대로 프로토 `frame(warpTime(elapsed))` — 소리·그림 같은 시간축(§0 1:1).
 */
class WebReelRenderer private constructor(
    private val webView: WebView,
    /** 이 영상의 총 길이(초) — 프로토 `TL.loop` 가 곳수(N)에 맞춰 정한다. */
    val durationSec: Float,
    /** 🔊 프로토의 **같은 소리**를 오프라인으로 구운 16비트 PCM(44.1k mono). 실패하면 null→무음. */
    val audioPcm: ShortArray?,
    private val main: Handler,
) {
    /** 만화에 넣을 한 현장 — 동네·단지(브랜드)·실제 사진(data URL, 없으면 null→추상 플레이스홀더). */
    data class Site(val region: String, val apt: String, val photoDataUrl: String?)

    private val src = Rect(0, 0, RENDER_W, RENDER_H)

    /**
     * [VideoMaker] 의 draw 람다에서 부른다(Default 스레드). `t` 는 0~1 로 흐르는 영상 전체 위치.
     *   WebView 가 그린 720×1280 한 컷을 받아 [canvas]([dstW]×[dstH]) 에 맞춰 그린다(작은 폴백은 줄여서).
     */
    /** 연속으로 한 컷도 못 받은 횟수 — 너무 쌓이면 "웹뷰가 죽은 것"으로 보고 던져서 지도 폴백으로 넘긴다. */
    private var consecFails = 0

    fun drawFrame(canvas: Canvas, t: Float, dstW: Int, dstH: Int) {
        val elapsed = t * durationSec
        val bmp = renderBlocking(elapsed)
        if (bmp != null) {
            canvas.drawBitmap(bmp, src, Rect(0, 0, dstW, dstH), null)
            bmp.recycle()
            consecFails = 0
        } else {
            // 한 컷 못 받으면 하늘색이라도 깔아 "검은 영상"을 피한다. 단 **연속으로 많이 실패하면**
            //   웹뷰가 죽은 것 → 던져서 VideoMaker 를 실패시키고 **지도 폴백**으로 넘긴다
            //   (전엔 전 컷 실패해도 단색 영상이 '성공'으로 저장됐다 — 코덱스 P2).
            consecFails++
            if (consecFails >= 8) throw IllegalStateException("만화 프레임 연속 $consecFails 회 실패 — 렌더 중단")
            canvas.drawColor(android.graphics.Color.rgb(0xbf, 0xe3, 0xff))
        }
    }

    /** 미리보기용 — **한 컷**(720×1280 비트맵)만 뽑는다. t:0..1. 어느 스레드에서 불러도 안전(메인 밖에서 돈다). */
    suspend fun renderStill(t: Float): Bitmap? =
        withContext(Dispatchers.Default) { renderBlocking(t * durationSec) }

    private fun renderBlocking(elapsed: Float): Bitmap? {
        val latch = CountDownLatch(1)
        // 래치의 countDown/await 가 메모리 가시성(happens-before)을 보장하니 평범한 holder 로 충분.
        val holder = arrayOfNulls<Bitmap>(1)
        main.post {
            webView.evaluateJavascript("__frame($elapsed)") { ret ->
                holder[0] = decode(ret)
                latch.countDown()
            }
        }
        if (!latch.await(10, TimeUnit.SECONDS)) return null
        return holder[0]
    }

    fun close() {
        main.post {
            runCatching { webView.loadUrl("about:blank") }
            runCatching { webView.destroy() }
        }
    }

    companion object {
        const val RENDER_W = 720
        const val RENDER_H = 1280
        private const val TAG = "WebReelRenderer"

        /** 비트맵 → data:image/jpeg;base64 (웹뷰 Image 로 넣으려고). 영상 안 칸이 작아 품질 80 이면 충분. */
        fun bitmapToJpegDataUrl(bmp: Bitmap, quality: Int = 80): String {
            val baos = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, quality, baos)
            return "data:image/jpeg;base64," + Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
        }

        /**
         * 렌더러를 연다 — 실패하면 null(그러면 부르는 쪽이 **지도 영상으로 폴백**).
         * @param sites 이번 달 현장 수(= 프로토의 N). 1~30 로 자른다.
         */
        @SuppressLint("SetJavaScriptEnabled")
        suspend fun open(ctx: Context, sites: List<Site>, month: Int, withAudio: Boolean = true): WebReelRenderer? = withContext(Dispatchers.Main) {
            if (sites.isEmpty()) return@withContext null
            val main = Handler(Looper.getMainLooper())
            val wv = runCatching { WebView(ctx) }.getOrNull() ?: return@withContext null
            // 소유권을 [WebReelRenderer] 로 넘기기 전에 **어느 경로로든**(타임아웃·셋업실패·취소·예외)
            //   끝나면 WebView 를 반드시 치운다 — 미리보기를 자주 열고 닫아 누수가 치명적(코덱스 P1).
            var success = false
            try {
            runCatching {
                wv.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
                wv.settings.javaScriptEnabled = true
                wv.settings.allowFileAccess = true
                wv.settings.loadsImagesAutomatically = true
                wv.layout(0, 0, 360, 640)
            }

            val loaded = CompletableDeferred<Boolean>()
            wv.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    if (!loaded.isCompleted) loaded.complete(true)
                }
                override fun onReceivedError(
                    view: WebView?, req: WebResourceRequest?, err: WebResourceError?,
                ) { /* 폰트 등 외부 리소스 실패는 치명 아님 — 시스템 한글폰트로 떨어져도 진행 */ }
            }
            wv.loadUrl("file:///android_asset/reel/record_cartoon.html")

            val ok = withTimeoutOrNull(8_000) { loaded.await() }
            if (ok == null) { android.util.Log.e(TAG, "페이지 로드 타임아웃"); return@withContext null }

            delay(600)   // 폰트 자리잡을 시간

            val n = sites.size.coerceIn(1, 30)
            val use = sites.take(n)
            // 실제 동네·단지·사진을 JSON 으로 — JSONArray 가 따옴표/역슬래시를 안전하게 처리한다.
            val regionsJson = JSONArray(use.map { it.region }).toString()
            val aptsJson = JSONArray(use.map { it.apt }).toString()
            val urlsJson = JSONArray().apply { use.forEach { put(it.photoDataUrl ?: JSONObject.NULL) } }.toString()
            // ⚙️ 조종만 한다 — 그리기 코드는 손대지 않는다.
            //   ① rAF 멈춤 ② 멈춤 ③ rebuild(N) ④ **실제 동네·단지·사진 주입** ⑤ __frame ⑥ 사진 준비도 __ready.
            val setup = """
                (function(){
                  try{
                    window.requestAnimationFrame=function(){return 0;};
                    paused=true; speed=0;
                    rebuild($n); if(typeof buildEvt==='function'){ buildEvt(); }
                    MONTH=$month;
                    var RG=$regionsJson, AP=$aptsJson, URLS=$urlsJson;
                    for(var i=0;i<RG.length;i++){ if(i<SITE_REGION.length){ SITE_REGION[i]=RG[i]; SITE_APT[i]=AP[i]||''; } }
                    window.__PHOTOS=[];
                    for(var j=0;j<URLS.length;j++){ (function(k){ if(!URLS[k]) return;
                      var im=new Image(); im.__ok=false;
                      im.onload=function(){ im.__ok=true; }; im.onerror=function(){ im.__ok=false; };
                      im.src=URLS[k]; __PHOTOS[k]=im; })(j); }
                    window.__frame=function(e){
                      if(cv.width!==$RENDER_W||cv.height!==$RENDER_H){ cv.width=$RENDER_W; cv.height=$RENDER_H; }
                      W=$RENDER_W; H=$RENDER_H; UL=2; DPR=1; ctx.setTransform(1,0,0,1,0,0);
                      frame(warpTime(e));
                      return cv.toDataURL('image/jpeg',0.92);
                    };
                    window.__ready=function(){ var n=0,t=0; if(window.__PHOTOS){ for(var i=0;i<__PHOTOS.length;i++){ if(__PHOTOS[i]){ t++; if(__PHOTOS[i].__ok) n++; } } } return n+'/'+t; };
                    return JSON.stringify({loop: TL.loop});
                  }catch(err){ return 'ERR:'+((err&&err.message)||err); }
                })();
            """.trimIndent()
            val res = evalOnMain(wv, setup)
            val durationSec = parseLoop(res)
            if (durationSec == null) {
                android.util.Log.e(TAG, "셋업 실패: $res")
                return@withContext null
            }
            // 실제 사진 decode 끝날 때까지 잠깐 기다린다(최대 ~2.4초). data: URL 이라 보통 즉시 끝난다.
            var tries = 0
            while (tries < 16) {
                val r = (evalOnMain(wv, "__ready()") ?: "\"0/0\"").trim('"')
                val p = r.split("/")
                if (p.size == 2 && p[0] == p[1]) break
                delay(150); tries++
            }
            // 🔊 소리도 **같은 HTML** 에서 오프라인으로 구워 온다(§12 — 소리·그림 한 소스). 실패하면 무음.
            //   미리보기(withAudio=false)는 소리가 필요 없어 건너뛴다(한 컷만 뽑으면 되니 빠르게).
            val audioPcm = if (!withAudio) null else runCatching { fetchAudioPcm(wv) }
                .onFailure { android.util.Log.e(TAG, "소리 가져오기 실패(무음): " + it.message) }
                .getOrNull()
            android.util.Log.i(
                TAG,
                "만화 렌더러 준비 — N=$n 길이=${durationSec}s 사진=${use.count { it.photoDataUrl != null }} 소리=${audioPcm?.size ?: 0}샘플"
            )
            val renderer = WebReelRenderer(wv, durationSec, audioPcm, main)
            success = true
            renderer
            } finally {
                // 성공(소유권 이전)이 아니면 — 실패·취소·예외 — WebView 를 여기서 반드시 해제.
                if (!success) { runCatching { wv.loadUrl("about:blank") }; runCatching { wv.destroy() } }
            }
        }

        /** open() 은 메인 스레드에서 돌므로 바로 호출하고 콜백만 기다린다. 응답이 영영 안 오면 8초에 포기(무한대기 방지, 코덱스 P2). */
        private suspend fun evalOnMain(wv: WebView, js: String): String? =
            withTimeoutOrNull(8_000) {
                suspendCancellableCoroutine { cont ->
                    wv.evaluateJavascript(js) { r -> if (cont.isActive) cont.resume(r) }
                }
            }

        /** 프로토의 오프라인 오디오 렌더를 돌려 16비트 PCM(mono 44.1k)으로 받아온다. 실패면 null→무음. */
        private suspend fun fetchAudioPcm(wv: WebView): ShortArray? {
            val start = evalOnMain(wv, "__renderAudio(${ReelMusic.SAMPLE_RATE})") ?: ""
            if (!start.contains("OK")) { android.util.Log.e(TAG, "소리 렌더 시작 실패: $start"); return null }
            var ready = ""
            var tries = 0
            while (tries < 50) {   // 오프라인 렌더는 보통 1~2초이면 끝난다
                ready = (evalOnMain(wv, "__audioReady()") ?: "\"\"").trim('"')
                if (ready.startsWith("OK:") || ready.startsWith("ERR:")) break
                delay(150); tries++
            }
            if (!ready.startsWith("OK:")) { android.util.Log.e(TAG, "소리 렌더 안 끝남: $ready"); return null }
            val total = ready.removePrefix("OK:").toIntOrNull() ?: return null
            // base64 를 조각내 가져온다 — 한 번에 너무 큰 문자열을 돌려받지 않으려고.
            val sb = StringBuilder(total)
            var off = 0; val chunk = 524288
            while (off < total) {
                val part = evalOnMain(wv, "__audioGet($off,$chunk)") ?: break
                val s = if (part.length >= 2 && part.first() == '"' && part.last() == '"') part.substring(1, part.length - 1) else part
                if (s.isEmpty()) break
                sb.append(s); off += chunk
            }
            // 조각을 다 못 받았으면 **반쪽 소리를 쓰지 않는다** — 뒷부분만 무음인 영상보다 통째로 무음이 안전(코덱스 P2).
            if (sb.length != total) { android.util.Log.e(TAG, "소리 조각 부족 ${sb.length}/$total — 무음으로"); return null }
            val bytes = runCatching { Base64.decode(sb.toString(), Base64.DEFAULT) }.getOrNull() ?: return null
            val shorts = ShortArray(bytes.size / 2)
            java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts)
            return shorts
        }

        private fun decode(ret: String?): Bitmap? {
            if (ret == null || ret == "null") return null
            var s = ret
            if (s.length >= 2 && s.first() == '"' && s.last() == '"') s = s.substring(1, s.length - 1)
            val comma = s.indexOf(',')
            if (comma < 0) return null
            val b64 = s.substring(comma + 1)
            val bytes = runCatching { Base64.decode(b64, Base64.DEFAULT) }.getOrNull() ?: return null
            return runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull()
        }

        private fun parseLoop(ret: String?): Float? {
            if (ret == null || ret == "null") return null
            var s = ret
            if (s.length >= 2 && s.first() == '"' && s.last() == '"') {
                s = s.substring(1, s.length - 1).replace("\\\"", "\"").replace("\\\\", "\\")
            }
            if (s.startsWith("ERR:")) return null
            val m = Regex("\"loop\"\\s*:\\s*([0-9.]+)").find(s) ?: return null
            return m.groupValues[1].toFloatOrNull()?.coerceIn(5f, 30f)
        }
    }
}
