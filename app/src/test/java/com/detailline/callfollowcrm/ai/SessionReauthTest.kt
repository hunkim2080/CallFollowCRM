package com.detailline.callfollowcrm.ai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 🔑 **다시 로그인시켜야 하나** — 이 판단이 두 번의 사고를 가른다. (2026-10-03)
 *
 *   폰에서 보려면 **서버의 `AUTH_ENFORCE` 를 켜야** 하고, 켜는 동안
 *   사장님 업무폰이 멈춘다. 그래서 시험이 대신 본다.
 *
 *   막아야 할 두 가지 사고:
 *     · 2026-08-15 — QR 웹 로그인(`/api/web/`) 의 401 로 **앱 세션을 끊어** 로그인이 풀렸다
 *       (사장님 *"QR 찍으면 시공막내 로그인이 풀림"*)
 *     · 403(「본인 것 아님」)으로 로그아웃시키면 **오탐으로 멀쩡한 사람을 쫓아낸다**
 *
 *   그리고 새로 되게 할 것:
 *     · 토큰이 **아예 없던 분**도 서버가 「로그인하라」고 말하면 로그인 화면으로.
 *       (전엔 안내 없이 화면만 텅 비었다)
 */
class SessionReauthTest {

    private val 우리서버 = "api.si0in.kr"

    private fun 판단(
        host: String = 우리서버,
        path: String = "/api/shared/with-me",
        code: Int = 401,
        hadToken: Boolean = false,
        authRequired: Boolean = false,
        sms: Boolean = true
    ) = SessionAuthInterceptor.shouldReauth(host, path, code, hadToken, authRequired, sms)

    // ── 막아야 하는 것 ───────────────────────────────────────────

    @Test
    fun `QR 웹 로그인의 401 로는 앱 세션을 안 끊는다 — 2026-08-15 사고`() {
        // 🔴 여기서 끊어서 「QR 찍으면 로그인이 풀림」이 났다
        assertFalse(판단(path = "/api/web/authorize", hadToken = true))
        assertFalse(판단(path = "/api/web/authorize", authRequired = true))
        assertFalse(판단(path = "/api/web/schedule", hadToken = true, authRequired = true))
    }

    @Test
    fun `403 으로는 안 끊는다 — 본인 것이 아닐 뿐 로그인은 멀쩡하다`() {
        assertFalse(판단(code = 403, hadToken = true))
        assertFalse(판단(code = 403, authRequired = true))
    }

    @Test
    fun `우리 서버가 아니면 아무것도 안 한다`() {
        // Ollama·구글 캘린더·버전 확인 등
        assertFalse(판단(host = "100.86.114.49", hadToken = true))
        assertFalse(판단(host = "si0in.kr", authRequired = true))
        assertFalse(판단(host = "www.googleapis.com", hadToken = true))
    }

    @Test
    fun `OTP 로그인이 꺼져 있으면 안 끊는다 — 보낼 데가 없다`() {
        assertFalse(판단(hadToken = true, sms = false))
        assertFalse(판단(authRequired = true, sms = false))
    }

    @Test
    fun `401 이 아니면 안 끊는다`() {
        listOf(200, 204, 400, 404, 409, 500, 502).forEach {
            assertFalse("코드 $it 에서 끊었다", 판단(code = it, hadToken = true, authRequired = true))
        }
    }

    @Test
    fun `토큰도 없고 서버도 말 안 하면 안 끊는다 — 옛 401 들로 쫓아내면 안 된다`() {
        // 🔴 `AUTH_ENFORCE` 를 켜기 전의 401 들(사진 백필 등)이 여기 걸리면 안 된다
        assertFalse(판단(hadToken = false, authRequired = false))
    }

    // ── 되어야 하는 것 ───────────────────────────────────────────

    @Test
    fun `토큰을 붙였는데도 401 이면 = 만료다 — 다시 로그인`() {
        assertTrue(판단(hadToken = true))
    }

    @Test
    fun `토큰이 없어도 서버가 「로그인하라」고 하면 다시 로그인`() {
        // 🔴 이게 없으면 **안내 없이 화면만 텅 빈다** (OTP 전에 번호만으로 들어온 폰들)
        assertTrue(판단(hadToken = false, authRequired = true))
    }

    @Test
    fun `보호되는 경로 어디서든 같다`() {
        listOf(
            "/api/shared/with-me", "/api/quote/submissions", "/api/team/members",
            "/suggestions/01012345678", "/api/customer-persona/01012345678",
            "/api/site-photos", "/api/mirror/shares", "/api/push/register"
        ).forEach {
            assertTrue("$it 에서 안 걸렸다", 판단(path = it, authRequired = true))
        }
    }

    @Test
    fun `web 이 경로 한가운데 있는 건 괜찮다 — 앞에서 시작할 때만 뺀다`() {
        // `/api/webfeed/...` 같은 게 생겨도 QR 예외로 잘못 빠지면 안 된다
        assertTrue(판단(path = "/api/webfeed/pull", authRequired = true))
    }
}
