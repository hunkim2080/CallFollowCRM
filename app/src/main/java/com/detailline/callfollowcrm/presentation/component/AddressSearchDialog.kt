package com.detailline.callfollowcrm.presentation.component

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.detailline.callfollowcrm.presentation.theme.TossTextPrimary
import com.detailline.callfollowcrm.presentation.theme.TossTextSecondary

/**
 * Daum(카카오) 우편번호 주소 검색 다이얼로그.
 *   WebView 에 Daum postcode 임베드를 띄우고, 선택 시 도로명주소(+건물명)를 콜백.
 *   프로토 openAddrSearch 자리에 실제 한국 주소 검색을 붙임. INTERNET 권한 필요(있음).
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun AddressSearchDialog(
    onPicked: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.82f)
                .clip(RoundedCornerShape(18.dp))
                .background(Color.White)
        ) {
            Column(Modifier.fillMaxSize()) {
                // 헤더
                Row(
                    Modifier.fillMaxWidth().padding(start = 18.dp, end = 10.dp, top = 14.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("주소 검색", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary,
                        modifier = Modifier.weight(1f))
                    Box(
                        Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).clickable { onDismiss() },
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Default.Close, "닫기", tint = TossTextSecondary, modifier = Modifier.size(20.dp)) }
                }
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            webViewClient = WebViewClient()
                            addJavascriptInterface(
                                object {
                                    @JavascriptInterface
                                    fun onComplete(address: String) {
                                        post { onPicked(address) }
                                    }
                                },
                                "AndroidBridge"
                            )
                            loadDataWithBaseURL(
                                "https://postcode.map.daum.net",
                                DAUM_POSTCODE_HTML, "text/html", "UTF-8", null
                            )
                        }
                    }
                )
            }
        }
    }
}

private const val DAUM_POSTCODE_HTML = """
<!DOCTYPE html>
<html>
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no">
</head>
<body style="margin:0;padding:0">
  <div id="wrap" style="width:100%;height:100vh"></div>
  <script src="//t1.daumcdn.net/mapjsapi/bundle/postcode/prod/postcode.v2.js"></script>
  <script>
    new daum.Postcode({
      oncomplete: function(data){
        var addr = data.roadAddress || data.jibunAddress || '';
        if (data.buildingName) { addr += ' (' + data.buildingName + ')'; }
        if (window.AndroidBridge && AndroidBridge.onComplete) { AndroidBridge.onComplete(addr); }
      },
      width: '100%',
      height: '100%'
    }).embed(document.getElementById('wrap'));
  </script>
</body>
</html>
"""
