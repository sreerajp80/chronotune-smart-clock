package `in`.sreerajp.chronotune_smart_clock.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.*
import kotlin.coroutines.coroutineContext
import kotlin.math.sin

class AudioEngine(private val context: Context) {
    private var mediaPlayer: MediaPlayer? = null
    private var synthJob: Job? = null
    private var playlistJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    data class PlaylistItem(val toneName: String, val uri: String?)

    // List of gorgeous synth track options
    val builtInTones = listOf(
        "Morning Breeze" to listOf(261.63, 329.63, 392.00, 523.25), // C Major (C4, E4, G4, C5)
        "Cosmic Shimmer" to listOf(440.00, 554.37, 659.25, 880.00), // A Major arpeggio
        "Ocean Zen" to listOf(349.23, 440.00, 523.25, 698.46),     // F Major arpeggio
        "Digital Alarm" to listOf(1000.0, 1500.0, 1000.0, 1500.0),  // Alternate buzzer style
        "Retro Chiptune" to listOf(523.25, 587.33, 659.25, 698.46, 783.99), // Upbeat scale
        "Deep Lofi Lounge" to listOf(196.00, 246.94, 293.66, 392.00) // Soothing G Major arpeggio
    )

    // Plays simulated pre-loaded files or custom select URIs
    fun playAudio(toneName: String, uriString: String? = null, volume: Float = 0.8f, durationMs: Long? = null) {
        stop()

        if (!uriString.isNullOrBlank()) {
            try {
                val mp = MediaPlayer()
                mp.setDataSource(context, Uri.parse(uriString))
                mp.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                mp.isLooping = true
                mp.setVolume(volume, volume)
                mp.setOnPreparedListener { it.start() }
                mp.setOnErrorListener { _, what, extra ->
                    Log.e("AudioEngine", "MediaPlayer error playing URI: what=$what extra=$extra")
                    mediaPlayer = null
                    mp.release()
                    false
                }
                mediaPlayer = mp
                mp.prepareAsync()
                Log.d("AudioEngine", "Preparing custom URI async: $uriString")
                setupDurationTimeout(durationMs)
                return
            } catch (e: Exception) {
                Log.e("AudioEngine", "Failed to play custom URI, defaulting to synth fallback: ${e.message}")
            }
        }

        // Search in built-in tones
        val synthTrack = builtInTones.find { it.first == toneName }
        if (synthTrack != null) {
            playSynthArpeggio(synthTrack.second, volume)
            setupDurationTimeout(durationMs)
        } else {
            // Play system default Alarm Sound as ultimate fallback
            try {
                val alertUri = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM)
                    ?: android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_RINGTONE)
                val mp = MediaPlayer()
                mp.setDataSource(context, alertUri)
                mp.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                mp.isLooping = true
                mp.setVolume(volume, volume)
                mp.setOnPreparedListener { it.start() }
                mp.setOnErrorListener { _, what, extra ->
                    Log.e("AudioEngine", "MediaPlayer error playing default: what=$what extra=$extra")
                    mediaPlayer = null
                    mp.release()
                    false
                }
                mediaPlayer = mp
                mp.prepareAsync()
                setupDurationTimeout(durationMs)
            } catch (e: Exception) {
                Log.e("AudioEngine", "Failed to play system default tone: ${e.message}")
                // Fallback to basic synth arpeggio
                playSynthArpeggio(listOf(440.0, 554.37, 659.25), volume)
                setupDurationTimeout(durationMs)
            }
        }
    }

    private fun setupDurationTimeout(durationMs: Long?) {
        if (durationMs != null && durationMs > 0) {
            scope.launch {
                delay(durationMs)
                stop()
            }
        }
    }

    private fun playSynthArpeggio(frequencies: List<Double>, volume: Float) {
        val sampleRate = 44100
        val bufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        val audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        audioTrack.play()

        synthJob = scope.launch(Dispatchers.Default) {
            val noteDurationSamples = sampleRate / 2 // 0.5 seconds per note arpeggio
            var currentNoteIndex = 0

            try {
                while (isActive) {
                    val freq = frequencies[currentNoteIndex]
                    val shortBuffer = ShortArray(noteDurationSamples)
                    
                    for (i in 0 until noteDurationSamples) {
                        // Synthesize wave: Sine with beautiful decay envelope (bell effect)
                        val angle = 2.0 * Math.PI * i * freq / sampleRate
                        val envelope = (noteDurationSamples - i).toDouble() / noteDurationSamples
                        val sampleVal = (sin(angle) * Short.MAX_VALUE * volume * 0.5 * envelope).toInt()
                        shortBuffer[i] = sampleVal.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                    }

                    audioTrack.write(shortBuffer, 0, noteDurationSamples)
                    currentNoteIndex = (currentNoteIndex + 1) % frequencies.size
                }
            } catch (e: Exception) {
                Log.d("AudioEngine", "Synth playback ended.")
            } finally {
                try {
                    audioTrack.stop()
                    audioTrack.release()
                } catch (e: Exception) { /* ignore */ }
            }
        }
    }

    // Plays a sequence of melodies/files one after another. Loops back to the start
    // after the last item until durationMs elapses (then everything stops).
    fun playPlaylist(items: List<PlaylistItem>, volume: Float, durationMs: Long?) {
        stop()
        if (items.isEmpty()) return

        playlistJob = scope.launch {
            var idx = 0
            while (isActive) {
                val item = items[idx % items.size]
                try {
                    if (!item.uri.isNullOrBlank()) {
                        playFileSuspending(item.uri, volume)
                    } else {
                        val freqs = builtInTones.find { it.first == item.toneName }?.second
                            ?: listOf(440.0, 554.37, 659.25)
                        playSynthArpeggioOnce(freqs, volume)
                    }
                } catch (e: Exception) {
                    Log.e("AudioEngine", "Playlist item '${item.toneName}' failed: ${e.message}")
                    delay(250)
                }
                idx++
            }
        }
        setupDurationTimeout(durationMs)
    }

    // Suspends until the file completes or errors out.
    private suspend fun playFileSuspending(uriString: String, volume: Float) {
        val done = CompletableDeferred<Unit>()
        val mp = MediaPlayer()
        mediaPlayer = mp
        try {
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            mp.isLooping = false
            mp.setVolume(volume, volume)
            mp.setOnCompletionListener {
                if (!done.isCompleted) done.complete(Unit)
            }
            mp.setOnErrorListener { _, what, extra ->
                Log.e("AudioEngine", "Playlist file error: what=$what extra=$extra uri=$uriString")
                if (!done.isCompleted) done.complete(Unit)
                false
            }
            mp.setOnPreparedListener { it.start() }
            mp.setDataSource(context, Uri.parse(uriString))
            mp.prepareAsync()
            try {
                done.await()
            } catch (e: CancellationException) {
                try { if (mp.isPlaying) mp.stop() } catch (_: Exception) { /* ignore */ }
                throw e
            }
        } finally {
            try { mp.release() } catch (_: Exception) { /* ignore */ }
            if (mediaPlayer === mp) mediaPlayer = null
        }
    }

    // Plays a single full cycle through every frequency in the arpeggio, then returns.
    private suspend fun playSynthArpeggioOnce(frequencies: List<Double>, volume: Float) {
        val sampleRate = 44100
        val bufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        val audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        try {
            audioTrack.play()
            val noteDurationSamples = sampleRate / 2 // 0.5s per note
            val shortBuffer = ShortArray(noteDurationSamples)
            for (freq in frequencies) {
                if (!coroutineContext.isActive) break
                for (i in 0 until noteDurationSamples) {
                    val angle = 2.0 * Math.PI * i * freq / sampleRate
                    val envelope = (noteDurationSamples - i).toDouble() / noteDurationSamples
                    val sampleVal = (sin(angle) * Short.MAX_VALUE * volume * 0.5 * envelope).toInt()
                    shortBuffer[i] = sampleVal.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                }
                audioTrack.write(shortBuffer, 0, noteDurationSamples)
            }
        } finally {
            try { audioTrack.stop() } catch (_: Exception) { /* ignore */ }
            try { audioTrack.release() } catch (_: Exception) { /* ignore */ }
        }
    }

    fun stop() {
        // Cancel any running playlist loop first so it doesn't queue another item.
        playlistJob?.cancel()
        playlistJob = null

        // Stop dynamic synth arpeggios
        synthJob?.cancel()
        synthJob = null

        // Stop standard playback
        try {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
            }
        } catch (e: Exception) {
            Log.e("AudioEngine", "Error stopping MediaPlayer: ${e.message}")
        } finally {
            mediaPlayer = null
        }
    }
}
