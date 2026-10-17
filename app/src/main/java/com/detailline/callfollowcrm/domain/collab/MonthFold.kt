package com.detailline.callfollowcrm.domain.collab

/**
 * 📅 **달 접기 판단 — 여기 한 곳.** (2026-10-17 사장님 "일정 많아지면 보기 힘들다")
 *
 *   달별로 쌓이는 두 화면(공유받은 「업체 내역」 · 「내가 공유한 현장」)이 같은 규칙을 쓴다:
 *   · **기본값 = 가장 최근 달만 펼침**, 나머지는 접힘(달이 쌓여도 머리만 쭉 보인다).
 *   · 달 머리를 누르면 그 달만 뒤집힌다. 상태로 들고 다니는 건 **「뒤집은 달」의 집합**뿐이다 —
 *     그래서 데이터가 새로 와서 최근 달이 바뀌어도(새 달이 생겨도) 새 최근 달이 저절로 펼쳐진다.
 *
 *   순수 함수 — 화면 둘에 복붙하지 않으려고 뗐다(§12-C 「2곳이면 공용」). 시험이 본다.
 */
object MonthFold {

    /** 가장 최근 달 = "yyyy-MM" 문자열 최대값. 비면 null. */
    fun latestOf(yms: Collection<String>): String? = yms.maxOrNull()

    /** 이 달이 펼쳐져 있나 — 최근 달은 기본 펼침, 나머지는 기본 접힘, 뒤집은 달은 반대. */
    fun isOpen(ym: String, latestYm: String?, toggled: Set<String>): Boolean =
        (ym == latestYm) != (ym in toggled)

    /** 달 머리를 눌렀다 — 그 달만 뒤집는다. */
    fun toggle(toggled: Set<String>, ym: String): Set<String> =
        if (ym in toggled) toggled - ym else toggled + ym
}
