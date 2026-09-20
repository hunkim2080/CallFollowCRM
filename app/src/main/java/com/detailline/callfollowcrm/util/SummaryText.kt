package com.detailline.callfollowcrm.util

/**
 * ✨ 한 줄 요약 다듬기. (2026-09-20 사장님 "이모지 빼")
 *
 * 서버가 예전엔 요약 맨 앞에 이모지를 붙여 보냈다(📋 문의 접수 대기 중…).
 * 앱은 그 줄 앞에 **✨**(= AI 가 쓴 글) 를 붙이므로 "✨ 📋 …" 처럼 이모지가 둘 겹쳤다.
 * 서버 프롬프트는 고쳤지만 **이미 저장된 옛 요약**이 남아 있어, 보여줄 때 한 번 더 걸러낸다.
 *
 * 맨 앞 이모지만 떼어낸다 — 문장 한가운데 글자는 건드리지 않는다.
 */
object SummaryText {
    private fun isEmojiOrSpace(ch: Char): Boolean {
        val c = ch.code
        return ch == ' ' || ch == '\u00A0' ||
            c == 0xFE0F || c == 0x200D ||          // 이모지 변형 선택자 · ZWJ
            c in 0x2190..0x21FF ||                 // 화살표
            c in 0x2600..0x27BF ||                 // 잡다한 기호 · 딩벳
            c in 0x2B00..0x2BFF ||
            c in 0xD800..0xDFFF                    // 서로게이트(😀 같은 네 바이트 이모지의 조각)
    }

    fun stripLeadingEmoji(raw: String?): String? {
        val s = raw ?: return null
        var i = 0
        while (i < s.length && isEmojiOrSpace(s[i])) i++
        // 전부 이모지였다면(= 글이 없음) 원본을 그대로 둔다.
        return if (i >= s.length) s else s.substring(i)
    }
}
