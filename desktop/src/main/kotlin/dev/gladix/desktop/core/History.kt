package dev.gladix.desktop.core

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import dev.brahmkshatriya.echo.common.models.Track
import kotlinx.serialization.Serializable

@Serializable
data class HistoryEntry(
    val track: Track,
    val playedAt: Long
)

/**
 * Recently played tracks, newest first, capped to [MAX] entries.
 */
class HistoryStore {

    private val entries = mutableListOf<HistoryEntry>()
    private val listeners = mutableListOf<() -> Unit>()

    /** Bumped on every change so Compose can observe the history list. */
    var version by androidx.compose.runtime.mutableStateOf(0)
        private set

    val all: List<HistoryEntry> get() = synchronized(entries) { entries.toList() }

    fun observe(listener: () -> Unit) = synchronized(listeners) { listeners.add(listener) }

    private fun notifyChanged() {
        version++
        val copy = synchronized(listeners) { listeners.toList() }
        copy.forEach { runCatching { it() } }
    }

    fun load() {
        val saved = readJsonFile<List<HistoryEntry>>(AppPaths.historyFile, emptyList())
        synchronized(entries) {
            entries.clear()
            entries.addAll(saved)
        }
        notifyChanged()
    }

    fun add(track: Track) {
        synchronized(entries) {
            entries.removeAll { it.track.id == track.id }
            entries.add(0, HistoryEntry(track, System.currentTimeMillis()))
            while (entries.size > MAX) entries.removeAt(entries.size - 1)
            writeJsonFile(AppPaths.historyFile, entries.toList())
        }
        notifyChanged()
    }

    fun clear() {
        synchronized(entries) {
            entries.clear()
            writeJsonFile(AppPaths.historyFile, emptyList<HistoryEntry>())
        }
        notifyChanged()
    }

    private companion object {
        const val MAX = 250
    }
}