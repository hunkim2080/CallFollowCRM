package com.detailline.callfollowcrm.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.text.TextPaint
import android.text.TextUtils
import com.detailline.callfollowcrm.R
import com.detailline.callfollowcrm.domain.collab.SettlementStatement
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 🧾 **일당 정산서 한 장** — 협업 「업체 내역」의 완료 현장을 **모던한 공식 서류** 모양 그림으로. (2026-10-17 사장님 승인 프로토)
 *
 *   화면을 찍지(PixelCopy) 않고 **새로 그린다** — [RecordShot] 과 같은 길. 현장이 30줄이어도 화면 밖이라고 잘리지 않고,
 *   폰 크기와 무관하게 1080 폭으로 고정된다. 글자 도구(font · paint · fit)는 [RecordShot] 것을 그대로 쓴다(두 벌 X).
 *   **저장·공유도 [RecordShot.save] · [RecordShot.share] 그대로**(MediaStore 사진첩 + cacheDir/shared FileProvider) — 여기선 그림만.
 *
 *   무엇을 적을지는 [SettlementStatement] 가 정한다(완료 건만 · 합계 · 기간). 여기엔 셈이 없다.
 *
 *   모양(승인 프로토): 검정·회색만(앱 보라/파랑 없음) · 왼쪽 위 작은 「일당 정산서」 · 오른쪽 위 상호 ·
 *   업체명 크게 · 기간 · 받은 일당 합계 · 표(날짜 M.dd / 현장 단지명+도로명 / 금액 콤마) · 합계 줄(N건 · 합계) ·
 *   발행(상호 · 대표자 / 번호 · 발행일). 상태 딱지·도장 없음.
 *
 *   ⚠️ 폰에서 눈으로 못 봤다(2026-10-17) — 좌표는 Pretendard 실측 비율([RecordShot] 의 0.81 등)을 따랐다.
 */
object SettlementShot {

    private const val W = 1080
    private const val PAD = 72f

    // 서류 색 — 공식 서류라 검정·회색만. 앱 토큰(보라·파랑)을 일부러 안 쓴다.
    private const val INK = 0xFF111111.toInt()
    private const val SUB = 0xFF4B5563.toInt()
    private const val HINT = 0xFF9AA3AF.toInt()
    private const val RULE = 0xFFE5E8EB.toInt()
    private const val HEAD_BG = 0xFFF7F8FA.toInt()
    private const val PAPER = 0xFFFFFFFF.toInt()

    // 표 칸 — 날짜 기둥 폭 · 금액 기둥 폭. 현장 기둥은 그 사이 전부.
    private const val COL_DATE = 150f
    private const val COL_AMOUNT = 230f
    private const val ROW_1LINE = 72f
    private const val ROW_2LINE = 104f

    private val dayFmt = SimpleDateFormat("M.dd", Locale.KOREA)
    private val periodFmt = SimpleDateFormat("yyyy.MM.dd", Locale.KOREA)
    private val issueFmt = SimpleDateFormat("yyyy. M. d.", Locale.KOREA)

    /** 파일 이름 — 「정산서_해시줄눈_20261017」. 사진첩에서 찾기 쉽게. */
    fun fileName(doc: SettlementStatement.Doc): String {
        val safe = doc.partnerName.replace(Regex("[\\\\/:*?\"<>|\\s]+"), "_").take(20).ifBlank { "업체" }
        return "정산서_" + safe + "_" + SimpleDateFormat("yyyyMMdd", Locale.KOREA).format(Date(doc.issuedAtMs))
    }

    /** 기간 글자 — 「2026.09.03 ~ 2026.10.14」, 하루면 한 날짜, 없으면 「-」. */
    fun periodText(doc: SettlementStatement.Doc): String {
        val a = doc.fromMs ?: return "-"
        val b = doc.toMs ?: a
        val s = periodFmt.format(Date(a)); val e = periodFmt.format(Date(b))
        return if (s == e) s else "$s ~ $e"
    }

    /** 높이는 내용에 맞춘다 — 줄 수대로 자란다(한 장에 다 들어간다). */
    private fun rowHeight(line: SettlementStatement.Line): Float =
        if (line.siteSub.isNullOrBlank()) ROW_1LINE else ROW_2LINE

