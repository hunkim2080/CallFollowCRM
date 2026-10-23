package com.detailline.callfollowcrm.util

/**
 * 문자 본문에서 **진짜 URL만** 찾는다 — 한 곳. (2026-10-16 사장님)
 *
 * ⚠️ 왜 안드로이드 기본 `Patterns.WEB_URL` 을 안 쓰나:
 *   "명성입니다.변함없는성원에감사드리며…" 처럼 **한글 문장에 마침표**가 있으면 그걸 주소로 오인해
 *   스팸 문장이 파란 링크가 됐고, 누르면 브라우저가 열렸다(가짜 링크라 위험하기도). (사장님 실기)
 *
 * 규칙 — 다음만 링크로 본다:
 *   · `http://` / `https://` 로 시작
 *   · `www.` 로 시작
 *   · **아스키 호스트 + 알려진 TLD** (`naver.me`, `bit.ly`, `cafe.naver.com` …)
 *   호스트 라벨을 `[a-z0-9-]` 로 묶어 **한글 라벨은 애초에 안 걸린다**("변함없는성원" 은 TLD 도 아님).
 */
object MessageUrl {

    // 알려진 TLD 만(아스키 호스트 뒤) — 흔한 것 + 스팸/단축도메인(AdHeuristics 와 같은 결).
    private val RE = Regex(
        "(?i)(?:" +
            "https?://\\S+" +                                           // 스킴 있는 진짜 URL — 한글 도메인(우리가게.eable.kr 꼴)도 포함, 공백 전까지 (2026-10-19 사장님)
            "|www\\.[a-z0-9\\-._]+(?:/[^\\s가-힣]*)?" +                   // www. 로 시작
            "|\\b[a-z0-9][a-z0-9\\-]*(?:\\.[a-z0-9\\-]+)*\\." +          // 아스키 호스트(여러 라벨)
            "(?:com|net|org|io|kr|me|ly|gg|link|shop|xyz|top|vip|club|info|biz|tv|cc|app|site|store|page|gl|me2|co)" +
            "(?:/[^\\s가-힣]*)?" +                                        // 뒤 경로(선택)
        ")"
    )

    data class Hit(val start: Int, val end: Int, val url: String)

    /** 본문의 모든 URL 위치 — [linkifyBody] 가 파란 밑줄을 그릴 자리. */
    fun find(body: String): List<Hit> =
        RE.findAll(body).map { Hit(it.range.first, it.range.last + 1, withScheme(it.value)) }.toList()

    /** 첫 URL — 스킴 보정해서. 없으면 null. */
    fun first(body: String): String? = RE.find(body)?.value?.let(::withScheme)

    private fun withScheme(raw: String): String =
        if (raw.startsWith("http://", true) || raw.startsWith("https://", true)) raw else "https://$raw"
}
