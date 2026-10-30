package dev.gladix.desktop.core

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import dev.brahmkshatriya.echo.common.models.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File
import java.io.InputStream

@Serializable
data class DownloadEntry(
    val track: Track,
    val path: String,
    val bytes: Long,
    val addedAt: Long
) {
    val file: File get() = File(path)
    val exists: Boolean get() = file.exists()
}

/**
 * Downloads tracks to disk by copying the already-decrypted stream the
 * extension provides, so the saved file is a plain, playable mp3/flac.
 */
class DownloadManager(
    private val client: DeezerClient,
    private val scope: CoroutineScope
) {

    private val _items = mutableMapOf<String, DownloadEntry>()
    private val _progress = mutableMapOf<String, Float>()
    private val listeners = mutableListOf<() -> Unit>()

    /** Bumped on every change so Compose can observe the download list. */
    var version by androidx.compose.runtime.mutableStateOf(0)
        private set

    val items: List<DownloadEntry>
        get() = synchronized(_items) { _items.values.sortedByDescending { it.addedAt } }

    fun progress(trackId: String): Float? = synchronized(_progress) { _progress[trackId] }

    fun entry(trackId: String): DownloadEntry? =
        synchronized(_items) { _items[trackId] }?.takeIf { it.exists }

    fun observe(listener: () -> Unit) {
        synchronized(listeners) { listeners.add(listener) }
    }

    private fun notifyChanged() {
        version++
        val copy = synchronized(listeners) { listeners.toList() }
        copy.forEach { runCatching { it() } }
    }

    fun load() {
        val saved = readJsonFile<List<DownloadEntry>>(AppPaths.downloadsFile, emptyList())
        synchronized(_items) {
            _items.clear()
            saved.forEach { _items[it.track.id] = it }
        }
        notifyChanged()
    }

    private fun persist() {
        writeJsonFile(AppPaths.downloadsFile, items)
    }

    fun download(track: Track, onDone: (Result<DownloadEntry>) -> Unit = {}) {
        if (entry(track.id) != null) {
            onDone(Result.success(entry(track.id)!!))
            return
        }
        synchronized(_progress) { _progress[track.id] = 0f }
        notifyChanged()
        scope.launch(Dispatchers.IO) {
            val result = runCatching {
                val stream = client.openStream(track)
                val tmp = File(AppPaths.downloads, "${track.id}.part")
                val written = stream.copyWithProgress(tmp, track.id)
                val ext = detectExtension(tmp)
                val target = File(AppPaths.downloads, "${track.id}.$ext")
                if (target.exists()) target.delete()
                tmp.renameTo(target)
                val entry = DownloadEntry(track, target.absolutePath, written, System.currentTimeMillis())
                synchronized(_items) { _items[track.id] = entry }
                persist()
                entry
            }
            synchronized(_progress) { _progress.remove(track.id) }
            notifyChanged()
            withContext(Dispatchers.Main) { onDone(result) }
        }
    }

    fun delete(trackId: String) {
        val entry = synchronized(_items) { _items.remove(trackId) } ?: return
        runCatching { entry.file.delete() }
        persist()
        notifyChanged()
    }

    private fun InputStream.copyWithProgress(target: File, trackId: String): Long {
        target.outputStream().use { out ->
            val buffer = ByteArray(64 * 1024)
            var total = 0L
            while (true) {
                val read = read(buffer)
                if (read < 0) break
                out.write(buffer, 0, read)
                total += read
                // Unknown final size: report a soft curve so the UI shows motion.
                val soft = (1f - 1f / (1f + total / 4_000_000f)).coerceIn(0f, 0.95f)
                synchronized(_progress) { _progress[trackId] = soft }
            }
            return total
        }
    }

    private fun detectExtension(file: File): String {
        val header = ByteArray(4)
        file.inputStream().use { it.read(header) }
        return when {
            header.size >= 4 && header[0] == 'f'.code.toByte() && header[1] == 'L'.code.toByte() &&
                header[2] == 'a'.code.toByte() && header[3] == 'C'.code.toByte() -> "flac"

            header.size >= 3 && header[0] == 'I'.code.toByte() &&
                header[1] == 'D'.code.toByte() && header[2] == '3'.code.toByte() -> "mp3"

            header.size >= 2 && header[0] == 'R'.code.toByte() && header[1] == 'I'.code.toByte() -> "wav"
            else -> "mp3"
        }
    }
}