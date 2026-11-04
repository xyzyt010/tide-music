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
import com.example.tidemusic.theme.TideMusicTheme
import com.example.tidemusic.ui.AppShell
import com.example.tidemusic.ui.LocalMediaController
import com.example.tidemusic.ui.common.AppLoadingScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Single-activity host (spec Section 1). All screens are Compose destinations. */
class MainActivity : ComponentActivity() {

    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val deepLinkFlow = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        checkManageAllFilesPermission()
        triggerLibraryScan(force = true)
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.dataString?.let { deepLinkFlow.value = it }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // lock the player deep-link intent: open the Player screen if the system routed us here.
        val initialDeepLink = intent?.dataString
        deepLinkFlow.value = initialDeepLink
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        val prefs = getSharedPreferences("app_settings", MODE_PRIVATE)
        val hasCompletedOnboarding = prefs.getBoolean("has_completed_onboarding", false)

        setContent {
            TideMusicTheme {
                val context = this
                var showGuide by remember {
                    mutableStateOf(!hasCompletedOnboarding && !hasAllPermissions())
                }
                var splashVisible by remember { mutableStateOf(true) }

                LaunchedEffect(Unit) {
                    if (!hasCompletedOnboarding) {
                        // On first launch, wait up to 1200ms or until initial scan ingests songs
                        val start = System.currentTimeMillis()
                        while (System.currentTimeMillis() - start < 1200L && ServiceLocator.repository.observeAllSongs().first().isEmpty()) {
                            delay(150L)
                        }
                    } else {
                        // Fast smooth splash transition (300ms)
                        delay(300L)
                    }
                    splashVisible = false
                }

                LaunchedEffect(Unit) {
                    ServiceLocator.playbackController.ensureServiceStarted()
                }
                val player by ServiceLocator.playbackController.playerState.collectAsState()
                val currentDeepLink by deepLinkFlow.collectAsState()
                CompositionLocalProvider(LocalMediaController provides player) {
                    Box(Modifier.fillMaxSize()) {
                        AppShell(initialDeepLink = currentDeepLink)

                        // Smooth splash overlay with running blue beam that dissolves immediately
                        AnimatedVisibility(
                            visible = splashVisible,
                            enter = fadeIn(tween(80)),
                            exit = fadeOut(tween(250)),
                            modifier = Modifier.fillMaxSize().zIndex(99f),
                        ) {
                            AppLoadingScreen()
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

        // Background non-blocking library scan (force full scan on first launch)
        triggerLibraryScan(force = !hasCompletedOnboarding)
    }

    override fun onResume() {
        super.onResume()
        if (hasAllPermissions()) {
            val prefs = getSharedPreferences("app_settings", MODE_PRIVATE)
            val isFirstLaunch = !prefs.getBoolean("has_completed_onboarding", false)
            triggerLibraryScan(force = isFirstLaunch)
            com.example.tidemusic.util.BatteryOptimizationHelper.promptIfNeeded(this)
        } else {
            requestPermissionsIfNeeded()
        }
    }

    override fun onPause() {
        super.onPause()
        ServiceLocator.playbackController.savePlaybackState()
    }

    override fun onStop() {
        super.onStop()
        ServiceLocator.playbackController.savePlaybackState()
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
     * Requests READ_MEDIA_AUDIO (API 33+) / READ_EXTERNAL_STORAGE (below 33).
     * System status bar pill/capsule does not require notification permission.
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

        if (perms.isNotEmpty()) {
            permissionLauncher.launch(perms.toTypedArray())
        } else {
            checkManageAllFilesPermission()
            triggerLibraryScan()
        }
    }

    private var lastScanTimeMs = 0L

    /**
     * Triggers asynchronous library scan in the background with a 60-second cooldown
     * to avoid thrashing disk I/O when resuming the app.
     * When [force] is true or the song database is empty, performs a full two-phase rescan.
     */
    private fun triggerLibraryScan(force: Boolean = false) {
        if (!hasAllPermissions()) return
        val now = System.currentTimeMillis()
        if (!force && lastScanTimeMs > 0L && (now - lastScanTimeMs) < 60_000L) {
            return
        }
        lastScanTimeMs = now
        val repository = ServiceLocator.repository
        activityScope.launch {
            try {
                val hasSongs = repository.observeAllSongs().first().isNotEmpty()
                if (force || !hasSongs) {
                    repository.rescanFull()
                } else {
                    repository.rescanIncremental()
                }
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Library scan failed", e)
            }
        }
    }
}
