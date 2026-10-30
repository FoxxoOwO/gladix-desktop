package dev.gladix.desktop.core.platform

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.sun.jna.WString
import dev.brahmkshatriya.echo.common.models.ImageHolder
import dev.brahmkshatriya.echo.common.models.Track
import dev.gladix.desktop.core.AppPaths
import dev.gladix.desktop.core.Http
import dev.gladix.desktop.core.Images
import dev.gladix.desktop.core.PrefsSettings
import dev.gladix.desktop.core.player.RepeatMode
import dev.gladix.desktop.ui.text.UiStrings
import io.github.selemba1000.JMTC
import io.github.selemba1000.JMTCEnabledButtons
import io.github.selemba1000.JMTCMusicProperties
import io.github.selemba1000.JMTCParameters
import io.github.selemba1000.JMTCPlayingState
import io.github.selemba1000.JMTCSettings
import io.github.selemba1000.JMTCTimelineProperties
import io.github.selemba1000.JMTCCallbacks
import io.github.selemba1000.JMTCMediaType
import io.github.selemba1000.windows.SMTCAdapter
import java.io.File
import java.util.concurrent.Executors

/**
 * Publishes the current playback to the Windows System Media Transport Controls
 * (SMTC).
 *
 * This is what makes Gladix show up in the Windows media flyout / quick
 * settings, on the lock screen and behind the hardware media keys: Windows
 * renders the widget from the SMTC session metadata and routes
 * Play/Pause/Next/Previous to [JMTCCallbacks] instead of a global keyboard hook.
 *
 * All native calls are funnelled through one daemon thread because the JNA
 * bridge is not thread safe, and because the underlying WinRT apartment is
 * created on that thread by `SMTCAdapter.init()`.
 */
