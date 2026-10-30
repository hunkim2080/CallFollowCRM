package com.detailline.callfollowcrm.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import com.detailline.callfollowcrm.R
import com.detailline.callfollowcrm.presentation.screen.chat.QuoteDocData
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 🧾 **견적서(직인) 그림** — 화면을 찍지(PixelCopy) 않고 **문서 전체를 1080폭으로 통째로 그린다**.
 *
 *   왜: PixelCopy 는 **화면에 보이는 부분만** 찍어 견적서가 (짧아도) 위/아래가 잘렸다. (2026-10-30 사장님)
 *   정산서 [SettlementShot] 와 같은 길 — 폰 크기와 무관하게 1080 폭. 글자 도구(font·paint·fit)는 [RecordShot] 것 그대로(두 벌 X).
 *   **저장은 호출부가** 한다(cacheDir PNG → FileProvider → 채팅 입력창 첨부). 여기선 그림만.
 *
 *   모양은 Compose 미리보기(QuoteDocScreen)와 같게 — 파란 선·표·합계, 빨간 계약금, 비고, 발행.
 *   무엇을 적을지는 [QuoteDocData](채팅이 만든 것)가 들고 온다. 셈은 없다(계약금만 그 자리에서, QuoteDocScreen 과 같은 식).
 *
 *   ⚠️ 폰에서 눈으로 확인 필요 — 좌표는 1080 폭 기준으로 잡았다(실측 보정은 사장님 확인 후).
 */
object QuoteShot {
    private const val W = 1080
    private const val PAD = 64f
    // 📄 A4 한 장 높이(210:297). 내용이 이보다 짧으면 아래를 흰 여백으로 채워 **인쇄용 한 장 서류**로. (2026-10-30 사장님)
    private val A4_H = Math.round(W * 297f / 210f)   // = 1527

    // 색 — Compose 미리보기(QuoteDocScreen)가 쓰는 토큰 값 그대로(라이트). 서류라 하드코딩이 맞다(SettlementShot 과 같은 길).
    private const val INK = 0xFF111111.toInt()
    private const val SUB = 0xFF5A6472.toInt()      // TossTextSecondary
    private const val HINT = 0xFF9AA3AF.toInt()     // TossTextTertiary
    private const val BLUE = 0xFF3182F6.toInt()     // TossBlue
    private const val BLUE_BG = 0xFFEEF4FF.toInt()  // primaryBg(라이트) ≈ TossBlueSoft
    private const val RULE = 0xFFEEF0F3.toInt()     // TossDivider
    private const val SEAL = 0xFFD6342C.toInt()     // QuoteDocScreen.SealRed
    private const val PAPER = 0xFFFFFFFF.toInt()

    /** 그릴 때 필요한 사업자 정보 — QuoteDocScreen 이 prefs 에서 읽어 넘긴다. */
    data class Biz(
        val name: String, val owner: String, val no: String,
        val addr: String, val phone: String, val validDays: Int
    )

    private val todayFmt = SimpleDateFormat("yyyy. M. d.", Locale.KOREA)
    private val workFmt = SimpleDateFormat("M월 d일 (E)", Locale.KOREA)

