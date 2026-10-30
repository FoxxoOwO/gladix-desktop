package dev.gladix.desktop.core.platform

import dev.gladix.desktop.core.player.RepeatMode
import dev.brahmkshatriya.echo.common.models.Track

/**
 * Bridge between [dev.gladix.desktop.core.player.PlayerController] and the
 * operating system's media controls.
 *
 * Keeping the controller behind this interface means the player never has to
 * know whether it is running on Windows, and the desktop integration can be
 * turned off (or fails to load) without touching playback.
 */
interface SystemMediaSink {

    /** A different track started (or the queue was cleared when [track] is null). */
    fun onTrackChanged(track: Track?, isPlaying: Boolean)

    fun onPlayingChanged(isPlaying: Boolean)

    fun onPositionChanged(positionMs: Long, durationMs: Long)

    fun onModesChanged(shuffle: Boolean, repeat: RepeatMode)

    fun onVolumeChanged(volume: Float) {}

    fun close() {}

    /** No-op sink used before the real one is attached. */
    object None : SystemMediaSink {
        override fun onTrackChanged(track: Track?, isPlaying: Boolean) {}
        override fun onPlayingChanged(isPlaying: Boolean) {}
        override fun onPositionChanged(positionMs: Long, durationMs: Long) {}
        override fun onModesChanged(shuffle: Boolean, repeat: RepeatMode) {}
    }
}