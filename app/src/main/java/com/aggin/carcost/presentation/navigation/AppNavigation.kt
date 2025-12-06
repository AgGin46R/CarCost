package com.aggin.carcost.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.aggin.carcost.data.remote.repository.SupabaseAuthRepository
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
}

@Composable
fun AppNavigation(
    navController: NavHostController = rememberNavController()
) {
    val supabaseAuth = SupabaseAuthRepository()
    val isLoggedIn = supabaseAuth.isUserLoggedIn()
    val startDestination = if (isLoggedIn) Screen.Home.route else Screen.Login.route

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
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
    }
}