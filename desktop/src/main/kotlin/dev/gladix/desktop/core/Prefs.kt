package dev.gladix.desktop.core

import dev.brahmkshatriya.echo.common.settings.Settings
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Root folder for all app data: %APPDATA%/Gladix on Windows.
 */
object AppPaths {
    val root: File = run {
        val appData = System.getenv("APPDATA")
        val base = if (appData != null) File(appData) else File(System.getProperty("user.home"))
        File(base, "Gladix").apply { mkdirs() }
    }

    val downloads: File = File(root, "downloads").apply { mkdirs() }
    val images: File = File(root, "images").apply { mkdirs() }

    val settingsFile = File(root, "settings.properties")
    val userFile = File(root, "user.json")
    val historyFile = File(root, "history.json")
    val queueFile = File(root, "queue.json")
    val downloadsFile = File(root, "downloads.json")
}

val AppJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    prettyPrint = true
}

/**
 * [Settings] implementation backed by a plain java.util.Properties file.
 * Everything is stored as a String; typed getters convert on read.
 */
class PrefsSettings(val file: File = AppPaths.settingsFile) : Settings {

    private val props = java.util.Properties()
    private val lock = Any()

    init {
        load()
    }

    private fun load() {
        if (!file.exists()) return
        file.inputStream().use { props.load(it) }
    }

    private fun save() {
        file.parentFile?.mkdirs()
        file.outputStream().use { props.store(it, "Gladix settings") }
    }

    private fun set(key: String, value: String?) {
        synchronized(lock) {
            if (value == null) props.remove(key) else props[key] = value
            save()
        }
    }

    private fun raw(key: String): String? = synchronized(lock) { props.getProperty(key) }

    override fun getString(key: String): String? = raw(key)
    override fun putString(key: String, value: String?) = set(key, value)

    override fun getStringSet(key: String): Set<String>? =
        raw(key)?.let { if (it.isEmpty()) emptySet() else it.split(SET_SEPARATOR).toSet() }

    override fun putStringSet(key: String, value: Set<String>?) =
        set(key, value?.joinToString(SET_SEPARATOR))

    override fun getInt(key: String): Int? = raw(key)?.toIntOrNull()
    override fun putInt(key: String, value: Int?) = set(key, value?.toString())

    override fun getBoolean(key: String): Boolean? = raw(key)?.toBooleanStrictOrNull()
    override fun putBoolean(key: String, value: Boolean?) = set(key, value?.toString())

    /** Reads a string, falling back to [fallback] when the key is unset. */
    fun getStringOrDefault(key: String, fallback: String): String = raw(key) ?: fallback

    companion object {
        private const val SET_SEPARATOR = "\u0001"

        const val KEY_QUALITY = "desktop_quality"
        const val QUALITY_FLAC = "flac"
        const val QUALITY_320 = "320"
        const val QUALITY_128 = "128"

        const val KEY_VOLUME = "desktop_volume"
        const val KEY_REPEAT = "desktop_repeat"
        const val KEY_SHUFFLE = "desktop_shuffle"
        const val KEY_LOGGING = "log"
        const val KEY_IMAGE_QUALITY = "image_quality"
        const val KEY_COUNTRY = "country"
        const val KEY_LANGUAGE = "lang"
        const val KEY_PROXY = "proxy"
        const val KEY_SEARCH_HISTORY = "history"
        const val KEY_MEDIA_SESSION = "desktop_media_session"
        const val KEY_MEDIA_KEYS = "desktop_media_keys"

        /** UI language of the app itself (`cs`/`en`), not the Deezer content language. */
        const val KEY_UI_LANG = "desktop_ui_lang"
    }
}

/** Small helper for the JSON files that hold app data. */
inline fun <reified T> readJsonFile(file: File, default: T): T {
    if (!file.exists()) return default
    return runCatching { AppJson.decodeFromString<T>(file.readText()) }.getOrDefault(default)
}

inline fun <reified T> writeJsonFile(file: File, value: T) {
    runCatching {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(AppJson.encodeToString(value))
        if (file.exists()) file.delete()
        tmp.renameTo(file)
    }
}