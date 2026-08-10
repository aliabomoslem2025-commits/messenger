package com.matrixmessenger.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.matrixmessenger.ui.screens.home.HomeScreen
import com.matrixmessenger.ui.screens.home.HomeViewModel
import com.matrixmessenger.ui.screens.login.LoginScreen
import com.matrixmessenger.ui.screens.login.LoginViewModel
import com.matrixmessenger.ui.screens.chat.ChatScreen
import com.matrixmessenger.ui.screens.chat.ChatViewModel
import com.matrixmessenger.ui.screens.profile.ProfileScreen
import com.matrixmessenger.ui.screens.profile.ProfileViewModel
import com.matrixmessenger.ui.screens.settings.SettingsScreen
import com.matrixmessenger.ui.screens.settings.SettingsViewModel

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object Home : Screen("home")
    object Chat : Screen("chat/{roomId}") {
        fun createRoute(roomId: String) = "chat/$roomId"
    }
    object Profile : Screen("profile")
    object Settings : Screen("settings")
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val loginViewModel: LoginViewModel = hiltViewModel()
    
    val authState by loginViewModel.authState.collectAsState(initial = null)
    
    val startDestination = if (authState != null) {
        Screen.Home.route
    } else {
        Screen.Login.route
    }
    
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Screen.Login.route) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                },
                onNavigateToRegister = { /* Handle registration */ }
            )
        }
        
        composable(Screen.Home.route) {
            val homeViewModel: HomeViewModel = hiltViewModel()
            HomeScreen(
                onRoomClick = { roomId ->
                    navController.navigate(Screen.Chat.createRoute(roomId))
                },
                onProfileClick = {
                    navController.navigate(Screen.Profile.route)
                },
                onSettingsClick = {
                    navController.navigate(Screen.Settings.route)
                },
                onLogout = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
        
        composable(
            route = Screen.Chat.route,
            arguments = listOf(
                navArgument("roomId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val roomId = backStackEntry.arguments?.getString("roomId") ?: return@composable
            val chatViewModel: ChatViewModel = hiltViewModel()
            ChatScreen(
                roomId = roomId,
                onBackClick = { navController.popBackStack() },
                onProfileClick = { userId -> /* Navigate to user profile */ }
            )
        }
        
        composable(Screen.Profile.route) {
            val profileViewModel: ProfileViewModel = hiltViewModel()
            ProfileScreen(
                onBackClick = { navController.popBackStack() },
                onSettingsClick = {
                    navController.navigate(Screen.Settings.route)
                }
            )
        }
        
        composable(Screen.Settings.route) {
            val settingsViewModel: SettingsViewModel = hiltViewModel()
            SettingsScreen(
                onBackClick = { navController.popBackStack() }
            )
        }
    }
}
