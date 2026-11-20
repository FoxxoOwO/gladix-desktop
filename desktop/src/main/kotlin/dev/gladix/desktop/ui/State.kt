package dev.gladix.desktop.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.brahmkshatriya.echo.common.models.Album
import dev.brahmkshatriya.echo.common.models.Artist
import dev.brahmkshatriya.echo.common.models.EchoMediaItem
import dev.brahmkshatriya.echo.common.models.Playlist
import dev.brahmkshatriya.echo.common.models.Shelf
import dev.brahmkshatriya.echo.common.models.Track
import dev.gladix.desktop.AppContext
import dev.gladix.desktop.ui.text.UiStrings
import androidx.compose.ui.input.key.Key
import dev.gladix.desktop.core.PrefsSettings
import dev.gladix.desktop.core.Shortcut
import dev.gladix.desktop.core.Shortcuts
import dev.gladix.desktop.core.ThemePreference

/**
 * A tiny async loader used by every screen: reloads when [keys] change and
 * never throws into composition.
 */
class Loader<T> {
    var data by mutableStateOf<T?>(null)
        internal set
    var error by mutableStateOf<String?>(null)
        internal set
    var loading by mutableStateOf(false)
        internal set
    var version by mutableStateOf(0)
        internal set

    fun reload() {
        version++
    }

    internal suspend fun load(block: suspend () -> T) {
        loading = true
        error = null
        try {
            data = block()
        } catch (t: Throwable) {
            if (t is kotlinx.coroutines.CancellationException) throw t
            error = t.message ?: t::class.simpleName ?: "Something went wrong"
        } finally {
            loading = false
        }
    }
}

@Composable
fun <T> rememberLoader(vararg keys: Any?, block: suspend () -> T): Loader<T> {
    val loader = remember { Loader<T>() }
    val version = loader.version
    LaunchedEffect(keys.toList(), version) { loader.load(block) }
    return loader
}

/** Which screen the window is showing. */
sealed interface Page {
    data object Home : Page
    data object Search : Page
    data object Library : Page
    data object Downloads : Page
    data object History : Page
    data object Queue : Page
    data object Settings : Page
    data object NowPlaying : Page
    data class Item(val item: EchoMediaItem) : Page
    data class Category(val category: Shelf.Category) : Page

    /** The "show all" page behind a horizontal shelf. */
    data class All(val all: AllItems) : Page
}

/**
 * A shelf that can keep loading more items, opened as its own full page.
 *
 * Only shelves carrying a [Shelf.Lists.more] feed land here: a fixed strip of
 * a dozen albums has nothing more to show, so it gets no button.
 */
sealed interface AllItems {
    val title: String

    data class Albums(override val title: String, val feed: dev.brahmkshatriya.echo.common.models.Feed<Shelf>) :
        AllItems
}

class AppState(val ctx: AppContext) {

    var page by mutableStateOf<Page>(Page.Home)
    var backStack by mutableStateOf<List<Page>>(emptyList())
    var searchQuery by mutableStateOf("")
    var loginError by mutableStateOf<String?>(null)
    var toast by mutableStateOf<String?>(null)

    private var themeChoice by mutableStateOf(
        ThemePreference.normalize(
            ctx.settings.getStringOrDefault(PrefsSettings.KEY_THEME, PrefsSettings.THEME_SYSTEM)
        )
    )

    /**
     * The theme choice (`system`/`dark`/`light`); [ThemePreference] turns it into
     * light/dark. Assigning it persists, so the settings screen only writes
     * `state.theme = ...` and Main recomposes into the new palette.
     */
    var theme: String
        get() = themeChoice
        set(value) {
            val normalized = ThemePreference.normalize(value)
            themeChoice = normalized
            ctx.settings.putString(PrefsSettings.KEY_THEME, normalized)
        }

    /** Bumped when a shortcut asks the search field for focus. */
    var searchFocusTick by mutableStateOf(0)
        private set

    fun focusSearch() {
        navigate(Page.Search)
        searchFocusTick++
    }

