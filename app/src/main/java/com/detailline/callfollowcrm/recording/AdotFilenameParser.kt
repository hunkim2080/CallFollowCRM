package com.detailline.callfollowcrm.recording

import java.text.SimpleDateFormat
import java.util.Locale

/**
 * 통화녹음/통화내용 파일명 파서. 에이닷·T전화·삼성 형식 모두 지원.
 *
 * A) 에이닷/T전화: [{연락처이름}_]{전화번호}_{yyyyMMddHHmmss}.ext
 *    예: 01080052080_20260515090523.m4a               (이름 없음)
 *        상민이_01024227744_20260521205004.m4a         (이름 접두어)
 *        💘사랑하는와이프💘_01048052630_20260513101135.m4a (이모지 이름)
 *        화성도시공사동부공원_0312671286_20260512160452.m4a (지역번호 10자리)
 *        회선생낭만포차_050713457734_20260520201012.m4a   (0507 안심번호 12자리)
 *    (.txt 도 동일 규칙 — Download/A.phone)
 * B) 삼성 통화녹음: "통화 녹음 {전화번호}_{yyMMdd}_{HHmmss}.ext"
 *    예: 통화 녹음 01025918978_260424_190911.m4a
 *
 * ★ 토큰 기반 파싱 — '_' 로 끊어 **맨 끝 토큰**으로 형식을 판별한다.
 *   끝이 14자리 숫자 → A(연속 날짜). 끝 둘이 6+6 → B. 전화번호는 날짜 토큰 바로 앞.
 *   이렇게 하면 앞에 무슨 이름(한글/이모지/공백/숫자)이 붙어도 끝의 [번호_날짜]만 보고 안전히 뽑는다.
 *   (옛 정규식은 "번호로 시작"만 봐서 `상민이_010…` 처럼 이름 붙은 파일을 통째로 놓쳤음. 2026-06-30 수정)
 *
 * ★★ C) **번호 자리에 이름이 든 파일** — [parseLoose] (2026-07-16 사장님 현장)
 *    예: 통화 남이편_260716_112558.m4a   /  홍길동_20260716112558.m4a
 *    폰(기기·통신사)에 따라 **연락처에 저장된 사람이면 번호 대신 이름**을 넣는다. 그러면 [parse] 는 null →
 *    그 녹음은 앱 눈에 아예 안 보였다(= "녹음 파일을 못 찾았어요"의 진짜 원인. 사장님 폰 실물 확인).
 *    → [parseLoose] 는 번호가 없어도 **시각만은** 뽑아 준다. 번호는 호출부가 통화기록으로 되찾는다
 *      (폰은 한 번에 한 통화만 하므로 '그 시각에 하던 통화' = 그 사람. CallRecordRepository.findCallAtTime).
 *    ⚠️ [parse] 는 기존 동작 그대로(번호 있는 것만) — 번호를 믿고 쓰는 호출부가 많아 건드리지 않는다.
 */
object AdotFilenameParser {

    // ⚠️ SimpleDateFormat 은 스레드 비안전 → 공유 필드로 두면 동시 스캔 시 날짜가 틀리게 파싱될 수 있음
    //    (object 싱글톤이라 여러 스캔 코루틴이 동시에 parse 가능). 호출마다 새로 만들어 격리. (2026-06-18 점검)
    private fun fmt14() = SimpleDateFormat("yyyyMMddHHmmss", Locale.KOREA)
    private fun fmtYY() = SimpleDateFormat("yyMMddHHmmss", Locale.KOREA)

    private val ALLOWED_EXT = setOf("m4a", "mp3", "wav", "txt")

    data class Parsed(
        val phoneNumber: String,   // 숫자만 (01080052080)
        val recordedAt: Long       // epoch ms
    )

    /**
     * 느슨한 파싱 결과 — 번호가 없어도 시각은 뽑는다.
     * @param phoneNumber 파일명에 번호가 있으면 숫자만, 없으면 null (이땐 [nameHint] 로 사람 이름이 들어옴).
     * @param nameHint 번호 자리에 있던 이름("남이편"). 앞의 고정 접두어("통화 녹음"/"통화")는 떼고 준다. 없으면 null.
     */
    data class Loose(
        val phoneNumber: String?,
        val nameHint: String?,
        val recordedAt: Long
    )

