package dev.gladix.desktop

import dev.brahmkshatriya.echo.common.models.Artist
import dev.brahmkshatriya.echo.common.models.Album
import dev.brahmkshatriya.echo.common.models.Shelf
import dev.brahmkshatriya.echo.common.models.ImageHolder
import dev.brahmkshatriya.echo.common.models.ImageHolder.Companion.toImageHolder
import dev.brahmkshatriya.echo.common.models.Streamable
import dev.brahmkshatriya.echo.common.models.Track
import dev.gladix.desktop.core.AppJson
import dev.gladix.desktop.core.AppPaths
import dev.gladix.desktop.core.DeezerClient
import dev.gladix.desktop.core.DownloadManager
import dev.gladix.desktop.core.SelfUpdater
import dev.gladix.desktop.core.UpdateChecker
import dev.gladix.desktop.core.readyState
import dev.gladix.desktop.core.sha256
import dev.gladix.desktop.core.expectedSha256
import java.io.File
import dev.gladix.desktop.core.HistoryStore
import dev.gladix.desktop.core.Lrclib
import dev.gladix.desktop.core.PrefsSettings
import dev.gladix.desktop.core.Shortcut
import dev.gladix.desktop.core.Shortcuts
import dev.gladix.desktop.core.ThemePreference
import dev.gladix.desktop.core.platform.AppIdentity
import dev.gladix.desktop.core.platform.WindowsMediaSession
import dev.gladix.desktop.core.player.PlayerController
import dev.gladix.desktop.core.player.mutedVolume
import dev.gladix.desktop.core.player.Decoders
import dev.gladix.desktop.core.player.SavedQueue
import dev.gladix.desktop.core.player.StreamQuality
import dev.gladix.desktop.ui.SortField
import dev.gladix.desktop.ui.SortSpec
import dev.gladix.desktop.ui.TypingFocus
import dev.gladix.desktop.ui.flatTrackRun
import dev.gladix.desktop.ui.sortShelves
import dev.gladix.desktop.ui.text.UiStrings
import kotlinx.serialization.encodeToString
import java.io.ByteArrayOutputStream as JavaByteArrayOutputStream
import java.io.ByteArrayInputStream
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.SourceDataLine

/**
 * Headless self-check for the parts that do not need a Deezer login:
 * serialization of the persisted models, the settings store, the audio
 * pipeline and the extension wiring. Run with `./gradlew :desktop:selfCheck`.
 */
