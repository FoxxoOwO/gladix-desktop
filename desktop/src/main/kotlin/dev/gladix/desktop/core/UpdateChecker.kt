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
 * Checks GitHub Releases for a newer build and downloads its Windows zip.
 *
 * Everything is optional: without network access, without a signed-in GitHub
 * account (the anonymous API is rate limited but sufficient) or on a build with
 * an unknown version the checker simply reports "up to date".
 */
class UpdateChecker(
    private val settings: PrefsSettings,
    private val scope: CoroutineScope
) {

    enum class State { Idle, Checking, UpToDate, Available, Downloading, Downloaded, Failed }

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

    private val json = Json { ignoreUnknownKeys = true }

    /** True when a newer release than [AppVersion.NAME] is known. */
    val hasUpdate: Boolean
        get() = latestVersion?.let { compareVersions(it, AppVersion.NAME) > 0 } == true

    /**
     * Starts the periodic check. The first run is delayed a little so it does
     * not compete with the startup feed requests.
     */
    fun startPeriodicCheck() {
        if (settings.getBoolean(PrefsSettings.KEY_UPDATE_CHECK) == false) return
        scope.launch(Dispatchers.IO) {
            delay(15_000)
            while (isActive) {
                runCatching { check() }
                delay(6 * 60 * 60 * 1000L) // every 6 hours
            }
        }
    }

    /** Asks the GitHub API for the newest release. Safe to call repeatedly. */
    suspend fun check(): Unit = withContext(Dispatchers.IO) {
        if (state == State.Checking) return@withContext
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
            downloadUrl = pickAsset(release["assets"] as? JsonArray)
            state = if (hasUpdate) State.Available else State.UpToDate
        }.onFailure {
            state = State.Failed
            error = it.message ?: it::class.simpleName
        }
    }

    /** Downloads the release zip into the app data folder. */
    fun download() {
        val url = downloadUrl ?: return
        if (state == State.Downloading) return
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
                downloadedFile = it
                downloadProgress = 1f
                state = State.Downloaded
            }.onFailure {
                state = State.Failed
                error = it.message ?: it::class.simpleName
            }
        }
    }

    /** Opens the folder holding the downloaded zip, plus its parent. */
    fun openDownloadFolder() {
        val dir = downloadedFile?.parentFile ?: File(AppPaths.root, "updates")
        runCatching { java.awt.Desktop.getDesktop().open(dir) }
    }

    private fun pickAsset(assets: JsonArray?): String? {
        if (assets == null) return null
        val entries = assets.mapNotNull { it as? JsonObject }
        val named = entries.firstOrNull { (it.string("name") ?: "").endsWith("-windows-x64.zip") }
        return named?.string("browser_download_url")
            ?: entries.firstOrNull()?.string("browser_download_url")
    }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull
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
