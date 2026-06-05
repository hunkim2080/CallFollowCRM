package com.detailline.callfollowcrm.presentation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
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
    CallFollowCrmTheme {
        Surface(color = TossGrayBg) {
            val navController = rememberNavController()
            val context = LocalContext.current

            val permsMissing = PermissionHelper.allMissingNonNotification(context).isNotEmpty()
            val startDestination = when {
                // 전면 리뉴얼 흐름: 로그인 → 온보딩(스토리텔링/캐릭터) → 권한 → 홈. 각 단계 1회.
                !container.preferences.hasSeenLogin -> Destinations.LOGIN
                !container.preferences.hasOnboarded -> Destinations.ONBOARDING
                permsMissing -> Destinations.PERMISSIONS
                else -> Destinations.HOME
            }

            LaunchedEffect(Unit) {
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
                    }
                }
            }

            // 현재 라우트 → 하단 탭바 표시 여부 + 활성 탭 판단.
            val currentEntry by navController.currentBackStackEntryAsState()
            val currentRoute = currentEntry?.destination?.route
            val showTabBar = currentRoute in RING_TAB_ROUTES

            Scaffold(
                // 상단 인셋은 각 화면이 자체 처리 → 외곽 Scaffold 는 0. 하단 탭바만 패딩 제공.
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                bottomBar = {
                    if (showTabBar) {
                        RingTabBar(
                            currentRoute = currentRoute,
                            onSelect = { route ->
                                if (route != currentRoute) {
                                    navController.navigate(route) {
                                        // 탭 전환: 시작 지점까지 pop + 상태 저장/복원으로 탭별 스크롤·스택 유지.
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
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
