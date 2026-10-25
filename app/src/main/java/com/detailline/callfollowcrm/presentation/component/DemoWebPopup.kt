package com.detailline.callfollowcrm.presentation.component

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
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
        // Dialog 창을 **전체화면**으로 — 안 그러면 창이 내용 크기로 잡혀(웹뷰가 짧아져) 카드 위가 잘린다. (2026-10-24)
        val view = androidx.compose.ui.platform.LocalView.current
        androidx.compose.runtime.SideEffect {
            (view.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window?.setLayout(
                android.view.WindowManager.LayoutParams.MATCH_PARENT,
                android.view.WindowManager.LayoutParams.MATCH_PARENT
            )
        }
        // 창 크기에 안 기대고 **화면 높이를 직접** 준다 — Dialog 가 내용 크기로 잡혀 짧아지는 것 방지.
        val cfg = androidx.compose.ui.platform.LocalConfiguration.current
        Box(Modifier.fillMaxWidth().height(cfg.screenHeightDp.dp)) {
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
                },
                // 🧹 닫을 때 WebView 를 **반드시 정리**한다. 안 그러면 WebView 가 입력 채널을 쥔 채 남아
                //   팝업이 사라진 뒤에도 **터치를 다 먹는다**(기기에 따라 매번 — S23U "들어갔다 나오면 먹통").
                //   stopLoading → 부모에서 떼기 → destroy 순서가 WebView 정리의 정석이다. (2026-10-25 사장님 재신고)
                onRelease = { wv ->
                    wv.stopLoading()
                    wv.loadUrl("about:blank")
                    (wv.parent as? android.view.ViewGroup)?.removeView(wv)
                    wv.removeJavascriptInterface("AppBridge")
                    wv.destroy()
                }
            )
            // 네이티브 닫기 — 오른쪽 위(상태바 아래).
            Box(
                Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(8.dp).size(36.dp)
                    .clip(CircleShape).background(Color.Black.copy(alpha = 0.5f))
                    .clickable { onDismiss() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Close, "닫기", tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
    }
}

/**
 * 원본 프로토에 주입: 바깥 소개(.lede)·꼬리(.foot) 숨김·배경 투명, **카드를 화면에 맞게 자동 축소(scale-to-fit)**,
 *   .cta → 앱 동작. 프로토는 고정 크기라 폰에서 넘치면 잘린다 → 통째로 보이게 줄인다.
 */
private const val POPUP_INJECT =
    "(function(){try{var s=document.createElement('style');" +
    "s.innerHTML='html,body{background:transparent!important;margin:0!important;padding:0!important;height:100%!important;overflow:hidden!important}" +
    " .lede,.foot{display:none!important}';" +
    "document.head.appendChild(s);" +
    "var card=document.querySelector('.card');" +
    "function fit(){if(!card)return;" +
    "card.style.position='fixed';card.style.left='50%';card.style.top='50%';card.style.margin='0';" +
    "card.style.transform='translate(-50%,-50%)';" +                // 먼저 scale 없이 자연 크기 측정
    "var r=card.getBoundingClientRect();var vw=window.innerWidth,vh=window.innerHeight;" +
    "if(r.width<50||r.height<50||vw<50||vh<50)return;" +
    "var sc=Math.min((vw-14)/r.width,(vh-14)/r.height,1);" +
    "if(!isFinite(sc)||sc<=0)sc=1;" +                               // 투명 방지(음수/0만 막음)
    "card.style.transform='translate(-50%,-50%) scale('+sc+')';}" + // 화면 정중앙 + 맞게 축소
    "fit();setTimeout(fit,250);setTimeout(fit,700);window.addEventListener('resize',fit);" +
    "var c=document.querySelector('.cta');" +
    "if(c&&!c.dataset.b){c.dataset.b=1;c.style.cursor='pointer';" +
    "c.addEventListener('click',function(){try{AppBridge.cta()}catch(e){}});}" +
    "}catch(e){}})();"
