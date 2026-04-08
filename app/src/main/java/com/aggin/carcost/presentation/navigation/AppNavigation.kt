package com.aggin.carcost.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.aggin.carcost.data.local.settings.SettingsManager
import com.aggin.carcost.data.remote.repository.SupabaseAuthRepository
import com.aggin.carcost.presentation.screens.onboarding.OnboardingScreen
import com.aggin.carcost.presentation.screens.add_car.AddCarScreen
import com.aggin.carcost.presentation.screens.add_expense.AddExpenseScreen
import com.aggin.carcost.presentation.screens.analytics.EnhancedAnalyticsScreen
import com.aggin.carcost.presentation.screens.auth.LoginScreen
import com.aggin.carcost.presentation.screens.auth.RegisterScreen
import com.aggin.carcost.presentation.screens.car_detail.CarDetailScreen
import com.aggin.carcost.presentation.screens.edit_car.EditCarScreen
import com.aggin.carcost.presentation.screens.edit_expense.EditExpenseScreen
import com.aggin.carcost.presentation.screens.export.ExportScreen
import com.aggin.carcost.presentation.screens.home.HomeScreen
import com.aggin.carcost.presentation.screens.map.MapScreen
import com.aggin.carcost.presentation.screens.profile.ProfileScreen
import com.aggin.carcost.presentation.screens.receipt_scan.ReceiptScanScreen
import com.aggin.carcost.presentation.screens.categories.CategoryManagementScreen
import com.aggin.carcost.presentation.screens.bug_report.BugReportScreen
import com.aggin.carcost.presentation.screens.planned_expenses.PlannedExpensesScreen
import com.aggin.carcost.presentation.screens.planned_expenses.AddPlannedExpenseScreen
import com.aggin.carcost.presentation.screens.planned_expenses.EditPlannedExpenseScreen
import com.aggin.carcost.presentation.screens.documents.DocumentsScreen
import com.aggin.carcost.presentation.screens.compare.CompareScreen
import com.aggin.carcost.presentation.screens.search.SearchScreen
import com.aggin.carcost.presentation.screens.budget.BudgetScreen
import com.aggin.carcost.presentation.screens.maintenance_dashboard.MaintenanceDashboardScreen
import com.aggin.carcost.presentation.screens.tco.TcoScreen
import com.aggin.carcost.presentation.screens.service_timeline.ServiceTimelineScreen
import com.aggin.carcost.presentation.screens.ai_insights.AiInsightsScreen
import com.aggin.carcost.presentation.screens.fuel_prices.FuelPricesScreen
import com.aggin.carcost.presentation.screens.vin_decoder.VinDecoderScreen
import com.aggin.carcost.presentation.screens.achievements.AchievementsScreen
import com.aggin.carcost.presentation.screens.goals.GoalsScreen
import com.aggin.carcost.presentation.screens.car_members.CarMembersScreen
import com.aggin.carcost.presentation.screens.car_members.AcceptInviteScreen
import com.aggin.carcost.presentation.screens.chat.ChatScreen
import com.aggin.carcost.presentation.screens.chat.ChatsListScreen
import com.aggin.carcost.presentation.screens.gps_trip.GpsTripScreen
import com.aggin.carcost.presentation.screens.gps_trip.TripMapScreen

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object Register : Screen("register")
    object Home : Screen("home")
    object Profile : Screen("profile")
    object AddCar : Screen("add_car")

    object CarDetail : Screen("car_detail/{carId}") {
        fun createRoute(carId: String) = "car_detail/$carId"
    }

    object EditCar : Screen("edit_car/{carId}") {
        fun createRoute(carId: String) = "edit_car/$carId"
    }

    object AddExpense : Screen("add_expense/{carId}?plannedId={plannedId}") {
        fun createRoute(carId: String, plannedId: String? = null): String {
            return if (plannedId != null) {
                "add_expense/$carId?plannedId=$plannedId"
            } else {
                "add_expense/$carId"
            }
        }
    }

    object EditExpense : Screen("edit_expense/{carId}/{expenseId}") {
        fun createRoute(carId: String, expenseId: String) = "edit_expense/$carId/$expenseId"
    }

    object Analytics : Screen("analytics/{carId}") {
        fun createRoute(carId: String) = "analytics/$carId"
    }

    object Map : Screen("map/{carId}") {
        fun createRoute(carId: String) = "map/$carId"
    }

    object Export : Screen("export/{carId}") {
        fun createRoute(carId: String) = "export/$carId"
    }

    object ReceiptScan : Screen("receipt_scan/{carId}") {
        fun createRoute(carId: String) = "receipt_scan/$carId"
    }

    object CategoryManagement : Screen("category_management")

    object BugReport : Screen("bug_report")

    // ✅ НОВЫЕ МАРШРУТЫ ДЛЯ ПЛАНОВ ПОКУПОК
    object PlannedExpenses : Screen("planned_expenses/{carId}") {
        fun createRoute(carId: String) = "planned_expenses/$carId"
    }

    object AddPlannedExpense : Screen("add_planned_expense/{carId}") {
        fun createRoute(carId: String) = "add_planned_expense/$carId"
    }

    object EditPlannedExpense : Screen("edit_planned_expense/{carId}/{plannedId}") {
        fun createRoute(carId: String, plannedId: String) = "edit_planned_expense/$carId/$plannedId"
    }

    object Documents : Screen("documents/{carId}") {
        fun createRoute(carId: String) = "documents/$carId"
    }

    object Compare : Screen("compare")

    object Search : Screen("search")

    object Onboarding : Screen("onboarding")

    object Budget : Screen("budget/{carId}") {
        fun createRoute(carId: String) = "budget/$carId"
    }

    object MaintenanceDashboard : Screen("maintenance_dashboard")

    object Tco : Screen("tco/{carId}") {
        fun createRoute(carId: String) = "tco/$carId"
    }

    object ServiceTimeline : Screen("service_timeline/{carId}") {
        fun createRoute(carId: String) = "service_timeline/$carId"
    }

    object AiInsights : Screen("ai_insights/{carId}") {
        fun createRoute(carId: String) = "ai_insights/$carId"
    }

    object FuelPrices : Screen("fuel_prices")

    object VinDecoder : Screen("vin_decoder/{carId}") {
        fun createRoute(carId: String) = "vin_decoder/$carId"
    }

    object Achievements : Screen("achievements")

    object Goals : Screen("goals/{carId}") {
        fun createRoute(carId: String) = "goals/$carId"
    }

    object CarMembers : Screen("car_members/{carId}") {
        fun createRoute(carId: String) = "car_members/$carId"
    }

    object GpsTrip : Screen("gps_trip/{carId}") {
        fun createRoute(carId: String) = "gps_trip/$carId"
    }

    object AcceptInvite : Screen("accept_invite/{token}") {
        fun createRoute(token: String) = "accept_invite/$token"
    }

    object TripMap : Screen("trip_map/{tripId}") {
        fun createRoute(tripId: String) = "trip_map/$tripId"
    }

    object Chat : Screen("chat/{carId}") {
        fun createRoute(carId: String) = "chat/$carId"
    }

    object ChatsList : Screen("chats_list")
}

