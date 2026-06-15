package com.detailline.callfollowcrm.presentation.screen.chat

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.detailline.callfollowcrm.CallFollowCrmApplication
import com.detailline.callfollowcrm.presentation.theme.TossBlue
import com.detailline.callfollowcrm.presentation.theme.TossGrayBg
import com.detailline.callfollowcrm.presentation.theme.TossTextPrimary
import com.detailline.callfollowcrm.presentation.theme.TossTextSecondary
import com.detailline.callfollowcrm.presentation.theme.TossTextTertiary
import java.io.File
import java.io.FileOutputStream
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale

/** 견적서 한 줄 — 품목/규격/금액(원). */
data class QuoteLine(val name: String, val spec: String, val amountWon: Long)

/** 견적서(직인) 미리보기에 필요한 데이터. depMode = ratio|fixed|none, depVal = %(ratio) 또는 만원(fixed). */
data class QuoteDocData(
    val lines: List<QuoteLine>,
    val totalWon: Long,
    val depMode: String,
    val depVal: Int
)

private val SealRed = Color(0xFFD6342C)
private val DocBorder = Color(0xFFCFD6DF)
private val DocHeaderBg = Color(0xFFF4F5F7)

private fun won(n: Long): String = NumberFormat.getNumberInstance(Locale.KOREA).format(n)

