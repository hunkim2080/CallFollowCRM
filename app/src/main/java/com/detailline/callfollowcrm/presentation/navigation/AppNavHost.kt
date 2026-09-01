package com.detailline.callfollowcrm.presentation.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.detailline.callfollowcrm.data.AppContainer
import com.detailline.callfollowcrm.presentation.screen.chat.ChatScreen
import com.detailline.callfollowcrm.presentation.screen.chat.ChatViewModel
import com.detailline.callfollowcrm.presentation.screen.aimessage.AiMessageScreen
import com.detailline.callfollowcrm.presentation.screen.aimessage.AiMessageViewModel
import com.detailline.callfollowcrm.presentation.screen.customer.CustomerDetailScreen
import com.detailline.callfollowcrm.presentation.screen.customer.CustomerDetailViewModel
import com.detailline.callfollowcrm.presentation.screen.followup.FollowUpScreen
import com.detailline.callfollowcrm.presentation.screen.followup.FollowUpViewModel
import com.detailline.callfollowcrm.presentation.screen.home.HomeScreen
import com.detailline.callfollowcrm.presentation.screen.home.HomeViewModel
import com.detailline.callfollowcrm.presentation.screen.onboarding.OnboardingPermissionScreen
import com.detailline.callfollowcrm.presentation.screen.pricing.PricingItemsScreen
import com.detailline.callfollowcrm.presentation.screen.pricing.PricingItemsViewModel
import com.detailline.callfollowcrm.presentation.screen.pricing.PricingExtractScreen
import com.detailline.callfollowcrm.presentation.screen.pricing.PricingExtractViewModel
import com.detailline.callfollowcrm.presentation.screen.schedule.ScheduleScreen
import com.detailline.callfollowcrm.presentation.screen.schedule.ScheduleViewModel
import com.detailline.callfollowcrm.presentation.screen.schedule.ScheduleAddScreen
import com.detailline.callfollowcrm.presentation.screen.schedule.ScheduleAddViewModel
import com.detailline.callfollowcrm.presentation.screen.settings.SettingsScreen
import com.detailline.callfollowcrm.presentation.screen.settings.SettingsViewModel
import com.detailline.callfollowcrm.presentation.screen.settlement.SettlementScreen
import com.detailline.callfollowcrm.presentation.screen.settlement.SettlementViewModel
import com.detailline.callfollowcrm.presentation.screen.business.BusinessInfoScreen
import com.detailline.callfollowcrm.presentation.screen.callsummary.CallSummaryScreen
import com.detailline.callfollowcrm.presentation.screen.notebook.NotebookScreen
import com.detailline.callfollowcrm.presentation.screen.notebook.NotebookViewModel
import com.detailline.callfollowcrm.presentation.screen.report.ReportScreen
import com.detailline.callfollowcrm.presentation.screen.report.ReportViewModel
import com.detailline.callfollowcrm.presentation.screen.stats.StatsScreen
import com.detailline.callfollowcrm.presentation.screen.trade.TradeSelectScreen
import com.detailline.callfollowcrm.presentation.screen.stylelearning.StyleLearningScreen
import com.detailline.callfollowcrm.presentation.screen.stylelearning.StyleLearningViewModel
import com.detailline.callfollowcrm.presentation.screen.principle.PrincipleManageScreen
import com.detailline.callfollowcrm.presentation.screen.principle.PrincipleManageViewModel
import com.detailline.callfollowcrm.presentation.screen.template.TemplateEditScreen
import com.detailline.callfollowcrm.presentation.screen.template.TemplateEditViewModel
import com.detailline.callfollowcrm.presentation.screen.template.TemplateListScreen
import com.detailline.callfollowcrm.presentation.screen.template.TemplateDiscoverScreen
import com.detailline.callfollowcrm.presentation.screen.template.TemplateDiscoverViewModel
import com.detailline.callfollowcrm.presentation.screen.template.TemplateListViewModel
import com.detailline.callfollowcrm.presentation.util.viewModelFactory

