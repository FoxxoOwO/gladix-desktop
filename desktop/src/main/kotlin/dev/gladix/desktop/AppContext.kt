package dev.gladix.desktop

import dev.gladix.desktop.core.AudioCacheManager
import dev.gladix.desktop.core.DeezerClient
import dev.gladix.desktop.core.DownloadManager
import dev.gladix.desktop.core.HistoryStore
import dev.gladix.desktop.core.PrefsSettings
import dev.gladix.desktop.core.UpdateChecker
import dev.gladix.desktop.core.platform.DiscordPresence
import dev.gladix.desktop.core.platform.SystemMediaSink
import dev.gladix.desktop.core.platform.WindowsMediaSession
import dev.gladix.desktop.core.player.PlayerController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Composition root: owns the singletons the whole UI shares.
 */
class AppContext {

    val scope: CoroutineScope = CoroutineScope(SupervisorJob() + kotlinx.coroutines.Dispatchers.Default)

    val settings = PrefsSettings()
    val client = DeezerClient(settings)
    val cache = AudioCacheManager(client, settings, scope)
    val downloads = DownloadManager(client, scope)
    val history = HistoryStore()
    val player = PlayerController(client, downloads, history, settings, scope, cache)
    val updates = UpdateChecker(settings, scope)

    /**
     * Bridges playback to the Windows media flyout. Windows then forwards the
     * hardware media keys to us, so no global keyboard hook is needed.
     */
    val mediaSession = WindowsMediaSession(
        settings = settings,
        onPlay = { player.resumeFromSystem() },
        onPause = { player.pauseFromSystem() },
        onStop = { player.stop() },
        onNext = { player.next() },
        onPrevious = { player.previous() },
        onSeek = { player.seekTo(it) },
        onShuffle = { player.applyShuffleMode(it) },
        onLoop = { player.applyRepeatMode(it) }
    )

    /**
     * Discord Rich Presence. Optional and independent of the Windows session:
     * both are fed the same playback events through [SystemMediaSink].
     */
    val discord = DiscordPresence(
        scope = scope,
        clientId = {
            // A cleared field falls back to the bundled Gladix application id,
            // so emptying the text box never silently kills the presence.
            settings.getString(PrefsSettings.KEY_DISCORD_CLIENT_ID)
                ?.trim()
                ?.takeIf { it.isNotEmpty() && it != "0" }
                ?: PrefsSettings.DEFAULT_DISCORD_CLIENT_ID
        }
    )

    fun init() {
        client.start()
        downloads.load()
        history.load()
        player.restore()
        // Both sinks receive the same events; the player also keeps its own
        // reference for the initial publish.
        player.systemMedia = FanoutMediaSink(listOf(mediaSession, discord))
        mediaSession.start()
        discord.setEnabled(settings.getBoolean(PrefsSettings.KEY_DISCORD_PRESENCE) ?: true)
        cache.evictIfNeeded()
        updates.startPeriodicCheck()
    }

    fun restart() = scope.launch { }
}

/**
 * Sends every playback event to all sinks. Attached to the player instead of a
 * single sink so Windows integration and Discord presence are independent
 * switches.
 */
class FanoutMediaSink(private val sinks: List<SystemMediaSink>) : SystemMediaSink {

    override fun onTrackChanged(
        track: dev.brahmkshatriya.echo.common.models.Track?,
        isPlaying: Boolean
    ) {
        sinks.forEach { runCatching { it.onTrackChanged(track, isPlaying) } }
    }

    override fun onPlayingChanged(isPlaying: Boolean) {
        sinks.forEach { runCatching { it.onPlayingChanged(isPlaying) } }
    }

    override fun onPositionChanged(positionMs: Long, durationMs: Long) {
        sinks.forEach { runCatching { it.onPositionChanged(positionMs, durationMs) } }
    }

    override fun onModesChanged(shuffle: Boolean, repeat: dev.gladix.desktop.core.player.RepeatMode) {
        sinks.forEach { runCatching { it.onModesChanged(shuffle, repeat) } }
    }

    override fun onVolumeChanged(volume: Float) {
        sinks.forEach { runCatching { it.onVolumeChanged(volume) } }
    }

    override fun close() {
        sinks.forEach { runCatching { it.close() } }
    }
}
