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
}
