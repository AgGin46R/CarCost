package com.aggin.carcost.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.aggin.carcost.data.local.database.AppDatabase
import com.aggin.carcost.data.local.repository.AuthRepository
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

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object Register : Screen("register")
    object Home : Screen("home")
    object Profile : Screen("profile")
    object AddCar : Screen("add_car")

    object CarDetail : Screen("car_detail/{carId}") {
        fun createRoute(carId: Long) = "car_detail/$carId"
    }

    object EditCar : Screen("edit_car/{carId}") {
        fun createRoute(carId: Long) = "edit_car/$carId"
    }

    object AddExpense : Screen("add_expense/{carId}") {
        fun createRoute(carId: Long) = "add_expense/$carId"
    }


    object EditExpense : Screen("edit_expense/{carId}/{expenseId}") {
        fun createRoute(carId: Long, expenseId: Long) = "edit_expense/$carId/$expenseId"
    }

    object Analytics : Screen("analytics/{carId}") {
        fun createRoute(carId: Long) = "analytics/$carId"
    }

    object Map : Screen("map/{carId}") {
        fun createRoute(carId: Long) = "map/$carId"
    }

    object Export : Screen("export/{carId}") {
        fun createRoute(carId: Long) = "export/$carId"
    }

    object ReceiptScan : Screen("receipt_scan/{carId}") {
        fun createRoute(carId: Long) = "receipt_scan/$carId"
    }

    object CategoryManagement : Screen("category_management")
}

@Composable
fun AppNavigation(
    navController: NavHostController = rememberNavController()
) {
    val context = LocalContext.current
    val authRepository = AuthRepository(userDao = AppDatabase.getDatabase(context).userDao())
    val isLoggedIn = authRepository.isUserLoggedIn()
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
            val carId = backStackEntry.arguments?.getString("carId")?.toLongOrNull() ?: 0L
            CarDetailScreen(carId = carId, navController = navController)
        }

        // Карта
        composable(
            route = Screen.Map.route,
            arguments = listOf(navArgument("carId") { type = NavType.StringType })
        ) { backStackEntry ->
            val carId = backStackEntry.arguments?.getString("carId")?.toLongOrNull() ?: 0L
            MapScreen(carId = carId, navController = navController)
        }

        // Редактирование автомобиля
        composable(
            route = Screen.EditCar.route,
            arguments = listOf(navArgument("carId") { type = NavType.StringType })
        ) { backStackEntry ->
            val carId = backStackEntry.arguments?.getString("carId")?.toLongOrNull() ?: 0L
            EditCarScreen(carId = carId, navController = navController)
        }

        // Добавление расхода
        composable(
            route = Screen.AddExpense.route,
            arguments = listOf(navArgument("carId") { type = NavType.StringType })
        ) { backStackEntry ->
            val carId = backStackEntry.arguments?.getString("carId")?.toLongOrNull() ?: 0L
            AddExpenseScreen(carId = carId, navController = navController)
        }

        // РЕДАКТИРОВАНИЕ РАСХОДА (НОВЫЙ МАРШРУТ)
        composable(
            route = Screen.EditExpense.route,
            arguments = listOf(
                navArgument("carId") { type = NavType.StringType },
                navArgument("expenseId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val carId = backStackEntry.arguments?.getString("carId")?.toLongOrNull() ?: 0L
            val expenseId = backStackEntry.arguments?.getString("expenseId")?.toLongOrNull() ?: 0L
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
            val carId = backStackEntry.arguments?.getString("carId")?.toLongOrNull() ?: 0L
            EnhancedAnalyticsScreen(carId = carId, navController = navController)
        }

        // Экспорт
        composable(
            route = Screen.Export.route,
            arguments = listOf(navArgument("carId") { type = NavType.StringType })
        ) { backStackEntry ->
            val carId = backStackEntry.arguments?.getString("carId")?.toLongOrNull() ?: 0L
            ExportScreen(carId = carId, navController = navController)
        }

        // Сканирование чека
        composable(
            route = Screen.ReceiptScan.route,
            arguments = listOf(navArgument("carId") { type = NavType.StringType })
        ) { backStackEntry ->
            val carId = backStackEntry.arguments?.getString("carId")?.toLongOrNull() ?: 0L
            ReceiptScanScreen(
                carId = carId,
                navController = navController
            )
        }

        // Управление категориями
        composable(Screen.CategoryManagement.route) {
            CategoryManagementScreen(navController = navController)
        }
    }
}