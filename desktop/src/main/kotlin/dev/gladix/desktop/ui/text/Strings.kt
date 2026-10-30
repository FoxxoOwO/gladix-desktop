package dev.gladix.desktop.ui.text

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** UI languages the app ships with. The [code] is what gets persisted. */
enum class Lang(val code: String, val nativeName: String) {
    Cs("cs", "Čeština"),
    En("en", "English");

    companion object {
        val default: Lang = Cs

        fun fromCode(code: String?): Lang =
            entries.firstOrNull { it.code.equals(code?.trim(), ignoreCase = true) } ?: default
    }
}

/**
 * Every piece of user-visible text. Screens read it through [UiStrings.s], which
 * is backed by snapshot state, so switching the language recomposes the UI
 * without restarting the app.
 *
 * Strings with parameters are functions instead of templates so that neither
 * language has to know about the other's word order.
 */
interface Strings {
    // Navigation rail
    val navHome: String
    val navSearch: String
    val navLibrary: String
    val navDownloads: String
    val navHistory: String
    val navQueue: String
    val navSettings: String

    // Shared widgets
    val retry: String
    val showAll: String
    val play: String
    val back: String
    val remove: String
    val clear: String
    fun error(message: String?): String

    // Home
    fun greeting(name: String): String
    val listenerFallback: String
    val recommendedByDeezer: String

    // Search
    val noCategoryContent: String
    val searchNotLoggedInHint: String
    val searchFieldLabel: String

    // Item / album / artist screens
    fun playingNow(title: String): String
    val unsupportedContent: String
    fun nowFollowing(name: String): String
    fun stoppedFollowing(name: String): String
    val follow: String
    val shuffle: String
    val like: String
    val savedToLibrary: String
    val removedFromLibrary: String
    val saveToLibrary: String
    val downloadAll: String
    val radio: String
    fun trackCount(count: Long): String
    fun downloading(count: Int): String
    val radioUnavailable: String
    fun radioTracks(count: Int): String
    val linkUnavailable: String
    val linkCopied: String
    val share: String
    val removeDownloaded: String
    fun downloadFailed(message: String?): String
    val download: String
    val artistLabel: String

    // Library
    val downloadedTracks: String
    val nothingDownloaded: String
    val nothingPlayed: String
    val clearQueue: String
    val queueEmpty: String
    val nothingPlaying: String
    val lyrics: String
    val lyricsUnavailable: String

    // Login
    fun loggedInAs(name: String): String
    val loginFailed: String
    val loginSubtitle: String
    val arlPlaceholder: String
    val arlHint: String
    val arlCookie: String
    val emailAndPassword: String
    val email: String
    val password: String
    val twoFactorHint: String
    val signingIn: String
    val signIn: String
    val appTagline: String

    // Player bar
    val nothingPlayingShort: String
    val previous: String
    val next: String
    val repeat: String
    val volume: String

    // Settings
    val settingsTitle: String
    val sectionAppearance: String
    val uiLanguage: String
    val sectionPlayback: String
    val streamQuality: String
    val qualityNote: String
    val sectionWindows: String
    val controlFromWindows: String
    val controlFromWindowsNote: String
    val mediaSessionOff: String
    val sectionAccount: String
    val logPlays: String
    val logPlaysNote: String
    val countryLabel: String
    val deezerLanguageLabel: String
    val proxyLabel: String
    val changesAfterRestart: String
    val sectionData: String
    fun dataFolder(path: String): String
    fun downloadsFolder(path: String): String
    val openFolder: String
    val clearHistory: String
    val historyCleared: String
    val sectionUser: String
    val notLoggedIn: String
    val signOut: String
    val sectionAbout: String
    val aboutLine: String
    val aboutCredits: String

    // Windows media integration status
    val mediaStarting: String
    val mediaUnavailable: String
    val smtcUnavailable: String
    val mediaActive: String
    val mediaActiveNoArt: String
    val mediaDisabled: String
    val mediaDisabledNote: String

    // Audio pipeline errors
    fun decodeFailed(container: String, details: String): String
    fun convertFailed(encoding: String, container: String): String
}

private object CsStrings : Strings {
    override val navHome = "Domů"
    override val navSearch = "Hledat"
    override val navLibrary = "Knihovna"
    override val navDownloads = "Stažené"
    override val navHistory = "Historie"
    override val navQueue = "Fronta"
    override val navSettings = "Nastavení"

