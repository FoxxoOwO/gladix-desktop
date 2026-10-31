package dev.gladix.desktop.core

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.brahmkshatriya.echo.common.helpers.ClientException
import dev.brahmkshatriya.echo.common.helpers.PagedData
import dev.brahmkshatriya.echo.common.models.Album
import dev.brahmkshatriya.echo.common.models.Artist
import dev.brahmkshatriya.echo.common.models.EchoMediaItem
import dev.brahmkshatriya.echo.common.models.Feed
import dev.brahmkshatriya.echo.common.models.Lyrics
import dev.brahmkshatriya.echo.common.models.Playlist
import dev.brahmkshatriya.echo.common.models.Radio
import dev.brahmkshatriya.echo.common.models.Shelf
import dev.brahmkshatriya.echo.common.models.Streamable
import dev.brahmkshatriya.echo.common.models.Tab
import dev.brahmkshatriya.echo.common.models.Track
import dev.brahmkshatriya.echo.common.models.TrackDetails
import dev.brahmkshatriya.echo.common.models.User
import dev.brahmkshatriya.echo.extension.DeezerExtension
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream

/**
 * Wrapper around the unmodified gladix Deezer extension.
 *
 * The extension is the same code the Android app ships, so search, browse,
 * lyrics, likes, library and stream decryption behave identically here.
 */
class DeezerClient(val settings: PrefsSettings) {

    val extension = DeezerExtension()

    var user by mutableStateOf<User?>(null)
        private set

    fun start() {
        // The extension reads every setting lazily through the session singleton,
        // so settings must be installed before anything else touches the api.
        extension.setSettings(settings)
        val saved = readJsonFile<User?>(AppPaths.userFile, null)
        if (saved != null) {
            user = saved
            extension.setLoginUser(saved)
        }
    }

    fun isLoggedIn(): Boolean = user != null

    suspend fun loginWithArl(arl: String): User = withContext(Dispatchers.IO) {
        val users = extension.onLogin("manual", mapOf("arl" to arl.trim()))
        finishLogin(users)
    }

    suspend fun loginWithPassword(email: String, password: String): User =
        withContext(Dispatchers.IO) {
            val users = extension.onLogin(
                "userPass",
                mapOf("email" to email.trim(), "pass" to password)
            )
            finishLogin(users)
        }

    private suspend fun finishLogin(users: List<User>): User {
        val first = users.firstOrNull()
            ?: throw IllegalStateException("Deezer did not return an account")
        if (first.id.isBlank()) throw IllegalStateException("Deezer returned a blank account id")
        extension.setLoginUser(first)
        user = first
        writeJsonFile(AppPaths.userFile, first)
        runCatching { extension.onExtensionSelected() }
        return first
    }

    fun logout() {
        runCatching { extension.setLoginUser(null) }
        user = null
        AppPaths.userFile.delete()
    }

    /** True when a failure means the stored credentials are dead. */
    fun isLoginRequired(t: Throwable): Boolean {
        var current: Throwable? = t
        var depth = 0
        while (current != null && depth < 8) {
            if (current is ClientException.LoginRequired) return true
            current = current.cause
            depth++
        }
        return false
    }

    // ------------------------------------------------------------------ feeds

    suspend fun homeFeed(): Feed<Shelf> = extension.loadHomeFeed()
    suspend fun libraryFeed(): Feed<Shelf> = extension.loadLibraryFeed()
    suspend fun searchFeed(query: String): Feed<Shelf> = extension.loadSearchFeed(query)

    suspend fun albumDetail(album: Album): Album = extension.loadAlbum(album)
    suspend fun artistDetail(artist: Artist): Artist = extension.loadArtist(artist)
    suspend fun playlistDetail(playlist: Playlist): Playlist = extension.loadPlaylist(playlist)

    suspend fun albumTrackPager(album: Album): TrackPager =
        TrackPager(extension.loadTracks(album))

    suspend fun playlistTrackPager(playlist: Playlist): TrackPager =
        TrackPager(extension.loadTracks(playlist))

    suspend fun artistShelves(artist: Artist): List<Shelf> =
        runCatching { extension.loadFeed(artist) }.getOrNull()?.allShelves().orEmpty()

    suspend fun albumShelves(album: Album): List<Shelf> =
        runCatching { extension.loadFeed(album) }.getOrNull()?.allShelves().orEmpty()

