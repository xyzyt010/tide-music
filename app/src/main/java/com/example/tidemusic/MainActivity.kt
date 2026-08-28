package com.example.tidemusic

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import com.example.tidemusic.di.ServiceLocator
import com.example.tidemusic.playback.ConnectionHolder
import com.example.tidemusic.theme.TideMusicTheme
import com.example.tidemusic.ui.AppShell
import com.example.tidemusic.ui.LocalMediaController
import com.example.tidemusic.ui.common.AppLoadingScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Single-activity host (spec Section 1). All screens are Compose destinations. */
class MainActivity : ComponentActivity() {

    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        checkManageAllFilesPermission()
        val hasCompletedInitialScan = getSharedPreferences("app_settings", MODE_PRIVATE)
            .getBoolean("has_completed_initial_scan", false)
        triggerLibraryScan(isInitial = !hasCompletedInitialScan)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Enforce IPv4-only networking for yt-dlp and all auxiliary connections (spec Section 6.6).
        System.setProperty("java.net.preferIPv4Stack", "true")

        // lock the player deep-link intent: open the Player screen if the system routed us here.
        val initialDeepLink = intent?.dataString
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        val prefs = getSharedPreferences("app_settings", MODE_PRIVATE)
        val hasCompletedOnboarding = prefs.getBoolean("has_completed_onboarding", false)
        val hasCompletedInitialScan = prefs.getBoolean("has_completed_initial_scan", false)

        setContent {
            TideMusicTheme {
                val context = this
                var showGuide by remember {
                    mutableStateOf(!hasCompletedOnboarding && !hasAllPermissions())
                }
                var isInitialSetup by remember { mutableStateOf(!hasCompletedInitialScan) }
                val scanProgress by ServiceLocator.repository.scanProgress.collectAsState()
                var initialDelayDone by remember { mutableStateOf(false) }

                LaunchedEffect(Unit) {
                    // Allow UI hierarchy and database indices to finish initializing smoothly
                    delay(450L)
                    initialDelayDone = true
                }

                // If it's initial setup, wait until full scan completes. Otherwise, only brief delay.
                val isStagingComplete = if (isInitialSetup) {
                    initialDelayDone && !scanProgress.isScanning && hasCompletedInitialScan
                } else {
                    initialDelayDone
                }

                // Connect to the MediaSessionService once for the app's lifetime.
                DisposableEffect(Unit) {
                    ConnectionHolder.connect(context)
                    onDispose { ConnectionHolder.disconnect() }
                }
                val controller by ConnectionHolder.controller.collectAsState()
                CompositionLocalProvider(LocalMediaController provides controller) {
                    Box(Modifier.fillMaxSize()) {
                        AppShell(initialDeepLink = initialDeepLink)

                        // Loading screen: shows live staging text on 1st setup, clean minimal blue beam thereafter
                        AnimatedVisibility(
                            visible = !isStagingComplete,
                            enter = fadeIn(tween(100)),
                            exit = fadeOut(tween(350)),
                            modifier = Modifier.fillMaxSize().zIndex(99f),
                        ) {
                            AppLoadingScreen(scanProgress = if (isInitialSetup) scanProgress else null)
                        }

                        if (showGuide) {
                            com.example.tidemusic.ui.onboarding.WelcomeGuideDialog(
                                onGrantPermissions = {
                                    prefs.edit().putBoolean("has_completed_onboarding", true).apply()
                                    showGuide = false
                                    requestPermissionsIfNeeded()
                                },
                                onDismiss = {
                                    prefs.edit().putBoolean("has_completed_onboarding", true).apply()
                                    showGuide = false
                                    requestPermissionsIfNeeded()
                                }
                            )
                        }
                    }
                }
            }
        }

        // Background preload: full scan if initial setup, incremental diff otherwise
        triggerLibraryScan(isInitial = !hasCompletedInitialScan)
    }

    override fun onResume() {
        super.onResume()
        if (hasAllPermissions()) {
            val hasCompletedInitialScan = getSharedPreferences("app_settings", MODE_PRIVATE)
                .getBoolean("has_completed_initial_scan", false)
            triggerLibraryScan(isInitial = !hasCompletedInitialScan)
        }
    }

    private fun hasAllPermissions(): Boolean {
        val audioPerm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        val hasAudio = ContextCompat.checkSelfPermission(this, audioPerm) == PackageManager.PERMISSION_GRANTED
        val hasAllFiles = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            android.os.Environment.isExternalStorageManager()
        } else true
        return hasAudio && hasAllFiles
    }

    private fun checkManageAllFilesPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !android.os.Environment.isExternalStorageManager()) {
            try {
                val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = android.net.Uri.parse("package:$packageName")
                startActivity(intent)
            } catch (_: Exception) {
                try {
                    val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    startActivity(intent)
                } catch (_: Exception) {}
            }
        }
    }

    /**
     * Requests READ_MEDIA_AUDIO (API 33+) / READ_EXTERNAL_STORAGE (below 33)
     * and POST_NOTIFICATIONS (API 33+).
     */
    private fun requestPermissionsIfNeeded() {
        val perms = mutableListOf<String>()

        val audioPerm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (ContextCompat.checkSelfPermission(this, audioPerm) != PackageManager.PERMISSION_GRANTED) {
            perms.add(audioPerm)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val notifPerm = Manifest.permission.POST_NOTIFICATIONS
            if (ContextCompat.checkSelfPermission(this, notifPerm) != PackageManager.PERMISSION_GRANTED) {
                perms.add(notifPerm)
            }
        }

        if (perms.isNotEmpty()) {
            permissionLauncher.launch(perms.toTypedArray())
        } else {
            checkManageAllFilesPermission()
            val hasCompletedInitialScan = getSharedPreferences("app_settings", MODE_PRIVATE)
                .getBoolean("has_completed_initial_scan", false)
            triggerLibraryScan(isInitial = !hasCompletedInitialScan)
        }
    }

    /**
     * Triggers library scan: full rebuild on first setup, incremental diff on subsequent launches.
     */
    private fun triggerLibraryScan(isInitial: Boolean = false) {
        val repository = ServiceLocator.repository
        val prefs = getSharedPreferences("app_settings", MODE_PRIVATE)
        activityScope.launch {
            try {
                if (isInitial) {
                    repository.rescanFull()
                    prefs.edit().putBoolean("has_completed_initial_scan", true).apply()
                } else {
                    repository.rescanIncremental()
                }
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Library scan failed", e)
            }
        }
    }
}