    override val retry = "Zkusit znovu"
    override val showAll = "zobrazit vše"
    override val play = "přehrát"
    override val back = "Zpět"
    override val remove = "Odebrat"
    override val clear = "Vymazat"
    override fun error(message: String?) = "Chyba: ${message ?: "neznámá chyba"}"

    override fun greeting(name: String) = "Dobrý den, $name"
    override val listenerFallback = "posluchači"
    override val recommendedByDeezer = "Doporučeno z Deezeru"

    override val noCategoryContent = "Tato kategorie nemá obsah."
    override val searchNotLoggedInHint = "Napiš název skladby, alba nebo interpreta."
    override val searchFieldLabel = "Hledat na Deezeru"

    override fun playingNow(title: String) = "Přehrávám $title"
    override val unsupportedContent = "Tento typ obsahu zatím není podporovaný."
    override fun nowFollowing(name: String) = "Sleduješ $name"
    override fun stoppedFollowing(name: String) = "Přestal jsi sledovat $name"
    override val follow = "Sledovat"
    override val shuffle = "Náhodně"
    override val like = "Líbí se mi"
    override val savedToLibrary = "Uloženo do knihovny"
    override val removedFromLibrary = "Odebráno z knihovny"
    override val saveToLibrary = "Uložit do knihovny"
    override val downloadAll = "Stáhnout vše"
    override val radio = "Rádio"
    override fun trackCount(count: Long) = "$count skladeb"
    override fun downloading(count: Int) = "Stahuji $count skladeb"
    override val radioUnavailable = "Rádio není pro tuto položku dostupné"
    override fun radioTracks(count: Int) = "Rádio: $count skladeb"
    override val linkUnavailable = "Odkaz se nepodařilo získat"
    override val linkCopied = "Odkaz zkopírován do schránky"
    override val share = "Sdílet"
    override val removeDownloaded = "Odstranit stažené"
    override fun downloadFailed(message: String?) = "Stažení selhalo: ${message ?: "?"}"
    override val download = "Stáhnout"
    override val artistLabel = "Interpret"

    override val downloadedTracks = "Stažené skladby"
    override val nothingDownloaded = "Zatím nic staženého. Stahuj tlačítkem u skladby nebo alba."
    override val nothingPlayed = "Zatím jsi nic nepřehrál."
    override val clearQueue = "Vyprázdnit frontu"
    override val queueEmpty = "Fronta je prázdná."
    override val nothingPlaying = "Nic se nepřehrává."
    override val lyrics = "Text písně"
    override val lyricsUnavailable = "Pro tuto skladbu není dostupný text."

    override fun loggedInAs(name: String) = "Přihlášeno jako $name"
    override val loginFailed = "Přihlášení se nezdařilo"
    override val loginSubtitle = "Přihlas se ke svému účtu Deezer"
    override val arlPlaceholder = "např. 1234abcd..."
    override val arlHint = "ARL najdeš v prohlížeči: deezer.com → F12 → Application → Cookies → arl."
    override val arlCookie = "ARL cookie"
    override val emailAndPassword = "E-mail a heslo"
    override val email = "E-mail"
    override val password = "Heslo"
    override val twoFactorHint = "U účtů s dvoufázovým ověřením použij ARL cookie."
    override val signingIn = "Přihlašuji…"
    override val signIn = "Přihlásit se"
    override val appTagline = "Gladix pro Windows"

    override val nothingPlayingShort = "Nic se nepřehrává"
    override val previous = "Předchozí"
    override val next = "Další"
    override val repeat = "Opakování"
    override val volume = "Zvuk"

