@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.media3.common.util.UnstableApi::class)

package com.example.tidemusic.ui.player

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.unit.IntOffset
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeMute
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.text.style.TextAlign
import com.example.tidemusic.util.LyricsHelper
import com.example.tidemusic.util.SongLyrics
import coil3.compose.AsyncImage
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.automirrored.rounded.FormatAlignLeft
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Queue
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.navigation3.runtime.NavKey
import com.example.tidemusic.di.ServiceLocator
import com.example.tidemusic.domain.LibraryRepository
import com.example.tidemusic.domain.Playlist
import com.example.tidemusic.domain.Song
import com.example.tidemusic.playback.PlaybackController
import com.example.tidemusic.theme.TideColors
import com.example.tidemusic.ui.LocalMediaController
import com.example.tidemusic.ui.common.PlayerArtwork
import com.example.tidemusic.ui.common.CenteredOverflowMenu
import com.example.tidemusic.ui.common.CenteredMenuItem
import com.example.tidemusic.ui.common.CenteredMenuDivider
import com.example.tidemusic.ui.common.mediaItemToSongDomain
import com.example.tidemusic.ui.common.setAsRingtoneSafe
import com.example.tidemusic.ui.common.shareSongSafe
import com.example.tidemusic.ui.rememberTideViewModel
import com.example.tidemusic.util.formatDuration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PlayerViewModel(
    private val repository: LibraryRepository,
    private val playbackController: PlaybackController,
) : ViewModel() {
    private val _isFavorite = MutableStateFlow(false)
    val isFavorite: StateFlow<Boolean> = _isFavorite.asStateFlow()

    private val _fullSong = MutableStateFlow<Song?>(null)
    val fullSong: StateFlow<Song?> = _fullSong.asStateFlow()

    fun bind(songId: Long) {
        viewModelScope.launch {
            repository.observeSong(songId).collect { s ->
                _isFavorite.value = s?.isFavorite ?: false
                if (s != null) _fullSong.value = s
            }
        }
    }

    fun toggleFavorite(songId: Long, v: Boolean) {
        viewModelScope.launch { repository.setFavorite(songId, v) }
    }

    fun updateTags(songId: Long, title: String, artist: String, album: String) {
        viewModelScope.launch {
            repository.updateTags(songId, title, artist, album)
            val updated = repository.getSong(songId)
            if (updated != null) _fullSong.value = updated
        }
    }

    fun deletePermanently(songId: Long) {
        viewModelScope.launch { repository.deletePermanently(listOf(songId)) }
    }

    fun addSongsToPlaylist(playlistId: Long, songIds: List<Long>) {
        viewModelScope.launch { repository.addSongsToPlaylist(playlistId, songIds) }
    }

    fun removeSongsFromPlaylist(playlistId: Long, songIds: List<Long>) {
        viewModelScope.launch {
            try {
                songIds.forEach { repository.removeSongFromPlaylist(playlistId, it) }
            } catch (_: Exception) {
            }
        }
    }

    fun createPlaylist(name: String, songIds: List<Long>) {
        viewModelScope.launch {
            try {
                val id = repository.createUserPlaylist(name)
                if (songIds.isNotEmpty()) repository.addSongsToPlaylist(id, songIds)
            } catch (_: Exception) {
            }
        }
    }

    fun observePlaylists(): StateFlow<List<Playlist>> = repository.observePlaylists().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )

    fun addToQueue(song: Song) = playbackController.addToQueue(song)

    fun playNext(song: Song) = playbackController.playNext(song)
}

