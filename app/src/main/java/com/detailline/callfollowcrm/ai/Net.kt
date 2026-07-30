package com.detailline.callfollowcrm.ai

import okhttp3.OkHttpClient

/**
 * 서버 저장소의 OkHttp 클라이언트 공용 진입점(보안 §D-4).
 *
 * `OkHttpClient.Builder()` 대신 `Net.builder()` 로 시작하면 [SessionAuthInterceptor] 가 물려
 * 로그인 세션 토큰을 자동으로 모든 api.si0in.kr 요청에 `Authorization: Bearer` 로 부착하고 401 을 감지한다.
 * 타임아웃 등 커스터마이즈는 그대로 체이닝하면 됨 (예: `Net.builder().connectTimeout(...).build()`).
 */
object Net {
    fun builder(): OkHttpClient.Builder =
        OkHttpClient.Builder().addInterceptor(SessionAuthInterceptor)
}
