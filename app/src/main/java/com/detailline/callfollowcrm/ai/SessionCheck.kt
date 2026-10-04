package com.detailline.callfollowcrm.ai

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

/**
 * 🤝 **앱을 켤 때 서버에 한 번 인사한다.** (2026-10-03 사장님)
 *
 *   사장님: *"앱 키니까 인증번호 쓰라고 안 나오는데?"*
 *
 *   상담함·일정·고객 목록은 전부 **폰 안에 저장된 것**이라 서버를 안 부른다.
 *   그래서 서버가 문을 잠가도 앱은 **아무 일 없는 것처럼** 보였다 —
 *   실제로는 추천·접수서·협업이 **조용히 다 멈춰** 있었는데도.
 *   (가게 전기는 들어오는데 수도만 끊긴 꼴. 받아둔 물이 있어 한참 모른다)
 *
 *   그래서 **켤 때 일부러 한 번** 두드린다. 하는 일은 없고 **막히는지만** 본다.
 *     · 문이 열려 있으면 → 200, 아무 일도 안 난다
 *     · 잠겨 있고 표가 없으면 → 401 + `X-Auth-Required`
 *       → [SessionAuthInterceptor] 가 재로그인 신호를 켜고 → 로그인 화면
 *
 *   ⚠️ **실패해도 조용히 넘긴다** — 인터넷이 없을 때 앱을 못 쓰게 만들면 안 된다.
 *      막는 건 서버가 하고, 여기는 **물어보기만** 한다.
 */
object SessionCheck {

    private val client by lazy { Net.builder().build() }

    suspend fun ping() = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url("${com.detailline.callfollowcrm.AppConfig.BASE_URL}/api/session/check")
                .get().build()
            client.newCall(req).execute().use { it.code }
        }.onFailure {
            Log.d("SessionCheck", "인사 실패(무시): ${it.message}")
        }.getOrNull()
    }
}
