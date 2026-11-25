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

    // ── 🧊 ANR·강제종료·네이티브 크래시 잡기 (2026-11-25 사장님 "앱 강제종료되면 잡히게") ──
    //    위 UncaughtExceptionHandler 는 **예외(크래시)만** 잡는다. ANR(메인스레드 멈춤)·네이티브·메모리 종료는
    //    예외가 아니라 못 잡는다. 안드로이드가 적어두는 종료사유(ApplicationExitInfo, Android 11+)를
    //    **다음 켤 때** 읽어, 비정상 죽음만 리포트로 돌려준다(부르는 쪽이 서버로 보냄). 같은 죽음은 timestamp 로 1번만.
    private const val EXIT_PREFS = "crashcatcher_exit"
    private const val EXIT_TS_KEY = "last_reported_exit_ts"

    /**
     * 지난 **비정상 종료**(ANR·네이티브·시그널·메모리·자원과다) 1건을 리포트 문자열로. 없거나 이미 보냈으면 null.
     *   앱 예외 크래시(REASON_CRASH)는 위 핸들러가 이미 파일로 적으니 여기선 제외(중복 방지).
     *   ANR·네이티브면 trace(멈춘 순간 메인스레드 덤프 = 어느 줄에서 멈췄나)도 함께 담는다.
     */
    fun lastAbnormalExit(context: Context): String? {
        if (android.os.Build.VERSION.SDK_INT < 30) return null   // ApplicationExitInfo = API 30+
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager ?: return null
        val infos = runCatching {
            am.getHistoricalProcessExitReasons(context.packageName, 0, 10)
        }.getOrNull().orEmpty()
        val sp = context.getSharedPreferences(EXIT_PREFS, Context.MODE_PRIVATE)
        val lastTs = sp.getLong(EXIT_TS_KEY, 0L)
        // 최근 것부터, 아직 안 보낸 비정상 죽음 1건.
        val info = infos.firstOrNull { it.reason in ABNORMAL && it.timestamp > lastTs } ?: return null
        sp.edit().putLong(EXIT_TS_KEY, info.timestamp).apply()
        val sb = StringBuilder()
        sb.append("reason=").append(reasonName(info.reason)).append('\n')
        sb.append("when=").append(info.timestamp).append('\n')
        sb.append("desc=").append(info.description ?: "").append('\n')
        sb.append("version=").append(
            runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrDefault("?")
        ).append('\n')
        sb.append("device=").append(android.os.Build.MANUFACTURER).append(' ')
            .append(android.os.Build.MODEL).append('\n')
        sb.append("android=").append(android.os.Build.VERSION.RELEASE)
            .append(" (SDK ").append(android.os.Build.VERSION.SDK_INT).append(")\n\n")
        // ANR·네이티브 종료엔 trace 가 들어있다. 맨 앞(메인스레드)에 원인이 있다.
        val trace = runCatching { info.traceInputStream?.bufferedReader()?.use { it.readText() } }.getOrNull()
        if (!trace.isNullOrBlank()) sb.append(trace)
        return sb.toString().take(MAX_CHARS)
    }

    /** 비정상 = 사용자/시스템의 정상 종료가 아닌 것. REASON_CRASH(앱 예외)는 핸들러가 이미 잡아 제외. */
    private val ABNORMAL: Set<Int>
        get() = setOf(
            android.app.ApplicationExitInfo.REASON_ANR,                        // 응답 없음
            android.app.ApplicationExitInfo.REASON_CRASH_NATIVE,               // 네이티브 크래시
            android.app.ApplicationExitInfo.REASON_SIGNALED,                   // 시그널 강제종료
            android.app.ApplicationExitInfo.REASON_LOW_MEMORY,                 // 메모리 부족 킬
            android.app.ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE    // 자원 과다
        )

    private fun reasonName(r: Int): String = when (r) {
        android.app.ApplicationExitInfo.REASON_ANR -> "ANR(응답 없음)"
        android.app.ApplicationExitInfo.REASON_CRASH_NATIVE -> "네이티브 크래시"
        android.app.ApplicationExitInfo.REASON_SIGNALED -> "시그널 강제종료"
        android.app.ApplicationExitInfo.REASON_LOW_MEMORY -> "메모리 부족"
        android.app.ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "자원 과다"
        else -> "기타($r)"
    }
}
