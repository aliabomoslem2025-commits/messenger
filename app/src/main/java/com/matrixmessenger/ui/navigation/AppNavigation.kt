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
import com.matrixmessenger.feature.chatlist.presentation.HomeScreen
import com.matrixmessenger.feature.chatlist.presentation.HomeViewModel
import com.matrixmessenger.ui.screens.login.LoginScreen
import com.matrixmessenger.ui.screens.login.LoginViewModel
import com.matrixmessenger.feature.chat.presentation.ChatScreen
import com.matrixmessenger.feature.chat.presentation.ChatViewModel
import com.matrixmessenger.ui.screens.profile.ProfileScreen
import com.matrixmessenger.ui.screens.profile.ProfileViewModel
import com.matrixmessenger.ui.screens.settings.SettingsScreen
import com.matrixmessenger.ui.screens.settings.SettingsViewModel
import com.matrixmessenger.feature.search.presentation.screen.SearchScreen
import com.matrixmessenger.feature.search.presentation.viewModel.SearchViewModel
import com.matrixmessenger.feature.call.presentation.screen.CallScreen
import com.matrixmessenger.feature.call.presentation.viewModel.CallViewModel
import com.matrixmessenger.feature.message.presentation.renderer.MessageRenderer

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object Home : Screen("home")
    object Chat : Screen("chat/{roomId}") {
        fun createRoute(roomId: String) = "chat/$roomId"
    }
    object Profile : Screen("profile")
    object Settings : Screen("settings")
    object Search : Screen("search")
    object Call : Screen("call/{roomId}") {
        fun createRoute(roomId: String) = "call/$roomId"
    }
}

@Composable
fun AppNavigation(messageRenderer: MessageRenderer) {
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
            HomeScreen(
                onRoomClick = { roomId ->
                    navController.navigate(Screen.Chat.createRoute(roomId))
                },
                onProfileClick = {
                    navController.navigate(Screen.Profile.route)
                },
                onNewChatClick = {
                    navController.navigate(Screen.Search.route)
                }
            )
        }
        
        composable(
            route = Screen.Chat.route,
            arguments = listOf(
                navArgument("roomId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val roomId = backStackEntry.arguments?.getString("roomId") ?: ""
            ChatScreen(
                onBackClick = { navController.popBackStack() },
                onProfileClick = { /* Navigate to user profile */ },
                onCallClick = {
                    navController.navigate(Screen.Call.createRoute(roomId))
                },
                messageRenderer = messageRenderer
            )
        }

        composable(Screen.Search.route) {
            val searchViewModel: SearchViewModel = hiltViewModel()
            val state by searchViewModel.uiState.collectAsState()
            SearchScreen(
                state = state,
                onEvent = searchViewModel::onEvent,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.Call.route,
            arguments = listOf(
                navArgument("roomId") { type = NavType.StringType }
            )
        ) {
            CallScreen(
                onDismiss = { navController.popBackStack() }
            )
        }
        
        composable(Screen.Profile.route) {
            ProfileScreen(
                onBackClick = { navController.popBackStack() },
                onSettingsClick = {
                    navController.navigate(Screen.Settings.route)
                }
            )
        }
        
        composable(Screen.Settings.route) {
            SettingsScreen(
                onBackClick = { navController.popBackStack() }
            )
        }
    }
}