class WindowsMediaSession(
    private val settings: PrefsSettings,
    private val onPlay: () -> Unit,
    private val onPause: () -> Unit,
    private val onStop: () -> Unit,
    private val onNext: () -> Unit,
    private val onPrevious: () -> Unit,
    private val onSeek: (Long) -> Unit,
    private val onShuffle: (Boolean) -> Unit,
    private val onLoop: (RepeatMode) -> Unit
) : SystemMediaSink {

    /** Coarse state of the bridge; the text itself follows the UI language. */
    private enum class Phase { Starting, Active, ActiveNoArt, Disabled, Unavailable, Failed }

    private var phase by mutableStateOf(Phase.Starting)
    private var failure by mutableStateOf<String?>(null)

    /** Human readable state, surfaced in the settings screen. */
    val status: String
        get() = when (phase) {
            Phase.Starting -> UiStrings.s.mediaStarting
            Phase.Active -> UiStrings.s.mediaActive
            Phase.ActiveNoArt -> UiStrings.s.mediaActiveNoArt
            Phase.Disabled -> UiStrings.s.mediaDisabled
            Phase.Unavailable -> UiStrings.s.mediaUnavailable
            Phase.Failed -> UiStrings.s.error(failure)
        }

    /** True once the native bridge answered, i.e. media keys reach the app. */
    var available by mutableStateOf(false)
        private set

    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "gladix-smtc").apply { isDaemon = true }
    }

    @Volatile
    private var enabled: Boolean =
        settings.getString(PrefsSettings.KEY_MEDIA_SESSION)?.toBooleanStrictOrNull() ?: true

    private var jmtc: JMTC? = null
    private var adapter: SMTCAdapter? = null

    private var artFile: File? = null
    private var currentTrackId: String? = null
    private var currentTrack: Track? = null
    private var currentPlaying = false
    private var currentShuffle = false
    private var currentRepeat = RepeatMode.ALL
    private var lastPosition = -1L
    private var lastDuration = -1L

    fun start() {
        if (!enabled) {
            phase = Phase.Disabled
            return
        }
        executor.execute { setup() }
    }

    fun setEnabled(value: Boolean) {
        enabled = value
        settings.putString(PrefsSettings.KEY_MEDIA_SESSION, value.toString())
        if (value) {
            executor.execute { setup() }
        } else {
            executor.execute { teardown() }
        }
    }

    // -------------------------------------------------------- SystemMediaSink

    override fun onTrackChanged(track: Track?, isPlaying: Boolean) {
        currentPlaying = isPlaying
        if (track == null) {
            currentTrack = null
            currentTrackId = null
            executor.execute { publishEmpty() }
            return
        }
        if (track.id == currentTrackId) {
            currentTrack = track
            onPlayingChanged(isPlaying)
            return
        }
        currentTrack = track
        currentTrackId = track.id
        lastPosition = -1L
        lastDuration = track.duration ?: 0L
        val art = coverFile(track)
        executor.execute { publishTrack(track, art, isPlaying) }
    }

    override fun onPlayingChanged(isPlaying: Boolean) {
        if (currentPlaying == isPlaying) return
        currentPlaying = isPlaying
        executor.execute {
            val control = jmtc ?: return@execute
            runCatching {
                control.playingState =
                    if (isPlaying) JMTCPlayingState.PLAYING else JMTCPlayingState.PAUSED
            }.onFailure { fail(it) }
        }
    }

    override fun onPositionChanged(positionMs: Long, durationMs: Long) {
        if (positionMs == lastPosition && durationMs == lastDuration) return
        val durationChanged = durationMs != lastDuration
        lastPosition = positionMs
        lastDuration = durationMs
        executor.execute {
            val control = jmtc ?: return@execute
            runCatching {
                if (durationChanged && durationMs > 0) {
                    control.setTimelineProperties(
                        JMTCTimelineProperties(0L, durationMs, 0L, durationMs)
                    )
                }
                control.setPosition(positionMs)
            }.onFailure { fail(it) }
        }
    }

    override fun onModesChanged(shuffle: Boolean, repeat: RepeatMode) {
        if (currentShuffle == shuffle && currentRepeat == repeat) return
        currentShuffle = shuffle
        currentRepeat = repeat
        executor.execute { publishModes() }
    }

    override fun close() {
        executor.execute { teardown() }
        executor.shutdown()
    }

    // -------------------------------------------------------------- internals

    private fun setup() {
        if (!enabled) return
        runCatching {
            if (jmtc == null) {
                val control = JMTC.getInstance(JMTCSettings("Gladix", ""))
                    ?: error(UiStrings.s.smtcUnavailable)
                jmtc = control
                adapter = nativeAdapter(control)
                control.enabledButtons = JMTCEnabledButtons(
                    /* isPlayEnabled = */ true,
                    /* isPauseEnabled = */ true,
                    /* isStopEnabled = */ false,
                    /* isNextEnabled = */ true,
                    /* isPreviousEnabled = */ true
                )
                control.setCallbacks(callbacks())
                control.mediaType = JMTCMediaType.Music
                control.playingState = JMTCPlayingState.CLOSED
                control.enabled = true
            }
            available = true
            phase = if (adapter != null) {
                Phase.Active
            } else {
                Phase.ActiveNoArt
            }
            // Restore whatever is already loaded. The track usually arrives
            // before the session exists, so it has to be published again here.
            currentTrack?.let { publishTrack(it, coverFile(it), currentPlaying) }
            publishModes()
        }.onFailure { fail(it) }
    }

    private fun teardown() {
        runCatching {
            jmtc?.let {
                runCatching { it.enabled = false }
                runCatching { it.resetDisplay() }
                runCatching { it.playingState = JMTCPlayingState.CLOSED }
            }
        }
        jmtc = null
        adapter = null
        available = false
        phase = if (enabled) Phase.Unavailable else Phase.Disabled
    }

    private fun publishTrack(track: Track, art: File?, isPlaying: Boolean) {
        val control = jmtc ?: return
        runCatching {
            control.mediaType = JMTCMediaType.Music
            control.playingState = JMTCPlayingState.CHANGING
            val album = track.album
            // Art is intentionally left out of the music properties: the JNA
            // wrapper hands StorageFile a file: URI, which the native side
            // rejects (and then waits for it forever). We set the thumbnail
            // through the raw adapter with a real path instead.
            control.mediaProperties = JMTCMusicProperties(
                track.title ?: "",
                track.artists.joinToString(", ") { it.name },
                album?.title ?: "",
                album?.artists?.joinToString(", ") { it.name } ?: "",
                track.genres.toTypedArray(),
                album?.trackCount?.toInt() ?: -1,
                track.albumOrderNumber?.toInt() ?: -1,
                null
            )
            setThumbnailBlocking(art)
            val duration = (track.duration ?: 0L).coerceAtLeast(0L)
            control.setTimelineProperties(JMTCTimelineProperties(0L, duration, 0L, duration))
            control.setPosition(0L)
            control.updateDisplay()
            control.playingState =
                if (isPlaying) JMTCPlayingState.PLAYING else JMTCPlayingState.PAUSED
            lastDuration = duration
            lastPosition = 0L
        }.onFailure { fail(it) }
    }

    private fun publishEmpty() {
        val control = jmtc ?: return
        runCatching {
            control.playingState = JMTCPlayingState.CLOSED
            control.resetDisplay()
        }.onFailure { fail(it) }
        artFile?.delete()
        artFile = null
    }

    private fun publishModes() {
        val control = jmtc ?: return
        runCatching {
            control.parameters = JMTCParameters(
                when (currentRepeat) {
                    RepeatMode.OFF -> JMTCParameters.LoopStatus.None
                    RepeatMode.ONE -> JMTCParameters.LoopStatus.Track
                    RepeatMode.ALL -> JMTCParameters.LoopStatus.Playlist
                },
                /* volume = */ null,
                /* rate = */ 1.0,
                /* shuffle = */ currentShuffle
            )
        }.onFailure { fail(it) }
    }

    /**
     * WinRT loads the thumbnail asynchronously from a real file path; push it
     * through the raw adapter and wait (bounded) so the following
     * `updateDisplay()` actually carries the artwork.
     */
    private fun setThumbnailBlocking(art: File?) {
        val native = adapter ?: return
        val file = art?.takeIf { it.isFile } ?: return
        runCatching {
            native.setThumbnail(WString(file.absolutePath))
            val deadline = System.currentTimeMillis() + 2000
            while (System.currentTimeMillis() < deadline) {
                val loaded = runCatching { native.thumbnailLoaded() }.getOrNull()
                if (loaded != false) return
                Thread.sleep(10)
            }
        }
    }

    private fun callbacks(): JMTCCallbacks {
        val bridge = JMTCCallbacks()
        bridge.onPlay = { runCatching { onPlay() } }
        bridge.onPause = { runCatching { onPause() } }
        bridge.onStop = { runCatching { onStop() } }
        bridge.onNext = { runCatching { onNext() } }
        bridge.onPrevious = { runCatching { onPrevious() } }
        bridge.onSeek = { position -> runCatching { onSeek(position ?: 0L) } }
        bridge.onShuffle = { value -> runCatching { onShuffle(value == true) } }
        bridge.onLoop = { loop ->
            runCatching {
                when (loop) {
                    JMTCParameters.LoopStatus.Track -> onLoop(RepeatMode.ONE)
                    JMTCParameters.LoopStatus.Playlist -> onLoop(RepeatMode.ALL)
                    else -> onLoop(RepeatMode.OFF)
                }
            }
        }
        return bridge
    }

    /**
     * The wrapper keeps the native library in a protected field; the guarded
     * thumbnail path needs it directly.
     */
    private fun nativeAdapter(control: JMTC): SMTCAdapter? = runCatching {
        val field = control.javaClass.getDeclaredField("lib")
        field.isAccessible = true
        field.get(control) as? SMTCAdapter
    }.getOrNull()

    /** Downloads the cover once so the flyout has a real image to show. */
    private fun coverFile(track: Track): File? = runCatching {
        val url = Images.urlOf(track.cover) ?: return null
        val headers = Images.headersOf(track.cover)
        val stem = "smtc-${track.id.hashCode()}"
        val existing = IMAGE_EXTENSIONS
            .map { File(AppPaths.images, "$stem.$it") }
            .firstOrNull { it.isFile && it.length() > 0 }
        val file = existing ?: run {
            val request = okhttp3.Request.Builder().url(url).apply {
                headers.forEach { (key, value) -> header(key, value) }
            }.build()
            Http.client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val bytes = response.body?.bytes() ?: return null
                val extension = when {
                    bytes.size > 8 && bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() -> "png"
                    bytes.size > 12 && String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF" -> "webp"
                    else -> "jpg"
                }
                File(AppPaths.images, "$stem.$extension").apply {
                    outputStream().use { out -> out.write(bytes) }
                }
            }
        }
        artFile?.takeIf { it != file }?.delete()
        artFile = file
        file
    }.onFailure { fail(it) }.getOrNull()

    private fun fail(t: Throwable) {
        available = false
        failure = t.message ?: t::class.simpleName
        phase = Phase.Failed
    }
}

/** Extensions tried when a cached cover for a track is looked up. */
private val IMAGE_EXTENSIONS = listOf("jpg", "png", "webp")