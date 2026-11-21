package com.detailline.callfollowcrm.domain.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ComposerInsertTest {

    @Test fun `빈 입력칸 - 넣는 글로 채운다`() {
        assertEquals("네, 주차 가능합니다.", ComposerInsert.append("", "네, 주차 가능합니다."))
    }

    @Test fun `빈칸만 있는 입력칸도 빈 것으로 본다`() {
        assertEquals("네, 주차 가능합니다.", ComposerInsert.append("  \n", "네, 주차 가능합니다."))
    }

    @Test fun `쓰던 글이 있으면 지우지 않고 줄 바꿔 뒤에 붙인다`() {
        assertEquals("안녕하세요\n네, 주차 가능합니다.", ComposerInsert.append("안녕하세요", "네, 주차 가능합니다."))
    }

    @Test fun `두 번 넣으면 두 번 붙는다(넣기만 할 뿐 판단하지 않는다)`() {
        val once = ComposerInsert.append("", "답")
        assertEquals("답\n답", ComposerInsert.append(once, "답"))
    }
}
