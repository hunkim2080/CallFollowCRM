package com.detailline.callfollowcrm.ai

import com.detailline.callfollowcrm.data.SessionTokenStore
import okhttp3.Interceptor
import okhttp3.Response

/**
 * 로그인 세션 토큰을 모든 서버 API 요청에 `Authorization: Bearer` 로 부착(보안 §D-4).
 *
 *  - 토큰이 없으면 아무것도 안 함(무영향) → OTP 로그인을 켜기 전엔 완전 무해(inert).
 *  - 우리 서버(api.si0in.kr)로 가는 요청에만 붙인다. Ollama(Tailnet) 등 다른 호스트는 건드리지 않음.
 *  - 이미 Authorization 헤더가 있으면(관리자·expo 등) 덮지 않는다.
 *  - 401(토큰 만료·무효) 을 받으면 토큰을 폐기하고 재로그인 신호를 보낸다([SessionTokenStore.invalidate]).
 *      · 403(소유권 불일치)은 **자동 로그아웃하지 않음** — 오탐 로그아웃 방지. 그대로 실패로 전달.
 *      · 401 처리도 `SMS_SIGNUP_ENABLED` 가 켜진 뒤에만 작동 → 그전엔 어떤 401 도 로그아웃을 유발하지 않음.
 *      · **웹 뷰어 엔드포인트(`/api/web/*`)의 401 은 앱 로그아웃을 유발하지 않는다** — 자체(QR) 인증 체계라
 *        여기서의 401 은 "앱 세션 만료"가 아니라 "웹 인증 문제". 앱 세션 무효화 X. (아래 사고 참고)
 */
object SessionAuthInterceptor : Interceptor {

    private const val API_HOST = "api.si0in.kr"

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val isOurApi = original.url.host == API_HOST
        val token = SessionTokenStore.current?.token

        val request = if (isOurApi && !token.isNullOrBlank() && original.header("Authorization") == null) {
            original.newBuilder().header("Authorization", "Bearer $token").build()
        } else {
            original
        }

        val response = chain.proceed(request)

        // 웹 뷰어(QR 로그인/스케줄 피드/로그아웃) 엔드포인트는 별도 인증 체계 → 여기서의 401 로 앱 세션을
        // 무효화하면 안 된다. 실제 사고(2026-08-15): 세션토큰 없는 기존 유저가 QR 스캔 → /api/web/authorize
        // 401(서버가 토큰 요구, 90121cd) → 이 인터셉터가 invalidate() → 앱이 재로그인으로 튕김
        // ("스캔하자마자 시공막내 로그인이 풀림"). 진짜 세션 만료는 핵심 API(추천/요약 등)의 401 로 잡힌다.
        val isWebViewerEndpoint = original.url.encodedPath.startsWith("/api/web/")

        if (isOurApi && !isWebViewerEndpoint &&
            response.code == 401 &&
            com.detailline.callfollowcrm.AppConfig.SMS_SIGNUP_ENABLED
        ) {
            // 유효토큰을 부착했는데도(또는 로그인했어야 하는데) 401 = 만료/무효 → 폐기 + 재로그인 유도.
            SessionTokenStore.current?.invalidate()
        }
        return response
    }
}
