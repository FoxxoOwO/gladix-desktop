package dev.gladix.desktop.core

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import dev.brahmkshatriya.echo.common.models.ImageHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jetbrains.skia.Image
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Minimal image loader: okhttp download + skia decode + in-memory LRU cache.
 * Deliberately dependency free so the app stays portable.
 */
object Images {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private const val MAX_ENTRIES = 300
    private val cache = object : LinkedHashMap<String, ImageBitmap>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?): Boolean =
            size > MAX_ENTRIES
    }

    private val missing = ConcurrentHashMap.newKeySet<String>()
    private val inFlight = ConcurrentHashMap<String, Any>()

    fun urlOf(holder: ImageHolder?): String? = when (holder) {
        null -> null
        is ImageHolder.NetworkRequestImageHolder -> holder.request.url
        is ImageHolder.ResourceUriImageHolder -> holder.uri
        else -> null
    }

    fun headersOf(holder: ImageHolder?): Map<String, String> = when (holder) {
        is ImageHolder.NetworkRequestImageHolder -> holder.request.headers
        else -> emptyMap()
    }

    fun cached(holder: ImageHolder?): ImageBitmap? {
        val url = urlOf(holder) ?: return null
        synchronized(cache) { return cache[url] }
    }

    /**
     * Loads [holder] into memory. Returns the decoded bitmap, or null when unavailable.
     * Concurrent callers for the same url are serialized (one download).
     */
    suspend fun load(holder: ImageHolder?): ImageBitmap? {
        val url = urlOf(holder) ?: return null
        synchronized(cache) { cache[url] }?.let { return it }
        if (missing.contains(url)) return null

        val lock = inFlight.computeIfAbsent(url) { Any() }
        synchronized(lock) {
            synchronized(cache) { cache[url] }?.let { return it }
            if (missing.contains(url)) return null
        }

        val bytes = withContext(Dispatchers.IO) { fetch(url, headersOf(holder)) }
        if (bytes == null) {
            missing.add(url)
            inFlight.remove(url)
            return null
        }
        val bitmap = withContext(Dispatchers.Default) {
            runCatching { Image.makeFromEncoded(bytes).toComposeImageBitmap() }.getOrNull()
        }
        if (bitmap == null) {
            missing.add(url)
        } else {
            synchronized(cache) { cache[url] = bitmap }
        }
        inFlight.remove(url)
        return bitmap
    }

    private fun fetch(url: String, headers: Map<String, String>): ByteArray? {
        if (url.startsWith("file:")) {
            return runCatching { java.io.File(java.net.URI(url)).readBytes() }.getOrNull()
        }
        if (!url.startsWith("http")) {
            return runCatching { java.io.File(url).readBytes() }.getOrNull()
        }
        return runCatching {
            val builder = Request.Builder().url(url)
            headers.forEach { (k, v) -> builder.header(k, v) }
            client.newCall(builder.build()).execute().use { response ->
                if (!response.isSuccessful) null else response.body.bytes()
            }
        }.getOrNull()
    }
}