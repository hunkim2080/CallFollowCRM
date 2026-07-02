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
    val depVal: Int,
    /** true = 항목가에 부가세 포함(합계=총액, 역산 표기) / false = 별도(합계=총액+10%). (2026-07-03 사장님) */
    val vatIncluded: Boolean = false,
    /** 선택한 시공 예정일(ms). null=협의 후 확정. */
    val workDateMs: Long? = null,
    /** 받는 분(이름/번호). null·빈값=고객님. */
    val recipient: String? = null
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
    // 뒤로가기 = 미리보기만 닫고 견적 편집기로 복귀. 없으면 back 이 ChatScreen 까지 pop 됨. (2026-06-23 사장님)
    androidx.activity.compose.BackHandler { onClose() }
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
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White)
                    .border(1.dp, Color(0xFFECEFF3), RoundedCornerShape(16.dp))
                    .padding(start = 22.dp, end = 22.dp, top = 24.dp, bottom = 26.dp)
            ) {
                // ── 헤더: 좌 타이틀 / 우 회사 (레터헤드) ──
                Row(Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text("견적서", fontSize = 25.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF111111))
                        Text("ESTIMATE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TossTextTertiary,
                            letterSpacing = 3.sp, modifier = Modifier.padding(top = 3.dp))
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(bizName, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary)
                        if (bizOwner.isNotBlank()) {
                            Spacer(Modifier.height(2.dp))
                            Text("대표 $bizOwner", fontSize = 11.sp, color = TossTextTertiary)
                        }
                        if (bizNo.isNotBlank()) {
                            Spacer(Modifier.height(1.dp))
                            Text("사업자 $bizNo", fontSize = 11.sp, color = TossTextTertiary)
                        }
                        if (bizPhone.isNotBlank()) {
                            Spacer(Modifier.height(1.dp))
                            Text(bizPhone, fontSize = 11.sp, color = TossTextTertiary)
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Box(Modifier.fillMaxWidth().height(2.5.dp).clip(RoundedCornerShape(2.dp)).background(TossBlue))
                Spacer(Modifier.height(18.dp))
                // ── 수신 ──
                val recipientName = data.recipient?.takeIf { it.isNotBlank() } ?: "고객님"
                Text("TO.  $recipientName 귀하", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111111))
                Spacer(Modifier.height(3.dp))
                Text("아래와 같이 견적합니다.", fontSize = 12.sp, color = TossTextSecondary)
                Spacer(Modifier.height(18.dp))
                // ── 항목 표 (오픈형, 격자 없음) ──
                QuoteTableHeader()
                data.lines.forEachIndexed { i, line ->
                    QuoteTableRow(line.name, line.spec, won(line.amountWon), i == data.lines.lastIndex)
                }
                Spacer(Modifier.height(14.dp))
                // ── 합계 요약 (우측 3줄, 부가세 명시) ──
                //   미포함=공급가에 10% 더함 / 포함=총액에서 역산. (2026-07-03 사장님)
                val supply: Long
                val vat: Long
                val grand: Long
                if (data.vatIncluded) {
                    grand = data.totalWon
                    supply = Math.round(data.totalWon / 1.1)
                    vat = grand - supply
                } else {
                    supply = data.totalWon
                    vat = Math.round(data.totalWon / 10.0)
                    grand = supply + vat
                }
                Row(Modifier.fillMaxWidth()) {
                    Spacer(Modifier.weight(1f))
                    Column(Modifier.width(215.dp)) {
                        SummaryRow("공급가액", "${won(supply)}원", false)
                        Spacer(Modifier.height(5.dp))
                        SummaryRow("부가세 (10%)", "${won(vat)}원", false)
                        Spacer(Modifier.height(7.dp))
                        Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFE5E9F0)))
                        Spacer(Modifier.height(7.dp))
                        SummaryRow("합계 (부가세 포함)", "${won(grand)}원", true)
                    }
                }
                // ── 계약금 / 잔금 ──
                if (data.depMode != "none") {
                    Spacer(Modifier.height(12.dp))
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFEEF4FF)).padding(14.dp)
                    ) {
                        Row(Modifier.fillMaxWidth()) {
                            Text(
                                "계약금" + (if (data.depMode == "ratio") " (${data.depVal}%)" else ""),
                                fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = SealRed,
                                modifier = Modifier.weight(1f)
                            )
                            Text("${won(depWon)}원", fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold,
                                color = SealRed, textAlign = TextAlign.End)
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(Modifier.fillMaxWidth()) {
                            Text("잔금", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111111),
                                modifier = Modifier.weight(1f))
                            Text("${won(grand - depWon)}원", fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF111111), textAlign = TextAlign.End)
                        }
                    }
                }
                // ── 비고 ──
                Spacer(Modifier.height(16.dp))
                Text("비고", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TossTextTertiary)
                Spacer(Modifier.height(5.dp))
                val workDateText = data.workDateMs
                    ?.let { SimpleDateFormat("M월 d일 (E)", Locale.KOREA).format(java.util.Date(it)) }
                    ?: "협의 후 확정"
                Text("· 시공 예정일 : $workDateText", fontSize = 12.sp, color = TossTextSecondary, lineHeight = 21.sp)
                if (data.depMode != "none") {
                    Text("· 계약금 입금 시 시공일이 확정됩니다", fontSize = 12.sp, color = SealRed,
                        fontWeight = FontWeight.Medium, lineHeight = 21.sp)
                }
                Text("· 유효기간 : 발행일로부터 ${validDays}일", fontSize = 12.sp, color = TossTextSecondary, lineHeight = 21.sp)
                // ── 서명란: 견적일자 + 직인 ──
                Spacer(Modifier.height(22.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFE5E9F0)))
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f)) {
                        Text(today, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111111))
                        Spacer(Modifier.height(3.dp))
                        Text("위와 같이 견적합니다.", fontSize = 12.sp, color = TossTextSecondary)
                        if (bizAddr.isNotBlank()) {
                            Spacer(Modifier.height(3.dp))
                            Text(bizAddr, fontSize = 11.sp, color = TossTextTertiary)
                        }
                    }
                    // 직인 — 음각(백문) 네모 도장: 빨강 꽉참 + 흰 테두리·글자로 진짜 관인/직인 느낌. (2026-07-03 사장님)
                    Box(Modifier.size(84.dp).rotate(-8f), contentAlignment = Alignment.Center) {
                        // 빨강 꽉 찬 네모(살짝 진한 테두리로 도장 각인 느낌)
                        Box(
                            Modifier.matchParentSize().clip(RoundedCornerShape(7.dp))
                                .background(SealRed)
                                .border(2.dp, Color(0xFFB0201A), RoundedCornerShape(7.dp))
                        )
                        // 안쪽 흰 테두리 + 흰 글자(음각)
                        Box(
                            Modifier.size(71.dp).clip(RoundedCornerShape(4.dp))
                                .border(1.5.dp, Color.White, RoundedCornerShape(4.dp))
                                .padding(horizontal = 5.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    seal.replace(" ", ""),
                                    fontSize = 12.sp, fontWeight = FontWeight.Black, color = Color.White,
                                    textAlign = TextAlign.Center, lineHeight = 14.sp, letterSpacing = 0.5.sp
                                )
                                if (bizOwner.isNotBlank()) {
                                    Spacer(Modifier.height(3.dp))
                                    Box(Modifier.width(38.dp).height(1.dp).background(Color.White))
                                    Spacer(Modifier.height(3.dp))
                                    Text(
                                        "대표 $bizOwner",
                                        fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = Color.White,
                                        textAlign = TextAlign.Center, lineHeight = 11.sp
                                    )
                                }
                            }
                        }
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
    Column {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                .background(Color(0xFFEEF4FF)).padding(vertical = 8.dp, horizontal = 4.dp)
        ) {
            Text("품목", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = TossBlue,
                textAlign = TextAlign.Start, modifier = Modifier.weight(2f).padding(horizontal = 4.dp))
            Text("규격", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = TossBlue,
                textAlign = TextAlign.Center, modifier = Modifier.weight(1.2f).padding(horizontal = 4.dp))
            Text("금액(원)", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = TossBlue,
                textAlign = TextAlign.End, modifier = Modifier.weight(1.3f).padding(horizontal = 4.dp))
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(TossBlue))
    }
}

