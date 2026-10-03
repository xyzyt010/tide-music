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

    private val _isInitializing = MutableStateFlow(true)
    val isInitializing: StateFlow<Boolean> = _isInitializing.asStateFlow()

    private val _hasPermission = MutableStateFlow(false)
    val hasPermission: StateFlow<Boolean> = _hasPermission.asStateFlow()

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

    private val _isVadSpeechActive = MutableStateFlow(false)
    val isVadSpeechActive: StateFlow<Boolean> = _isVadSpeechActive.asStateFlow()

    private val _statusMessage = MutableStateFlow("Initializing TenVAD, microWakeWord, Vosk & Laya ONNX...")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private val _commandHistory = MutableStateFlow<List<CommandHistoryItem>>(emptyList())
    val commandHistory: StateFlow<List<CommandHistoryItem>> = _commandHistory.asStateFlow()

    private var tenVad: TenVadDetector? = null
    private var wakeWordEngine: WakeWordEngine? = null
    private var voskRecognizer: VoskSpeechRecognizer? = null
    private var layaEngine: LayaDecisionEngine? = null

    init {
        initializeEngines()
    }

    private fun initializeEngines() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _statusMessage.value = "Loading TenVAD voice activity detector..."
                val vad = TenVadDetector(hopSize = 256, threshold = 0.5f)
                tenVad = vad

                _statusMessage.value = "Loading microWakeWord neural model ('Hey Jarvis')..."
                wakeWordEngine = WakeWordEngine(
                    context = context,
                    tenVad = vad,
                    onWakeWordDetected = {
                        viewModelScope.launch(Dispatchers.Main) {
                            Log.i("VoiceCommandViewModel", "Wake word recognized via microWakeWord! Activating STT...")
                            _statusMessage.value = "Wake word detected! Listening..."
                            wakeWordEngine?.stop()
                            startListening()
                        }
                    },
                    onRmsChanged = { rms ->
                        if (!_isListening.value) {
                            _audioRms.value = rms
                        }
                    }
                )

                _statusMessage.value = "Preparing Vosk offline ASR & Laya Multilingual ONNX..."
                val vosk = VoskSpeechRecognizer(
                    context = context,
                    tenVad = vad,
                    onReady = {
                        viewModelScope.launch(Dispatchers.Main) {
                            _isListening.value = true
                            _statusMessage.value = "Listening… Speak your command now."
                        }
                    },
                    onRmsChanged = { rms ->
                        _audioRms.value = rms
                    },
                    onVadStateChanged = { isSpeech, prob ->
                        _isVadSpeechActive.value = isSpeech
                    },
                    onPartialResult = { partial ->
                        viewModelScope.launch(Dispatchers.Main) {
                            _partialTranscript.value = partial
                        }
                    },
                    onFinalResult = { finalResult ->
                        viewModelScope.launch(Dispatchers.Main) {
                            _isListening.value = false
                            _transcript.value = finalResult
                            _partialTranscript.value = ""
                            processTranscript(finalResult)
                            if (_isWakeWordActive.value && _hasPermission.value) {
                                wakeWordEngine?.start()
                            }
                        }
                    },
                    onError = { err ->
                        viewModelScope.launch(Dispatchers.Main) {
                            _isListening.value = false
                            _audioRms.value = 0f
                            _statusMessage.value = err
                            if (_isWakeWordActive.value && _hasPermission.value) {
                                wakeWordEngine?.start()
                            }
                        }
                    },
                    onListeningEnded = {
                        viewModelScope.launch(Dispatchers.Main) {
                            _isListening.value = false
                            _audioRms.value = 0f
                        }
                    }
                )
                vosk.initialize()
                voskRecognizer = vosk

                val laya = LayaDecisionEngine(context)
                laya.initialize()
                layaEngine = laya

                _isInitializing.value = false
                _statusMessage.value = "Ready. Say \"Hey Jarvis\" or tap the microphone."
                Log.i("VoiceCommandViewModel", "All voice AI engines initialized successfully")

                // Start wake word engine only if mic permission is already granted
                if (_hasPermission.value && _isWakeWordActive.value) {
                    wakeWordEngine?.start()
                }
            } catch (e: Throwable) {
                Log.e("VoiceCommandViewModel", "Failed to initialize AI engines: ${e.message}", e)
                _isInitializing.value = false
                _statusMessage.value = "Ready (using standard speech engine)."
            }
        }
    }

    fun onPermissionGranted(granted: Boolean) {
        _hasPermission.value = granted
        if (granted) {
            _statusMessage.value = "Ready. Say \"Hey Jarvis\" or tap the microphone."
            if (_isWakeWordActive.value && !_isInitializing.value && !_isListening.value) {
                wakeWordEngine?.start()
            }
        } else {
            _statusMessage.value = "Microphone permission is required for voice commands."
            wakeWordEngine?.stop()
        }
    }

    fun startListening() {
        if (!_hasPermission.value) {
            _statusMessage.value = "Grant microphone permission first."
            return
        }
        _partialTranscript.value = ""
        _audioRms.value = 0f
        _statusMessage.value = "Listening…"
        wakeWordEngine?.stop()
        voskRecognizer?.startListening()
    }

    fun toggleListening() {
        if (_isListening.value) {
            stopListening()
        } else {
            startListening()
        }
    }

    fun stopListening() {
        voskRecognizer?.stopListening()
        _isListening.value = false
        _audioRms.value = 0f
        if (_isWakeWordActive.value && _hasPermission.value) {
            wakeWordEngine?.start()
        }
    }

    fun toggleWakeWord(active: Boolean) {
        _isWakeWordActive.value = active
        if (active && _hasPermission.value && !_isListening.value) {
            wakeWordEngine?.start()
            _statusMessage.value = "Wake word \"Hey Jarvis\" enabled."
        } else {
            wakeWordEngine?.stop()
            _statusMessage.value = if (!active) "Wake word disabled. Tap mic to speak." else _statusMessage.value
        }
    }

    fun executeQuickCommand(command: String) {
        _transcript.value = command
        processTranscript(command)
    }

    /**
     * Executes recognized workflow strictly if the spoken intent is recognized.
     */
    private fun processTranscript(text: String) {
        if (text.isBlank()) {
            _statusMessage.value = "No speech detected. Tap mic to try again."
            return
        }

        viewModelScope.launch {
            val allSongs: List<Song> = withContext(Dispatchers.IO) {
                try {
                    repository.observeAllSongs().first()
                } catch (e: Exception) {
                    emptyList<Song>()
                }
            }

            // Run intent classification through Laya decision model
            val decisionEngine = layaEngine
            val (intent, confidence) = decisionEngine?.classifyIntent(text, allSongs)
                ?: Pair(VoiceIntent.Unknown(text), 0f)

            _lastIntent.value = intent
            _matchConfidence.value = confidence

            when (intent) {
                is VoiceIntent.PlaySong -> {
                    val (bestSong, matchScore) = decisionEngine?.matchBestSong(intent.query, allSongs) ?: Pair(null, 0f)
                    _matchedSong.value = bestSong
                    _matchConfidence.value = matchScore

                    if (bestSong != null) {
                        _statusMessage.value = "Playing \"${bestSong.title}\" by ${bestSong.artist}"
                        executeWorkflow("Played \"${bestSong.title}\"") {
                            playbackController.playSong(bestSong)
                        }
                    } else {
                        _statusMessage.value = "Song \"${intent.query}\" not found in library."
                        addHistory(text, "Song not found: ${intent.query}")
                    }
                }

                is VoiceIntent.PlayCurrent -> {
                    _statusMessage.value = "Resuming playback"
                    executeWorkflow("Resumed playback") {
                        playbackController.playPause()
                    }
                }

                is VoiceIntent.Pause -> {
                    _statusMessage.value = "Playback paused"
                    executeWorkflow("Paused music") {
                        playbackController.pause()
                    }
                }

                is VoiceIntent.Next -> {
                    _statusMessage.value = "Skipping to next track"
                    executeWorkflow("Skipped to next track") {
                        playbackController.next()
                    }
                }

                is VoiceIntent.Previous -> {
                    _statusMessage.value = "Returning to previous track"
                    executeWorkflow("Previous track") {
                        playbackController.previous()
                    }
                }

                is VoiceIntent.ToggleShuffle -> {
                    val enabled = playbackController.isShuffleEnabledState.value
                    val newMode = !enabled
                    _statusMessage.value = if (newMode) "Shuffle enabled (Hardware entropy)" else "Shuffle disabled"
                    executeWorkflow(if (newMode) "Turned shuffle ON" else "Turned shuffle OFF") {
                        playbackController.toggleShuffle()
                    }
                }

                is VoiceIntent.Volume -> {
                    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                    if (audioManager != null) {
                        val direction = if (intent.delta > 0) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
                        audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
                        _statusMessage.value = if (intent.delta > 0) "Volume increased" else "Volume decreased"
                        addHistory(text, if (intent.delta > 0) "Increased volume" else "Decreased volume")
                    }
                }

                is VoiceIntent.ToggleRepeat -> {
                    executeWorkflow("Toggled repeat mode") {
                        playbackController.toggleRepeat()
                    }
                }

                is VoiceIntent.ToggleFavorite -> {
                    _statusMessage.value = "Toggled favorite"
                    addHistory(text, "Toggled favorite")
                }

                is VoiceIntent.Unknown -> {
                    _statusMessage.value = "Command not recognized: \"$text\""
                    addHistory(text, "Unrecognized command")
                }
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

    private fun addHistory(spokenText: String, actionDesc: String) {
        val newItem = CommandHistoryItem(
            transcript = spokenText,
            actionDescription = actionDesc
        )
        _commandHistory.value = listOf(newItem) + _commandHistory.value.take(19)
    }

    override fun onCleared() {
        super.onCleared()
        wakeWordEngine?.stop()
        wakeWordEngine?.close()
        voskRecognizer?.stopListening()
        voskRecognizer?.close()
        tenVad?.close()
        layaEngine?.close()
    }
}