    suspend fun playlistShelves(playlist: Playlist): List<Shelf> =
        runCatching { extension.loadFeed(playlist) }.getOrNull()?.allShelves().orEmpty()

    suspend fun trackShelves(track: Track): List<Shelf> =
        runCatching { extension.loadFeed(track) }.getOrNull()?.allShelves().orEmpty()

    suspend fun radioFor(item: EchoMediaItem, context: EchoMediaItem?): Feed<Track>? =
        runCatching { extension.radio(item, context) }.getOrNull()?.let {
            runCatching { extension.loadTracks(it) }.getOrNull()
        }

    // ---------------------------------------------------------------- library

    suspend fun isLiked(item: EchoMediaItem): Boolean =
        runCatching { extension.isItemLiked(item) }.getOrDefault(false)

    suspend fun setLiked(item: EchoMediaItem, liked: Boolean) {
        extension.likeItem(item, liked)
    }

    suspend fun isSaved(item: EchoMediaItem): Boolean =
        runCatching { extension.isItemSaved(item) }.getOrDefault(false)

    suspend fun setSaved(item: EchoMediaItem, saved: Boolean) {
        extension.saveToLibrary(item, saved)
    }

    suspend fun followed(item: EchoMediaItem): Boolean =
        runCatching { extension.isFollowing(item) }.getOrDefault(false)

    suspend fun setFollowed(item: EchoMediaItem, follow: Boolean) {
        extension.followItem(item, follow)
    }

    suspend fun shareUrl(item: EchoMediaItem): String? =
        runCatching { extension.onShare(item) }.getOrNull()

    // -------------------------------------------------------------- playlists

    /** Playlists of the signed-in user that accept new tracks, and whether [track] is in them. */
    suspend fun editablePlaylists(track: Track?): List<Pair<Playlist, Boolean>> =
        runCatching { extension.listEditablePlaylists(track) }.getOrDefault(emptyList())

    suspend fun createPlaylist(title: String): Playlist =
        extension.createPlaylist(title, null)

    /** Appends [tracks] to the end of [playlist]. */
    suspend fun addToPlaylist(playlist: Playlist, tracks: List<Track>) {
        extension.addTracksToPlaylist(playlist, tracks, tracks.size, tracks)
    }

    // ----------------------------------------------------------------- lyrics

    suspend fun lyrics(track: Track): String? = runCatching {
        val feed = extension.searchTrackLyrics("desktop", track)
        val entry = feed.allItems().firstOrNull() ?: return@runCatching null
        lyricToText(entry.lyrics)
    }.getOrNull()

    private fun lyricToText(lyric: Lyrics.Lyric?): String? = when (lyric) {
        null -> null
        is Lyrics.Simple -> lyric.text
        is Lyrics.Timed -> lyric.list.joinToString("\n") { it.text }
        is Lyrics.WordByWord -> lyric.list.joinToString("\n") { line ->
            line.joinToString(" ") { it.text }
        }
    }

    // ----------------------------------------------------------------- stream

    /** Preferred quality, highest first: flac -> 320 -> 128. */
    fun preferredQualityValues(): List<Int> = when (settings.getStringOrDefault(
        PrefsSettings.KEY_QUALITY, PrefsSettings.QUALITY_FLAC
    )) {
        PrefsSettings.QUALITY_320 -> listOf(6, 3, 0)
        PrefsSettings.QUALITY_128 -> listOf(3, 0)
        else -> listOf(9, 6, 3, 0)
    }

    /**
     * Opens the decrypted audio stream for [track], always starting at the
     * beginning. The player decodes forward when seeking.
     */
    suspend fun openStream(track: Track): InputStream = withContext(Dispatchers.IO) {
        val full = if (track.streamables.isEmpty()) extension.loadTrack(track, false) else track
        val streamable = pickStreamable(full)
        val media = extension.loadStreamableMedia(streamable, false)
        when (media) {
            is Streamable.Media.Server -> {
                val source = media.sources.firstOrNull()
                    ?: throw IllegalStateException("Deezer returned no source")
                when (source) {
                    is Streamable.Source.Raw -> {
                        val provider = source.streamProvider
                            ?: throw IllegalStateException("Source has no stream provider")
                        provider.provide(0L, -1L).first
                    }

                    is Streamable.Source.Http -> openHttp(source)
                    else -> throw IllegalStateException("Unsupported source")
                }
            }

            else -> throw IllegalStateException("Unsupported media")
        }
    }