    /**
     * Runs the shortcut bound to [key], if any. Returns true when the event was
     * used, so the window does not let it fall through to the menu system.
     */
    fun handleShortcut(
        key: Key,
        ctrl: Boolean = false,
        shift: Boolean = false,
        alt: Boolean = false
    ): Boolean = when (Shortcuts.of(key, ctrl, shift, alt)) {
        Shortcut.PlayPause -> {
            if (ctx.player.current != null) ctx.player.togglePlayPause()
            ctx.player.current != null
        }

        Shortcut.Next -> {
            ctx.player.next()
            ctx.player.current != null
        }

        Shortcut.Previous -> {
            ctx.player.previous()
            ctx.player.current != null
        }

        Shortcut.Search -> {
            focusSearch()
            true
        }

        Shortcut.VolumeUp -> {
            ctx.player.changeVolume(ctx.player.volume + VOLUME_STEP)
            true
        }

        Shortcut.VolumeDown -> {
            ctx.player.changeVolume(ctx.player.volume - VOLUME_STEP)
            true
        }

        Shortcut.Mute -> {
            ctx.player.toggleMute()
            true
        }

        Shortcut.SeekForward -> seekBy(SEEK_STEP_MS)

        Shortcut.SeekBack -> seekBy(-SEEK_STEP_MS)

        Shortcut.Back -> if (canGoBack) {
            back()
            true
        } else false

        null -> false
    }

    /** Moves inside the current track; false when nothing plays. */
    private fun seekBy(deltaMs: Long): Boolean {
        if (ctx.player.current == null) return false
        ctx.player.seekTo(ctx.player.positionMs + deltaMs)
        return true
    }

    /**
     * Derived from the client instead of being cached in a field: the session is
     * restored by [AppContext.init], which runs after this class is constructed,
     * so a cached flag would show the login screen to an already signed in user.
     */
    val loggedIn: Boolean get() = ctx.client.user != null

    val canGoBack: Boolean get() = backStack.isNotEmpty()

    fun navigate(target: Page) {
        if (target == page) return
        backStack = backStack + page
        page = target
    }

    fun back() {
        page = backStack.lastOrNull() ?: Page.Home
        backStack = backStack.dropLast(1)
    }

    fun open(item: EchoMediaItem) = navigate(Page.Item(item))

    fun openCategory(category: Shelf.Category) = navigate(Page.Category(category))

    fun openAll(all: AllItems) = navigate(Page.All(all))

    fun showToast(message: String) {
        toast = message
    }

    /**
     * Runs a write such as a like toggle. A failure undoes the optimistic UI
     * change and says why, instead of leaving the button lying.
     */
    suspend fun write(revert: () -> Unit = {}, block: suspend () -> Unit) {
        try {
            block()
        } catch (t: Throwable) {
            if (t is kotlinx.coroutines.CancellationException) throw t
            revert()
            showToast(UiStrings.s.error(t.message))
        }
    }
}

fun itemTitle(item: EchoMediaItem): String = when (item) {
    is Track -> item.title
    is Album -> item.title
    is Playlist -> item.title
    is Artist -> item.name
    else -> item.title
}

fun itemSubtitle(item: EchoMediaItem): String? = when (item) {
    is Track -> item.artists.joinToString(", ") { it.name }.ifBlank { item.subtitle }
    is Album -> item.artists.joinToString(", ") { it.name }.ifBlank { null }
    is Playlist -> item.authors.joinToString(", ") { it.name }.ifBlank { null }
    is Artist -> UiStrings.s.artistLabel
    else -> item.subtitle
}

fun formatDuration(ms: Long?): String {
    if (ms == null || ms < 0) return ""
    if (ms == 0L) return "0:00"
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

fun formatCount(count: Long?): String? {
    if (count == null || count <= 0) return null
    return when {
        count >= 1_000_000 -> "%.1fM".format(count / 1_000_000.0)
        count >= 1_000 -> "%.1fK".format(count / 1_000.0)
        else -> count.toString()
    }
}

/** One volume key press. */
private const val VOLUME_STEP = 0.05f

/** One seek key press. */
private const val SEEK_STEP_MS = 10_000L