package com.example.tidemusic.ui.albums

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation3.runtime.NavKey
import coil3.compose.AsyncImage
import com.example.tidemusic.domain.Album
import com.example.tidemusic.domain.LibraryRepository
import com.example.tidemusic.theme.TideColors
import com.example.tidemusic.ui.rememberTideViewModel
import com.example.tidemusic.util.PlaceholderArt
import com.example.tidemusic.util.orUnknown
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * Albums screen (spec Section 6.2): a grid of album-art blocks. Tapping opens the album
 * detail screen listing its tracks in order. Missing artwork falls back to the same
 * placeholder-gradient system used everywhere else (Section 8.1).
 */
class AlbumsViewModel(
    repository: LibraryRepository,
) : ViewModel() {
    val albums: StateFlow<List<Album>> = repository.observeAlbums()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

@Composable
fun AlbumsScreen(
    onAlbumClick: (Long) -> Unit,
    onOpenScreen: (NavKey) -> Unit,
    viewModel: AlbumsViewModel = rememberTideViewModel { AlbumsViewModel(it.repository) },
) {
    val albums by viewModel.albums.collectAsState()
    Column(modifier = Modifier.fillMaxSize()) {
        com.example.tidemusic.ui.common.ThinTopBar(title = "Albums")
        if (albums.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    text = "No albums found.\nAlbums need at least 2 songs with proper album tags.\nYou can download music from the Download tab.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TideColors.textSecondary,
                    modifier = Modifier.padding(32.dp)
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize().weight(1f).padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(albums, key = { it.id }) { album ->
                    AlbumTile(album = album, onClick = { onAlbumClick(album.id) })
                }
            }
        }
    }
}

@Composable
private fun AlbumTile(album: Album, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            if (album.artworkUri != null) {
                AsyncImage(
                    model = android.net.Uri.parse(album.artworkUri),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                com.example.tidemusic.ui.common.VinylDiscPlaceholder(
                    modifier = Modifier.fillMaxSize(0.75f),
                    spinning = false,
                )
            }
        }
        Text(
            text = album.name.orUnknown(),
            style = MaterialTheme.typography.bodyMedium,
            color = TideColors.textPrimary,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(
            text = album.artist.orUnknown(),
            style = MaterialTheme.typography.bodySmall,
            color = TideColors.textSecondary,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}
