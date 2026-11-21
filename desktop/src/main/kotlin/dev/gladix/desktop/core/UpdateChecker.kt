package dev.gladix.desktop.core

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.io.File

/**
 * Checks GitHub Releases for a newer build and downloads the zip of this
 * platform.
 *
 * Everything is optional: without network access, without a signed-in GitHub
 * account (the anonymous API is rate limited but sufficient) or on a build with
 * an unknown version the checker simply reports "up to date".
 */
class UpdateChecker(
    private val settings: PrefsSettings,
    private val scope: CoroutineScope
) {

    enum class State {
        Idle, Checking, UpToDate, Available, Downloading,
        Extracting, Ready, Downloaded, Applying, Failed
    }

    var state by mutableStateOf(State.Idle)
        private set

    /** Version of the release found by the last successful check. */
    var latestVersion by mutableStateOf<String?>(null)
        private set
    var downloadUrl by mutableStateOf<String?>(null)
        private set
    var releaseNotes by mutableStateOf<String?>(null)
        private set
    var downloadProgress by mutableStateOf(0f)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    /** File of the downloaded zip, once [download] finished. */
    var downloadedFile by mutableStateOf<File?>(null)
        private set

    /** Unpacked app image, ready to be swapped into the install folder. */
    var stagedImage by mutableStateOf<File?>(null)
        private set

    // Versions of [stagedImage] and [downloadedFile]; a check never discards a
    // prepared update just because it runs again, but must not keep the wrong one.
    private var stagedVersion: String? = null
    private var downloadedVersion: String? = null

    /** Called by the UI to shut the app down after [applyAndRestart]. */
    var onExitRequest: (() -> Unit)? = null

    private val selfUpdater = SelfUpdater()

    private val json = Json { ignoreUnknownKeys = true }

    /** Automatic mode is the default; the settings switch only mutes it. */
    private val autoEnabled: Boolean
        get() = settings.getBoolean(PrefsSettings.KEY_UPDATE_CHECK) != false

    /** True when a newer release than [AppVersion.NAME] is known. */
    val hasUpdate: Boolean
        get() = latestVersion?.let { compareVersions(it, AppVersion.NAME) > 0 } == true

    /** True while a check, a download or a swap is in flight. */
    val busy: Boolean
        get() = blocksCheck(state)

    /**
     * Starts the periodic check. The switch is read on every round, so turning
     * it off stops the requests and turning it on starts them without a
     * restart; while it is off the loop only re-reads the setting.
     */
    fun startPeriodicCheck() {
        scope.launch(Dispatchers.IO) {
            // A previous run may have left an unapplied staging copy or the
            // backup folder of a swap that could not delete it.
            runCatching { selfUpdater.cleanUp() }
            delay(15_000)
            while (isActive) {
                if (autoEnabled) {
                    runCatching { check() }
                    delay(6 * 60 * 60 * 1000L) // every 6 hours
                } else {
                    delay(60_000L)
                }
            }
        }
    }

    /** Asks the GitHub API for the newest release. Safe to call repeatedly. */
    suspend fun check(): Unit = withContext(Dispatchers.IO) {
        // A check started mid-download would reset the state and let the auto
        // path begin a second download into the same file.
        if (busy) return@withContext
        state = State.Checking
        error = null
        val result = runCatching {
            val request = okhttp3.Request.Builder()
                .url("https://api.github.com/repos/${AppVersion.REPO}/releases/latest")
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "Gladix/${AppVersion.NAME}")
                .build()
            Http.client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("HTTP ${response.code}")
                response.body.string()
            }
        }
        result.onSuccess { body ->
            val release = runCatching { json.parseToJsonElement(body) as JsonObject }.getOrNull()
            if (release == null) {
                state = State.Failed
                error = "Malformed release payload"
                return@onSuccess
            }
            val tag = release.string("tag_name")
            latestVersion = tag?.trimStart('v')
            releaseNotes = release.string("body")
            downloadUrl = pickReleaseAsset(release["assets"] as? JsonArray)
            state = readyState(hasUpdate, latestVersion, stagedVersion, downloadedVersion)
            // Fully automatic path: while the switch is on, fetch and unpack
            // the release right away so the user only confirms the restart.
            // An already prepared update is not downloaded twice, and a build
            // that cannot swap its image (run from Gradle) keeps it manual.
            if (
                hasUpdate && autoEnabled && selfUpdater.isSupported &&
                downloadUrl != null && state == State.Available
            ) {
                download()
            }
        }.onFailure {
            state = State.Failed
            error = it.message ?: it::class.simpleName
        }
    }

    /** Downloads the release zip into the app data folder. */
    fun download() {
        val url = downloadUrl ?: return
        if (state == State.Downloading || state == State.Extracting || state == State.Ready) return
        if (state == State.Applying) return
        state = State.Downloading
        downloadProgress = 0f
        scope.launch(Dispatchers.IO) {
            val target = File(AppPaths.root, "updates").apply { mkdirs() }
            val name = url.substringAfterLast('/')
            val file = File(target, name)
            val result = runCatching {
                val request = okhttp3.Request.Builder()
                    .url(url)
                    .header("User-Agent", "Gladix/${AppVersion.NAME}")
                    .build()
                Http.client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) error("HTTP ${response.code}")
                    val total = response.body.contentLength()
                    response.body.byteStream().use { input ->
                        file.outputStream().use { out ->
                            val buffer = ByteArray(128 * 1024)
                            var written = 0L
                            while (true) {
                                val read = input.read(buffer)
                                if (read < 0) break
                                out.write(buffer, 0, read)
                                written += read
                                downloadProgress = if (total > 0) {
                                    (written.toFloat() / total).coerceIn(0f, 1f)
                                } else {
                                    // Unknown size: creep towards 95 % so the bar moves.
                                    (1f - 1f / (1f + written / 8_000_000f)).coerceIn(0f, 0.95f)
                                }
                            }
                        }
                    }
                }
                file
            }
            result.onSuccess {
                val expected = expectedSha256(releaseNotes, it.name)
                if (expected != null && sha256(it) != expected) {
                    runCatching { it.delete() }
                    state = State.Failed
                    error = "checksum mismatch for ${it.name}"
                    return@onSuccess
                }
                downloadedFile = it
                downloadedVersion = latestVersion
                downloadProgress = 1f
                prepare(it)
            }.onFailure {
                state = State.Failed
                error = it.message ?: it::class.simpleName
            }
        }
    }

    /**
     * Unpacks the downloaded ZIP. On a packaged build this always ends in
     * [State.Ready]; anywhere else it degrades to [State.Downloaded] and the
     * folder can still be opened by hand.
     */
    private suspend fun prepare(zip: File) = withContext(Dispatchers.IO) {
        if (!selfUpdater.isSupported) {
            state = State.Downloaded
            return@withContext
        }
        state = State.Extracting
        runCatching { selfUpdater.stage(zip) }.onSuccess { image ->
            stagedImage = image
            stagedVersion = latestVersion
            state = State.Ready
            // The ZIP is no longer needed once the image is unpacked.
            runCatching { zip.delete() }
        }.onFailure {
            state = State.Failed
            error = it.message ?: it::class.simpleName
        }
    }

    /** Swaps the staged image in, then asks the UI to close the window. */
    fun applyAndRestart() {
        val image = stagedImage ?: return
        if (state == State.Applying) return
        if (!selfUpdater.apply(image, restart = true)) {
            state = State.Failed
            error = "update script could not be started"
            return
        }
        state = State.Applying
        onExitRequest?.invoke()
    }

    /**
     * Installs a staged update without relaunching, used when the window is
     * closed normally. The next start already runs the new version.
     */
    fun applyOnExit() {
        val image = stagedImage ?: return
        if (state != State.Ready) return
        runCatching { selfUpdater.apply(image, restart = false) }
    }

    /** Opens the folder holding the downloaded zip, plus its parent. */
    fun openDownloadFolder() {
        val dir = downloadedFile?.parentFile ?: File(AppPaths.root, "updates")
        runCatching { java.awt.Desktop.getDesktop().open(dir) }
    }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull
}

