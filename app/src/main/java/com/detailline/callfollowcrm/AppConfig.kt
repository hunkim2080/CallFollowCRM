package com.detailline.callfollowcrm

/**
 * 앱 전역 서버 설정 — baseUrl 한 곳 모음.
 *
 * 2026-06-04: 맥미니 서버를 Cloudflare Tunnel 로 공개 도메인 노출(api.si0in.kr).
 * 이전 Tailnet IP(100.86.114.49:8000) 는 사장님 폰만 닿았으나, 공개 도메인은
 * 고객 폰·외부 네트워크에서도 닿음(서비스화 핵심). 서버 측 endpoint 는 동일.
 *
 * 주의: Ollama(11434) 는 터널에 노출되지 않은 Tailnet 전용이라 여기서 다루지 않음
 * (OllamaRefineRepository 가 직접 Tailnet IP 사용).
 */
object AppConfig {
    /** FastAPI 메인 서버 base URL (공개 도메인). */
    const val BASE_URL: String = "https://api.si0in.kr"

    /**
     * 문자 인증 회원가입 사용 여부. false = 예전 간단 로그인(번호만) 으로 진입.
     *   지금 false 인 이유: SOLAPI(문자 발송)가 서버에 아직 안 켜짐 → 문자 회원가입은 아무도 못 씀(심사자·테스터 막힘).
     *   SOLAPI + 대표번호(발신번호) 준비되면 true 로 바꿔 정식 출시 때 문자 회원가입 활성화. (2026-07-05 사장님)
     */
    const val SMS_SIGNUP_ENABLED: Boolean = false
}
