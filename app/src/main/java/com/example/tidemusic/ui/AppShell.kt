package com.example.tidemusic.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.zIndex
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.blur
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import com.example.tidemusic.ui.common.mediaItemToSongDomain
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import com.example.tidemusic.theme.TideColors
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.tidemusic.AlbumDetail
import com.example.tidemusic.Albums
import com.example.tidemusic.ArtistDetail
import com.example.tidemusic.Artists
import com.example.tidemusic.Download
import com.example.tidemusic.Equalizer as EqualizerNavKey
import com.example.tidemusic.FolderDetail
import com.example.tidemusic.Folders
import com.example.tidemusic.HelpInfo
import com.example.tidemusic.Main
import com.example.tidemusic.Player
import com.example.tidemusic.PlaylistDetail
import com.example.tidemusic.Playlists
import com.example.tidemusic.Queue
import com.example.tidemusic.R
import com.example.tidemusic.Search
import com.example.tidemusic.Settings as SettingsNavKey
import com.example.tidemusic.SleepTimer
import com.example.tidemusic.di.ServiceLocator
import com.example.tidemusic.ui.albums.AlbumsScreen
import com.example.tidemusic.ui.artists.ArtistsScreen
import com.example.tidemusic.ui.artists.ArtistDetailScreen
import com.example.tidemusic.ui.common.CenteredOverflowMenu
import com.example.tidemusic.ui.common.CenteredMenuItem
import com.example.tidemusic.ui.albums.AlbumDetailScreen
import com.example.tidemusic.ui.download.DownloadScreen
import com.example.tidemusic.ui.equalizer.EqualizerScreen
import com.example.tidemusic.ui.folders.FolderDetailScreen
import com.example.tidemusic.ui.folders.FoldersScreen
import com.example.tidemusic.ui.info.HelpInfoScreen
import com.example.tidemusic.ui.player.PlayerScreen
import com.example.tidemusic.ui.playlists.PlaylistDetailScreen
import com.example.tidemusic.ui.playlists.PlaylistsScreen
import com.example.tidemusic.ui.queue.QueueScreen
import com.example.tidemusic.ui.search.SearchScreen
import com.example.tidemusic.ui.settings.SettingsScreen
import com.example.tidemusic.ui.settings.SleepTimerScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Root shell of the app:
 *  - No top bar — all vertical space is for content.
 *  - Horizontally-scrollable bottom navigation bar with section icons + "More" overflow.
 *  - Persistent mini-player bar pinned above the nav row.
 *  - Content pages are swipeable via HorizontalPager.
 *  - Per-section navigation state is preserved when switching tabs.
 *  - Detail screens are rendered inside pager pages to allow smooth touch dragging/swiping.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppShell(initialDeepLink: String? = null) {
    val backStack = rememberNavBackStack(Main)

    val currentKey = backStack.lastOrNull()
    val isFullScreenDest = currentKey == EqualizerNavKey ||
            currentKey == SettingsNavKey ||
            currentKey == HelpInfo ||
            currentKey == SleepTimer ||
            currentKey == Albums ||
            currentKey == Artists ||
            currentKey == Folders ||
            currentKey is AlbumDetail ||
            currentKey is ArtistDetail ||
            currentKey is FolderDetail

    // Player is removed from bottom dock per requirements: it is accessible via notification or tapping mini dock
    // Albums, Artists, Folders moved to the "More" overflow menu
    val sections = remember { listOf(Queue, Playlists, Search, Download) }
    val titles = remember {
        listOf(
            R.string.section_queue, R.string.section_playlists,
            R.string.section_search, R.string.section_download
        )
    }
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { sections.size })
    val isDarkMode by ServiceLocator.settingsManager.isDarkMode.collectAsState()
    val scope = rememberCoroutineScope()
    // Per-section navigation state preservation:
    // Maps section index -> list of detail NavKeys that were on the backStack for that section.
    val sectionStacks = remember { mutableStateMapOf<Int, List<NavKey>>() }
    var isPlayerExpanded by remember { mutableStateOf(initialDeepLink == "tidemusic://player") }

    LaunchedEffect(initialDeepLink) {
        if (initialDeepLink == "tidemusic://player") {
            isPlayerExpanded = true
        }
    }

    val controller = LocalMediaController.current
    var currentMediaItem by remember { mutableStateOf(controller?.currentMediaItem) }
    var isPlaying by remember { mutableStateOf(controller?.isPlaying == true) }
    LaunchedEffect(controller) {
        if (controller != null) {
            val listener = object : androidx.media3.common.Player.Listener {
                override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                    currentMediaItem = mediaItem
                }
                override fun onIsPlayingChanged(playing: Boolean) {
                    isPlaying = playing
                }
            }
            controller.addListener(listener)
            currentMediaItem = controller.currentMediaItem
            isPlaying = controller.isPlaying
        }
    }

    // Determine which section index a detail NavKey belongs to.
    fun sectionIndexForDetailKey(key: NavKey): Int = when (key) {
        is PlaylistDetail -> 1 // Playlists
        else -> -1
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = TideColors.background,
            contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
            bottomBar = {
                if (!isFullScreenDest) {
                    var menuOpen by remember { mutableStateOf(false) }

                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val miniPlayerEnabledPref by ServiceLocator.settingsManager.isMiniPlayerEnabled.collectAsState()
                        val hasMiniPlayer = currentMediaItem != null && miniPlayerEnabledPref
                        if (hasMiniPlayer) {
                            com.example.tidemusic.ui.common.MiniPlayerBar(
                                onClick = { isPlayerExpanded = true }
                            )
                        }

                        val bottomBarDividerColor = TideColors.textSecondary.copy(alpha = 0.18f)
                        val bottomBarBackground = TideColors.surface.copy(alpha = 0.96f)

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(bottomBarBackground)
                                .drawBehind {
                                    drawLine(
                                        color = bottomBarDividerColor,
                                        start = Offset(0f, 0f),
                                        end = Offset(size.width, 0f),
                                        strokeWidth = 1.dp.toPx(),
                                    )
                                }
                                .windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.navigationBars)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp, vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                sections.forEachIndexed { index, navKey ->
                                    val selected = pagerState.currentPage == index
                                    val icon = when (navKey) {
                                        Queue -> Icons.Rounded.QueueMusic
                                        Playlists -> Icons.Rounded.PlaylistPlay
                                        Search -> Icons.Rounded.Search
                                        Download -> Icons.Rounded.Download
                                        else -> Icons.Rounded.QueueMusic
                                    }
                                    val label = when (navKey) {
                                        Queue -> "Queue"
                                        Playlists -> "Playlists"
                                        Search -> "Search"
                                        Download -> "Download"
                                        else -> ""
                                    }
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { scope.launch { pagerState.animateScrollToPage(index) } },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                        ) {
                                            // Active page line indicator directly in sync over the dividing line
                                            Box(
                                                modifier = Modifier
                                                    .width(32.dp)
                                                    .height(2.5.dp)
                                                    .background(
                                                        if (selected) TideColors.accent else Color.Transparent,
                                                        shape = androidx.compose.foundation.shape.RoundedCornerShape(bottomStart = 1.5.dp, bottomEnd = 1.5.dp)
                                                    )
                                            )
                                            Spacer(Modifier.height(5.dp))
                                            Icon(
                                                imageVector = icon,
                                                contentDescription = label,
                                                tint = if (selected) TideColors.accent else TideColors.textSecondary,
                                                modifier = Modifier.size(24.dp)
                                            )
                                            Spacer(Modifier.height(5.dp))
                                        }
                                    }
                                }

                                // "More" overflow menu item as the 5th equally-weighted item
                                Box(
                                    modifier = Modifier.weight(1f),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier
                                            .clickable { menuOpen = true }
                                    ) {
                                        Box(modifier = Modifier.width(32.dp).height(2.5.dp))
                                        Spacer(Modifier.height(5.dp))
                                        Icon(
                                            imageVector = Icons.Rounded.MoreVert,
                                            contentDescription = "More",
                                            tint = TideColors.textSecondary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(Modifier.height(5.dp))
                                    }
                                    DropdownMenu(
                                        expanded = menuOpen,
                                        onDismissRequest = { menuOpen = false },
                                        containerColor = TideColors.surfaceElevated,
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Albums", color = TideColors.textPrimary) },
                                            leadingIcon = { Icon(Icons.Rounded.Album, null, tint = TideColors.textSecondary) },
                                            onClick = {
                                                menuOpen = false
                                                backStack.add(Albums)
                                            },
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Artists", color = TideColors.textPrimary) },
                                            leadingIcon = { Icon(Icons.Rounded.Person, null, tint = TideColors.textSecondary) },
                                            onClick = {
                                                menuOpen = false
                                                backStack.add(Artists)
                                            },
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Folders", color = TideColors.textPrimary) },
                                            leadingIcon = { Icon(Icons.Rounded.Folder, null, tint = TideColors.textSecondary) },
                                            onClick = {
                                                menuOpen = false
                                                backStack.add(Folders)
                                            },
                                        )
                                        HorizontalDivider(color = TideColors.outline)
                                        DropdownMenuItem(
                                            text = { Text(if (isDarkMode) "Light Theme" else "Dark Theme", color = TideColors.textPrimary) },
                                            leadingIcon = { Icon(if (isDarkMode) Icons.Rounded.LightMode else Icons.Rounded.DarkMode, null, tint = TideColors.textSecondary) },
                                            onClick = {
                                                menuOpen = false
                                                ServiceLocator.settingsManager.setDarkMode(!isDarkMode)
                                            },
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Equalizer", color = TideColors.textPrimary) },
                                            leadingIcon = { Icon(Icons.Rounded.Equalizer, null, tint = TideColors.textSecondary) },
                                            onClick = { menuOpen = false; backStack.add(EqualizerNavKey) },
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Scan Library", color = TideColors.textPrimary) },
                                            leadingIcon = { Icon(Icons.Rounded.Refresh, null, tint = TideColors.textSecondary) },
                                            onClick = {
                                                menuOpen = false
                                                scope.launch(Dispatchers.IO) {
                                                    try { ServiceLocator.repository.rescanFull() } catch (_: Exception) {}
                                                }
                                            },
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Settings", color = TideColors.textPrimary) },
                                            leadingIcon = { Icon(Icons.Rounded.Settings, null, tint = TideColors.textSecondary) },
                                            onClick = { menuOpen = false; backStack.add(SettingsNavKey) },
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Help & Info", color = TideColors.textPrimary) },
                                            leadingIcon = { Icon(Icons.Rounded.Info, null, tint = TideColors.textSecondary) },
                                            onClick = { menuOpen = false; backStack.add(HelpInfo) },
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Sleep Timer", color = TideColors.textPrimary) },
                                            leadingIcon = { Icon(Icons.Rounded.Timer, null, tint = TideColors.textSecondary) },
                                            onClick = { menuOpen = false; backStack.add(SleepTimer) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        ) { appPadding ->
            NavDisplay(
                backStack = backStack,
                onBack = { backStack.removeLastOrNull() },
                modifier = Modifier.padding(
                    top = appPadding.calculateTopPadding(),
                    bottom = appPadding.calculateBottomPadding(),
                ),
                entryProvider = entryProvider {
                    entry<Main> {
                        ShellContents(
                            pagerState = pagerState,
                            sections = sections,
                            sectionStacks = sectionStacks,
                            onOpenScreen = { dest ->
                                val sectionIndex = sections.indexOf(dest)
                                if (sectionIndex >= 0) {
                                    scope.launch {
                                        try {
                                            pagerState.animateScrollToPage(sectionIndex)
                                        } catch (_: Exception) {
                                            pagerState.scrollToPage(sectionIndex)
                                        }
                                    }
                                } else {
                                    try {
                                        backStack.add(dest)
                                    } catch (_: Exception) {
                                    }
                                }
                            },
                            onOpenDetail = { dest ->
                                val idx = sectionIndexForDetailKey(dest)
                                if (idx >= 0) {
                                    val current = sectionStacks[idx] ?: emptyList()
                                    sectionStacks[idx] = current + dest
                                }
                            },
                            onBackDetail = {
                                val idx = pagerState.currentPage
                                val current = sectionStacks[idx] ?: emptyList()
                                if (current.isNotEmpty()) {
                                    sectionStacks[idx] = current.dropLast(1)
                                }
                            }
                        )
                    }
                    entry<EqualizerNavKey> { EqualizerScreen(onBack = { backStack.removeLastOrNull() }) }
                    entry<SettingsNavKey> { SettingsScreen(onBack = { backStack.removeLastOrNull() }) }
                    entry<HelpInfo> { HelpInfoScreen(onBack = { backStack.removeLastOrNull() }) }
                    entry<SleepTimer> { SleepTimerScreen(onBack = { backStack.removeLastOrNull() }) }
                    entry<Albums> {
                        AlbumsScreen(
                            onAlbumClick = { id -> backStack.add(AlbumDetail(id)) },
                            onOpenScreen = { dest -> backStack.add(dest) },
                            onBack = { backStack.removeLastOrNull() },
                        )
                    }
                    entry<Artists> {
                        ArtistsScreen(
                            onArtistClick = { id, name -> backStack.add(ArtistDetail(id, name)) },
                            onBack = { backStack.removeLastOrNull() },
                        )
                    }
                    entry<Folders> {
                        FoldersScreen(
                            onFolderClick = { f -> backStack.add(FolderDetail(f.id, f.path)) },
                            onBack = { backStack.removeLastOrNull() },
                        )
                    }
                    entry<AlbumDetail> { detail ->
                        AlbumDetailScreen(
                            albumId = detail.albumId,
                            onBack = { backStack.removeLastOrNull() },
                        )
                    }
                    entry<ArtistDetail> { detail ->
                        ArtistDetailScreen(
                            artistId = detail.artistId,
                            artistName = detail.artistName,
                            onBack = { backStack.removeLastOrNull() },
                        )
                    }
                    entry<FolderDetail> { detail ->
                        FolderDetailScreen(
                            folderId = detail.folderId,
                            path = detail.folderPath,
                            onBack = { backStack.removeLastOrNull() },
                            onFolderClick = { child -> backStack.add(FolderDetail(child.id, child.path)) },
                        )
                    }
                },
            )
        }

        // Smooth vertical expand overlay for PlayerScreen when clicked from mini player
        AnimatedVisibility(
            visible = isPlayerExpanded,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            ) + fadeIn(animationSpec = tween(200)),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            ) + fadeOut(animationSpec = tween(180)),
            modifier = Modifier.fillMaxSize().zIndex(20f),
        ) {
            BackHandler(enabled = isPlayerExpanded) {
                isPlayerExpanded = false
            }
            PlayerScreen(
                onBack = { isPlayerExpanded = false },
                onNavigate = { dest ->
                    isPlayerExpanded = false
                    val sectionIndex = sections.indexOf(dest)
                    if (sectionIndex >= 0) {
                        scope.launch {
                            try {
                                pagerState.animateScrollToPage(sectionIndex)
                            } catch (_: Exception) {
                                pagerState.scrollToPage(sectionIndex)
                            }
                        }
                    } else {
                        try {
                            backStack.add(dest)
                        } catch (_: Exception) {}
                    }
                },
            )
        }

        // In-App Dynamic Island Mini Pill Widget (Black capsule pill with tiny artwork + animated sound bars)

    }
}

