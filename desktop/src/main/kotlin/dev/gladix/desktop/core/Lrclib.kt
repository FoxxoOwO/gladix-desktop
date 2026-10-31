package dev.gladix.desktop.core

import dev.brahmkshatriya.echo.common.models.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.Request
import java.net.URLEncoder
import kotlin.math.abs

/**
 * Fallback lyric source: lrclib.net.
 *
 * Deezer only carries words for part of the catalogue - a normal favourites
 * list answers with `lyrics: null` for roughly half of its tracks - so whenever
 * Deezer has nothing the app asks this free, key-less API instead. The exact
 * lookup runs first, a fuzzy search by title and artist second; the candidate
 * with the closest duration wins. Synchronized LRC text is reduced to plain
 * lines so the same pane can render both sources.
 */
object Lrclib {

    private const val BASE = "https://lrclib.net/api"
    private const val USER_AGENT = "Gladix/1.1 (Windows)"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /** Returns plain lyrics for [track], or null when lrclib has none. */
    suspend fun lyrics(track: Track): String? = withContext(Dispatchers.IO) {
        val title = track.title.trim()
        if (title.isEmpty()) return@withContext null
        val artist = track.artists.firstOrNull()?.name?.trim().orEmpty()
        val album = track.album?.title?.trim().orEmpty()
        val seconds = track.duration?.let { (it / 1000).toInt() } ?: 0

        val exact = get(
            "$BASE/get?track_name=${title.enc()}&artist_name=${artist.enc()}" +
                    "&album_name=${album.enc()}&duration=$seconds"
        )
        val fromExact = exact?.let { best(candidates(it), seconds) }
        if (fromExact != null) return@withContext fromExact

        val search = get("$BASE/search?track_name=${title.enc()}&artist_name=${artist.enc()}")
        val fromSearch = search?.let { best(candidates(it), seconds) }
        if (fromSearch != null) return@withContext fromSearch

        val loose = get("$BASE/search?q=${"$artist $title".trim().enc()}")
        loose?.let { best(candidates(it), seconds) }
    }

    private suspend fun get(path: String): String? = runCatching {
        val request = Request.Builder().url(path).header("User-Agent", USER_AGENT).build()
        Http.client.newCall(request).execute().use { response ->
            if (response.isSuccessful) response.body?.string() else null
        }
    }.getOrNull()

    private fun candidates(body: String): List<JsonObject> = runCatching {
        when (val element = json.parseToJsonElement(body)) {
            is JsonArray -> element.mapNotNull { it as? JsonObject }
            is JsonObject -> listOf(element)
            else -> emptyList()
        }
    }.getOrDefault(emptyList())

    private fun best(list: List<JsonObject>, seconds: Int): String? {
        val scored = list.mapNotNull { obj ->
            val text = obj.lyricsText() ?: return@mapNotNull null
            val duration = (obj["duration"] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()
            text to abs((duration ?: 0.0) - seconds)
        }
        return scored.minByOrNull { it.second }?.first
    }

    private fun JsonObject.lyricsText(): String? {
        val plain = (this["plainLyrics"] as? JsonPrimitive)?.contentOrNull
        if (!plain.isNullOrBlank()) return plain.trim()
        val synced = (this["syncedLyrics"] as? JsonPrimitive)?.contentOrNull
        if (synced.isNullOrBlank()) return null
        return stripTimestamps(synced)
    }

    /** Drops `[mm:ss.xx]` / `[00:12.34]` markers and squeezes blank lines. */
    internal fun stripTimestamps(synced: String): String? {
        val lines = synced.lines()
            .map { line -> line.replace(TIMESTAMP, "").trim() }
            .filter { it.isNotEmpty() }
        return lines.takeIf { it.isNotEmpty() }?.joinToString("\n")
    }

    private val TIMESTAMP = Regex("""\[\d{1,2}:\d{2}(?:[.:]\d{1,3})?]""")

    private fun String.enc(): String = URLEncoder.encode(this, "UTF-8")
}
