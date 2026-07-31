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
     *   2026-07-31: SOLAPI 실측 통과(사장님 번호로 인증문자 실제 도착 확인) → true 전환. 새 유저 진입 = OTP SignupScreen.
     *     기존 유저(bizPhone 저장돼 있음)는 무영향(OTP 안 거치고 그대로). 세션토큰 저장/부착은 이미 배선됨(보안 §D-4).
     *   ⚠️ 서버 AUTH_ENFORCE(§B-2)는 **이 버전이 테스터 폰에 실제 설치·업데이트된 뒤에야** 켤 것.
     *      그 전에 켜면 구버전(토큰 미부착) 폰이 전 기능 401 로 깨짐. (docs/SERVER_HANDOFF_session_token.md)
     */
    const val SMS_SIGNUP_ENABLED: Boolean = true

    /**
     * 개인정보 동의 문서 버전 (서버 docVersion 과 동일). 이 값이 바뀌면 다음 진입 때 동의 게이트 재노출.
     *   현재 문안=초안. 정식 출시 전 표준양식 검토 후 갱신. (추가97 2026-07-06 cowork)
     */
    const val CONSENT_VERSION: String = "2026-07-06"

    /** 동의문·처리방침 링크 (서버 static). */
    const val CONSENT_REQUIRED_URL: String = "$BASE_URL/consent/required"
    const val CONSENT_OPTIONAL_URL: String = "$BASE_URL/consent/optional"
    const val PRIVACY_POLICY_URL: String = "$BASE_URL/privacy"

    /**
     * 베타 테스터 신청 문자를 받을 번호 (로그인 화면 '베타 신청하기' → 문자앱으로 이 번호에 전송).
     *   화이트리스트에 없는 사람이 신청하면 사장님이 이 번호로 문자를 받아 수동 등록. (2026-07-25 사장님)
     */
    const val BETA_APPLY_PHONE: String = "01039690479"
}
