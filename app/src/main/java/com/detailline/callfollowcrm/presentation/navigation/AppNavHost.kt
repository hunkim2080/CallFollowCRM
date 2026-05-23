package com.detailline.callfollowcrm.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.detailline.callfollowcrm.data.AppContainer
import com.detailline.callfollowcrm.domain.model.CustomerStatus
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
import com.detailline.callfollowcrm.presentation.screen.pipeline.PipelineScreen
import com.detailline.callfollowcrm.presentation.screen.pipeline.PipelineViewModel
import com.detailline.callfollowcrm.presentation.screen.schedule.ScheduleScreen
import com.detailline.callfollowcrm.presentation.screen.schedule.ScheduleViewModel
import com.detailline.callfollowcrm.presentation.screen.settings.SettingsScreen
import com.detailline.callfollowcrm.presentation.screen.settings.SettingsViewModel
import com.detailline.callfollowcrm.presentation.screen.stylelearning.StyleLearningScreen
import com.detailline.callfollowcrm.presentation.screen.stylelearning.StyleLearningViewModel
import com.detailline.callfollowcrm.presentation.screen.template.TemplateEditScreen
import com.detailline.callfollowcrm.presentation.screen.template.TemplateEditViewModel
import com.detailline.callfollowcrm.presentation.screen.template.TemplateListScreen
import com.detailline.callfollowcrm.presentation.screen.template.TemplateListViewModel
import com.detailline.callfollowcrm.presentation.util.viewModelFactory

@Composable
fun AppNavHost(
    navController: NavHostController,
    container: AppContainer,
    startDestination: String
) {
    NavHost(navController = navController, startDestination = startDestination) {

        composable(Destinations.ONBOARDING) {
            OnboardingPermissionScreen(
                onContinue = {
                    navController.navigate(Destinations.HOME) {
                        popUpTo(Destinations.ONBOARDING) { inclusive = true }
                    }
                }
            )
        }

        composable(Destinations.HOME) {
            val vm: HomeViewModel = viewModel(factory = viewModelFactory { HomeViewModel(container) })
            HomeScreen(
                viewModel = vm,
                // 대시보드의 모든 번호/카드 클릭은 ChatScreen 으로 통일 (메인 진입점).
                // customerId 없으면 ChatViewModel 이 phone 으로 lookup, 발송/[ⓘ] 시 upsert.
                onOpenChat = { phone, customerId ->
                    navController.navigate(Destinations.chat(phone, customerId))
                },
                // FAB "수동 입력" 은 기존 FollowUp 화면 유지 (번호 직접 입력 + 상태/메모 한 번에).
                onOpenManualEntry = { navController.navigate(Destinations.followUp()) },
                onOpenPipeline = { statusName -> navController.navigate(Destinations.pipeline(statusName)) },
                onOpenSchedule = { navController.navigate(Destinations.SCHEDULE) },
                onOpenTemplates = { navController.navigate(Destinations.TEMPLATE_LIST) },
                onOpenAiMessage = { navController.navigate(Destinations.AI_MESSAGE) },
                onOpenStyleLearning = { navController.navigate(Destinations.STYLE_LEARNING) },
                onOpenSettings = { navController.navigate(Destinations.SETTINGS) }
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
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Destinations.CHAT_WITH_ARG,
            arguments = listOf(
                navArgument("phone") { type = NavType.StringType; defaultValue = "" },
                navArgument("customerId") { type = NavType.LongType; defaultValue = -1L }
            )
        ) { backStack ->
            val phone = backStack.arguments?.getString("phone").orEmpty()
            val customerId = backStack.arguments?.getLong("customerId")?.takeIf { it > 0 }
            val vm: ChatViewModel = viewModel(
                factory = viewModelFactory { ChatViewModel(container, phone, customerId) }
            )
            ChatScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() },
                onOpenCustomerDetail = { id -> navController.navigate(Destinations.customerDetail(id)) }
            )
        }

        composable(Destinations.SCHEDULE) {
            val vm: ScheduleViewModel = viewModel(factory = viewModelFactory { ScheduleViewModel(container) })
            ScheduleScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() },
                onOpenCustomer = { id -> navController.navigate(Destinations.customerDetail(id)) }
            )
        }

        composable(
            route = Destinations.PIPELINE_WITH_ARG,
            arguments = listOf(navArgument("statusName") { type = NavType.StringType })
        ) { backStack ->
            val statusName = backStack.arguments?.getString("statusName").orEmpty()
            val statusLabel = runCatching { CustomerStatus.valueOf(statusName).label }.getOrDefault(statusName)
            val vm: PipelineViewModel = viewModel(
                factory = viewModelFactory { PipelineViewModel(container, statusLabel) }
            )
            PipelineScreen(
                viewModel = vm,
                statusLabel = statusLabel,
                onBack = { navController.popBackStack() },
                onOpenCustomer = { id -> navController.navigate(Destinations.customerDetail(id)) }
            )
        }

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
                onOpenChat = { phone, customerId -> navController.navigate(Destinations.chat(phone, customerId)) }
            )
        }

        composable(Destinations.TEMPLATE_LIST) {
            val vm: TemplateListViewModel = viewModel(factory = viewModelFactory { TemplateListViewModel(container) })
            TemplateListScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() },
                onEdit = { id -> navController.navigate(Destinations.templateEdit(id)) },
                onNew = { navController.navigate(Destinations.templateEdit(null)) }
            )
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
                onOpenTemplates = { navController.navigate(Destinations.TEMPLATE_LIST) }
            )
        }
    }
}
