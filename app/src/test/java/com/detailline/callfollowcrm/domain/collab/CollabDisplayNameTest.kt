package com.detailline.callfollowcrm.domain.collab

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 🤝 협업 상대 이름 — ① 내가 저장/지은 이름 → ② 상대 사업자명 → ③ 번호. (2026-10-27 사장님 규칙)
 */
class CollabDisplayNameTest {

    @Test fun `내가 저장한 이름이 있으면 그걸로 (상대 상호보다 우선)`() {
        assertEquals("김포 김사장", CollabDisplayName.merge("김포 김사장", "대박줄눈", accepted = true))
        assertEquals("탄성 파인", CollabDisplayName.merge("탄성 파인", "협업 사장", accepted = true))
    }

    @Test fun `내 이름 없으면 상대가 등록한 사업자명`() {
        assertEquals("대박줄눈", CollabDisplayName.merge("", "대박줄눈", accepted = true))
        assertEquals("대박줄눈", CollabDisplayName.merge(CollabDisplayName.PLACEHOLDER, "대박줄눈", accepted = true))
        assertEquals("대박줄눈", CollabDisplayName.merge("협업 사장", "대박줄눈", accepted = true))
        assertEquals("대박줄눈", CollabDisplayName.merge("01022647734", "대박줄눈", accepted = true))
    }

    @Test fun `미수락이면 상대 사업자명 안 쓴다 — 내 이름 또는 번호`() {
        assertEquals("김포 김사장", CollabDisplayName.merge("김포 김사장", "대박줄눈", accepted = false))
        assertEquals("010-2264-7734", CollabDisplayName.merge("01022647734", "대박줄눈", accepted = false))
    }

    @Test fun `둘 다 없으면 번호 — 하이픈 010-0000-0000`() {
        assertEquals("010-2264-7734", CollabDisplayName.merge("01022647734", null, accepted = true))
        assertEquals("010-2264-7734", CollabDisplayName.merge("010-2264-7734", null, accepted = true))
        assertEquals("010-2264-7734", CollabDisplayName.merge("협업 사장", "01022647734", accepted = true))
    }

    @Test fun `둘 다 없고 번호도 없으면 빈 글자 — 부르는 쪽이 거른다`() {
        assertEquals("", CollabDisplayName.merge("", null, accepted = true))
        assertEquals("", CollabDisplayName.merge(CollabDisplayName.PLACEHOLDER, null, accepted = true))
    }

    @Test fun `공백은 다듬는다`() {
        assertEquals("김포 김사장", CollabDisplayName.merge("  김포 김사장 ", " 대박줄눈 ", accepted = true))
    }
}
