package dev.gladix.desktop.core.platform

import dev.brahmkshatriya.echo.common.models.Track
import dev.gladix.desktop.core.AppVersion
import dev.gladix.desktop.core.player.RepeatMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Discord Rich Presence over the local Discord IPC socket.
 *
 * Discord does not accept this over HTTP: the desktop client creates a named
 * pipe (`\\.\pipe\discord-ipc-N` on Windows, a unix socket elsewhere) and every
 * message is a binary frame — a 32 bit little endian opcode, a 32 bit little
 * endian length, then that many bytes of UTF-8 JSON.
 *
 * Opcodes used here:
 *  - `0` handshake, sent once with our application id;
 *  - `1` frame, carrying `SET_ACTIVITY`.
 *
 * Everything is best effort. Discord being closed, the pipe already taken, or a
 * rate limit are normal states and never surface in the player — the presence
 * simply does not update until the next retry.
 *
 * @param clientId supplies the Discord application id; reading it lazily keeps
 *   a changed id in Settings working without rebuilding the sink.
 */
class DiscordPresence(
    private val scope: CoroutineScope,
    private val clientId: () -> String
) : SystemMediaSink {

    /** What Discord is told, in the shape the presence needs. */
    private data class Activity(
        val title: String,
        val artist: String,
        val album: String?,
        val playing: Boolean,
        val durationMs: Long,
        /** Wall clock time the reported position belongs to. */
        val positionAnchor: Long,
        val positionMs: Long
    )

    private var job: Job? = null

    @Volatile
    private var enabled = false

    @Volatile
    private var activity: Activity? = null

    /** Signature of the last activity Discord acknowledged, for change detection. */
    @Volatile
    private var pushedSignature: String? = null

    private var lastPushAt = 0L

    @Volatile
    private var connected = false

    // ------------------------------------------------------------ lifecycle

    fun setEnabled(value: Boolean) {
        if (enabled == value) return
        enabled = value
        if (value) start() else stop()
    }

    private fun start() {
        if (job != null) return
        job = scope.launch(Dispatchers.IO) {
            var backoff = 5_000L
            while (isActive && enabled) {
                val stayed = runCatching { serve() }.getOrDefault(false)
                if (!enabled || !isActive) break
                // A short-lived connection usually means Discord is not running
                // yet; back off so a closed Discord costs nothing.
                if (stayed) {
                    backoff = 5_000L
                } else {
                    delay(backoff)
                    backoff = (backoff * 2).coerceAtMost(60_000L)
                }
            }
        }
    }

    private fun stop() {
        job?.cancel()
        job = null
        activity = null
        pushedSignature = null
        connected = false
    }

    /** True once the handshake went through, for the settings screen. */
    val isConnected: Boolean get() = connected

    // ------------------------------------------------------- SystemMediaSink

    override fun onTrackChanged(track: Track?, isPlaying: Boolean) {
        if (!enabled) return
        if (track == null) {
            activity = null
            return
        }
        activity = Activity(
            title = track.title,
            artist = track.artists.joinToString(", ") { it.name },
            album = track.album?.title,
            playing = isPlaying,
            durationMs = track.duration ?: 0L,
            positionAnchor = System.currentTimeMillis(),
            positionMs = 0L
        )
    }

    override fun onPlayingChanged(isPlaying: Boolean) {
        if (!enabled) return
        val current = activity ?: return
        activity = current.copy(
            playing = isPlaying,
            positionAnchor = System.currentTimeMillis()
        )
    }

    override fun onPositionChanged(positionMs: Long, durationMs: Long) {
        if (!enabled) return
        val current = activity ?: return
        activity = current.copy(
            positionMs = positionMs,
            durationMs = durationMs,
            positionAnchor = System.currentTimeMillis()
        )
    }

    override fun onModesChanged(shuffle: Boolean, repeat: RepeatMode) = Unit

    override fun close() = stop()

    // ---------------------------------------------------------------- wire

    /**
     * Connects to the first free Discord pipe and pushes activity changes until
     * the connection drops. Returns true when it stayed connected for a while.
     */
    private suspend fun serve(): Boolean {
        val id = clientId().trim()
        if (id.isEmpty() || id == "0") return false

        val channel = openPipe() ?: return false
        val startedAt = System.currentTimeMillis()
        try {
            write(channel, 0, buildJsonObject {
                put("v", 1)
                put("client_id", id)
            }.toString())
            connected = true
            pushedSignature = null

            while (enabled) {
                val current = activity
                if (current == null) {
                    if (pushedSignature != null) {
                        clearActivity(channel)
                    }
                } else {
                    val signature = signatureOf(current)
                    val now = System.currentTimeMillis()
                    // Discord rate limits SET_ACTIVITY to 5 updates per 20 s.
                    if (signature != pushedSignature && now - lastPushAt >= 4_000) {
                        pushActivity(channel, current)
                        pushedSignature = signature
                        lastPushAt = now
                    }
                }
                delay(2_000)
            }
            return System.currentTimeMillis() - startedAt > 10_000
        } catch (t: Throwable) {
            // A broken pipe is the normal "Discord closed" signal.
            return false
        } finally {
            connected = false
            runCatching { channel.close() }
        }
    }

    /** Changes that require a new SET_ACTIVITY; elapsed time is not one. */
    private fun signatureOf(a: Activity): String =
        "${a.title}|${a.artist}|${a.playing}|${a.durationMs}"

    private fun pushActivity(channel: RandomAccessFile, a: Activity) {
        // Discord renders the elapsed time itself from these timestamps, which
        // is why the position anchor matters and positions are not re-sent.
        val start = a.positionAnchor - a.positionMs
        val json = buildJsonObject {
            put("cmd", "SET_ACTIVITY")
            putJsonObject("args") {
                put("pid", ProcessHandle.current().pid())
                putJsonObject("activity") {
                    put("details", a.title.take(128))
                    put("state", a.artist.take(128).ifBlank { "Gladix" })
                    put("instance", false)
                    putJsonObject("assets") {
                        put("large_image", "gladix")
                        put("large_text", (a.album ?: AppVersion.NAME).take(128))
                    }
                    if (a.durationMs > 0) {
                        putJsonObject("timestamps") {
                            put("start", start)
                            put("end", start + a.durationMs)
                        }
                    }
                }
            }
            put("nonce", java.util.UUID.randomUUID().toString())
        }
        write(channel, 1, json.toString())
    }

    private fun clearActivity(channel: RandomAccessFile) {
        val json = buildJsonObject {
            put("cmd", "SET_ACTIVITY")
            putJsonObject("args") {
                put("pid", ProcessHandle.current().pid())
                put("activity", JsonNull)
            }
            put("nonce", java.util.UUID.randomUUID().toString())
        }
        write(channel, 1, json.toString())
        pushedSignature = null
    }

    private fun write(channel: RandomAccessFile, opcode: Int, payload: String) {
        val bytes = payload.toByteArray(Charsets.UTF_8)
        val header = ByteBuffer.allocate(8)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putInt(opcode)
            .putInt(bytes.size)
            .array()
        // One write keeps header and body adjacent from Discord's point of view.
        channel.write(header + bytes)
    }

    /** The pipe Discord listens on: `discord-ipc-0` … `discord-ipc-9`. */
    private fun openPipe(): RandomAccessFile? {
        for (index in 0..9) {
            val path = pipePath(index) ?: continue
            val handle = runCatching { RandomAccessFile(path, "rw") }.getOrNull()
            if (handle != null) return handle
        }
        return null
    }

    private fun pipePath(index: Int): String? {
        val isWindows = System.getProperty("os.name").lowercase().contains("win")
        if (isWindows) return "\\\\.\\pipe\\discord-ipc-$index"
        val base = System.getenv("XDG_RUNTIME_DIR")
            ?: System.getenv("TMPDIR")
            ?: System.getenv("TMP")
            ?: System.getenv("TEMP")
            ?: "/tmp"
        return File(base, "discord-ipc-$index").absolutePath
    }
}
