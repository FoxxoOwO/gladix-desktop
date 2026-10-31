package dev.gladix.desktop.core.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.brahmkshatriya.echo.common.models.EchoMediaItem
import dev.brahmkshatriya.echo.common.models.Track
import dev.gladix.desktop.core.AppPaths
import dev.gladix.desktop.core.DeezerClient
import dev.gladix.desktop.core.DownloadManager
import dev.gladix.desktop.core.HistoryStore
import dev.gladix.desktop.core.PrefsSettings
import dev.gladix.desktop.core.readJsonFile
import dev.gladix.desktop.core.writeJsonFile
import dev.gladix.desktop.core.platform.SystemMediaSink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.io.File
import java.io.InputStream

enum class RepeatMode { OFF, ALL, ONE }

@Serializable
data class SavedQueue(
    val tracks: List<Track> = emptyList(),
    val index: Int = -1,
    val shuffleOrder: List<Int> = emptyList()
)

/**
 * Owns the play queue and drives [AudioEngine].
 *
 * Downloads are preferred over streaming, then the extension decrypts on the
 * fly. Deezer scrobbling and local history are reported for every track that
 * plays long enough to count.
 */
class PlayerController(
    private val client: DeezerClient,
    private val downloads: DownloadManager,
    private val history: HistoryStore,
    private val settings: PrefsSettings,
    private val scope: CoroutineScope
) {

    private val engine = AudioEngine(scope)

    /** Windows media controls; attached by the app context once it is built. */
    var systemMedia: SystemMediaSink = SystemMediaSink.None
        set(value) {
            field = value
            publishSystemTrack()
        }

    var queue by mutableStateOf<List<Track>>(emptyList())
        private set
    var index by mutableStateOf(-1)
        private set
    var context by mutableStateOf<EchoMediaItem?>(null)
        private set

    var isPlaying by mutableStateOf(false)
        private set
    var isBuffering by mutableStateOf(false)
        private set
    var positionMs by mutableStateOf(0L)
        private set
    var durationMs by mutableStateOf(0L)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var volume by mutableStateOf(0.85f)
        private set
    var repeatMode by mutableStateOf(RepeatMode.ALL)
        private set
    var shuffle by mutableStateOf(false)
        private set

    /** Format of the stream that is currently open, for the quality indicator. */
    var streamQuality by mutableStateOf<StreamQuality?>(null)
        private set

    var shuffleOrder by mutableStateOf<List<Int>>(emptyList())
        private set

    private var playJob: Job? = null
    private var scrobbleJob: Job? = null
    private var scrobbledId: String? = null
    private var lastSystemPosition = 0L

    val current: Track? get() = queue.getOrNull(index)

    init {
        engine.onPosition = { position ->
            positionMs = position
            if (position - lastSystemPosition >= 1000 || position < lastSystemPosition) {
                lastSystemPosition = position
                systemMedia.onPositionChanged(position, durationMs)
            }
        }
        engine.onPlayingChanged = {
            isPlaying = it
            systemMedia.onPlayingChanged(it)
        }
        engine.onEnded = { scope.launch { onTrackEnded() } }
        engine.onError = { t -> scope.launch { onEngineError(t) } }
        engine.onFormat = { quality -> streamQuality = quality }

        volume = settings.getString(PrefsSettings.KEY_VOLUME)?.toFloatOrNull() ?: 0.85f
        engine.setVolume(volume)
        repeatMode = when (settings.getString(PrefsSettings.KEY_REPEAT)) {
            "ONE" -> RepeatMode.ONE
            "OFF" -> RepeatMode.OFF
            else -> RepeatMode.ALL
        }
        shuffle = settings.getString(PrefsSettings.KEY_SHUFFLE)?.toBooleanStrictOrNull() ?: false
    }

    fun restore() {
        val saved = readJsonFile<SavedQueue?>(AppPaths.queueFile, null) ?: return
        if (saved.tracks.isEmpty()) return
        queue = saved.tracks
        shuffleOrder = saved.shuffleOrder
        index = saved.index.coerceIn(-1, saved.tracks.size - 1)
        durationMs = current?.duration ?: 0L
    }

    private fun persist() {
        writeJsonFile(
            AppPaths.queueFile,
            SavedQueue(queue, index, shuffleOrder)
        )
    }

    private fun publishSystemTrack() {
        systemMedia.onTrackChanged(current, isPlaying || isBuffering)
        systemMedia.onModesChanged(shuffle, repeatMode)
        systemMedia.onPositionChanged(positionMs, durationMs)
    }

    // ------------------------------------------------------------- controls

    fun playNow(track: Track, context: EchoMediaItem? = null) {
        val existing = queue.indexOfFirst { it.id == track.id }
        if (existing >= 0) {
            index = existing
            this.context = context ?: this.context
            startCurrent(0L)
            return
        }
        val insertAt = if (index < 0) queue.size else index + 1
        queue = queue.toMutableList().apply { add(insertAt, track) }
        index = insertAt
        this.context = context
        rebuildShuffleOrder()
        startCurrent(0L)
    }

    fun playList(tracks: List<Track>, startIndex: Int = 0, context: EchoMediaItem? = null) {
        if (tracks.isEmpty()) return
        queue = tracks
        index = startIndex.coerceIn(0, tracks.size - 1)
        this.context = context
        rebuildShuffleOrder()
        startCurrent(0L)
    }

    fun addNext(track: Track) {
        val insertAt = (index + 1).coerceIn(0, queue.size)
        queue = queue.toMutableList().apply { add(insertAt, track) }
        rebuildShuffleOrder()
        persist()
    }

    fun addToQueue(tracks: List<Track>) {
        queue = queue + tracks
        rebuildShuffleOrder()
        persist()
    }

    fun removeFromQueue(position: Int) {
        if (position !in queue.indices) return
        val wasCurrent = position == index
        val list = queue.toMutableList().apply { removeAt(position) }
        queue = list
        if (wasCurrent) {
            index = position.coerceAtMost(list.size - 1)
            if (index < 0) stop() else startCurrent(0L)
        } else if (position < index) {
            index -= 1
        }
        rebuildShuffleOrder()
        persist()
    }

    fun moveInQueue(from: Int, to: Int) {
        if (from !in queue.indices || to !in queue.indices || from == to) return
        val list = queue.toMutableList()
        val item = list.removeAt(from)
        list.add(to, item)
        val currentTrack = current
        queue = list
        index = currentTrack?.let { list.indexOfFirst { t -> t.id == it.id } } ?: -1
        rebuildShuffleOrder()
        persist()
    }

    fun clearQueue() {
        stop()
        queue = emptyList()
        index = -1
        shuffleOrder = emptyList()
        context = null
        persist()
    }

    fun togglePlayPause() {
        when {
            current == null -> return
            playJob == null -> startCurrent(positionMs)
            isPlaying -> engine.pause()
            else -> engine.resume()
        }
    }

    /** Explicit play, used by the Windows media keys (they never toggle). */
    fun resumeFromSystem() {
        if (current == null) return
        if (playJob == null) startCurrent(positionMs) else if (!isPlaying) engine.resume()
    }

    /** Explicit pause, used by the Windows media keys. */
    fun pauseFromSystem() {
        if (isPlaying) engine.pause()
    }

    fun next() {
        if (queue.isEmpty()) return
        val nextIndex = nextIndex()
        if (nextIndex == null) {
            stop()
            return
        }
        index = nextIndex
        startCurrent(0L)
    }

    fun previous() {
        if (queue.isEmpty()) return
        // Restart the track first, like every other player.
        if (positionMs > 3000) {
            seekTo(0L)
            return
        }
        val prevIndex = previousIndex()
        if (prevIndex == null) {
            seekTo(0L)
            return
        }
        index = prevIndex
        startCurrent(0L)
    }

    fun seekTo(ms: Long) {
        val dur = durationMs
        val target = if (dur > 0) ms.coerceIn(0L, dur) else ms.coerceAtLeast(0L)
        positionMs = target
        systemMedia.onPositionChanged(target, durationMs)
        if (current == null) return
        if (playJob == null) {
            startCurrent(target)
        } else {
            engine.seekTo(target)
            if (!isPlaying) {
                isBuffering = true
            }
        }
    }

    fun changeVolume(value: Float) {
        volume = value.coerceIn(0f, 1f)
        engine.setVolume(volume)
        settings.putString(PrefsSettings.KEY_VOLUME, volume.toString())
    }

    fun cycleRepeat() {
        repeatMode = when (repeatMode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        settings.putString(PrefsSettings.KEY_REPEAT, repeatMode.name)
        systemMedia.onModesChanged(shuffle, repeatMode)
    }

    fun toggleShuffle() {
        shuffle = !shuffle
        settings.putString(PrefsSettings.KEY_SHUFFLE, shuffle.toString())
        rebuildShuffleOrder()
        persist()
        systemMedia.onModesChanged(shuffle, repeatMode)
    }

    /** Explicit repeat setter, used by the Windows media flyout. */
    fun applyRepeatMode(mode: RepeatMode) {
        if (repeatMode == mode) return
        repeatMode = mode
        settings.putString(PrefsSettings.KEY_REPEAT, mode.name)
        systemMedia.onModesChanged(shuffle, repeatMode)
    }

    /** Explicit shuffle setter, used by the Windows media flyout. */
    fun applyShuffleMode(value: Boolean) {
        if (shuffle == value) return
        shuffle = value
        settings.putString(PrefsSettings.KEY_SHUFFLE, value.toString())
        rebuildShuffleOrder()
        persist()
        systemMedia.onModesChanged(shuffle, repeatMode)
    }

    fun stop() {
        playJob = null
        scrobbleJob?.cancel()
        engine.stop()
        positionMs = 0L
        isPlaying = false
        isBuffering = false
        streamQuality = null
        systemMedia.onPlayingChanged(false)
    }

    // -------------------------------------------------------------- internal

    private fun nextIndex(): Int? {
        if (repeatMode == RepeatMode.ONE) return index
        if (shuffle && shuffleOrder.isNotEmpty()) {
            val pos = shuffleOrder.indexOf(index)
            val nextPos = pos + 1
            if (nextPos < shuffleOrder.size) return shuffleOrder[nextPos]
            return if (repeatMode == RepeatMode.ALL) shuffleOrder.firstOrNull() else null
        }
        val next = index + 1
        if (next < queue.size) return next
        return if (repeatMode == RepeatMode.ALL) 0 else null
    }

    private fun previousIndex(): Int? {
        if (shuffle && shuffleOrder.isNotEmpty()) {
            val pos = shuffleOrder.indexOf(index)
            if (pos > 0) return shuffleOrder[pos - 1]
            return null
        }
        return if (index > 0) index - 1 else null
    }

    private fun rebuildShuffleOrder() {
        if (!shuffle) {
            shuffleOrder = emptyList()
            return
        }
        val positions = queue.indices.toMutableList()
        positions.shuffle()
        // Keep the current track first so shuffle does not jump on toggle.
        val currentPos = positions.indexOf(index)
        if (currentPos > 0) {
            positions.removeAt(currentPos)
            positions.add(0, index)
        }
        shuffleOrder = positions
    }

    private fun startCurrent(startAt: Long) {
        val track = current ?: return
        scrobbleJob?.cancel()
        scrobbledId = null
        playJob?.cancel()
        playJob = scope.launch {
            isBuffering = true
            error = null
            durationMs = track.duration ?: 0L
            positionMs = startAt
            // Stale until the decoder reports the new stream.
            streamQuality = null
            history.add(track)
            persist()

            val opener: suspend () -> InputStream = {
                val offline = downloads.entry(track.id)
                if (offline != null) {
                    File(offline.path).inputStream()
                } else {
                    client.openStream(track)
                }
            }

            try {
                engine.play(opener, startAt)
                isBuffering = false
                lastSystemPosition = startAt
                publishSystemTrack()
                scheduleScrobble(track)
            } catch (t: Throwable) {
                isBuffering = false
                error = t.message ?: t::class.simpleName ?: "Playback failed"
            }
        }
    }

    private fun scheduleScrobble(track: Track) {        scrobbleJob?.cancel()
        scrobbleJob = scope.launch {
            val delayMs = client.markPlayedAfterMs()
            delay(delayMs)
            if (current?.id == track.id && scrobbledId != track.id) {
                scrobbledId = track.id
                client.markPlayed(track, context, delayMs, track.duration)
            }
        }
    }

    private suspend fun onTrackEnded() {
        isBuffering = false
        if (repeatMode == RepeatMode.ONE) {
            startCurrent(0L)
            return
        }
        val nextIdx = if (shuffle && shuffleOrder.isNotEmpty()) {
            val pos = shuffleOrder.indexOf(index)
            val nextPos = pos + 1
            when {
                nextPos < shuffleOrder.size -> shuffleOrder[nextPos]
                repeatMode == RepeatMode.ALL -> shuffleOrder.firstOrNull()
                else -> null
            }
        } else {
            val next = index + 1
            when {
                next < queue.size -> next
                repeatMode == RepeatMode.ALL -> 0
                else -> null
            }
        }
        if (nextIdx == null) {
            playJob = null
            engine.stop()
            positionMs = 0L
            return
        }
        index = nextIdx
        startCurrent(0L)
    }

    private suspend fun onEngineError(t: Throwable) {
        isBuffering = false
        isPlaying = false
        error = t.message ?: t::class.simpleName ?: "Playback failed"
    }

    fun clearError() {
        error = null
    }
}