package com.detailline.callfollowcrm.debug

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import com.detailline.callfollowcrm.R

/**
 * 디버그 전용 — 홈 위젯(widget_today.xml)을 고정 크기(360×548dp)로 화면에 띄워 **폰에서 실제 렌더를 캡처**해
 * 프로토와 폰트·줄간격을 1:1 대조하려는 미리보기. (위젯은 홈에 자동배치가 안 돼서 이 하네스로 본다.)
 *   adb shell am start -n com.detailline.callfollowcrm/com.detailline.callfollowcrm.debug.WidgetPreviewActivity --es s featured
 *   s = featured | compact | collab | empty
 */
class WidgetPreviewActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val d = resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()
        val root = FrameLayout(this).apply { setBackgroundColor(0xFF141A24.toInt()) }
        val v = LayoutInflater.from(this).inflate(R.layout.widget_today, root, false)
        v.layoutParams = FrameLayout.LayoutParams(dp(360), dp(548), Gravity.CENTER)
        root.addView(v)
        setContentView(root)

        fun set(id: Int, t: String) { v.findViewById<TextView>(id).text = t }
        fun gone(id: Int) { v.findViewById<View>(id).visibility = View.GONE }
        fun show(id: Int) { v.findViewById<View>(id).visibility = View.VISIBLE }

        val scenario = intent.getStringExtra("s") ?: "featured"
        val empty = scenario == "empty"
        val collab = scenario == "collab"
        val compact = scenario == "compact"
        val compactLight = scenario == "compactlight"  // 메모만 있고 견적 없음 — 빈칸이 생기던 경우(아파트 그림으로 채움 검증)
        val tmr = scenario == "tomorrow"               // 저녁 6시 이후 '내일 갈 현장' 모드

        // ① 답장 안 함
        gone(R.id.widget_reply_check); gone(R.id.widget_reply_done)
        set(R.id.widget_unanswered_name, "이름 미등록 고객")
        set(R.id.widget_unanswered_elapsed, "2시간째 대기")
        set(R.id.widget_unanswered_msg, "문자 · 욕실 줄눈 색상 변경 가능할까요?")

        // ② 오늘/내일 갈 현장 머리
        set(R.id.widget_today_label, if (tmr) "내일 갈 현장" else "오늘 갈 현장")
        set(R.id.widget_today_chip, if (tmr) "● 내일 시공" else "● 지금 시공")
        set(R.id.widget_empty_title, if (tmr) "내일 갈 현장이 없어요" else "오늘 갈 현장이 없어요")
        set(R.id.widget_count, if (empty) "" else if (collab) "· 1곳" else "· 2곳")
        set(R.id.widget_synced, "2분 전")

        if (empty) {
            gone(R.id.widget_primary); show(R.id.widget_empty)
            set(R.id.widget_empty_sub, "오늘은 여유 있게,\n다음 일정을 확인해보세요.")
        } else {
            show(R.id.widget_primary); gone(R.id.widget_empty)
            // 칩
            if (collab) { show(R.id.widget_collab_chip); set(R.id.widget_collab_chip, "🤝 협업 · 탄성파인 사장님") }
            else gone(R.id.widget_collab_chip)
            gone(R.id.widget_time)
            set(R.id.widget_region, if (collab) "화성 동탄 · 동탄역" else "수원 영통 · 광교")
            set(R.id.widget_name, if (collab) "힐스테이트자이" else "광교자연앤힐스테이트")
            set(R.id.widget_open, if (collab) "길찾기 — 누르면 출발" else "길찾기")
            if (compact || compactLight) {
                // compact: 작은 동호수 + 메모. compactLight(메모만·견적X)는 아파트 그림+카드배경으로 빈칸 채움.
                gone(R.id.widget_featured_address); show(R.id.widget_compact_address)
                gone(R.id.widget_complex)
                if (compact) {  // 이름 있는 현장 — 단지명 밑에 동호수(세로)
                    set(R.id.widget_name, "탄성 수원 푸르지오 고객 고객님"); set(R.id.widget_region, "경기 수원")
                    show(R.id.widget_compact_complex); set(R.id.widget_compact_complex, "수원역푸르지오자이")
                } else gone(R.id.widget_compact_complex)
                set(R.id.widget_compact_unit, if (collab) "203동 1504호" else "137동 203호")
                show(R.id.widget_memo); set(R.id.widget_memo_text, "욕실 실리콘 상태 먼저 확인\n주차 B1, 세대 1357* 그대로")
                if (compactLight) {
                    gone(R.id.widget_items); show(R.id.widget_motif)
                    v.findViewById<View>(R.id.widget_site_card).setBackgroundResource(R.drawable.widget_site_card_bg)
                    v.findViewById<View>(R.id.widget_card_content).setPadding(dp(13), dp(11), dp(13), dp(11))
                } else {
                    show(R.id.widget_items); set(R.id.widget_items_text, "욕실 2곳 · 현관 · 베란다")
                    gone(R.id.widget_motif)
                    v.findViewById<View>(R.id.widget_site_card).setBackgroundColor(Color.TRANSPARENT)
                }
            } else {
                // featured: 큰 파란 동호수 + 아파트 그림, 메모·견적 없음
                show(R.id.widget_featured_address); gone(R.id.widget_compact_address); show(R.id.widget_motif)
                gone(R.id.widget_complex)
                set(R.id.widget_unit, if (collab) "203동 1504호" else "102동 1203호")
                gone(R.id.widget_memo); gone(R.id.widget_items)
                v.findViewById<View>(R.id.widget_card_content).setPadding(dp(13), dp(11), dp(13), dp(11))
            }
        }

        // ③ 이어서
        set(R.id.widget_next_label, "이어서")
        if (collab) { show(R.id.widget_next_collab) } else gone(R.id.widget_next_collab)
        set(R.id.widget_next_name, "성복역 롯데캐슬")
        set(R.id.widget_next_sub, "용인 수지 · 108동 704호")
        set(R.id.widget_next_time, "오후 2:00")
    }
}
