package com.warrantykeeper.presentation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.warrantykeeper.data.local.prefs.PreferencesManager
import com.warrantykeeper.presentation.camera.CameraScreen
import com.warrantykeeper.presentation.details.DocumentDetailScreen
import com.warrantykeeper.presentation.login.LoginScreen
import com.warrantykeeper.presentation.main.MainScreen
import com.warrantykeeper.presentation.settings.SettingsScreen
import com.warrantykeeper.ui.theme.WarrantyKeeperTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var preferencesManager: PreferencesManager

    // Launcher для запроса разрешения POST_NOTIFICATIONS (Android 13+)
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            // Разрешение получено или отклонено — ничего критичного, просто логируем
            android.util.Log.d("MainActivity", "POST_NOTIFICATIONS granted: $isGranted")
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Запрашиваем разрешение на уведомления при первом запуске (Android 13+)
        requestNotificationPermissionIfNeeded()

        setContent {
            WarrantyKeeperTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val isLoggedIn by preferencesManager.isLoggedIn.collectAsState(initial = false)
                    AppNavigation(isLoggedIn = isLoggedIn)
                }
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    // Уже есть — хорошо
                }
                else -> {
                    // Запрашиваем — система покажет диалог автоматически
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
        // На Android < 13 разрешение не требуется — уведомления работают автоматически
    }
}

@Composable
fun AppNavigation(
    isLoggedIn: Boolean,
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = if (isLoggedIn) Screen.Main.route else Screen.Login.route
    ) {
        composable(Screen.Login.route) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Screen.Main.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Main.route) {
            MainScreen(
                onNavigateToCamera = {
                    navController.navigate(Screen.Camera.route)
                },
                onNavigateToDocument = { documentId ->
                    navController.navigate(Screen.DocumentDetails.createRoute(documentId))
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                }
            )
        }

        composable(Screen.Camera.route) {
            CameraScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onDocumentSaved = { documentId ->
                    navController.popBackStack()
                    navController.navigate(Screen.DocumentDetails.createRoute(documentId))
                }
            )
        }

        composable(
            route = Screen.DocumentDetails.route,
            arguments = listOf(navArgument("documentId") { type = NavType.LongType })
        ) {
            DocumentDetailScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onLogout = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
    }
}

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object Main : Screen("main")
    object Camera : Screen("camera")
    object Settings : Screen("settings")
    object DocumentDetails : Screen("document/{documentId}") {
        fun createRoute(documentId: Long) = "document/$documentId"
    }
}
