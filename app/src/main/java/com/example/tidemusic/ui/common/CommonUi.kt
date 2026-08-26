package com.example.tidemusic.ui.common

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.ui.draw.blur
import androidx.compose.foundation.basicMarquee
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Spacer
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import coil3.compose.AsyncImage
import com.example.tidemusic.domain.Song
import com.example.tidemusic.theme.TideColors
import com.example.tidemusic.ui.LocalMediaController
import com.example.tidemusic.util.PlaceholderArt
import com.example.tidemusic.util.formatDuration
import com.example.tidemusic.util.orUnknown

@Composable
fun ThinTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null
) {
    val borderColor = TideColors.outline
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(TideColors.background)
            .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
            .height(48.dp)
            .drawBehind {
                val y = size.height
                drawLine(borderColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
            }
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(androidx.compose.material.icons.Icons.Rounded.ArrowBack, null, tint = TideColors.textPrimary)
            }
        } else {
            androidx.compose.foundation.layout.Spacer(Modifier.width(12.dp))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = TideColors.textPrimary,
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (trailing != null) {
            trailing()
        }
    }
}

/**
 * Persistent glass mini-player bar (spec Section 5 / 2). Haze-blurred, hairline border,
 * pinned above the tab bar. Only visible when a queue is loaded. Tapping opens the
 * full-screen Player screen.
 *
 * Lifts the live current item / play state from the controller via a simple polling effect
 * — no Flow factories involved, so listeners are contained within the controller's lifetime
 * and replaced when the controller is rebuilt (when MainActivity reconnects on process
 * recreation).
 */