@Composable
fun AppNavHost(
    navController: NavHostController,
    container: AppContainer,
    startDestination: String,
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier
) {
    // 토스 스타일 전환 (2026-06-03 사장님 통점 "그림자 생기듯 부자연스럽게 넘어감"):
    //   기본 NavHost 전환 = 페이드+스케일(스크림 그림자처럼 보임) → 수평 슬라이드 push 로 교체.
    //   상세 화면: 오른쪽에서 밀려 들어오고(enter), 뒤로가기 시 오른쪽으로 빠짐(popExit). 아래 화면은 살짝 패럴랙스.
    //   하단 탭 5개(홈/일정/정산/통계/더보기) 간 전환은 슬라이드 대신 페이드(좌우로 미는 게 어색하므로).
    val tabRoutes = setOf(
        Destinations.HOME,
        Destinations.SCHEDULE,
        Destinations.SETTLEMENT,
        Destinations.STATS,
        Destinations.SETTINGS
    )
    val slideDur = 280
    val fadeDur = 180
    fun routeBase(route: String?): String? = route?.substringBefore("?")
    fun isTabSwitch(from: String?, to: String?): Boolean = routeBase(from) in tabRoutes && routeBase(to) in tabRoutes

    // 사용자 여정 — 화면 진입(목적지 변경)마다 screen_view 적재. 30초 배치로 전송. (2026-06-23 사장님)
    androidx.compose.runtime.LaunchedEffect(navController) {
        navController.currentBackStackEntryFlow.collect { entry ->
            container.journeyEventRepository.track("screen_view", screen = entry.destination.route)
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
        enterTransition = {
            if (isTabSwitch(initialState.destination.route, targetState.destination.route)) {
                fadeIn(tween(fadeDur))
            } else {
                slideInHorizontally(tween(slideDur)) { full -> full } + fadeIn(tween(slideDur))
            }
        },
        exitTransition = {
            if (isTabSwitch(initialState.destination.route, targetState.destination.route)) {
                fadeOut(tween(fadeDur))
            } else {
                // 아래로 깔리는 화면은 살짝 왼쪽으로 (패럴랙스) + 페이드아웃
                slideOutHorizontally(tween(slideDur)) { full -> -full / 4 } + fadeOut(tween(slideDur))
            }
        },
        popEnterTransition = {
            if (isTabSwitch(initialState.destination.route, targetState.destination.route)) {
                fadeIn(tween(fadeDur))
            } else {
                slideInHorizontally(tween(slideDur)) { full -> -full / 4 } + fadeIn(tween(slideDur))
            }
        },
        popExitTransition = {
            if (isTabSwitch(initialState.destination.route, targetState.destination.route)) {
                fadeOut(tween(fadeDur))
            } else {
                slideOutHorizontally(tween(slideDur)) { full -> full } + fadeOut(tween(slideDur))
            }
        }
    ) {

        composable(Destinations.LOGIN) {
            val context = androidx.compose.ui.platform.LocalContext.current
            val goNext = {
                container.preferences.hasSeenLogin = true
                val next = when {
                    // 개인정보 동의 미완 → 동의 게이트 먼저(추가97). 로그인 직후 번호가 있어 POST 가능.
                    container.preferences.consentAgreedVersion != com.detailline.callfollowcrm.AppConfig.CONSENT_VERSION -> Destinations.CONSENT
                    !container.preferences.hasOnboarded -> Destinations.ONBOARDING
                    com.detailline.callfollowcrm.util.PermissionHelper
                        .allMissingNonNotification(context).isNotEmpty() -> Destinations.PERMISSIONS
                    else -> Destinations.HOME
                }
                navController.navigate(next) {
                    popUpTo(Destinations.LOGIN) { inclusive = true }
                }
            }
            com.detailline.callfollowcrm.presentation.screen.login.LoginScreen(
                onLoginPhone = { phone ->
                    // 테스터: 할당받은 본인 번호로 로그인 → bizPhone 저장(협업·팀 기능 키). (2026-06-14 사장님)
                    container.preferences.bizPhone = phone.trim()
                    val ph = phone.filter { it.isDigit() }
                    if (ph.length >= 9) runCatching {
                        com.google.firebase.messaging.FirebaseMessaging.getInstance().token
                            .addOnSuccessListener { token ->
                                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                                    runCatching { container.pushRegisterRepository.register(ph, token) }
                                }
                            }
                    }
                    goNext()
                },
                onProceed = goNext
            )
        }

        // 회원가입 (폰 인증번호) — Play 공개 첫 화면. enrolled→온보딩 / member→홈 / waitlisted→대기(화면 내부). (2026-07-04)
        composable(Destinations.SIGNUP) {
            val ctx = androidx.compose.ui.platform.LocalContext.current
            val vm: com.detailline.callfollowcrm.presentation.screen.signup.SignupViewModel =
                viewModel(factory = viewModelFactory { com.detailline.callfollowcrm.presentation.screen.signup.SignupViewModel(container) })
            // 가입 성공 = 인증된 번호 저장 + FCM 등록(로그인 흐름과 동일).
            fun completeSignup(phone: String) {
                val ph = phone.filter { it.isDigit() }
                container.preferences.bizPhone = ph
                if (ph.length >= 9) runCatching {
                    com.google.firebase.messaging.FirebaseMessaging.getInstance().token
                        .addOnSuccessListener { token ->
                            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                                runCatching { container.pushRegisterRepository.register(ph, token) }
                            }
                        }
                }
            }
            com.detailline.callfollowcrm.presentation.screen.signup.SignupScreen(
                viewModel = vm,
                onEnrolled = { phone, _, _ ->
                    completeSignup(phone)   // 신규 가입 → 동의 게이트 → 온보딩(스토리텔링)
                    navController.navigate(Destinations.CONSENT) {
                        popUpTo(Destinations.SIGNUP) { inclusive = true }
                    }
                },
                onMember = { phone, _ ->
                    completeSignup(phone)
                    container.preferences.hasOnboarded = true   // 기존 회원 → 온보딩 스킵
                    // 동의 미완이면 게이트 먼저, 아니면 권한/홈.
                    val next = when {
                        container.preferences.consentAgreedVersion != com.detailline.callfollowcrm.AppConfig.CONSENT_VERSION -> Destinations.CONSENT
                        com.detailline.callfollowcrm.util.PermissionHelper.allMissingNonNotification(ctx).isNotEmpty() -> Destinations.PERMISSIONS
                        else -> Destinations.HOME
                    }
                    navController.navigate(next) {
                        popUpTo(Destinations.SIGNUP) { inclusive = true }
                    }
                }
            )
        }

        // 개인정보 동의 게이트 (진입 1회) — 필수 동의해야 통과. 동의 = POST /api/consent + prefs 저장 + 다음 화면. (추가97)
        composable(Destinations.CONSENT) {
            val ctx = androidx.compose.ui.platform.LocalContext.current
            val scope = androidx.compose.runtime.rememberCoroutineScope()
            com.detailline.callfollowcrm.presentation.screen.consent.ConsentScreen(
                optionalPreChecked = container.preferences.toneUploadConsented,
                onOpenLink = { url ->
                    // 외부 브라우저 대신 앱 내 웹뷰 — 크롬 없는 기기(심사 포함)에서도 항상 표시. (2026-07-11)
                    com.detailline.callfollowcrm.presentation.screen.web.DocWebViewActivity.open(
                        ctx, url, com.detailline.callfollowcrm.presentation.screen.web.DocWebViewActivity.titleFor(url)
                    )
                },
                onAgree = { optionalAgreed ->
                    container.preferences.consentAgreedVersion = com.detailline.callfollowcrm.AppConfig.CONSENT_VERSION
                    container.preferences.toneUploadConsented = optionalAgreed
                    val phone = container.preferences.bizPhone
                    scope.launch {
                        runCatching { container.authRepository.postConsent(phone, "required", true) }
                        runCatching { container.authRepository.postConsent(phone, "optional_quality", optionalAgreed) }
                    }
                    val next = when {
                        !container.preferences.hasOnboarded -> Destinations.ONBOARDING
                        com.detailline.callfollowcrm.util.PermissionHelper
                            .allMissingNonNotification(ctx).isNotEmpty() -> Destinations.PERMISSIONS
                        else -> Destinations.HOME
                    }
                    navController.navigate(next) { popUpTo(Destinations.CONSENT) { inclusive = true } }
                }
            )
        }

        // 스토리텔링 온보딩 (캐러셀 → 업종 → 상호·지역 → 막내 비서 탄생)
        composable(Destinations.ONBOARDING) {
            val context = androidx.compose.ui.platform.LocalContext.current
            com.detailline.callfollowcrm.presentation.screen.onboarding.OnboardingScreen(
                prefs = container.preferences,
                onFinish = {
                    val next = if (
                        com.detailline.callfollowcrm.util.PermissionHelper
                            .allMissingNonNotification(context).isNotEmpty()
                    ) Destinations.PERMISSIONS else Destinations.HOME
                    navController.navigate(next) {
                        popUpTo(Destinations.ONBOARDING) { inclusive = true }
                    }
                }
            )
        }

        // 권한 요청 (기존 화면) — 온보딩 다음 단계 → 연결 마법사(SETUP)로
        composable(Destinations.PERMISSIONS) {
            OnboardingPermissionScreen(
                onContinue = {
                    navController.navigate(Destinations.SETUP) {
                        popUpTo(Destinations.PERMISSIONS) { inclusive = true }
                    }
                }
            )
        }

        // 연결 마법사 (통화녹음 연결 등) — 신규 사용자 온보딩 마지막 단계. (2026-07-28)
        composable(Destinations.SETUP) {
            com.detailline.callfollowcrm.presentation.screen.onboarding.OnboardingSetupScreen(
                onFinish = {
                    navController.navigate(Destinations.HOME) {
                        popUpTo(Destinations.SETUP) { inclusive = true }
                    }
                },
                preferences = container.preferences
            )
        }

        composable(Destinations.HOME) {
            val vm: HomeViewModel = viewModel(factory = viewModelFactory { HomeViewModel(container) })
            // PC 웹 QR 빠른 로그인 (2026-08-31 사장님) — 홈 상단 [QR] → 스캔 → ticket 승인(웹뷰어 approve 와 동일).
            val homeCtx = LocalContext.current
            val homeScope = rememberCoroutineScope()
            val webQrScanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
                val contents = result.contents
                if (!contents.isNullOrBlank()) {
                    val ticket = runCatching { android.net.Uri.parse(contents).getQueryParameter("t") }
                        .getOrNull()?.takeIf { it.isNotBlank() }
                    if (ticket == null) {
                        android.widget.Toast.makeText(homeCtx, "시공막내 웹 로그인 QR이 아니에요", android.widget.Toast.LENGTH_SHORT).show()
                    } else {
                        val ownerPhone = container.preferences.bizPhone
                        if (ownerPhone.filter { it.isDigit() }.length < 9) {
                            android.widget.Toast.makeText(homeCtx, "먼저 내 번호(로그인)가 필요해요", android.widget.Toast.LENGTH_SHORT).show()
                        } else homeScope.launch {
                            val r = container.webFeedRepository.authorize(ticket, ownerPhone)
                            val msg = when (r) {
                                com.detailline.callfollowcrm.ai.WebFeedRepository.AuthResult.OK -> {
                                    container.preferences.webViewerActive = true
                                    runCatching { container.webFeedSyncManager.pushNow(force = true) }
                                    container.ownerPhotoUploadManager.kick(homeScope)
                                    "PC 웹에 로그인됐어요 ✅ 사진도 웹으로 올라가요"
                                }
                                com.detailline.callfollowcrm.ai.WebFeedRepository.AuthResult.EXPIRED ->
                                    "QR이 만료됐어요. 웹에서 새 QR을 띄워 다시 찍어주세요."
                                else -> "웹 로그인 실패 — 처음이면 더보기 → 시공막내 웹에서 인증을 한 번 해주세요."
                            }
                            android.widget.Toast.makeText(homeCtx, msg, android.widget.Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
            HomeScreen(
                viewModel = vm,
                // 카드 탭 [💬 메시지] 인라인 액션. customerId 없으면 ChatViewModel 이 phone lookup.
                onOpenChat = { phone, customerId ->
                    navController.navigate(Destinations.chat(phone, customerId))
                },
                // 카드 탭 [ⓘ 고객 카드] 인라인 액션. customerId 없는 카드(SMS-only 등)는 호출부에서 차단.
                onOpenCustomerDetail = { id -> navController.navigate(Destinations.customerDetail(id)) },
                // FAB "수동 입력" 은 기존 FollowUp 화면 유지 (번호 직접 입력 + 상태/메모 한 번에).
                onOpenManualEntry = { navController.navigate(Destinations.followUp()) },
                onOpenSchedule = { navController.navigate(Destinations.SCHEDULE) },
                onOpenScheduleAtDay = { dayMs -> navController.navigate(Destinations.schedule(dayMs)) },
                onAddSchedule = { navController.navigate(Destinations.SCHEDULE_ADD) },
                onOpenTemplates = { navController.navigate(Destinations.TEMPLATE_LIST) },
                onOpenAiMessage = { navController.navigate(Destinations.AI_MESSAGE) },
                onOpenStyleLearning = { navController.navigate(Destinations.STYLE_LEARNING) },
                onOpenSettings = { navController.navigate(Destinations.SETTINGS) },
                onOpenSearch = { navController.navigate(Destinations.SEARCH) },
                onOpenCustomers = { navController.navigate(Destinations.CUSTOMERS) },
                onOpenNewLeads = { navController.navigate(Destinations.NEW_LEADS) },
                onOpenSettlement = { navController.navigate(Destinations.SETTLEMENT) },
                onOpenRecurringDue = { navController.navigate(Destinations.RECURRING_DUE) },
                onOpenScheduleReminder = { navController.navigate(Destinations.SCHEDULE_REMINDER) },
                onOpenEstimateFollowup = { navController.navigate(Destinations.ESTIMATE_FOLLOWUP) },
                onOpenTeam = { navController.navigate(Destinations.TEAM) },
                onOpenCollabSites = { navController.navigate(Destinations.COLLAB_SITES) },
                onOpenCollabSiteDetail = { shareId -> navController.navigate(Destinations.collabSites(shareId)) },
                onOpenAutoSmsSettings = { navController.navigate(Destinations.SETTINGS_AUTOSMS) },
                // 막내 팁 카드 → 해당 기능 라우트로 이동(제네릭). (2026-07-04 사장님)
                onOpenRoute = { route -> navController.navigate(route) },
                // PC 웹 QR 빠른 로그인 — 홈 상단 [QR] 아이콘. (2026-08-31 사장님)
                onScanWebQr = {
                    webQrScanLauncher.launch(
                        ScanOptions()
                            .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                            .setPrompt("PC 화면의 QR을 비춰주세요")
                            .setBeepEnabled(false)
                    )
                },
                onOpenTradeSelect = { navController.navigate(Destinations.TRADE_SELECT) }
            )
        }

        composable(Destinations.AI_MESSAGE) {
            val vm: AiMessageViewModel = viewModel(factory = viewModelFactory { AiMessageViewModel(container) })
            AiMessageScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Destinations.STYLE_LEARNING) {
            val vm: StyleLearningViewModel = viewModel(
                factory = viewModelFactory { StyleLearningViewModel(container) }
            )
            StyleLearningScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() },
                onOpenPrinciples = { navController.navigate(Destinations.PRINCIPLES) }
            )
        }

        composable(Destinations.PRINCIPLES) {
            val vm: PrincipleManageViewModel = viewModel(
                factory = viewModelFactory { PrincipleManageViewModel(container) }
            )
            PrincipleManageScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Destinations.CALL_SUMMARY_WITH_ARG,
            arguments = listOf(
                navArgument("phone") { type = NavType.StringType; defaultValue = "" },
                navArgument("name") { type = NavType.StringType; nullable = true; defaultValue = null }
            )
        ) { entry ->
            CallSummaryScreen(
                container = container,
                phone = entry.arguments?.getString("phone").orEmpty(),
                customerName = entry.arguments?.getString("name"),
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Destinations.CHAT_WITH_ARG,
            arguments = listOf(
                navArgument("phone") { type = NavType.StringType; defaultValue = "" },
                navArgument("customerId") { type = NavType.LongType; defaultValue = -1L },
                navArgument("editIssuedId") { type = NavType.LongType; defaultValue = -1L }
            )
        ) { backStack ->
            val phone = backStack.arguments?.getString("phone").orEmpty()
            val customerId = backStack.arguments?.getLong("customerId")?.takeIf { it > 0 }
            val editIssuedId = backStack.arguments?.getLong("editIssuedId") ?: -1L
            val vm: ChatViewModel = viewModel(
                factory = viewModelFactory { ChatViewModel(container, phone, customerId) }
            )
            ChatScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() },
                onOpenCustomerDetail = { id -> navController.navigate(Destinations.customerDetail(id)) },
                editIssuedId = editIssuedId,
                // 통화녹음 미연결 통화카드 "연결 설정하러 가기" → 자동 문자/녹음 설정. (2026-07-12 사장님)
                onOpenRecordingSettings = { navController.navigate(Destinations.SETTINGS_AUTOSMS) }
            )
        }

        composable(
            route = Destinations.SCHEDULE_WITH_ARG,
            arguments = listOf(navArgument("day") { type = NavType.LongType; defaultValue = -1L })
        ) { entry ->
            val vm: ScheduleViewModel = viewModel(factory = viewModelFactory { ScheduleViewModel(container) })
            // 구글 캘린더 — 일정 탭 상단 버튼에서 바로 연결/동기화. (2026-09-01 사장님)
            val schedCtx = LocalContext.current
            val schedScope = rememberCoroutineScope()
            fun schedToast(m: String) = android.widget.Toast.makeText(schedCtx, m, android.widget.Toast.LENGTH_SHORT).show()
            val calConnectLauncher = rememberLauncherForActivityResult(
                androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult()
            ) { result ->
                val token = container.googleCalendarConnection.tokenFromConsentResult(result.data)
                if (token != null) {
                    container.preferences.googleCalendarConnected = true
                    schedScope.launch { runCatching { container.calendarSyncManager.syncAll() }; schedToast("구글 캘린더 연결·동기화 완료") }
                } else schedToast("연결이 취소됐어요")
            }
            ScheduleScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() },
                onOpenCustomer = { id -> navController.navigate(Destinations.customerDetail(id)) },
                onOpenChat = { phone, customerId -> navController.navigate(Destinations.chat(phone, customerId)) },
                onAddSchedule = { day -> navController.navigate(Destinations.scheduleAdd(day)) },
                onOpenSettle = { navController.navigate(Destinations.SETTLEMENT) },
                onOpenCollabSites = { shareId -> navController.navigate(Destinations.collabSites(shareId)) },
                calendarConnected = container.preferences.googleCalendarConnected,
                onCalendarSync = {
                    schedScope.launch {
                        if (container.preferences.googleCalendarConnected) {
                            val n = runCatching { container.calendarSyncManager.syncAll() }.getOrDefault(-1)
                            schedToast(if (n >= 0) "구글 캘린더에 동기화했어요" else "동기화 실패 — 잠시 후 다시")
                        } else when (val r = runCatching { container.googleCalendarConnection.authorize() }.getOrNull()) {
                            is com.detailline.callfollowcrm.data.calendar.GoogleCalendarConnection.AuthResult.Success -> {
                                container.preferences.googleCalendarConnected = true
                                runCatching { container.calendarSyncManager.syncAll() }
                                schedToast("구글 캘린더 연결·동기화 완료")
                            }
                            is com.detailline.callfollowcrm.data.calendar.GoogleCalendarConnection.AuthResult.NeedsConsent ->
                                calConnectLauncher.launch(androidx.activity.result.IntentSenderRequest.Builder(r.intentSender).build())
                            else -> schedToast("구글 로그인을 시작할 수 없어요")
                        }
                    }
                },
                initialSelectedDayMs = entry.arguments?.getLong("day")?.takeIf { it > 0L }
            )
        }

        composable(
            route = Destinations.SCHEDULE_ADD_WITH_ARG,
            arguments = listOf(navArgument("day") { type = NavType.LongType; defaultValue = -1L })
        ) { entry ->
            val vm: ScheduleAddViewModel = viewModel(factory = viewModelFactory { ScheduleAddViewModel(container) })
            ScheduleAddScreen(
                viewModel = vm,
                initialDayMs = entry.arguments?.getLong("day")?.takeIf { it > 0L },
                onDone = { navController.popBackStack() },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Destinations.SETTLEMENT) {
            val vm: SettlementViewModel = viewModel(factory = viewModelFactory { SettlementViewModel(container) })
            SettlementScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() },
                onOpenCustomer = { id -> navController.navigate(Destinations.customerDetail(id)) },
                onOpenScheduleAtDay = { dayMs ->
                    if (dayMs > 0L) navController.navigate(Destinations.schedule(dayMs))
                    else navController.navigate(Destinations.SCHEDULE)
                }
            )
        }

        composable(Destinations.STATS) {
            val vm: com.detailline.callfollowcrm.presentation.screen.stats.StatsViewModel =
                viewModel(factory = viewModelFactory { com.detailline.callfollowcrm.presentation.screen.stats.StatsViewModel(container) })
            StatsScreen(
                viewModel = vm,
                onOpenVisited = { navController.navigate(Destinations.VISITED) }
            )
        }

        composable(Destinations.VISITED) {
            val vm: com.detailline.callfollowcrm.presentation.screen.stats.VisitedViewModel =
                viewModel(factory = viewModelFactory { com.detailline.callfollowcrm.presentation.screen.stats.VisitedViewModel(container) })
            com.detailline.callfollowcrm.presentation.screen.stats.VisitedScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() },
                onOpenCustomer = { id -> navController.navigate(Destinations.customerDetail(id)) }
            )
        }

        composable(Destinations.SEARCH) {
            val vm: com.detailline.callfollowcrm.presentation.screen.search.SearchViewModel =
                viewModel(factory = viewModelFactory { com.detailline.callfollowcrm.presentation.screen.search.SearchViewModel(container) })
            com.detailline.callfollowcrm.presentation.screen.search.SearchScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() },
                onOpenChat = { phone, customerId -> navController.navigate(Destinations.chat(phone, customerId)) }
            )
        }

        composable(Destinations.CUSTOMERS) {
            val vm: com.detailline.callfollowcrm.presentation.screen.customers.CustomersViewModel =
                viewModel(factory = viewModelFactory { com.detailline.callfollowcrm.presentation.screen.customers.CustomersViewModel(container) })
            com.detailline.callfollowcrm.presentation.screen.customers.CustomersScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() },
                onOpenCustomerDetail = { id -> navController.navigate(Destinations.customerDetail(id)) }
            )
        }

        composable(Destinations.NEW_LEADS) {
            val vm: com.detailline.callfollowcrm.presentation.screen.newleads.NewLeadsViewModel =
                viewModel(factory = viewModelFactory { com.detailline.callfollowcrm.presentation.screen.newleads.NewLeadsViewModel(container) })
            com.detailline.callfollowcrm.presentation.screen.newleads.NewLeadsScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() },
                onOpenLead = { phone, customerId ->
                    // 신규는 고객정보가 거의 없음 → 고객상세 말고 바로 채팅으로. (2026-06-23 사장님)
                    navController.navigate(Destinations.chat(phone, customerId.takeIf { it > 0 }))
                },
                onReContact = { phone, customerId -> navController.navigate(Destinations.chat(phone, customerId)) }
            )
        }

        // 2026-05-25: PIPELINE 라우트 폐기 — CustomerStatus enum 제거 + 카테고리 시스템 통일.

        composable(
            route = Destinations.FOLLOW_UP_WITH_ARG,
            arguments = listOf(
                navArgument("phone") { type = NavType.StringType; defaultValue = ""; nullable = true },
                navArgument("callRecordId") { type = NavType.LongType; defaultValue = -1L },
                navArgument("templateId") { type = NavType.LongType; defaultValue = -1L }
            )
        ) { backStack ->
            val phone = backStack.arguments?.getString("phone").orEmpty()
            val callRecordId = backStack.arguments?.getLong("callRecordId")?.takeIf { it > 0 }
            val templateId = backStack.arguments?.getLong("templateId")?.takeIf { it > 0 }
            val vm: FollowUpViewModel = viewModel(
                factory = viewModelFactory { FollowUpViewModel(container, phone, callRecordId, templateId) }
            )
            FollowUpScreen(
                viewModel = vm,
                onDone = { navController.popBackStack() },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Destinations.CUSTOMER_DETAIL_WITH_ARG,
            arguments = listOf(navArgument("customerId") { type = NavType.LongType })
        ) { backStack ->
            val id = backStack.arguments?.getLong("customerId") ?: return@composable
            val vm: CustomerDetailViewModel = viewModel(
                factory = viewModelFactory { CustomerDetailViewModel(container, id) }
            )
            CustomerDetailScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() },
                // CustomerDetail 의 하단 "문자 보내기" 는 이제 ChatScreen 으로 (메인 대화 채널).
                onOpenChat = { phone, customerId -> navController.navigate(Destinations.chat(phone, customerId)) },
                // 발행 이력 "수정" → 채팅으로 이동하며 그 접수서 편집기 재오픈. (2026-07-10 사장님)
                onOpenChatEditIssued = { phone, customerId, issuedId ->
                    navController.navigate(Destinations.chat(phone, customerId, issuedId))
                }
            )
        }

        composable(Destinations.TEMPLATE_LIST) {
            val vm: TemplateListViewModel = viewModel(factory = viewModelFactory { TemplateListViewModel(container) })
            TemplateListScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() },
                onEdit = { id -> navController.navigate(Destinations.templateEdit(id)) },
                onNew = { navController.navigate(Destinations.templateEdit(null)) },
                onOpenDiscover = { navController.navigate(Destinations.TEMPLATE_DISCOVER) }
            )
        }

        // 자주 쓰는 문자 찾기 — 반복 발송 문자를 템플릿으로 저장 제안. (2026-07-02)
        composable(Destinations.TEMPLATE_DISCOVER) {
            val vm: TemplateDiscoverViewModel = viewModel(factory = viewModelFactory { TemplateDiscoverViewModel(container) })
            TemplateDiscoverScreen(viewModel = vm, onBack = { navController.popBackStack() })
        }

        composable(
            route = Destinations.TEMPLATE_EDIT_WITH_ARG,
            arguments = listOf(navArgument("id") { type = NavType.LongType; defaultValue = -1L })
        ) { backStack ->
            val id = backStack.arguments?.getLong("id")?.takeIf { it > 0 }
            val vm: TemplateEditViewModel = viewModel(
                factory = viewModelFactory { TemplateEditViewModel(container, id) }
            )
            TemplateEditScreen(viewModel = vm, onDone = { navController.popBackStack() })
        }

        composable(Destinations.SETTINGS) {
            val vm: SettingsViewModel = viewModel(factory = viewModelFactory { SettingsViewModel(container) })
            SettingsScreen(
                viewModel = vm,
                container = container,
                onBack = { navController.popBackStack() },
                onOpenTemplates = { navController.navigate(Destinations.TEMPLATE_LIST) },
                onOpenPricingItems = { navController.navigate(Destinations.PRICING_ITEMS) },
                onOpenBusinessInfo = { navController.navigate(Destinations.BUSINESS_INFO) },
                onOpenNotebook = { navController.navigate(Destinations.NOTEBOOK) },
                onOpenTeam = { navController.navigate(Destinations.TEAM) },
                onOpenCollabSites = { navController.navigate(Destinations.COLLAB_SITES) },
                onOpenCollabRecord = { navController.navigate(Destinations.COLLAB_RECORD) },
                onOpenReport = { navController.navigate(Destinations.REPORT) },
                onOpenTradeSelect = { navController.navigate(Destinations.TRADE_SELECT) },
                onOpenRecurring = { navController.navigate(Destinations.RECURRING) },
                onOpenPrinciples = { navController.navigate(Destinations.PRINCIPLES) },
                onOpenSpamList = { navController.navigate(Destinations.SPAM_LIST) },
                onOpenPersonalList = { navController.navigate(Destinations.PERSONAL_LIST) },
                onOpenSoundSettings = { navController.navigate(Destinations.SOUND_SETTINGS) },
                onOpenExpo = { navController.navigate(Destinations.EXPO) },
                onShowIntro = { navController.navigate(Destinations.ONBOARDING) }
            )
        }

        // 더보기 → 박람회 — 박람회 팀 전용 창구(완전 별개 공간, 카톡 스타일). 하단 탭 없음. (2026-07-21 사장님)
        composable(Destinations.EXPO) {
            com.detailline.callfollowcrm.presentation.screen.expo.ExpoScreen(
                container = container,
                onExit = { navController.popBackStack() }
            )
        }

        // 더보기 → 알림 소리 — 알림 종류별 소리 고르기 + 미리듣기. (2026-07-10 사장님)
        composable(Destinations.SOUND_SETTINGS) {
            com.detailline.callfollowcrm.presentation.screen.settings.SoundSettingsScreen(
                prefs = container.preferences,
                onBack = { navController.popBackStack() }
            )
        }

        // 자동 문자(부재중 응답 펼침) 직행 — 상담함 "자동답장" 알림 길게누름 진입.
        composable(Destinations.SETTINGS_AUTOSMS) {
            val vm: SettingsViewModel = viewModel(factory = viewModelFactory { SettingsViewModel(container) })
            SettingsScreen(
                viewModel = vm,
                container = container,
                onBack = { navController.popBackStack() },
                onOpenTemplates = { navController.navigate(Destinations.TEMPLATE_LIST) },
                onOpenPricingItems = { navController.navigate(Destinations.PRICING_ITEMS) },
                onOpenBusinessInfo = { navController.navigate(Destinations.BUSINESS_INFO) },
                onOpenNotebook = { navController.navigate(Destinations.NOTEBOOK) },
                onOpenTeam = { navController.navigate(Destinations.TEAM) },
                onOpenCollabSites = { navController.navigate(Destinations.COLLAB_SITES) },
                onOpenCollabRecord = { navController.navigate(Destinations.COLLAB_RECORD) },
                onOpenReport = { navController.navigate(Destinations.REPORT) },
                onOpenTradeSelect = { navController.navigate(Destinations.TRADE_SELECT) },
                onOpenRecurring = { navController.navigate(Destinations.RECURRING) },
                onShowIntro = { navController.navigate(Destinations.ONBOARDING) },
                initialSubPage = "autosms"
            )
        }

        // 더보기 → 스팸 차단 번호 / 사생활 번호 목록(잘못 등록 시 복구). (2026-06-23 사장님)
        composable(Destinations.SPAM_LIST) {
            com.detailline.callfollowcrm.presentation.screen.spam.SpamListScreen(
                container = container, kind = "spam",
                onBack = { navController.popBackStack() }
            )
        }
        composable(Destinations.PERSONAL_LIST) {
            com.detailline.callfollowcrm.presentation.screen.spam.SpamListScreen(
                container = container, kind = "personal",
                onBack = { navController.popBackStack() }
            )
        }

        // 더보기 → 협업 기록 — 협업 사장별·월별 집계(받은/준). 기록·세금용. (2026-08-01 사장님)
        composable(Destinations.COLLAB_RECORD) {
            val vm: com.detailline.callfollowcrm.presentation.screen.collab.CollabRecordViewModel =
                viewModel(factory = viewModelFactory { com.detailline.callfollowcrm.presentation.screen.collab.CollabRecordViewModel(container) })
            com.detailline.callfollowcrm.presentation.screen.collab.CollabRecordScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Destinations.CLOSING_BRIEF) {
            val vm: com.detailline.callfollowcrm.presentation.screen.brief.ClosingBriefViewModel =
                viewModel(factory = viewModelFactory { com.detailline.callfollowcrm.presentation.screen.brief.ClosingBriefViewModel(container) })
            com.detailline.callfollowcrm.presentation.screen.brief.ClosingBriefScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() },
                onOpenChat = { phone, customerId -> navController.navigate(Destinations.chat(phone, customerId)) },
                onOpenSchedule = { navController.navigate(Destinations.SCHEDULE) },
                onOpenNewLeads = { navController.navigate(Destinations.NEW_LEADS) },
                onOpenSettlement = { navController.navigate(Destinations.SETTLEMENT) }
            )
        }

        composable(Destinations.BUSINESS_INFO) {
            BusinessInfoScreen(
                prefs = container.preferences,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Destinations.NOTEBOOK) {
            val vm: NotebookViewModel = viewModel(factory = viewModelFactory { NotebookViewModel(container) })
            // 수첩 = 거래처만 (일당·알바는 인원 관리로 이동).
            NotebookScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() },
                restrictKind = com.detailline.callfollowcrm.presentation.screen.notebook.NotebookTab.VENDOR
            )
        }

        composable(Destinations.TEAM) {
            val vm: com.detailline.callfollowcrm.presentation.screen.team.TeamViewModel =
                viewModel(factory = viewModelFactory { com.detailline.callfollowcrm.presentation.screen.team.TeamViewModel(container) })
            // 인원 관리 = 팀원(서버) + 일당사장(수첩 WORKER). NotebookViewModel 도 주입.
            val nbVm: NotebookViewModel = viewModel(factory = viewModelFactory { NotebookViewModel(container) })
            com.detailline.callfollowcrm.presentation.screen.team.TeamScreen(
                viewModel = vm,
                notebookViewModel = nbVm,
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            Destinations.COLLAB_SITES_WITH_ARG,
            arguments = listOf(
                navArgument("share") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("tab") { type = NavType.StringType; nullable = true; defaultValue = null }
            )
        ) { entry ->
            val shareId = entry.arguments?.getString("share")
            val tab = entry.arguments?.getString("tab")
            val vm: com.detailline.callfollowcrm.presentation.screen.sharedsite.SharedSiteViewModel =
                viewModel(factory = viewModelFactory { com.detailline.callfollowcrm.presentation.screen.sharedsite.SharedSiteViewModel(container) })
            com.detailline.callfollowcrm.presentation.screen.sharedsite.SharedSiteScreen(
                viewModel = vm,
                initialShareId = shareId,
                initialTab = tab,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Destinations.REPORT) {
            val vm: ReportViewModel = viewModel(factory = viewModelFactory { ReportViewModel(container) })
            ReportScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() },
                onOpenCustomer = { id -> navController.navigate(Destinations.customerDetail(id)) }
            )
        }

        composable(Destinations.TRADE_SELECT) {
            TradeSelectScreen(prefs = container.preferences, onBack = { navController.popBackStack() })
        }

        composable(Destinations.RECURRING) {
            val vm: com.detailline.callfollowcrm.presentation.screen.recurring.RecurringMessagesViewModel =
                viewModel(factory = viewModelFactory { com.detailline.callfollowcrm.presentation.screen.recurring.RecurringMessagesViewModel(container) })
            com.detailline.callfollowcrm.presentation.screen.recurring.RecurringMessagesScreen(
                viewModel = vm, onBack = { navController.popBackStack() }
            )
        }

        composable(Destinations.RECURRING_DUE) {
            val vm: com.detailline.callfollowcrm.presentation.screen.recurring.RecurringDueViewModel =
                viewModel(factory = viewModelFactory { com.detailline.callfollowcrm.presentation.screen.recurring.RecurringDueViewModel(container) })
            com.detailline.callfollowcrm.presentation.screen.recurring.RecurringDueScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() },
                onOpenChat = { phone, customerId -> navController.navigate(Destinations.chat(phone, customerId)) }
            )
        }

        composable(Destinations.SCHEDULE_REMINDER) {
            val vm: com.detailline.callfollowcrm.presentation.screen.reminder.ScheduleReminderViewModel =
                viewModel(factory = viewModelFactory { com.detailline.callfollowcrm.presentation.screen.reminder.ScheduleReminderViewModel(container) })
            com.detailline.callfollowcrm.presentation.screen.reminder.ScheduleReminderScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() },
                onOpenChat = { phone, customerId -> navController.navigate(Destinations.chat(phone, customerId)) }
            )
        }

        composable(Destinations.ESTIMATE_FOLLOWUP) {
            val vm: com.detailline.callfollowcrm.presentation.screen.estimate.EstimateFollowupViewModel =
                viewModel(factory = viewModelFactory { com.detailline.callfollowcrm.presentation.screen.estimate.EstimateFollowupViewModel(container) })
            com.detailline.callfollowcrm.presentation.screen.estimate.EstimateFollowupScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() },
                onOpenChat = { phone, customerId -> navController.navigate(Destinations.chat(phone, customerId)) }
            )
        }

        composable(Destinations.PRICING_ITEMS) {
            val vm: PricingItemsViewModel = viewModel(factory = viewModelFactory { PricingItemsViewModel(container) })
            PricingItemsScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() },
                onOpenExtract = { navController.navigate(Destinations.PRICING_EXTRACT) }
            )
        }

        // 문자에서 가격 불러오기 — 2단계 확인(문자 기반 가격 추출). (2026-07-02)
        composable(Destinations.PRICING_EXTRACT) {
            val vm: PricingExtractViewModel = viewModel(factory = viewModelFactory { PricingExtractViewModel(container) })
            PricingExtractScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() },
                onDone = { navController.popBackStack() },
                preferences = container.preferences
            )
        }
    }
}
