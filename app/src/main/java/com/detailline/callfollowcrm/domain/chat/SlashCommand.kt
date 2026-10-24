package com.detailline.callfollowcrm.domain.chat

/**
 * 대화창 **"/" 명령** — 치면 바로 접수서·견적·일정·문구·사진. (2026-10-24 사장님 "카톡 봇 벤치마킹")
 *
 * ⊕ 메뉴에 있던 걸 "/"로도 되게 한 것(⊕ 는 그대로 둠). 1단계 = 이 5개를 **바로 실행**,
 *   2단계에서 `/고객` 요약 카드 추가 예정. UI 는 [SlashCommandMenu], 프로토 artifact/5nhqznJqPqXLtyfeTaHULA.
 */
enum class SlashCommand(val key: String, val desc: String) {
    INTAKE("접수서", "시공접수서 보내기"),
    QUOTE("견적", "견적서 작성"),
    SCHEDULE("일정", "내 일정 확인"),
    TEMPLATE("문구", "저장한 문구 넣기"),
    PHOTO("사진", "사진 첨부"),
    CUSTOMER("고객", "이 고객 요약 보기"),
}

/** "/" 명령 거르기 — 순수 함수(화면에서 직접 split/판단하지 말 것). */
object SlashMenu {

    /**
     * 입력이 "/명령" 꼴이면 "/" 뒤 글자(질의)를, 아니면 null.
     * 공백·줄바꿈이 섞이면 **명령이 아니다**(= 그냥 "/" 로 시작하는 문장) → null.
     */
    fun queryOf(input: String): String? {
        if (!input.startsWith("/")) return null
        val q = input.substring(1)
        if (q.any { it == ' ' || it == '\n' }) return null
        return q
    }

    /** 질의로 거른 명령. 빈 질의("/")는 전부. 안 맞으면 빈 목록(→ 드롭다운 숨김). */
    fun match(query: String): List<SlashCommand> =
        if (query.isEmpty()) SlashCommand.entries.toList()
        else SlashCommand.entries.filter { it.key.startsWith(query) }
}