@Composable
fun MiniPlayerBar(onClick: () -> Unit) {
    val controller = LocalMediaController.current ?: return

    var currentItem by remember { mutableStateOf<MediaItem?>(controller.currentMediaItem) }
    var isPlaying by remember { mutableStateOf(controller.isPlaying) }
    var position by remember { mutableFloatStateOf(controller.currentPosition.toFloat()) }
    var duration by remember { mutableFloatStateOf(controller.duration.coerceAtLeast(0L).toFloat()) }

    LaunchedEffect(controller) {
        controller.removeListener(controllerListener)
        controllerListener = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                currentItem = mediaItem
                position = controller.currentPosition.toFloat()
                duration = controller.duration.coerceAtLeast(0L).toFloat()
            }
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                position = controller.currentPosition.toFloat()
                duration = controller.duration.coerceAtLeast(0L).toFloat()
            }
        }
        controller.addListener(controllerListener)
        currentItem = controller.currentMediaItem
        isPlaying = controller.isPlaying
        position = controller.currentPosition.toFloat()
        duration = controller.duration.coerceAtLeast(0L).toFloat()
    }

    LaunchedEffect(controller, isPlaying) {
        if (isPlaying) {
            while (true) {
                position = controller.currentPosition.toFloat()
                duration = controller.duration.coerceAtLeast(0L).toFloat()
                kotlinx.coroutines.delay(500L)
            }
        }
    }

    val song = currentItem?.let(::mediaItemToSongDomain)

    val artworkModel = song?.let {
        remember(it.id, it.filePath, it.uri, it.dateModified) {
            com.example.tidemusic.util.SongArtworkRequest(
                songId = it.id,
                filePath = it.filePath,
                uriString = it.uri,
                dateModified = it.dateModified,
            )
        }
    }
    var hasArtwork by remember(song?.id) { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(68.dp)
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.Black)
            .border(
                width = 1.dp,
                color = if (hasArtwork) Color.White.copy(alpha = 0.16f) else TideColors.outline,
                shape = RoundedCornerShape(14.dp),
            )
            .clickable(onClick = onClick),
    ) {
        // Frosted glass blurred background of song artwork.
        // Overscanned slightly so the blur doesn't produce dark fringes at the edges,
        // and scaled up so a stronger radius still fills the bar completely.
        if (artworkModel != null) {
            AsyncImage(
                model = artworkModel,
                contentDescription = null,
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        scaleX = 1.35f
                        scaleY = 1.35f
                    }
                    .blur(40.dp),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                onState = { state ->
                    hasArtwork = state is coil3.compose.AsyncImagePainter.State.Success
                },
            )
        }

        // Tint overlay: dark translucent if artwork is active, pitch black if not
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    if (hasArtwork) Color(0xFF141414).copy(alpha = 0.84f)
                    else Color.Black
                ),
        )

        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (song != null) {
                    // Small cover image / default vinyl record window
                    ArtworkTile(song = song, size = 46.dp, rounded = 8.dp)

                    // Title & tiny artist text
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 10.dp),
                    ) {
                        Text(
                            text = song.title.orUnknown(),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                            ),
                            color = TideColors.textPrimary,
                            maxLines = 1,
                            modifier = Modifier.basicMarquee(),
                        )
                        Spacer(Modifier.height(1.dp))
                        Text(
                            text = song.artist.orUnknown(),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 11.sp,
                            ),
                            color = TideColors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    // 3 Main buttons: Previous, Play/Pause, Next + Shuffle toggle button
                    var shuffleEnabled by remember { mutableStateOf(controller?.shuffleModeEnabled ?: false) }
                    LaunchedEffect(controller) {
                        shuffleEnabled = controller?.shuffleModeEnabled ?: false
                    }

                    IconButton(
                        onClick = { com.example.tidemusic.di.ServiceLocator.playbackController.previous() },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SkipPrevious,
                            contentDescription = "Previous",
                            tint = TideColors.textPrimary,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    IconButton(
                        onClick = { com.example.tidemusic.di.ServiceLocator.playbackController.togglePlayPause() },
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = TideColors.accent,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                    IconButton(
                        onClick = { com.example.tidemusic.di.ServiceLocator.playbackController.next() },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SkipNext,
                            contentDescription = "Next",
                            tint = TideColors.textPrimary,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    IconButton(
                        onClick = {
                            val next = !shuffleEnabled
                            shuffleEnabled = next
                            com.example.tidemusic.di.ServiceLocator.playbackController.setShuffleMode(next)
                        },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Shuffle,
                            contentDescription = "Shuffle",
                            tint = if (shuffleEnabled) TideColors.accent else TideColors.textSecondary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(TideColors.outline),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.QueueMusic, null, tint = TideColors.textSecondary, modifier = Modifier.size(24.dp))
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 10.dp),
                    ) {
                        Text("Not Playing", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, fontSize = 14.sp), color = TideColors.textPrimary)
                        Text("Tap to open player", style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp), color = TideColors.textSecondary)
                    }
                    // No play button here: with an empty queue there is nothing to play —
                    // a decorative dead button only misleads (removed).
                }
            }

            // Interactive timeline seek bar with expanding knob & glowing halo
            if (song != null && duration > 0f) {
                var isDraggingTimeline by remember { mutableStateOf(false) }
                var dragFraction by remember { mutableFloatStateOf(0f) }
                val currentFraction = (position / duration.coerceAtLeast(1f)).coerceIn(0f, 1f)
                val displayFraction = if (isDraggingTimeline) dragFraction else currentFraction

                val trackHeight by animateDpAsState(
                    targetValue = if (isDraggingTimeline) 4.5.dp else 3.dp,
                    label = "miniTrackHeight"
                )
                val knobSize by animateDpAsState(
                    targetValue = if (isDraggingTimeline) 13.dp else 8.dp,
                    label = "miniKnobSize"
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(16.dp)
                        .padding(horizontal = 8.dp)
                        .pointerInput(duration) {
                            detectTapGestures(
                                onPress = { offset ->
                                    isDraggingTimeline = true
                                    val frac = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                                    dragFraction = frac
                                    val targetMs = (frac * duration).toLong()
                                    controller.seekTo(targetMs)
                                    tryAwaitRelease()
                                    isDraggingTimeline = false
                                }
                            )
                        }
                        .pointerInput(duration) {
                            detectHorizontalDragGestures(
                                onDragStart = { offset ->
                                    isDraggingTimeline = true
                                    dragFraction = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                                },
                                onDragEnd = {
                                    val targetMs = (dragFraction * duration).toLong()
                                    controller.seekTo(targetMs)
                                    isDraggingTimeline = false
                                },
                                onDragCancel = {
                                    isDraggingTimeline = false
                                },
                                onHorizontalDrag = { change, _ ->
                                    change.consume()
                                    val frac = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                                    dragFraction = frac
                                }
                            )
                        },
                    contentAlignment = Alignment.CenterStart
                ) {
                    // Inactive background track line
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(trackHeight)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color.White.copy(alpha = 0.22f))
                    )

                    // Active elapsed track line
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(displayFraction)
                            .height(trackHeight)
                            .clip(RoundedCornerShape(2.dp))
                            .background(TideColors.accent)
                    )

                    // Expanding round knob + glowing halo
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(displayFraction)
                            .wrapContentWidth(Alignment.End),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isDraggingTimeline) {
                            Box(
                                modifier = Modifier
                                    .offset(x = (knobSize + 8.dp) / 2)
                                    .size(knobSize + 8.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.25f))
                            )
                        }
                        Box(
                            modifier = Modifier
                                .offset(x = knobSize / 2)
                                .size(knobSize)
                                .clip(CircleShape)
                                .background(Color.White)
                        )
                    }
                }
                Spacer(Modifier.height(5.dp))
            }
        }
    }
}