    fun render(ctx: Context, doc: SettlementStatement.Doc): Bitmap {
        val xbold = RecordShot.font(ctx, R.font.pretendard_extrabold)
        val bold = RecordShot.font(ctx, R.font.pretendard_bold)
        val med = RecordShot.font(ctx, R.font.pretendard_medium)

        // ── 높이 셈 (그리기 전에 한 번) ──
        val rowsH = if (doc.isEmpty) ROW_1LINE else doc.lines.sumOf { rowHeight(it).toDouble() }.toFloat()
        val headerBlock = 30f + 70f + 50f + 48f + 44f          // 윗줄 · 업체명 · 기간 · 합계 요약 · 굵은 줄까지
        val tableHead = 56f
        val totalBlock = 12f + 64f + 36f
        val footer = 70f + 40f
        val h = (PAD + headerBlock + tableHead + rowsH + totalBlock + footer + PAD).toInt()

        val bmp = Bitmap.createBitmap(W, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(PAPER)
        val right = W - PAD
        val innerW = W - PAD * 2

        // ── 윗줄: 왼쪽 작은 제목 / 오른쪽 상호 ──
        var y = PAD + 30f
        c.drawText("일당 정산서", PAD, y, RecordShot.paint(med, 26f, HINT).apply { letterSpacing = 0.08f })
        if (doc.issuerName.isNotBlank()) {
            val p = RecordShot.fit(RecordShot.paint(bold, 30f, INK, Paint.Align.RIGHT), doc.issuerName, innerW / 2, 30f, 20f)
            c.drawText(doc.issuerName, right, y, p)
        }

        // ── 업체명 (크게) ──
        y += 70f
        val nameP = RecordShot.fit(RecordShot.paint(xbold, 52f, INK), doc.partnerName, innerW, 52f, 32f)
        c.drawText(doc.partnerName, PAD, y, nameP)

        // ── 기간 ──
        y += 50f
        c.drawText("기간", PAD, y, RecordShot.paint(med, 26f, HINT))
        c.drawText(periodText(doc), PAD + 90f, y, RecordShot.paint(med, 28f, SUB).apply { fontFeatureSettings = "tnum" })

        // ── 받은 일당 합계 ──
        y += 48f
        c.drawText("받은 일당 합계", PAD, y, RecordShot.paint(med, 26f, HINT))
        c.drawText(MoneyFormatter.won(doc.totalWon), right, y,
            RecordShot.paint(xbold, 40f, INK, Paint.Align.RIGHT).apply { fontFeatureSettings = "tnum" })

        // ── 굵은 줄 ──
        y += 44f
        c.drawRect(PAD, y - 3f, right, y, Paint().apply { color = INK })

        // ── 표 머리 ──
        c.drawRect(PAD, y, right, y + tableHead, Paint().apply { color = HEAD_BG })
        val headP = RecordShot.paint(bold, 24f, HINT)
        val headBase = y + 36f
        c.drawText("날짜", PAD + 12f, headBase, headP)
        c.drawText("현장", PAD + COL_DATE, headBase, headP)
        c.drawText("금액(원)", right - 12f, headBase, RecordShot.paint(bold, 24f, HINT, Paint.Align.RIGHT))
        y += tableHead
        c.drawRect(PAD, y, right, y + 1.5f, Paint().apply { color = RULE })

        // ── 줄들 ──
        val dateP = RecordShot.paint(bold, 28f, INK).apply { fontFeatureSettings = "tnum" }
        val siteP = RecordShot.paint(bold, 30f, INK)
        val subP = RecordShot.paint(med, 24f, HINT)
        val amtP = RecordShot.paint(bold, 30f, INK, Paint.Align.RIGHT).apply { fontFeatureSettings = "tnum" }
        val siteMaxW = innerW - COL_DATE - COL_AMOUNT
        val ruleP = Paint().apply { color = RULE }
        if (doc.isEmpty) {
            c.drawText("완료된 현장이 아직 없어요", PAD + COL_DATE, y + 46f, RecordShot.paint(med, 28f, HINT))
            y += ROW_1LINE
            c.drawRect(PAD, y, right, y + 1.5f, ruleP)
        } else {
            doc.lines.forEach { line ->
                val rh = rowHeight(line)
                c.drawText(dayFmt.format(Date(line.dateMs)), PAD + 12f, y + 46f, dateP)
                c.drawText(ellipsize(line.site, siteP, siteMaxW), PAD + COL_DATE, y + 46f, siteP)
                line.siteSub?.takeIf { it.isNotBlank() }?.let { sub ->
                    c.drawText(ellipsize(sub, subP, siteMaxW), PAD + COL_DATE, y + 84f, subP)
                }
                // 표 안은 「원」 없이 콤마만 — 머리에 (원) 이 적혀 있다. 콤마는 MoneyFormatter 한 곳.
                c.drawText(MoneyFormatter.grouped(line.wageWon.toString()), right - 12f, y + 46f, amtP)
                y += rh
                c.drawRect(PAD, y, right, y + 1.5f, ruleP)
            }
        }

        // ── 합계 줄 ──
        y += 12f
        c.drawRect(PAD, y, right, y + 2f, Paint().apply { color = INK })
        y += 64f
        c.drawText("합계", PAD + 12f, y, RecordShot.paint(bold, 28f, SUB))
        c.drawText("${doc.count}건", PAD + COL_DATE, y, RecordShot.paint(med, 28f, SUB).apply { fontFeatureSettings = "tnum" })
        c.drawText(MoneyFormatter.won(doc.totalWon), right - 12f, y,
            RecordShot.paint(xbold, 40f, INK, Paint.Align.RIGHT).apply { fontFeatureSettings = "tnum" })
        y += 36f

        // ── 발행 ──
        y += 70f
        c.drawText("발행", PAD, y, RecordShot.paint(med, 24f, HINT))
        val issuer = listOfNotNull(
            listOfNotNull(doc.issuerName.takeIf { it.isNotBlank() }, doc.issuerOwner.takeIf { it.isNotBlank() })
                .joinToString(" · ").takeIf { it.isNotBlank() },
            doc.issuerPhone.takeIf { it.isNotBlank() }
        ).joinToString(" / ")
        val dateStr = issueFmt.format(Date(doc.issuedAtMs))
        val dateP2 = RecordShot.paint(med, 26f, SUB, Paint.Align.RIGHT)
        c.drawText(dateStr, right, y, dateP2)
        if (issuer.isNotBlank()) {
            val maxW = innerW - 70f - dateP2.measureText(dateStr) - 24f
            val ip = RecordShot.paint(bold, 26f, SUB)
            c.drawText(ellipsize(issuer, ip, maxW), PAD + 70f, y, ip)
        }
        return bmp
    }

    /** 폭을 넘치면 끝을 「…」로 — 서류 밖으로 글자가 나가면 서류가 아니다. */
    private fun ellipsize(text: String, p: Paint, maxW: Float): String =
        TextUtils.ellipsize(text, TextPaint(p), maxW, TextUtils.TruncateAt.END).toString()
}
