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
     * @param bigLabel 아파트명+동호수 (예: "동아 아파트 718동 602호"). 동/호 부분만 강조.
     * @param roadLine 도로명·지역 (예: "경기 수원시 영통구 봉영로 1526"). 비면 줄 숨김.
     * @param metaLine 시각·안내 (예: "⏰ 오전 9시 · 탭하면 길찾기"). 비면 줄 숨김.
     */
    fun buildView(context: Context, bigLabel: String, roadLine: String?, metaLine: String?): RemoteViews? =
        runCatching {
            val rv = RemoteViews(context.packageName, R.layout.notification_today_site)
            rv.setTextViewText(R.id.today_big, emphasizeUnit(context, bigLabel))
            setLineOrGone(rv, R.id.today_road, roadLine)
            setLineOrGone(rv, R.id.today_meta, metaLine)
            rv
        }.getOrNull()

    /** 동/호 부분에만 청록색 + 밑줄. 못 찾으면 원문(강조 없음). */
    private fun emphasizeUnit(context: Context, label: String): CharSequence {
        val m = UNIT_TAIL.find(label) ?: return label
        val accent = ContextCompat.getColor(context, R.color.today_accent)
        return SpannableString(label).apply {
            setSpan(ForegroundColorSpan(accent), m.range.first, label.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(UnderlineSpan(), m.range.first, label.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    private fun setLineOrGone(rv: RemoteViews, id: Int, text: String?) {
        if (text.isNullOrBlank()) {
            rv.setViewVisibility(id, android.view.View.GONE)
        } else {
            rv.setViewVisibility(id, android.view.View.VISIBLE)
            rv.setTextViewText(id, text)
        }
    }
}
