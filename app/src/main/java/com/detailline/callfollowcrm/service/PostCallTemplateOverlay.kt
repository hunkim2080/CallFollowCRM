package com.detailline.callfollowcrm.service

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.detailline.callfollowcrm.MainActivity
import com.detailline.callfollowcrm.util.PermissionHelper
import com.detailline.callfollowcrm.util.PhoneNumberFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 통화 후 "문자 보낼까요?" 를 화면 위에 큼직한 오버레이 카드로 띄운다. (2026-07-12 사장님)
 *   알림(작음) 대신 앱 위에 크게 → 통화 끝나고 바로 템플릿 골라 보내기 쉽게.
 *   "다른 앱 위에 표시"(SYSTEM_ALERT_WINDOW) 권한 필요. 없으면 호출부(CallStateReceiver)가 알림으로 폴백.
 *   WindowManager + ComposeView + 커스텀 LifecycleOwner = IncomingCallOverlay 와 동일 패턴.
 */
object PostCallTemplateOverlay {

    private const val TAG = "PostCallOverlay"
    private const val SAFETY_TIMEOUT_MS = 90_000L

    private val main = Handler(Looper.getMainLooper())
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var currentView: View? = null
    private var currentOwner: OverlayLifecycleOwner2? = null
    private var safetyJob: Job? = null

    private val _state = MutableStateFlow<PickerState?>(null)
    val state = _state.asStateFlow()

    data class Tpl(val text: String, val photos: List<String>)
    data class PickerState(val phone: String, val title: String, val templates: List<Tpl>)

    /** 큼직한 통화 후 템플릿 카드를 띄운다. 권한 없으면 false 반환(호출부가 알림 폴백). */
    fun show(context: Context, phone: String, displayName: String?, items: List<Pair<String, List<String>>>): Boolean {
        val appCtx = context.applicationContext
        if (!PermissionHelper.hasOverlay(appCtx)) return false
        if (items.isEmpty()) return false
        val who = displayName?.takeIf { it.isNotBlank() } ?: PhoneNumberFormatter.format(phone)
        _state.value = PickerState(phone, "${who}님께 문자 보낼까요?", items.take(3).map { Tpl(it.first, it.second) })
        main.post { if (currentView == null) actuallyShow(appCtx) }
        startSafety()
        return true
    }

    private fun onPick(index: Int) {
        val st = _state.value ?: return
        val tpl = st.templates.getOrNull(index) ?: return
        val ctx = currentView?.context?.applicationContext ?: return
        runCatching {
            val intent = Intent(ctx, MainActivity::class.java).apply {
                action = MainActivity.ACTION_CHAT
                putExtra(MainActivity.EXTRA_PHONE_NUMBER, st.phone)
                if (tpl.text.isNotBlank()) putExtra(MainActivity.EXTRA_PREFILL_BODY, tpl.text)
                if (tpl.photos.isNotEmpty()) putExtra(MainActivity.EXTRA_PREFILL_PHOTO, tpl.photos.joinToString("\n"))
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            ctx.startActivity(intent)
        }
        main.post { actuallyHide() }
    }

    private fun onClose() { main.post { actuallyHide() } }

    @SuppressLint("InflateParams")
    private fun actuallyShow(appContext: Context) {
        val wm = appContext.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        val owner = OverlayLifecycleOwner2().also { it.onCreate(); it.onStart(); it.onResume() }
        val composeView = ComposeView(appContext).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setContent {
                val st by state.collectAsState()
                st?.let { PostCallCard(it, onPick = ::onPick, onClose = ::onClose) }
            }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            // 버튼 탭은 받되(포커스는 카드 밖으로 안 뺏게 NOT_TOUCH_MODAL) 카드 밖 터치는 통과.
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }
        runCatching { wm.addView(composeView, params) }
            .onSuccess { currentView = composeView; currentOwner = owner; android.util.Log.d(TAG, "show OK") }
            .onFailure { android.util.Log.w(TAG, "addView FAILED", it); _state.value = null }
    }

    private fun actuallyHide() {
        safetyJob?.cancel(); safetyJob = null
        currentView?.let { v ->
            runCatching {
                val wm = v.context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
                wm?.removeView(v)
            }
        }
        currentOwner?.onDestroy()
        currentOwner = null
        currentView = null
        _state.value = null
    }

    private fun startSafety() {
        safetyJob?.cancel()
        safetyJob = ioScope.launch {
            delay(SAFETY_TIMEOUT_MS)
            main.post { actuallyHide() }
        }
    }
}

// ----- 카드 UI -----

private val PBlue = Color(0xFF3182F6)
private val PInk = Color(0xFF191F28)
private val PSub = Color(0xFF4E5968)
private val PTertiary = Color(0xFF8B95A1)
private val PGrayBg = Color(0xFFF2F4F6)

@Composable
private fun PostCallCard(
    state: PostCallTemplateOverlay.PickerState,
    onPick: (Int) -> Unit,
    onClose: () -> Unit
) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 14.dp)) {
        Column(
            Modifier
                .fillMaxWidth()
                .shadow(20.dp, RoundedCornerShape(26.dp), clip = false)
                .clip(RoundedCornerShape(26.dp))
                .background(Color.White)
                .padding(22.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("📞", fontSize = 26.sp)
                Spacer(Modifier.size(12.dp))
                Text(
                    state.title, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold,
                    color = PInk, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    Modifier.size(38.dp).clip(CircleShape).background(PGrayBg).clickable { onClose() },
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Filled.Close, "닫기", tint = PTertiary, modifier = Modifier.size(22.dp)) }
            }
            Spacer(Modifier.height(6.dp))
            Text("보낼 문자를 고르면 확인 후 보낼 수 있어요", fontSize = 14.sp, color = PTertiary)
            Spacer(Modifier.height(16.dp))

            state.templates.forEachIndexed { i, tpl ->
                if (i > 0) Spacer(Modifier.height(10.dp))
                val preview = tpl.text.replace("\n", " ").trim()
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                        .background(PGrayBg).clickable { onPick(i) }.padding(16.dp)
                ) {
                    Text(
                        "${i + 1}번 문자", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = PBlue
                    )
                    if (preview.isNotBlank()) {
                        Spacer(Modifier.height(5.dp))
                        Text(preview, fontSize = 16.sp, color = PInk, maxLines = 3, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                    }
                    if (tpl.photos.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text("📷 사진 ${tpl.photos.size}장 첨부", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = PSub)
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

// ----- WindowManager 안 ComposeView 용 커스텀 LifecycleOwner (IncomingCallOverlay 와 동일 패턴) -----

private class OverlayLifecycleOwner2 :
    LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    fun onCreate() {
        savedStateController.performAttach()
        savedStateController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    fun onStart() { lifecycleRegistry.currentState = Lifecycle.State.STARTED }
    fun onResume() { lifecycleRegistry.currentState = Lifecycle.State.RESUMED }
    fun onDestroy() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        store.clear()
    }
}
