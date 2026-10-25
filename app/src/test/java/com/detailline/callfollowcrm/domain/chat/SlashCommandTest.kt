package com.detailline.callfollowcrm.domain.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 대화창 "/" 명령 거르기 — 치는 중 뭐가 떠야 하나. */
class SlashCommandTest {

    @Test fun `슬래시로 시작 안 하면 명령 아님`() {
        assertNull(SlashMenu.queryOf("안녕하세요"))
        assertNull(SlashMenu.queryOf(""))
    }

    @Test fun `슬래시만 치면 전부`() {
        assertEquals("", SlashMenu.queryOf("/"))
        assertEquals(SlashCommand.entries.toList(), SlashMenu.match(""))
        assertEquals(5, SlashMenu.match("").size)
    }

    @Test fun `글자로 걸러진다`() {
        assertEquals("접", SlashMenu.queryOf("/접"))
        assertEquals(listOf(SlashCommand.INTAKE), SlashMenu.match("접"))
        assertEquals(listOf(SlashCommand.SCHEDULE), SlashMenu.match("일"))
        assertEquals(listOf(SlashCommand.PHOTO), SlashMenu.match("사진"))
        assertEquals(listOf(SlashCommand.CUSTOMER), SlashMenu.match("고"))
    }

    @Test fun `공백 섞이면 명령 아님 — 그냥 문장`() {
        assertNull(SlashMenu.queryOf("/접수서 보내줘"))
        assertNull(SlashMenu.queryOf("/ 안녕"))
        assertNull(SlashMenu.queryOf("/오늘\n내일"))
    }

    @Test fun `안 맞으면 빈 목록 — 드롭다운 숨김`() {
        assertEquals(emptyList<SlashCommand>(), SlashMenu.match("없는명령"))
    }
}
