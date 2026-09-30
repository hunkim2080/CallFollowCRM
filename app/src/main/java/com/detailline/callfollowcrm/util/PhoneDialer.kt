package com.detailline.callfollowcrm.util

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * ☎️ **전화를 거는 자리 — 한 곳.** (2026-09-30 사장님)
 *
 *   "전화를 걸어도 이게 똑같이 떴으면 좋겠어. **통화카드** 말야.
 *    전화 올 때 신규인지 뭔지 알 수 있는 카드. 내가 전화하면서도 메모나 일정 같은 걸 볼 수 있잖아."
 *
 * 왜 한 곳으로 모으나 — 전엔 **열 군데**가 각자 다이얼러를 열었다(홈 3곳 · 챗 · 고객정보 ·
 * 일정 · 수첩 · 협업 2곳 · 박람회). 거는 번호를 기억해 두는 일을 거기 열 번 적으면
 * **한 곳을 빼먹는 순간** 그 화면에서만 카드가 안 뜬다. 그런 건 아무도 못 찾는다.
 *
 * 🔎 **거는 번호를 왜 기억해 두나**
 *   받는 전화는 「벨이 울린다」는 신호에 번호가 실려 온다. 그런데 **거는 전화엔 벨이 없다** —
 *   안드로이드는 「통화 중」이라고만 알려주고 **번호는 안 준다**(Android 10 부터 막혔다).
 *   그래서 **앱에서 다이얼러를 열 때** 그 번호를 적어두고, 통화가 시작되면 그걸 쓴다.
 *
 * ⚠️ 다이얼러를 열어놓고 **안 걸 수도 있다.** 그래서 [TTL_MS] 가 지나면 잊는다 —
 *    안 그러면 한참 뒤 딴 사람에게 건 전화에 **엉뚱한 손님 카드**가 뜬다.
 */
object PhoneDialer {

    /** 다이얼러를 열고 이 시간 안에 통화가 시작되면 「그 번호로 건 것」으로 본다. */
    private const val TTL_MS = 3 * 60 * 1000L

    @Volatile private var pendingNumber: String? = null
    @Volatile private var pendingAtMs: Long = 0L

    /**
     * 다이얼러를 연다 — **번호가 채워진 채로.** 저절로 걸리지는 않는다(사장님이 통화를 누른다).
     * 권한이 필요 없는 길이라 그대로 둔다.
     */
    fun open(context: Context, phone: String?) {
        val digits = phone?.trim().orEmpty()
        if (digits.isBlank()) return
        remember(digits)
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(digits)))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    /** 다이얼러를 우리가 안 열고 번호만 아는 경우(예: 통화 화면에서 바로)에도 기억시킬 수 있게. */
    fun remember(phone: String?) {
        val digits = phone?.trim().orEmpty()
        if (digits.isBlank()) return
        pendingNumber = digits
        pendingAtMs = System.currentTimeMillis()
    }

    /**
     * 방금 건 번호를 **꺼내 쓰고 지운다.** 없거나 오래됐으면 null.
     *   한 번만 쓰는 이유 — 한 통화에 한 번이면 충분하고, 남겨두면 다음 통화에 새어 나간다.
     */
    fun take(): String? {
        val n = pendingNumber ?: return null
        pendingNumber = null
        if (System.currentTimeMillis() - pendingAtMs > TTL_MS) return null
        return n
    }

    /** 통화가 끝났을 때 — 안 쓴 기억은 버린다. */
    fun clear() {
        pendingNumber = null
        pendingAtMs = 0L
    }
}
