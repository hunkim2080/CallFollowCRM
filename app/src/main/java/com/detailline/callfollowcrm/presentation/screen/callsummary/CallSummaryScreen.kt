package com.detailline.callfollowcrm.presentation.screen.callsummary

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.detailline.callfollowcrm.data.AppContainer
import com.detailline.callfollowcrm.presentation.theme.TossBlue
import com.detailline.callfollowcrm.presentation.theme.TossGrayBg
import com.detailline.callfollowcrm.presentation.theme.TossTextPrimary
import com.detailline.callfollowcrm.presentation.theme.TossTextSecondary
import com.detailline.callfollowcrm.presentation.theme.TossTextTertiary
import com.detailline.callfollowcrm.util.PhoneNumberFormatter
import com.detailline.callfollowcrm.util.SmsSender
import kotlinx.coroutines.launch

private val Purple = Color(0xFF7C5CFC)

/**
 * 통화 정리해서 보내기 (2026-06-03 사장님 요청, A안 = 정리 중심).
 *
 * 흐름: [🎤 음성 / 📋 붙여넣기 / ✍️ 직접] 으로 통화 내용 입력
 *   → 서버 §18(/api/call-summary, Haiku) 가 한 줄 + 불릿 + **고객용 후속 문자 초안** 생성
 *   → 사장님이 초안 확인·수정 → [고객에게 보내기].
 *
 * 에이닷 의존 X — 음성/직접 입력은 에이닷 없어도 됨. (에이닷 ↑공유는 별도 경로 AdotSummaryImporter)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallSummaryScreen(
    container: AppContainer,
    phone: String,
    customerName: String?,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    var raw by remember { mutableStateOf(TextFieldValue("")) }
    var loading by remember { mutableStateOf(false) }
    var oneLine by remember { mutableStateOf<String?>(null) }
    var bullets by remember { mutableStateOf<List<String>>(emptyList()) }
    var draft by remember { mutableStateOf(TextFieldValue("")) }
    var hasResult by remember { mutableStateOf(false) }

    val title = customerName?.takeIf { it.isNotBlank() } ?: PhoneNumberFormatter.format(phone)

    // 🎤 음성 입력 — 시스템 음성인식 UI (RECORD_AUDIO 권한 불필요, 에이닷 무관).
    val voiceLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                .orEmpty()
            if (spoken.isNotBlank()) {
                val cur = raw.text
                val merged = if (cur.isBlank()) spoken else "$cur $spoken"
                raw = TextFieldValue(merged, selection = androidx.compose.ui.text.TextRange(merged.length))
            }
        }
    }
    fun launchVoice() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "통화 내용을 말씀하세요")
        }
        runCatching { voiceLauncher.launch(intent) }
            .onFailure { Toast.makeText(context, "이 기기에서 음성 입력을 지원하지 않아요", Toast.LENGTH_SHORT).show() }
    }

    fun summarize() {
        val text = raw.text.trim()
        if (text.isBlank()) {
            Toast.makeText(context, "통화 내용을 먼저 입력해주세요", Toast.LENGTH_SHORT).show()
            return
        }
        loading = true
        scope.launch {
            val customer = runCatching { container.customerRepository.findByPhone(phone) }.getOrNull()
            val res = container.callSummaryServerRepository.summarize(
                phone = phone,
                rawText = text,
                startedAtMs = System.currentTimeMillis(),
                customerName = customerName ?: customer?.name,
                customerMemo = customer?.memo
            )
            loading = false
            res.onSuccess { s ->
                oneLine = s.oneLine
                bullets = s.bullets
                val d = s.followupSms?.takeIf { it.isNotBlank() }
                    ?: s.oneLine?.takeIf { it.isNotBlank() }
                    ?: text
                draft = TextFieldValue(d, selection = androidx.compose.ui.text.TextRange(d.length))
                hasResult = true
            }.onFailure {
                Toast.makeText(context, "정리에 실패했어요. 잠시 후 다시 시도해주세요", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun send() {
        val body = draft.text.trim()
        if (body.isBlank()) {
            Toast.makeText(context, "보낼 내용이 비어 있어요", Toast.LENGTH_SHORT).show()
            return
        }
        if (!SmsSender.hasPermission(context)) {
            Toast.makeText(context, "문자 권한이 필요해요", Toast.LENGTH_SHORT).show()
            return
        }
        scope.launch {
            val customer = runCatching { container.customerRepository.upsertByPhone(phoneNumber = phone) }.getOrNull()
            val ok = SmsSender.sendDirect(context, phone, body)
            container.messageHistoryRepository.recordEstimateSent(phone, customer?.id, body)
            Toast.makeText(
                context,
                if (ok) "통화 정리를 보냈어요 ✓" else "발송에 실패했어요",
                Toast.LENGTH_SHORT
            ).show()
            if (ok) onBack()
        }
    }

    Scaffold(
        containerColor = TossGrayBg,
        topBar = {
            TopAppBar(
                title = { Text("통화 정리해서 보내기", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }
    ) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 16.dp)
        ) {
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = TossTextPrimary)
            Spacer(Modifier.height(2.dp))
            Text(
                if (hasResult) "고객에게 보낼 정리 메시지예요. 확인하고 수정하세요."
                else "방금 통화 내용을 적거나 말하면, AI가 정리해서 고객에게 보낼 문자로 만들어드려요.",
                fontSize = 13.sp, color = TossTextTertiary, lineHeight = 18.sp
            )
            Spacer(Modifier.height(16.dp))

            if (!hasResult) {
                // ── 입력 단계 ──
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SourceButton("🎤 음성으로", Modifier.weight(1f)) { launchVoice() }
                    SourceButton("📋 붙여넣기", Modifier.weight(1f)) {
                        val t = clipboard.getText()?.text.orEmpty()
                        if (t.isBlank()) {
                            Toast.makeText(context, "복사된 내용이 없어요", Toast.LENGTH_SHORT).show()
                        } else {
                            val cur = raw.text
                            val merged = if (cur.isBlank()) t else "$cur\n$t"
                            raw = TextFieldValue(merged, selection = androidx.compose.ui.text.TextRange(merged.length))
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White)
                        .heightIn(min = 150.dp)
                        .padding(14.dp)
                ) {
                    if (raw.text.isEmpty()) {
                        Text(
                            "예) 욕실 줄눈 문의. 30평대, 안방·공용 2개. 다음 주 견적 보기로 함.",
                            color = TossTextTertiary, fontSize = 14.sp, lineHeight = 20.sp
                        )
                    }
                    BasicTextField(
                        value = raw,
                        onValueChange = { raw = it },
                        textStyle = TextStyle(fontSize = 14.sp, color = TossTextPrimary, lineHeight = 20.sp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(Modifier.height(16.dp))
                PrimaryButton(
                    text = if (loading) "AI가 정리하는 중…" else "✨ AI로 정리하기",
                    enabled = !loading && raw.text.isNotBlank(),
                    loading = loading,
                    color = Purple
                ) { summarize() }
            } else {
                // ── 결과 단계 ──
                if (!oneLine.isNullOrBlank() || bullets.isNotEmpty()) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White)
                            .padding(16.dp)
                    ) {
                        Text("📋 통화 요약", fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold, color = TossTextTertiary)
                        oneLine?.takeIf { it.isNotBlank() }?.let {
                            Spacer(Modifier.height(6.dp))
                            Text(it, fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary, lineHeight = 20.sp)
                        }
                        bullets.forEach { b ->
                            Spacer(Modifier.height(5.dp))
                            Text("· $b", fontSize = 13.sp, color = TossTextSecondary, lineHeight = 19.sp)
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                }
                Text("고객에게 보낼 문자", fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold, color = TossTextTertiary)
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White)
                        .heightIn(min = 130.dp)
                        .padding(14.dp)
                ) {
                    BasicTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        textStyle = TextStyle(fontSize = 14.sp, color = TossTextPrimary, lineHeight = 20.sp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(Modifier.height(16.dp))
                PrimaryButton(text = "고객에게 보내기", enabled = true, loading = false, color = TossBlue) { send() }
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(TossGrayBg)
                        .clickable { hasResult = false }
                        .padding(vertical = 13.dp),
                    contentAlignment = Alignment.Center
                ) { Text("다시 정리", color = TossTextSecondary, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
private fun SourceButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) { Text(text, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary) }
}

@Composable
private fun PrimaryButton(
    text: String,
    enabled: Boolean,
    loading: Boolean,
    color: Color,
    onClick: () -> Unit
) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (enabled) color else color.copy(alpha = 0.4f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 15.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (loading) {
                CircularProgressIndicator(Modifier.height(16.dp), color = Color.White, strokeWidth = 2.dp)
            }
            Text(text, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
    }
}