fun main(args: Array<String>) {
    if (args.firstOrNull() == "media-probe") {
        AppIdentity.install()
        mediaProbe()
        return
    }
    var failures = 0
    // SMTC is a Windows API; on other systems the session only reports that it
    // is unavailable, so the check below has nothing to assert there.
    val isWindows = System.getProperty("os.name").orEmpty().lowercase().contains("win")

    fun check(name: String, block: () -> Unit) {
        val result = runCatching(block)
        if (result.isSuccess) {
            println("PASS  $name")
        } else {
            failures++
            println("FAIL  $name -> ${result.exceptionOrNull()}")
        }
    }

    // ---------------------------------------------------------------- models

    // The sort sheet writes these choices to settings.properties, so the pure
    // ordering logic has to be provably right without a window on screen.
    check("sort spec orders by title, duration and reverses the original") {
        fun t(id: String, title: String, ms: Long, artist: String) = Track(
            id = id,
            title = title,
            artists = listOf(Artist(id = "a-$id", name = artist)),
            duration = ms
        )
        val a = t("1", "Beta", 3000L, "Zoe")
        val b = t("2", "alpha", 1000L, "Adam")
        val c = t("3", "Gamma", 2000L, "Mike")
        val original = listOf(a, b, c)

        if (SortSpec().apply(original) != original) error("default order changed")

        val byTitle = SortSpec(SortField.TITLE).apply(original)
        if (byTitle.map { it.title } != listOf("alpha", "Beta", "Gamma")) {
            error("title order: ${byTitle.map { it.title }}")
        }

        val byDuration = SortSpec(SortField.DURATION).apply(original)
        if (byDuration.map { it.id } != listOf("2", "3", "1")) {
            error("duration order: ${byDuration.map { it.id }}")
        }

        val byArtists = SortSpec(SortField.ARTISTS).apply(original)
        if (byArtists.map { it.id } != listOf("2", "3", "1")) {
            error("artist order: ${byArtists.map { it.id }}")
        }

        val reversed = SortSpec(SortField.DEFAULT, reversed = true).apply(original)
        if (reversed.map { it.id } != listOf("3", "2", "1")) {
            error("reversed order: ${reversed.map { it.id }}")
        }

        val titleReversed = SortSpec(SortField.TITLE, reversed = true).apply(original)
        if (titleReversed.map { it.title } != listOf("Gamma", "Beta", "alpha")) {
            error("reversed title order: ${titleReversed.map { it.title }}")
        }

        if (SortField.fromKey("duration") != SortField.DURATION) error("key lookup broken")
        if (SortField.fromKey("nonsense") != SortField.DEFAULT) error("fallback broken")
        if (!SortSpec().isDefault) error("default spec should be default")
        if (SortSpec(reversed = true).isDefault) error("reversed spec is not default")
    }

    check("keyboard shortcuts map only the intended combinations") {
        fun expect(name: String, actual: Shortcut?, wanted: Shortcut?) {
            if (actual != wanted) error("$name -> $actual, wanted $wanted")
        }
        expect("space", Shortcuts.of(androidx.compose.ui.input.key.Key.Spacebar), Shortcut.PlayPause)
        expect("ctrl+right", Shortcuts.of(androidx.compose.ui.input.key.Key.DirectionRight, ctrl = true), Shortcut.Next)
        expect("ctrl+left", Shortcuts.of(androidx.compose.ui.input.key.Key.DirectionLeft, ctrl = true), Shortcut.Previous)
        expect("ctrl+F", Shortcuts.of(androidx.compose.ui.input.key.Key.F, ctrl = true), Shortcut.Search)
        expect("ctrl+shift+right", Shortcuts.of(androidx.compose.ui.input.key.Key.DirectionRight, ctrl = true, shift = true), Shortcut.SeekForward)
        expect("ctrl+shift+left", Shortcuts.of(androidx.compose.ui.input.key.Key.DirectionLeft, ctrl = true, shift = true), Shortcut.SeekBack)
        expect("ctrl+up", Shortcuts.of(androidx.compose.ui.input.key.Key.DirectionUp, ctrl = true), Shortcut.VolumeUp)
        expect("ctrl+down", Shortcuts.of(androidx.compose.ui.input.key.Key.DirectionDown, ctrl = true), Shortcut.VolumeDown)
        expect("M", Shortcuts.of(androidx.compose.ui.input.key.Key.M), Shortcut.Mute)
        expect("escape", Shortcuts.of(androidx.compose.ui.input.key.Key.Escape), Shortcut.Back)
        // A bare arrow belongs to the focused list, and a shifted or alted key
        // must never move playback behind the user's back.
        expect("bare right", Shortcuts.of(androidx.compose.ui.input.key.Key.DirectionRight), null)
        expect("bare up", Shortcuts.of(androidx.compose.ui.input.key.Key.DirectionUp), null)
        expect("shift+M", Shortcuts.of(androidx.compose.ui.input.key.Key.M, shift = true), null)
        expect("alt+M", Shortcuts.of(androidx.compose.ui.input.key.Key.M, alt = true), null)
        expect("ctrl+M", Shortcuts.of(androidx.compose.ui.input.key.Key.M, ctrl = true), null)
        expect("shift+escape", Shortcuts.of(androidx.compose.ui.input.key.Key.Escape, shift = true), null)
        expect("shift+space", Shortcuts.of(androidx.compose.ui.input.key.Key.Spacebar, shift = true), null)
        expect("alt+space", Shortcuts.of(androidx.compose.ui.input.key.Key.Spacebar, alt = true), null)
        expect("ctrl+space", Shortcuts.of(androidx.compose.ui.input.key.Key.Spacebar, ctrl = true), null)
        expect("plain letter", Shortcuts.of(androidx.compose.ui.input.key.Key.F), null)
    }

    check("mute keeps the level to come back to") {
        if (mutedVolume(0.6f, 0.6f) != 0f) error("an audible player should mute to zero")
        if (mutedVolume(0f, 0.6f) != 0.6f) error("unmute should restore the remembered level")
        if (mutedVolume(0f, 0f) != 0.85f) error("a forgotten level should fall back to the default")
    }

    check("theme preference resolves system, dark and light") {
        if (ThemePreference.normalize(null) != PrefsSettings.THEME_SYSTEM) error("null is not system")
        if (ThemePreference.normalize("purple") != PrefsSettings.THEME_SYSTEM) error("unknown is not system")
        if (ThemePreference.normalize(PrefsSettings.THEME_LIGHT) != PrefsSettings.THEME_LIGHT) error("light reset")
        if (!ThemePreference.isDark(PrefsSettings.THEME_SYSTEM, true)) error("system should follow dark")
        if (ThemePreference.isDark(PrefsSettings.THEME_SYSTEM, false)) error("system should follow light")
        if (!ThemePreference.isDark(PrefsSettings.THEME_DARK, false)) error("dark ignored")
        if (ThemePreference.isDark(PrefsSettings.THEME_LIGHT, true)) error("light ignored")
        if (ThemePreference.choices.first() != PrefsSettings.THEME_SYSTEM) error("system should be first")
        if (ThemePreference.choices.size != 3) error("choices: ${ThemePreference.choices}")
    }

    check("feed shelves sort their tracks and item rows") {
        fun t(id: String, title: String) = Track(
            id = id,
            title = title,
            artists = listOf(Artist(id = "a-$id", name = "A")),
            duration = 1000L
        )
        val beta = t("1", "Beta")
        val alpha = t("2", "alpha")
        val tracks = Shelf.Lists.Tracks(id = "s1", title = "Skladby", list = listOf(beta, alpha))
        val items = Shelf.Lists.Items(
            id = "s2",
            title = "Skladby jako položky",
            list = listOf(beta, alpha)
        )
        val albums = Shelf.Lists.Items(
            id = "s3",
            title = "Alba",
            list = listOf(
                Album(id = "al-2", title = "Zebra"),
                Album(id = "al-1", title = "Apple")
            )
        )
        val shelves = listOf(tracks, items, albums)

        sortShelves(shelves, SortSpec()).let { unchanged ->
            if (unchanged != shelves) error("default sort must not touch the feed")
        }

        val sorted = sortShelves(shelves, SortSpec(SortField.TITLE))
        val sortedTracks = (sorted[0] as Shelf.Lists.Tracks).list
        if (sortedTracks.map { it.title } != listOf("alpha", "Beta")) {
            error("track shelf order: ${sortedTracks.map { it.title }}")
        }
        val sortedItems = (sorted[1] as Shelf.Lists.Items).list
        if (sortedItems.map { it.title } != listOf("alpha", "Beta")) {
            error("item shelf order: ${sortedItems.map { it.title }}")
        }
        if (sortedItems.size != 2) error("item shelf lost rows while sorting")
        val sortedAlbums = (sorted[2] as Shelf.Lists.Items).list
        if (sortedAlbums.map { it.title } != listOf("Apple", "Zebra")) {
            error("album shelf order: ${sortedAlbums.map { it.title }}")
        }
        val reversed = sortShelves(shelves, SortSpec(SortField.TITLE, reversed = true))
        if ((reversed[1] as Shelf.Lists.Items).list.map { it.title } != listOf("Beta", "alpha")) {
            error("reversed item shelf order is wrong")
        }

        // The library sends one Shelf.Item per track, so that shape must sort too.
        val loose = listOf(Shelf.Item(beta), Shelf.Item(alpha))
        val looseSorted = sortShelves(loose, SortSpec(SortField.TITLE))
        if (looseSorted.map { it.title } != listOf("alpha", "Beta")) {
            error("loose track rows order: ${looseSorted.map { it.title }}")
        }
        if (looseSorted.size != 2) error("loose rows were dropped while sorting")
    }

    // Clicking a track of a fine grained library tab has to queue the tracks
    // around it, so the shape of that run is checked without a window.
    check("flat library rows queue the rest of the tab") {
        fun t(id: String) = Track(
            id = id,
            title = "T$id",
            artists = listOf(Artist(id = "a-$id", name = "A")),
            duration = 1000L
        )
        val one = t("1")
        val two = t("2")
        val three = t("3")
        val album = Shelf.Item(Album(id = "al", title = "Album"))
        val shelves = listOf(
            Shelf.Item(one), Shelf.Item(two), album, Shelf.Item(three)
        )

        val firstRun = flatTrackRun(shelves, 0)
        if (firstRun?.map { it.id } != listOf("1", "2")) {
            error("first run: ${firstRun?.map { it.id }}")
        }
        if (flatTrackRun(shelves, 1)?.size != 1) error("run from a single row is wrong")
        if (flatTrackRun(shelves, 2) != null) error("an album row must not start a track run")
        val lastRun = flatTrackRun(shelves, 3)
        if (lastRun?.map { it.id } != listOf("3")) error("last run: ${lastRun?.map { it.id }}")
    }

    // Deezer has no words for a large part of the catalogue, so the lrclib
    // fallback has to strip its LRC markers and pick the closest candidate.
    check("lyrics fallback strips timestamps and picks the closest match") {
        val synced = "[00:12.34] First line\n[00:15.02] Second line\n\n[00:20.00] Third line"
        val stripped = Lrclib.stripTimestamps(synced)
            ?: error("timestamps produced no lyrics")
        if (stripped != "First line\nSecond line\nThird line") {
            error("stripped text: $stripped")
        }
        if (Lrclib.stripTimestamps("[00:01.00]\n[00:02.00]") != null) {
            error("an empty lyric body must answer null")
        }
    }

    check("Track survives JSON round-trip") {
        val track = Track(
            id = "3135556",
            title = "Harder, Better, Faster, Stronger",
            artists = listOf(Artist(id = "27", name = "Daft Punk")),
            duration = 224000L,
            cover = "https://e-cdns-images.dzcdn.net/images/cover/abc/500x500-000000-80-0-0.jpg"
                .toImageHolder(),
            streamables = listOf(
                Streamable(
                    id = "dzp:3135556:flac",
                    quality = 9,
                    type = Streamable.MediaType.Server,
                    title = "FLAC",
                    extras = mapOf("TRACK_TOKEN" to "abc")
                )
            )
        )
        val json = AppJson.encodeToString(track)
        val back = AppJson.decodeFromString<Track>(json)
        if (back != track) error("round-trip mismatch:\n$json")
    }

    check("SavedQueue survives JSON round-trip") {
        val queue = SavedQueue(
            tracks = listOf(Track(id = "1", title = "A"), Track(id = "2", title = "B")),
            index = 1,
            shuffleOrder = listOf(1, 0)
        )
        val back = AppJson.decodeFromString<SavedQueue>(AppJson.encodeToString(queue))
        if (back != queue) error("round-trip mismatch")
    }

    check("ImageHolder variants serialize") {
        val holders = listOf<ImageHolder>(
            "https://example.com/a.jpg".toImageHolder(),
            "https://example.com/b.jpg".toImageHolder(crop = true)
        )
        holders.forEach { holder ->
            AppJson.decodeFromString<ImageHolder>(AppJson.encodeToString(holder))
        }
    }

    // -------------------------------------------------------------- settings

    check("PrefsSettings stores every type") {
        val file = java.io.File(AppPaths.root, "selfcheck.properties")
        file.delete()
        val prefs = PrefsSettings(file)
        prefs.putString("s", "value")
        prefs.putInt("i", 42)
        prefs.putBoolean("b", true)
        prefs.putStringSet("set", setOf("a", "b"))

        val reread = PrefsSettings(file)
        if (reread.getString("s") != "value") error("string lost")
        if (reread.getInt("i") != 42) error("int lost")
        if (reread.getBoolean("b") != true) error("bool lost")
        if (reread.getStringSet("set") != setOf("a", "b")) error("set lost")
        file.delete()
    }

    check("quality ladder honours the setting") {
        val file = java.io.File(AppPaths.root, "selfcheck2.properties")
        file.delete()
        val prefs = PrefsSettings(file)
        val client = DeezerClient(prefs)

        prefs.putString(PrefsSettings.KEY_QUALITY, PrefsSettings.QUALITY_FLAC)
        if (client.preferredQualityValues() != listOf(9, 6, 3, 0)) error("flac ladder")
        prefs.putString(PrefsSettings.KEY_QUALITY, PrefsSettings.QUALITY_320)
        if (client.preferredQualityValues() != listOf(6, 3, 0)) error("320 ladder")
        prefs.putString(PrefsSettings.KEY_QUALITY, PrefsSettings.QUALITY_128)
        if (client.preferredQualityValues() != listOf(3, 0)) error("128 ladder")
        file.delete()
    }

    check("pickStreamable degrades to the closest quality") {
        val prefs = PrefsSettings(java.io.File(AppPaths.root, "selfcheck3.properties"))
        prefs.putString(PrefsSettings.KEY_QUALITY, PrefsSettings.QUALITY_FLAC)
        val client = DeezerClient(prefs)
        fun streamable(quality: Int) = Streamable(
            id = "q$quality", quality = quality, type = Streamable.MediaType.Server
        )

        val flacTrack = Track(id = "x", title = "x", streamables = listOf(streamable(9), streamable(6)))
        if (client.pickStreamable(flacTrack).quality != 9) error("expected flac")

        val mp3Only = Track(id = "y", title = "y", streamables = listOf(streamable(6), streamable(3)))
        if (client.pickStreamable(mp3Only).quality != 6) error("expected 320")

        prefs.putString(PrefsSettings.KEY_QUALITY, PrefsSettings.QUALITY_128)
        if (client.pickStreamable(flacTrack).quality != 6) error("expected lowest available (320) when 128 is not offered")

        val none = Track(id = "z", title = "z", streamables = emptyList())
        val failed = runCatching { client.pickStreamable(none) }.isFailure
        if (!failed) error("empty streamables should throw")
        prefs.file.delete()
    }

    check("AudioCacheManager respects limits and eviction") {
        val prefsFile = java.io.File(AppPaths.root, "selfcheck-cache.properties")
        prefsFile.delete()
        val prefs = PrefsSettings(prefsFile)
        prefs.putInt(PrefsSettings.KEY_CACHE_SIZE_MB, 1) // 1 MB limit
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO)
        val client = DeezerClient(prefs)
        // A throwaway folder: the test must never touch the user's real cache.
        val testDir = java.io.File(AppPaths.root, "selfcheck-cache").apply {
            deleteRecursively()
            mkdirs()
        }
        val cache = dev.gladix.desktop.core.AudioCacheManager(client, prefs, scope, testDir)

        cache.clear()
        if (cache.currentSizeBytes() != 0L) error("cache not cleared")

        // Create dummy files in cache
        val f1 = java.io.File(cache.cacheDir, "track1.mp3").apply { writeBytes(ByteArray(400 * 1024)) }
        f1.setLastModified(System.currentTimeMillis() - 20_000)
        val f2 = java.io.File(cache.cacheDir, "track2.mp3").apply { writeBytes(ByteArray(400 * 1024)) }
        f2.setLastModified(System.currentTimeMillis() - 10_000)
        val f3 = java.io.File(cache.cacheDir, "track3.mp3").apply { writeBytes(ByteArray(400 * 1024)) }
        f3.setLastModified(System.currentTimeMillis())

        if (cache.currentSizeBytes() < 1200 * 1024) error("files not written")

        // Evict
        cache.evictIfNeeded()
        // Total limit is 1MB, so oldest track1 should be evicted first
        if (f1.exists()) error("track1 should have been evicted")
        if (!f2.exists() || !f3.exists()) error("track2 and track3 should remain")

        cache.clear()
        testDir.deleteRecursively()
        prefsFile.delete()
    }

    // ------------------------------------------------------------- updates

    check("version comparison orders dotted releases") {
        fun cmp(a: String, b: String) = dev.gladix.desktop.core.compareVersions(a, b)

        if (cmp("1.3.0", "1.3.0") != 0) error("identical versions differ")
        if (cmp("1.4.0", "1.3.0") <= 0) error("1.4.0 should be newer than 1.3.0")
        if (cmp("1.3.0", "1.4.0") >= 0) error("1.3.0 should be older than 1.4.0")
        // 1.10 is newer than 1.9, which a string compare gets wrong.
        if (cmp("1.10.0", "1.9.0") <= 0) error("1.10.0 should be newer than 1.9.0")
        if (cmp("v1.3.0", "1.3.0") != 0) error("leading v should be ignored")
        if (cmp("1.3", "1.3.0") != 0) error("missing patch should count as zero")
        if (cmp("2.0.0", "1.99.99") <= 0) error("major bump wins")
    }

    check("release assets are picked for the right platform") {
        val assets = AppJson.parseToJsonElement(
            """[{"name":"Gladix-1.4.6-linux-x64.zip","browser_download_url":"u-linux"},
                 {"name":"Gladix-1.4.6-macos-arm64.zip","browser_download_url":"u-mac-arm"},
                 {"name":"Gladix-1.4.6-macos-x64.zip","browser_download_url":"u-mac-x64"},
                 {"name":"Gladix-1.4.6-windows-x64.zip","browser_download_url":"u-win"}]"""
        ) as kotlinx.serialization.json.JsonArray

        fun pick(os: String, arch: String) = dev.gladix.desktop.core.pickReleaseAsset(assets, os, arch)

        if (pick("Windows 11", "amd64") != "u-win") error("windows picked the wrong asset")
        if (pick("Linux", "amd64") != "u-linux") error("linux picked the wrong asset")
        if (pick("Mac OS X", "aarch64") != "u-mac-arm") error("apple silicon picked the wrong asset")
        if (pick("Mac OS X", "x86_64") != "u-mac-x64") error("intel mac picked the wrong asset")
        if (pick("FreeBSD", "amd64") != null) error("an unknown platform should pick nothing")
        println("      windows=u-win linux=u-linux macos-arm=u-mac-arm macos-x64=u-mac-x64")
    }

    check("update downloads are checked against the release notes") {
        val notes = """Gladix 1.4.7

## SHA-256
2187d1c0d0b7d1f9d0db1ec6e1ba9d64c3e1a5f2b0d9c1e8f7a6b5c4d3e2f1a0  Gladix-1.4.7-windows-x64.zip
9f0e8d7c6b5a49382716f5e4d3c2b1a09f8e7d6c5b4a39281706f5e4d3c2b1a0  Gladix-1.4.7-linux-x64.zip
""".trimIndent()

        val win = expectedSha256(notes, "Gladix-1.4.7-windows-x64.zip")
        if (win != "2187d1c0d0b7d1f9d0db1ec6e1ba9d64c3e1a5f2b0d9c1e8f7a6b5c4d3e2f1a0") {
            error("windows hash was not read from the notes: $win")
        }
        if (expectedSha256(notes, "Gladix-1.4.7-macos-x64.zip") != null) {
            error("a hash was invented for a file the notes do not list")
        }
        if (expectedSha256(null, "x.zip") != null) error("null notes gave a hash")
        val placeholder = "SHA-256" + System.lineSeparator() + "TODO  Gladix-1.4.7-windows-x64.zip"
        if (expectedSha256(placeholder, "Gladix-1.4.7-windows-x64.zip") != null) {
            error("an unfinished placeholder block was accepted")
        }

        val file = File(AppPaths.root, "selfcheck-sha256.txt")
        file.writeText("abc")
        val digest = sha256(file)
        file.delete()
        if (digest != "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad") {
            error("sha256 of \"abc\" was $digest")
        }
        file.delete()
    }

    check("a prepared update is not fetched again") {
        fun s(hasUpdate: Boolean, latest: String?, staged: String?, downloaded: String?) =
            readyState(hasUpdate, latest, staged, downloaded)

        if (s(false, "1.4.7", null, null) != UpdateChecker.State.UpToDate) error("no update should be up to date")
        if (s(true, "1.4.8", null, null) != UpdateChecker.State.Available) error("a new version should be available")
        if (s(true, "1.4.8", "1.4.8", null) != UpdateChecker.State.Ready) error("staged update should stay ready")
        if (s(true, "1.4.8", null, "1.4.8") != UpdateChecker.State.Downloaded) error("downloaded update should stay downloaded")
        // A newer release must not reuse the older staging copy.
        if (s(true, "1.4.9", "1.4.8", null) != UpdateChecker.State.Available) error("an old staging copy was reused")
    }

    check("an app image is recognised on every platform") {
        val base = java.nio.file.Files.createTempDirectory("gladix-image").toFile()

        val linux = File(base, "GladixLinux")
        File(linux, "bin").mkdirs()
        File(linux, "lib").mkdirs()
        File(linux, "bin/Gladix").writeText("#!/bin/sh")

        val mac = File(base, "Gladix.app")
        File(mac, "Contents/MacOS").mkdirs()
        File(mac, "Contents/MacOS/Gladix").writeText("x")

        val win = File(base, "GladixWindows")
        File(win, "app").mkdirs()
        File(win, "Gladix.exe").writeText("x")

        if (!SelfUpdater(linux).isSupported) error("linux image not recognised")
        if (!SelfUpdater(mac).isSupported) error("mac bundle not recognised")
        if (!SelfUpdater(win).isSupported) error("windows image not recognised")
        if (SelfUpdater(File(base, "empty")).isSupported) error("an empty folder looked like an image")

        val script = SelfUpdater(linux).renderShScript("/tmp/a b/src", "/tmp/a b/dst", true, 42, "/tmp/a b/log")
        for (needle in listOf("'/tmp/a b/src'", "'/tmp/a b/dst'", "restart=1", "pid=42", "while kill -0", "rm -rf", "rolled back")) {
            if (!script.contains(needle)) error("swap script is missing $needle")
        }
        if (script.contains("restart=0")) error("the restart flag was lost")
        val quoted = SelfUpdater(linux).renderShScript("/tmp/it's/src", "/tmp/dst", false, 1, "/tmp/log")
        if (!quoted.contains("/tmp/it'\\''s/src")) error("an apostrophe in the path was not escaped")
        base.deleteRecursively()
    }

    check("typing in a text field keeps the space bar out of playback") {
        val typing = TypingFocus()
        if (typing.active) error("nothing is focused yet")
        typing.update(true)
        if (!typing.active) error("a focused field should block the shortcut")
        // Focus moving to another field: the old one leaves before the new one
        // arrives, so the gap is one callback wide and no key can land there.
        typing.update(false)
        typing.update(true)
        if (!typing.active) error("moving focus between fields lost the state")
        typing.update(false)
        if (typing.active) error("the last field left, yet typing stays active")
        // A spurious release must not drive the count negative.
        typing.update(false)
        typing.update(true)
        if (!typing.active) error("an extra release broke the count")
    }

    check("the download folder follows the setting") {
        val prefs = PrefsSettings(java.io.File(AppPaths.root, "selfcheck-downloads.properties"))
        val manager = DownloadManager(
            DeezerClient(prefs),
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default),
            prefs
        )
        if (manager.dir != AppPaths.downloads) error("an unset folder should fall back to app data")

        val picked = java.nio.file.Files.createTempDirectory("gladix-dl").toFile()
        prefs.putString(PrefsSettings.KEY_DOWNLOAD_DIR, picked.absolutePath)
        if (manager.dir != picked) error("a picked folder should be used as is")

        // A folder that was moved or deleted must not break downloading.
        picked.deleteRecursively()
        if (manager.dir != AppPaths.downloads) error("a missing folder should fall back to app data")

        prefs.putString(PrefsSettings.KEY_DOWNLOAD_DIR, null)
        println("      default=${AppPaths.downloads.name} picked=$picked")
    }

    // ----------------------------------------------------------------- audio

    check("javax.sound knows the PCM format and registers decoders") {
        val types = AudioSystem.getAudioFileTypes().map { it.extension.lowercase() }
        println("      decodable containers: $types")

        val rate = 44100f
        val seconds = 1
        val frames = (rate * seconds).toInt()
        val pcm = AudioFormat(rate, 16, 2, true, false)
        val data = ByteArray(frames * pcm.frameSize)
        val wav = JavaByteArrayOutputStream().apply {
            write("RIFF".toByteArray())
            write(intLe(36 + data.size))
            write("WAVEfmt ".toByteArray())
            write(intLe(16))
            write(shortLe(1))
            write(shortLe(2))
            write(intLe(rate.toInt()))
            write(intLe((rate * pcm.frameSize).toInt()))
            write(shortLe(pcm.frameSize.toShort()))
            write(shortLe(16))
            write("data".toByteArray())
            write(intLe(data.size))
            write(data)
        }.toByteArray()

        val stream = AudioSystem.getAudioInputStream(ByteArrayInputStream(wav))
        val format = stream.format
        if (format.sampleRate != rate) error("sample rate $format")
        var total = 0L
        val buffer = ByteArray(8192)
        while (true) {
            val read = stream.read(buffer)
            if (read < 0) break
            total += read
        }
        if (total != data.size.toLong()) error("read $total of ${data.size}")
    }

    check("real MP3 file decodes to PCM through Decoders") {
        val candidates = listOf(
            java.io.File("testdata/sample.mp3"),
            java.io.File("../testdata/sample.mp3"),
            java.io.File(System.getProperty("user.dir"), "testdata/sample.mp3")
        )
        val file = candidates.firstOrNull { it.exists() }
        if (file == null) {
            println("      testdata/sample.mp3 not found - skipped")
        } else {
            file.inputStream().use { raw ->
                val pcmStream = Decoders.openPcm(raw)
                val format = pcmStream.format
                println("      container=${Decoders.lastContainer} format=$format")
                if (format.encoding != AudioFormat.Encoding.PCM_SIGNED) error("not PCM: $format")
                if (format.sampleSizeInBits != 16) error("not 16 bit: $format")
                if (format.isBigEndian) error("not little endian: $format")
                var total = 0L
                val buffer = ByteArray(8192)
                while (true) {
                    val read = pcmStream.read(buffer)
                    if (read < 0) break
                    total += read
                }
                val seconds =
                    total.toDouble() / (format.frameSize * format.sampleRate)
                println("      decoded $total bytes = $seconds s")
                if (seconds < 0.5) error("decoded only $seconds s")
            }
        }
    }

    check("real FLAC file decodes to PCM through Decoders") {
        val file = listOf(
            java.io.File("testdata/sample.flac"),
            java.io.File("../testdata/sample.flac")
        ).firstOrNull { it.exists() }
        if (file == null) {
            println("      testdata/sample.flac not found - skipped")
        } else {
            file.inputStream().use { raw ->
                val pcmStream = Decoders.openPcm(raw)
                val format = pcmStream.format
                println("      container=${Decoders.lastContainer} format=$format")
                if (Decoders.lastContainer != Decoders.Container.FLAC) {
                    error("expected FLAC, got ${Decoders.lastContainer}")
                }
                if (format.encoding != AudioFormat.Encoding.PCM_SIGNED) error("not PCM: $format")
                if (format.sampleSizeInBits != 16) error("not 16 bit: $format")
                var total = 0L
                val buffer = ByteArray(8192)
                while (true) {
                    val read = pcmStream.read(buffer)
                    if (read < 0) break
                    total += read
                }
                val seconds = total.toDouble() / (format.frameSize * format.sampleRate)
                println("      decoded $total bytes = $seconds s")
                if (seconds < 1.0) error("decoded only $seconds s")
            }
        }
    }

    check("container sniffing recognises Deezer formats") {
        val flac = "fLaC".toByteArray(Charsets.ISO_8859_1)
        if (Decoders.sniff(flac, 4) != Decoders.Container.FLAC) error("flac not detected")
        val sync = byteArrayOf(0xFF.toByte(), 0xFB.toByte(), 0x90.toByte(), 0x00)
        if (Decoders.sniff(sync, 4) != Decoders.Container.MP3) error("frame sync not detected")
        val id3 = "ID3".toByteArray(Charsets.ISO_8859_1)
        if (Decoders.sniff(id3, 4) != Decoders.Container.MP3) error("id3 not detected")
        val riff = "RIFFxxxx".toByteArray(Charsets.ISO_8859_1)
        if (Decoders.sniff(riff, 8) != Decoders.Container.WAV) error("wav not detected")
    }

    check("audio output line availability is reported, not crashed on") {
        val pcm = AudioFormat(44100f, 16, 2, true, false)
        val info = DataLine.Info(SourceDataLine::class.java, pcm)
        val supported = AudioSystem.isLineSupported(info)
        val mixers = AudioSystem.getMixerInfo().size
        println("      output line supported=$supported, mixers=$mixers")
    }

    // The quality pill next to the player shows what the decoder really got,
    // not what the quality setting asked for, so its formatting has to be right.
    check("stream quality label reports container, rate and channels") {
        val flac = StreamQuality(
            container = Decoders.Container.FLAC,
            sampleRate = 44100,
            channels = 2,
            bitsPerSample = 16
        )
        println("      label=${flac.label}")
        if (flac.label != "FLAC · 44.1 kHz · 2ch") error("unexpected label: ${flac.label}")

        val mp3 = StreamQuality(Decoders.Container.MP3, 48000, 1, 16)
        if (mp3.label != "MP3 · 48 kHz · 1ch") error("unexpected label: ${mp3.label}")

        val unknown = StreamQuality(Decoders.Container.UNKNOWN, 0, 0, 0)
        if (unknown.label != null) error("unknown should render nothing: ${unknown.label}")
    }

    check("decoder reports the container it actually opened") {
        val file = listOf(
            java.io.File("testdata/sample.flac"),
            java.io.File("../testdata/sample.flac")
        ).firstOrNull { it.exists() }
        if (file == null) {
            println("      testdata/sample.flac not found - skipped")
        } else {
            file.inputStream().use { raw ->
                val decoded = Decoders.open(raw)
                println("      container=${decoded.container} last=${Decoders.lastContainer}")
                if (decoded.container != Decoders.Container.FLAC) {
                    error("expected FLAC, got ${decoded.container}")
                }
                if (Decoders.lastContainer != decoded.container) error("lastContainer not updated")
                decoded.stream.close()
            }
        }
    }

    // ------------------------------------------------------------- extension

    check("DeezerExtension instantiates and accepts settings") {
        val prefs = PrefsSettings(java.io.File(AppPaths.root, "selfcheck4.properties"))
        val client = DeezerClient(prefs)
        client.extension.setSettings(prefs)
        if (client.isLoggedIn()) error("should not be logged in")
        prefs.file.delete()
    }

    // ------------------------------------------------------- media controls

    if (isWindows) check("Windows media session (SMTC) accepts metadata and artwork") {
        val prefs = PrefsSettings(java.io.File(AppPaths.root, "selfcheck5.properties"))
        val player = PlayerController(
            DeezerClient(prefs),
            DownloadManager(DeezerClient(prefs), kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default), prefs),
            HistoryStore(),
            prefs,
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default)
        )
        val session = WindowsMediaSession(
            settings = prefs,
            onPlay = { player.resumeFromSystem() },
            onPause = { player.pauseFromSystem() },
            onStop = { player.stop() },
            onNext = { player.next() },
            onPrevious = { player.previous() },
            onSeek = { player.seekTo(it) },
            onShuffle = { player.applyShuffleMode(it) },
            onLoop = { player.applyRepeatMode(it) }
        )
        session.start()
        val deadline = System.currentTimeMillis() + 8000
        while (System.currentTimeMillis() < deadline && session.status == UiStrings.s.mediaStarting) {
            Thread.sleep(50)
        }
        println("      status=${session.status} available=${session.available}")
        if (!session.available) error("SMTC bridge did not come up: ${session.status}")

        // Artwork: the native side loads thumbnails from a real file path.
        val art = java.io.File(AppPaths.images, "selfcheck-art.png")
        java.awt.image.BufferedImage(64, 64, java.awt.image.BufferedImage.TYPE_INT_RGB).let {
            javax.imageio.ImageIO.write(it, "png", art)
        }
        val track = dev.brahmkshatriya.echo.common.models.Track(
            id = "selfcheck-track",
            title = "Self check",
            artists = emptyList(),
            duration = 180_000L
        )
        session.onTrackChanged(track, true)
        session.onPositionChanged(30_000L, 180_000L)
        Thread.sleep(4000)
        println("      after publish: status=${session.status} available=${session.available}")
        session.close()
        art.delete()
        prefs.file.delete()
    }

    println()
    if (failures == 0) {
        println("All checks passed.")
    } else {
        println("$failures check(s) failed.")
        kotlin.system.exitProcess(1)
    }
}