    /** Best available streamable for the preferred quality. */
    fun pickStreamable(track: Track): Streamable {
        val servers = track.streamables.filter { it.type == Streamable.MediaType.Server }
        if (servers.isEmpty()) throw IllegalStateException("Track has no audio source")

        val wanted = preferredQualityValues()
        for (value in wanted) {
            val match = servers.filter { it.quality == value }
            if (match.isNotEmpty()) return match.first()
        }
        // Nothing matched: prefer the highest quality that is at most the best we asked for.
        val best = wanted.first()
        return servers.filter { it.quality <= best }.maxByOrNull { it.quality }
            ?: servers.minByOrNull { it.quality }
            ?: servers.first()
    }

    private fun openHttp(source: Streamable.Source.Http): InputStream {
        val request = source.request
        val builder = okhttp3.Request.Builder().url(request.url)
        request.headers.forEach { (key, value) -> builder.header(key, value) }
        val response = Http.client.newCall(builder.build()).execute()
        if (!response.isSuccessful) {
            response.close()
            throw IllegalStateException("HTTP ${response.code} while opening stream")
        }
        return response.body.byteStream()
    }

    suspend fun resolveTrack(track: Track): Track =
        runCatching { extension.loadTrack(track, false) }.getOrDefault(track)

    // ------------------------------------------------------------------- misc

    suspend fun markPlayed(
        track: Track,
        context: EchoMediaItem?,
        positionMs: Long,
        durationMs: Long?
    ) {
        runCatching {
            extension.onMarkAsPlayed(
                TrackDetails(
                    extensionId = "deezer",
                    track = track,
                    context = context,
                    currentPosition = positionMs,
                    totalDuration = durationMs
                )
            )
        }
    }

    suspend fun markPlayedAfterMs(): Long = runCatching {
        extension.getMarkAsPlayedDuration(
            TrackDetails("deezer", Track(id = "0", title = ""), null, 0L, null)
        )
    }.getOrDefault(30_000L)
}

/**
 * Incrementally loads a [Feed] of tracks, one page at a time.
 */
class TrackPager(private val feed: Feed<Track>) {

    private var paged: PagedData<Track>? = null
    private var tabIndex = 0
    private var continuation: String? = null
    private var started = false

    var items: List<Track> = emptyList()
        private set
    var finished = false
        private set

    val hasMore: Boolean get() = !finished

    suspend fun loadNext(): List<Track> {
        if (finished) return emptyList()
        val tabs = feed.notSortTabs
        val data = paged ?: run {
            val tab = tabs.getOrNull(tabIndex)
            if (tabs.isNotEmpty() && tab == null) {
                finished = true
                return emptyList()
            }
            started = true
            feed.getPagedData(tab).pagedData.also { paged = it }
        }
        val page = data.loadPage(continuation)
        items = items + page.data
        continuation = page.continuation
        if (continuation == null) {
            // Move on to the next tab, if any.
            val next = tabIndex + 1
            if (next < tabs.size) {
                tabIndex = next
                paged = null
                continuation = null
            } else {
                finished = true
            }
        }
        return page.data
    }

    suspend fun loadAll(): List<Track> {
        while (!finished) loadNext()
        return items
    }
}

internal suspend fun Feed<Shelf>.allShelves(): List<Shelf> {
    val tabs = notSortTabs
    if (tabs.isEmpty()) return getPagedData(null).pagedData.loadAll()
    val result = mutableListOf<Shelf>()
    for (tab in tabs) {
        runCatching { getPagedData(tab).pagedData.loadAll() }.onSuccess { result += it }
    }
    return result
}

internal suspend fun <T : Any> Feed<T>.allItems(): List<T> {
    val tabs = notSortTabs
    if (tabs.isEmpty()) return getPagedData(null).pagedData.loadAll()
    val result = mutableListOf<T>()
    for (tab in tabs) {
        runCatching { getPagedData(tab).pagedData.loadAll() }.onSuccess { result += it }
    }
    return result
}

internal fun Feed<*>.firstTab(): Tab? = notSortTabs.firstOrNull()

/** Shared okhttp client for non-extension HTTP. */
object Http {
    val client: okhttp3.OkHttpClient by lazy {
        okhttp3.OkHttpClient.Builder()
            .connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }
}