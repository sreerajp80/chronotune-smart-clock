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
import kotlin.math.cos
import kotlin.math.sin

class AudioEngine(context: Context) {
    // Always hold the application context so this engine can never leak an Activity, even when
    // it is parked in the static ActiveAlarmState singleton.
    private val context: Context = context.applicationContext

    private var mediaPlayer: MediaPlayer? = null
    private var synthJob: Job? = null
    private var playlistJob: Job? = null
    private var fadeJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val audioManager by lazy {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    // The system alarm-stream level we override while ringing, so we can put it back
    // afterwards. -1 means "not currently overridden".
    private var savedAlarmStreamVolume: Int = -1

    // Make the in-app volume setting the *only* thing that determines loudness. All playback
    // runs on USAGE_ALARM, whose output the OS scales by the device's STREAM_ALARM slider; by
    // pinning that stream to max while we ring, the per-track `volume` scalar (and the synth's
    // baked-in gain) become the sole determinant — independent of the phone's alarm volume.
    private fun overrideAlarmStream() {
        try {
            if (savedAlarmStreamVolume == -1) {
                savedAlarmStreamVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
            }
            val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, max, 0)
        } catch (e: Exception) {
            Log.e("AudioEngine", "Failed to override alarm stream volume: ${e.message}")
        }
    }