private fun intLe(value: Int) = byteArrayOf(
    (value and 0xFF).toByte(),
    ((value shr 8) and 0xFF).toByte(),
    ((value shr 16) and 0xFF).toByte(),
    ((value shr 24) and 0xFF).toByte()
)

/**
 * Long running probe used to verify the Windows integration from the outside:
 * it publishes a fake track so the shell can see the session, then logs every
 * media-key callback until the JVM is killed.
 */
private fun mediaProbe() {
    val prefs = PrefsSettings(java.io.File(AppPaths.root, "selfcheck-probe.properties"))
    val track = Track(
        id = "probe-track",
        title = "Gladix SMTC probe",
        artists = listOf(Artist(id = "probe-artist", name = "Probe Artist")),
        cover = "https://picsum.photos/id/1025/500/500.jpg"
            .toImageHolder(),
        duration = 240_000L
    )
    val session = WindowsMediaSession(
        settings = prefs,
        onPlay = { println("PROBE CALLBACK play") },
        onPause = { println("PROBE CALLBACK pause") },
        onStop = { println("PROBE CALLBACK stop") },
        onNext = { println("PROBE CALLBACK next") },
        onPrevious = { println("PROBE CALLBACK previous") },
        onSeek = { println("PROBE CALLBACK seek $it") },
        onShuffle = { println("PROBE CALLBACK shuffle $it") },
        onLoop = { println("PROBE CALLBACK loop $it") }
    )
    session.start()
    val deadline = System.currentTimeMillis() + 5000
    while (System.currentTimeMillis() < deadline && session.status == UiStrings.s.mediaStarting) Thread.sleep(50)
    println("PROBE STATUS ${session.status} available=${session.available}")
    session.onTrackChanged(track, true)
    session.onModesChanged(false, dev.gladix.desktop.core.player.RepeatMode.ALL)
    println("PROBE READY")
    var position = 0L
    while (true) {
        Thread.sleep(1000)
        position += 1000
        session.onPositionChanged(position, 240_000L)
    }
}

private fun shortLe(value: Short) = byteArrayOf(
    (value.toInt() and 0xFF).toByte(),
    ((value.toInt() shr 8) and 0xFF).toByte()
)