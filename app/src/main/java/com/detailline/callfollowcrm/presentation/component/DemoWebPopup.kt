package com.detailline.callfollowcrm.presentation.component

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * 🎬 **데모를 어두운 팝업(스크림)에 — 프로토 그대로(웹뷰).** (2026-10-24 사장님 "팝업이면 배경 어두워져야 · 프로토랑 1:1")
 *
 *   손으로 Compose 로 다시 그리면 프로토와 100% 같긴 어렵다. 그래서 **프로토 HTML 자체**([assetPath],
 *   assets 의 demos 폴더 html)를 WebView 로 띄운다 — 움직임까지 1:1. Dialog 가 **뒷배경을 어둡게**(스크림) 한다.
 *   HTML 안 [접수서 보내기] → `AppBridge.cta()` → [onCta], ✕/바깥 탭 → [onDismiss]. 서버·인터넷 안 씀(로컬 asset).
 */
@SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
@Composable
fun DemoWebPopup(
    assetPath: String,
    onCta: () -> Unit,
    onDismiss: () -> Unit
) {
    val onCtaCb by rememberUpdatedState(onCta)
    val onDismissCb by rememberUpdatedState(onDismiss)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxWidth().fillMaxHeight(0.92f).padding(horizontal = 12.dp)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    val wv = WebView(ctx)
                    wv.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    wv.settings.javaScriptEnabled = true
                    wv.addJavascriptInterface(object {
                        @JavascriptInterface fun cta() { wv.post { onCtaCb() } }
                        @JavascriptInterface fun close() { wv.post { onDismissCb() } }
                    }, "AppBridge")
                    wv.loadUrl("file:///android_asset/$assetPath")
                    wv
                }
            )
        }
    }
}