    /** 번호가 확실히 든 파일만. 기존 호출부 계약 그대로(번호를 믿고 쓰는 곳들). */
    fun parse(filename: String): Parsed? {
        val l = parseLoose(filename) ?: return null
        val phone = l.phoneNumber ?: return null
        return Parsed(phone, l.recordedAt)
    }

    /**
     * 번호가 없어도(연락처 이름만 들어도) **시각**은 뽑는다. 번호 되찾기는 호출부(통화기록 대조)의 몫.
     *   ※ 통화녹음이 아닌 오디오(음성메모 등)도 이름 끝이 `_날짜_시각` 이면 통과할 수 있다 →
     *     번호 없는 건 반드시 '그 시각에 통화가 실제로 있었는지' 로 한 번 더 거른 뒤 쓸 것.
     */
    fun parseLoose(filename: String): Loose? {
        val name = filename.substringAfterLast('/')
        val dot = name.lastIndexOf('.')
        if (dot < 0) return null
        if (name.substring(dot + 1).lowercase() !in ALLOWED_EXT) return null
        val stem = name.substring(0, dot)

        // '_' 로 토큰화 (빈 토큰 제거). 에이닷·삼성 둘 다 '_' 로 번호/날짜를 끊는다.
        val tokens = stem.split('_').map { it.trim() }.filter { it.isNotEmpty() }
        if (tokens.size < 2) return null

        // A) 마지막 토큰이 14자리 숫자(yyyyMMddHHmmss) → 에이닷/T전화. 전화번호(또는 이름) = 직전 토큰.
        val last = tokens.last()
        if (last.length == 14 && last.all { it.isDigit() }) {
            val t = runCatching { fmt14().parse(last)?.time }.getOrNull() ?: return null
            return withOwner(tokens[tokens.size - 2], t)
        }

        // B) 마지막 둘이 6+6 (yyMMdd_HHmmss) → 삼성 통화녹음. 전화번호(또는 이름) = 그 앞 토큰.
        if (tokens.size >= 3) {
            val hhmmss = tokens[tokens.size - 1]
            val yymmdd = tokens[tokens.size - 2]
            if (hhmmss.length == 6 && hhmmss.all { it.isDigit() } &&
                yymmdd.length == 6 && yymmdd.all { it.isDigit() }
            ) {
                val t = runCatching { fmtYY().parse(yymmdd + hhmmss)?.time }.getOrNull() ?: return null
                return withOwner(tokens[tokens.size - 3], t)
            }
        }

        return null
    }

    /**
     * 날짜 토큰 바로 앞 토큰에서 번호 또는 이름을 뽑는다.
     *   "통화 녹음 01025918978" → 번호 / "통화 남이편" → 이름("남이편") / "01080052080" → 번호.
     *   번호 판정: 마지막 공백조각의 숫자가 8~15자리. (한국 번호: 지역번호 10자리·0507 안심번호 12자리 포함)
     */
    private fun withOwner(ownerToken: String, recordedAt: Long): Loose? {
        // 마지막 공백조각 우선(옛 B 규칙) → 안 되면 토큰 전체의 숫자(옛 A 규칙). 둘 다 기존 동작 보존.
        val phone = digitsOf(ownerToken.split(Regex("\\s+")).last())
            .takeIf { it.length in 8..15 }
            ?: digitsOf(ownerToken).takeIf { it.length in 8..15 }
        if (phone != null) return Loose(phone, null, recordedAt)
        // 번호가 아니면 이름. 고정 접두어("통화 녹음 홍길동" → "홍길동")를 떼고 남는 게 진짜 이름.
        val nameHint = PREFIXES.fold(ownerToken) { acc, p -> acc.removePrefix(p).trim() }
            .takeIf { it.isNotBlank() }
        return Loose(null, nameHint, recordedAt)
    }

    /** 녹음앱이 파일명 앞에 붙이는 고정 접두어 — 이름만 남기려고 뗀다. 긴 것부터(앞이 먹히면 뒤는 안 봄). */
    private val PREFIXES = listOf("통화 녹음", "통화녹음", "통화", "Call recording", "Call")

    private fun digitsOf(s: String): String = s.filter { it.isDigit() }
}
