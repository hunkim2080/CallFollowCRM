package com.detailline.callfollowcrm.presentation.screen.web

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback

/**
 * 앱 내 문서 웹뷰 (동의문·처리방침 등) — 외부 브라우저 없이 항상 열림. (2026-07-11 사장님 지적)
 *
 *   문제: 동의/처리방침을 Intent.ACTION_VIEW 로 외부 브라우저에 던지면, 크롬 등 브라우저가 없는
 *         기기(플레이 심사 기기 포함)에선 ActivityNotFoundException → runCatching 이 삼켜 "아무 일도 안 남".
 *   해결: 안드로이드 시스템 WebView(크롬 없는 폰에도 거의 항상 존재)로 앱 안에서 직접 렌더.
 *
 *   URL 은 공개 도메인(api.si0in.kr, Cloudflare 터널)이라 외부망에서 닿음. 로드 실패 시 안내 문구 표시.
 *   화면: 상단 "‹ 닫기 + 제목" 바 + WebView. 뒤로가기 = 웹 히스토리 우선 → 없으면 종료.
 */
class DocWebViewActivity : ComponentActivity() {

    private var web: WebView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = intent.getStringExtra(EXTRA_URL).orEmpty()
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { "약관" }
        val d = resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            fitsSystemWindows = true
        }

        // ── 상단 바 (‹ 닫기 + 제목) ──
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54))
            setPadding(dp(14), 0, dp(16), 0)
        }
        val back = TextView(this).apply {
            text = "‹ 닫기"
            textSize = 15f
            setTextColor(Color.parseColor("#3182F6"))
            setPadding(dp(4), dp(6), dp(10), dp(6))
            setOnClickListener { finish() }
        }
        val titleView = TextView(this).apply {
            text = title
            textSize = 16f
            setTextColor(Color.parseColor("#0B0F19"))
            maxLines = 1
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                .apply { leftMargin = dp(8) }
        }
        bar.addView(back)
        bar.addView(titleView)

        val divider = android.view.View(this).apply {
            setBackgroundColor(Color.parseColor("#EEF1F5"))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1))
        }

        val webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            // 보안(2026-07-30): 우리 도메인(si0in.kr) https 로만 이동 허용 + 로컬 파일 접근 차단.
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView?, request: WebResourceRequest?
                ): Boolean {
                    val u = request?.url ?: return true
                    val host = u.host.orEmpty()
                    val allowed = u.scheme == "https" &&
                        (host == "si0in.kr" || host.endsWith(".si0in.kr"))
                    return !allowed   // 허용 도메인만 웹뷰가 로드, 그 외 네비게이션은 차단
                }
                override fun onReceivedError(
                    view: WebView?, request: WebResourceRequest?, error: WebResourceError?
                ) {
                    // 메인 프레임 로드 실패(서버 다운/오프라인)만 안내로 대체. 하위 리소스 실패는 무시.
                    if (request?.isForMainFrame == true) {
                        view?.loadData(ERROR_HTML, "text/html; charset=utf-8", "utf-8")
                    }
                }
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            )
        }
        web = webView
        if (url.isBlank()) webView.loadData(ERROR_HTML, "text/html; charset=utf-8", "utf-8")
        else webView.loadUrl(url)

        root.addView(bar)
        root.addView(divider)
        root.addView(webView)
        setContentView(root)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val w = web
                if (w != null && w.canGoBack()) w.goBack() else finish()
            }
        })
    }

    override fun onDestroy() {
        web?.destroy()
        web = null
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_URL = "url"
        private const val EXTRA_TITLE = "title"
        private const val ERROR_HTML =
            "<html><body style='font-family:sans-serif;padding:40px 24px;color:#3A4250;line-height:1.6'>" +
                "<h3 style='color:#0B0F19'>내용을 불러오지 못했어요</h3>" +
                "<p>인터넷 연결을 확인한 뒤 다시 시도해 주세요.</p></body></html>"

        /** 문서 웹뷰 열기 — 어디서든 context 로 호출. 브라우저 유무와 무관하게 앱 안에서 표시. */
        fun open(ctx: Context, url: String, title: String) {
            runCatching {
                ctx.startActivity(
                    Intent(ctx, DocWebViewActivity::class.java).apply {
                        putExtra(EXTRA_URL, url)
                        putExtra(EXTRA_TITLE, title)
                        if (ctx !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                )
            }
        }

        /** 우리 문서 URL → 화면 제목 매핑. */
        fun titleFor(url: String): String = when {
            url.contains("/consent/required") -> "개인정보 수집·이용 동의"
            url.contains("/consent/optional") -> "서비스 품질 향상 동의"
            url.contains("/privacy") -> "개인정보처리방침"
            else -> "약관"
        }
    }
}
