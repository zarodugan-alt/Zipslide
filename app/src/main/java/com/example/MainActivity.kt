package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.KeyEvent
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.data.ScanWorker
import androidx.navigation.navArgument
import com.example.data.AppSettings
import com.example.design.ZipSlideAppTheme
import com.example.design.ZipSlideTheme
import com.example.ui.browser.BrowserScreen
import com.example.ui.browser.BrowserViewModel
import com.example.ui.contents.ZipContentsScreen
import com.example.ui.contents.ZipContentsViewModel
import com.example.ui.onboarding.OnboardingScreen
import com.example.ui.onboarding.OnboardingEvent
import com.example.ui.onboarding.OnboardingViewModel
import com.example.ui.settings.SettingsScreen
import com.example.ui.settings.SettingsViewModel
import com.example.ui.slideshow.SlideshowScreen
import com.example.ui.slideshow.SlideshowViewModel
import com.example.ui.storage.StorageOverviewScreen
import com.example.ui.storage.StorageOverviewViewModel
import com.example.ui.storage.VolumeDiagnosticsScreen
import com.example.ui.storage.VolumeDiagnosticsViewModel
import com.example.util.VolumeKey
import com.example.util.VolumeKeyDispatcher
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
        val volumeRepo = app.volumeRepository

        setContent {
            val browserViewModel: BrowserViewModel = viewModel()
            val browserState by browserViewModel.state.collectAsStateWithLifecycle()
            val settings = browserState.settings
            val zips = browserState.items
            val isScanning = browserState.scanProgress is com.example.data.ScanProgress.Scanning

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
                            val onboardingViewModel: OnboardingViewModel = viewModel()
                            OnboardingScreen(
                                onPermissionGranted = {
                                    permissionGranted = true
                                    onboardingViewModel.onEvent(OnboardingEvent.AccessGranted)
                                    navController.navigate("browser") {
                                        popUpTo("onboarding") { inclusive = true }
                                    }
                                },
                                onChooseFolderInstead = { uri ->
                                    permissionGranted = true
                                    onboardingViewModel.onEvent(OnboardingEvent.FolderChosen(uri))
                                    navController.navigate("browser") {
                                        popUpTo("onboarding") { inclusive = true }
                                    }
                                }
                            )
                        }

                        composable("browser") {
                            BrowserScreen(
                                fallbackZips = zips,
                                fallbackIsScanning = isScanning,
                                fallbackSettings = settings,
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
                                },
                                state = browserState,
                                onEvent = browserViewModel::onEvent
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
                            val slideshowViewModel: SlideshowViewModel = viewModel()
                            val slideshowState by slideshowViewModel.state.collectAsStateWithLifecycle()
                            SlideshowScreen(
                                zipPath = path,
                                initialFrameIndex = startFrame,
                                settings = slideshowState.settings,
                                zipRepository = zipRepo,
                                onBack = { navController.popBackStack() },
                                state = slideshowState,
                                onEvent = slideshowViewModel::onEvent
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
                            val contentsViewModel: ZipContentsViewModel = viewModel()
                            val contentsState by contentsViewModel.state.collectAsStateWithLifecycle()
                            ZipContentsScreen(
                                zipPath = path,
                                zipRepository = zipRepo,
                                onNavigateToSlideshow = { targetPath, frameIndex ->
                                    val enc = URLEncoder.encode(targetPath, StandardCharsets.UTF_8.toString())
                                    navController.navigate("slideshow/$enc/$frameIndex")
                                },
                                onBack = { navController.popBackStack() },
                                state = contentsState,
                                onEvent = contentsViewModel::onEvent
                            )
                        }

                        composable("storage_overview") {
                            val storageViewModel: StorageOverviewViewModel = viewModel()
                            val storageState by storageViewModel.state.collectAsStateWithLifecycle()
                            StorageOverviewScreen(
                                volumeRepository = volumeRepo,
                                zips = storageState.zips,
                                onBack = { navController.popBackStack() },
                                volumesFromState = storageState.volumes
                            )
                        }

                        composable("diagnostics") {
                            val diagnosticsViewModel: VolumeDiagnosticsViewModel = viewModel()
                            val diagnosticsState by diagnosticsViewModel.state.collectAsStateWithLifecycle()
                            VolumeDiagnosticsScreen(
                                volumeRepository = volumeRepo,
                                zips = diagnosticsState.zips,
                                onBack = { navController.popBackStack() },
                                volumesFromState = diagnosticsState.volumes
                            )
                        }

                        composable("settings") {
                            val settingsViewModel: SettingsViewModel = viewModel()
                            val settingsState by settingsViewModel.state.collectAsStateWithLifecycle()
                            SettingsScreen(
                                settings = settingsState.settings,
                                onNavigateToDiagnostics = {
                                    navController.navigate("diagnostics")
                                },
                                onBack = { navController.popBackStack() },
                                cacheSizeBytes = settingsState.thumbnailCacheBytes,
                                onEvent = settingsViewModel::onEvent
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * Hardware volume keys drive the viewer when it is on screen: volume down moves forward,
     * volume up moves back, and holding either one auto-repeats for fast jumping. Both the down
     * and the up event are swallowed so the system volume panel never appears mid-slideshow.
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val key = keyCode.toVolumeKey()
        if (key != null && VolumeKeyDispatcher.dispatch(key, (event?.repeatCount ?: 0) > 0)) return true
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode.toVolumeKey() != null && VolumeKeyDispatcher.isActive) return true
        return super.onKeyUp(keyCode, event)
    }

    private fun Int.toVolumeKey(): VolumeKey? = when (this) {
        KeyEvent.KEYCODE_VOLUME_DOWN -> VolumeKey.DOWN
        KeyEvent.KEYCODE_VOLUME_UP -> VolumeKey.UP
        else -> null
    }

    override fun onResume() {
        super.onResume()
        if (hasPermission()) {
            WorkManager.getInstance(this).enqueueUniqueWork(
                "scan",
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<ScanWorker>().build()
            )
        }
    }
}
