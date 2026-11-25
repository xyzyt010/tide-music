package com.example.tidemusic.ui.common

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

object ScrollMemoryManager {
    private val scrollPositions = ConcurrentHashMap<String, Pair<Int, Int>>()

    fun save(key: String, index: Int, offset: Int) {
        if (index >= 0 && offset >= 0) {
            scrollPositions[key] = Pair(index, offset)
        }
    }

    fun get(key: String): Pair<Int, Int>? = scrollPositions[key]

    fun clear(key: String) {
        scrollPositions.remove(key)
    }

    fun clearAll() {
        scrollPositions.clear()
    }
}

/**
 * Remembers and automatically restores scroll position for a LazyColumn across
 * navigation and backgrounding, surviving as long as the application process is alive.
 */
@Composable
fun rememberScrollMemoryState(
    key: String,
    itemCount: Int = 0,
): LazyListState {
    val initial = remember(key) { ScrollMemoryManager.get(key) }
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = initial?.first ?: 0,
        initialFirstVisibleItemScrollOffset = initial?.second ?: 0,
    )

    var hasRestored by remember(key) {
        mutableStateOf(initial == null || (initial.first == 0 && initial.second == 0))
    }

    // If user touches and drags, immediately accept user's position as restored
    LaunchedEffect(key, listState) {
        snapshotFlow { listState.isScrollInProgress }
            .filter { it }
            .collectLatest {
                hasRestored = true
            }
    }

    // Attempt to restore scroll position once items appear in the list
    LaunchedEffect(key, itemCount) {
        if (!hasRestored) {
            val saved = ScrollMemoryManager.get(key)
            if (saved != null && (saved.first > 0 || saved.second > 0)) {
                snapshotFlow { maxOf(itemCount, listState.layoutInfo.totalItemsCount) }
                    .filter { it > 0 }
                    .collectLatest { total ->
                        val targetIndex = saved.first.coerceIn(0, (total - 1).coerceAtLeast(0))
                        try {
                            listState.scrollToItem(targetIndex, saved.second)
                        } catch (_: Exception) {}
                        hasRestored = true
                    }
            } else {
                hasRestored = true
            }
        }
    }

    // Continuously record scroll updates
    LaunchedEffect(key, listState) {
        snapshotFlow {
            val total = maxOf(itemCount, listState.layoutInfo.totalItemsCount)
            Triple(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset, total)
        }
        .filter { (_, _, total) -> total > 0 }
        .distinctUntilChanged()
        .collect { (index, offset, _) ->
            if (hasRestored) {
                ScrollMemoryManager.save(key, index, offset)
            }
        }
    }

    return listState
}

/**
 * Remembers and automatically restores scroll position for a LazyVerticalGrid across
 * navigation and backgrounding, surviving as long as the application process is alive.
 */
@Composable
fun rememberScrollGridMemoryState(
    key: String,
    itemCount: Int = 0,
): androidx.compose.foundation.lazy.grid.LazyGridState {
    val initial = remember(key) { ScrollMemoryManager.get(key) }
    val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState(
        initialFirstVisibleItemIndex = initial?.first ?: 0,
        initialFirstVisibleItemScrollOffset = initial?.second ?: 0,
    )

    var hasRestored by remember(key) {
        mutableStateOf(initial == null || (initial.first == 0 && initial.second == 0))
    }

    LaunchedEffect(key, gridState) {
        snapshotFlow { gridState.isScrollInProgress }
            .filter { it }
            .collectLatest {
                hasRestored = true
            }
    }

    LaunchedEffect(key, itemCount) {
        if (!hasRestored) {
            val saved = ScrollMemoryManager.get(key)
            if (saved != null && (saved.first > 0 || saved.second > 0)) {
                snapshotFlow { maxOf(itemCount, gridState.layoutInfo.totalItemsCount) }
                    .filter { it > 0 }
                    .collectLatest { total ->
                        val targetIndex = saved.first.coerceIn(0, (total - 1).coerceAtLeast(0))
                        try {
                            gridState.scrollToItem(targetIndex, saved.second)
                        } catch (_: Exception) {}
                        hasRestored = true
                    }
            } else {
                hasRestored = true
            }
        }
    }

    LaunchedEffect(key, gridState) {
        snapshotFlow {
            val total = maxOf(itemCount, gridState.layoutInfo.totalItemsCount)
            Triple(gridState.firstVisibleItemIndex, gridState.firstVisibleItemScrollOffset, total)
        }
        .filter { (_, _, total) -> total > 0 }
        .distinctUntilChanged()
        .collect { (index, offset, _) ->
            if (hasRestored) {
                ScrollMemoryManager.save(key, index, offset)
            }
        }
    }

    return gridState
}