/** Lifecycle-managed listener for the mini player (released when composition leaves). */
private var controllerListener: Player.Listener = object : Player.Listener {}

/**
 * Resolves album art via Coil. When artwork is missing, shows a black tile with a dark grey-scaled vinyl disc.
 */
@Composable
fun ArtworkTile(song: Song, size: Dp, rounded: Dp = 6.dp) {
    val artworkModel = remember(song.id, song.filePath, song.uri, song.dateModified) {
        com.example.tidemusic.util.SongArtworkRequest(
            songId = song.id,
            filePath = song.filePath,
            uriString = song.uri,
            dateModified = song.dateModified,
        )
    }
    var imageSuccess by remember(song.id) { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(rounded))
            .background(Color.Black, shape = RoundedCornerShape(rounded)),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = artworkModel,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            onState = { state ->
                imageSuccess = state is coil3.compose.AsyncImagePainter.State.Success
            },
        )
        if (!imageSuccess) {
            VinylDiscPlaceholder(modifier = Modifier.fillMaxSize(0.72f), spinning = false)
        }
    }
}

/** Large player artwork: real cover art, or a rotating dark grey-scaled vinyl disc on black. */
@Composable
fun PlayerArtwork(song: Song, size: Dp, isPlaying: Boolean = true) {
    val artworkModel = remember(song.id, song.filePath, song.uri, song.dateModified) {
        com.example.tidemusic.util.SongArtworkRequest(
            songId = song.id,
            filePath = song.filePath,
            uriString = song.uri,
            dateModified = song.dateModified,
        )
    }
    var imageSuccess by remember(song.id) { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.Black, shape = RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = artworkModel,
            contentDescription = song.title,
            modifier = Modifier.fillMaxSize(),
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            onState = { state ->
                imageSuccess = state is coil3.compose.AsyncImagePainter.State.Success
            },
        )
        if (!imageSuccess) {
            VinylDiscPlaceholder(
                modifier = Modifier.fillMaxSize(0.78f),
                spinning = isPlaying,
            )
        }
    }
}

