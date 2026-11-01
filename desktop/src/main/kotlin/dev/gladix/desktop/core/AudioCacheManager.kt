package dev.gladix.desktop.core

import dev.brahmkshatriya.echo.common.models.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages caching of decrypted audio streams on disk with LRU eviction
 * based on user-configured maximum cache size in MB.
 */
class AudioCacheManager(
    private val client: DeezerClient,
    private val settings: PrefsSettings,
    private val scope: CoroutineScope
) {
    private val dir = AppPaths.cache

    /** In-memory track of cached files: trackId -> File */
    private val inFlight = ConcurrentHashMap.newKeySet<String>()

    val cacheDir: File get() = dir

    /** Configured max size in bytes. 0 means caching is disabled. */
    val maxSizeBytes: Long
        get() {
            val mb = settings.getInt(PrefsSettings.KEY_CACHE_SIZE_MB) ?: PrefsSettings.DEFAULT_CACHE_SIZE_MB
            return (mb.coerceAtLeast(0).toLong()) * 1024L * 1024L
        }

    /** Returns currently used cache size in bytes. */
    fun currentSizeBytes(): Long {
        return dir.listFiles()?.filter { it.isFile && !it.name.endsWith(".part") }?.sumOf { it.length() } ?: 0L
    }

    /** Clears all cached files. */
    fun clear() {
        dir.listFiles()?.forEach { file ->
            runCatching { file.delete() }
        }
    }

    /**
     * Preloads track into disk cache in the background so future playback
     * or queue transitions are instantaneous.
     */
    fun preload(track: Track) {
        if (maxSizeBytes <= 0) return
        if (getCached(track.id) != null) return
        if (!inFlight.add(track.id)) return

        scope.launch(Dispatchers.IO) {
            val partFile = File(dir, "${track.id}.part")
            try {
                val stream = client.openStream(track)
                partFile.outputStream().use { out ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = stream.read(buffer)
                        if (read < 0) break
                        out.write(buffer, 0, read)
                    }
                }
                if (partFile.length() > 1024) {
                    val ext = detectExtension(partFile)
                    val targetFile = File(dir, "${track.id}.$ext")
                    if (targetFile.exists()) targetFile.delete()
                    partFile.renameTo(targetFile)
                    targetFile.setLastModified(System.currentTimeMillis())
                    evictIfNeeded()
                } else {
                    partFile.delete()
                }
            } catch (e: Exception) {
                runCatching { partFile.delete() }
            } finally {
                inFlight.remove(track.id)
            }
        }
    }

    /**
     * Checks if track is cached and complete. If so, updates lastModified and returns the file.
     */
    fun getCached(trackId: String): File? {
        if (maxSizeBytes <= 0) return null
        val candidates = dir.listFiles { _, name -> name.startsWith("$trackId.") && !name.endsWith(".part") }
        val file = candidates?.firstOrNull()?.takeIf { it.exists() && it.length() > 0 } ?: return null
        // Update access time for LRU
        file.setLastModified(System.currentTimeMillis())
        return file
    }

    /**
     * Opens stream for track. If cached, opens local file.
     * Otherwise streams from network while spooling into cache (if enabled).
     */
    suspend fun openStream(track: Track): InputStream {
        val cached = getCached(track.id)
        if (cached != null) {
            return cached.inputStream().buffered(64 * 1024)
        }

        val originalStream = client.openStream(track)
        if (maxSizeBytes <= 0) {
            return originalStream.buffered(64 * 1024)
        }

        // Return a tee-ing stream that writes to cache file in background or during read
        return wrapTeeStream(track.id, originalStream).buffered(64 * 1024)
    }

    private fun wrapTeeStream(trackId: String, upstream: InputStream): InputStream {
        if (!inFlight.add(trackId)) {
            // Already caching this track in another stream
            return upstream
        }

        val partFile = File(dir, "$trackId.part")
        val outStream = runCatching { partFile.outputStream() }.getOrNull()
        if (outStream == null) {
            inFlight.remove(trackId)
            return upstream
        }

        return object : InputStream() {
            private var bytesWritten = 0L
            private var completed = false
            private var closed = false

            override fun read(): Int {
                val b = upstream.read()
                if (b >= 0) {
                    try {
                        outStream.write(b)
                        bytesWritten++
                    } catch (e: Exception) {
                        closeOutQuietly()
                    }
                } else if (!completed) {
                    completed = true
                    finalizeCache()
                }
                return b
            }

            override fun read(b: ByteArray, off: Int, len: Int): Int {
                val n = upstream.read(b, off, len)
                if (n > 0) {
                    try {
                        outStream.write(b, off, n)
                        bytesWritten += n
                    } catch (e: Exception) {
                        closeOutQuietly()
                    }
                } else if (n < 0 && !completed) {
                    completed = true
                    finalizeCache()
                }
                return n
            }

            override fun close() {
                if (closed) return
                closed = true
                try {
                    upstream.close()
                } finally {
                    closeOutQuietly()
                    inFlight.remove(trackId)
                    if (!completed) {
                        // Incomplete stream read, discard part file
                        runCatching { partFile.delete() }
                    }
                }
            }

            private fun closeOutQuietly() {
                runCatching { outStream.flush() }
                runCatching { outStream.close() }
            }

            private fun finalizeCache() {
                closeOutQuietly()
                inFlight.remove(trackId)
                if (bytesWritten > 1024) { // Only keep if non-trivial audio was read
                    scope.launch(Dispatchers.IO) {
                        try {
                            val ext = detectExtension(partFile)
                            val targetFile = File(dir, "$trackId.$ext")
                            if (targetFile.exists()) targetFile.delete()
                            partFile.renameTo(targetFile)
                            targetFile.setLastModified(System.currentTimeMillis())
                            evictIfNeeded()
                        } catch (e: Exception) {
                            runCatching { partFile.delete() }
                        }
                    }
                } else {
                    runCatching { partFile.delete() }
                }
            }
        }
    }

    private fun detectExtension(file: File): String {
        val header = ByteArray(4)
        runCatching { file.inputStream().use { it.read(header) } }
        return when {
            header.size >= 4 && header[0] == 'f'.code.toByte() && header[1] == 'L'.code.toByte() &&
                    header[2] == 'a'.code.toByte() && header[3] == 'C'.code.toByte() -> "flac"

            header.size >= 3 && header[0] == 'I'.code.toByte() &&
                    header[1] == 'D'.code.toByte() && header[2] == '3'.code.toByte() -> "mp3"

            header.size >= 2 && header[0] == 'R'.code.toByte() && header[1] == 'I'.code.toByte() -> "wav"
            else -> "mp3"
        }
    }

    /**
     * Evicts oldest accessed files if cache exceeds [maxSizeBytes].
     */
    fun evictIfNeeded() {
        val limit = maxSizeBytes
        if (limit <= 0) {
            clear()
            return
        }

        val files = dir.listFiles { _, name -> !name.endsWith(".part") } ?: return
        var total = files.sumOf { it.length() }
        if (total <= limit) return

        // Sort by last modified ascending (oldest first)
        val sorted = files.sortedBy { it.lastModified() }
        for (f in sorted) {
            if (total <= limit) break
            val length = f.length()
            if (f.delete()) {
                total -= length
            }
        }
    }
}
