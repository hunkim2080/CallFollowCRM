package com.detailline.callfollowcrm.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 📡 「인터넷이 돌아왔다」를 듣는 **귀** — 공용. (설계 §1-D-5: 지금 앱에 ConnectivityManager 가 0곳)
 *
 *   우체통 drain 이 이걸로 복구 직후 깨어난다. 미러·웹피드·캘린더 푸시도 같은 자리에서 찌를 수 있다
 *   (§1-B — 큐에 안 넣고 신호만 공유). **새 OkHttp·새 워커를 만들지 않는다**(§10).
 */
class NetworkWatch(context: Context) {

    private val cm = context.applicationContext
        .getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    @Volatile private var registered = false
    @Volatile private var wasValidated = false   // false→true 로 바뀔 때만 깨운다(도배 방지)

    /** 지금 인터넷이 되나. 모르면 **된다고** 본다(지금 동작과 같게 — 괜히 막지 않는다). */
    fun isOnline(): Boolean {
        val c = cm ?: return true
        val net = c.activeNetwork ?: return false
        val caps = c.getNetworkCapabilities(net) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    /** 복구되면 부를 것 등록(중복 안전). 첫 등록 때 콜백을 건다. */
    fun onAvailable(block: () -> Unit) {
        listeners.addIfAbsent(block)
        ensureRegistered()
    }

    @Synchronized
    private fun ensureRegistered() {
        if (registered) return
        val c = cm ?: return
        runCatching {
            c.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                // ⚠️ onAvailable 은 **검증(VALIDATED) 전**에 온다 — 그때 isOnline() 은 아직 false 라 깨워도 헛발.
                //   진짜 인터넷이 되는 순간(VALIDATED)은 onCapabilitiesChanged 로 온다. 거기서 깨운다. (Fable #6)
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                    val ok = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                    if (ok && !wasValidated) {
                        wasValidated = true
                        listeners.forEach { runCatching { it() } }
                    } else if (!ok) {
                        wasValidated = false
                    }
                }

                override fun onLost(network: Network) { wasValidated = false }
            })
            registered = true
        }
    }
}
