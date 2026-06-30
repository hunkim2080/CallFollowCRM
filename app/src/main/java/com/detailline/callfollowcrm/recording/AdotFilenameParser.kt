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

    fun parse(filename: String): Parsed? {
        val name = filename.substringAfterLast('/')
        val dot = name.lastIndexOf('.')
        if (dot < 0) return null
        if (name.substring(dot + 1).lowercase() !in ALLOWED_EXT) return null
        val stem = name.substring(0, dot)

        // '_' 로 토큰화 (빈 토큰 제거). 에이닷·삼성 둘 다 '_' 로 번호/날짜를 끊는다.
        val tokens = stem.split('_').map { it.trim() }.filter { it.isNotEmpty() }
        if (tokens.size < 2) return null

        // A) 마지막 토큰이 14자리 숫자(yyyyMMddHHmmss) → 에이닷/T전화. 전화번호 = 직전 토큰의 숫자.
        val last = tokens.last()
        if (last.length == 14 && last.all { it.isDigit() }) {
            val phone = digitsOf(tokens[tokens.size - 2])
            if (phone.length !in 8..15) return null
            val t = runCatching { fmt14().parse(last)?.time }.getOrNull() ?: return null
            return Parsed(phone, t)
        }

        // B) 마지막 둘이 6+6 (yyMMdd_HHmmss) → 삼성 통화녹음. 전화번호 = 그 앞 토큰의 숫자.
        if (tokens.size >= 3) {
            val hhmmss = tokens[tokens.size - 1]
            val yymmdd = tokens[tokens.size - 2]
            if (hhmmss.length == 6 && hhmmss.all { it.isDigit() } &&
                yymmdd.length == 6 && yymmdd.all { it.isDigit() }
            ) {
                // 번호 토큰엔 "통화 녹음 01025918978" 처럼 이름+공백이 섞일 수 있어 마지막 공백조각의 숫자만.
                val phone = digitsOf(tokens[tokens.size - 3].split(Regex("\\s+")).last())
                if (phone.length in 8..15) {
                    val t = runCatching { fmtYY().parse(yymmdd + hhmmss)?.time }.getOrNull() ?: return null
                    return Parsed(phone, t)
                }
            }
        }

        return null
    }

    private fun digitsOf(s: String): String = s.filter { it.isDigit() }
}
