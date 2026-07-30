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

        if (isOurApi &&
            response.code == 401 &&
            com.detailline.callfollowcrm.AppConfig.SMS_SIGNUP_ENABLED
        ) {
            // 유효토큰을 부착했는데도(또는 로그인했어야 하는데) 401 = 만료/무효 → 폐기 + 재로그인 유도.
            SessionTokenStore.current?.invalidate()
        }
        return response
    }
}
