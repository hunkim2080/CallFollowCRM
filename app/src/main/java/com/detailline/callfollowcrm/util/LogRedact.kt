package com.detailline.callfollowcrm.util

/**
 * 로그에 전화번호·이름 같은 PII 를 남기지 않기 위한 마스킹 유틸.
 * 릴리즈 빌드가 Log 를 strip 하지 않는 동안(minify off) logcat/버그리포트로 새는 것 방지. (2026-07-30 보안 감사)
 */
object LogRedact {
    /** 전화번호를 뒤 4자리만 남기고 마스킹. 예: 010-1234-5678 → ***5678. */
    fun phone(p: String?): String {
        val d = p?.filter { it.isDigit() }.orEmpty()
        return if (d.length < 4) "***" else "***" + d.takeLast(4)
    }
}