@Composable
private fun QuoteTableRow(name: String, spec: String, amount: String, last: Boolean) {
    Column {
        Row(Modifier.fillMaxWidth().padding(vertical = 11.dp, horizontal = 4.dp)) {
            Text(name, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111111),
                modifier = Modifier.weight(2f).padding(horizontal = 4.dp))
            Text(spec, fontSize = 11.5.sp, color = TossTextTertiary, textAlign = TextAlign.Center,
                modifier = Modifier.weight(1.2f).padding(horizontal = 4.dp))
            Text(amount, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111111), textAlign = TextAlign.End,
                modifier = Modifier.weight(1.3f).padding(horizontal = 4.dp))
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(if (last) Color(0xFFD5DBE3) else Color(0xFFEDF0F4)))
    }
}

@Composable
private fun SummaryRow(label: String, value: String, emphasize: Boolean) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            fontSize = if (emphasize) 13.sp else 11.5.sp,
            fontWeight = if (emphasize) FontWeight.ExtraBold else FontWeight.Medium,
            color = if (emphasize) Color(0xFF111111) else TossTextSecondary,
            modifier = Modifier.weight(1f)
        )
        Text(
            value,
            fontSize = if (emphasize) 16.sp else 12.5.sp,
            fontWeight = if (emphasize) FontWeight.ExtraBold else FontWeight.Bold,
            color = if (emphasize) TossBlue else Color(0xFF111111),
            textAlign = TextAlign.End
        )
    }
}
