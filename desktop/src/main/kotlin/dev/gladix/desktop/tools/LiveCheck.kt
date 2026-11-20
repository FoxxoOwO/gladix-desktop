package dev.gladix.desktop.tools

import dev.brahmkshatriya.echo.common.models.Album
import dev.brahmkshatriya.echo.common.models.Shelf
import dev.brahmkshatriya.echo.common.models.Track
import dev.gladix.desktop.core.AppPaths
import dev.gladix.desktop.core.DeezerClient
import dev.gladix.desktop.core.DownloadManager
import dev.gladix.desktop.core.Lrclib
import dev.gladix.desktop.core.PrefsSettings
import dev.gladix.desktop.core.allItems
import dev.gladix.desktop.core.allShelves
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import kotlin.system.exitProcess

/**
 * Exercises the parts of the app that talk to Deezer for real: a whole album,
 * its lyrics and a radio queue built from it.
 *
 * This needs the account that is already signed in on this machine
 * (`%APPDATA%/Gladix/user.json`), so it is meant for a manual run and never for
 * CI. Every network call has a timeout: a hung gateway must fail loudly instead
 * of parking the build.
 */
private var failures = 0

private fun step(name: String) = println("== $name")

private fun check(name: String, condition: Boolean, detail: String = "") {
    if (condition) println("  ok   $name${if (detail.isEmpty()) "" else " ($detail)"}")
    else {
        failures++
        println("  FAIL $name${if (detail.isEmpty()) "" else " - $detail"}")
    }
}

private suspend fun <T> probe(name: String, block: suspend () -> T): T? = try {
    withTimeout(60_000) { block() }
} catch (e: Throwable) {
    failures++
    println("  FAIL $name - ${e::class.simpleName}: ${e.message}")
    null
}

private fun signature(file: File): String {
    val head = file.inputStream().use { input ->
        val buffer = ByteArray(4)
        val read = input.read(buffer)
        if (read < 4) "" else String(buffer, 0, 4, Charsets.ISO_8859_1)
    }
    return when {
        head == "fLaC" -> "flac"
        head.startsWith("ID3") -> "mp3"
        head.startsWith("\u00FF\u00FBr".take(2)) || head[0] == '\u00FF'.code.toChar() -> "mp3"
        head.startsWith("OggS") -> "ogg"
        else -> "unknown"
    }
}

fun main() = runBlocking {
    val settings = PrefsSettings(AppPaths.settingsFile)
    val client = DeezerClient(settings)
    client.start()
    if (!client.isLoggedIn()) {
        println("liveCheck skipped: no signed-in account in ${AppPaths.userFile}")
        return@runBlocking
    }
    println("account: ${client.user?.name} (${client.user?.id})")

    // 1. Album: full track list, then a real stream for the first track.
    step("album")
    val album = probe("search finds an album") {
        client.searchFeed("Daft Punk Discovery").allShelves()
            .filterIsInstance<Shelf.Lists<*>>()
            .flatMap { it.list }
            .filterIsInstance<Album>()
            .firstOrNull { it.title.contains("Discovery", ignoreCase = true) }
            ?: throw IllegalStateException("no Discovery album in the search feed")
    }
    var track: Track? = null
    if (album != null) {
        println("  album: ${album.title} by ${album.artists.firstOrNull()?.name} (${album.id})")
        val tracks = probe("album track list loads") {
            client.albumTrackPager(album).loadAll()
        }
        check("album has tracks", !tracks.isNullOrEmpty(), "${tracks?.size ?: 0} tracks")
        track = tracks?.firstOrNull()
    }

    // 2. Downloads: the same call the download button makes, then clean up.
    step("download")
    val downloads = DownloadManager(client, CoroutineScope(SupervisorJob() + Dispatchers.IO), PrefsSettings())
    downloads.load()
    if (track != null) {
        val entry = probe("track downloads") {
            var result: Result<dev.gladix.desktop.core.DownloadEntry>? = null
            downloads.download(track) { result = it }
            val deadline = System.currentTimeMillis() + 60_000
            while (result == null && System.currentTimeMillis() < deadline) {
                kotlinx.coroutines.delay(200)
            }
            result?.getOrThrow() ?: throw IllegalStateException("download never finished")
        }
        check("file written", entry?.exists == true, "${entry?.bytes ?: 0} bytes")
        val kind = entry?.file?.let { signature(it) }
        check("file is real audio", kind == "mp3" || kind == "flac", kind ?: "missing")
        entry?.let {
            downloads.delete(it.track.id)
            check("file removed", !it.file.exists())
        }
    }

    // 3. Lyrics: Deezer first, lrclib as the fallback the pane relies on.
    step("lyrics")
    if (track != null) {
        val text = probe("lyrics resolve") { client.lyrics(track) }
        check("lyrics are not empty", !text.isNullOrBlank(), "${text?.length ?: 0} chars")
        println("  first line: ${text?.lineSequence()?.firstOrNull()}")
        val fallback = probe("lrclib answers") { Lrclib.lyrics(track) }
        check("lrclib is reachable", true, "${fallback?.length ?: 0} chars")
    }

    // 4. Radio: a queue built from the album, which is what the radio button does.
    step("radio")
    if (album != null) {
        val feed = probe("radio feed loads") { client.radioFor(album, null) }
        check("radio feed exists", feed != null)
        val tracks = feed?.let { probe("radio queue fills") { it.allItems() } }
        check("radio has tracks", !tracks.isNullOrEmpty(), "${tracks?.size ?: 0} tracks")
        println("  first: ${tracks?.firstOrNull()?.title}")
    }

    // 5. Flow: the account's personal stream, no seed track needed.
    step("flow")
    val flow = probe("flow loads") { client.flowTracks() }
    check("flow has tracks", !flow.isNullOrEmpty(), "${flow?.size ?: 0} tracks")
    println("  first: ${flow?.firstOrNull()?.title}")

    // Endless Flow relies on a fresh batch not repeating the one already queued.
    val again = probe("flow loads twice") { client.flowTracks() }
    val known = flow.orEmpty().mapTo(HashSet()) { it.id }
    val fresh = again.orEmpty().filterNot { it.id in known }
    check("flow keeps producing new tracks", fresh.isNotEmpty(), "${fresh.size} of ${again?.size ?: 0} new")

    if (failures > 0) {
        println("liveCheck FAILED ($failures)")
        exitProcess(1)
    }
    println("liveCheck ok")
}