/** Black vinyl disc with subtle monochromatic white/grey rings; optional rotation while playing. */
@Composable
fun VinylDiscPlaceholder(modifier: Modifier = Modifier, spinning: Boolean = false) {
    val infinite = rememberInfiniteTransition(label = "vinyl")
    val angle by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "vinylSpin",
    )
    val rotation = if (spinning) angle else 0f
    androidx.compose.foundation.Canvas(
        modifier = modifier.graphicsLayer { rotationZ = rotation },
    ) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r = minOf(size.width, size.height) / 2f
        // Disc body (charcoal black)
        drawCircle(color = Color(0xFF141414), radius = r, center = Offset(cx, cy))
        // Outer subtle white outline
        drawCircle(
            color = Color.White.copy(alpha = 0.22f),
            radius = r,
            center = Offset(cx, cy),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx()),
        )
        // Monochromatic groove rings
        val rings = listOf(0.82f, 0.68f, 0.54f, 0.40f)
        for (f in rings) {
            drawCircle(
                color = Color.White.copy(alpha = 0.08f),
                radius = r * f,
                center = Offset(cx, cy),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx()),
            )
        }
        // Label / spindle (dark grey-scaled)
        drawCircle(color = Color(0xFF262626), radius = r * 0.22f, center = Offset(cx, cy))
        drawCircle(
            color = Color.White.copy(alpha = 0.3f),
            radius = r * 0.22f,
            center = Offset(cx, cy),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx()),
        )
        drawCircle(color = Color(0xFF6B6B6B), radius = r * 0.045f, center = Offset(cx, cy))
    }
}

/** Reduces the live MediaItem → domain Song for the mini-player / Player-screen display. */
fun mediaItemToSongDomain(item: MediaItem): Song {
    val id = item.mediaId.toLongOrNull() ?: 0L
    val meta = item.mediaMetadata
    val extras = meta.extras
    // tide-art:// is our internal media-session artwork scheme (resolved by
    // TideArtworkBitmapLoader) — not a directly loadable image URI, so keep it out of the
    // domain model; UI artwork goes through SongArtworkRequest (file path based) instead.
    val artwork = extras?.getString(com.example.tidemusic.playback.PlaybackController.EXTRA_ARTWORK_URI)
        ?.takeIf { !it.startsWith("tidemusic://") && !it.startsWith("tide-art://") }
        ?: meta.artworkUri?.toString()?.takeIf { !it.startsWith("tidemusic://") && !it.startsWith("tide-art://") }
    return Song(
        id = id,
        filePath = extras?.getString(com.example.tidemusic.playback.PlaybackController.EXTRA_FILE_PATH).orEmpty(),
        uri = item.localConfiguration?.uri?.toString() ?: "",
        title = meta.title?.toString() ?: "",
        artist = meta.artist?.toString() ?: "",
        album = meta.albumTitle?.toString() ?: "",
        durationMs = extras?.getLong(com.example.tidemusic.playback.PlaybackController.EXTRA_DURATION_MS) ?: 0L,
        dateAdded = extras?.getLong(com.example.tidemusic.playback.PlaybackController.EXTRA_DATE_ADDED) ?: 0L,
        dateModified = extras?.getLong(com.example.tidemusic.playback.PlaybackController.EXTRA_DATE_MODIFIED) ?: 0L,
        size = extras?.getLong(com.example.tidemusic.playback.PlaybackController.EXTRA_SIZE) ?: 0L,
        mimeType = extras?.getString(com.example.tidemusic.playback.PlaybackController.EXTRA_MIME_TYPE).orEmpty(),
        bitrate = extras?.getInt(com.example.tidemusic.playback.PlaybackController.EXTRA_BITRATE) ?: 0,
        sampleRate = extras?.getInt(com.example.tidemusic.playback.PlaybackController.EXTRA_SAMPLE_RATE) ?: 0,
        trackNumber = meta.trackNumber ?: 0,
        discNumber = meta.discNumber ?: 0,
        playCount = extras?.getInt(com.example.tidemusic.playback.PlaybackController.EXTRA_PLAY_COUNT) ?: 0,
        lastPlayedTimestamp = extras?.getLong(com.example.tidemusic.playback.PlaybackController.EXTRA_LAST_PLAYED)
            ?.takeIf { extras.containsKey(com.example.tidemusic.playback.PlaybackController.EXTRA_LAST_PLAYED) },
        isFavorite = false,
        folderId = null,
        artworkUri = artwork,
    )
}