    /** 견적서 한 장 — 내용 높이에 딱 맞는 Bitmap. (넉넉한 캔버스에 그린 뒤 실제 쓴 높이로 자른다 = 높이 셈을 두 벌로 안 만든다.) */
    fun render(ctx: Context, data: QuoteDocData, biz: Biz): Bitmap {
        val xbold = RecordShot.font(ctx, R.font.pretendard_extrabold)
        val bold = RecordShot.font(ctx, R.font.pretendard_bold)
        val med = RecordShot.font(ctx, R.font.pretendard_medium)

        val scratchH = (maxOf(A4_H, 1500 + data.lines.size * 110 + (data.memo?.length ?: 0) * 2) + 80).coerceAtMost(8000)
        val scratch = Bitmap.createBitmap(W, scratchH, Bitmap.Config.ARGB_8888)
        val c = Canvas(scratch)
        c.drawColor(PAPER)
        val right = W - PAD
        val innerW = W - PAD * 2

        // ── 헤더: 좌 견적서/ESTIMATE · 우 상호·대표·사업자·전화 ──
        var y = PAD + 56f
        c.drawText("견적서", PAD, y, RecordShot.paint(xbold, 60f, INK))
        c.drawText("ESTIMATE", PAD + 2f, y + 34f, RecordShot.paint(bold, 22f, HINT).apply { letterSpacing = 0.22f })
        run {
            var ry = PAD + 44f
            if (biz.name.isNotBlank()) {
                c.drawText(biz.name, right, ry, RecordShot.fit(RecordShot.paint(bold, 34f, INK, Paint.Align.RIGHT), biz.name, innerW * 0.6f, 34f, 24f)); ry += 36f
            }
            if (biz.owner.isNotBlank()) { c.drawText("대표 ${biz.owner}", right, ry, RecordShot.paint(med, 24f, HINT, Paint.Align.RIGHT)); ry += 30f }
            if (biz.no.isNotBlank()) { c.drawText("사업자 ${biz.no}", right, ry, RecordShot.paint(med, 24f, HINT, Paint.Align.RIGHT)); ry += 30f }
            if (biz.phone.isNotBlank()) c.drawText(PhoneNumberFormatter.format(biz.phone), right, ry, RecordShot.paint(med, 24f, HINT, Paint.Align.RIGHT))
        }
        // 오른쪽 상호 블록이 4줄(상호·대표·사업자·전화)까지 올 수 있어 — 파란 선이 전화번호를 물지 않게 넉넉히. (2026-10-30 사장님 "잘려 보임")
        y += 108f
        // ── 파란 선 ──
        c.drawRect(PAD, y, right, y + 6f, Paint().apply { color = BLUE })
        y += 56f
        // ── 수신 ──
        val recipient = data.recipient?.takeIf { it.isNotBlank() } ?: "고객님"
        val toText = "TO.  $recipient 귀하"
        c.drawText(toText, PAD, y, RecordShot.fit(RecordShot.paint(bold, 36f, INK), toText, innerW, 36f, 24f))
        y += 34f
        c.drawText("아래와 같이 견적합니다.", PAD, y, RecordShot.paint(med, 26f, SUB))
        y += 46f

        // ── 표 머리: 품목 / 규격 / 금액(원) ──
        val amountColW = 260f
        val specColW = 190f
        val nameColW = innerW - amountColW - specColW
        val specCenterX = PAD + nameColW + specColW / 2f
        c.drawRect(PAD, y, right, y + 56f, Paint().apply { color = BLUE_BG })
        val headBase = y + 37f
        c.drawText("품목", PAD + 14f, headBase, RecordShot.paint(bold, 24f, BLUE))
        c.drawText("규격", specCenterX, headBase, RecordShot.paint(bold, 24f, BLUE, Paint.Align.CENTER))
        c.drawText("금액(원)", right - 14f, headBase, RecordShot.paint(bold, 24f, BLUE, Paint.Align.RIGHT))
        y += 56f
        c.drawRect(PAD, y, right, y + 2f, Paint().apply { color = BLUE })

        // ── 줄들 ──
        val nameP = RecordShot.paint(bold, 30f, INK)
        val specP = RecordShot.paint(med, 24f, HINT, Paint.Align.CENTER)
        val amtP = RecordShot.paint(bold, 30f, INK, Paint.Align.RIGHT).apply { fontFeatureSettings = "tnum" }
        val ruleP = Paint().apply { color = RULE }
        data.lines.forEach { line ->
            y += 62f
            c.drawText(ellipsize(line.name, nameP, nameColW - 14f), PAD + 14f, y, nameP)
            if (line.spec.isNotBlank()) c.drawText(ellipsize(line.spec, specP, specColW), specCenterX, y, specP)
            c.drawText(MoneyFormatter.grouped(line.amountWon), right - 14f, y, amtP)
            y += 20f
            c.drawRect(PAD, y, right, y + 1.5f, ruleP)
        }
        y += 42f

        // ── 합계 (우측) + 부가세 ──
        val grand = data.totalWon
        c.drawText("합계", right - 270f, y, RecordShot.paint(xbold, 30f, INK, Paint.Align.RIGHT))
        c.drawText("${MoneyFormatter.grouped(grand)}원", right, y, RecordShot.paint(xbold, 42f, BLUE, Paint.Align.RIGHT).apply { fontFeatureSettings = "tnum" })
        y += 32f
        c.drawText(if (data.vatIncluded) "부가세 포함" else "부가세 별도", right, y, RecordShot.paint(med, 22f, HINT, Paint.Align.RIGHT))
        y += 38f

        // ── 계약금 / 잔금 박스 ──
        val depWon = when (data.depMode) {
            "ratio" -> grand * data.depVal / 100
            "fixed" -> data.depVal * 10_000L
            else -> 0L
        }
        if (data.depMode != "none") {
            val boxTop = y
            val boxH = 146f
            c.drawRoundRect(RectF(PAD, boxTop, right, boxTop + boxH), 20f, 20f, Paint().apply { color = BLUE_BG })
            var by = boxTop + 56f
            val depLabel = "계약금" + (if (data.depMode == "ratio") " (${data.depVal}%)" else "")
            c.drawText(depLabel, PAD + 28f, by, RecordShot.paint(bold, 27f, SEAL))
            c.drawText("${MoneyFormatter.grouped(depWon)}원", right - 28f, by, RecordShot.paint(xbold, 27f, SEAL, Paint.Align.RIGHT).apply { fontFeatureSettings = "tnum" })
            by += 52f
            c.drawText("잔금", PAD + 28f, by, RecordShot.paint(bold, 27f, INK))
            c.drawText("${MoneyFormatter.grouped((grand - depWon).coerceAtLeast(0L))}원", right - 28f, by, RecordShot.paint(xbold, 27f, INK, Paint.Align.RIGHT).apply { fontFeatureSettings = "tnum" })
            y = boxTop + boxH
        }
        y += 42f

        // ── 비고 (긴 줄은 StaticLayout 으로 줄바꿈 — 서류 밖으로 안 나가게) ──
        c.drawText("비고", PAD, y, RecordShot.paint(bold, 24f, HINT))
        y += 16f
        val noteP = TextPaint(RecordShot.paint(med, 25f, SUB))
        val sealNoteP = TextPaint(RecordShot.paint(med, 25f, SEAL))
        val workDateText = data.workDateMs?.let { workFmt.format(Date(it)) } ?: "협의 후 확정"
        y = drawWrapped(c, "· 시공 예정일 : $workDateText", noteP, y, innerW)
        if (data.depMode != "none") {
            y = drawWrapped(c, "· 계약금 입금 시 시공일이 확정됩니다", sealNoteP, y, innerW)
            val dl = MoneyFormatter.manwonOrWon(depWon)
            y = drawWrapped(c, "· 시공이 끝난 뒤 계약금 ${dl}을 제외하고 입금해주시면 됩니다", noteP, y, innerW)
        } else {
            y = drawWrapped(c, "· 시공이 끝난 뒤 입금해주세요", noteP, y, innerW)
        }
        y = drawWrapped(c, "· 유효기간 : 발행일로부터 ${biz.validDays}일", noteP, y, innerW)
        data.memo?.takeIf { it.isNotBlank() }?.let { y = drawWrapped(c, "· $it", noteP, y, innerW) }
        y += 42f

        // ── 서명란: 선 + 발행일 + 문구 + 주소 ──
        c.drawRect(PAD, y, right, y + 1.5f, ruleP)
        y += 48f
        c.drawText(todayFmt.format(Date()), PAD, y, RecordShot.paint(bold, 27f, INK))
        y += 34f
        c.drawText("위와 같이 견적합니다.", PAD, y, RecordShot.paint(med, 24f, SUB))
        if (biz.addr.isNotBlank()) {
            y += 32f
            val ap = RecordShot.paint(med, 22f, HINT)
            c.drawText(ellipsize(biz.addr, ap, innerW), PAD, y, ap)
        }
        y += PAD

        // 📄 내용이 A4 한 장보다 짧으면 **A4 높이까지 흰 여백**으로(인쇄용 한 장 서류). 길면 그 높이로.
        //   scratch 는 처음에 전체를 흰색으로 칠했으니, 내용 아래는 자동으로 흰 여백이다.
        val finalH = y.toInt().coerceIn(1, scratchH)
        val pageH = maxOf(finalH, A4_H).coerceAtMost(scratchH)
        val out = Bitmap.createBitmap(scratch, 0, 0, W, pageH)
        if (out !== scratch) scratch.recycle()
        return out
    }

    /** 한 줄(필요하면 여러 줄로 접어서) 그리고, 다음 줄 시작 y 를 돌려준다. */
    private fun drawWrapped(c: Canvas, text: String, p: TextPaint, top: Float, width: Float): Float {
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, p, width.toInt())
            .setLineSpacing(6f, 1f).build()
        c.save()
        c.translate(PAD, top)
        layout.draw(c)
        c.restore()
        return top + layout.height + 8f
    }

    /** 폭을 넘치면 끝을 「…」로 — 서류 밖으로 글자가 나가면 서류가 아니다. */
    private fun ellipsize(text: String, p: Paint, maxW: Float): String =
        TextUtils.ellipsize(text, TextPaint(p), maxW, TextUtils.TruncateAt.END).toString()
}
