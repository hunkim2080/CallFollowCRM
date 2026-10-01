package com.detailline.callfollowcrm.presentation.util

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * 💾 **사장님이 적은 것을 저장하는 자리 — 실패하면 반드시 말한다.** (2026-10-02 사장님 지시)
 *
 * ## 왜 생겼나
 * 같은 「메모 저장」인데 길이 **두 벌**이었다 —
 *   · 고객 메모: 저장이 터지면 **앱이 꺼진다**(runCatching 없이 바로 호출)
 *   · 현장 메모: 저장이 터지면 **아무 일도 안 일어난다**(`runCatching { … }` 로 덮여 있었다)
 * 둘 다 틀렸다. 앞은 과하고 뒤는 **사장님이 적었다고 믿고 나간다** —
 * 현관 비번·주차 메모가 그렇게 사라지면 다음 날 현장 앞에서 못 들어간다.
 *
 * ## 규칙
 *   · 조용히 넘겨도 되는 건 **지워도 되는 것**(임시파일·캐시·알림)뿐이다.
 *   · **사장님이 손으로 적거나 눌러서 바뀌는 값**(메모·돈·일정·주소·사진)은 여기를 쓴다.
 *   · 실패하면 ① 로그에 남기고 ② **화면에 말한다**. 앱은 안 꺼뜨린다.
 *
 * ⚠️ [CancellationException] 은 **그대로 다시 던진다** — 화면을 닫아서 생긴 취소까지
 *    「저장 실패」라고 하면 멀쩡한데 틀린 말을 하게 된다.
 */
object SaveGuard {

    /**
     * @param what 사장님 말로 된 이름. 「현장 메모」 「잔금」 「시공일」 처럼.
     * @param tell 화면에 띄울 통로(보통 ViewModel 의 `_toast`).
     * @return 성공하면 결과, 실패하면 null.
     */
    suspend fun <T> run(
        what: String,
        tell: MutableStateFlow<String?>,
        block: suspend () -> T
    ): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        android.util.Log.e("SaveGuard", "$what 저장 실패", e)
        // 「무엇이」 안 됐는지 말한다 — 「오류가 발생했습니다」는 사장님이 할 수 있는 게 없다.
        tell.value = "$what 저장을 못 했어요 — 다시 한 번 눌러주세요"
        null
    }
}