/**
 * Picks the release archive for this platform. Assets are named
 * `Gladix-<version>-<platform>.zip`, so the platform token has to appear in the
 * name; no fallback, because a wrong-OS download is worse than none.
 */
internal fun pickReleaseAsset(
    assets: JsonArray?,
    os: String = System.getProperty("os.name").orEmpty(),
    arch: String = System.getProperty("os.arch").orEmpty()
): String? {
    if (assets == null) return null
    val token = platformToken(os, arch) ?: return null
    val entry = assets.mapNotNull { it as? JsonObject }.firstOrNull {
        val name = (it["name"] as? JsonPrimitive)?.contentOrNull ?: ""
        // A `.sha256` or `.blockmap` next to the archive carries the same token.
        name.contains(token) && name.endsWith(".zip", ignoreCase = true)
    } ?: return null
    return (entry["browser_download_url"] as? JsonPrimitive)?.contentOrNull
}

/** `-windows`, `-linux`, `-macos-arm64` or `-macos-x64`. */
internal fun platformToken(os: String, arch: String): String? {
    val name = os.lowercase()
    val cpu = arch.lowercase()
    return when {
        name.contains("win") -> "-windows"
        name.contains("mac") || name.contains("darwin") ->
            if (cpu.contains("arm") || cpu.contains("aarch64")) "-macos-arm64" else "-macos-x64"
        name.contains("linux") -> "-linux"
        else -> null
    }
}

