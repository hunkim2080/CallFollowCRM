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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
    const val SAFETY_TIMEOUT_MS = 45_000L   // 자동 닫힘 — 카드 상단 카운트다운 바와 동기. (2026-07-12 사장님)

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

    /** 템플릿 탭 = 바로 발송 (확인 없이). 사장님 2026-07-12: "복붙 말고 바로 전송". 사진 있으면 MMS, 없으면 SMS. */
    private fun onPick(index: Int) {
        val st = _state.value ?: return
        val tpl = st.templates.getOrNull(index) ?: return
        val ctx = currentView?.context?.applicationContext ?: return
        val phone = st.phone
        ioScope.launch {
            val ok = if (tpl.photos.isEmpty()) {
                com.detailline.callfollowcrm.util.SmsSender.sendDirect(ctx, phone, tpl.text)
            } else {
                val uris = tpl.photos.mapNotNull { runCatching { android.net.Uri.parse(it) }.getOrNull() }
                val mmsOk = runCatching { com.detailline.callfollowcrm.util.SmsSender.sendMms(ctx, phone, tpl.text, uris) }.getOrDefault(false)
                // 사진(MMS) 못 보냈고(기본 문자앱 아님 등) 글이라도 있으면 글만이라도 보냄.
                if (!mmsOk && tpl.text.isNotBlank()) com.detailline.callfollowcrm.util.SmsSender.sendDirect(ctx, phone, tpl.text) else mmsOk
            }
            main.post {
                android.widget.Toast.makeText(
                    ctx,
                    if (ok) "문자를 보냈어요 ✓" else "발송 실패 — 잠시 후 다시 시도하거나 채팅에서 보내주세요",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
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
                st?.let { PostCallCard(it, timeoutMs = SAFETY_TIMEOUT_MS, onPick = ::onPick, onClose = ::onClose) }
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PostCallCard(
    state: PostCallTemplateOverlay.PickerState,
    timeoutMs: Long,
    onPick: (Int) -> Unit,
    onClose: () -> Unit
) {
    // 자동 닫힘 카운트다운 — 가로 바가 1f→0f 로 줄어듦(사장님 2026-07-12). 안전타임아웃과 동기.
    val progress = remember { androidx.compose.animation.core.Animatable(1f) }
    LaunchedEffect(Unit) {
        progress.animateTo(
            0f,
            animationSpec = androidx.compose.animation.core.tween(
                durationMillis = timeoutMs.toInt(),
                easing = androidx.compose.animation.core.LinearEasing
            )
        )
    }
    Box(Modifier.fillMaxWidth().padding(horizontal = 14.dp)) {
        Column(
            Modifier
                .fillMaxWidth()
                .shadow(20.dp, RoundedCornerShape(26.dp), clip = false)
                .clip(RoundedCornerShape(26.dp))
                .background(Color.White)
                .padding(20.dp)
        ) {
            // ── 시공막내 브랜딩 헤더 + 닫기 ──
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.clip(RoundedCornerShape(999.dp)).background(Color(0xFFE8F1FE))
                        .padding(horizontal = 11.dp, vertical = 5.dp)
                ) { Text("✨ 시공막내", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = PBlue) }
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier.size(38.dp).clip(CircleShape).background(PGrayBg).clickable { onClose() },
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Filled.Close, "닫기", tint = PTertiary, modifier = Modifier.size(22.dp)) }
            }
            Spacer(Modifier.height(10.dp))
            // 카운트다운 바 (점점 줄어듦)
            Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFFEDF0F3))) {
                Box(Modifier.fillMaxWidth(progress.value).height(4.dp).clip(RoundedCornerShape(2.dp)).background(PBlue))
            }
            Spacer(Modifier.height(14.dp))
            Text(
                "📞 ${state.title}", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold,
                color = PInk, maxLines = 2, overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(14.dp))

            // 옆으로 넘기는 템플릿 카드(페이저) — 한 장씩 깔끔하게. (사장님 2026-07-12)
            val pager = rememberPagerState(pageCount = { state.templates.size })
            HorizontalPager(
                state = pager,
                pageSpacing = 10.dp,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    end = if (state.templates.size > 1) 30.dp else 0.dp
                )
            ) { page ->
                TemplatePage(page + 1, state.templates.size, state.templates[page], onSend = { onPick(page) })
            }

            if (state.templates.size > 1) {
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    repeat(state.templates.size) { i ->
                        Box(
                            Modifier.padding(horizontal = 3.dp)
                                .size(if (i == pager.currentPage) 8.dp else 6.dp)
                                .clip(CircleShape)
                                .background(if (i == pager.currentPage) PBlue else Color(0xFFD1D6DB))
                        )
                    }
                }
                Spacer(Modifier.height(5.dp))
                Text("← 옆으로 넘겨 다른 문자 보기", fontSize = 11.5.sp, color = PTertiary,
                    modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
            Spacer(Modifier.height(12.dp))
            Text("시공막내가 도와드리는 서비스예요", fontSize = 11.5.sp, color = PTertiary,
                modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

@Composable
private fun TemplatePage(index: Int, total: Int, tpl: PostCallTemplateOverlay.Tpl, onSend: () -> Unit) {
    val preview = tpl.text.replace("\n", " ").trim()
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(PGrayBg).padding(16.dp)
    ) {
        Text("$index / $total", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = PBlue)
        if (preview.isNotBlank()) {
            Spacer(Modifier.height(7.dp))
            Text(preview, fontSize = 15.sp, color = PInk, maxLines = 4, overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Medium, lineHeight = 21.sp)
        }
        if (tpl.photos.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                tpl.photos.forEach { uri ->
                    coil.compose.AsyncImage(
                        model = android.net.Uri.parse(uri),
                        contentDescription = "보낼 사진",
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier.size(58.dp).clip(RoundedCornerShape(10.dp)).background(Color.White)
                    )
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(PBlue)
                .clickable { onSend() }.padding(vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("👆 이 문자 바로 보내기", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
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