@Composable
private fun ShellContents(
    pagerState: PagerState,
    sections: List<NavKey>,
    sectionStacks: androidx.compose.runtime.snapshots.SnapshotStateMap<Int, List<NavKey>>,
    onOpenScreen: (NavKey) -> Unit,
    onOpenDetail: (NavKey) -> Unit,
    onBackDetail: () -> Unit
) {
    val currentPage = pagerState.currentPage
    val currentSectionStack = sectionStacks[currentPage] ?: emptyList()

    // Handle back button clicks: pop local detail screen stack if it's not empty
    BackHandler(enabled = currentSectionStack.isNotEmpty()) {
        onBackDetail()
    }

    Column(Modifier.fillMaxSize().background(TideColors.background)) {
        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
            val stack = sectionStacks[page] ?: emptyList()
            if (stack.isNotEmpty()) {
                when (val topKey = stack.last()) {
                    is PlaylistDetail -> {
                        PlaylistDetailScreen(
                            playlistId = topKey.playlistId,
                            title = topKey.playlistName,
                            isBuiltIn = topKey.isBuiltIn,
                            onBack = onBackDetail
                        )
                    }
                }
            } else {
                when (sections[page]) {
                    Queue -> QueueScreen(onBack = { /* top-level, no back */ })
                    Playlists -> PlaylistsScreen(onPlaylistClick = { p -> onOpenDetail(PlaylistDetail(p.id, p.name, p.isBuiltIn)) })
                    Search -> SearchScreen(isActive = pagerState.currentPage == page)
                    Download -> DownloadScreen()
                }
            }
        }
    }
}
