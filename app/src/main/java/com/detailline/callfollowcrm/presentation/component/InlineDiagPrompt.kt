package com.detailline.callfollowcrm.presentation.component

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.detailline.callfollowcrm.data.preferences.AppPreferences
import com.detailline.callfollowcrm.presentation.theme.TossBlueDark
import com.detailline.callfollowcrm.presentation.theme.TossTextTertiary
import com.detailline.callfollowcrm.util.DiagnosticsReporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * "막힌 자리에서 진단 보내기" (2026-07-29 사장님) — 코딩한 흐름이 안 될 때 그 화면에서 바로
 *   원인을 사장님께 보내는 재사용 링크. 더보기 깊숙한 진단까지 못 가는(홍보 유입) 사용자 구제.
 *
 * 겁주지 않게 = 진짜 실패 지점에만 붙인다(첫 시도 실패 X, "해봐도 계속 안 됨"일 때).
 * 버튼 한 번 → [DiagnosticsReporter.sendAuto] 서버 직송, 실패 시 공유 시트 폴백. 개인정보 없음.
 *
 * @param tag 어디서 막혔나 (예: "온보딩-녹음연결").
 * @param buildExtra 상황별 진단 텍스트 생성(IO 에서 호출됨). 예: AdotFolderScanner.recordingDiag(ctx).
 */
@Composable
fun InlineDiagPrompt(
    prefs: AppPreferences,
    tag: String,
    modifier: Modifier = Modifier,
    prompt: String = "이렇게 해도 계속 안 되나요?",
    action: String = "진단 보내기",
    buildExtra: () -> String = { "" }
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var sending by remember { mutableStateOf(false) }
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = !sending) {
                sending = true
                Toast.makeText(context, "진단을 보내는 중…", Toast.LENGTH_SHORT).show()
                scope.launch {
                    val extra = withContext(Dispatchers.IO) {
                        runCatching { buildExtra() }.getOrDefault("")
                    }
                    val ok = DiagnosticsReporter.sendAuto(context, prefs, tag, extra)
                    sending = false
                    if (ok) {
                        Toast.makeText(context, "진단을 보냈어요. 원인을 확인할게요 🙏", Toast.LENGTH_LONG).show()
                    } else {
                        // 서버 직송 실패 → 공유로 폴백(리포트 유실 방지).
                        Toast.makeText(context, "바로 전송이 안 돼 공유로 열었어요", Toast.LENGTH_LONG).show()
                        DiagnosticsReporter.share(context, DiagnosticsReporter.autoReport(prefs, tag, extra))
                    }
                }
            }
            .padding(vertical = 11.dp, horizontal = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(prompt, fontSize = 13.sp, color = TossTextTertiary, fontWeight = FontWeight.Medium)
        Spacer(Modifier.width(7.dp))
        Text(
            if (sending) "보내는 중…" else "🩺 $action",
            fontSize = 13.sp, color = TossBlueDark, fontWeight = FontWeight.Bold
        )
    }
}
