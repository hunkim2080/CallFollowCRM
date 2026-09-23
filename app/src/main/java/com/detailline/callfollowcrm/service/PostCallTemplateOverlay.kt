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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import com.detailline.callfollowcrm.presentation.theme.AppTheme
import com.detailline.callfollowcrm.presentation.theme.AppType
import com.detailline.callfollowcrm.presentation.theme.AppShape
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
    data class PickerState(val phone: String, val title: String, val templates: List<Tpl>, val preview: Boolean = false)

    /**
     * 큼직한 통화 후 템플릿 카드를 띄운다. 권한 없으면 false 반환(호출부가 알림 폴백).
     * @param preview true = 미리보기(설정에서 확인용). 탭해도 실제 발송 안 하고 안내만.
     */
    fun show(
        context: Context,
        phone: String,
        displayName: String?,
        items: List<Pair<String, List<String>>>,
        preview: Boolean = false
    ): Boolean {
        val appCtx = context.applicationContext
        if (!PermissionHelper.hasOverlay(appCtx)) return false
        if (items.isEmpty()) return false
        val who = displayName?.takeIf { it.isNotBlank() } ?: PhoneNumberFormatter.format(phone)
        _state.value = PickerState(phone, "${who}님께 문자 보낼까요?", items.take(3).map { Tpl(it.first, it.second) }, preview)
        main.post { if (currentView == null) actuallyShow(appCtx) }
        startSafety()
        return true
    }

    /** 템플릿 탭 = 바로 발송 (확인 없이). 사장님 2026-07-12: "복붙 말고 바로 전송". 사진 있으면 MMS, 없으면 SMS. */
    private fun onPick(index: Int, overrideText: String? = null) {
        val st = _state.value ?: return
        val tpl0 = st.templates.getOrNull(index) ?: return
        // 사장님이 창에서 고친 글이 있으면 **이번 발송에만** 쓴다. 저장된 템플릿은 안 건드린다.
        //   (한 손님한테 깎아준 값이 다음 손님한테도 나가면 안 된다. 2026-09-24 사장님)
        val tpl = overrideText?.trim()?.takeIf { it.isNotBlank() }
            ?.let { tpl0.copy(text = it) } ?: tpl0
        val ctx = currentView?.context?.applicationContext ?: return
        if (st.preview) {
            main.post {
                android.widget.Toast.makeText(ctx, "미리보기예요 — 실제 통화 땐 여기서 바로 발송돼요", android.widget.Toast.LENGTH_SHORT).show()
                actuallyHide()
            }
            return
        }
        val phone = st.phone
        ioScope.launch {
            val hadPhotos = tpl.photos.isNotEmpty()
            val mmsOk = if (hadPhotos) {
                val uris = tpl.photos.mapNotNull { runCatching { android.net.Uri.parse(it) }.getOrNull() }
                runCatching { com.detailline.callfollowcrm.util.SmsSender.sendMms(ctx, phone, tpl.text, uris) }.getOrDefault(false)
            } else false
            // 사진 없거나(SMS) 사진(MMS) 실패 시 → 글이라도 발송.
            val textOk = if ((!hadPhotos || !mmsOk) && tpl.text.isNotBlank())
                com.detailline.callfollowcrm.util.SmsSender.sendDirect(ctx, phone, tpl.text) else false
            // 정직한 결과 — 사진 실패를 '보냈어요 ✓'로 숨기지 않음. (2026-08-15 UX감사)
            val msg = when {
                mmsOk || (!hadPhotos && textOk) -> "문자를 보냈어요 ✓"
                hadPhotos && textOk -> "글은 보냈어요 · 사진은 못 갔어요 — 채팅에서 사진 다시 보내주세요"
                else -> "발송 실패 — 잠시 후 다시 시도하거나 채팅에서 보내주세요"
            }
            main.post {
                android.widget.Toast.makeText(ctx, msg, android.widget.Toast.LENGTH_LONG).show()
            }
        }
        main.post { actuallyHide() }
    }

    /** 창에서 고친 글로 보낸다. 저장된 템플릿은 안 바뀐다. (2026-09-24 사장님) */
    fun sendEdited(index: Int, text: String) = onPick(index, text)

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

    /** 고치는 동안은 안 닫는다 — 쓰던 글이 날아가면 안 된다. (2026-09-24 사장님) */
    fun pauseSafety() { safetyJob?.cancel(); safetyJob = null }
    fun resumeSafety() { startSafety() }

    private fun startSafety() {
        safetyJob?.cancel()
        safetyJob = ioScope.launch {
            delay(SAFETY_TIMEOUT_MS)
            main.post { actuallyHide() }
        }
    }
}