@Composable
fun AppNavigation(
    navController: NavHostController = rememberNavController(),
    pendingInviteToken: String? = null,
    pendingNavRoute: String? = null,
    onNavRouteConsumed: () -> Unit = {}
) {
    val context = LocalContext.current
    val settingsManager = remember { SettingsManager(context) }
    // null = ещё не прочитано, true/false = прочитано
    val onboardingDone by settingsManager.onboardingDoneFlow.collectAsState(initial = null)

    val supabaseAuth = SupabaseAuthRepository()
    val isLoggedIn = supabaseAuth.isUserLoggedIn()

    // Ждём пока DataStore вернёт значение
    val done = onboardingDone ?: return

    // Если пришёл deep link с токеном и пользователь залогинен — сразу на экран принятия
    val startDestination = when {
        !done -> Screen.Onboarding.route
        isLoggedIn && pendingInviteToken != null -> Screen.AcceptInvite.createRoute(pendingInviteToken)
        isLoggedIn -> Screen.Home.route
        else -> Screen.Login.route
    }

    // Handle deep link from notification tap (after NavHost is initialized)
    LaunchedEffect(pendingNavRoute) {
        val route = pendingNavRoute ?: return@LaunchedEffect
        if (!isLoggedIn) return@LaunchedEffect
        kotlinx.coroutines.delay(200) // wait for NavHost to be ready
        try {
            navController.navigate(route) { launchSingleTop = true }
        } catch (_: Exception) { }
        onNavRouteConsumed()
    }

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        // Онбординг
        composable(Screen.Onboarding.route) {
            OnboardingScreen(navController = navController)
        }

        // Аутентификация
        composable(Screen.Login.route) {
            LoginScreen(navController = navController)
        }

        composable(Screen.Register.route) {
            RegisterScreen(navController = navController)
        }

        // Главные экраны
        composable(Screen.Home.route) {
            HomeScreen(navController = navController)
        }

        composable(Screen.Profile.route) {
            ProfileScreen(navController = navController)
        }

        composable(Screen.AddCar.route) {
            AddCarScreen(navController = navController)
        }

        // Детали автомобиля
        composable(
            route = Screen.CarDetail.route,
            arguments = listOf(navArgument("carId") { type = NavType.StringType })
        ) { backStackEntry ->
            val carId = backStackEntry.arguments?.getString("carId") ?: ""
            CarDetailScreen(carId = carId, navController = navController)
        }

        // Карта
        composable(
            route = Screen.Map.route,
            arguments = listOf(navArgument("carId") { type = NavType.StringType })
        ) { backStackEntry ->
            val carId = backStackEntry.arguments?.getString("carId") ?: ""
            MapScreen(carId = carId, navController = navController)
        }

        // Редактирование автомобиля
        composable(
            route = Screen.EditCar.route,
            arguments = listOf(navArgument("carId") { type = NavType.StringType })
        ) { backStackEntry ->
            val carId = backStackEntry.arguments?.getString("carId") ?: ""
            EditCarScreen(carId = carId, navController = navController)
        }

        // Добавление расхода
        composable(
            route = Screen.AddExpense.route,
            arguments = listOf(
                navArgument("carId") { type = NavType.StringType },
                navArgument("plannedId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val carId = backStackEntry.arguments?.getString("carId") ?: ""
            val plannedId = backStackEntry.arguments?.getString("plannedId")
            AddExpenseScreen(
                carId = carId,
                plannedId = plannedId,
                navController = navController
            )
        }

        // Редактирование расхода
        composable(
            route = Screen.EditExpense.route,
            arguments = listOf(
                navArgument("carId") { type = NavType.StringType },
                navArgument("expenseId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val carId = backStackEntry.arguments?.getString("carId") ?: ""
            val expenseId = backStackEntry.arguments?.getString("expenseId") ?: ""
            EditExpenseScreen(
                carId = carId,
                expenseId = expenseId,
                navController = navController
            )
        }

        // Аналитика
        composable(
            route = Screen.Analytics.route,
            arguments = listOf(navArgument("carId") { type = NavType.StringType })
        ) { backStackEntry ->
            val carId = backStackEntry.arguments?.getString("carId") ?: ""
            EnhancedAnalyticsScreen(carId = carId, navController = navController)
        }

        // Экспорт
        composable(
            route = Screen.Export.route,
            arguments = listOf(navArgument("carId") { type = NavType.StringType })
        ) { backStackEntry ->
            val carId = backStackEntry.arguments?.getString("carId") ?: ""
            ExportScreen(carId = carId, navController = navController)
        }

        // Сканирование чека
        composable(
            route = Screen.ReceiptScan.route,
            arguments = listOf(navArgument("carId") { type = NavType.StringType })
        ) { backStackEntry ->
            val carId = backStackEntry.arguments?.getString("carId") ?: ""
            ReceiptScanScreen(
                carId = carId,
                navController = navController
            )
        }

        // Управление категориями
        composable(Screen.CategoryManagement.route) {
            CategoryManagementScreen(navController = navController)
        }

        // Отчет об ошибках
        composable(Screen.BugReport.route) {
            BugReportScreen(navController = navController)
        }

        // ✅ НОВЫЕ МАРШРУТЫ ДЛЯ ПЛАНОВ ПОКУПОК

        // Список запланированных покупок
        composable(
            route = Screen.PlannedExpenses.route,
            arguments = listOf(navArgument("carId") { type = NavType.StringType })
        ) { backStackEntry ->
            val carId = backStackEntry.arguments?.getString("carId") ?: ""
            PlannedExpensesScreen(carId = carId, navController = navController)
        }

        // Добавление запланированной покупки
        composable(
            route = Screen.AddPlannedExpense.route,
            arguments = listOf(navArgument("carId") { type = NavType.StringType })
        ) { backStackEntry ->
            val carId = backStackEntry.arguments?.getString("carId") ?: ""
            AddPlannedExpenseScreen(carId = carId, navController = navController)
        }

        // Сравнение авто
        composable(Screen.Compare.route) {
            CompareScreen(navController = navController)
        }

        // Глобальный поиск
        composable(Screen.Search.route) {
            SearchScreen(navController = navController)
        }

        // Дашборд ТО
        composable(Screen.MaintenanceDashboard.route) {
            MaintenanceDashboardScreen(navController = navController)
        }

        // Таймлайн обслуживания
        composable(
            route = Screen.ServiceTimeline.route,
            arguments = listOf(navArgument("carId") { type = NavType.StringType })
        ) { backStackEntry ->
            val carId = backStackEntry.arguments?.getString("carId") ?: ""
            ServiceTimelineScreen(carId = carId, navController = navController)
        }

        // TCO — стоимость владения
        composable(
            route = Screen.Tco.route,
            arguments = listOf(navArgument("carId") { type = NavType.StringType })
        ) { backStackEntry ->
            val carId = backStackEntry.arguments?.getString("carId") ?: ""
            TcoScreen(carId = carId, navController = navController)
        }

        // Бюджет по категориям
        composable(
            route = Screen.Budget.route,
            arguments = listOf(navArgument("carId") { type = NavType.StringType })
        ) { backStackEntry ->
            val carId = backStackEntry.arguments?.getString("carId") ?: ""
            BudgetScreen(carId = carId, navController = navController)
        }

        // Хранилище документов
        composable(
            route = Screen.Documents.route,
            arguments = listOf(navArgument("carId") { type = NavType.StringType })
        ) { backStackEntry ->
            val carId = backStackEntry.arguments?.getString("carId") ?: ""
            DocumentsScreen(carId = carId, navController = navController)
        }

        // Цены топлива
        composable(Screen.FuelPrices.route) {
            FuelPricesScreen(navController = navController)
        }

        // GPS Поездки
        composable(
            route = Screen.GpsTrip.route,
            arguments = listOf(navArgument("carId") { type = NavType.StringType })
        ) { backStackEntry ->
            val carId = backStackEntry.arguments?.getString("carId") ?: ""
            GpsTripScreen(carId = carId, navController = navController)
        }

        // Участники авто
        composable(
            route = Screen.CarMembers.route,
            arguments = listOf(navArgument("carId") { type = NavType.StringType })
        ) { backStackEntry ->
            val carId = backStackEntry.arguments?.getString("carId") ?: ""
            CarMembersScreen(carId = carId, navController = navController)
        }

        // Достижения
        composable(Screen.Achievements.route) {
            AchievementsScreen(navController = navController)
        }

        // Цели накопления
        composable(
            route = Screen.Goals.route,
            arguments = listOf(navArgument("carId") { type = NavType.StringType })
        ) { backStackEntry ->
            val carId = backStackEntry.arguments?.getString("carId") ?: ""
            GoalsScreen(carId = carId, navController = navController)
        }

        // VIN-декодер
        composable(
            route = Screen.VinDecoder.route,
            arguments = listOf(navArgument("carId") { type = NavType.StringType })
        ) { backStackEntry ->
            val carId = backStackEntry.arguments?.getString("carId") ?: ""
            VinDecoderScreen(carId = carId, navController = navController)
        }

        // AI-советы
        composable(
            route = Screen.AiInsights.route,
            arguments = listOf(navArgument("carId") { type = NavType.StringType })
        ) { backStackEntry ->
            val carId = backStackEntry.arguments?.getString("carId") ?: ""
            AiInsightsScreen(carId = carId, navController = navController)
        }

        // Редактирование запланированной покупки
        composable(
            route = Screen.EditPlannedExpense.route,
            arguments = listOf(
                navArgument("carId") { type = NavType.StringType },
                navArgument("plannedId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val carId = backStackEntry.arguments?.getString("carId") ?: ""
            val plannedId = backStackEntry.arguments?.getString("plannedId") ?: ""
            EditPlannedExpenseScreen(
                carId = carId,
                plannedId = plannedId,
                navController = navController
            )
        }

        // Принятие приглашения по deep link
        composable(
            route = Screen.AcceptInvite.route,
            arguments = listOf(navArgument("token") { type = NavType.StringType })
        ) { backStackEntry ->
            val token = backStackEntry.arguments?.getString("token") ?: ""
            AcceptInviteScreen(token = token, navController = navController)
        }

        // Карта маршрута GPS-поездки
        composable(
            route = Screen.TripMap.route,
            arguments = listOf(navArgument("tripId") { type = NavType.StringType })
        ) { backStackEntry ->
            val tripId = backStackEntry.arguments?.getString("tripId") ?: ""
            TripMapScreen(tripId = tripId, navController = navController)
        }

        // Чат участников авто
        composable(
            route = Screen.Chat.route,
            arguments = listOf(navArgument("carId") { type = NavType.StringType })
        ) { backStackEntry ->
            val carId = backStackEntry.arguments?.getString("carId") ?: ""
            ChatScreen(carId = carId, navController = navController)
        }

        // Список всех чатов (из профиля)
        composable(Screen.ChatsList.route) {
            ChatsListScreen(navController = navController)
        }
    }
}