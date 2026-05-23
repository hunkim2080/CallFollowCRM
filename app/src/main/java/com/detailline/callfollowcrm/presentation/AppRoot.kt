package com.detailline.callfollowcrm.presentation

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.rememberNavController
import com.detailline.callfollowcrm.data.AppContainer
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

            val showOnboarding = PermissionHelper.allMissingNonNotification(context).isNotEmpty()
            val startDestination = if (showOnboarding) Destinations.ONBOARDING else Destinations.HOME

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
                    }
                }
            }

            AppNavHost(
                navController = navController,
                container = container,
                startDestination = startDestination
            )
        }
    }
}
