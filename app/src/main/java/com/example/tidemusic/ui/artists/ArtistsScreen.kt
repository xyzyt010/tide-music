package com.example.tidemusic.ui.artists

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
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
    val distinctArtists = remember(rawArtists) {
        val seen = HashSet<String>()
        rawArtists.filter { artist ->
            val name = artist.name.trim()
            if (name.isBlank() || name.equals("unknown", ignoreCase = true) || name.equals("<unknown>", ignoreCase = true)) {
                false
            } else {
                seen.add(name.lowercase())
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        com.example.tidemusic.ui.common.ThinTopBar(title = "Artists")
        if (distinctArtists.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    text = "No artists found.\nSongs need valid artist metadata tags.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TideColors.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(32.dp),
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize().weight(1f).padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(distinctArtists, key = { it.id }) { artist ->
                    ArtistTile(artist = artist, onClick = { onArtistClick(artist.id, artist.name) })
                }
            }
        }
    }
}

@Composable
private fun ArtistTile(artist: Artist, onClick: () -> Unit) {
    val artworkModel: Any? = remember(artist.id, artist.artworkUri) {
        val uri = artist.artworkUri
        if (uri != null && (uri.startsWith("content://") || uri.startsWith("http://") || uri.startsWith("https://"))) {
            android.net.Uri.parse(uri)
        } else if (!uri.isNullOrBlank()) {
            com.example.tidemusic.util.SongArtworkRequest(
                songId = artist.id,
                filePath = uri,
                uriString = "",
                dateModified = 0L,
            )
        } else {
            null
        }
    }
    var imageSuccess by remember(artist.id) { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .aspectRatio(1f)
                .clip(CircleShape)
                .background(TideColors.surface),
            contentAlignment = Alignment.Center,
        ) {
            if (artworkModel != null) {
                AsyncImage(
                    model = artworkModel,
                    contentDescription = artist.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    onState = { state ->
                        imageSuccess = state is coil3.compose.AsyncImagePainter.State.Success
                    },
                )
            }
            if (!imageSuccess) {
                com.example.tidemusic.ui.common.VinylDiscPlaceholder(
                    modifier = Modifier.fillMaxSize(0.7f),
                    spinning = false,
                )
            }
        }
        Text(
            text = artist.name.takeIf { it.isNotBlank() } ?: "Unknown Artist",
            style = MaterialTheme.typography.bodyMedium,
            color = TideColors.textPrimary,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            text = "${artist.trackCount} ${if (artist.trackCount == 1) "song" else "songs"}",
            style = MaterialTheme.typography.bodySmall,
            color = TideColors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}