private fun findActivity(ctx: Context): Activity? {
    var c = ctx
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

/**
 * 프로토 openQuoteDoc 1:1 — 직인 찍힌 정식 견적서.
 *   화면에 그린 .qdoc 영역을 PixelCopy 로 캡처 → PNG → 문자/공유(이미지).
 *   2026-06-03 견적 2단계.
 */
@Composable
fun QuoteDocScreen(
    data: QuoteDocData,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val prefs = remember {
        (context.applicationContext as CallFollowCrmApplication).container.preferences
    }
    val bizName = prefs.bizName.ifBlank { "상호 미설정" }
    val bizOwner = prefs.bizOwner
    val bizNo = prefs.bizNo
    val bizAddr = prefs.bizAddr
    val bizPhone = prefs.bizPhone
    val seal = prefs.bizSeal.ifBlank { bizName }
    val validDays = prefs.bizQuoteValidDays.takeIf { it > 0 } ?: 14

    val today = remember { SimpleDateFormat("yyyy. M. d.", Locale.KOREA).format(java.util.Date()) }
    val depWon = when (data.depMode) {
        "ratio" -> data.totalWon * data.depVal / 100
        "fixed" -> data.depVal * 10_000L
        else -> 0L
    }

    // 캡처할 .qdoc 영역(윈도우 좌표).
    var docRect by remember { mutableStateOf<Rect?>(null) }

    fun captureAndShare() {
        val rect = docRect ?: return
        if (rect.width() <= 0 || rect.height() <= 0) return
        val window = findActivity(context)?.window ?: return
        val bmp = Bitmap.createBitmap(rect.width(), rect.height(), Bitmap.Config.ARGB_8888)
        try {
            PixelCopy.request(window, rect, bmp, { result ->
                if (result == PixelCopy.SUCCESS) {
                    runCatching {
                        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
                        val f = File(dir, "견적서.png")
                        FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                        val uri = androidx.core.content.FileProvider.getUriForFile(
                            context, context.packageName + ".fileprovider", f
                        )
                        val send = Intent(Intent.ACTION_SEND)
                            .setType("image/png")
                            .putExtra(Intent.EXTRA_STREAM, uri)
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        context.startActivity(Intent.createChooser(send, "견적서 보내기"))
                    }.onFailure {
                        android.widget.Toast.makeText(context, "이미지 공유에 실패했어요", android.widget.Toast.LENGTH_SHORT).show()
                    }
                } else {
                    android.widget.Toast.makeText(context, "이미지를 만들지 못했어요", android.widget.Toast.LENGTH_SHORT).show()
                }
            }, Handler(Looper.getMainLooper()))
        } catch (e: Exception) {
            android.widget.Toast.makeText(context, "이미지 캡처를 지원하지 않는 기기예요", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    Column(Modifier.fillMaxSize().background(TossGrayBg)) {
        // 앱바 — 프로토: "견적서" + "미리보기 닫기"
        Row(
            Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("견적서", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary)
            Spacer(Modifier.weight(1f))
            Text("미리보기 닫기", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TossTextTertiary,
                modifier = Modifier.clickable { onClose() })
        }
        // 안내
        Text(
            "📩 고객에게 이 이미지(직인 포함)가 문자로 가요",
            fontSize = 12.sp, color = TossTextSecondary, fontWeight = FontWeight.Medium,
            modifier = Modifier.fillMaxWidth().background(Color(0xFFEEF4FF)).padding(horizontal = 18.dp, vertical = 11.dp)
        )

        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp)
        ) {
            // .qdoc — 캡처 대상
            Column(
                Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { c ->
                        val r = c.boundsInWindow()
                        docRect = Rect(r.left.toInt(), r.top.toInt(), r.right.toInt(), r.bottom.toInt())
                    }
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White)
                    .border(1.dp, Color(0xFFD8DEE8), RoundedCornerShape(8.dp))
                    .padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 26.dp)
            ) {
                // 제목
                Text(
                    "견 적 서",
                    fontSize = 23.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF111111),
                    letterSpacing = 9.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                )
                // meta
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("견적일자 : $today", fontSize = 11.sp, color = TossTextTertiary)
                    Text("아래와 같이 견적서를 제출합니다.", fontSize = 11.sp, color = TossTextTertiary)
                }
                Spacer(Modifier.height(14.dp))
                // 고객님 귀하
                Text(
                    "고객님 귀하", fontSize = 15.sp, color = Color(0xFF111111),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                )
                Box(Modifier.fillMaxWidth().height(2.dp).background(Color(0xFF111111)))
                Spacer(Modifier.height(2.dp))
                // 표
                QuoteTableHeader()
                data.lines.forEach { line ->
                    QuoteTableRow(line.name, line.spec, won(line.amountWon))
                }
                // 합계
                Row(Modifier.fillMaxWidth().background(Color(0xFFFAFBFC)).border(1.dp, DocBorder)) {
                    Text("합계 (부가세 별도)", fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF111111),
                        modifier = Modifier.weight(2f).padding(9.dp))
                    Text(won(data.totalWon), fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold, color = TossBlue,
                        textAlign = TextAlign.End, modifier = Modifier.weight(1f).padding(9.dp))
                }
                // notes
                Spacer(Modifier.height(14.dp))
                Text("· 시공 예정일 : 협의 후 확정", fontSize = 12.sp, color = TossTextSecondary, lineHeight = 22.sp)
                if (data.depMode != "none") {
                    val depText = "· 계약금 ${won(depWon)}원" +
                        (if (data.depMode == "ratio") " (총액의 ${data.depVal}%)" else "") + " 입금 시 시공일 확정"
                    Text(depText, fontSize = 12.sp, color = SealRed, fontWeight = FontWeight.Bold, lineHeight = 22.sp)
                }
                Text("· 유효기간 : 발행일로부터 ${validDays}일", fontSize = 12.sp, color = TossTextSecondary, lineHeight = 22.sp)
                // foot — 회사 + 직인
                Spacer(Modifier.height(24.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f)) {
                        Text(bizName, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = TossTextPrimary)
                        val line1 = buildList {
                            if (bizOwner.isNotBlank()) add("대표 $bizOwner")
                            if (bizNo.isNotBlank()) add("사업자 $bizNo")
                        }.joinToString(" · ")
                        if (line1.isNotBlank()) {
                            Spacer(Modifier.height(2.dp))
                            Text(line1, fontSize = 11.sp, color = TossTextTertiary)
                        }
                        val line2 = buildList {
                            if (bizAddr.isNotBlank()) add(bizAddr)
                            if (bizPhone.isNotBlank()) add(bizPhone)
                        }.joinToString(" · ")
                        if (line2.isNotBlank()) {
                            Spacer(Modifier.height(2.dp))
                            Text(line2, fontSize = 11.sp, color = TossTextTertiary)
                        }
                    }
                    // 직인
                    Box(
                        Modifier.size(62.dp).rotate(-12f).clip(CircleShape)
                            .border(2.5.dp, SealRed, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            seal.replace(" ", "\n"),
                            fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = SealRed,
                            textAlign = TextAlign.Center, lineHeight = 13.sp
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        // 발송 버튼
        Box(
            Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 18.dp, vertical = 12.dp)
        ) {
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(TossBlue)
                    .clickable { captureAndShare() }.padding(vertical = 15.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("📩 이미지로 문자 보내기", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun QuoteTableHeader() {
    Row(Modifier.fillMaxWidth().background(DocHeaderBg).border(1.dp, DocBorder)) {
        Text("품목", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = TossTextPrimary,
            textAlign = TextAlign.Start, modifier = Modifier.weight(2f).padding(horizontal = 6.dp, vertical = 8.dp))
        Text("규격", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = TossTextPrimary,
            textAlign = TextAlign.Center, modifier = Modifier.weight(1.4f).padding(horizontal = 6.dp, vertical = 8.dp))
        Text("금액(원)", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = TossTextPrimary,
            textAlign = TextAlign.End, modifier = Modifier.weight(1.3f).padding(horizontal = 6.dp, vertical = 8.dp))
    }
}

@Composable
private fun QuoteTableRow(name: String, spec: String, amount: String) {
    Row(Modifier.fillMaxWidth().border(1.dp, DocBorder)) {
        Text(name, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary,
            modifier = Modifier.weight(2f).padding(horizontal = 8.dp, vertical = 9.dp))
        Text(spec, fontSize = 11.5.sp, color = TossTextTertiary, textAlign = TextAlign.Center,
            modifier = Modifier.weight(1.4f).padding(horizontal = 8.dp, vertical = 9.dp))
        Text(amount, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary, textAlign = TextAlign.End,
            modifier = Modifier.weight(1.3f).padding(horizontal = 8.dp, vertical = 9.dp))
    }
}
