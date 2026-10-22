package com.detailline.callfollowcrm.presentation.util

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.detailline.callfollowcrm.CallFollowCrmApplication
import com.detailline.callfollowcrm.service.NotificationHelper
import com.detailline.callfollowcrm.util.PhoneKey

/**
 * 🔔 이 대화를 **열어서 보는 동안**은 그 번호 수신 알림을 안 울리고(쌓인 것도 치움), 벗어나면 다시 알림 — 카톡·삼성문자식. (2026-10-22 사장님)
 *   "보고 있는데 푸시가 쌓이던 것" 수정. ChatScreen 이 한 줄로 부른다.
 *
 * - ON_RESUME(화면에 보임): '지금 보는 번호' 전역([CallFollowCrmApplication.openChatPhoneKey])에 등록 + 떠 있던 그 번호 알림 치움
 *     (기존 2026-10-05 "열면 알림 치움" 동작 흡수).
 * - ON_PAUSE·닫기: 내 번호면 비운다 → 백그라운드로 내리면 그 번호도 다시 알림(카톡과 동일).
 *
 * 실제 알림 억제는 [NotificationHelper.suppressedBecauseViewing] **한 곳**이 이 전역을 보고 판단한다(§12).
 * 매칭키는 수신 알림 id([NotificationHelper.smsNotificationId])와 같은 [PhoneKey.of].
 */
@Composable
fun OpenChatNotificationGate(phoneNumber: String) {
    val context: Context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, phoneNumber) {
        val key = PhoneKey.of(phoneNumber)
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    CallFollowCrmApplication.openChatPhoneKey = key
                    NotificationHelper.cancelIncomingSms(context, phoneNumber)
                }
                Lifecycle.Event.ON_PAUSE ->
                    if (CallFollowCrmApplication.openChatPhoneKey == key) CallFollowCrmApplication.openChatPhoneKey = null
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            if (CallFollowCrmApplication.openChatPhoneKey == key) CallFollowCrmApplication.openChatPhoneKey = null
        }
    }
}
