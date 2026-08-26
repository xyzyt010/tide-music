package com.example.tidemusic.ui.queue

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.example.tidemusic.di.ServiceLocator
import com.example.tidemusic.domain.Song
import com.example.tidemusic.theme.TideColors
import com.example.tidemusic.ui.LocalMediaController
import com.example.tidemusic.ui.common.MultiSelectActionBar
import com.example.tidemusic.ui.common.SongOverflowMenu
import com.example.tidemusic.ui.common.SongRow
import com.example.tidemusic.ui.common.ThinTopBar
import com.example.tidemusic.ui.common.mediaItemToSongDomain
import com.example.tidemusic.ui.common.rememberMultiSelectState
import com.example.tidemusic.ui.common.SortCriteria
import com.example.tidemusic.ui.common.SortDropdown
import com.example.tidemusic.ui.common.compareWith
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class QueueSong(val song: Song, val originalIndex: Int)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun QueueScreen(onBack: () -> Unit) {
    val controller = LocalMediaController.current
    var songs by remember { mutableStateOf<List<QueueSong>>(emptyList()) }
    var currentIndex by remember { mutableIntStateOf(controller?.currentMediaItemIndex ?: -1) }
    val scope = rememberCoroutineScope()
    val playlists by ServiceLocator.repository.observePlaylists().collectAsState(initial = emptyList())
    val multiSelect = rememberMultiSelectState()
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val itemHeightPx = with(density) { 72.dp.toPx() }
    val edgeScrollZonePx = with(density) { 72.dp.toPx() }

    var sortCriteria by remember { mutableStateOf<List<SortCriteria>>(emptyList()) }
    var showSortDropdown by remember { mutableStateOf(false) }

    // Drag reorder state — magnetic live reordering + edge auto-scroll
    var draggingIndex by remember { mutableIntStateOf(-1) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var hoverTarget by remember { mutableIntStateOf(-1) }
    var menuForIndex by remember { mutableIntStateOf(-1) }
    var autoScrollJob by remember { mutableStateOf<Job?>(null) }
    // Finger Y in list viewport coordinates (for auto-scroll)
    var fingerYInList by remember { mutableFloatStateOf(0f) }

    fun refreshQueue() {
        val c = controller ?: return
        try {
            val count = c.mediaItemCount
            val newSongs = mutableListOf<QueueSong>()
            for (i in 0 until count) {
                val item = c.getMediaItemAt(i)
                newSongs.add(QueueSong(song = mediaItemToSongDomain(item), originalIndex = i))
            }
            songs = newSongs
            currentIndex = c.currentMediaItemIndex
        } catch (_: Exception) {
            // MediaController may be mid-update
        }
    }

    // Enrich queue rows with full Room metadata (artwork, path, duration, etc.)
    LaunchedEffect(songs.map { it.song.id }) {
        val ids = songs.map { it.song.id }.filter { it > 0L }.distinct()
        if (ids.isEmpty()) return@LaunchedEffect
        try {
            val full = ServiceLocator.repository.getSongsByIds(ids).associateBy { it.id }
            if (full.isEmpty()) return@LaunchedEffect
            songs = songs.map { qs ->
                val enriched = full[qs.song.id] ?: return@map qs
                qs.copy(song = enriched)
            }
        } catch (_: Exception) {
        }
    }

    DisposableEffect(controller) {
        val listener = object : Player.Listener {
            override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                if (draggingIndex < 0) refreshQueue()
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                currentIndex = controller?.currentMediaItemIndex ?: -1
            }
        }
        controller?.addListener(listener)
        refreshQueue()
        onDispose {
            controller?.removeListener(listener)
            autoScrollJob?.cancel()
        }
    }

    val displaySongs = remember(songs, sortCriteria) {
        if (sortCriteria.isEmpty()) songs
        else songs.sortedWith { q1, q2 ->
            for (criteria in sortCriteria) {
                val res = q1.song.compareWith(q2.song, criteria)
                if (res != 0) return@sortedWith res
            }
            0
        }
    }

    val canDrag = !multiSelect.active && sortCriteria.isEmpty()

    fun moveItem(from: Int, to: Int) {
        val c = controller ?: return
        if (from == to) return
        if (from !in 0 until c.mediaItemCount) return
        if (to !in 0 until c.mediaItemCount) return
        try {
            c.moveMediaItem(from, to)
            refreshQueue()
        } catch (_: Exception) {
        }
    }

    fun removeAt(index: Int) {
        val c = controller ?: return
        try {
            if (index in 0 until c.mediaItemCount) {
                c.removeMediaItem(index)
                refreshQueue()
            }
        } catch (_: Exception) {
        }
    }

    fun stopAutoScroll() {
        autoScrollJob?.cancel()
        autoScrollJob = null
    }

    fun startEdgeAutoScrollIfNeeded() {
        val viewport = listState.layoutInfo.viewportEndOffset.toFloat()
        val nearTop = fingerYInList < edgeScrollZonePx
        val nearBottom = fingerYInList > viewport - edgeScrollZonePx
        if (!nearTop && !nearBottom) {
            stopAutoScroll()
            return
        }
        if (autoScrollJob?.isActive == true) return
        autoScrollJob = scope.launch {
            while (isActive && draggingIndex >= 0) {
                val vp = listState.layoutInfo.viewportEndOffset.toFloat()
                val speed = when {
                    fingerYInList < edgeScrollZonePx -> {
                        val t = ((edgeScrollZonePx - fingerYInList) / edgeScrollZonePx).coerceIn(0f, 1f)
                        -((8f + 28f * t))
                    }
                    fingerYInList > vp - edgeScrollZonePx -> {
                        val t = ((fingerYInList - (vp - edgeScrollZonePx)) / edgeScrollZonePx).coerceIn(0f, 1f)
                        (8f + 28f * t)
                    }
                    else -> {
                        stopAutoScroll()
                        break
                    }
                }
                listState.dispatchRawDelta(speed)
                // Keep drag offset coherent with scroll so the floating row stays under the finger.
                dragOffsetY += speed
                // Recompute hover target from total displacement
                val from = draggingIndex
                if (from >= 0 && itemHeightPx > 0f) {
                    val shift = Math.round(dragOffsetY / itemHeightPx)
                    hoverTarget = (from + shift).coerceIn(0, displaySongs.lastIndex)
                }
                delay(16L)
            }
        }
    }

    /** Magnetic visual shift for non-dragged rows while a drag is active. */
    fun magneticOffset(index: Int): Float {
        val from = draggingIndex
        val to = hoverTarget
        if (from < 0 || to < 0 || from == to) return 0f
        return when {
            from < to && index in (from + 1)..to -> -itemHeightPx
            from > to && index in to until from -> itemHeightPx
            else -> 0f
        }
    }

    Column(Modifier.fillMaxSize()) {
        ThinTopBar(
            title = "Queue",
            trailing = {
                Box {
                    IconButton(onClick = { showSortDropdown = true }) {
                        Icon(Icons.Rounded.Sort, contentDescription = "Sort")
                    }
                    SortDropdown(
                        expanded = showSortDropdown,
                        onDismissRequest = { showSortDropdown = false },
                        activeCriteria = sortCriteria,
                        onCriteriaChanged = { sortCriteria = it },
                    )
                }
            },
        )
        Box(Modifier.weight(1f)) {
            if (displaySongs.isEmpty()) {
                Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        Icons.Rounded.LibraryMusic,
                        contentDescription = null,
                        tint = TideColors.textSecondary,
                        modifier = Modifier.padding(bottom = 16.dp),
                    )
                    Text(
                        "Queue is empty",
                        style = MaterialTheme.typography.bodyLarge,
                        color = TideColors.textSecondary,
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    itemsIndexed(
                        displaySongs,
                        key = { _, qs -> "${qs.song.id}_${qs.originalIndex}" },
                    ) { index, qs ->
                        val isCurrentPlaying = qs.originalIndex == currentIndex
                        val isDragging = canDrag && draggingIndex == index
                        val shift = if (isDragging) 0f else magneticOffset(index)
                        val animatedShift = remember { Animatable(0f) }
                        LaunchedEffect(shift, isDragging) {
                            if (!isDragging) {
                                animatedShift.animateTo(
                                    shift,
                                    spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy),
                                )
                            } else {
                                animatedShift.snapTo(0f)
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(72.dp)
                                .zIndex(if (isDragging) 2f else 0f)
                                .graphicsLayer {
                                    translationY = if (isDragging) dragOffsetY else animatedShift.value
                                    if (isDragging) {
                                        alpha = 0.95f
                                        shadowElevation = 12f
                                        scaleX = 1.02f
                                        scaleY = 1.02f
                                    }
                                }
                                .padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (canDrag) {
                                Icon(
                                    imageVector = Icons.Rounded.DragHandle,
                                    contentDescription = "Hold and drag to reorder",
                                    tint = if (isDragging) TideColors.accent else TideColors.textSecondary,
                                    modifier = Modifier
                                        .padding(start = 8.dp, end = 4.dp)
                                        .pointerInput(index, displaySongs.size, canDrag) {
                                            detectDragGesturesAfterLongPress(
                                                onDragStart = { offset ->
                                                    draggingIndex = index
                                                    hoverTarget = index
                                                    dragOffsetY = 0f
                                                    // Approximate finger Y from item position in viewport
                                                    val info = listState.layoutInfo.visibleItemsInfo
                                                        .firstOrNull { it.index == index }
                                                    fingerYInList = (info?.offset?.toFloat() ?: 0f) + offset.y
                                                },
                                                onDragEnd = {
                                                    stopAutoScroll()
                                                    val from = draggingIndex
                                                    val to = hoverTarget
                                                    if (from >= 0 && to >= 0 && from != to) {
                                                        val fromOrig = displaySongs.getOrNull(from)?.originalIndex
                                                        val toOrig = displaySongs.getOrNull(to)?.originalIndex
                                                        if (fromOrig != null && toOrig != null) {
                                                            moveItem(fromOrig, toOrig)
                                                        }
                                                    }
                                                    draggingIndex = -1
                                                    hoverTarget = -1
                                                    dragOffsetY = 0f
                                                },
                                                onDragCancel = {
                                                    stopAutoScroll()
                                                    draggingIndex = -1
                                                    hoverTarget = -1
                                                    dragOffsetY = 0f
                                                },
                                                onDrag = { change, dragAmount ->
                                                    change.consume()
                                                    dragOffsetY += dragAmount.y
                                                    fingerYInList += dragAmount.y
                                                    val from = draggingIndex
                                                    if (from >= 0 && itemHeightPx > 0f) {
                                                        val shiftIdx = Math.round(dragOffsetY / itemHeightPx)
                                                        hoverTarget = (from + shiftIdx)
                                                            .coerceIn(0, displaySongs.lastIndex)
                                                    }
                                                    startEdgeAutoScrollIfNeeded()
                                                },
                                            )
                                        },
                                )
                            }
                            Box(Modifier.weight(1f)) {
                                SongRow(
                                    song = qs.song,
                                    highlight = isCurrentPlaying,
                                    onClick = {
                                        try {
                                            controller?.seekToDefaultPosition(qs.originalIndex)
                                            controller?.play()
                                        } catch (_: Exception) {
                                        }
                                    },
                                    multiSelect = multiSelect,
                                    trailing = {
                                        if (!multiSelect.active) {
                                            Box {
                                                IconButton(onClick = { menuForIndex = index }) {
                                                    Icon(
                                                        Icons.Rounded.MoreVert,
                                                        contentDescription = "Options",
                                                        tint = TideColors.textSecondary,
                                                    )
                                                }
                                                SongOverflowMenu(
                                                    song = qs.song,
                                                    expanded = menuForIndex == index,
                                                    onDismiss = { menuForIndex = -1 },
                                                    showRemoveFromQueue = true,
                                                    onRemoveFromQueue = {
                                                        removeAt(qs.originalIndex)
                                                    },
                                                    onPlayAfterCurrent = {
                                                        try {
                                                            val c = controller ?: return@SongOverflowMenu
                                                            val insertAt = (c.currentMediaItemIndex + 1)
                                                                .coerceIn(0, c.mediaItemCount)
                                                            val from = qs.originalIndex
                                                            if (from in 0 until c.mediaItemCount) {
                                                                val target = if (from < insertAt) insertAt - 1 else insertAt
                                                                c.moveMediaItem(
                                                                    from,
                                                                    target.coerceIn(0, c.mediaItemCount - 1),
                                                                )
                                                                refreshQueue()
                                                            }
                                                        } catch (_: Exception) {
                                                        }
                                                    },
                                                    onAddToQueue = {
                                                        try {
                                                            val c = controller ?: return@SongOverflowMenu
                                                            val from = qs.originalIndex
                                                            val last = c.mediaItemCount - 1
                                                            if (from in 0..last && from != last) {
                                                                c.moveMediaItem(from, last)
                                                                refreshQueue()
                                                            }
                                                        } catch (_: Exception) {
                                                        }
                                                    },
                                                    onDeleted = {
                                                        removeAt(qs.originalIndex)
                                                    },
                                                )
                                            }
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }

            MultiSelectActionBar(
                state = multiSelect,
                allIds = displaySongs.map { it.song.id },
                playlists = playlists,
                onDelete = { ids ->
                    val indicesToRemove = displaySongs
                        .filter { it.song.id in ids }
                        .map { it.originalIndex }
                        .sortedDescending()
                    indicesToRemove.forEach { removeAt(it) }
                    scope.launch {
                        try {
                            ServiceLocator.repository.deletePermanently(ids.toList())
                        } catch (_: Exception) {
                        }
                    }
                },
                onAddToPlaylist = { playlistId, songIds ->
                    scope.launch {
                        try {
                            ServiceLocator.repository.addSongsToPlaylist(playlistId, songIds)
                        } catch (_: Exception) {
                        }
                    }
                },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}
