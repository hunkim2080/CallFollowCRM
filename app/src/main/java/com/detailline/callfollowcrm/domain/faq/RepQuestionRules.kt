package com.detailline.callfollowcrm.domain.faq

/**
 * 💬 **대표질문(FAQ) 글자 규칙 — 한 곳.** (2026-11-21 · 프로토 EYKUs11cVT7JW71yVrVoBA ③ 등록)
 *
 *   질문 20자 · 답변 500자 · 사진 0/1. 등록 폼(카운터·막기)과 저장소(한 번 더 자르기)가 **같은 숫자**를 쓴다.
 *   순수 함수 — 시험은 `RepQuestionRulesTest`.
 */
object RepQuestionRules {

    // 28자 — 20자는 자연스러운 질문이 중간에 잘렸다("…일정이 겹치는"). 한 줄에 들어가는 선에서 넉넉히. (2026-11-22 사장님)
    const val QUESTION_MAX = 28
    const val ANSWER_MAX = 500
    const val IMAGE_MAX = 1
    const val CATEGORY_MAX = 10

    /** 손님 공개 페이지 "무엇이 궁금하세요?" 의 기본 분류 — 사장님이 「새 분류」로 더하거나 안 골라도 된다. */
    val DEFAULT_CATEGORIES = listOf("가격", "시공시간", "A/S", "관리법")

    /** 분류는 한 줄 짧게 — 줄바꿈 빼고 [CATEGORY_MAX] 자까지. 빈칸이면 「」(미분류). */
    fun clampCategory(raw: String): String = cut(raw.replace('\n', ' ').replace('\r', ' ').trim(), CATEGORY_MAX)

    /** 질문은 한 줄 — 줄바꿈은 빈칸으로, [QUESTION_MAX] 자까지. */
    fun clampQuestion(raw: String): String = cut(raw.replace('\n', ' ').replace('\r', ' '), QUESTION_MAX)

    /** 답변은 [ANSWER_MAX] 자까지(줄바꿈 그대로). */
    fun clampAnswer(raw: String): String = cut(raw, ANSWER_MAX)

    /** 「3/20」 — 라벨 오른쪽 카운터. */
    fun counter(text: String, max: Int): String = "${text.length}/$max"

    /** 등록할 수 있나 — 질문·답 **둘 다** 글자가 있어야(빈칸만은 빈 것). */
    fun canSave(question: String, answer: String): Boolean = question.isNotBlank() && answer.isNotBlank()

    /**
     * 손님 **웹 페이지에서만** 굵게 — `**강조**` 표식. **문자 발송·앱 표시엔 뗀다**(평문).
     *   사장님이 답에 `줄눈 **무료 A/S**` 처럼 적으면 그 부분만 손님 페이지에서 굵게(서버가 처리).
     */
    fun stripEmphasis(text: String): String = text.replace("**", "")

    /**
     * 관리 목록의 답 미리보기 한 줄 — 줄바꿈은 빈칸으로, 사진이 있으면 뒤에 「 (사진)」. 강조 표식(**)은 뗀다.
     *   (프로토 ② 「욕실 기준 평균 OO만원대입니다 (사진)」)
     */
    fun previewLine(answer: String, hasImage: Boolean): String {
        val one = stripEmphasis(answer).trim().replace(Regex("\\s*\\n\\s*"), " ")
        return if (hasImage) "$one (사진)" else one
    }

    /** [max] 자에서 자르되 **이모지(대리쌍)를 반으로 가르지 않는다** — 반쪽이 남으면 깨진 글자(□)로 저장된다. */
    private fun cut(s: String, max: Int): String {
        if (s.length <= max) return s
        val t = s.substring(0, max)
        return if (t.isNotEmpty() && Character.isHighSurrogate(t.last())) t.dropLast(1) else t
    }
}
