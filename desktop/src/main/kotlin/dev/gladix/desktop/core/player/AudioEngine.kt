package dev.gladix.desktop.core.player

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.InputStream
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.FloatControl
import javax.sound.sampled.SourceDataLine
import kotlin.math.log10
import kotlin.math.min

/**
 * Plays a decrypted audio [InputStream] through the system mixer.
 *
 * Formats: whatever javax.sound can decode on the classpath - mp3spi (MP3) and
 * jflac (FLAC) are registered as SPI providers, WAV is built in.
 *
 * Seeking reopens the stream at byte 0 and decodes forward, which is correct
 * for both CBR MP3 and FLAC without knowing bitrates or frame boundaries.
 */
class AudioEngine(private val scope: CoroutineScope) {

    var onEnded: (() -> Unit)? = null
    var onError: ((Throwable) -> Unit)? = null
    var onPosition: ((Long) -> Unit)? = null
    var onPlayingChanged: ((Boolean) -> Unit)? = null

    /** Called once per opened stream with the decoded format and its container. */
    var onFormat: ((StreamQuality) -> Unit)? = null

    private var job: Job? = null
    private var session = 0

    @Volatile private var line: SourceDataLine? = null
    @Volatile private var paused = false
    @Volatile private var volume = 1f
    @Volatile private var usingGain = false

    private var opener: (suspend () -> InputStream)? = null

    val isPaused: Boolean get() = paused

    fun setVolume(value: Float) {
        volume = value.coerceIn(0f, 1f)
        applyGain()
    }

    /** Starts (or restarts) playback of [opener] at [startMs]. */
    fun play(opener: suspend () -> InputStream, startMs: Long = 0L) {
        this.opener = opener
        start(startMs)
    }

    /** Seeks inside the current track, keeping the paused/playing state. */
    fun seekTo(ms: Long) {
        if (opener == null || job == null) return
        start(ms)
    }

    fun pause() {
        if (job == null) return
        paused = true
        runCatching { line?.stop() }
        onPlayingChanged?.invoke(false)
    }

    fun resume() {
        if (job == null) return
        paused = false
        runCatching { line?.start() }
        onPlayingChanged?.invoke(true)
    }

    fun stop() {
        session++
        job?.cancel()
        job = null
        closeLine()
        onPlayingChanged?.invoke(false)
    }

    private fun start(startMs: Long) {
        val op = opener ?: return
        session++
        val mySession = session
        job?.cancel()
        closeLine()
        paused = false
        job = scope.launch(Dispatchers.IO) {
            var ended = false
            try {
                val stream = op()
                stream.use {
                    ended = decode(it, startMs, mySession)
                }
            } catch (t: Throwable) {
                if (isActive && mySession == session) onError?.invoke(t)
            } finally {
                if (mySession == session) {
                    closeLine()
                    if (ended) {
                        onPosition?.invoke(0L)
                        onEnded?.invoke()
                    }
                }
            }
        }
    }

    private fun closeLine() {
        val l = line ?: return
        line = null
        runCatching { l.stop() }
        runCatching { l.flush() }
        runCatching { l.close() }
    }

    /** Returns true when the stream played to its end. */
    private suspend fun decode(input: InputStream, startMs: Long, mySession: Int): Boolean {
        val decoded = Decoders.open(input)
        val audio = decoded.stream
        val pcm = audio.format
        onFormat?.invoke(
            StreamQuality(
                container = decoded.container,
                sampleRate = pcm.sampleRate.toInt(),
                channels = pcm.channels,
                bitsPerSample = pcm.sampleSizeInBits
            )
        )

        val rate = pcm.sampleRate.toInt().coerceAtLeast(1)
        val frameSize = pcm.frameSize.coerceAtLeast(1)
        val targetFrames = startMs.coerceAtLeast(0) * rate / 1000L

        val info = DataLine.Info(SourceDataLine::class.java, pcm)
        if (!AudioSystem.isLineSupported(info)) {
            onError?.invoke(IllegalStateException("Audio output not supported: $pcm"))
            return false
        }
        val dataLine = AudioSystem.getLine(info) as SourceDataLine
        // Open line with a 256 KB buffer (~1.5s of 44.1kHz 16-bit stereo) to absorb jitter
        dataLine.open(pcm, 256 * 1024)
        line = dataLine
        usingGain = runCatching {
            dataLine.getControl(FloatControl.Type.MASTER_GAIN) != null
        }.getOrDefault(false)
        applyGain()
        dataLine.start()
        if (!paused) onPlayingChanged?.invoke(true)

        val buffer = ByteArray(16 * 1024)
        var frames: Long = 0
        var lastReport = 0L

        // Decode-and-drop forward to the requested position.
        var toSkip = targetFrames * frameSize
        while (toSkip > 0 && mySession == session) {
            val want = min(buffer.size.toLong(), toSkip).toInt()
            val read = audio.read(buffer, 0, want)
            if (read < 0) break
            toSkip -= read
            frames += read / frameSize
        }
        if (startMs > 0) onPosition?.invoke(frames * 1000L / rate)

        while (mySession == session) {
            if (paused) {
                delay(50)
                continue
            }
            val read = audio.read(buffer, 0, buffer.size)
            if (read < 0) break
            if (read == 0) continue
            if (usingGain) {
                dataLine.write(buffer, 0, read)
            } else {
                val scaled = applySoftwareVolume(buffer, read)
                dataLine.write(scaled, 0, read)
            }
            frames += read / frameSize

            val now = System.currentTimeMillis()
            if (now - lastReport >= 150) {
                lastReport = now
                onPosition?.invoke(frames * 1000L / rate)
            }
        }

        if (mySession != session) return false
        runCatching { dataLine.drain() }
        return true
    }

    private fun applyGain() {
        if (!usingGain) return
        val control = runCatching {
            line?.getControl(FloatControl.Type.MASTER_GAIN) as? FloatControl
        }.getOrNull() ?: return
        runCatching {
            val db = if (volume <= 0.0001f) control.minimum
            else (20.0 * log10(volume.toDouble())).toFloat()
            control.value = db.coerceIn(control.minimum, control.maximum)
        }
    }

    private fun applySoftwareVolume(buffer: ByteArray, length: Int): ByteArray {
        if (volume >= 0.999f) return buffer
        var i = 0
        while (i + 1 < length) {
            val sample = ((buffer[i + 1].toInt() shl 8) or (buffer[i].toInt() and 0xFF)).toShort()
            val scaled = (sample * volume).toInt().coerceIn(-32768, 32767)
            buffer[i] = (scaled and 0xFF).toByte()
            buffer[i + 1] = ((scaled shr 8) and 0xFF).toByte()
            i += 2
        }
        return buffer
    }
}