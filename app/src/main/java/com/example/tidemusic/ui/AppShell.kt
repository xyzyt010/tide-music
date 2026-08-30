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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
    if (initialDeepLink == "tidemusic://player" && backStack.size == 1) {
        backStack.add(Player)
    }

    val currentKey = backStack.lastOrNull()
    val isFullScreenDest = currentKey == EqualizerNavKey ||
            currentKey == SettingsNavKey ||
            currentKey == HelpInfo ||
            currentKey == SleepTimer

    val sections = remember { listOf(Player, Queue, Albums, Playlists, Folders, Search, Download, Artists) }
    val titles = remember {
        listOf(
            R.string.section_player, R.string.section_queue, R.string.section_albums, R.string.section_playlists,
            R.string.section_folders, R.string.section_search, R.string.section_download, R.string.section_artists
        )
    }
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { sections.size })
    val isDarkMode by ServiceLocator.settingsManager.isDarkMode.collectAsState()
    val scope = rememberCoroutineScope()    // Per-section navigation state preservation:
    // Maps section index -> list of detail NavKeys that were on the backStack for that section.
    val sectionStacks = remember { mutableStateMapOf<Int, List<NavKey>>() }
    var isPlayerExpanded by remember { mutableStateOf(false) }

    // Determine which section index a detail NavKey belongs to.
    fun sectionIndexForDetailKey(key: NavKey): Int = when (key) {
        is AlbumDetail -> 2   // Albums
        is PlaylistDetail -> 3 // Playlists
        is FolderDetail -> 4   // Folders
        is ArtistDetail -> 7   // Artists
        else -> -1
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = TideColors.background,
            contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
            bottomBar = {
                if (!isFullScreenDest) {
                    var menuOpen by remember { mutableStateOf(false) }
                    val barOutline = Color.White.copy(alpha = 0.16f)
                    val isPlayerPage = pagerState.currentPage == 0
                    val controller = LocalMediaController.current
                    var currentMediaItem by remember { mutableStateOf(controller?.currentMediaItem) }
                    LaunchedEffect(controller) {
                        controller?.addListener(object : androidx.media3.common.Player.Listener {
                            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                                currentMediaItem = mediaItem
                            }
                        })
                        currentMediaItem = controller?.currentMediaItem
                    }
                    val currentSong = currentMediaItem?.let(::mediaItemToSongDomain)
                    val artworkModel = if (isPlayerPage && currentSong != null) {
                        remember(currentSong.id, currentSong.filePath, currentSong.uri, currentSong.dateModified) {
                            com.example.tidemusic.util.SongArtworkRequest(
                                songId = currentSong.id,
                                filePath = currentSong.filePath,
                                uriString = currentSong.uri,
                                dateModified = currentSong.dateModified,
                            )
                        }
                    } else null
                    var hasArtwork by remember(currentSong?.id) { mutableStateOf(false) }

                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val miniPlayerEnabledPref by ServiceLocator.settingsManager.isMiniPlayerEnabled.collectAsState()
                        val hasMiniPlayer = !isPlayerPage && currentMediaItem != null && miniPlayerEnabledPref
                        if (hasMiniPlayer) {
                            com.example.tidemusic.ui.common.MiniPlayerBar(
                                onClick = { isPlayerExpanded = true }
                            )
                        }

                        val bottomBarDividerColor = if (isPlayerPage) Color.White.copy(alpha = 0.16f) else TideColors.textSecondary.copy(alpha = 0.18f)
                        val bottomBarBackground = if (isPlayerPage) Color.Transparent else TideColors.surface.copy(alpha = 0.96f)

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(bottomBarBackground)
                                .drawBehind {
                                    // Translucent hairline division line: always shown across all pages including Player page
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
                                    .horizontalScroll(rememberScrollState())
                                    .padding(horizontal = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                sections.forEachIndexed { index, navKey ->
                                    val selected = pagerState.currentPage == index
                                    val icon = when (navKey) {
                                        Player -> Icons.Rounded.PlayArrow
                                        Queue -> Icons.Rounded.QueueMusic
                                        Albums -> Icons.Rounded.Album
                                        Playlists -> Icons.Rounded.PlaylistPlay
                                        Folders -> Icons.Rounded.Folder
                                        Search -> Icons.Rounded.Search
                                        Download -> Icons.Rounded.Download
                                        Artists -> Icons.Rounded.Person
                                        else -> Icons.Rounded.PlayArrow
                                    }
                                    val label = when (navKey) {
                                        Player -> "Player"
                                        Queue -> "Queue"
                                        Albums -> "Albums"
                                        Playlists -> "Playlists"
                                        Folders -> "Folders"
                                        Search -> "Search"
                                        Download -> "Download"
                                        Artists -> "Artists"
                                        else -> ""
                                    }
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier
                                            .clickable { scope.launch { pagerState.animateScrollToPage(index) } }
                                            .padding(horizontal = 6.dp)
                                    ) {
                                        // Active page line indicator directly in sync over the dividing line
                                        Box(
                                            modifier = Modifier
                                                .width(36.dp)
                                                .height(2.5.dp)
                                                .background(
                                                    if (selected) TideColors.accent else Color.Transparent,
                                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(bottomStart = 1.5.dp, bottomEnd = 1.5.dp)
                                                )
                                        )
                                        Spacer(Modifier.height(3.dp))
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = label,
                                            tint = if (selected) TideColors.accent else TideColors.textSecondary
                                        )
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (selected) TideColors.accent else TideColors.textSecondary
                                        )
                                        Spacer(Modifier.height(3.dp))
                                    }
                                }

                                // "More" overflow menu at the end of the scrollable row
                                Box {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier
                                            .clickable { menuOpen = true }
                                            .padding(horizontal = 6.dp)
                                    ) {
                                        Box(modifier = Modifier.width(36.dp).height(2.5.dp))
                                        Spacer(Modifier.height(3.dp))
                                        Icon(Icons.Rounded.MoreVert, contentDescription = "More", tint = TideColors.textSecondary)
                                        Text("More", style = MaterialTheme.typography.labelSmall, color = TideColors.textSecondary)
                                        Spacer(Modifier.height(3.dp))
                                    }
                                    CenteredOverflowMenu(
                                        visible = menuOpen,
                                        onDismiss = { menuOpen = false },
                                        title = "More",
                                    ) {
                                        CenteredMenuItem(
                                            icon = if (isDarkMode) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                                            label = if (isDarkMode) "Light Theme" else "Dark Theme",
                                        ) {
                                            menuOpen = false
                                            ServiceLocator.settingsManager.setDarkMode(!isDarkMode)
                                        }
                                        CenteredMenuItem(Icons.Rounded.Equalizer, "Equalizer") {
                                            menuOpen = false; backStack.add(EqualizerNavKey)
                                        }
                                        CenteredMenuItem(Icons.Rounded.Refresh, "Scan Library") {
                                            menuOpen = false
                                            scope.launch(Dispatchers.IO) {
                                                try { ServiceLocator.repository.rescanFull() } catch (_: Exception) {}
                                            }
                                        }
                                        CenteredMenuItem(Icons.Rounded.Settings, "Settings") {
                                            menuOpen = false; backStack.add(SettingsNavKey)
                                        }
                                        CenteredMenuItem(Icons.Rounded.Info, "Help & Info") {
                                            menuOpen = false; backStack.add(HelpInfo)
                                        }
                                        CenteredMenuItem(Icons.Rounded.Timer, "Sleep Timer") {
                                            menuOpen = false; backStack.add(SleepTimer)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        ) { appPadding ->
            val isPlayer = pagerState.currentPage == 0
            NavDisplay(
                backStack = backStack,
                onBack = { backStack.removeLastOrNull() },
                modifier = Modifier.padding(
                    top = appPadding.calculateTopPadding(),
                    bottom = if (isPlayer) 0.dp else appPadding.calculateBottomPadding(),
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
                    is AlbumDetail -> {
                        AlbumDetailScreen(
                            albumId = topKey.albumId,
                            onBack = onBackDetail
                        )
                    }
                    is PlaylistDetail -> {
                        PlaylistDetailScreen(
                            playlistId = topKey.playlistId,
                            title = topKey.playlistName,
                            isBuiltIn = topKey.isBuiltIn,
                            onBack = onBackDetail
                        )
                    }
                    is FolderDetail -> {
                        FolderDetailScreen(
                            folderId = topKey.folderId,
                            path = topKey.folderPath,
                            onBack = onBackDetail,
                            onFolderClick = { child -> onOpenDetail(FolderDetail(child.id, child.path)) }
                        )
                    }
                    is ArtistDetail -> {
                        ArtistDetailScreen(
                            artistId = topKey.artistId,
                            artistName = topKey.artistName,
                            onBack = onBackDetail,
                        )
                    }
                }
            } else {
                when (sections[page]) {
                    Player -> PlayerScreen(onBack = null, onNavigate = onOpenScreen)
                    Queue -> QueueScreen(onBack = { /* top-level, no back */ })
                    Albums -> AlbumsScreen(onAlbumClick = { id -> onOpenDetail(AlbumDetail(id)) }, onOpenScreen = onOpenScreen)
                    Playlists -> PlaylistsScreen(onPlaylistClick = { p -> onOpenDetail(PlaylistDetail(p.id, p.name, p.isBuiltIn)) })
                    Folders -> FoldersScreen(onFolderClick = { f -> onOpenDetail(FolderDetail(f.id, f.path)) })
                    Search -> SearchScreen(isActive = pagerState.currentPage == page)
                    Download -> DownloadScreen()
                    Artists -> ArtistsScreen(onArtistClick = { id, name -> onOpenDetail(ArtistDetail(id, name)) })
                }
            }
        }
    }
}
