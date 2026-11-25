package com.detailline.callfollowcrm.service

import android.content.Context
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.UnderlineSpan
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.detailline.callfollowcrm.R

/**
 * 📍 오늘의 현장 알림(현장 1곳) **커스텀 배너** — 왼쪽 청록 바 + 동호수 크게/밑줄 + 도로명 + 시각.
 *   쿠팡식 '눈에 띄는 알림'. 글자색은 레이아웃의 알림 전용 TextAppearance 가 **테마(라이트/다크) 자동 적응**,
 *   동호수만 여기서 today_accent 색 + 밑줄로 강조. (2026-11-25 사장님 "색·밑줄로 눈에 띄게")
 *
 *   ⚠️ inflate/spannable 실패(기기별)면 null → 부르는 쪽([NotificationHelper.showTodaySites])이 글자 알림 폴백.
 */
object TodaySiteNotifier {

    /** 동호수 꼬리 — "718동 602호" / "602호" 가 맨 끝에 있을 때만(이 부분만 밑줄+색). */
    private val UNIT_TAIL = Regex("""(\d{1,4}동\s*)?\d{1,5}호\s*$""")

    /**
     * 긴 라벨을 **단지명 / 동호수** 로 가른다 — 분리 셈은 여기 한 곳(§12. 커스텀 배너·글자 폴백이 같이 씀).
     *   "래미안루센티아아파트 111동 1103호" → ("래미안루센티아아파트", "111동 1103호").
     *   앞에 단지명이 없으면(동호수뿐) 통째로 단지명 자리에 두고 동호수는 null(빈 제목 방지).
     */
    fun splitComplexUnit(label: String): Pair<String, String?> {
        val m = UNIT_TAIL.find(label) ?: return label.trim() to null
        if (m.range.first <= 0) return label.trim() to null
        val complex = label.substring(0, m.range.first).trim()
        val unit = m.value.trim()
        return if (complex.isBlank() || unit.isBlank()) label.trim() to null else complex to unit
    }

    /**
     * @param bigLabel 아파트명+동호수 (예: "동아 아파트 718동 602호"). 단지명/동호수 두 줄로 나눠 그린다.
     * @param roadLine 도로명·지역 (예: "경기 수원시 영통구 봉영로 1526"). 비면 줄 숨김.
     * @param metaLine 시각·안내 (예: "⏰ 오전 9시 · 탭하면 길찾기"). 비면 줄 숨김.
     */
    fun buildView(context: Context, bigLabel: String, roadLine: String?, metaLine: String?): RemoteViews? =
        runCatching {
            val rv = RemoteViews(context.packageName, R.layout.notification_today_site)
            val (complex, unit) = splitComplexUnit(bigLabel)
            rv.setTextViewText(R.id.today_big, complex)
            setLineOrGone(rv, R.id.today_unit, unit?.let { emphasize(context, it) })
            setLineOrGone(rv, R.id.today_road, roadLine)
            setLineOrGone(rv, R.id.today_meta, metaLine)
            rv
        }.getOrNull()

    /** 동호수 줄 전체에 청록색 + 밑줄. */
    private fun emphasize(context: Context, unit: String): CharSequence {
        val accent = ContextCompat.getColor(context, R.color.today_accent)
        return SpannableString(unit).apply {
            setSpan(ForegroundColorSpan(accent), 0, unit.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(UnderlineSpan(), 0, unit.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    private fun setLineOrGone(rv: RemoteViews, id: Int, text: CharSequence?) {
        if (text.isNullOrBlank()) {
            rv.setViewVisibility(id, android.view.View.GONE)
        } else {
            rv.setViewVisibility(id, android.view.View.VISIBLE)
            rv.setTextViewText(id, text)
        }
    }
}