    override val settingsTitle = "Nastavení"
    override val sectionAppearance = "Vzhled"
    override val uiLanguage = "Jazyk aplikace"
    override val sectionPlayback = "Přehrávání"
    override val streamQuality = "Kvalita streamu"
    override val qualityNote = "Když zvolená kvalita není dostupná, přehrávač použije nejbližší " +
        "nižší. Stažené skladby se přehrávají vždy z disku."
    override val sectionWindows = "Windows a mediální klávesy"
    override val controlFromWindows = "Ovládat přehrávání z Windows"
    override val controlFromWindowsNote = "Přehrávání se zobrazí v rychlých nastaveních a na " +
        "zamykací obrazovce. Mediální klávesy na klávesnici (přehrát/pauza, další, předchozí) " +
        "pak ovládají Gladix."
    override val mediaSessionOff = "Vypnuto – Gladix se ve Windows nezobrazuje."
    override val sectionAccount = "Deezer účet"
    override val logPlays = "Logovat přehrání na Deezeru"
    override val logPlaysNote = "Skladby se započítají do doporučení a statistik."
    override val countryLabel = "Země (např. CZ)"
    override val deezerLanguageLabel = "Jazyk (např. cs)"
    override val proxyLabel = "Proxy (volitelné, host:port)"
    override val changesAfterRestart = "Změny se projeví po restartu aplikace."
    override val sectionData = "Data a úložiště"
    override fun dataFolder(path: String) = "Složka s daty: $path"
    override fun downloadsFolder(path: String) = "Stažené skladby: $path"
    override val openFolder = "Otevřít složku"
    override val clearHistory = "Vymazat historii"
    override val historyCleared = "Historie vymazána"
    override val sectionUser = "Účet"
    override val notLoggedIn = "Nepřihlášeno"
    override val signOut = "Odhlásit se"
    override val sectionAbout = "O aplikaci"
    override val aboutLine = "Gladix pro Windows – neoficiální Deezer klient."
    override val aboutCredits = "Postaveno na open-source jádru Echo (brahmkshatriya) a rozšíření " +
        "Deezer z projektu Gladix (rschwertley). Licence: Unabandon Public License."

    override val mediaStarting = "Spouštím…"
    override val mediaUnavailable = "Nedostupné"
    override val smtcUnavailable = "SMTC není na tomto systému dostupný"
    override val mediaActive = "Aktivní – přehrávání se zobrazuje ve Windows"
    override val mediaActiveNoArt = "Aktivní (bez obalu alba)"
    override val mediaDisabled = "Vypnuto"
    override val mediaDisabledNote = "Vypnuto – Gladix se ve Windows nezobrazuje."

    override fun decodeFailed(container: String, details: String) =
        "Nepodařilo se dekódovat audio ($container): $details"
    override fun convertFailed(encoding: String, container: String) =
        "Nelze převést $encoding na PCM ($container)"
}

private object EnStrings : Strings {
    override val navHome = "Home"
    override val navSearch = "Search"
    override val navLibrary = "Library"
    override val navDownloads = "Downloads"
    override val navHistory = "History"
    override val navQueue = "Queue"
    override val navSettings = "Settings"

    override val retry = "Try again"
    override val showAll = "show all"
    override val play = "play"
    override val back = "Back"
    override val remove = "Remove"
    override val clear = "Clear"
    override fun error(message: String?) = "Error: ${message ?: "unknown error"}"

    override fun greeting(name: String) = "Hello, $name"
    override val listenerFallback = "listener"
    override val recommendedByDeezer = "Recommended by Deezer"

    override val noCategoryContent = "This category has no content."
    override val searchNotLoggedInHint = "Type the name of a track, album or artist."
    override val searchFieldLabel = "Search Deezer"

    override fun playingNow(title: String) = "Playing $title"
    override val unsupportedContent = "This kind of content is not supported yet."
    override fun nowFollowing(name: String) = "Following $name"
    override fun stoppedFollowing(name: String) = "Stopped following $name"
    override val follow = "Follow"
    override val shuffle = "Shuffle"
    override val like = "Like"
    override val savedToLibrary = "Saved to library"
    override val removedFromLibrary = "Removed from library"
    override val saveToLibrary = "Save to library"
    override val downloadAll = "Download all"
    override val radio = "Radio"
    override fun trackCount(count: Long) = if (count == 1L) "1 track" else "$count tracks"
    override fun downloading(count: Int) =
        if (count == 1) "Downloading 1 track" else "Downloading $count tracks"
    override val radioUnavailable = "Radio is not available for this item"
    override fun radioTracks(count: Int) = "Radio: $count tracks"
    override val linkUnavailable = "Could not get the link"
    override val linkCopied = "Link copied to clipboard"
    override val share = "Share"
    override val removeDownloaded = "Remove download"
    override fun downloadFailed(message: String?) = "Download failed: ${message ?: "?"}"
    override val download = "Download"
    override val artistLabel = "Artist"

