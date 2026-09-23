package com.detailline.callfollowcrm.util

/**
 * 메인 스레드 안전망의 **판단 부분**만 떼어낸 것 — 폰 없이도 시험할 수 있게. (2026-09-24)
 *
 * 왜 떼어냈나: 사장님 업무폰엔 우리가 만든 앱을 직접 깔 수 없다(플레이로만 들어간다).
 * 그물이 제대로 짜였는지 **폰에서 확인할 방법이 없으므로**, 판단 로직을 여기로 옮겨
 * 실제 크래시 스택을 그대로 재현한 시험(ComposeCrashNetTest)으로 증명한다.
 *
 * 그물이 삼키는 건 **Compose 프레임워크가 스스로 터뜨리는, 우리 코드와 무관한** 3종뿐이다.
 * 나머지는 전부 그대로 터뜨려 크래시로 남긴다 — 숨기면 못 고친다.
 */
object ComposeCrashNet {

    /** 스택에서 '우리 앱 코드'로 셀 클래스 접두사. */
    private const val APP = "com.detailline.callfollowcrm"

    /**
     * 그물 자신은 항상 스택 맨 아래에 찍힌다(Looper.loop 를 감싸고 있으므로).
     * 그러니 '우리 코드가 끼어 있나' 를 셀 때 이건 빼야 한다. 안 빼면 ③번 그물코가 **절대 안 걸린다.**
     */
    private const val GUARD = "$APP.CallFollowCrmApplication"

    /**
     * 삼켜도 되는 예외인가 — 아래 3종만 true.
     *
     *  ① ACTION_HOVER_EXIT 버그 — 마우스/미러링/DeX 의 hover 이벤트를 Compose 가 예약 실행한 뒤
     *     화면이 바뀌면 터지는 프레임워크 버그. 손가락으로 쓰는 사장님·고객은 hover 자체가 없어 안 걸린다.
     *
     *  ② LazyLayout '미리 그리기'(prefetch) 레이스 — 화면 밖 항목을 미리 그리다 막 사라진 컴포지션을 건드림.
     *     삼켜도 무해하다. 실제로 그 항목까지 스크롤하면 그때 제대로 그려진다.
     *
     *  ③ 화면 전환 중 목록을 재다가 나는 같은 레이스 (2026-09-24 사장님 업무폰에서 실제로 앱이 꺼진 건).
     *     ②와 뿌리는 같은데(Compose 1.6.x SlotTable) 들어온 문이 다르다 —
     *     미리 그리기가 아니라 NavHost 화면 전환 → Scaffold → LazyColumn → subcompose 로 들어와서
     *     ② 그물코를 그냥 빠져나갔다. 그래서 앱이 그대로 꺼졌다.
     *
     * ③의 안전장치: **스택에 우리 앱 코드가 한 줄이라도 있으면 삼키지 않는다.**
     * 우리 코드가 끼어 있으면 우리 잘못일 수 있고, 우리 잘못은 숨기면 안 된다.
     */
    fun isKnownHarmless(t: Throwable?): Boolean {
        var e: Throwable? = t
        var hops = 0
        while (e != null && hops++ < 20) {                       // cause 가 자기 자신을 가리키는 경우 방어
            if (e is IllegalStateException &&
                e.message?.contains("HOVER_EXIT", ignoreCase = true) == true
            ) return true

            if (e.stackTrace.any {
                    it.className.contains("AndroidComposeView") &&
                        it.methodName.contains("HoverExit", ignoreCase = true)
                }
            ) return true

            if (e is IndexOutOfBoundsException &&
                e.stackTrace.any { it.className.contains("LazyLayoutPrefetcher") }
            ) return true

            if (e is IndexOutOfBoundsException &&
                e.stackTrace.any { it.className.startsWith("androidx.compose.runtime.SlotTable") } &&
                e.stackTrace.none { it.className.startsWith(APP) && !it.className.startsWith(GUARD) }
            ) return true

            val next = e.cause
            e = if (next === e) null else next
        }
        return false
    }

    /**
     * 그물이 폭주하지 않게 하는 한도 — 기본 **10초에 30번까지.**
     *
     * 그물은 '한 프레임 삐끗'을 받아내라고 있는 것이지, 망가진 상태를 계속 받아내라고 있는 게 아니다.
     * 계속 터지는데 계속 삼키면 앱은 안 꺼지지만 **화면이 멈춘 채로 남는다.**
     * 꺼지는 것보다 멈춘 채 살아있는 게 더 나쁘다 — 원인이 안 보이고 신고도 안 올라간다.
     * 한도를 넘으면 [allow] 가 false 를 주고, 부르는 쪽은 그대로 터뜨린다.
     */
    class Throttle(private val max: Int = 30, private val windowMs: Long = 10_000L) {
        private val times = ArrayDeque<Long>()

        fun allow(nowMs: Long): Boolean {
            while (times.isNotEmpty() && nowMs - times.first() > windowMs) times.removeFirst()
            if (times.size >= max) return false
            times.addLast(nowMs)
            return true
        }
    }
}
