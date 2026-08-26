package com.example.tidemusic.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tidemusic.di.ServiceLocator
import com.example.tidemusic.domain.LibraryRepository
import com.example.tidemusic.domain.Song
import com.example.tidemusic.playback.PlaybackController
import com.example.tidemusic.theme.TideColors
import com.example.tidemusic.ui.common.MultiSelectActionBar
import com.example.tidemusic.ui.common.SongRow
import com.example.tidemusic.ui.common.rememberMultiSelectState
import com.example.tidemusic.ui.rememberTideViewModel
import com.example.tidemusic.ui.common.SortCriteria
import com.example.tidemusic.ui.common.SortDropdown
import com.example.tidemusic.ui.common.applySortCriteria
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.json.JSONArray

class SearchViewModel(
    private val repository: LibraryRepository,
    private val playback: PlaybackController,
    context: android.content.Context,
) : ViewModel() {

    private val sharedPrefs = context.getSharedPreferences("search_history_pref", android.content.Context.MODE_PRIVATE)

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    private val _results = MutableStateFlow<List<Song>>(emptyList())
    val results: StateFlow<List<Song>> = _results

    private val _history = MutableStateFlow<List<String>>(emptyList())
    val history: StateFlow<List<String>> = _history

    init {
        loadHistory()

        @Suppress("OPT_IN_USAGE")
        _query
            .debounce(150)
            .distinctUntilChanged()
            .flatMapLatest { q ->
                if (q.isBlank()) flowOf(emptyList())
                else repository.searchSongs(q)
            }
            .onEach { _results.value = it }
            .launchIn(viewModelScope)
    }

    fun setQuery(q: String) { _query.value = q }

    fun playAt(index: Int, songs: List<Song>) {
        playback.setQueue(songs, startIndex = index)
        saveQueryToHistory(_query.value)
    }

    private fun loadHistory() {
        val jsonString = sharedPrefs.getString("history", "[]") ?: "[]"
        try {
            val array = JSONArray(jsonString)
            val list = mutableListOf<String>()
            for (i in 0 until array.length()) {
                list.add(array.getString(i))
            }
            _history.value = list
        } catch (e: Exception) {
            _history.value = emptyList()
        }
    }

    fun saveQueryToHistory(q: String) {
        if (q.isBlank()) return
        val currentList = _history.value.toMutableList()
        currentList.remove(q)
        currentList.add(0, q)
        if (currentList.size > 20) {
            currentList.removeAt(currentList.lastIndex)
        }
        _history.value = currentList
        sharedPrefs.edit().putString("history", JSONArray(currentList).toString()).apply()
    }

    fun deleteHistoryItem(q: String) {
        val currentList = _history.value.toMutableList()
        currentList.remove(q)
        _history.value = currentList
        sharedPrefs.edit().putString("history", JSONArray(currentList).toString()).apply()
    }

    fun clearHistory() {
        _history.value = emptyList()
        sharedPrefs.edit().remove("history").apply()
    }
}

@Composable
fun SearchScreen(
    isActive: Boolean = true,
    viewModel: SearchViewModel = rememberTideViewModel {
        SearchViewModel(it.repository, it.playbackController, com.example.tidemusic.TideMusicApp.instance)
    }
) {
    val query by viewModel.query.collectAsState()
    val results by viewModel.results.collectAsState()
    val history by viewModel.history.collectAsState()
    val multiSelect = rememberMultiSelectState()
    val playlists by ServiceLocator.repository.observePlaylists().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    
    var sortCriteria by remember { mutableStateOf<List<SortCriteria>>(emptyList()) }
    var showSortDropdown by remember { mutableStateOf(false) }

    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current

    val glowProgress by animateFloatAsState(
        targetValue = if (isActive) 1f else 0f,
        animationSpec = tween(durationMillis = 350),
        label = "glowProgress"
    )

    androidx.compose.runtime.LaunchedEffect(isActive) {
        if (isActive) {
            kotlinx.coroutines.delay(100)
            try {
                focusRequester.requestFocus()
                keyboardController?.show()
            } catch (_: Exception) {}
        } else {
            try {
                keyboardController?.hide()
                focusManager.clearFocus()
            } catch (_: Exception) {}
        }
    }

    val borderColor = lerp(
        start = TideColors.outline,
        stop = Color.White,
        fraction = glowProgress
    )
    val borderWidth = lerp(1.dp, 1.8.dp, glowProgress)

    val sortedResults = remember(results, sortCriteria) {
        results.applySortCriteria(sortCriteria)
    }

    Column(Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(TideColors.background)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            BasicTextField(
                value = query,
                onValueChange = viewModel::setQuery,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = TideColors.textPrimary),
                singleLine = true,
                cursorBrush = androidx.compose.ui.graphics.SolidColor(TideColors.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    viewModel.saveQueryToHistory(query)
                }),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .focusRequester(focusRequester),
                decorationBox = { innerTextField ->
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(TideColors.surfaceElevated, shape = RoundedCornerShape(20.dp))
                            .border(width = borderWidth, color = borderColor, shape = RoundedCornerShape(20.dp))
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = "Search",
                            tint = TideColors.textSecondary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                            if (query.isEmpty()) {
                                Text(
                                    text = "Search songs, albums, artists…",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = TideColors.textSecondary
                                )
                            }
                            innerTextField()
                        }
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setQuery("") }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Rounded.Close, "Clear", tint = TideColors.textSecondary, modifier = Modifier.size(18.dp))
                            }
                        }
                        Box {
                            IconButton(onClick = { showSortDropdown = true }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Rounded.Sort, contentDescription = "Sort", tint = TideColors.textSecondary, modifier = Modifier.size(20.dp))
                            }
                            SortDropdown(
                                expanded = showSortDropdown,
                                onDismissRequest = { showSortDropdown = false },
                                activeCriteria = sortCriteria,
                                onCriteriaChanged = { sortCriteria = it }
                            )
                        }
                    }
                }
            )
        }
        
        Box(Modifier.fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize()) {
                if (query.isBlank()) {
                    if (history.isNotEmpty()) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Recent Searches",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = TideColors.textSecondary
                                )
                                Text(
                                    text = "Clear All",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TideColors.accent,
                                    modifier = Modifier.clickable { viewModel.clearHistory() }
                                )
                            }
                        }
                        items(history) { histQuery ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.setQuery(histQuery) }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.History,
                                    contentDescription = null,
                                    tint = TideColors.textSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    text = histQuery,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = TideColors.textPrimary,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = { viewModel.deleteHistoryItem(histQuery) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "Remove",
                                        tint = TideColors.textSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                } else {
                    item {
                        Text(
                            text = "${sortedResults.size} results",
                            style = MaterialTheme.typography.bodySmall,
                            color = TideColors.textSecondary,
                            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
                        )
                    }
                    items(sortedResults, key = { it.id }) { song ->
                        val index = sortedResults.indexOf(song)
                        SongRow(
                            song = song,
                            onClick = { viewModel.playAt(index, sortedResults) },
                            multiSelect = multiSelect,
                        )
                    }
                }
            }
            MultiSelectActionBar(
                state = multiSelect,
                allIds = sortedResults.map { it.id },
                playlists = playlists,
                onDelete = { ids -> scope.launch { ServiceLocator.repository.deletePermanently(ids.toList()) } },
                onAddToPlaylist = { playlistId, songIds -> scope.launch { ServiceLocator.repository.addSongsToPlaylist(playlistId, songIds) } },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}
