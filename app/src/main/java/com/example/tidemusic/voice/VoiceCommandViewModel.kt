package com.example.tidemusic.voice

import android.content.Context
import android.media.AudioManager
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import com.example.tidemusic.di.ServiceLocator
import com.example.tidemusic.domain.LibraryRepository
import com.example.tidemusic.domain.Song
import com.example.tidemusic.playback.PlaybackController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class CommandHistoryItem(
    val transcript: String,
    val actionDescription: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Smart Decision-Making Assistant ViewModel.
 *
 * Replaces offline STT and wake word models with lightweight, instant,
 * typed command dispatching and high-accuracy fuzzy library matching.
 */
class VoiceCommandViewModel(
    private val context: Context,
    private val playbackController: PlaybackController = ServiceLocator.playbackController,
    private val repository: LibraryRepository = ServiceLocator.repository,
) : ViewModel() {

    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _transcript = MutableStateFlow("")
    val transcript: StateFlow<String> = _transcript.asStateFlow()

    private val _lastIntent = MutableStateFlow<VoiceIntent?>(null)
    val lastIntent: StateFlow<VoiceIntent?> = _lastIntent.asStateFlow()

    private val _matchedSong = MutableStateFlow<Song?>(null)
    val matchedSong: StateFlow<Song?> = _matchedSong.asStateFlow()

    private val _matchConfidence = MutableStateFlow(0.0f)
    val matchConfidence: StateFlow<Float> = _matchConfidence.asStateFlow()

    private val _matchType = MutableStateFlow("")
    val matchType: StateFlow<String> = _matchType.asStateFlow()

    private val _statusMessage = MutableStateFlow("Ready. Type a command or song name below.")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private val _commandHistory = MutableStateFlow<List<CommandHistoryItem>>(emptyList())
    val commandHistory: StateFlow<List<CommandHistoryItem>> = _commandHistory.asStateFlow()

    fun onInputTextChanged(text: String) {
        _inputText.value = text
    }

    fun executeQuickCommand(command: String) {
        submitCommand(command)
    }

    fun playMatchedSong(song: Song) {
        playbackController.playSong(song)
        _statusMessage.value = "Playing \"${song.title}\" by ${song.artist}"
        addHistory("Play ${song.title}", "Played: ${song.title}")
    }

    fun submitCommand(explicitQuery: String? = null) {
        val query = (explicitQuery ?: _inputText.value).trim()
        if (query.isBlank()) return

        if (explicitQuery == null) {
            _inputText.value = ""
        }

        _transcript.value = query
        _isProcessing.value = true

        viewModelScope.launch {
            try {
                val allSongs: List<Song> = withContext(Dispatchers.IO) {
                    try {
                        repository.observeAllSongs().first()
                    } catch (e: Exception) {
                        emptyList()
                    }
                }

                val intent = VoiceDecisionModel.classifyIntent(query)
                _lastIntent.value = intent

                when (intent) {
                    is VoiceIntent.PlaySong -> {
                        val match = VoiceDecisionModel.matchBestSong(intent.query, allSongs)
                        if (match != null) {
                            _matchedSong.value = match.song
                            _matchConfidence.value = match.score
                            _matchType.value = match.matchType
                            _statusMessage.value = "Playing \"${match.song.title}\" by ${match.song.artist} (${match.matchType})"
                            executeWorkflow("Played \"${match.song.title}\"") {
                                playbackController.playSong(match.song)
                            }
                        } else {
                            _matchedSong.value = null
                            _matchConfidence.value = 0f
                            _matchType.value = ""
                            _statusMessage.value = "Song \"${intent.query}\" not found in library."
                            addHistory(query, "Song not found: ${intent.query}")
                        }
                    }

                    is VoiceIntent.ShuffleAll -> {
                        _statusMessage.value = "Shuffle All enabled (Hardware entropy)"
                        executeWorkflow("Shuffle All enabled") {
                            playbackController.setShuffleMode(PlaybackController.ShuffleMode.ALL)
                        }
                    }

                    is VoiceIntent.ToggleShuffle -> {
                        val newMode = when (intent.enable) {
                            true -> PlaybackController.ShuffleMode.QUEUE
                            false -> PlaybackController.ShuffleMode.OFF
                            null -> playbackController.cycleShuffleMode()
                        }
                        if (intent.enable != null) {
                            playbackController.setShuffleMode(newMode)
                        }
                        val desc = when (newMode) {
                            PlaybackController.ShuffleMode.ALL -> "Shuffle All enabled"
                            PlaybackController.ShuffleMode.QUEUE -> "Shuffle Playlist enabled"
                            PlaybackController.ShuffleMode.OFF -> "Shuffle disabled"
                        }
                        _statusMessage.value = desc
                        addHistory(query, desc)
                    }

                    is VoiceIntent.PlayCurrent -> {
                        if (playbackController.isPlaying) {
                            _statusMessage.value = "Music is already playing"
                            addHistory(query, "Already playing")
                        } else {
                            _statusMessage.value = "Resuming playback"
                            executeWorkflow("Resumed playback") {
                                if (playbackController.mediaItemCount == 0) {
                                    val firstSong = allSongs.firstOrNull()
                                    if (firstSong != null) {
                                        playbackController.playSong(firstSong)
                                    } else {
                                        playbackController.play()
                                    }
                                } else {
                                    playbackController.play()
                                }
                            }
                        }
                    }

                    is VoiceIntent.Pause -> {
                        _statusMessage.value = "Playback paused"
                        executeWorkflow("Paused music") {
                            playbackController.pause()
                        }
                    }

                    is VoiceIntent.Next -> {
                        _statusMessage.value = "Skipped to next track"
                        executeWorkflow("Next track") {
                            playbackController.next()
                        }
                    }

                    is VoiceIntent.Previous -> {
                        _statusMessage.value = "Returning to previous track"
                        executeWorkflow("Previous track") {
                            playbackController.previous()
                        }
                    }

                    is VoiceIntent.ToggleRepeat -> {
                        val newMode = if (intent.mode != null) {
                            playbackController.setRepeatMode(intent.mode)
                        } else {
                            playbackController.cycleRepeat()
                        }
                        val modeDesc = when (newMode) {
                            Player.REPEAT_MODE_ONE -> "Repeat One Song"
                            Player.REPEAT_MODE_ALL -> "Repeat Queue"
                            else -> "Repeat Off"
                        }
                        _statusMessage.value = "Repeat mode: $modeDesc"
                        addHistory(query, "Repeat: $modeDesc")
                    }

                    is VoiceIntent.ToggleFavorite -> {
                        _statusMessage.value = "Toggled favorite for current song"
                        executeWorkflow("Toggled favorite") {
                            playbackController.toggleFavoriteCurrentSong()
                        }
                    }

                    is VoiceIntent.Volume -> {
                        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                        if (audioManager != null) {
                            val direction = if (intent.delta > 0) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
                            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
                            val desc = if (intent.delta > 0) "Volume raised" else "Volume lowered"
                            _statusMessage.value = desc
                            addHistory(query, desc)
                        }
                    }

                    is VoiceIntent.Unknown -> {
                        // High-accuracy fallback: treat utterance as fuzzy song title search
                        val fallbackMatch = VoiceDecisionModel.matchBestSong(query, allSongs)
                        if (fallbackMatch != null && fallbackMatch.score >= 0.35f) {
                            _matchedSong.value = fallbackMatch.song
                            _matchConfidence.value = fallbackMatch.score
                            _matchType.value = fallbackMatch.matchType
                            _statusMessage.value = "Playing \"${fallbackMatch.song.title}\" by ${fallbackMatch.song.artist} (${fallbackMatch.matchType})"
                            executeWorkflow("Played \"${fallbackMatch.song.title}\"") {
                                playbackController.playSong(fallbackMatch.song)
                            }
                        } else {
                            _matchedSong.value = null
                            _matchConfidence.value = 0f
                            _matchType.value = ""
                            _statusMessage.value = "Command not recognized: \"$query\". Try a song title or action."
                            addHistory(query, "Unrecognized command")
                        }
                    }
                }
            } catch (e: Throwable) {
                Log.e("VoiceCommandViewModel", "Error processing command: ${e.message}", e)
                _statusMessage.value = "Error executing command: ${e.localizedMessage ?: "Unknown error"}"
            } finally {
                _isProcessing.value = false
            }
        }
    }

    private fun executeWorkflow(actionDesc: String, block: () -> Unit) {
        try {
            block()
            addHistory(_transcript.value, actionDesc)
        } catch (e: Throwable) {
            Log.e("VoiceCommandViewModel", "Workflow execution error: ${e.message}", e)
            _statusMessage.value = "Action error: ${e.message}"
        }
    }

    private fun addHistory(transcript: String, actionDesc: String) {
        val newItem = CommandHistoryItem(
            transcript = transcript,
            actionDescription = actionDesc
        )
        _commandHistory.value = listOf(newItem) + _commandHistory.value.take(29)
    }
}