@Composable
fun PlayerScreen(
    onBack: (() -> Unit)? = null,
    onNavigate: (NavKey) -> Unit,
    viewModel: PlayerViewModel = rememberTideViewModel {
        PlayerViewModel(it.repository, it.playbackController)
    },
) {
    val controller = LocalMediaController.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var overflowOpen by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showPlaylistDialog by remember { mutableStateOf(false) }
    var showInfoSheet by remember { mutableStateOf(false) }
    var showTagEditorDialog by remember { mutableStateOf(false) }
    var showBookmarksDialog by remember { mutableStateOf(false) }
    var showLyricsDialog by remember { mutableStateOf(false) }
    var showAbRepeatDialog by remember { mutableStateOf(false) }
    var abPointA by remember { mutableStateOf<Long?>(null) }
    var abPointB by remember { mutableStateOf<Long?>(null) }
    var speedValue by remember { mutableStateOf(1.0f) }
    var pitchValue by remember { mutableStateOf(1.0f) }
    var shuffleToast by remember { mutableStateOf<String?>(null) }

    var currentItem by remember { mutableStateOf<MediaItem?>(controller?.currentMediaItem) }
    var isPlaying by remember { mutableStateOf(controller?.isPlaying ?: false) }
    var position by remember { mutableStateOf(controller?.currentPosition ?: 0L) }
    var duration by remember { mutableStateOf(controller?.duration ?: 0L) }
    var shuffle by remember { mutableStateOf(controller?.shuffleModeEnabled ?: false) }
    var repeatMode by remember { mutableStateOf(controller?.repeatMode ?: Player.REPEAT_MODE_OFF) }

    LaunchedEffect(controller) {
        controller?.run {
            val listener = object : Player.Listener {
                override fun onMediaItemTransition(m: MediaItem?, r: Int) {
                    currentItem = m
                    position = currentPosition
                    duration = this@run.duration.coerceAtLeast(0L)
                }

                override fun onIsPlayingChanged(p: Boolean) {
                    isPlaying = p
                    position = currentPosition
                    duration = this@run.duration.coerceAtLeast(0L)
                }

                override fun onPlaybackStateChanged(state: Int) {
                    position = currentPosition
                    duration = this@run.duration.coerceAtLeast(0L)
                }

                override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                    shuffle = shuffleModeEnabled
                }

                override fun onRepeatModeChanged(mode: Int) {
                    repeatMode = mode
                }

                override fun onPositionDiscontinuity(
                    oldPosition: Player.PositionInfo,
                    newPosition: Player.PositionInfo,
                    reason: Int,
                ) {
                    position = newPosition.positionMs
                    duration = this@run.duration.coerceAtLeast(0L)
                }
            }
            addListener(listener)
            currentItem = currentMediaItem
            isPlaying = this.isPlaying
            position = currentPosition
            duration = this.duration.coerceAtLeast(0L)
            shuffle = shuffleModeEnabled
            repeatMode = this.repeatMode
            speedValue = playbackParameters.speed
            pitchValue = playbackParameters.pitch
        }
    }

    // Live position loop with A-B repeat support
    LaunchedEffect(controller, isPlaying, abPointA, abPointB) {
        if (controller != null && isPlaying) {
            while (true) {
                val curr = controller.currentPosition
                val start = abPointA
                val end = abPointB
                if (start != null && end != null && end > start && curr >= end) {
                    controller.seekTo(start)
                    position = start
                } else {
                    position = curr
                }
                duration = controller.duration.coerceAtLeast(0L)
                kotlinx.coroutines.delay(250L)
            }
        }
    }

    LaunchedEffect(shuffleToast) {
        val msg = shuffleToast ?: return@LaunchedEffect
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        shuffleToast = null
    }

    val mediaSong = currentItem?.let { mediaItemToSongDomain(it) }
    LaunchedEffect(mediaSong?.id) {
        if (mediaSong != null) {
            viewModel.bind(mediaSong.id)
            controller?.let {
                position = it.currentPosition
                duration = it.duration.coerceAtLeast(0L)
            }
        }
    }
    val isFavorite by viewModel.isFavorite.collectAsState()
    val fullSong by viewModel.fullSong.collectAsState()
    val playlists by viewModel.observePlaylists().collectAsState()
    val infoSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Prefer full Room song (artwork, path, bitrate…); fall back to media metadata.
    val s = fullSong?.takeIf { mediaSong == null || it.id == mediaSong.id } ?: mediaSong

    if (s == null) {
        Box(
            Modifier.fillMaxSize().background(TideColors.background),
            contentAlignment = Alignment.Center,
        ) {
            Text("Nothing playing", style = MaterialTheme.typography.bodyLarge, color = TideColors.textSecondary)
        }
        return
    }

    val displayDuration = if (duration > 0) duration else s.durationMs

    val artworkModel = remember(s.id, s.filePath, s.uri, s.dateModified) {
        com.example.tidemusic.util.SongArtworkRequest(
            songId = s.id,
            filePath = s.filePath,
            uriString = s.uri,
            dateModified = s.dateModified,
        )
    }
    var hasArtwork by remember(s.id) { mutableStateOf(false) }
    val isDarkMode by ServiceLocator.settingsManager.isDarkMode.collectAsState()
    val glassEnabled by ServiceLocator.settingsManager.isGlassEffectsEnabled.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            .background(TideColors.background),
    ) {
        // 1. Frosted glass blurred background if song has image and glassEnabled is true.
        if (glassEnabled) {
            AsyncImage(
                model = artworkModel,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = 1.35f
                        scaleY = 1.35f
                    }
                    .blur(64.dp),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                onState = { state ->
                    hasArtwork = state is coil3.compose.AsyncImagePainter.State.Success
                },
            )
        }

        // 2. Tint overlay matching dark or light mode
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    if (hasArtwork && glassEnabled) {
                        if (isDarkMode) androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.32f)
                        else androidx.compose.ui.graphics.Color.White.copy(alpha = 0.35f)
                    } else {
                        TideColors.background
                    }
                ),
        )

        Column(Modifier.fillMaxSize()) {
            // Minimal clean top bar (back button only)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onBack != null) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Rounded.KeyboardArrowDown,
                            contentDescription = "Collapse Player",
                            tint = TideColors.textPrimary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 76.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                var playerViewPage by remember { mutableIntStateOf(0) }
                var swipeDragOffset by remember { mutableFloatStateOf(0f) }

                Box(
                    modifier = Modifier
                        .sizeIn(maxWidth = 280.dp, maxHeight = 280.dp)
                        .aspectRatio(1f)
                        .pointerInput(playerViewPage) {
                            detectHorizontalDragGestures(
                                onDragEnd = {
                                    if (swipeDragOffset < -40f && playerViewPage == 0) {
                                        playerViewPage = 1 // Swiped left -> show lyrics
                                    } else if (swipeDragOffset > 40f && playerViewPage == 1) {
                                        playerViewPage = 0 // Swiped right -> show artwork
                                    }
                                    swipeDragOffset = 0f
                                },
                                onDragCancel = {
                                    swipeDragOffset = 0f
                                },
                                onHorizontalDrag = { change, dragAmount ->
                                    change.consume()
                                    swipeDragOffset += dragAmount
                                }
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    AnimatedContent(
                        targetState = playerViewPage,
                        transitionSpec = {
                            if (targetState > initialState) {
                                (slideInHorizontally { width -> width / 2 } + fadeIn(animationSpec = tween(220))) togetherWith
                                (slideOutHorizontally { width -> -width / 2 } + fadeOut(animationSpec = tween(220)))
                            } else {
                                (slideInHorizontally { width -> -width / 2 } + fadeIn(animationSpec = tween(220))) togetherWith
                                (slideOutHorizontally { width -> width / 2 } + fadeOut(animationSpec = tween(220)))
                            }
                        },
                        label = "PlayerViewSwitch"
                    ) { page ->
                        if (page == 0) {
                            PlayerArtwork(song = s, size = 280.dp, isPlaying = isPlaying)
                        } else {
                            LyricsCanvasView(
                                song = s,
                                positionMs = position.toLong(),
                                onSeek = { controller?.seekTo(it) },
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Animated 2-dot / oval pill indicator
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    for (i in 0..1) {
                        val isSelected = playerViewPage == i
                        val targetWidth = if (isSelected) 18.dp else 6.dp
                        val widthAnim by animateDpAsState(
                            targetValue = targetWidth,
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                            label = "indicatorWidth"
                        )
                        val color = if (isSelected) TideColors.accent else Color.White.copy(alpha = 0.35f)

                        Box(
                            modifier = Modifier
                                .width(widthAnim)
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(color)
                                .clickable {
                                    playerViewPage = i
                                }
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(0.55f))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = s.title.ifBlank { "Unknown" },
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        color = TideColors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = s.artist.ifBlank { "<unknown>" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = TideColors.textPrimary.copy(alpha = 0.75f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = s.album.ifBlank { "<unknown>" },
                        style = MaterialTheme.typography.bodySmall,
                        color = TideColors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Clean action row: Favorite, Repeat, Shuffle, 3-dot Menu (no clutter!)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { viewModel.toggleFavorite(s.id, !isFavorite) }) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            contentDescription = "Favorite",
                            tint = if (isFavorite) TideColors.error else TideColors.textPrimary.copy(alpha = 0.8f),
                        )
                    }
                    IconButton(
                        onClick = {
                            repeatMode = when (repeatMode) {
                                Player.REPEAT_MODE_OFF -> {
                                    controller?.repeatMode = Player.REPEAT_MODE_ALL
                                    Player.REPEAT_MODE_ALL
                                }
                                Player.REPEAT_MODE_ALL -> {
                                    controller?.repeatMode = Player.REPEAT_MODE_ONE
                                    Player.REPEAT_MODE_ONE
                                }
                                else -> {
                                    controller?.repeatMode = Player.REPEAT_MODE_OFF
                                    Player.REPEAT_MODE_OFF
                                }
                            }
                        },
                    ) {
                        Icon(
                            imageVector = if (repeatMode == Player.REPEAT_MODE_ONE) {
                                Icons.Rounded.RepeatOne
                            } else {
                                Icons.Rounded.Repeat
                            },
                            contentDescription = "Repeat",
                            tint = if (repeatMode != Player.REPEAT_MODE_OFF) {
                                TideColors.accent
                            } else {
                                TideColors.textPrimary.copy(alpha = 0.8f)
                            },
                        )
                    }
                    // Shuffle ON → shuffle icon (accent). OFF → straight arrows (sequential).
                    IconButton(
                        onClick = {
                            val next = !shuffle
                            shuffle = next
                            controller?.shuffleModeEnabled = next
                            ServiceLocator.playbackController.setShuffleMode(next)
                            shuffleToast = if (next) "Shuffle mode is on" else "Shuffle mode is off"
                        },
                    ) {
                        Icon(
                            imageVector = if (shuffle) Icons.Rounded.Shuffle else Icons.Rounded.SwapHoriz,
                            contentDescription = if (shuffle) "Shuffle on" else "Shuffle off",
                            tint = if (shuffle) TideColors.accent else TideColors.textPrimary.copy(alpha = 0.8f),
                        )
                    }
                    Box {
                        IconButton(onClick = { overflowOpen = true }) {
                            Icon(Icons.Rounded.MoreHoriz, "More", tint = TideColors.textPrimary.copy(alpha = 0.8f))
                        }
                        PlayerOverflowMenu(
                            expanded = overflowOpen,
                            onDismiss = { overflowOpen = false },
                            song = s,
                            onSpeedPitch = { showSpeedDialog = true },
                            onAddToQueue = {
                                viewModel.addToQueue(s)
                                Toast.makeText(context, "Added to queue", Toast.LENGTH_SHORT).show()
                            },
                            onAddToPlaylists = { showPlaylistDialog = true },
                            onSongDetails = { showInfoSheet = true },
                            onShare = { shareSongSafe(context, s) },
                            onRingtone = { setAsRingtoneSafe(context, s) },
                            onDelete = {
                                viewModel.deletePermanently(s.id)
                                Toast.makeText(context, "Deleted", Toast.LENGTH_SHORT).show()
                            },
                            onEqualizer = { onNavigate(com.example.tidemusic.Equalizer) },
                            onSleepTimer = { onNavigate(com.example.tidemusic.SleepTimer) },
                            onSettings = { onNavigate(com.example.tidemusic.Settings) },
                            onEditTags = { showTagEditorDialog = true },
                            onBookmarks = { showBookmarksDialog = true },
                            onAbRepeat = { showAbRepeatDialog = true },
                            onShowLyrics = { showLyricsDialog = true },
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Seek bar
                var sliderPosition by remember { mutableStateOf<Float?>(null) }
                val maxDuration = displayDuration.toFloat().coerceAtLeast(1f)
                val currentValue = sliderPosition ?: position.toFloat().coerceIn(0f, maxDuration)
                val fraction = (currentValue / maxDuration).coerceIn(0f, 1f)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = formatDuration(sliderPosition?.toLong() ?: position),
                        color = TideColors.textPrimary.copy(alpha = 0.8f),
                        fontSize = 13.sp,
                        modifier = Modifier.width(42.dp),
                    )
                    val trackActive = TideColors.textPrimary
                    val trackInactive = TideColors.textPrimary.copy(alpha = 0.25f)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(32.dp)
                            .pointerInput(maxDuration) {
                                detectHorizontalDragGestures(
                                    onDragStart = { offset ->
                                        sliderPosition = (offset.x / size.width) * maxDuration
                                    },
                                    onDragEnd = {
                                        sliderPosition?.let {
                                            controller?.seekTo(it.toLong())
                                            position = it.toLong()
                                        }
                                        sliderPosition = null
                                    },
                                    onDragCancel = { sliderPosition = null },
                                    onHorizontalDrag = { _, dragAmount ->
                                        val current = sliderPosition ?: position.toFloat()
                                        val delta = (dragAmount / size.width) * maxDuration
                                        sliderPosition = (current + delta).coerceIn(0f, maxDuration)
                                    },
                                )
                            }
                            .pointerInput(maxDuration) {
                                detectTapGestures { offset ->
                                    val tapped = (offset.x / size.width) * maxDuration
                                    controller?.seekTo(tapped.toLong())
                                    position = tapped.toLong()
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        val isDragging = sliderPosition != null
                        val animatedThumbRadius by animateDpAsState(
                            targetValue = if (isDragging) 9.dp else 7.dp,
                            label = "playerThumbRadius"
                        )
                        val animatedTrackHeight by animateDpAsState(
                            targetValue = if (isDragging) 4.5.dp else 2.5.dp,
                            label = "playerTrackHeight"
                        )

                        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                            val trackY = size.height / 2f
                            val trackHeight = animatedTrackHeight.toPx()
                            val thumbRadius = animatedThumbRadius.toPx()
                            val activeWidth = size.width * fraction

                            drawLine(
                                color = trackInactive,
                                start = androidx.compose.ui.geometry.Offset(0f, trackY),
                                end = androidx.compose.ui.geometry.Offset(size.width, trackY),
                                strokeWidth = trackHeight,
                                cap = androidx.compose.ui.graphics.StrokeCap.Round,
                            )
                            drawLine(
                                color = trackActive,
                                start = androidx.compose.ui.geometry.Offset(0f, trackY),
                                end = androidx.compose.ui.geometry.Offset(activeWidth, trackY),
                                strokeWidth = trackHeight,
                                cap = androidx.compose.ui.graphics.StrokeCap.Round,
                            )
                            if (isDragging) {
                                // Slightly highlighted translucent white halo around the main circle
                                drawCircle(
                                    color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.25f),
                                    radius = thumbRadius + 7.dp.toPx(),
                                    center = androidx.compose.ui.geometry.Offset(activeWidth, trackY),
                                )
                            }
                            drawCircle(
                                color = androidx.compose.ui.graphics.Color.White,
                                radius = thumbRadius,
                                center = androidx.compose.ui.geometry.Offset(activeWidth, trackY),
                            )
                        }
                    }
                    Text(
                        text = formatDuration(displayDuration),
                        color = TideColors.textPrimary.copy(alpha = 0.8f),
                        fontSize = 13.sp,
                        modifier = Modifier.width(42.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.End,
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Transport controls: Previous, Play/Pause, Next
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = { ServiceLocator.playbackController.previous() },
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(Icons.Rounded.SkipPrevious, "Previous", tint = TideColors.textPrimary, modifier = Modifier.size(38.dp))
                    }
                    IconButton(
                        onClick = { ServiceLocator.playbackController.togglePlayPause() },
                        modifier = Modifier.size(72.dp),
                    ) {
                        Icon(
                            if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            if (isPlaying) "Pause" else "Play",
                            tint = TideColors.textPrimary,
                            modifier = Modifier.size(56.dp),
                        )
                    }
                    IconButton(
                        onClick = { ServiceLocator.playbackController.next() },
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(Icons.Rounded.SkipNext, "Next", tint = TideColors.textPrimary, modifier = Modifier.size(38.dp))
                    }
                }

                Spacer(modifier = Modifier.height(22.dp))

                // Always-open sound slider at the bottom of the Player section
                val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager }
                var currentVol by remember {
                    mutableFloatStateOf((audioManager?.getStreamVolume(android.media.AudioManager.STREAM_MUSIC) ?: 0).toFloat())
                }
                val maxVol = remember {
                    (audioManager?.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC) ?: 15).toFloat()
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = {
                            val newVol = if (currentVol > 0f) 0 else (maxVol * 0.3f).toInt()
                            currentVol = newVol.toFloat()
                            audioManager?.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, newVol, 0)
                        },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector = if (currentVol == 0f) Icons.AutoMirrored.Rounded.VolumeMute else Icons.AutoMirrored.Rounded.VolumeDown,
                            contentDescription = "Mute/Unmute",
                            tint = TideColors.textSecondary,
                            modifier = Modifier.size(20.dp),
                        )
                    }

                    Spacer(Modifier.width(8.dp))

                    com.example.tidemusic.ui.common.TideScrubSlider(
                        value = currentVol,
                        range = 0f..maxVol,
                        onValueChange = { newVolFloat ->
                            val newVol = newVolFloat.toInt().coerceIn(0, maxVol.toInt())
                            currentVol = newVolFloat
                            audioManager?.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, newVol, 0)
                        },
                        modifier = Modifier.weight(1f),
                        activeColor = TideColors.textPrimary,
                        touchHeight = 32.dp,
                        restTrackHeight = 3.dp,
                        dragTrackHeight = 5.dp,
                        restThumbRadius = 5.5.dp,
                        dragThumbRadius = 8.5.dp,
                    )

                    Spacer(Modifier.width(8.dp))

                    IconButton(
                        onClick = {
                            val newVol = maxVol.toInt()
                            currentVol = newVol.toFloat()
                            audioManager?.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, newVol, 0)
                        },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.VolumeUp,
                            contentDescription = "Max Volume",
                            tint = TideColors.textSecondary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }

    if (showInfoSheet) {
        ModalBottomSheet(
            onDismissRequest = { showInfoSheet = false },
            sheetState = infoSheetState,
            containerColor = TideColors.surfaceElevated,
        ) {
            SongInfoContent(
                song = s,
                playbackDurationMs = displayDuration,
                onDone = { showInfoSheet = false },
                onEditTags = {
                    showInfoSheet = false
                    showTagEditorDialog = true
                },
            )
        }
    }

    if (showTagEditorDialog) {
        TagEditorDialog(
            song = s,
            onDismiss = { showTagEditorDialog = false },
            onSave = { title, artist, album ->
                viewModel.updateTags(s.id, title, artist, album)
                scope.launch {
                    com.example.tidemusic.util.FileTagWriter.writeTags(context, s, title, artist, album)
                }
                showTagEditorDialog = false
                Toast.makeText(context, "Tags updated", Toast.LENGTH_SHORT).show()
            },
        )
    }

    if (showBookmarksDialog) {
        BookmarksDialog(
            song = s,
            currentPositionMs = position,
            onSeekTo = { targetMs ->
                controller?.seekTo(targetMs)
                position = targetMs
            },
            onDismiss = { showBookmarksDialog = false },
        )
    }

    if (showLyricsDialog) {
        LyricsDialog(
            song = s,
            onDismiss = { showLyricsDialog = false },
        )
    }

    if (showAbRepeatDialog) {
        ABRepeatDialog(
            currentPos = position,
            pointA = abPointA,
            pointB = abPointB,
            onSetPointA = { ptA ->
                abPointA = ptA
                Toast.makeText(context, "Point A set to ${formatDuration(ptA)}", Toast.LENGTH_SHORT).show()
            },
            onSetPointB = { ptB ->
                abPointB = ptB
                Toast.makeText(context, "Point B set to ${formatDuration(ptB)}", Toast.LENGTH_SHORT).show()
            },
            onClearLoop = {
                abPointA = null
                abPointB = null
                Toast.makeText(context, "A-B loop cleared", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showAbRepeatDialog = false },
        )
    }

    if (showSpeedDialog) {
        androidx.compose.material3.BasicAlertDialog(
            onDismissRequest = { showSpeedDialog = false },
            content = {
                Column(
                    Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(TideColors.surfaceElevated)
                        .padding(24.dp)
                        .width(300.dp),
                ) {
                    Text("Playback Speed & Pitch", style = MaterialTheme.typography.titleMedium, color = TideColors.textPrimary, fontWeight = FontWeight.Bold)
                    Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Speed: ${String.format(Locale.US, "%.2f", speedValue)}x", color = TideColors.accent, fontWeight = FontWeight.SemiBold)
                        Slider(
                            value = speedValue,
                            onValueChange = { speedValue = it },
                            valueRange = 0.5f..2.0f,
                            steps = 15,
                            modifier = Modifier.fillMaxWidth(),
                            colors = androidx.compose.material3.SliderDefaults.colors(
                                thumbColor = TideColors.accent,
                                activeTrackColor = TideColors.accent,
                            ),
                        )
                        Text("Pitch: ${String.format(Locale.US, "%.2f", pitchValue)}x", color = TideColors.accent, fontWeight = FontWeight.SemiBold)
                        Slider(
                            value = pitchValue,
                            onValueChange = { pitchValue = it },
                            valueRange = 0.5f..2.0f,
                            steps = 15,
                            modifier = Modifier.fillMaxWidth(),
                            colors = androidx.compose.material3.SliderDefaults.colors(
                                thumbColor = TideColors.accent,
                                activeTrackColor = TideColors.accent,
                            ),
                        )
                    }
                    Row(
                        Modifier.padding(top = 24.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = { showSpeedDialog = false }) {
                            Text("Cancel", color = TideColors.textSecondary)
                        }
                        TextButton(
                            onClick = {
                                controller?.playbackParameters = PlaybackParameters(speedValue, pitchValue)
                                showSpeedDialog = false
                            },
                        ) {
                            Text("Apply", color = TideColors.accent, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
        )
    }

    if (showPlaylistDialog) {
        AddToPlaylistDialog(
            song = s,
            playlists = playlists.filterNot { it.isBuiltIn },
            onDismiss = { showPlaylistDialog = false },
            onAdd = { selectedIds ->
                selectedIds.forEach { id -> viewModel.addSongsToPlaylist(id, listOf(s.id)) }
                showPlaylistDialog = false
                Toast.makeText(context, "Added to playlist(s)", Toast.LENGTH_SHORT).show()
            },
            onCreateNew = { name ->
                viewModel.createPlaylist(name, listOf(s.id))
                showPlaylistDialog = false
                Toast.makeText(context, "Playlist created", Toast.LENGTH_SHORT).show()
            },
        )
    }
}

@Composable
private fun PlayerOverflowMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    song: Song,
    onSpeedPitch: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddToPlaylists: () -> Unit,
    onShare: () -> Unit,
    onRingtone: () -> Unit,
    onDelete: () -> Unit,
    onEqualizer: () -> Unit,
    onSleepTimer: () -> Unit,
    onSettings: () -> Unit,
    onEditTags: () -> Unit,
    onBookmarks: () -> Unit,
    onAbRepeat: () -> Unit,
    onShowLyrics: () -> Unit,
    onSongDetails: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lrcPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val lrcText = context.contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() }
                    if (!lrcText.isNullOrBlank()) {
                        val audioFile = java.io.File(song.filePath)
                        val targetLrc = if (audioFile.exists() && audioFile.parentFile != null && audioFile.parentFile.canWrite()) {
                            java.io.File(audioFile.parentFile, "${audioFile.nameWithoutExtension}.lrc")
                        } else {
                            val tideDir = java.io.File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MUSIC), "TideMusic")
                            if (!tideDir.exists()) tideDir.mkdirs()
                            java.io.File(tideDir, "${song.title.ifBlank { "song" }}.lrc")
                        }
                        targetLrc.writeText(lrcText)
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            android.widget.Toast.makeText(context, "Lyrics attached successfully!", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        android.widget.Toast.makeText(context, "Failed to attach lyrics: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    CenteredOverflowMenu(
        visible = expanded,
        onDismiss = onDismiss,
        title = "More Options",
    ) {
        CenteredMenuItem(Icons.Rounded.Description, "Add / Embed Lyrics (.lrc)") {
            onDismiss()
            lrcPickerLauncher.launch(arrayOf("*/*", "text/*", "application/octet-stream"))
        }
        val autoScrollSetting by com.example.tidemusic.di.ServiceLocator.settingsManager.isLyricsAutoScrollEnabled.collectAsState()
        CenteredMenuItem(
            if (autoScrollSetting) Icons.Rounded.Autorenew else Icons.AutoMirrored.Rounded.FormatAlignLeft,
            if (autoScrollSetting) "Lyrics Auto-Scroll: ON" else "Lyrics Auto-Scroll: OFF"
        ) {
            onDismiss()
            val next = !autoScrollSetting
            com.example.tidemusic.di.ServiceLocator.settingsManager.setLyricsAutoScrollEnabled(next)
            android.widget.Toast.makeText(context, if (next) "Lyrics Auto-Scroll: ON" else "Lyrics Auto-Scroll: OFF", android.widget.Toast.LENGTH_SHORT).show()
        }
        CenteredMenuItem(Icons.Rounded.MusicNote, "Show lyrics") {
            onDismiss()
            onShowLyrics()
        }
        CenteredMenuItem(Icons.Rounded.Bookmark, "Bookmarks/Notes") {
            onDismiss()
            onBookmarks()
        }
        CenteredMenuItem(Icons.Rounded.Repeat, "A-B repeat") {
            onDismiss()
            onAbRepeat()
        }
        CenteredMenuItem(Icons.Rounded.Speed, "Play speed and Pitch") {
            onDismiss()
            onSpeedPitch()
        }
        CenteredMenuDivider()
        CenteredMenuItem(Icons.Rounded.Queue, "Add to a queue") {
            onDismiss()
            onAddToQueue()
        }
        CenteredMenuItem(Icons.Rounded.PlaylistAdd, "Add to playlists") {
            onDismiss()
            onAddToPlaylists()
        }
        CenteredMenuItem(Icons.Rounded.Info, "Song details") {
            onDismiss()
            onSongDetails()
        }
        CenteredMenuDivider()
        CenteredMenuItem(Icons.Rounded.Edit, "Edit tags") {
            onDismiss()
            onEditTags()
        }
        CenteredMenuItem(Icons.Rounded.Notifications, "Set as ringtone") {
            onDismiss()
            onRingtone()
        }
        CenteredMenuItem(Icons.Rounded.Share, "Share song") {
            onDismiss()
            onShare()
        }
        CenteredMenuItem(Icons.Rounded.Delete, "Delete permanently", destructive = true) {
            onDismiss()
            onDelete()
        }
        CenteredMenuDivider()
        CenteredMenuItem(Icons.Rounded.Equalizer, "Equalizer") {
            onDismiss()
            onEqualizer()
        }
        CenteredMenuItem(Icons.Rounded.Timer, "Sleep Timer") {
            onDismiss()
            onSleepTimer()
        }
        CenteredMenuItem(Icons.Rounded.Settings, "Settings") {
            onDismiss()
            onSettings()
        }
    }
}

@Composable
private fun LyricsDialog(
    song: Song,
    onDismiss: () -> Unit,
) {
    androidx.compose.material3.BasicAlertDialog(
        onDismissRequest = onDismiss,
        content = {
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(TideColors.surfaceElevated)
                    .padding(24.dp)
                    .width(320.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.MusicNote, null, tint = TideColors.accent)
                    Spacer(Modifier.width(10.dp))
                    Text("Lyrics", style = MaterialTheme.typography.titleMedium, color = TideColors.textPrimary, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "No embedded lyrics found in \"${song.title}\".",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TideColors.textSecondary,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
                Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text("Close", color = TideColors.accent, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
    )
}

@Composable
private fun ABRepeatDialog(
    currentPos: Long,
    pointA: Long?,
    pointB: Long?,
    onSetPointA: (Long) -> Unit,
    onSetPointB: (Long) -> Unit,
    onClearLoop: () -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.compose.material3.BasicAlertDialog(
        onDismissRequest = onDismiss,
        content = {
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(TideColors.surfaceElevated)
                    .padding(24.dp)
                    .width(320.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Repeat, null, tint = TideColors.accent)
                    Spacer(Modifier.width(10.dp))
                    Text("A-B Loop Repeat", style = MaterialTheme.typography.titleMedium, color = TideColors.textPrimary, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "Repeat a specific section of the track seamlessly.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TideColors.textSecondary,
                )
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Point A: ${pointA?.let { formatDuration(it) } ?: "Not set"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (pointA != null) TideColors.accent else TideColors.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    androidx.compose.material3.OutlinedButton(onClick = { onSetPointA(currentPos) }) {
                        Text("Set A (${formatDuration(currentPos)})")
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Point B: ${pointB?.let { formatDuration(it) } ?: "Not set"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (pointB != null) TideColors.accent else TideColors.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    androidx.compose.material3.OutlinedButton(onClick = { onSetPointB(currentPos) }) {
                        Text("Set B (${formatDuration(currentPos)})")
                    }
                }
                Spacer(Modifier.height(20.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = onClearLoop) {
                        Text("Clear Loop", color = TideColors.error)
                    }
                    TextButton(onClick = onDismiss) {
                        Text("Done", color = TideColors.accent, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
    )
}

@Composable
fun SongInfoContent(
    song: Song,
    playbackDurationMs: Long,
    onDone: () -> Unit,
    onEditTags: () -> Unit,
) {
    val context = LocalContext.current
    val dateFmt = remember { SimpleDateFormat("d MMMM yyyy HH:mm", Locale.getDefault()) }
    val fileName = remember(song.filePath, song.title) {
        when {
            song.filePath.isNotBlank() -> java.io.File(song.filePath).name
            song.uri.isNotBlank() -> song.uri.substringAfterLast('/').substringBefore('?')
            else -> "${song.title}.mp3"
        }
    }
    val location = remember(song.filePath) {
        if (song.filePath.isBlank()) "Unknown location"
        else {
            val parent = java.io.File(song.filePath).parent ?: song.filePath
            parent
                .replace("/storage/emulated/0", "Internal Storage")
                .replace("/sdcard", "Internal Storage")
                .trim('/')
                .replace("/", " › ")
                .ifBlank { parent }
        }
    }
    val formatLabel = remember(song.mimeType, fileName) {
        val ext = fileName.substringAfterLast('.', "").uppercase(Locale.getDefault())
        when {
            ext.isNotBlank() -> ext.replaceFirstChar { it.titlecase(Locale.getDefault()) }
            song.mimeType.contains("mpeg", true) -> "Mp3"
            song.mimeType.contains("flac", true) -> "Flac"
            song.mimeType.contains("mp4", true) || song.mimeType.contains("aac", true) -> "M4a"
            song.mimeType.contains("ogg", true) -> "Ogg"
            song.mimeType.isNotBlank() -> song.mimeType.substringAfter('/')
            else -> "Unknown"
        }
    }
    val encoding = remember(formatLabel, song.mimeType) {
        when {
            formatLabel.equals("Mp3", true) || song.mimeType.contains("mpeg", true) -> "MPEG-1 Layer 3"
            formatLabel.equals("Flac", true) -> "FLAC"
            formatLabel.equals("M4a", true) || formatLabel.equals("Aac", true) -> "AAC"
            formatLabel.equals("Ogg", true) -> "Ogg Vorbis"
            formatLabel.equals("Wav", true) -> "PCM"
            else -> song.mimeType.ifBlank { "—" }
        }
    }
    val sizeLabel = remember(song.size) {
        if (song.size <= 0L) "—"
        else {
            val mb = song.size / (1024.0 * 1024.0)
            String.format(Locale.US, "%.2f MB", mb)
        }
    }
    val durMs = if (playbackDurationMs > 0) playbackDurationMs else song.durationMs

    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(max = 520.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Info, null, tint = TideColors.textSecondary)
            Spacer(Modifier.width(8.dp))
            Text("Song info", style = MaterialTheme.typography.titleMedium, color = TideColors.textPrimary)
        }
        Spacer(Modifier.height(16.dp))

        InfoBlock("File name", fileName)
        InfoBlock("Location", location)
        HorizontalDivider(Modifier.padding(vertical = 10.dp), color = TideColors.outline)
        InfoBlock("Title", song.title.ifBlank { "—" })
        InfoBlock("Album", song.album.ifBlank { "[unknown]" })
        InfoBlock("Artist", song.artist.ifBlank { "[unknown]" })
        InfoBlock("Album-Artist", song.artist.ifBlank { "[unknown]" })
        InfoBlock("Composer", "[unknown]")
        InfoBlock("Genre", "[unknown]")
        InfoBlock("Track number", song.trackNumber.toString())
        InfoBlock("Disc number", song.discNumber.coerceAtLeast(1).toString())
        HorizontalDivider(Modifier.padding(vertical = 10.dp), color = TideColors.outline)
        InfoBlock("Duration", formatDuration(durMs))
        InfoBlock(
            "Bitrate",
            if (song.bitrate > 0) "${song.bitrate} kbps" else "—",
        )
        InfoBlock(
            "Sample rate",
            if (song.sampleRate > 0) "${song.sampleRate} Hz" else "—",
        )
        InfoBlock("Bits per sample", "16")
        InfoBlock("Format", formatLabel)
        InfoBlock("Encoding", encoding)
        InfoBlock("Channels", "Stereo")
        InfoBlock("Size", sizeLabel)
        HorizontalDivider(Modifier.padding(vertical = 10.dp), color = TideColors.outline)
        InfoBlock(
            "Date added to library",
            if (song.dateAdded > 0) dateFmt.format(Date(song.dateAdded * (if (song.dateAdded < 10_000_000_000L) 1000 else 1))) else "—",
        )
        InfoBlock(
            "Date modified",
            if (song.dateModified > 0) dateFmt.format(Date(song.dateModified * (if (song.dateModified < 10_000_000_000L) 1000 else 1))) else "—",
        )
        InfoBlock(
            "Date last played",
            song.lastPlayedTimestamp?.let { ts ->
                dateFmt.format(Date(ts * (if (ts < 10_000_000_000L) 1000 else 1)))
            } ?: "—",
        )
        InfoBlock(
            "Played",
            when {
                song.playCount <= 0 -> "Never"
                song.playCount == 1 -> "1 time before"
                else -> "${song.playCount} times before"
            },
        )
        if (song.filePath.isNotBlank()) {
            InfoBlock("Full path", song.filePath)
        }
        if (song.uri.isNotBlank()) {
            InfoBlock("URI", song.uri)
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TextButton(onClick = onEditTags) {
                Text("EDIT TAGS", color = TideColors.accent, fontWeight = FontWeight.Bold)
            }
            TextButton(onClick = onDone) {
                Text("DONE", color = TideColors.accent, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun InfoBlock(label: String, value: String) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = TideColors.textPrimary, fontWeight = FontWeight.SemiBold)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = TideColors.textSecondary)
    }
}

@Composable
fun AddToPlaylistDialog(
    song: Song,
    playlists: List<Playlist>,
    onDismiss: () -> Unit,
    onAdd: (Set<Long>) -> Unit,
    onCreateNew: (String) -> Unit,
) {
    var selected by remember { mutableStateOf(setOf<Long>()) }
    var showNewName by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }

    androidx.compose.material3.BasicAlertDialog(
        onDismissRequest = onDismiss,
        content = {
            Column(
                Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(TideColors.surfaceElevated)
                    .padding(20.dp)
                    .width(320.dp)
                    .heightIn(max = 480.dp),
            ) {
                Text(
                    song.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = TideColors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(12.dp))
                Text("Add to playlists", style = MaterialTheme.typography.titleMedium, color = TideColors.accent)
                Spacer(Modifier.height(8.dp))
                Column(
                    Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                ) {
                    playlists.forEach { pl ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selected = if (pl.id in selected) selected - pl.id else selected + pl.id
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = pl.id in selected,
                                onCheckedChange = {
                                    selected = if (it) selected + pl.id else selected - pl.id
                                },
                            )
                            Text(pl.name, color = TideColors.textPrimary, modifier = Modifier.padding(start = 4.dp))
                        }
                    }
                    if (playlists.isEmpty()) {
                        Text(
                            "No custom playlists yet.",
                            color = TideColors.textSecondary,
                            modifier = Modifier.padding(vertical = 12.dp),
                        )
                    }
                }
                if (showNewName) {
                    androidx.compose.material3.OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Playlist name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                } else {
                    TextButton(onClick = { showNewName = true }) {
                        Text("+  New Playlist", color = TideColors.textPrimary)
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("CANCEL", color = TideColors.accent)
                    }
                    TextButton(
                        onClick = {
                            if (showNewName && newName.isNotBlank()) {
                                onCreateNew(newName.trim())
                            } else {
                                onAdd(selected)
                            }
                        },
                    ) {
                        Text("ADD", color = TideColors.accent, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
    )
}

@Composable
fun LyricsCanvasView(
    song: Song,
    positionMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var lyrics by remember(song.id) { mutableStateOf<SongLyrics?>(null) }
    var isLoading by remember(song.id) { mutableStateOf(true) }
    var isAutoScroll by remember(song.id) { mutableStateOf(true) }

    LaunchedEffect(song.id) {
        isLoading = true
        lyrics = LyricsHelper.loadLyrics(song)
        isLoading = false
        isAutoScroll = true
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black.copy(alpha = 0.58f))
            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        val currentLyrics = lyrics
        if (isLoading) {
            CircularProgressIndicator(
                color = TideColors.accent,
                modifier = Modifier.size(32.dp),
                strokeWidth = 2.dp,
            )
        } else if (currentLyrics == null || currentLyrics.isEmpty) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(16.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.MusicNote,
                    contentDescription = null,
                    tint = TideColors.textSecondary.copy(alpha = 0.5f),
                    modifier = Modifier.size(44.dp),
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "No Subtitles or Lyrics Found",
                    style = MaterialTheme.typography.titleMedium,
                    color = TideColors.textPrimary,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "No subtitles or lyrics were found on the source video or attached to this file",
                    style = MaterialTheme.typography.bodySmall,
                    color = TideColors.textSecondary,
                    textAlign = TextAlign.Center,
                )
            }
        } else if (currentLyrics.isSynced) {
            val listState = rememberLazyListState()
            val activeIndex = remember(positionMs, currentLyrics.lines) {
                val idx = currentLyrics.lines.indexOfLast { it.timestampMs <= positionMs }
                if (idx >= 0) idx else 0
            }

            // Auto-scroll to center active lyric whenever song progresses, seek occurs, or user releases touch
            LaunchedEffect(activeIndex, isAutoScroll, listState.isScrollInProgress) {
                if (isAutoScroll && !listState.isScrollInProgress && activeIndex in currentLyrics.lines.indices) {
                    try {
                        listState.animateScrollToItem(
                            index = activeIndex,
                            scrollOffset = 0,
                        )
                    } catch (_: Exception) {}
                }
            }

            BoxWithConstraints(Modifier.fillMaxSize()) {
                val verticalCenterPadding = (maxHeight / 2 - 28.dp).coerceAtLeast(16.dp)

                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = verticalCenterPadding, bottom = verticalCenterPadding),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    itemsIndexed(currentLyrics.lines, key = { index, item -> "${index}_${item.timestampMs}" }) { index, line ->
                        val isActive = index == activeIndex
                        val textColor by androidx.compose.animation.animateColorAsState(
                            targetValue = if (isActive) Color.White else Color.White.copy(alpha = 0.32f),
                            animationSpec = tween(durationMillis = 280),
                            label = "lyricColor"
                        )

                        Text(
                            text = line.text,
                            style = if (isActive) {
                                MaterialTheme.typography.titleMedium.copy(
                                    fontSize = 19.sp,
                                    fontWeight = FontWeight.Bold,
                                    lineHeight = 26.sp,
                                )
                            } else {
                                MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    lineHeight = 22.sp,
                                )
                            },
                            color = textColor,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSeek(line.timestampMs)
                                    isAutoScroll = true
                                }
                                .padding(vertical = 4.dp, horizontal = 8.dp),
                            textAlign = TextAlign.Center,
                        )
                    }
                }

                // Single toggle action button:
                // When in Auto mode -> Shows "Manual" (clicking it enters Manual mode)
                // When in Manual mode -> Shows "Auto-Scroll" (clicking it enters Auto mode)
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isAutoScroll) TideColors.accent.copy(alpha = 0.24f) else Color.White.copy(alpha = 0.16f))
                        .clickable {
                            isAutoScroll = !isAutoScroll
                        }
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = if (isAutoScroll) Icons.AutoMirrored.Rounded.FormatAlignLeft else Icons.Rounded.Autorenew,
                        contentDescription = null,
                        tint = if (isAutoScroll) TideColors.accent else Color.White,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = if (isAutoScroll) "Manual" else "Auto-Scroll",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                        color = if (isAutoScroll) TideColors.accent else Color.White,
                    )
                }
            }
        } else {
            // Plain text unsynchronized lyrics
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(currentLyrics.plainLines) { line ->
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 15.sp,
                            lineHeight = 22.sp,
                        ),
                        color = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
