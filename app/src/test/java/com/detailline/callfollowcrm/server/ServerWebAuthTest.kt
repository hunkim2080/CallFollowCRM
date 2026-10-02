package com.detailline.callfollowcrm.server

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 🔐 **직원 웹 주소(`/api/web/…`)가 인증을 안 거치고 열리는 것**을 막는다. (2026-10-03)
 *
 *   2026-10-02 Fable 코드 점검 🔥4:
 *     "인증 코드가 **28곳에 복붙**돼 있다. AI 가 새 주소를 만들며 **하나 빼먹으면**
 *      토큰 없이 회원 데이터가 열린다."
 *
 *   서버(`server/main.py`)는 파이썬이라 우리 가드가 못 보지만, **글자는 읽을 수 있다.**
 *   그래서 여기서 읽고 센다 — 그리고 이 시험은 **CI 가 배포 전에 돌린다.**
 *
 *   ⚠️ 왜 「테스트」인가: 서버를 바꾸는 게 아니다. 라이브 서버는 git 이 아니고 origin 보다
 *      한참 뒤라(GOTCHAS §3) **구조를 바꾸는 건 위험**하다. 대신 **빠진 걸 잡기만** 한다.
 *
 *   인증은 두 갈래다 (둘 중 하나는 반드시 있어야 한다):
 *     · `_web_owner_from_request(request)` — 브라우저 **쿠키(세션)**. 직원이 PC 에서 볼 때
 *     · `_web_push_auth(owner, token)`     — **앱 → 서버** push. 몸통에 토큰이 온다
 *
 *   로그인하는 길만 예외다 — 거긴 아직 세션이 없으니 열려 있어야 한다.
 */
class ServerWebAuthTest {

    /** 로그인 과정이라 **열려 있어야 하는** 주소. 여기 더 넣을 땐 이유를 적는다. */
    private val 열려야하는곳 = setOf(
        "/api/web/login/ticket",   // QR 띄우기 — 아직 아무도 로그인 안 했다
        "/api/web/authorize",      // 폰이 그 QR 을 승인 — 승인 자체가 인증이다
        "/api/web/login/status"    // 브라우저가 "승인됐나?" 를 묻는다 — 티켓만 안다
    )

    private fun serverFile(): File {
        var dir: File? = File("").absoluteFile
        while (dir != null) {
            File(dir, "server/main.py").let { if (it.exists()) return it }
            dir = dir.parentFile
        }
        throw AssertionError("server/main.py 를 못 찾았다")
    }

    @Test
    fun `직원 웹 주소는 모두 인증을 거쳐야 한다`() {
        val lines = serverFile().readText().split("\n")
        val route = Regex("""^@app\.(?:get|post|put|delete)\("(/api/web/[^"]*)"""")
        val 샘 = Regex("""_web_owner_from_request|_web_push_auth""")

        val 구멍 = mutableListOf<String>()
        var 센것 = 0
        for (i in lines.indices) {
            val path = route.find(lines[i])?.groupValues?.get(1) ?: continue
            센것++
            if (path in 열려야하는곳) continue
            var j = i + 1
            while (j < lines.size && !lines[j].startsWith("@app.")) j++
            val body = lines.subList(i, j).joinToString("\n")
            if (!샘.containsMatchIn(body)) 구멍 += "$path  (main.py:${i + 1})"
        }

        assertTrue("직원 웹 주소를 하나도 못 찾았다 — 검사가 헛돌고 있다", 센것 >= 20)
        assertTrue(
            "🔓 **인증 없이 열리는 직원 웹 주소**가 있습니다. 토큰 없이 회원 자료가 나갑니다.\n" +
                구멍.joinToString("\n") { "   · $it" } +
                "\n   → `_web_owner_from_request(request)` (브라우저 쿠키) 또는 " +
                "`_web_push_auth(owner, token)` (앱 push) 중 하나를 넣으세요.\n" +
                "   → 정말 열려 있어야 하는 길이면 이 시험의 `열려야하는곳` 에 **이유와 함께** 적으세요.",
            구멍.isEmpty()
        )
    }

    @Test
    fun `로그인 길 목록이 실제로 있어야 한다 — 이름이 바뀌면 예외가 조용히 넓어진다`() {
        val txt = serverFile().readText()
        val 사라진것 = 열려야하는곳.filter { !txt.contains("\"$it\"") }
        assertTrue(
            "예외로 적어둔 주소가 서버에 없습니다 — 이름이 바뀌었나요? " +
                "그대로 두면 **다른 주소가 조용히 예외로 들어올 수** 있습니다: $사라진것",
            사라진것.isEmpty()
        )
    }
}
