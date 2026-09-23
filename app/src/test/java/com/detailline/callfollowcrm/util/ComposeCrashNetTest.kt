package com.detailline.callfollowcrm.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 안전망이 **사장님 폰에서 실제로 앱을 꺼뜨린 크래시는 잡고, 우리 잘못은 안 숨기는지** 증명. (2026-09-24)
 *
 * 왜 이 시험이 필요한가: 사장님 업무폰엔 우리가 만든 앱을 직접 깔 수 없다(플레이로만 들어간다).
 * 그래서 "고쳤습니다" 를 폰에서 눈으로 확인할 수가 없다. 대신 폰에서 꺼낸 **진짜 크래시 스택 그대로**
 * 여기서 재현해 두고, 그물이 그걸 잡는지 시험으로 못 박는다.
 *
 * 2026-09-24 02:09:17 / :20 / :29 · 플레이가 새 버전 42% 내려받던 중 3번 연달아 꺼짐:
 *     java.lang.ArrayIndexOutOfBoundsException: length=0; index=-5
 *         at androidx.compose.runtime.SlotTableKt.key(SlotTable.kt:3522)
 *         ... (전부 androidx.compose.*)
 *         at com.detailline.callfollowcrm.CallFollowCrmApplication.installMainThreadHoverCrashGuard$lambda$9
 */
class ComposeCrashNetTest {

    private fun frame(cls: String, method: String = "x") = StackTraceElement(cls, method, null, -1)

    private fun withStack(t: Throwable, vararg classes: String): Throwable {
        t.stackTrace = classes.map { frame(it) }.toTypedArray()
        return t
    }

    /** 사장님 폰 크래시 그대로 — 전부 프레임워크, 맨 아래만 그물 자신. */
    private fun bossCrash() = withStack(
        ArrayIndexOutOfBoundsException("length=0; index=-5"),
        "androidx.compose.runtime.SlotTableKt",
        "androidx.compose.runtime.SlotReader",
        "androidx.compose.runtime.ComposerImpl",
        "androidx.compose.runtime.CompositionImpl",
        "androidx.compose.ui.layout.LayoutNodeSubcompositionsState",
        "androidx.compose.foundation.lazy.LazyListMeasuredItemProvider",
        "androidx.compose.material3.ScaffoldKt\$ScaffoldLayoutWithMeasureFix\$1\$1",
        "androidx.compose.animation.AnimatedContentKt\$AnimatedContent\$6\$1\$1",
        "androidx.compose.ui.platform.AndroidComposeView",
        "android.view.ViewRootImpl",
        "com.detailline.callfollowcrm.CallFollowCrmApplication",   // ← 그물 자신(항상 찍힌다)
        "android.app.ActivityThread",
    )

    @Test
    fun `사장님 폰에서 앱을 꺼뜨린 그 크래시는 이제 잡힌다`() {
        assertTrue(ComposeCrashNet.isKnownHarmless(bossCrash()))
    }

    @Test
    fun `그물 자신이 스택에 있다고 우리 코드로 세면 안 된다`() {
        // 이 한 줄 때문에 그물코가 통째로 죽을 뻔했다 — Looper 를 감싸고 있어 **항상** 찍히기 때문.
        val onlyGuard = bossCrash().stackTrace.count { it.className.startsWith("com.detailline") }
        assertTrue(onlyGuard == 1)
        assertTrue(ComposeCrashNet.isKnownHarmless(bossCrash()))
    }

    @Test
    fun `우리 화면 코드가 스택에 끼어 있으면 안 삼킨다 — 우리 잘못은 숨기지 않는다`() {
        val ours = withStack(
            ArrayIndexOutOfBoundsException("length=0; index=-5"),
            "androidx.compose.runtime.SlotTableKt",
            "androidx.compose.runtime.ComposerImpl",
            "com.detailline.callfollowcrm.presentation.screen.home.HomeScreenKt",  // ← 우리 코드
            "com.detailline.callfollowcrm.CallFollowCrmApplication",
        )
        assertFalse(ComposeCrashNet.isKnownHarmless(ours))
    }

    @Test
    fun `마우스 hover 크래시는 그대로 잡힌다`() {
        val hover = IllegalStateException("The ACTION_HOVER_EXIT event was not cleared")
        assertTrue(ComposeCrashNet.isKnownHarmless(hover))
    }

    @Test
    fun `미리그리기 prefetch 레이스도 그대로 잡힌다`() {
        val prefetch = withStack(
            IndexOutOfBoundsException("index -3"),
            "androidx.compose.foundation.lazy.layout.LazyLayoutPrefetcher",
            "android.view.Choreographer",
        )
        assertTrue(ComposeCrashNet.isKnownHarmless(prefetch))
    }

    @Test
    fun `평범한 앱 크래시는 그대로 터진다`() {
        val npe = withStack(
            NullPointerException("customer was null"),
            "com.detailline.callfollowcrm.data.repository.CustomerRepository",
        )
        assertFalse(ComposeCrashNet.isKnownHarmless(npe))

        val ise = withStack(
            IllegalStateException("no schedule"),
            "com.detailline.callfollowcrm.presentation.screen.schedule.ScheduleScreenKt",
        )
        assertFalse(ComposeCrashNet.isKnownHarmless(ise))
    }

    @Test
    fun `우리가 목록 번호를 잘못 센 것은 그대로 터진다`() {
        // 같은 IndexOutOfBounds 라도 Compose SlotTable 이 아니면 그물 밖. (우리 버그를 가리면 안 된다)
        val ourIndexBug = withStack(
            IndexOutOfBoundsException("Index 5 out of bounds for length 3"),
            "java.util.ArrayList",
            "com.detailline.callfollowcrm.presentation.screen.chat.ChatScreenKt",
        )
        assertFalse(ComposeCrashNet.isKnownHarmless(ourIndexBug))
    }

    @Test
    fun `속에 감싸인 원인까지 들여다본다`() {
        val wrapped = RuntimeException("wrapper", bossCrash())
        wrapped.stackTrace = arrayOf(frame("android.os.Handler"))
        assertTrue(ComposeCrashNet.isKnownHarmless(wrapped))
    }

    @Test
    fun `원인이 자기 자신을 가리켜도 멈추지 않는다`() {
        // 무한 while 로 앱이 굳는 걸 막는 방어. (그물이 앱을 굳히면 본말전도)
        val a = withStack(RuntimeException("a"), "com.detailline.callfollowcrm.Foo")
        assertFalse(ComposeCrashNet.isKnownHarmless(a))
        assertFalse(ComposeCrashNet.isKnownHarmless(null))
    }

    @Test
    fun `그물이 폭주하면 더는 안 삼키고 터뜨린다`() {
        val t = ComposeCrashNet.Throttle(max = 30, windowMs = 10_000L)
        repeat(30) { assertTrue("${it + 1}번째는 삼켜야 한다", t.allow(1_000L)) }
        assertFalse("31번째부터는 터뜨려야 한다", t.allow(1_000L))
        assertFalse(t.allow(5_000L))          // 아직 같은 10초 창 안
        assertTrue("10초가 지나면 다시 그물이 일한다", t.allow(12_000L))
    }

    @Test
    fun `띄엄띄엄 나는 건 계속 삼킨다`() {
        val t = ComposeCrashNet.Throttle(max = 30, windowMs = 10_000L)
        var now = 0L
        repeat(200) {
            assertTrue(t.allow(now))
            now += 1_000L                      // 1초에 한 번 = 창 안에 30번을 못 채운다
        }
    }
}
