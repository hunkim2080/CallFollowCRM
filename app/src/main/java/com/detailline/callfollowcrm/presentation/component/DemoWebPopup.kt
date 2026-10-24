package com.detailline.callfollowcrm.presentation.component

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * 🎬 **데모를 어두운 팝업(스크림)에 — 프로토 그대로(웹뷰).** (2026-10-24 사장님 "팝업이면 배경 어두워져야 · 프로토랑 1:1")
 *
 *   손으로 Compose 로 다시 그리면 프로토와 100% 같긴 어렵다. 그래서 **프로토 HTML 그대로**([assetPath],
 *   assets 의 demos 폴더 html)를 WebView 로 띄운다 — 움직임까지 1:1. Dialog 가 **뒷배경을 어둡게**(스크림) 한다.
 *   HTML 은 **원본 프로토 그대로** 두고, 로드되면 자잘한 것만 주입한다: 바깥 소개/꼬리 숨김·배경 투명·카드 가운데,
 *   그리고 [접수서 보내기] 같은 `.cta` 클릭 → `AppBridge.cta()` → [onCta]. 닫기는 **네이티브 ✕**([onDismiss]) + 바깥 탭/뒤로.
 *   서버·인터넷 안 씀(로컬 asset).
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
                    }, "AppBridge")
                    wv.webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            view?.evaluateJavascript(POPUP_INJECT, null)
                        }
                    }
                    wv.loadUrl("file:///android_asset/$assetPath")
                    wv
                }
            )
            // 네이티브 닫기 — 오른쪽 위.
            Box(
                Modifier.align(Alignment.TopEnd).padding(2.dp).size(34.dp)
                    .clip(CircleShape).background(Color.Black.copy(alpha = 0.45f))
                    .clickable { onDismiss() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Close, "닫기", tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
    }
}

/** 원본 프로토에 주입: 바깥 소개(.lede)·꼬리(.foot) 숨김, 배경 투명, 카드 가운데, .cta → 앱 동작. */
private const val POPUP_INJECT =
    "(function(){try{var s=document.createElement('style');" +
    "s.innerHTML='html,body{background:transparent!important;height:100%!important}" +
    " body{padding:6px!important;display:flex!important;align-items:center!important;justify-content:center!important}" +
    " .lede,.foot{display:none!important} .card{margin:0!important}';" +
    "document.head.appendChild(s);" +
    "var c=document.querySelector('.cta');" +
    "if(c&&!c.dataset.b){c.dataset.b=1;c.style.cursor='pointer';" +
    "c.addEventListener('click',function(){try{AppBridge.cta()}catch(e){}});}" +
    "}catch(e){}})();"
