package dev.gladix.desktop

import dev.gladix.desktop.core.DeezerClient
import dev.gladix.desktop.core.DownloadManager
import dev.gladix.desktop.core.HistoryStore
import dev.gladix.desktop.core.PrefsSettings
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
    val downloads = DownloadManager(client, scope)
    val history = HistoryStore()
    val player = PlayerController(client, downloads, history, settings, scope)

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

    fun init() {
        client.start()
        downloads.load()
        history.load()
        player.restore()
        player.systemMedia = mediaSession
        mediaSession.start()
    }

    fun restart() = scope.launch { }
}