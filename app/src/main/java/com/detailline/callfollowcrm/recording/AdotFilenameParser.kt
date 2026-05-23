package com.detailline.callfollowcrm.recording

import java.text.SimpleDateFormat
import java.util.Locale

/**
 * 에이닷 통화녹음 파일명 파서.
 *
 * 패턴: {전화번호}_{yyyyMMddHHmmss}.m4a
 * 예: 01080052080_20260515090523.m4a
 *
 * 다양한 변형도 허용:
 *  - 하이픈/공백 포함 (010-8005-2080_20260515090523.m4a)
 *  - 확장자 .mp3 / .wav 도 시도
 */
object AdotFilenameParser {

    private val pattern = Regex("""^([\d\-\s]{9,15})[_\-\s](\d{14})\.(m4a|mp3|wav)$""", RegexOption.IGNORE_CASE)
    private val datetimeFormat = SimpleDateFormat("yyyyMMddHHmmss", Locale.KOREA)

    data class Parsed(
        val phoneNumber: String,   // 숫자만 (01080052080)
        val recordedAt: Long       // epoch ms
    )

    fun parse(filename: String): Parsed? {
        val name = filename.substringAfterLast('/')
        val match = pattern.matchEntire(name) ?: return null
        val rawPhone = match.groupValues[1]
        val rawTime = match.groupValues[2]

        val phone = rawPhone.filter { it.isDigit() }
        if (phone.length < 9) return null

        val time = runCatching { datetimeFormat.parse(rawTime)?.time }.getOrNull() ?: return null
        return Parsed(phoneNumber = phone, recordedAt = time)
    }
}
