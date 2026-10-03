package com.example.tidemusic.voice

import android.content.Context
import android.media.AudioManager
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

class VoiceCommandViewModel(
    private val context: Context,
    private val playbackController: PlaybackController = ServiceLocator.playbackController,
    private val repository: LibraryRepository = ServiceLocator.repository,
) : ViewModel() {

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _isWakeWordActive = MutableStateFlow(true)
    val isWakeWordActive: StateFlow<Boolean> = _isWakeWordActive.asStateFlow()

    private val _transcript = MutableStateFlow("")
    val transcript: StateFlow<String> = _transcript.asStateFlow()

    private val _partialTranscript = MutableStateFlow("")
    val partialTranscript: StateFlow<String> = _partialTranscript.asStateFlow()

    private val _lastIntent = MutableStateFlow<VoiceIntent?>(null)
    val lastIntent: StateFlow<VoiceIntent?> = _lastIntent.asStateFlow()

    private val _matchedSong = MutableStateFlow<Song?>(null)
    val matchedSong: StateFlow<Song?> = _matchedSong.asStateFlow()

    private val _matchConfidence = MutableStateFlow(0.0f)
    val matchConfidence: StateFlow<Float> = _matchConfidence.asStateFlow()

    private val _audioRms = MutableStateFlow(0.0f)
    val audioRms: StateFlow<Float> = _audioRms.asStateFlow()

    private val _statusMessage = MutableStateFlow("Ready. Say \"Hey Jarvis\" or tap the microphone.")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private val _commandHistory = MutableStateFlow<List<CommandHistoryItem>>(emptyList())
    val commandHistory: StateFlow<List<CommandHistoryItem>> = _commandHistory.asStateFlow()

    private var voiceRecognizer: VoiceRecognizer? = null
    private var wakeWordEngine: WakeWordEngine? = null

    init {
        setupVoiceRecognizer()
        setupWakeWordEngine()
    }

    private fun setupVoiceRecognizer() {
        voiceRecognizer = VoiceRecognizer(
            context = context,
            onReady = {
                _isListening.value = true
                _statusMessage.value = "Listening… Speak your command now."
            },
            onRmsChanged = { rms ->
                // Map RMS dB (approx 0 - 60 dB) to normalized 0.0 - 1.0 for visualizer
                val normalized = (rms / 60.0f).coerceIn(0.0f, 1.0f)
                _audioRms.value = normalized
            },
            onPartialResult = { partial ->
                _partialTranscript.value = partial
            },
            onFinalResult = { finalResult ->
                _isListening.value = false
                _transcript.value = finalResult
                _partialTranscript.value = ""
                processTranscript(finalResult)
                // Resume wake word engine after STT finishes
                if (_isWakeWordActive.value) {
                    wakeWordEngine?.start()
                }
            },
            onError = { error ->
                _isListening.value = false
                _audioRms.value = 0f
                _statusMessage.value = error
                if (_isWakeWordActive.value) {
                    wakeWordEngine?.start()
                }
            },
            onListeningEnded = {
                _isListening.value = false
                _audioRms.value = 0f
            }
        )
    }

    private fun setupWakeWordEngine() {
        wakeWordEngine = WakeWordEngine(
            context = context,
            onWakeWordDetected = {
                viewModelScope.launch(Dispatchers.Main) {
                    Log.i("VoiceCommandViewModel", "Wake word recognized! Activating STT…")
                    _statusMessage.value = "Wake word detected! Listening…"
                    // Pause wake word engine while active STT is listening
                    wakeWordEngine?.stop()
                    startListening()
                }
            },
            onRmsChanged = { rms ->
                if (!_isListening.value) {
                    _audioRms.value = (rms / 65.0f).coerceIn(0.0f, 1.0f)
                }
            }
        )

        if (_isWakeWordActive.value) {
            wakeWordEngine?.start()
        }
    }

    fun startListening() {
        _partialTranscript.value = ""
        _audioRms.value = 0f
        _statusMessage.value = "Listening…"
        wakeWordEngine?.stop()
        voiceRecognizer?.startListening()
    }

    fun stopListening() {
        voiceRecognizer?.stopListening()
        _isListening.value = false
        _audioRms.value = 0f
        if (_isWakeWordActive.value) {
            wakeWordEngine?.start()
        }
    }

    fun toggleListening() {
        if (_isListening.value) {
            stopListening()
        } else {
            startListening()
        }
    }

    fun toggleWakeWord(enabled: Boolean) {
        _isWakeWordActive.value = enabled
        if (enabled) {
            _statusMessage.value = "Wake word active. Say \"Hey Jarvis\" or click mic."
            if (!_isListening.value) {
                wakeWordEngine?.start()
            }
        } else {
            _statusMessage.value = "Wake word disabled. Tap mic to speak."
            wakeWordEngine?.stop()
        }
    }

    fun executeQuickCommand(commandText: String) {
        _transcript.value = commandText
        _partialTranscript.value = ""
        processTranscript(commandText)
    }

    private fun processTranscript(transcriptText: String) {
        viewModelScope.launch {
            _statusMessage.value = "Processing command with decision model…"
            val intent = VoiceDecisionModel.classifyIntent(transcriptText)
            _lastIntent.value = intent

            when (intent) {
                is VoiceIntent.PlaySong -> {
                    val allSongs = repository.observeAllSongs().first()
                    val match = VoiceDecisionModel.matchBestSong(intent.query, allSongs)

                    if (match != null) {
                        _matchedSong.value = match.song
                        _matchConfidence.value = match.score
                        _statusMessage.value = "Playing: ${match.song.title} (${(match.score * 100).toInt()}% match)"

                        // Queue and immediately play the matched song
                        withContext(Dispatchers.Main) {
                            playbackController.setQueue(listOf(match.song), 0)
                        }

                        addHistory(transcriptText, "Played \"${match.song.title}\" by ${match.song.artist}")
                    } else {
                        _matchedSong.value = null
                        _matchConfidence.value = 0f
                        _statusMessage.value = "Could not find a song matching \"${intent.query}\" in your library."
                        addHistory(transcriptText, "No matching song found for \"${intent.query}\"")
                    }
                }

                is VoiceIntent.Pause -> {
                    withContext(Dispatchers.Main) {
                        if (playbackController.isPlaying) {
                            playbackController.togglePlayPause()
                        }
                    }
                    _statusMessage.value = "Paused playback."
                    addHistory(transcriptText, "Paused current song")
                }

                is VoiceIntent.PlayCurrent -> {
                    withContext(Dispatchers.Main) {
                        if (!playbackController.isPlaying) {
                            playbackController.togglePlayPause()
                        }
                    }
                    _statusMessage.value = "Resumed playback."
                    addHistory(transcriptText, "Resumed playback")
                }

                is VoiceIntent.Next -> {
                    withContext(Dispatchers.Main) {
                        playbackController.next()
                    }
                    _statusMessage.value = "Skipped to next track."
                    addHistory(transcriptText, "Skipped to next song")
                }

                is VoiceIntent.Previous -> {
                    withContext(Dispatchers.Main) {
                        playbackController.previous()
                    }
                    _statusMessage.value = "Went back to previous track."
                    addHistory(transcriptText, "Played previous track")
                }

                is VoiceIntent.ToggleShuffle -> {
                    val nextShuffle = intent.enable ?: !playbackController.isShuffleEnabled
                    withContext(Dispatchers.Main) {
                        playbackController.setShuffleMode(nextShuffle)
                    }
                    _statusMessage.value = if (nextShuffle) "Shuffle mode activated (Hardware entropy)." else "Sequential playback enabled."
                    addHistory(transcriptText, if (nextShuffle) "Turned shuffle ON" else "Turned shuffle OFF")
                }

                is VoiceIntent.ToggleRepeat -> {
                    val mode = intent.mode ?: androidx.media3.common.Player.REPEAT_MODE_ONE
                    withContext(Dispatchers.Main) {
                        playbackController.repeatMode = mode
                    }
                    _statusMessage.value = if (mode == androidx.media3.common.Player.REPEAT_MODE_ONE) "Repeating current song." else "Repeating queue."
                    addHistory(transcriptText, "Set repeat mode")
                }

                is VoiceIntent.ToggleFavorite -> {
                    withContext(Dispatchers.Main) {
                        playbackController.toggleFavoriteCurrentSong()
                    }
                    _statusMessage.value = "Toggled favorite for current song."
                    addHistory(transcriptText, "Toggled favorite")
                }

                is VoiceIntent.Volume -> {
                    withContext(Dispatchers.Main) {
                        try {
                            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                            val direction = if (intent.delta > 0) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
                            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
                        } catch (e: Throwable) {
                            Log.e("VoiceCommandViewModel", "Error adjusting volume", e)
                        }
                    }
                    _statusMessage.value = if (intent.delta > 0) "Volume increased." else "Volume decreased."
                    addHistory(transcriptText, if (intent.delta > 0) "Turned volume up" else "Turned volume down")
                }

                is VoiceIntent.Unknown -> {
                    _statusMessage.value = "Could not understand command \"$transcriptText\". Try \"Play [song title]\" or \"Pause\"."
                    addHistory(transcriptText, "Unrecognized command")
                }
            }
        }
    }

    private fun addHistory(transcript: String, description: String) {
        val current = _commandHistory.value.toMutableList()
        current.add(0, CommandHistoryItem(transcript, description))
        if (current.size > 15) {
            current.removeAt(current.lastIndex)
        }
        _commandHistory.value = current
    }

    override fun onCleared() {
        wakeWordEngine?.stop()
        wakeWordEngine = null
        voiceRecognizer?.stopListening()
        voiceRecognizer = null
        super.onCleared()
    }
}
