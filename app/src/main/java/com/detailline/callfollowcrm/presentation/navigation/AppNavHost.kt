package com.detailline.callfollowcrm.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
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
import com.detailline.callfollowcrm.presentation.screen.schedule.ScheduleScreen
import com.detailline.callfollowcrm.presentation.screen.schedule.ScheduleViewModel
import com.detailline.callfollowcrm.presentation.screen.schedule.ScheduleAddScreen
import com.detailline.callfollowcrm.presentation.screen.schedule.ScheduleAddViewModel
import com.detailline.callfollowcrm.presentation.screen.settings.SettingsScreen
import com.detailline.callfollowcrm.presentation.screen.settings.SettingsViewModel
import com.detailline.callfollowcrm.presentation.screen.settlement.SettlementScreen
import com.detailline.callfollowcrm.presentation.screen.settlement.SettlementViewModel
import com.detailline.callfollowcrm.presentation.screen.business.BusinessInfoScreen
import com.detailline.callfollowcrm.presentation.screen.notebook.NotebookScreen
import com.detailline.callfollowcrm.presentation.screen.notebook.NotebookViewModel
import com.detailline.callfollowcrm.presentation.screen.report.ReportScreen
import com.detailline.callfollowcrm.presentation.screen.report.ReportViewModel
import com.detailline.callfollowcrm.presentation.screen.stats.StatsScreen
import com.detailline.callfollowcrm.presentation.screen.trade.TradeSelectScreen
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
    startDestination: String,
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier
) {
    NavHost(navController = navController, startDestination = startDestination, modifier = modifier) {

        composable(Destinations.LOGIN) {
            val context = androidx.compose.ui.platform.LocalContext.current
            com.detailline.callfollowcrm.presentation.screen.login.LoginScreen(
                onProceed = {
                    container.preferences.hasSeenLogin = true
                    val next = when {
                        !container.preferences.hasOnboarded -> Destinations.ONBOARDING
                        com.detailline.callfollowcrm.util.PermissionHelper
                            .allMissingNonNotification(context).isNotEmpty() -> Destinations.PERMISSIONS
                        else -> Destinations.HOME
                    }
                    navController.navigate(next) {
                        popUpTo(Destinations.LOGIN) { inclusive = true }
                    }
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

        // 권한 요청 (기존 화면) — 온보딩 다음 단계
        composable(Destinations.PERMISSIONS) {
            OnboardingPermissionScreen(
                onContinue = {
                    navController.navigate(Destinations.HOME) {
                        popUpTo(Destinations.PERMISSIONS) { inclusive = true }
                    }
                }
            )
        }

        composable(Destinations.HOME) {
            val vm: HomeViewModel = viewModel(factory = viewModelFactory { HomeViewModel(container) })
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
                onOpenTemplates = { navController.navigate(Destinations.TEMPLATE_LIST) },
                onOpenAiMessage = { navController.navigate(Destinations.AI_MESSAGE) },
                onOpenStyleLearning = { navController.navigate(Destinations.STYLE_LEARNING) },
                onOpenSettings = { navController.navigate(Destinations.SETTINGS) },
                onOpenSearch = { navController.navigate(Destinations.SEARCH) },
                onOpenSettlement = { navController.navigate(Destinations.SETTLEMENT) },
                onOpenRecurringDue = { navController.navigate(Destinations.RECURRING_DUE) },
                onOpenScheduleReminder = { navController.navigate(Destinations.SCHEDULE_REMINDER) },
                onOpenEstimateFollowup = { navController.navigate(Destinations.ESTIMATE_FOLLOWUP) }
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
                onOpenCustomer = { id -> navController.navigate(Destinations.customerDetail(id)) },
                onAddSchedule = { navController.navigate(Destinations.SCHEDULE_ADD) }
            )
        }

        composable(Destinations.SCHEDULE_ADD) {
            val vm: ScheduleAddViewModel = viewModel(factory = viewModelFactory { ScheduleAddViewModel(container) })
            ScheduleAddScreen(
                viewModel = vm,
                onDone = { navController.popBackStack() },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Destinations.SETTLEMENT) {
            val vm: SettlementViewModel = viewModel(factory = viewModelFactory { SettlementViewModel(container) })
            SettlementScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() },
                onOpenCustomer = { id -> navController.navigate(Destinations.customerDetail(id)) }
            )
        }

        composable(Destinations.STATS) {
            val vm: ReportViewModel = viewModel(factory = viewModelFactory { ReportViewModel(container) })
            StatsScreen(viewModel = vm)
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
                onOpenTemplates = { navController.navigate(Destinations.TEMPLATE_LIST) },
                onOpenPricingItems = { navController.navigate(Destinations.PRICING_ITEMS) },
                onOpenBusinessInfo = { navController.navigate(Destinations.BUSINESS_INFO) },
                onOpenNotebook = { navController.navigate(Destinations.NOTEBOOK) },
                onOpenReport = { navController.navigate(Destinations.REPORT) },
                onOpenTradeSelect = { navController.navigate(Destinations.TRADE_SELECT) },
                onOpenRecurring = { navController.navigate(Destinations.RECURRING) }
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
            NotebookScreen(viewModel = vm, onBack = { navController.popBackStack() })
        }

        composable(Destinations.REPORT) {
            val vm: ReportViewModel = viewModel(factory = viewModelFactory { ReportViewModel(container) })
            ReportScreen(viewModel = vm, onBack = { navController.popBackStack() })
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
                onBack = { navController.popBackStack() }
            )
        }
    }
}
