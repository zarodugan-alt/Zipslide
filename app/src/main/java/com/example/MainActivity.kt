package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.AppSettings
import com.example.design.ZipSlideAppTheme
import com.example.design.ZipSlideTheme
import com.example.ui.browser.BrowserScreen
import com.example.ui.browser.BrowserViewModel
import com.example.ui.contents.ZipContentsScreen
import com.example.ui.onboarding.OnboardingScreen
import com.example.ui.settings.SettingsScreen
import com.example.ui.slideshow.SlideshowScreen
import com.example.ui.storage.StorageOverviewScreen
import com.example.ui.storage.VolumeDiagnosticsScreen
import kotlinx.coroutines.launch
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class MainActivity : ComponentActivity() {

    private fun hasPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as ZipSlideApplication
        val zipRepo = app.zipRepository
        val settingsRepo = app.settingsRepository
        val volumeRepo = app.volumeRepository

        setContent {
            val browserViewModel: BrowserViewModel = viewModel()
            val browserState by browserViewModel.state.collectAsStateWithLifecycle()
            val settings = browserState.settings
            val zips = browserState.items
            val isScanning = browserState.scanProgress is com.example.data.ScanProgress.Scanning

            val scope = rememberCoroutineScope()
            val navController = rememberNavController()

            var permissionGranted by remember { mutableStateOf(hasPermission()) }

            LaunchedEffect(Unit) {
                permissionGranted = hasPermission()
            }

            val startDestination = if (permissionGranted || settings.onboardingCompleted || settings.customScanFolderUri != null) {
                "browser"
            } else {
                "onboarding"
            }

            ZipSlideAppTheme(themeSetting = settings.theme) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(ZipSlideTheme.colors.bg)
                ) {
                    NavHost(
                        navController = navController,
                        startDestination = startDestination
                    ) {
                        composable("onboarding") {
                            OnboardingScreen(
                                onPermissionGranted = {
                                    permissionGranted = true
                                    scope.launch {
                                        settingsRepo.setOnboardingCompleted(true)
                                        zipRepo.triggerRescan()
                                    }
                                    navController.navigate("browser") {
                                        popUpTo("onboarding") { inclusive = true }
                                    }
                                },
                                onChooseFolderInstead = { uri ->
                                    permissionGranted = true
                                    scope.launch {
                                        settingsRepo.updateCustomScanFolder(uri.toString())
                                        settingsRepo.setOnboardingCompleted(true)
                                        zipRepo.triggerRescan()
                                    }
                                    navController.navigate("browser") {
                                        popUpTo("onboarding") { inclusive = true }
                                    }
                                }
                            )
                        }

                        composable("browser") {
                            BrowserScreen(
                                zips = zips,
                                isScanning = isScanning,
                                settings = settings,
                                volumeRepository = volumeRepo,
                                zipRepository = zipRepo,
                                onNavigateToSlideshow = { path, startFrame ->
                                    val encoded = URLEncoder.encode(path, StandardCharsets.UTF_8.toString())
                                    navController.navigate("slideshow/$encoded/$startFrame")
                                },
                                onNavigateToContents = { path ->
                                    val encoded = URLEncoder.encode(path, StandardCharsets.UTF_8.toString())
                                    navController.navigate("contents/$encoded")
                                },
                                onNavigateToSettings = {
                                    navController.navigate("settings")
                                },
                                onNavigateToStorageOverview = {
                                    navController.navigate("storage_overview")
                                },
                                onSelectFolderToScan = {
                                    navController.navigate("settings")
                                }
                            )
                        }

                        composable(
                            route = "slideshow/{encodedPath}/{startFrame}",
                            arguments = listOf(
                                navArgument("encodedPath") { type = NavType.StringType },
                                navArgument("startFrame") { type = NavType.IntType }
                            )
                        ) { backStackEntry ->
                            val encoded = backStackEntry.arguments?.getString("encodedPath") ?: ""
                            val path = URLDecoder.decode(encoded, StandardCharsets.UTF_8.toString())
                            val startFrame = backStackEntry.arguments?.getInt("startFrame") ?: 0
                            SlideshowScreen(
                                zipPath = path,
                                initialFrameIndex = startFrame,
                                settings = settings,
                                zipRepository = zipRepo,
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable(
                            route = "contents/{encodedPath}",
                            arguments = listOf(
                                navArgument("encodedPath") { type = NavType.StringType }
                            )
                        ) { backStackEntry ->
                            val encoded = backStackEntry.arguments?.getString("encodedPath") ?: ""
                            val path = URLDecoder.decode(encoded, StandardCharsets.UTF_8.toString())
                            ZipContentsScreen(
                                zipPath = path,
                                zipRepository = zipRepo,
                                onNavigateToSlideshow = { targetPath, frameIndex ->
                                    val enc = URLEncoder.encode(targetPath, StandardCharsets.UTF_8.toString())
                                    navController.navigate("slideshow/$enc/$frameIndex")
                                },
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable("storage_overview") {
                            StorageOverviewScreen(
                                volumeRepository = volumeRepo,
                                zips = zips,
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable("diagnostics") {
                            VolumeDiagnosticsScreen(
                                volumeRepository = volumeRepo,
                                zips = zips,
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable("settings") {
                            SettingsScreen(
                                settings = settings,
                                settingsRepository = settingsRepo,
                                zipRepository = zipRepo,
                                onNavigateToDiagnostics = {
                                    navController.navigate("diagnostics")
                                },
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (hasPermission()) {
            (application as? ZipSlideApplication)?.zipRepository?.triggerRescan()
        }
    }
}
