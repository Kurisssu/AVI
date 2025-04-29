package com.example.aviv1

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.aviv1.ui.screens.ChatScreen
import com.example.aviv1.ui.screens.ConversationsScreen
import com.example.aviv1.ui.screens.MainScreen
import com.example.aviv1.ui.screens.SettingsScreen
import com.example.aviv1.ui.theme.AVIV1Theme

class MainActivity : ComponentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        enableEdgeToEdge()
        setContent {
            AVIV1Theme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavHost()
                }
            }
        }
    }
}

// Definirea rutelor de navigare
sealed class AppScreens(val route: String) {
    object Main : AppScreens("main")
    object Chat : AppScreens("chat")
    object Conversations : AppScreens("conversations")
    object Settings : AppScreens("settings")
}

// Implementarea navigării
@Composable
fun AppNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    startDestination: String = AppScreens.Main.route
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        composable(route = AppScreens.Main.route) {
            MainScreen(
                onNavigateToChat = { navController.navigate(AppScreens.Chat.route) },
                onNavigateToSettings = { navController.navigate(AppScreens.Settings.route) }
            )
        }
        
        composable(route = AppScreens.Chat.route) {
            ChatScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToConversations = { navController.navigate(AppScreens.Conversations.route) }
            )
        }
        
        composable(route = AppScreens.Conversations.route) {
            ConversationsScreen(
                onNavigateBack = { navController.popBackStack() },
                onSelectConversation = { conversationId ->
                    navController.navigate(AppScreens.Chat.route)
                }
            )
        }
        
        composable(route = AppScreens.Settings.route) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}