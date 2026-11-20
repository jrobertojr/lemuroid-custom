package com.swordfish.lemuroid.app.shared.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.SystemClock
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.FrontendTheme
import com.swordfish.lemuroid.lib.preferences.SharedPreferencesHelper
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin

object FrontendSoundEngine {
    private const val SAMPLE_RATE = 22050
    private const val IDLE_RELEASE_MS = 3_000L
    private const val NAVIGATE_DEBOUNCE_MS = 22L

    private enum class SoundType {
        NAVIGATE,
        CONFIRM,
        SWITCH_SYSTEM,
        FAVORITE,
        BACK,
    }

    private enum class SoundProfile {
        WARM_SOFT,
        RETRO_CHIP,
        HEAVY_CRISP,
        HITECH_CLEAN,
    }

    private enum class Waveform {
        WARM_SINE,
        SOFT_SQUARE,
        CRISP_PUNCH,
        GLASSY,
    }

    private val waveformCache = ConcurrentHashMap<Pair<SoundType, SoundProfile>, ShortArray>()

    private val executor =
        Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "FrontendSoundEngine").apply {
                isDaemon = true
            }
        }

    // Accessed exclusively on the single-threaded executor
    private var audioTrack: AudioTrack? = null
    private var trackBufferShorts: Int = 0
    private var silenceBuffer: ShortArray = ShortArray(0)
    private var releaseFuture: ScheduledFuture<*>? = null

    @Volatile
    private var cachedPrefKey: String? = null

    @Volatile
    private var lastNavigateUptimeMs: Long = 0L

    fun playNavigate(
        context: Context,
        theme: FrontendTheme,
    ) {
        val now = SystemClock.uptimeMillis()
        if (now - lastNavigateUptimeMs < NAVIGATE_DEBOUNCE_MS) return
        lastNavigateUptimeMs = now
        play(context, theme, SoundType.NAVIGATE)
    }

    fun playConfirm(
        context: Context,
        theme: FrontendTheme,
    ) {
        play(context, theme, SoundType.CONFIRM)
    }

    fun playSwitchSystem(
        context: Context,
        theme: FrontendTheme,
    ) {
        play(context, theme, SoundType.SWITCH_SYSTEM)
    }

    fun playFavorite(
        context: Context,
        theme: FrontendTheme,
    ) {
        play(context, theme, SoundType.FAVORITE)
    }

    fun playBack(
        context: Context,
        theme: FrontendTheme,
    ) {
        play(context, theme, SoundType.BACK)
    }

    private fun play(
        context: Context,
        theme: FrontendTheme,
        soundType: SoundType,
    ) {
        runCatching {
            val appContext = context.applicationContext ?: context
            val profile = theme.toSoundProfile()
            executor.execute {
                runCatching {
                    if (!isEnabled(appContext)) return@runCatching
                    val samples = getOrSynthesize(soundType, profile)
                    playSamplesOnAudioThread(samples)
                }
            }
        }
    }

    private fun isEnabled(context: Context): Boolean {
        val key =
            cachedPrefKey ?: context.getString(R.string.pref_key_frontend_sounds).also {
                cachedPrefKey = it
            }
        return SharedPreferencesHelper.getSharedPreferences(context).getBoolean(key, true)
    }

    private fun FrontendTheme.toSoundProfile(): SoundProfile =
        when (this) {
            FrontendTheme.LOCADORA,
            FrontendTheme.CONSOLE,
            FrontendTheme.NETFLIX,
            FrontendTheme.DEFAULT,
            -> SoundProfile.WARM_SOFT

            FrontendTheme.RETRO_80_90,
            FrontendTheme.TV,
            -> SoundProfile.RETRO_CHIP

            FrontendTheme.THRASH_METAL,
            FrontendTheme.ROCKSTAR,
            -> SoundProfile.HEAVY_CRISP

            FrontendTheme.THREE_D -> SoundProfile.HITECH_CLEAN
        }

    private fun playSamplesOnAudioThread(samples: ShortArray) {
        releaseFuture?.cancel(false)
        releaseFuture = null

        val track = ensureAudioTrack() ?: return
        if (track.state != AudioTrack.STATE_INITIALIZED) {
            releaseTrackOnAudioThread()
            return
        }

        if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
            track.pause()
            track.flush()
        }

        track.play()
        track.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)

        // Pad with silence up to buffer size so AudioFlinger starts immediately without stalling
        val padCount = trackBufferShorts - samples.size
        if (padCount > 0) {
            if (silenceBuffer.size < padCount) {
                silenceBuffer = ShortArray(padCount)
            }
            track.write(silenceBuffer, 0, padCount, AudioTrack.WRITE_NON_BLOCKING)
        }

        releaseFuture =
            executor.schedule(
                { runCatching { releaseTrackOnAudioThread() } },
                IDLE_RELEASE_MS,
                TimeUnit.MILLISECONDS,
            )
    }

    private fun ensureAudioTrack(): AudioTrack? {
        audioTrack?.let { existing ->
            if (existing.state == AudioTrack.STATE_INITIALIZED) {
                return existing
            }
            releaseTrackOnAudioThread()
        }

        val minBufBytes =
            AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
        if (minBufBytes <= 0) return null

        val bufferBytes = maxOf(minBufBytes, (SAMPLE_RATE / 10) * 2)
        val track =
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(SAMPLE_RATE)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setBufferSizeInBytes(bufferBytes)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

        if (track.state != AudioTrack.STATE_INITIALIZED) {
            runCatching { track.release() }
            return null
        }

        audioTrack = track
        trackBufferShorts = bufferBytes / 2
        return track
    }

    private fun releaseTrackOnAudioThread() {
        releaseFuture?.cancel(false)
        releaseFuture = null
        val track = audioTrack ?: return
        audioTrack = null
        trackBufferShorts = 0
        runCatching {
            if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                track.pause()
                track.flush()
            }
            track.stop()
        }
        runCatching { track.release() }
    }

    private fun getOrSynthesize(
        soundType: SoundType,
        profile: SoundProfile,
    ): ShortArray {
        val key = soundType to profile
        return waveformCache[key] ?: synthesize(soundType, profile).also {
            waveformCache[key] = it
        }
    }

    private fun synthesize(
        soundType: SoundType,
        profile: SoundProfile,
    ): ShortArray =
        when (soundType) {
            SoundType.NAVIGATE ->
                when (profile) {
                    SoundProfile.WARM_SOFT ->
                        synthesizeGlide(
                            durationMs = 28,
                            startFreq = 520.0,
                            endFreq = 680.0,
                            waveform = Waveform.WARM_SINE,
                            amplitude = 0.24,
                            decayRate = 5.2,
                        )
                    SoundProfile.RETRO_CHIP ->
                        synthesizeGlide(
                            durationMs = 28,
                            startFreq = 740.0,
                            endFreq = 980.0,
                            waveform = Waveform.SOFT_SQUARE,
                            amplitude = 0.20,
                            decayRate = 4.8,
                        )
                    SoundProfile.HEAVY_CRISP ->
                        synthesizeGlide(
                            durationMs = 28,
                            startFreq = 260.0,
                            endFreq = 175.0,
                            waveform = Waveform.CRISP_PUNCH,
                            amplitude = 0.26,
                            decayRate = 5.8,
                        )
                    SoundProfile.HITECH_CLEAN ->
                        synthesizeGlide(
                            durationMs = 28,
                            startFreq = 880.0,
                            endFreq = 1180.0,
                            waveform = Waveform.GLASSY,
                            amplitude = 0.22,
                            decayRate = 5.0,
                        )
                }

            SoundType.CONFIRM ->
                when (profile) {
                    SoundProfile.WARM_SOFT ->
                        synthesizeSequence(
                            notes = listOf(Note(523.25, 34), Note(783.99, 41)),
                            waveform = Waveform.WARM_SINE,
                            amplitude = 0.26,
                        )
                    SoundProfile.RETRO_CHIP ->
                        synthesizeSequence(
                            notes = listOf(Note(659.25, 34), Note(987.77, 41)),
                            waveform = Waveform.SOFT_SQUARE,
                            amplitude = 0.20,
                        )
                    SoundProfile.HEAVY_CRISP ->
                        synthesizeSequence(
                            notes = listOf(Note(293.66, 34), Note(440.00, 41)),
                            waveform = Waveform.CRISP_PUNCH,
                            amplitude = 0.27,
                        )
                    SoundProfile.HITECH_CLEAN ->
                        synthesizeSequence(
                            notes = listOf(Note(783.99, 34), Note(1174.66, 41)),
                            waveform = Waveform.GLASSY,
                            amplitude = 0.23,
                        )
                }

            SoundType.SWITCH_SYSTEM ->
                when (profile) {
                    SoundProfile.WARM_SOFT ->
                        synthesizeGlide(
                            durationMs = 50,
                            startFreq = 360.0,
                            endFreq = 640.0,
                            waveform = Waveform.WARM_SINE,
                            amplitude = 0.24,
                            decayRate = 3.8,
                        )
                    SoundProfile.RETRO_CHIP ->
                        synthesizeGlide(
                            durationMs = 50,
                            startFreq = 440.0,
                            endFreq = 880.0,
                            waveform = Waveform.SOFT_SQUARE,
                            amplitude = 0.19,
                            decayRate = 3.6,
                        )
                    SoundProfile.HEAVY_CRISP ->
                        synthesizeGlide(
                            durationMs = 50,
                            startFreq = 220.0,
                            endFreq = 420.0,
                            waveform = Waveform.CRISP_PUNCH,
                            amplitude = 0.26,
                            decayRate = 4.2,
                        )
                    SoundProfile.HITECH_CLEAN ->
                        synthesizeGlide(
                            durationMs = 50,
                            startFreq = 600.0,
                            endFreq = 1100.0,
                            waveform = Waveform.GLASSY,
                            amplitude = 0.22,
                            decayRate = 3.8,
                        )
                }

            SoundType.FAVORITE ->
                when (profile) {
                    SoundProfile.WARM_SOFT ->
                        synthesizeSequence(
                            notes = listOf(Note(523.25, 25), Note(659.25, 25), Note(1046.50, 30)),
                            waveform = Waveform.WARM_SINE,
                            amplitude = 0.26,
                        )
                    SoundProfile.RETRO_CHIP ->
                        synthesizeSequence(
                            notes = listOf(Note(783.99, 25), Note(987.77, 25), Note(1174.66, 30)),
                            waveform = Waveform.SOFT_SQUARE,
                            amplitude = 0.20,
                        )
                    SoundProfile.HEAVY_CRISP ->
                        synthesizeSequence(
                            notes = listOf(Note(329.63, 25), Note(493.88, 25), Note(659.25, 30)),
                            waveform = Waveform.CRISP_PUNCH,
                            amplitude = 0.26,
                        )
                    SoundProfile.HITECH_CLEAN ->
                        synthesizeSequence(
                            notes = listOf(Note(880.00, 25), Note(1108.73, 25), Note(1318.51, 30)),
                            waveform = Waveform.GLASSY,
                            amplitude = 0.23,
                        )
                }

            SoundType.BACK ->
                when (profile) {
                    SoundProfile.WARM_SOFT ->
                        synthesizeGlide(
                            durationMs = 55,
                            startFreq = 520.0,
                            endFreq = 330.0,
                            waveform = Waveform.WARM_SINE,
                            amplitude = 0.23,
                            decayRate = 4.0,
                        )
                    SoundProfile.RETRO_CHIP ->
                        synthesizeGlide(
                            durationMs = 55,
                            startFreq = 660.0,
                            endFreq = 370.0,
                            waveform = Waveform.SOFT_SQUARE,
                            amplitude = 0.19,
                            decayRate = 4.0,
                        )
                    SoundProfile.HEAVY_CRISP ->
                        synthesizeGlide(
                            durationMs = 55,
                            startFreq = 340.0,
                            endFreq = 185.0,
                            waveform = Waveform.CRISP_PUNCH,
                            amplitude = 0.25,
                            decayRate = 4.5,
                        )
                    SoundProfile.HITECH_CLEAN ->
                        synthesizeGlide(
                            durationMs = 55,
                            startFreq = 820.0,
                            endFreq = 510.0,
                            waveform = Waveform.GLASSY,
                            amplitude = 0.21,
                            decayRate = 4.0,
                        )
                }
        }

    private data class Note(
        val freqHz: Double,
        val durationMs: Int,
    )

    private fun synthesizeGlide(
        durationMs: Int,
        startFreq: Double,
        endFreq: Double,
        waveform: Waveform,
        amplitude: Double,
        decayRate: Double,
    ): ShortArray {
        val totalSamples = (SAMPLE_RATE * durationMs) / 1000
        val out = ShortArray(totalSamples)
        val attackSamples = min(totalSamples / 5, (SAMPLE_RATE * 2) / 1000).coerceAtLeast(1)
        val releaseSamples = min(totalSamples / 4, (SAMPLE_RATE * 4) / 1000).coerceAtLeast(1)
        var phase = 0.0

        for (i in 0 until totalSamples) {
            val progress = i.toDouble() / totalSamples.toDouble()
            val freq = startFreq + (endFreq - startFreq) * progress
            phase += (2.0 * PI * freq) / SAMPLE_RATE

            val attackEnv = if (i < attackSamples) i.toDouble() / attackSamples else 1.0
            val decayEnv = exp(-decayRate * progress)
            val tailEnv =
                if (i >= totalSamples - releaseSamples) {
                    (totalSamples - i).toDouble() / releaseSamples
                } else {
                    1.0
                }
            val env = attackEnv * decayEnv * tailEnv
            val sample = sampleWaveform(waveform, phase) * env * amplitude
            out[i] = (sample * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return out
    }

    private fun synthesizeSequence(
        notes: List<Note>,
        waveform: Waveform,
        amplitude: Double,
    ): ShortArray {
        val noteBuffers =
            notes.map { note ->
                synthesizeGlide(
                    durationMs = note.durationMs,
                    startFreq = note.freqHz,
                    endFreq = note.freqHz * 1.015,
                    waveform = waveform,
                    amplitude = amplitude,
                    decayRate = 3.2,
                )
            }
        val totalSize = noteBuffers.sumOf { it.size }
        val result = ShortArray(totalSize)
        var offset = 0
        for (buf in noteBuffers) {
            buf.copyInto(result, destinationOffset = offset)
            offset += buf.size
        }
        return result
    }

    private fun sampleWaveform(
        waveform: Waveform,
        phase: Double,
    ): Double =
        when (waveform) {
            Waveform.WARM_SINE ->
                0.82 * sin(phase) + 0.18 * sin(2.0 * phase)

            Waveform.SOFT_SQUARE ->
                0.70 * sin(phase) + 0.22 * sin(3.0 * phase) + 0.08 * sin(5.0 * phase)

            Waveform.CRISP_PUNCH ->
                0.65 * sin(phase) + 0.25 * sin(2.0 * phase) + 0.10 * sin(3.5 * phase)

            Waveform.GLASSY ->
                0.72 * sin(phase) + 0.20 * sin(2.0 * phase) + 0.08 * sin(4.0 * phase)
        }
}