    // Restore the user's original alarm-stream level so we don't permanently change it.
    private fun restoreAlarmStream() {
        if (savedAlarmStreamVolume == -1) return
        try {
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, savedAlarmStreamVolume, 0)
        } catch (e: Exception) {
            Log.e("AudioEngine", "Failed to restore alarm stream volume: ${e.message}")
        } finally {
            savedAlarmStreamVolume = -1
        }
    }

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

    // Plays simulated pre-loaded files or custom select URIs.
    // fadeInMs > 0 ramps the volume from near-silent up to `volume` over that window.
    fun playAudio(toneName: String, uriString: String? = null, volume: Float = 0.8f, durationMs: Long? = null, fadeInMs: Long = 0L) {
        stop()
        overrideAlarmStream()

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
                applyInitialVolume(mp, volume, fadeInMs)
                mp.setOnPreparedListener {
                    it.start()
                    startMediaFade(it, volume, fadeInMs)
                }
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
            playSynthArpeggio(synthTrack.second, volume, fadeInMs)
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
                applyInitialVolume(mp, volume, fadeInMs)
                mp.setOnPreparedListener {
                    it.start()
                    startMediaFade(it, volume, fadeInMs)
                }
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
                playSynthArpeggio(listOf(440.0, 554.37, 659.25), volume, fadeInMs)
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

    // Low volume floor a fade starts from (never fully silent so the very first beat is audible).
    private val fadeFloor = 0.05f

    // Set the player's starting volume: the fade floor when ramping, otherwise the full target.
    private fun applyInitialVolume(mp: MediaPlayer, volume: Float, fadeInMs: Long) {
        val start = if (fadeInMs > 0L) (volume * fadeFloor).coerceAtMost(volume) else volume
        try { mp.setVolume(start, start) } catch (_: Exception) { /* released */ }
    }

    // Steps the player volume from the floor up to `volume` over fadeInMs in ~50 ms increments.
    // No-op when fadeInMs <= 0. Cancelled (and superseded) by stop().
    private fun startMediaFade(mp: MediaPlayer, volume: Float, fadeInMs: Long) {
        if (fadeInMs <= 0L) return
        fadeJob?.cancel()
        fadeJob = scope.launch {
            val stepMs = 50L
            val steps = (fadeInMs / stepMs).coerceAtLeast(1L)
            val from = volume * fadeFloor
            for (i in 1..steps) {
                if (!isActive) return@launch
                val v = (from + (volume - from) * (i.toFloat() / steps)).coerceIn(0f, volume)
                try {
                    if (mediaPlayer === mp) mp.setVolume(v, v) else return@launch
                } catch (_: Exception) {
                    return@launch // player released mid-fade
                }
                delay(stepMs)
            }
        }
    }

    // Crossfade shape used when transitioning between Music-schedule playlist items.
    enum class CrossfadeCurve { LINEAR, EQUAL_POWER }

    private fun playSynthArpeggio(frequencies: List<Double>, volume: Float, fadeInMs: Long = 0L) {
        val sampleRate = 44100
        val bufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_STEREO,
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
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
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
            // Fade ramp measured in frames; 0 means no fade (gain immediately 1f).
            val fadeFrames = (fadeInMs / 1000.0 * sampleRate)
            var totalFrames = 0L
            var currentNoteIndex = 0

            try {
                while (isActive) {
                    val freq = frequencies[currentNoteIndex]
                    // Interleaved stereo: [L, R] per frame.
                    val shortBuffer = ShortArray(noteDurationSamples * 2)

                    for (i in 0 until noteDurationSamples) {
                        // Harmonic-rich tone with an ADSR envelope; right channel slightly
                        // detuned for stereo width.
                        val env = adsrEnvelope(i, noteDurationSamples)
                        val fade = if (fadeFrames > 0.0) (totalFrames / fadeFrames).coerceAtMost(1.0) else 1.0
                        val amp = Short.MAX_VALUE * volume * 0.5 * env * fade
                        val left = (harmonicSample(i, freq, sampleRate) * amp).toInt()
                        val right = (harmonicSample(i, freq * stereoDetune, sampleRate) * amp).toInt()
                        shortBuffer[i * 2] = left.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                        shortBuffer[i * 2 + 1] = right.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                        totalFrames++
                    }

                    audioTrack.write(shortBuffer, 0, shortBuffer.size)
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

    // Plays a sequence of melodies/files one after another, loosely looping until durationMs elapses.
    // Consecutive items are crossfaded: the outgoing item's tail overlaps the incoming item's head,
    // their gains ramping in opposite directions over `crossfadeMs` (0 = hard cut, the old behavior).
    //   - curve     : LINEAR or EQUAL_POWER (constant perceived loudness through the blend).
    //   - normalize : when true, file items are level-matched toward a reference loudness (see Voice).
    // Used only by Music schedules; alarms never call this.
    fun playPlaylist(
        items: List<PlaylistItem>,
        volume: Float,
        durationMs: Long?,
        crossfadeMs: Long = 0L,
        curve: CrossfadeCurve = CrossfadeCurve.EQUAL_POWER,
        normalize: Boolean = false,
    ) {
        stop()
        if (items.isEmpty()) return
        overrideAlarmStream()

        playlistJob = scope.launch {
            var idx = 0
            var current: Voice? = null
            var incoming: Voice? = null
            try {
                while (isActive) {
                    val item = items[idx % items.size]
                    incoming = try {
                        createVoice(item, volume, normalize)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.e("AudioEngine", "Playlist item '${item.toneName}' failed: ${e.message}")
                        delay(250)
                        idx++
                        continue
                    }
                    val inc = incoming!!

                    // Usable crossfade window for this transition: never longer than half of either
                    // item, and skipped entirely for the first item or unknown-duration outgoing item.
                    val cur = current
                    var xf = if (crossfadeMs <= 0L || cur == null) 0L else crossfadeMs
                    if (xf > 0L && inc.durationMs > 0L) xf = minOf(xf, inc.durationMs / 2)
                    if (xf > 0L && cur != null && cur.durationMs > 0L) xf = minOf(xf, cur.durationMs / 2)

                    if (cur == null || xf <= 0L) {
                        inc.setGain(1f)
                        cur?.release()
                    } else {
                        crossfade(cur, inc, xf, curve)
                        cur.release()
                    }
                    current = inc
                    incoming = null

                    // Hold until it's time to bring in the next item (xf before the end), or the
                    // current item finishes (the fallback for unknown-duration files).
                    while (isActive && !inc.finished()) {
                        val dur = inc.durationMs
                        if (dur > 0L && inc.elapsedMs() >= dur - xf) break
                        delay(40)
                    }
                    idx++
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("AudioEngine", "Playlist playback error: ${e.message}")
            } finally {
                withContext(NonCancellable) {
                    try { current?.release() } catch (_: Exception) { /* ignore */ }
                    try { incoming?.release() } catch (_: Exception) { /* ignore */ }
                }
            }
        }
        setupDurationTimeout(durationMs)
    }

    private suspend fun createVoice(item: PlaylistItem, volume: Float, normalize: Boolean): Voice {
        return if (!item.uri.isNullOrBlank()) {
            FileVoice.create(context, item.uri, volume, normalize)
        } else {
            val freqs = builtInTones.find { it.first == item.toneName }?.second
                ?: listOf(440.0, 554.37, 659.25)
            SynthVoice.create(freqs, volume)
        }
    }

    // Ramps `out` down and `inn` up over `durationMs`, in ~40 ms steps, using the chosen curve.
    // EQUAL_POWER (cos/sin) keeps the summed power roughly constant so there's no loudness dip mid-blend.
    private suspend fun crossfade(out: Voice, inn: Voice, durationMs: Long, curve: CrossfadeCurve) {
        val stepMs = 40L
        val steps = (durationMs / stepMs).coerceAtLeast(1L)
        val halfPi = (Math.PI / 2.0).toFloat()
        for (s in 1..steps) {
            if (!coroutineContext.isActive) return
            val f = (s.toFloat() / steps).coerceIn(0f, 1f)
            val outG: Float
            val inG: Float
            when (curve) {
                CrossfadeCurve.LINEAR -> { outG = 1f - f; inG = f }
                CrossfadeCurve.EQUAL_POWER -> { outG = cos(f * halfPi); inG = sin(f * halfPi) }
            }
            out.setGain(outG)
            inn.setGain(inG)
            delay(stepMs)
        }
        out.setGain(0f)
        inn.setGain(1f)
    }

    // ---- Playlist voices ------------------------------------------------------------------------
    // A single playing playlist item whose loudness we can ramp for crossfading. setGain(g) takes a
    // 0..1 multiplier that is applied on top of the playlist `volume` (and, for files, a normalization
    // gain). Backed by a MediaPlayer (files) or a MODE_STATIC AudioTrack (built-in synth).
    private sealed class Voice {
        abstract val durationMs: Long          // <= 0 means unknown
        abstract fun setGain(g: Float)
        abstract fun elapsedMs(): Long
        abstract fun finished(): Boolean
        abstract fun release()
    }

    private class FileVoice private constructor(
        private val mp: MediaPlayer,
        override val durationMs: Long,
        private val volume: Float,
        private val normGain: Float,
    ) : Voice() {
        @Volatile private var completed = false

        override fun setGain(g: Float) {
            val v = (volume * normGain * g).coerceIn(0f, 1f)
            try { mp.setVolume(v, v) } catch (_: Exception) { /* released */ }
        }

        override fun elapsedMs(): Long = try { mp.currentPosition.toLong() } catch (_: Exception) { 0L }

        override fun finished(): Boolean = completed

        override fun release() {
            try { if (mp.isPlaying) mp.stop() } catch (_: Exception) { /* ignore */ }
            try { mp.release() } catch (_: Exception) { /* ignore */ }
        }

        companion object {
            suspend fun create(context: Context, uriString: String, volume: Float, normalize: Boolean): FileVoice {
                val mp = MediaPlayer()
                mp.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                mp.isLooping = false
                val norm = if (normalize) computeFileNormGain(context, uriString) else 1f
                try {
                    withContext(Dispatchers.IO) {
                        mp.setDataSource(context, Uri.parse(uriString))
                        mp.prepare()
                    }
                } catch (e: Exception) {
                    try { mp.release() } catch (_: Exception) { /* ignore */ }
                    throw e
                }
                val dur = try { mp.duration.toLong() } catch (_: Exception) { -1L }
                val voice = FileVoice(mp, if (dur > 0L) dur else -1L, volume, norm)
                voice.setGain(0f) // start silent; the caller ramps it in (or sets full for the first item)
                mp.setOnCompletionListener { voice.completed = true }
                mp.setOnErrorListener { _, what, extra ->
                    Log.e("AudioEngine", "Playlist file error: what=$what extra=$extra uri=$uriString")
                    voice.completed = true
                    false
                }
                mp.start()
                return voice
            }
        }
    }

    private class SynthVoice private constructor(
        private val track: AudioTrack,
        private val totalFrames: Int,
        private val sampleRate: Int,
        private val volume: Float,
    ) : Voice() {
        override val durationMs: Long = totalFrames * 1000L / sampleRate

        override fun setGain(g: Float) {
            val v = (volume * g).coerceIn(0f, 1f)
            try { track.setVolume(v) } catch (_: Exception) { /* released */ }
        }

        override fun elapsedMs(): Long =
            try { track.playbackHeadPosition.toLong() * 1000L / sampleRate } catch (_: Exception) { 0L }

        override fun finished(): Boolean =
            try { track.playbackHeadPosition >= totalFrames } catch (_: Exception) { true }

        override fun release() {
            try { track.pause() } catch (_: Exception) { /* ignore */ }
            try { track.flush() } catch (_: Exception) { /* ignore */ }
            try { track.release() } catch (_: Exception) { /* ignore */ }
        }

        companion object {
            fun create(frequencies: List<Double>, volume: Float): SynthVoice {
                val sampleRate = 44100
                val noteDurationSamples = sampleRate / 2 // 0.5s per note
                val totalFrames = noteDurationSamples * frequencies.size
                val buffer = ShortArray(totalFrames * 2) // interleaved stereo [L, R]
                // Amplitude here is gain-agnostic; loudness is controlled live via track.setVolume().
                var w = 0
                for (freq in frequencies) {
                    for (i in 0 until noteDurationSamples) {
                        val env = adsrEnvelope(i, noteDurationSamples)
                        val amp = Short.MAX_VALUE * 0.5 * env
                        val left = (harmonicSample(i, freq, sampleRate) * amp).toInt()
                        val right = (harmonicSample(i, freq * stereoDetune, sampleRate) * amp).toInt()
                        buffer[w++] = left.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                        buffer[w++] = right.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                    }
                }
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .build()
                    )
                    .setBufferSizeInBytes(buffer.size * 2) // shorts -> bytes
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
                track.write(buffer, 0, buffer.size)
                track.setVolume(0f) // start silent; caller ramps in
                track.play()
                return SynthVoice(track, totalFrames, sampleRate, volume)
            }
        }
    }

    companion object {
        // Subtle detune applied to the right channel so the synth tones render with genuine stereo
        // width (a gentle chorus) instead of identical dual-mono on both speakers.
        private const val stereoDetune = 1.004

        // Additive-synthesis weights: fundamental + a few overtones with falling amplitude. Summing
        // these gives a warmer, organ/bell-like timbre instead of a bare sine. Normalised by the weight
        // sum so the composite waveform stays within ~[-1, 1].
        private val harmonicWeights = doubleArrayOf(1.0, 0.45, 0.22, 0.12, 0.06)
        private val harmonicNorm = harmonicWeights.sum()

        // Harmonic-rich waveform value in [-1, 1] for sample `i` at `freq`.
        fun harmonicSample(i: Int, freq: Double, sampleRate: Int): Double {
            var acc = 0.0
            val base = 2.0 * Math.PI * i * freq / sampleRate
            for (h in harmonicWeights.indices) {
                acc += harmonicWeights[h] * sin(base * (h + 1))
            }
            return acc / harmonicNorm
        }

        // ADSR-style gain in [0, 1] across one note of `noteSamples` frames: soft attack, gentle decay
        // to a sustain plateau, then a smooth release tail. Replaces the old abrupt linear decay for a
        // more musical, less clicky note.
        fun adsrEnvelope(i: Int, noteSamples: Int): Double {
            val t = i.toDouble() / noteSamples
            val attack = 0.04
            val decay = 0.18
            val release = 0.30
            val sustain = 0.65
            val g = when {
                t < attack -> t / attack
                t < attack + decay -> 1.0 - (1.0 - sustain) * ((t - attack) / decay)
                t < 1.0 - release -> sustain
                else -> sustain * (1.0 - (t - (1.0 - release)) / release)
            }
            return g.coerceIn(0.0, 1.0)
        }

        // ---- Loudness normalization ----------------------------------------------------------------
        // Reference RMS (in normalized [-1, 1] units) that file items are nudged toward, so a quiet
        // and a loud track play at roughly the same perceived level when normalization is on.
        private const val TARGET_RMS = 0.18
        private val normGainCache = java.util.concurrent.ConcurrentHashMap<String, Float>()

        // Per-URI normalization gain (cached). Estimates the file's loudness from a short decoded prefix
        // and returns targetRms/measuredRms, clamped to ~±6 dB. Any failure falls back to unity (no-op).
        fun computeFileNormGain(context: Context, uriString: String): Float {
            normGainCache[uriString]?.let { return it }
            val gain = try {
                val rms = measurePrefixRms(context, uriString)
                if (rms <= 0.0) 1f else (TARGET_RMS / rms).coerceIn(0.5, 2.0).toFloat()
            } catch (e: Exception) {
                Log.e("AudioEngine", "Loudness analysis failed for $uriString: ${e.message}")
                1f
            }
            normGainCache[uriString] = gain
            return gain
        }

        // Decodes up to ~4 s of PCM from the start of the file and returns its RMS in [0, 1]. Assumes
        // 16-bit PCM decoder output (the common case); best-effort, bounded, and clamped downstream.
        private fun measurePrefixRms(context: Context, uriString: String): Double {
            val extractor = android.media.MediaExtractor()
            var codec: android.media.MediaCodec? = null
            try {
                extractor.setDataSource(context, Uri.parse(uriString), null)
                var trackIndex = -1
                var format: android.media.MediaFormat? = null
                for (t in 0 until extractor.trackCount) {
                    val f = extractor.getTrackFormat(t)
                    val mime = f.getString(android.media.MediaFormat.KEY_MIME) ?: continue
                    if (mime.startsWith("audio/")) { trackIndex = t; format = f; break }
                }
                if (trackIndex < 0 || format == null) return 0.0
                extractor.selectTrack(trackIndex)
                val mime = format.getString(android.media.MediaFormat.KEY_MIME)!!
                codec = android.media.MediaCodec.createDecoderByType(mime)
                codec.configure(format, null, null, 0)
                codec.start()

                val maxSamples = 44100L * 4 // ~4 s of samples
                var sampleCount = 0L
                var sumSquares = 0.0
                val info = android.media.MediaCodec.BufferInfo()
                var sawInputEOS = false
                var sawOutputEOS = false

                while (!sawOutputEOS && sampleCount < maxSamples) {
                    if (!sawInputEOS) {
                        val inIndex = codec.dequeueInputBuffer(10_000)
                        if (inIndex >= 0) {
                            val inBuf = codec.getInputBuffer(inIndex)!!
                            val size = extractor.readSampleData(inBuf, 0)
                            if (size < 0) {
                                codec.queueInputBuffer(inIndex, 0, 0, 0, android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                sawInputEOS = true
                            } else {
                                codec.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }
                    val outIndex = codec.dequeueOutputBuffer(info, 10_000)
                    if (outIndex >= 0) {
                        if (info.flags and android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) sawOutputEOS = true
                        val outBuf = codec.getOutputBuffer(outIndex)
                        if (outBuf != null && info.size > 0) {
                            outBuf.position(info.offset)
                            outBuf.limit(info.offset + info.size)
                            val shorts = outBuf.order(java.nio.ByteOrder.nativeOrder()).asShortBuffer()
                            while (shorts.hasRemaining() && sampleCount < maxSamples) {
                                val s = shorts.get() / 32768.0
                                sumSquares += s * s
                                sampleCount++
                            }
                        }
                        codec.releaseOutputBuffer(outIndex, false)
                    }
                }
                return if (sampleCount > 0) Math.sqrt(sumSquares / sampleCount) else 0.0
            } finally {
                try { codec?.stop() } catch (_: Exception) { /* ignore */ }
                try { codec?.release() } catch (_: Exception) { /* ignore */ }
                try { extractor.release() } catch (_: Exception) { /* ignore */ }
            }
        }
    }

    fun stop() {
        // Cancel any in-flight volume fade so it can't touch a player we're about to release.
        fadeJob?.cancel()
        fadeJob = null

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

        // Put the device's alarm-stream volume back the way the user had it.
        restoreAlarmStream()
    }
}