// ----- 카드 UI -----

// 색은 **디자인 시스템에서 가져온다.** 이 파일이 따로 들고 있던 값들을 옮긴 것이다. (2026-09-24 사장님)
//   파란색은 원래 같았다(#3182F6 = colors.primary). 나머지는 몇 단위 차이라 보기엔 그대로다.
//   LocalAppColors 는 기본값(LightColors)이 있어 오버레이(WindowManager)에서도 그냥 읽힌다.
private val PBlue @Composable get() = AppTheme.colors.primary
private val PInk @Composable get() = AppTheme.colors.text
private val PSub @Composable get() = AppTheme.colors.textSub
private val PTertiary @Composable get() = AppTheme.colors.textHint
private val PGrayBg @Composable get() = AppTheme.colors.neutralBg

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PostCallCard(
    state: PostCallTemplateOverlay.PickerState,
    timeoutMs: Long,
    onPick: (Int) -> Unit,
    onClose: () -> Unit
) {
    // 고치는 중인가 — 그 동안은 창이 안 닫힌다.
    var editing by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    var editIndex by remember { mutableStateOf(0) }

    // 자동 닫힘 카운트다운. **회색**이다 — 파란 막대는 "다 차면 보내진다"로 읽힌다. (2026-09-24 사장님)
    val progress = remember { androidx.compose.animation.core.Animatable(1f) }
    LaunchedEffect(editing) {
        if (editing) {
            progress.stop()                                   // 그 자리에 멈춘다
            PostCallTemplateOverlay.pauseSafety()
        } else {
            PostCallTemplateOverlay.resumeSafety()
            progress.animateTo(
                0f,
                animationSpec = androidx.compose.animation.core.tween(
                    durationMillis = (timeoutMs * progress.value).toInt().coerceAtLeast(1),
                    easing = androidx.compose.animation.core.LinearEasing
                )
            )
        }
    }
    Box(Modifier.fillMaxWidth().padding(horizontal = 14.dp)) {
        Column(
            Modifier
                .fillMaxWidth()
                .shadow(20.dp, AppShape.xl, clip = false)
                .clip(AppShape.xl)
                .background(AppTheme.colors.surface)
                .padding(20.dp)
        ) {
            // ── 시공막내 브랜딩 헤더 + 닫기 ──
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.clip(AppShape.pill).background(AppTheme.colors.primaryBg)
                        .padding(horizontal = 11.dp, vertical = 5.dp)
                ) { Text("시공막내", style = AppType.label, color = PBlue) }
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier.size(38.dp).clip(CircleShape).background(PGrayBg).clickable { onClose() },
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Filled.Close, "닫기", tint = PTertiary, modifier = Modifier.size(22.dp)) }
            }
            Spacer(Modifier.height(10.dp))
            // 카운트다운 바 (점점 줄어듦) — 회색. 자리·굵기는 그대로.
            Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(PGrayBg)) {
                Box(
                    Modifier.fillMaxWidth(progress.value).height(4.dp).clip(RoundedCornerShape(2.dp))
                        .background(AppTheme.colors.neutral.copy(alpha = if (editing) 0.28f else 0.55f))
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                if (editing) "고치는 동안은 안 닫혀요" else "그냥 두면 닫혀요",
                style = AppType.caption, color = PTertiary
            )
            Spacer(Modifier.height(14.dp))
            Text(
                "${state.title}", style = AppType.title,
                color = PInk, maxLines = 2, overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(14.dp))

            if (editing) {
                // ── 고치는 중 — 저장된 템플릿은 안 바뀐다 ──
                Column(
                    Modifier.fillMaxWidth().clip(AppShape.lg).background(PGrayBg).padding(16.dp)
                ) {
                    Text("${editIndex + 1} / ${state.templates.size} · 고치는 중",
                        style = AppType.label, color = PBlue)
                    Spacer(Modifier.height(7.dp))
                    androidx.compose.material3.OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 220.dp),
                        textStyle = AppType.headline.copy(fontWeight = FontWeight.Medium),
                        shape = AppShape.md,
                        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = AppTheme.colors.surface,
                            unfocusedContainerColor = AppTheme.colors.surface,
                            focusedBorderColor = PBlue,
                            unfocusedBorderColor = PBlue
                        )
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.clip(AppShape.md).clickable { editing = false }
                                .padding(horizontal = 14.dp, vertical = 14.dp)
                        ) { Text("되돌리기", style = AppType.headline, color = PSub) }
                        Spacer(Modifier.width(8.dp))
                        Box(
                            Modifier.weight(1f).clip(AppShape.md).background(PBlue)
                                .clickable { onPickEdited(onPick, editIndex, draft) }
                                .padding(vertical = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "이대로 보내기", color = AppTheme.colors.textOnPrimary,
                                style = AppType.headline.copy(fontWeight = FontWeight.ExtraBold)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "고친 건 이번 한 번만 써요 · 저장된 문자는 그대로예요",
                    style = AppType.caption, color = PTertiary,
                    modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            } else {
                // 옆으로 넘기는 템플릿 카드(페이저) — 한 장씩 깔끔하게. (사장님 2026-07-12)
                val pager = rememberPagerState(pageCount = { state.templates.size })
                HorizontalPager(
                    state = pager,
                    pageSpacing = 10.dp,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        end = if (state.templates.size > 1) 30.dp else 0.dp
                    )
                ) { page ->
                    TemplatePage(
                        page + 1, state.templates.size, state.templates[page],
                        onSend = { onPick(page) },
                        onEdit = { editIndex = page; draft = state.templates[page].text; editing = true }
                    )
                }

                if (state.templates.size > 1) {
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        repeat(state.templates.size) { i ->
                            Box(
                                Modifier.padding(horizontal = 3.dp)
                                    .size(if (i == pager.currentPage) 8.dp else 6.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (i == pager.currentPage) PBlue
                                        else AppTheme.colors.neutral.copy(alpha = 0.45f)
                                    )
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("시공막내가 도와드리는 서비스예요", style = AppType.caption, color = PTertiary,
                modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

/** 고쳐서 보내기 — 창의 onPick 은 index 만 받으므로 편집본은 오버레이에 직접 넘긴다. */
private fun onPickEdited(onPick: (Int) -> Unit, index: Int, text: String) {
    PostCallTemplateOverlay.sendEdited(index, text)
}

@Composable
private fun TemplatePage(
    index: Int,
    total: Int,
    tpl: PostCallTemplateOverlay.Tpl,
    onSend: () -> Unit,
    onEdit: () -> Unit
) {
    Column(
        Modifier.fillMaxWidth().clip(AppShape.lg).background(PGrayBg).padding(16.dp)
    ) {
        Text("$index / $total", style = AppType.label, color = PBlue)
        if (tpl.text.isNotBlank()) {
            Spacer(Modifier.height(7.dp))
            // **줄바꿈을 살리고 안 자른다** — 길면 이 칸 안에서 스크롤해 끝까지 읽는다. (2026-09-24 사장님
            //   "그냥 스크롤로 다 보여줄수있는거아닌가"). 전엔 replace("\n"," ") + 4줄 컷이었다.
            Text(
                tpl.text,
                color = PInk,
                style = AppType.headline.copy(fontWeight = FontWeight.Medium),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 147.dp)
                    .verticalScroll(rememberScrollState())
                    .clickable { onEdit() }
            )
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
                        modifier = Modifier.size(58.dp).clip(AppShape.sm).background(AppTheme.colors.surface)
                    )
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.clip(AppShape.md).clickable { onEdit() }
                    .padding(horizontal = 14.dp, vertical = 14.dp)
            ) { Text("고치기", style = AppType.headline, color = PSub) }
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.weight(1f).clip(AppShape.md).background(PBlue)
                    .clickable { onSend() }.padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "바로 보내기", color = AppTheme.colors.textOnPrimary,
                    style = AppType.headline.copy(fontWeight = FontWeight.ExtraBold)
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        // 오해가 나는 자리는 **버튼 옆**이라 여기서 못을 박는다. (2026-09-24 사장님)
        Text(
            "눌러야 나갑니다 · 그냥 두면 안 보내고 닫혀요",
            style = AppType.caption, color = PTertiary,
            modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
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
