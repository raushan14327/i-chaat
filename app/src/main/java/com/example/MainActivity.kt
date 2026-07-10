package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.navigation.navDeepLink
import com.example.data.model.User
import com.example.ui.auth.AuthViewModel
import com.example.ui.auth.LoginScreen
import com.example.ui.auth.RegisterScreen
import com.example.ui.chat.ChatListScreen
import com.example.ui.chat.ChatScreen
import com.example.ui.chat.ChatViewModel
import com.example.ui.profile.ProfileScreen
import com.example.ui.profile.UserProfileScreen
import com.example.ui.settings.SettingsScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    private val authViewModel: AuthViewModel by viewModels()
    private val chatViewModel: ChatViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        setContent {
            // Theme preference management locally
            var isDarkTheme by remember { mutableStateOf(false) }
            val systemDark = isSystemInDarkTheme()
            
            // Sync with system default initially
            LaunchedEffect(systemDark) {
                isDarkTheme = systemDark
            }

            val currentUser by authViewModel.currentUser.collectAsState()

            // Dynamic permission check for Android 13+ (POST_NOTIFICATIONS)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { _ -> }
                LaunchedEffect(Unit) {
                    permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                }
            }

            // Session warm-up whenever the current user changes
            LaunchedEffect(currentUser) {
                currentUser?.let { user ->
                    chatViewModel.initSession(user.userId)
                }
            }

            MyApplicationTheme(darkTheme = isDarkTheme) {
                val navController = rememberNavController()

                // Route navigator
                val startDestination = if (currentUser != null) {
                    // Warm up session
                    chatViewModel.initSession(currentUser!!.userId)
                    "chatList"
                } else {
                    "login"
                }

                NavHost(
                    navController = navController,
                    startDestination = startDestination,
                    modifier = Modifier.fillMaxSize()
                ) {
                    composable("login") {
                        LoginScreen(
                            viewModel = authViewModel,
                            onNavigateToRegister = {
                                navController.navigate("register")
                            },
                            onLoginSuccess = { user ->
                                chatViewModel.initSession(user.userId)
                                navController.navigate("chatList") {
                                    popUpTo("login") { inclusive = true }
                                }
                            }
                        )
                    }

                    composable("register") {
                        RegisterScreen(
                            viewModel = authViewModel,
                            onNavigateBack = {
                                navController.popBackStack()
                            },
                            onRegisterSuccess = { user ->
                                chatViewModel.initSession(user.userId)
                                navController.navigate("chatList") {
                                    popUpTo("login") { inclusive = true }
                                    popUpTo("register") { inclusive = true }
                                }
                            }
                        )
                    }

                    composable("chatList") {
                        ChatListScreen(
                            chatViewModel = chatViewModel,
                            onNavigateToChat = { otherId ->
                                navController.navigate("chat/$otherId")
                            },
                            onNavigateToProfile = {
                                navController.navigate("profile")
                            },
                            onNavigateToSettings = {
                                navController.navigate("settings")
                            }
                        )
                    }

                    composable(
                        route = "chat/{recipientId}",
                        arguments = listOf(navArgument("recipientId") { type = NavType.StringType }),
                        deepLinks = listOf(
                            navDeepLink {
                                uriPattern = "ichat://chat/{recipientId}"
                            },
                            navDeepLink {
                                uriPattern = "https://ichat.com/chat/{recipientId}"
                            }
                        )
                    ) { backStackEntry ->
                        val recipientId = backStackEntry.arguments?.getString("recipientId") ?: ""
                        ChatScreen(
                            chatViewModel = chatViewModel,
                            recipientId = recipientId,
                            onNavigateBack = {
                                navController.popBackStack()
                            },
                            onNavigateToUserProfile = { uId ->
                                navController.navigate("userProfile/$uId")
                            }
                        )
                    }

                    composable(
                        route = "userProfile/{userId}",
                        arguments = listOf(navArgument("userId") { type = NavType.StringType })
                    ) { backStackEntry ->
                        val uId = backStackEntry.arguments?.getString("userId") ?: ""
                        UserProfileScreen(
                            userId = uId,
                            chatViewModel = chatViewModel,
                            onNavigateBack = {
                                navController.popBackStack()
                            }
                        )
                    }

                    composable("profile") {
                        ProfileScreen(
                            authViewModel = authViewModel,
                            chatViewModel = chatViewModel,
                            onNavigateBack = {
                                navController.popBackStack()
                            }
                        )
                    }

                    composable("settings") {
                        SettingsScreen(
                            authViewModel = authViewModel,
                            isDarkTheme = isDarkTheme,
                            onToggleTheme = { isDarkTheme = it },
                            onNavigateBack = {
                                navController.popBackStack()
                            },
                            onLogout = {
                                navController.navigate("login") {
                                    popUpTo("chatList") { inclusive = true }
                                    popUpTo("settings") { inclusive = true }
                                    popUpTo("profile") { inclusive = true }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
