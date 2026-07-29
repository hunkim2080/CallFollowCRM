package com.detailline.callfollowcrm.presentation

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.flow.first
import com.detailline.callfollowcrm.data.AppContainer
import com.detailline.callfollowcrm.presentation.component.RING_TAB_ROUTES
import com.detailline.callfollowcrm.presentation.component.RingTabBar
import com.detailline.callfollowcrm.presentation.navigation.AppNavHost
import com.detailline.callfollowcrm.presentation.navigation.Destinations
import com.detailline.callfollowcrm.presentation.navigation.NavEvent
import com.detailline.callfollowcrm.presentation.theme.CallFollowCrmTheme
import com.detailline.callfollowcrm.presentation.theme.TossGrayBg
import com.detailline.callfollowcrm.util.PermissionHelper

@Composable
fun AppRoot(container: AppContainer) {
    // 다크모드 임시 강제 라이트(2026-07-30): 화면 다수가 Color.White 를 하드코딩해 시스템 다크에서
    //   흰 바탕에 흰 글씨(입력 안 보임)·다이얼로그 색 얼룩이 생김. 토큰 완전 이관 전까지 라이트 고정(정직함).
    CallFollowCrmTheme(darkOverride = false) {
        Surface(color = TossGrayBg) {
            val navController = rememberNavController()
            val context = LocalContext.current

            // 연락처 이름 자동 반영(READ_CONTACTS) — 온보딩 마친 기존 사용자에게 1회만 요청(선택 권한, 거부해도 앱 정상).
            //   신규 사용자는 온보딩 배치에서 함께 요청됨. 허가되면 즉시 이름 백필. (2026-07-21 사장님)
            val bgScope = rememberCoroutineScope()
            val contactsLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { granted ->
                if (granted) bgScope.launch(Dispatchers.IO) {
                    com.detailline.callfollowcrm.util.ContactNameResolver.clearCache()
                    runCatching { container.customerRepository.fillBlankNamesFromContacts(context) }
                }
            }
            LaunchedEffect(Unit) {
                if (container.preferences.hasOnboarded &&
                    !PermissionHelper.hasContacts(context) &&
                    !container.preferences.contactsPermissionAsked
                ) {
                    container.preferences.contactsPermissionAsked = true
                    runCatching { contactsLauncher.launch(android.Manifest.permission.READ_CONTACTS) }
                }
            }

            val permsMissing = PermissionHelper.allMissingNonNotification(context).isNotEmpty()
            val smsSignup = com.detailline.callfollowcrm.AppConfig.SMS_SIGNUP_ENABLED
            val startDestination = when {
                // 문자 회원가입(SIGNUP)은 SOLAPI 준비 전까지 off — 그동안은 예전 간단 로그인(번호만)으로 진입.
                //   심사자·테스터가 문자 없이도 앱에 들어올 수 있어야 함(Play 검토·베타 테스트). (2026-07-05 사장님)
                smsSignup && container.preferences.pendingWaitlist -> Destinations.SIGNUP
                container.preferences.bizPhone.isBlank() -> if (smsSignup) Destinations.SIGNUP else Destinations.LOGIN
                // 번호는 있는데 개인정보 동의 미완(기존 사용자 포함) → 동의 게이트 1회. (추가97 2026-07-06)
                container.preferences.consentAgreedVersion != com.detailline.callfollowcrm.AppConfig.CONSENT_VERSION -> Destinations.CONSENT
                !container.preferences.hasOnboarded -> Destinations.ONBOARDING
                permsMissing -> Destinations.PERMISSIONS
                else -> Destinations.HOME
            }

            LaunchedEffect(Unit) {
                // 콜드스타트 딥링크(협업 공유 링크·알림 탭) race 방지: NavHost 가 그래프를 세팅하기 전에
                //   navigate 하면 "Navigation graph has not been set" 크래시. 첫 백스택 엔트리(그래프 준비 신호)를
                //   기다린 뒤 수집 시작 — 버퍼(Channel.BUFFERED)된 이벤트는 그 직후 정상 전달됨.
                navController.currentBackStackEntryFlow.first()
                container.navEvents.events.collect { event ->
                    when (event) {
                        is NavEvent.OpenFollowUp -> {
                            navController.navigate(
                                Destinations.followUp(
                                    phone = event.phoneNumber,
                                    callRecordId = event.callRecordId,
                                    templateId = event.templateId
                                )
                            ) { launchSingleTop = true }
                        }
                        is NavEvent.OpenChat -> {
                            navController.navigate(
                                Destinations.chat(event.phoneNumber, event.customerId)
                            ) { launchSingleTop = true }
                        }
                        is NavEvent.OpenCallSummary -> {
                            navController.navigate(
                                Destinations.callSummary(event.phoneNumber, event.name)
                            ) { launchSingleTop = true }
                        }
                        is NavEvent.OpenClosingBrief -> {
                            navController.navigate(Destinations.CLOSING_BRIEF) { launchSingleTop = true }
                        }
                        is NavEvent.OpenCollabSites -> {
                            navController.navigate(Destinations.collabSites(event.shareId, event.tab)) { launchSingleTop = true }
                        }
                        is NavEvent.OpenCustomerDetail -> {
                            navController.navigate(Destinations.customerDetail(event.customerId)) { launchSingleTop = true }
                        }
                    }
                }
            }

            // 현재 라우트 → 하단 탭바 표시 여부 + 활성 탭 판단.
            val currentEntry by navController.currentBackStackEntryAsState()
            // 인자 있는 탭 라우트("schedule?day=...") 도 같은 탭으로 인식 — "?" 앞 기준으로 비교.
            val currentRoute = currentEntry?.destination?.route?.substringBefore("?")
            var lastTabRoute by remember { mutableStateOf(Destinations.HOME) }
            LaunchedEffect(currentRoute) {
                if (currentRoute in RING_TAB_ROUTES) {
                    lastTabRoute = currentRoute ?: Destinations.HOME
                }
            }
            val detailRoutesWithTabs = setOf(Destinations.CUSTOMER_DETAIL_WITH_ARG)
            val showTabBar = currentRoute in RING_TAB_ROUTES || currentRoute in detailRoutesWithTabs

            Scaffold(
                // 상단 인셋은 각 화면이 자체 처리 → 외곽 Scaffold 는 0. 하단 탭바만 패딩 제공.
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                bottomBar = {
                    if (showTabBar) {
                        // 상담함 배지 = 받은 협업 요청(수락 대기) 수 — 푸시 지워도 여기서 눈에 띄게. (2026-06-14 사장님)
                        val pendingCollab by container.collabEventCenter.pendingInvites.collectAsState()
                        RingTabBar(
                            inboxBadge = pendingCollab.size.takeIf { it > 0 },
                            currentRoute = if (currentRoute in RING_TAB_ROUTES) currentRoute else lastTabRoute,
                            onSelect = { route ->
                                // 2026-06-07 사장님 통점: 탭이 가끔 안 눌림.
                                //   원인 후보 = recompose 로 갱신되는 currentRoute 가 전환 중 잠깐 stale → 같은 탭으로 오판해 navigate 스킵.
                                //   해결: 탭 누르는 순간의 실제 목적지(navController.currentDestination)로 비교.
                                val live = navController.currentDestination?.route?.substringBefore("?")
                                android.util.Log.d("NAVTAB", "tap=$route state=$currentRoute live=$live")
                                val alreadyOnRootTab = currentRoute in RING_TAB_ROUTES && route == live
                                if (!alreadyOnRootTab) {
                                    val startId = navController.graph.findStartDestination().id
                                    // 2026-06-10 사장님 통점 "상세화면(고객상세 등)에서 하단 탭이 안 눌림":
                                    //   터치는 정상(NAVTAB 로그 찍힘)인데 navigate 가 조용히 무시됨(no-op).
                                    //   원인 = 탭 루트가 아닌 상세화면 위에서 restoreState=true 로 탭 전환 시
                                    //          Navigation 의 상태 복원이 충돌해 전환을 삼킴(예외도 안 남).
                                    //   해결: 상세 위에 있으면 먼저 시작지점까지 pop → "탭루트→탭" 정상 경로로 만든 뒤 전환.
                                    if (live !in RING_TAB_ROUTES) {
                                        navController.popBackStack(startId, /* inclusive = */ false)
                                    }
                                    navController.navigate(route) {
                                        // 탭 전환: 시작 지점까지 pop + 상태 저장/복원으로 탭별 스크롤·스택 유지.
                                        popUpTo(startId) {
                                            saveState = route != Destinations.HOME
                                        }
                                        launchSingleTop = true
                                        restoreState = route != Destinations.HOME
                                    }
                                }
                            }
                        )
                    }
                }
            ) { innerPadding ->
                AppNavHost(
                    navController = navController,
                    container = container,
                    startDestination = startDestination,
                    modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding())
                )
            }
        }
    }
}
