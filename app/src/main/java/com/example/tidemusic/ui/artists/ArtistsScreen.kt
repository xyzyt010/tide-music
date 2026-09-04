package com.example.tidemusic.ui.artists

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tidemusic.domain.Artist
import com.example.tidemusic.domain.LibraryRepository
import com.example.tidemusic.theme.TideColors
import com.example.tidemusic.ui.rememberTideViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class ArtistsViewModel(
    repository: LibraryRepository,
) : ViewModel() {
    val artists: StateFlow<List<Artist>> = repository.observeArtists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

@Composable
fun ArtistsScreen(
    onArtistClick: (Long, String) -> Unit,
    viewModel: ArtistsViewModel = rememberTideViewModel { ArtistsViewModel(it.repository) },
) {
    val rawArtists by viewModel.artists.collectAsState()
    var isAscending by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }

    val sortedArtists = remember(rawArtists, isAscending) {
        val seen = HashSet<String>()
        val filtered = rawArtists.filter { artist ->
            val name = artist.name.trim()
            if (name.isBlank() || name.equals("unknown", ignoreCase = true) || name.equals("<unknown>", ignoreCase = true)) {
                false
            } else {
                seen.add(name.lowercase())
            }
        }
        if (isAscending) {
            filtered.sortedBy { it.name.trim().lowercase() }
        } else {
            filtered.sortedByDescending { it.name.trim().lowercase() }
        }
    }

    val displayArtists = remember(sortedArtists, searchQuery) {
        val q = searchQuery.trim()
        if (q.isBlank()) sortedArtists
        else sortedArtists.filter { it.name.contains(q, ignoreCase = true) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        com.example.tidemusic.ui.common.ThinTopBar(
            title = "Artists",
            trailing = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { isAscending = !isAscending }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Sort,
                        contentDescription = "Toggle Sort",
                        tint = TideColors.accent,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = if (isAscending) "A → Z" else "Z → A",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                        ),
                        color = TideColors.accent,
                    )
                }
            }
        )

        // Compact Search Bar (No auto-focus so keyboard never opens automatically)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(TideColors.surfaceElevated.copy(alpha = 0.65f))
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = "Search",
                    tint = TideColors.textSecondary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                androidx.compose.foundation.text.BasicTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = TideColors.textPrimary),
                    modifier = Modifier.weight(1f),
                    decorationBox = { innerTextField ->
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = "Search artists...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TideColors.textSecondary.copy(alpha = 0.6f),
                            )
                        }
                        innerTextField()
                    }
                )
                if (searchQuery.isNotEmpty()) {
                    IconButton(
                        onClick = { searchQuery = "" },
                        modifier = Modifier.size(22.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Clear search",
                            tint = TideColors.textSecondary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }

        if (displayArtists.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    text = if (searchQuery.isNotBlank()) "No artists matching \"$searchQuery\"" else "No artists found.\nSongs need valid artist metadata tags.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TideColors.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(32.dp),
                )
            }
        } else {
            val listState = com.example.tidemusic.ui.common.rememberScrollMemoryState("artists_list", displayArtists.size)
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
            ) {
                items(displayArtists, key = { it.id }) { artist ->
                    ArtistListRow(
                        artist = artist,
                        onClick = { onArtistClick(artist.id, artist.name) }
                    )
                    HorizontalDivider(
                        color = TideColors.outline.copy(alpha = 0.5f),
                        thickness = 0.5.dp,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ArtistListRow(
    artist: Artist,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = artist.name.takeIf { it.isNotBlank() } ?: "Unknown Artist",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                ),
                color = TideColors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${artist.trackCount} ${if (artist.trackCount == 1) "song" else "songs"}",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 13.sp,
                ),
                color = TideColors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = TideColors.textSecondary.copy(alpha = 0.6f),
            modifier = Modifier.size(20.dp),
        )
    }
}