/**
 * Compares dotted numeric versions (`1.3.0` vs `1.10.2`). Missing components
 * count as zero, so `1.3` equals `1.3.0`. Non-numeric suffixes are ignored.
 */
fun compareVersions(a: String, b: String): Int {
    fun parts(v: String) = v.trim().trimStart('v')
        .split('.', '-', '+')
        .map { part -> part.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }

    val left = parts(a)
    val right = parts(b)
    val size = maxOf(left.size, right.size)
    for (index in 0 until size) {
        val l = left.getOrElse(index) { 0 }
        val r = right.getOrElse(index) { 0 }
        if (l != r) return l.compareTo(r)
    }
    return 0
}

/** A check must not start while one is running or a download/swap is using it. */
internal fun blocksCheck(state: UpdateChecker.State): Boolean = when (state) {
    UpdateChecker.State.Checking, UpdateChecker.State.Downloading,
    UpdateChecker.State.Extracting, UpdateChecker.State.Applying -> true

    else -> false
}

/**
 * State a finished check shows. A version already staged or downloaded for this
 * exact release survives the next check instead of being fetched again.
 */
internal fun readyState(
    hasUpdate: Boolean,
    latest: String?,
    stagedVersion: String?,
    downloadedVersion: String?
): UpdateChecker.State = when {
    !hasUpdate -> UpdateChecker.State.UpToDate
    stagedVersion != null && stagedVersion == latest -> UpdateChecker.State.Ready
    downloadedVersion != null && downloadedVersion == latest -> UpdateChecker.State.Downloaded
    else -> UpdateChecker.State.Available
}

/**
 * SHA-256 listed for [fileName] in the release notes, if the notes contain the
 * `SHA-256` block the release workflow writes (`<hash>  <name>` per line).
 */
internal fun expectedSha256(notes: String?, fileName: String): String? =
    notes.orEmpty().lineSequence()
        .map { it.trim() }
        .mapNotNull { line ->
            val parts = line.split("  ").filter { it.isNotBlank() }
            val hash = parts.getOrNull(0).orEmpty()
            if (
                parts.size == 2 && hash.length == 64 &&
                hash.all { it in "0123456789abcdefABCDEF" } && parts[1] == fileName
            ) hash.lowercase() else null
        }
        .firstOrNull()

/** Lowercase hex SHA-256 of a file, streamed so a 100 MB zip is fine. */
internal fun sha256(file: File): String {
    val digest = java.security.MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(128 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

