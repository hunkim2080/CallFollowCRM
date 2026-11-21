package com.detailline.callfollowcrm.domain.ui

/**
 * ✍️ **답장 입력칸에 글 넣기 — 한 곳.** (2026-11-21)
 *
 *   「문구 넣기」(자주 쓰는 문구)와 「대표질문」이 **같은 방식**으로 넣는다:
 *   입력칸이 비었으면 그 글로 채우고, 쓰던 글이 있으면 **지우지 않고 줄을 바꿔 뒤에 붙인다**.
 *   넣기만 한다 — 보내는 건 사장님이 ▶ 를 눌러야.
 *
 *   순수 함수 — 시험은 `ComposerInsertTest`.
 */
object ComposerInsert {
    fun append(input: String, piece: String): String =
        if (input.isBlank()) piece else input + "\n" + piece
}
