package com.detailline.callfollowcrm.util

/**
 * 한국 전화번호를 사람이 읽기 좋은 하이픈 형식으로 변환한다.
 *
 * 처리 규칙(가장 흔한 케이스 위주):
 *  - 11자리 휴대폰        010xxxxxxxx       → 010-xxxx-xxxx
 *  - 10자리 휴대폰 (구형)  01xxxxxxxxx       → 01x-xxx-xxxx
 *  - 10자리 서울 국번      02xxxxxxxx        → 02-xxxx-xxxx
 *  - 9자리 서울 국번       02xxxxxxx         → 02-xxx-xxxx
 *  - 10자리 그 외 국번     031xxxxxxx 등     → 031-xxx-xxxx
 *  - 11자리 그 외 국번     070xxxxxxxx 등    → 070-xxxx-xxxx
 *  - 8자리 (지역번호 없음) → xxxx-xxxx
 *
 * 알 수 없는 길이/형식이면 원본 그대로 반환 (안전).
 * 이미 하이픈이 들어 있어도 한 번 풀어서 다시 포맷.
 */
object PhoneNumberFormatter {

    /**
     * 이 문자열이 사실상 '전화번호'인가 — **이름 칸에 번호가 들어있는 고객이 많다**(이름 없이 저장되면
     *   번호가 이름 자리를 차지). 그런 건 사람 이름으로 취급하면 안 된다.
     *   숫자/하이픈/공백/+/괄호만으로 이뤄지고 숫자가 8자리 이상이면 번호.
     *   "김철수 010-…" 처럼 이름이 섞였으면 이름으로 본다(사장님이 일부러 적어둔 정보 — 지우면 손실).
     *   (2026-07-15 오늘의 현장 알림 + 일당 사람 고르기 양쪽에서 필요해 여기로 모음.)
     */
    fun looksLikePhone(s: String): Boolean {
        val t = s.trim()
        if (t.isEmpty()) return false
        if (!t.all { it.isDigit() || it == '-' || it == ' ' || it == '+' || it == '(' || it == ')' }) return false
        return t.count { it.isDigit() } >= 8
    }

    fun format(raw: String): String {
        if (raw.isBlank()) return raw
        val digits = raw.filter { it.isDigit() }
        if (digits.isEmpty()) return raw

        return when {
            digits.length == 11 ->
                "${digits.substring(0, 3)}-${digits.substring(3, 7)}-${digits.substring(7)}"

            digits.length == 10 && digits.startsWith("02") ->
                "${digits.substring(0, 2)}-${digits.substring(2, 6)}-${digits.substring(6)}"

            digits.length == 10 ->
                "${digits.substring(0, 3)}-${digits.substring(3, 6)}-${digits.substring(6)}"

            digits.length == 9 && digits.startsWith("02") ->
                "${digits.substring(0, 2)}-${digits.substring(2, 5)}-${digits.substring(5)}"

            digits.length == 8 ->
                "${digits.substring(0, 4)}-${digits.substring(4)}"

            else -> raw
        }
    }

    /**
     * 입력 도중에도 (= 자릿수가 완성되지 않아도) 하이픈을 점진적으로 붙여주는 포맷.
     * 폰번 입력칸에서 [onValueChange] 콜백으로 사용. 숫자 외 문자는 모두 버리고 다시 조립.
     *
     * 휴대폰(01x): 1~3 → "01x" → 4~7 → "01x-NNNN" → 8~11 → "01x-NNNN-NNNN" (11자 cap)
     * 서울(02): 1~2 → "02" → 3~5 → "02-NNN" → 6~9 → "02-NNN-NNNN" → 10 → "02-NNNN-NNNN" (10자 cap)
     * 기타 지역(031 등): 1~3 → "NNN" → 4~6 → "NNN-NNN" → 7~10 → "NNN-NNN-NNNN" (10자 cap)
     *
     * 빈 입력은 빈 문자열 반환. 숫자 외 시작 (예: "+82") 은 그대로는 처리 안 하고 + 와 숫자만 남김.
     */
    fun formatProgressive(input: String): String {
        // 선두의 + 는 보존(국제전화 대비), 이후 숫자만 추출.
        val hasPlus = input.trimStart().startsWith("+")
        val digits = input.filter { it.isDigit() }
        if (digits.isEmpty()) return if (hasPlus) "+" else ""

        val core = when {
            digits.startsWith("02") -> {
                val d = digits.take(10)
                when (d.length) {
                    in 0..2 -> d
                    in 3..5 -> "${d.take(2)}-${d.drop(2)}"
                    in 6..9 -> "${d.take(2)}-${d.substring(2, 5)}-${d.drop(5)}"
                    else -> "${d.take(2)}-${d.substring(2, 6)}-${d.drop(6)}"
                }
            }
            digits.startsWith("01") -> {
                val d = digits.take(11)
                when (d.length) {
                    in 0..3 -> d
                    in 4..7 -> "${d.take(3)}-${d.drop(3)}"
                    else -> "${d.take(3)}-${d.substring(3, 7)}-${d.drop(7)}"
                }
            }
            else -> {
                val d = digits.take(10)
                when (d.length) {
                    in 0..3 -> d
                    in 4..6 -> "${d.take(3)}-${d.drop(3)}"
                    else -> "${d.take(3)}-${d.substring(3, 6)}-${d.drop(6)}"
                }
            }
        }
        return if (hasPlus) "+$core" else core
    }
}
