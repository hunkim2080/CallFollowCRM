package com.detailline.callfollowcrm.domain.collab

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 🤝 협업 상대 이름 — 수락 후엔 「상호(내 이름표)」. (2026-10-18 사장님 A안)
 *
 * 틀리면 어느 쪽으로 틀리는가 —
 *   수락 전에 상호를 보이면 **아직 아닌 사람을 확정처럼** 보인다(2026-07-09 「요청 중」 버그의 재판).
 *   수락 뒤에도 내 이름표만 보이면 협업 현장의 상호와 **두 이름**이라 헷갈린다(이번 사고).
 */
class CollabDisplayNameTest {

    @Test fun `수락됐고 상호가 있으면 상호 앞 · 내 이름표 괄호`() {
        assertEquals("대박줄눈(김포 김사장)", CollabDisplayName.merge("김포 김사장", "대박줄눈", accepted = true))
    }

    @Test fun `상호와 내 이름표가 같으면 하나만`() {
        assertEquals("대박줄눈", CollabDisplayName.merge("대박줄눈", "대박줄눈", accepted = true))
        assertEquals("대박줄눈", CollabDisplayName.merge(" 대박줄눈 ", "대박줄눈", accepted = true))
    }

    @Test fun `상호가 없으면(아직 서버가 못 채움) 내 이름표만`() {
        assertEquals("김포 김사장", CollabDisplayName.merge("김포 김사장", null, accepted = true))
        assertEquals("김포 김사장", CollabDisplayName.merge("김포 김사장", "  ", accepted = true))
    }

    @Test fun `미수락이면 상호가 있어도 내 이름표만 — 아직 아닌 사람을 확정처럼 보이지 않게`() {
        assertEquals("김포 김사장", CollabDisplayName.merge("김포 김사장", "대박줄눈", accepted = false))
    }

    @Test fun `내 이름표가 비면 상호만`() {
        assertEquals("대박줄눈", CollabDisplayName.merge("", "대박줄눈", accepted = true))
        assertEquals("대박줄눈", CollabDisplayName.merge("   ", "대박줄눈", accepted = true))
    }

    @Test fun `내 이름표가 자리표시(협업 사장님)뿐이면 상호만`() {
        assertEquals("대박줄눈", CollabDisplayName.merge(CollabDisplayName.PLACEHOLDER, "대박줄눈", accepted = true))
    }

    @Test fun `둘 다 없으면 빈 글자 — 부르는 쪽이 거른다`() {
        assertEquals("", CollabDisplayName.merge("", null, accepted = true))
        assertEquals("", CollabDisplayName.merge("", null, accepted = false))
    }

    @Test fun `공백은 다듬는다`() {
        assertEquals("대박줄눈(김포 김사장)", CollabDisplayName.merge("  김포 김사장 ", " 대박줄눈 ", accepted = true))
    }
}