/** Single shared row layout for song lists (queue, playlist detail, search results).
 *  Supports optional multi-select: pass a [MultiSelectState] to enable long-press selection. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SongRow(
    song: Song,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
    highlight: Boolean = false,
    multiSelect: MultiSelectState? = null,
    titleOverride: String? = null,
    subtitleOverride: String? = null,
) {
    val isSelected = multiSelect?.isSelected(song.id) ?: false
    val bgColor = if (isSelected) TideColors.accent.copy(alpha = 0.15f) else Color.Transparent
    val songRowBorderColor = TideColors.outline
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(bgColor)
            .combinedClickable(
                onClick = {
                    if (multiSelect != null && multiSelect.active) {
                        multiSelect.toggle(song.id)
                    } else {
                        onClick()
                    }
                },
                onLongClick = {
                    multiSelect?.toggle(song.id)
                },
            )
            .drawBehind {
                val y = size.height
                drawLine(songRowBorderColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
            }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (multiSelect != null && multiSelect.active) {
            androidx.compose.material3.Checkbox(
                checked = isSelected,
                onCheckedChange = { multiSelect.toggle(song.id) },
                colors = androidx.compose.material3.CheckboxDefaults.colors(
                    checkedColor = TideColors.accent,
                    uncheckedColor = TideColors.textSecondary,
                ),
            )
        }
        ArtworkTile(song = song, size = 56.dp)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                text = titleOverride ?: song.title.orUnknown(),
                style = MaterialTheme.typography.bodyLarge,
                color = if (highlight) TideColors.accent else TideColors.textPrimary,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitleOverride ?: "${song.artist.orUnknown()} • ${formatDuration(song.durationMs)}",
                style = MaterialTheme.typography.bodySmall,
                color = TideColors.textSecondary,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        if (multiSelect == null || !multiSelect.active) {
            if (trailing != null) {
                trailing()
            } else {
                var menuOpen by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(
                            imageVector = Icons.Rounded.MoreVert,
                            contentDescription = "Song Options",
                            tint = TideColors.textSecondary,
                        )
                    }
                    SongOverflowMenu(
                        song = song,
                        expanded = menuOpen,
                        onDismiss = { menuOpen = false },
                    )
                }
            }
        }
    }
}

@Composable
fun rememberMediaController(): MediaController? = LocalMediaController.current

enum class SortCriteria(val label: String, val group: String) {
    PROBABLE_USAGE("Ranked by Probable Usage (Most Used to Least Used)", "usage"),
    DATE_ADDED_DESC("Date: Latest to Oldest", "date"),
    DATE_ADDED_ASC("Date: Oldest to Latest", "date"),
    
    TITLE_ASC("Title (A-Z)", "title"),
    TITLE_DESC("Title (Z-A)", "title"),
    
    ARTIST_ASC("Artist (A-Z)", "artist"),
    ARTIST_DESC("Artist (Z-A)", "artist"),
    
    ALBUM_ASC("Album", "album"),
    
    RECENTLY_PLAYED("Recently Played", "recently_played"),
    
    MOST_PLAYED("Most Played (Play Count)", "most_played"),
    
    GENRE_ASC("Genre", "genre"),
    
    DURATION_ASC("Duration/Length (Shortest first)", "duration"),
    DURATION_DESC("Duration/Length (Longest first)", "duration"),
    
    LIKED_FIRST("Rating / Liked Songs First", "rating"),
    SIZE_DESC("File Size (Largest first)", "size"),
    BITRATE_DESC("Bitrate / Audio Quality", "quality")
}

fun Song.compareWith(other: Song, criteria: SortCriteria): Int {
    return try {
        when (criteria) {
            SortCriteria.PROBABLE_USAGE -> {
                val playComp = other.playCount.compareTo(this.playCount)
                if (playComp != 0) playComp
                else {
                    val t1 = this.lastPlayedTimestamp ?: 0L
                    val t2 = other.lastPlayedTimestamp ?: 0L
                    t2.compareTo(t1)
                }
            }
            SortCriteria.DATE_ADDED_DESC -> other.dateAdded.compareTo(this.dateAdded)
            SortCriteria.DATE_ADDED_ASC -> this.dateAdded.compareTo(other.dateAdded)
            
            SortCriteria.TITLE_ASC -> this.title.orEmpty().compareTo(other.title.orEmpty(), ignoreCase = true)
            SortCriteria.TITLE_DESC -> other.title.orEmpty().compareTo(this.title.orEmpty(), ignoreCase = true)
            
            SortCriteria.ARTIST_ASC -> this.artist.orEmpty().compareTo(other.artist.orEmpty(), ignoreCase = true)
            SortCriteria.ARTIST_DESC -> other.artist.orEmpty().compareTo(this.artist.orEmpty(), ignoreCase = true)
            
            SortCriteria.ALBUM_ASC -> {
                val albumComp = this.album.orEmpty().compareTo(other.album.orEmpty(), ignoreCase = true)
                if (albumComp != 0) albumComp
                else this.trackNumber.compareTo(other.trackNumber)
            }
            
            SortCriteria.RECENTLY_PLAYED -> {
                val t1 = this.lastPlayedTimestamp ?: 0L
                val t2 = other.lastPlayedTimestamp ?: 0L
                t2.compareTo(t1)
            }
            
            SortCriteria.MOST_PLAYED -> other.playCount.compareTo(this.playCount)
            
            SortCriteria.GENRE_ASC -> this.mimeType.orEmpty().compareTo(other.mimeType.orEmpty(), ignoreCase = true)
            
            SortCriteria.DURATION_ASC -> this.durationMs.compareTo(other.durationMs)
            SortCriteria.DURATION_DESC -> other.durationMs.compareTo(this.durationMs)
            
            SortCriteria.LIKED_FIRST -> {
                val b1 = if (this.isFavorite) 1 else 0
                val b2 = if (other.isFavorite) 1 else 0
                b2.compareTo(b1)
            }
            
            SortCriteria.SIZE_DESC -> other.size.compareTo(this.size)
            SortCriteria.BITRATE_DESC -> {
                val r = other.bitrate.compareTo(this.bitrate)
                if (r != 0) r else other.sampleRate.compareTo(this.sampleRate)
            }
        }
    } catch (e: Exception) {
        0
    }
}

fun List<Song>.applySortCriteria(criteriaList: List<SortCriteria>): List<Song> {
    if (criteriaList.isEmpty()) return this
    return try {
        this.sortedWith { s1, s2 ->
            for (criteria in criteriaList) {
                val comp = s1.compareWith(s2, criteria)
                if (comp != 0) return@sortedWith comp
            }
            0
        }
    } catch (e: Exception) {
        this
    }
}

@Composable
fun TickmarkBox(
    checked: Boolean,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (checked) TideColors.accent else Color.Transparent
    val borderColor = if (checked) TideColors.accent else TideColors.textSecondary
    val checkmarkColor = TideColors.background

    Box(
        modifier = modifier
            .size(18.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(backgroundColor)
            .border(
                width = 1.5.dp,
                color = borderColor,
                shape = RoundedCornerShape(4.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        if (checked) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = checkmarkColor,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Composable
fun SortDropdown(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    activeCriteria: List<SortCriteria>,
    onCriteriaChanged: (List<SortCriteria>) -> Unit
) {
    androidx.compose.material3.DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = Modifier
            .width(290.dp)
            .background(TideColors.surfaceElevated)
    ) {
        Text(
            text = "Sort By",
            style = MaterialTheme.typography.titleMedium,
            color = TideColors.textPrimary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
        )
        androidx.compose.material3.HorizontalDivider(
            color = TideColors.outline,
            thickness = 1.dp
        )
        
        SortCriteria.entries.forEach { criteria ->
            val isChecked = activeCriteria.contains(criteria)
            androidx.compose.material3.DropdownMenuItem(
                text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TickmarkBox(
                            checked = isChecked,
                            modifier = Modifier.size(18.dp)
                        )
                        androidx.compose.foundation.layout.Spacer(Modifier.width(12.dp))
                        Text(
                            text = criteria.label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isChecked) TideColors.accent else TideColors.textPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                onClick = {
                    val newCriteria = activeCriteria.toMutableList()
                    if (isChecked) {
                        newCriteria.remove(criteria)
                    } else {
                        newCriteria.removeAll { it.group == criteria.group }
                        newCriteria.add(criteria)
                    }
                    onCriteriaChanged(newCriteria)
                }
            )
        }
    }
}
