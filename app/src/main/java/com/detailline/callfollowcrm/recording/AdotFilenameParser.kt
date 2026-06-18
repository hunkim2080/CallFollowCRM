package com.detailline.callfollowcrm.recording

import java.text.SimpleDateFormat
import java.util.Locale

/**
 * 통화녹음/통화내용 파일명 파서. 두 형식 모두 지원.
 *
 * A) 에이닷: {전화번호}_{yyyyMMddHHmmss}.m4a
 *    예: 01080052080_20260515090523.m4a  (.txt 도 동일 규칙 — Download/A.phone)
 * B) 삼성 통화녹음: "통화 녹음 {전화번호}_{yyMMdd}_{HHmmss}.m4a"
 *    예: 통화 녹음 01025918978_260424_190911.m4a  (앞 접두어 + 6+6 날짜)
 *
 * 허용: 전화번호 하이픈/공백, 확장자 m4a/mp3/wav/txt, B 형식의 한글 접두어.
 */
object AdotFilenameParser {

    // A: 시작이 전화번호, 14자리 연속 날짜
    private val patAdot = Regex(
        """^([\d\-\s]{9,15})[_\-\s](\d{14})\.(m4a|mp3|wav|txt)$""",
        RegexOption.IGNORE_CASE
    )
    // B: 임의 접두어("통화 녹음 " 등) + 01… 전화번호 + yyMMdd_HHmmss
    private val patSamsung = Regex(
        """^.*?(01[\d\-\s]{8,12})[_\-\s](\d{6})[_\-\s](\d{6})\.(m4a|mp3|wav|txt)$""",
        RegexOption.IGNORE_CASE
    )
    // ⚠️ SimpleDateFormat 은 스레드 비안전 → 공유 필드로 두면 동시 스캔 시 날짜가 틀리게 파싱될 수 있음
    //    (object 싱글톤이라 여러 스캔 코루틴이 동시에 parse 가능). 호출마다 새로 만들어 격리. (2026-06-18 점검)
    private fun fmt14() = SimpleDateFormat("yyyyMMddHHmmss", Locale.KOREA)
    private fun fmtYY() = SimpleDateFormat("yyMMddHHmmss", Locale.KOREA)

    data class Parsed(
        val phoneNumber: String,   // 숫자만 (01080052080)
        val recordedAt: Long       // epoch ms
    )

    fun parse(filename: String): Parsed? {
        val name = filename.substringAfterLast('/')

        patAdot.matchEntire(name)?.let { m ->
            val phone = m.groupValues[1].filter { it.isDigit() }
            if (phone.length < 9) return null
            val time = runCatching { fmt14().parse(m.groupValues[2])?.time }.getOrNull() ?: return null
            return Parsed(phone, time)
        }

        patSamsung.matchEntire(name)?.let { m ->
            val phone = m.groupValues[1].filter { it.isDigit() }
            if (phone.length < 10) return null
            val time = runCatching {
                fmtYY().parse(m.groupValues[2] + m.groupValues[3])?.time
            }.getOrNull() ?: return null
            return Parsed(phone, time)
        }

        return null
    }
}
