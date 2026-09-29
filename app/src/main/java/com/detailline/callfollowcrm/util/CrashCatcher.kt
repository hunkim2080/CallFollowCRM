package com.detailline.callfollowcrm.util

import android.content.Context
import java.io.File

/**
 * 💥 **앱이 죽으면 그 자리를 적어둔다.** (2026-09-30 사장님)
 *
 *   "사람들 오류날 때… 우리가 그 개인 사용자의 로그를 보고 고쳐줄 수 있어야 하는데
 *    지금은 그런 환경이 안 만들어진 것 같아."
 *
 * 맞다. 지금은 **사장님이 [문제 신고]를 직접 눌러야만** 우리가 안다.
 * 앱이 그냥 꺼지면 **아무도 모른다** — 쓰는 분은 「또 꺼졌네」 하고 넘어가고, 우리는 영영 못 고친다.
 *
 * 하는 일 —
 *   ① 죽는 순간 **파일에 적는다.** 그때 서버로 보내려 하면 못 보낸다(앱이 무너지는 중이라).
 *   ② **다음에 앱을 켤 때** 그 파일을 서버로 보낸다. 보내고 지운다.
 *
 * ⚠️ **개인정보는 안 담는다.** 손님 이름·번호·주소·대화는 들어가지 않는다 —
 *    담기는 건 어디서 왜 죽었는지(클래스·줄 번호)와 앱 버전·기기뿐이다.
 *
 * ⚠️ 원래 하던 일을 **가로채지 않는다.** 적기만 하고 시스템 기본 처리에 그대로 넘긴다 —
 *    안 그러면 「죽지도 살지도 않은」 상태로 멈춘다.
 */
object CrashCatcher {

    private const val FILE = "last_crash.txt"
    /** 너무 길면 보내다 실패한다. 원인은 늘 맨 앞 몇 줄에 있다. */
    private const val MAX_CHARS = 8000

    fun install(context: Context) {
        val app = context.applicationContext
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, err ->
            runCatching { write(app, thread, err) }
            // 우리가 삼키면 앱이 멈춘 채로 남는다 — 원래 하던 대로 넘긴다.
            prev?.uncaughtException(thread, err)
        }
    }

    private fun write(context: Context, thread: Thread, err: Throwable) {
        val sb = StringBuilder()
        sb.append("when=").append(System.currentTimeMillis()).append('\n')
        sb.append("thread=").append(thread.name).append('\n')
        sb.append("version=").append(
            runCatching {
                val p = context.packageManager.getPackageInfo(context.packageName, 0)
                "${p.versionName}"
            }.getOrDefault("?")
        ).append('\n')
        sb.append("device=").append(android.os.Build.MANUFACTURER).append(' ')
            .append(android.os.Build.MODEL).append('\n')
        sb.append("android=").append(android.os.Build.VERSION.RELEASE)
            .append(" (SDK ").append(android.os.Build.VERSION.SDK_INT).append(")\n\n")
        sb.append(stackOf(err))
        val text = sb.toString().take(MAX_CHARS)
        File(context.filesDir, FILE).writeText(text)
    }

    private fun stackOf(err: Throwable): String {
        val sw = java.io.StringWriter()
        err.printStackTrace(java.io.PrintWriter(sw))
        return sw.toString()
    }

    /** 적어둔 게 있으면 꺼내고 **파일은 지운다**(두 번 보내지 않게). 없으면 null. */
    fun takeSaved(context: Context): String? {
        val f = File(context.applicationContext.filesDir, FILE)
        if (!f.exists()) return null
        val text = runCatching { f.readText() }.getOrNull()
        runCatching { f.delete() }
        return text?.takeIf { it.isNotBlank() }
    }
}
