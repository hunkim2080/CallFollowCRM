package com.detailline.callfollowcrm.presentation.screen.customer

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 고객 메모 자동저장 규칙. (2026-09-16 실기에서 발견한 사고)
 *
 * 사고: 고객 정보를 열기만 해도 **메모가 빈 글자로 저장**됐다.
 *   화면이 열릴 때 고객이 아직 안 왔으면 입력 상자는 ""로 시작한다. 고객이 도착하는 순간
 *   화면 상자가 새로 만들어지면서 옛 상자의 "정리" 코드가 돌았고, 그게 ""를 저장해버렸다.
 *   화면엔 글이 그대로 보여서 아무도 못 알아챘다(표시는 "저장 중…"에서 멈춰 있었다).
 *
 * 그래서 규칙: **사람이 직접 고친 적이 있을 때만** 저장한다.
 */
class MemoAutoSaveRuleTest {

    @Test
    fun `안 고쳤으면 저장하지 않는다`() {
        // 화면만 열었을 때 — 이게 true 가 되면 메모가 날아간다
        assertFalse(shouldSaveMemo(dirty = false, input = "", saved = "현관 비번 1234"))
        assertFalse(shouldSaveMemo(dirty = false, input = "현관 비번 1234", saved = ""))
        assertFalse(shouldSaveMemo(dirty = false, input = "", saved = null))
    }

    @Test
    fun `고쳤고 값이 다르면 저장한다`() {
        assertTrue(shouldSaveMemo(dirty = true, input = "현관 비번 5678", saved = "현관 비번 1234"))
        assertTrue(shouldSaveMemo(dirty = true, input = "처음 쓰는 메모", saved = ""))
    }

    @Test
    fun `지우는 것도 저장한다`() {
        // 사람이 일부러 다 지운 건 존중해야 한다 — 위 사고와 구분되는 지점이 dirty 하나다
        assertTrue(shouldSaveMemo(dirty = true, input = "", saved = "현관 비번 1234"))
    }

    @Test
    fun `고쳤어도 값이 같으면 쓸데없이 저장하지 않는다`() {
        assertFalse(shouldSaveMemo(dirty = true, input = "같은 글", saved = "같은 글"))
        assertFalse(shouldSaveMemo(dirty = true, input = "", saved = null))
    }
}
