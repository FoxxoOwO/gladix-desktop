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
import dev.gladix.desktop.core.HistoryStore
import dev.gladix.desktop.core.Lrclib
import dev.gladix.desktop.core.PrefsSettings
import dev.gladix.desktop.core.platform.AppIdentity
import dev.gladix.desktop.core.platform.WindowsMediaSession
import dev.gladix.desktop.core.player.PlayerController
import dev.gladix.desktop.core.player.Decoders
import dev.gladix.desktop.core.player.SavedQueue
import dev.gladix.desktop.core.player.StreamQuality
import dev.gladix.desktop.ui.SortField
import dev.gladix.desktop.ui.SortSpec
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

    check("Windows media session (SMTC) accepts metadata and artwork") {
        val prefs = PrefsSettings(java.io.File(AppPaths.root, "selfcheck5.properties"))
        val player = PlayerController(
            DeezerClient(prefs),
            DownloadManager(DeezerClient(prefs), kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default)),
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