package com.detailline.callfollowcrm.domain.collab

import org.junit.Assert.assertEquals
import org.junit.Test

class CollabShareResolveTest {
    private fun ev(sid: String, title: String, name: String, at: Long) =
        CollabShareResolve.EventLite(sid, title, name, at)

    @Test fun `이미 shareId 있으면 그대로`() {
        val events = listOf(ev("sh_X", "성복자이 현장", "해시줄눈", 100))
        assertEquals("sh_keep", CollabShareResolve.fill("해시줄눈", "sh_keep", "성복자이 현장", events))
    }

    @Test fun `빈 shareId 는 제목+상호 최신으로 채움`() {
        val events = listOf(
            ev("sh_old", "성복자이 현장", "해시줄눈", 100),
            ev("sh_new", "성복자이 현장", "해시줄눈", 200),
            ev("sh_other", "다른 현장", "해시줄눈", 300)
        )
        assertEquals("sh_new", CollabShareResolve.fill("해시줄눈", "", "성복자이 현장", events))
    }

    @Test fun `상호 다르면 안 채움`() {
        val events = listOf(ev("sh_X", "성복자이 현장", "다른상호", 100))
        assertEquals("", CollabShareResolve.fill("해시줄눈", "", "성복자이 현장", events))
    }

    @Test fun `제목 다르면 안 채움`() {
        val events = listOf(ev("sh_X", "딴 현장", "해시줄눈", 100))
        assertEquals("", CollabShareResolve.fill("해시줄눈", "", "성복자이 현장", events))
    }

    @Test fun `상호 미상(협업 사장님)이면 제목만으로 채움`() {
        val events = listOf(ev("sh_X", "성복자이 현장", "아무개", 100))
        assertEquals("sh_X", CollabShareResolve.fill("협업 사장님", "", "성복자이 현장", events))
    }

    @Test fun `제목 비면 빈 채로`() {
        val events = listOf(ev("sh_X", "성복자이 현장", "해시줄눈", 100))
        assertEquals("", CollabShareResolve.fill("해시줄눈", "", "", events))
    }
}
