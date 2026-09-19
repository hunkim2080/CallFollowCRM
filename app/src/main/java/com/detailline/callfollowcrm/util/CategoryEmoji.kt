package com.detailline.callfollowcrm.util

/**
 * 분류 이름 → 이모지. **앱 안에 든 작은 표**로 맞춘다. (2026-09-19 사장님 선택 ①)
 *
 * 왜 AI 가 아닌가: 이모지는 **틀려도 손해가 없는 일**이다. 그런 일에 서버 호출·돈·기다림을 쓸 이유가 없다.
 *   ("싼 일은 폰에서" — 2026-09-19 온디바이스 얘기와 같은 자리)
 *   표에 없으면 🏷 로 둔다. 사장님이 직접 고른 이모지는 **절대 안 건드린다.**
 */
object CategoryEmoji {

    /** 못 맞혔을 때. 빈 칸으로 두지 않는다 — 격자가 무너진다. */
    const val FALLBACK = "\uD83C\uDFF7"   // 🏷

    // 앞에 있는 것부터 본다 — 더 좁은 말이 위로.
    private val TABLE: List<Pair<List<String>, String>> = listOf(
        listOf("미분류", "분류 안") to "\uD83D\uDCC2",              // 📂
        listOf("완료", "끝", "마감") to "\u2705",                    // ✅
        listOf("대기", "예정", "잡힘") to "\uD83D\uDD28",           // 🔨
        listOf("업체", "거래", "사장", "협력", "파트너") to "\uD83E\uDD1D", // 🤝
        listOf("택배", "배송", "발송") to "\uD83D\uDCE6",           // 📦
        listOf("일당", "인부", "알바", "아르바이트") to "\uD83D\uDC77", // 👷
        listOf("친구", "지인", "소개", "추천") to "\uD83D\uDC65",    // 👥
        listOf("가족", "집사람", "부모") to "\uD83D\uDC6A",          // 👪
        listOf("as", "a/s", "하자", "수리", "보수") to "\uD83D\uDD27", // 🔧
        listOf("견적", "상담", "문의") to "\uD83D\uDCAC",            // 💬
        listOf("취소", "보류", "중단") to "\u26D4",                   // ⛔
        listOf("단골", "우수", "vip") to "\u2B50",                    // ⭐
        listOf("돈", "미수", "잔금", "입금") to "\uD83D\uDCB0",      // 💰
        listOf("자재", "재료", "구매") to "\uD83E\uDDF1",            // 🧱
        listOf("박람회", "행사") to "\uD83C\uDFAA"                   // 🎪
    )

    /** 이름으로 이모지 하나. 못 맞히면 [FALLBACK]. */
    fun forName(name: String): String {
        val n = name.lowercase().replace(" ", "")
        for ((words, emoji) in TABLE) if (words.any { n.contains(it.replace(" ", "")) }) return emoji
        return FALLBACK
    }

    /** 새 분류를 만들 때 옆에 깔아줄 후보들 — 첫 번째가 추천. 사장님이 다른 걸 고를 수 있게. */
    fun candidatesFor(name: String): List<String> {
        val first = forName(name)
        val common = listOf(
            "\uD83C\uDFF7", "\uD83D\uDC65", "\uD83E\uDD1D", "\u2B50",
            "\uD83D\uDD28", "\u2705", "\uD83D\uDCB0", "\uD83D\uDCC2"
        )
        return (listOf(first) + common.filterNot { it == first }).take(6)
    }
}
