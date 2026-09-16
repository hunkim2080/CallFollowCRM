package com.detailline.callfollowcrm.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.google.android.gms.auth.api.identity.GetPhoneNumberHintIntentRequest
import com.google.android.gms.auth.api.identity.Identity

/**
 * 내 번호 자동 입력 — 구글 **전화번호 힌트**. (2026-09-16 사장님)
 *
 *   "당근은 처음 설치하고 들어가니까 핸드폰번호를 자기가 입력하지 않아도 바로 자동입력되네?
 *    우리 처음 로그인할 때 스스로 입력해야 하는데, 이런 방식으로 바로 입력하면 가입이 정말 편할 것 같네."
 *
 * 구글 플레이 서비스가 폰에 든 번호를 **한 번 탭**으로 넣어준다.
 *   · **권한이 필요 없다.** 앱은 번호를 못 훔쳐본다 — 사용자가 고른 것만 받는다.
 *     (READ_PHONE_NUMBERS 로 직접 읽는 방법도 있지만, 권한을 물어야 하고
 *      국내 통신사는 유심에 번호를 안 적어두는 일이 잦아 자주 빈 값이다.)
 *   · 안 되는 폰도 있다(플레이 서비스 없음·번호 미등록). 그땐 **조용히 넘어가고** 직접 입력하면 된다.
 *     자동이 실패했다고 화면에 에러를 띄우지 않는다 — 사용자는 뭘 잘못한 게 없다.
 */
object PhoneNumberHint {

    /** 번호 선택창을 띄운다. 못 띄우면 [onUnavailable] (보통은 아무것도 안 함). */
    fun request(
        activity: Activity,
        launcher: ActivityResultLauncher<IntentSenderRequest>,
        onUnavailable: () -> Unit = {}
    ) {
        runCatching {
            Identity.getSignInClient(activity)
                .getPhoneNumberHintIntent(GetPhoneNumberHintIntentRequest.builder().build())
                .addOnSuccessListener { launcher.launch(IntentSenderRequest.Builder(it).build()) }
                .addOnFailureListener { onUnavailable() }
        }.onFailure { onUnavailable() }
    }

    /** 선택 결과에서 번호를 꺼낸다. 취소했거나 실패면 null. */
    fun parse(context: Context, data: Intent?): String? {
        val raw = runCatching {
            Identity.getSignInClient(context).getPhoneNumberFromIntent(data ?: return null)
        }.getOrNull()
        return toLocalKorean(raw)
    }

    /**
     * 구글이 주는 국제 형식(+821064610131)을 우리가 쓰는 형식(01064610131)으로.
     *
     * 왜 따로 빼뒀나: 여기서 틀리면 **엉뚱한 번호로 가입**된다(앞자리가 통째로 달라진다).
     * 화면으로는 확인하기 번거로운 순수 계산이라 단위 테스트로 고정한다.
     *
     * @return 숫자만 남긴 국내 번호. 한국 번호로 안 보이면 null.
     */
    fun toLocalKorean(raw: String?): String? {
        val t = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        var d = t.filter { it.isDigit() }
        if (d.isEmpty()) return null
        when {
            // +82 10 xxxx xxxx → 0 10 xxxx xxxx
            d.startsWith("82") && (t.startsWith("+") || d.length >= 11) && !d.startsWith("820") -> d = "0" + d.removePrefix("82")
            // +82 0 10 ... (통신사에 따라 0 을 안 뗀 채로 오기도 한다)
            d.startsWith("820") -> d = d.removePrefix("82")
        }
        // 국내 휴대폰은 010/011/016/017/018/019 로 시작하는 10~11자리.
        if (!d.startsWith("0") || d.length !in 10..11) return null
        return d
    }
}