    override val downloadedTracks = "Downloaded tracks"
    override val nothingDownloaded = "Nothing downloaded yet. Use the button next to a track or album."
    override val nothingPlayed = "You have not played anything yet."
    override val clearQueue = "Clear queue"
    override val queueEmpty = "The queue is empty."
    override val nothingPlaying = "Nothing is playing."
    override val lyrics = "Lyrics"
    override val lyricsUnavailable = "No lyrics available for this track."

    override fun loggedInAs(name: String) = "Signed in as $name"
    override val loginFailed = "Sign in failed"
    override val loginSubtitle = "Sign in to your Deezer account"
    override val arlPlaceholder = "e.g. 1234abcd..."
    override val arlHint = "Find the ARL in your browser: deezer.com → F12 → Application → Cookies → arl."
    override val arlCookie = "ARL cookie"
    override val emailAndPassword = "E-mail and password"
    override val email = "E-mail"
    override val password = "Password"
    override val twoFactorHint = "For accounts with two-factor authentication, use the ARL cookie."
    override val signingIn = "Signing in…"
    override val signIn = "Sign in"
    override val appTagline = "Gladix for Windows"

    override val nothingPlayingShort = "Nothing is playing"
    override val previous = "Previous"
    override val next = "Next"
    override val repeat = "Repeat"
    override val volume = "Volume"

    override val settingsTitle = "Settings"
    override val sectionAppearance = "Appearance"
    override val uiLanguage = "Application language"
    override val sectionPlayback = "Playback"
    override val streamQuality = "Stream quality"
    override val qualityNote = "When the selected quality is unavailable, the player falls back to " +
        "the next lower one. Downloaded tracks always play from disk."
    override val sectionWindows = "Windows and media keys"
    override val controlFromWindows = "Control playback from Windows"
    override val controlFromWindowsNote = "Playback appears in quick settings and on the lock " +
        "screen. The media keys on your keyboard (play/pause, next, previous) then control Gladix."
    override val mediaSessionOff = "Off – Gladix is not shown in Windows."
    override val sectionAccount = "Deezer account"
    override val logPlays = "Report plays to Deezer"
    override val logPlaysNote = "Tracks count towards your recommendations and statistics."
    override val countryLabel = "Country (e.g. CZ)"
    override val deezerLanguageLabel = "Language (e.g. cs)"
    override val proxyLabel = "Proxy (optional, host:port)"
    override val changesAfterRestart = "Changes take effect after restarting the app."
    override val sectionData = "Data and storage"
    override fun dataFolder(path: String) = "Data folder: $path"
    override fun downloadsFolder(path: String) = "Downloaded tracks: $path"
    override val openFolder = "Open folder"
    override val clearHistory = "Clear history"
    override val historyCleared = "History cleared"
    override val sectionUser = "Account"
    override val notLoggedIn = "Not signed in"
    override val signOut = "Sign out"
    override val sectionAbout = "About"
    override val aboutLine = "Gladix for Windows – an unofficial Deezer client."
    override val aboutCredits = "Built on the open-source Echo core (brahmkshatriya) and the Deezer " +
        "extension from the Gladix project (rschwertley). License: Unabandon Public License."

    override val mediaStarting = "Starting…"
    override val mediaUnavailable = "Unavailable"
    override val smtcUnavailable = "SMTC is not available on this system"
    override val mediaActive = "Active – playback is shown in Windows"
    override val mediaActiveNoArt = "Active (without album art)"
    override val mediaDisabled = "Off"
    override val mediaDisabledNote = "Off – Gladix is not shown in Windows."

    override fun decodeFailed(container: String, details: String) =
        "Could not decode audio ($container): $details"
    override fun convertFailed(encoding: String, container: String) =
        "Cannot convert $encoding to PCM ($container)"
}

/**
 * Process-wide holder for the active language. Backed by snapshot state, so
 * every composable that reads [s] recomposes when the language changes.
 */
object UiStrings {

    var lang: Lang by mutableStateOf(Lang.default)
        private set

    /** Current string table. */
    val s: Strings get() = if (lang == Lang.En) EnStrings else CsStrings

    fun set(target: Lang) {
        lang = target
    }

    /** Applies a persisted code (`cs`/`en`); anything unknown falls back to Czech. */
    fun load(code: String?) {
        lang = Lang.fromCode(code)
    }
}