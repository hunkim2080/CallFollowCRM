package com.detailline.callfollowcrm.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 🏷 **분류 이름 → 이모지** — 시험이 없었다. (2026-10-03)
 *
 *   사장님 선택(2026-09-19 ①): AI 가 아니라 **앱 안에 든 작은 표**로 맞춘다.
 *   *"이모지는 틀려도 손해가 없는 일이다. 그런 일에 서버 호출·돈·기다림을 쓸 이유가 없다."*
 *
 *   ⚠️ 틀려도 손해는 없지만 **빈 칸이 되면 격자가 무너진다** — 그래서 못 맞히면 반드시 🏷.
 *      그리고 **사장님이 직접 고른 이모지는 절대 안 건드린다**(이 셈은 새로 만들 때만 쓴다).
 */
class CategoryEmojiTest {

    private val 분류없음 = "📂"   // 📂
    private val 완료 = "✅"             // ✅
    private val 대기 = "🔨"       // 🔨
    private val 업체 = "🤝"       // 🤝
    private val 택배 = "📦"       // 📦
    private val 지인 = "👥"       // 👥
    private val 수리 = "🔧"       // 🔧
    private val 단골 = "⭐"             // ⭐
    private val 돈 = "💰"         // 💰

    @Test
    fun `자주 쓰는 분류를 맞힌다`() {
        assertEquals(분류없음, CategoryEmoji.forName("미분류"))
        assertEquals(완료, CategoryEmoji.forName("완료"))
        assertEquals(대기, CategoryEmoji.forName("시공 대기"))
        assertEquals(택배, CategoryEmoji.forName("택배"))
        assertEquals(지인, CategoryEmoji.forName("지인"))
        assertEquals(돈, CategoryEmoji.forName("미수"))
    }

    @Test
    fun `띄어쓰기는 무시한다 — 「단골 손님」도 단골이다`() {
        assertEquals(단골, CategoryEmoji.forName("단골 손님"))
        assertEquals(단골, CategoryEmoji.forName("단골손님"))
    }

    @Test
    fun `대소문자를 안 가린다`() {
        assertEquals(수리, CategoryEmoji.forName("AS"))
        assertEquals(수리, CategoryEmoji.forName("as"))
        assertEquals(수리, CategoryEmoji.forName("A/S"))
        assertEquals(단골, CategoryEmoji.forName("VIP"))
    }

    @Test
    fun `못 맞히면 빈 칸이 아니라 기본 딱지다 — 격자가 무너지면 안 된다`() {
        assertEquals(CategoryEmoji.FALLBACK, CategoryEmoji.forName("아무거나"))
        assertEquals(CategoryEmoji.FALLBACK, CategoryEmoji.forName(""))
        assertEquals(CategoryEmoji.FALLBACK, CategoryEmoji.forName("   "))
    }

    @Test
    fun `앞에 있는 말이 먼저다 — 좁은 말이 위에 있다`() {
        // 「미분류」는 맨 위라 「분류」가 들어간 다른 말보다 먼저 걸린다
        assertEquals(분류없음, CategoryEmoji.forName("미분류"))
        // 「업체」가 든 이름은 거래처 쪽으로 간다
        assertEquals(업체, CategoryEmoji.forName("협력업체"))
    }

    @Test
    fun `고를 후보는 여섯 개이고 첫 번째가 추천이다`() {
        val c = CategoryEmoji.candidatesFor("택배")
        assertEquals(6, c.size)
        assertEquals("첫 번째는 맞힌 것이어야 한다", 택배, c.first())
    }

    @Test
    fun `후보에 같은 이모지가 두 번 안 나온다`() {
        listOf("택배", "미분류", "단골", "아무거나").forEach { name ->
            val c = CategoryEmoji.candidatesFor(name)
            assertEquals("「$name」 후보에 겹치는 게 있다: $c", c.size, c.distinct().size)
        }
    }

    @Test
    fun `맞힌 이모지가 흔한 후보에 들어 있어도 한 번만 나온다`() {
        // 📂(미분류)는 흔한 후보 목록에도 들어 있다 — 그래도 한 번만
        val c = CategoryEmoji.candidatesFor("미분류")
        assertEquals(분류없음, c.first())
        assertEquals(1, c.count { it == 분류없음 })
    }

    @Test
    fun `기본 딱지는 비어 있지 않다`() {
        assertTrue(CategoryEmoji.FALLBACK.isNotBlank())
    }
}
