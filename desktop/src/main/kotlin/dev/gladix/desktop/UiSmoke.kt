package dev.gladix.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import dev.brahmkshatriya.echo.common.models.Album
import dev.brahmkshatriya.echo.common.models.Artist
import dev.brahmkshatriya.echo.common.models.Track
import dev.gladix.desktop.ui.App
import dev.gladix.desktop.ui.AppState
import dev.gladix.desktop.ui.Page
import dev.gladix.desktop.ui.SaveToPlaylistDialog
import dev.gladix.desktop.ui.SortControls
import dev.gladix.desktop.ui.SortSheet
import dev.gladix.desktop.ui.SortState
import dev.gladix.desktop.ui.TrackMenuButton
import kotlinx.coroutines.delay
import kotlin.system.exitProcess

/**
 * Opens every screen of the app, one after another, in a real window and exits
 * non-zero if composition throws.
 *
 * The headless [SelfCheck] proves the logic; this proves the composables, which
 * a unit test cannot: a wrong icon name, a `weight` in the wrong scope or a
 * missing string shows up here as a crash instead of on the user's screen.
 *
 * Run with `./gradlew :desktop:uiSmoke`. It needs no Deezer credentials: the
 * loaders fail into their error states, which is also what has to render.
 */
fun main() {
    val ctx = AppContext()
    val state = AppState(ctx)

    val smokeTracks = listOf(
        Track(
            id = "smoke-1",
            title = "Fast Lane",
            artists = listOf(Artist(id = "smoke-artist", name = "AmaG")),
            album = Album(
                id = "smoke-album",
                title = "Fast Lane",
                artists = listOf(Artist(id = "smoke-artist", name = "AmaG")),
                label = "Smoke Records"
            ),
            duration = 140_000L,
            albumOrderNumber = 3L
        ),
        Track(
            id = "smoke-2",
            title = "Second Track",
            artists = listOf(Artist(id = "smoke-artist-2", name = "B")),
            duration = 200_000L
        ),
        Track(
            id = "smoke-3",
            title = "alpha",
            artists = listOf(Artist(id = "smoke-artist-3", name = "C")),
            duration = 90_000L
        )
    )
    // Fills the queue without touching the network: `current` is set before any
    // stream is opened, so the player UI has something real to draw.
    state.ctx.player.playList(smokeTracks, 0)

    val screens: List<Pair<String, Page>> = listOf(
        "home" to Page.Home,
        "search" to Page.Search,
        "library" to Page.Library,
        "downloads" to Page.Downloads,
        "history" to Page.History,
        "queue" to Page.Queue,
        "settings" to Page.Settings,
        "now playing" to Page.NowPlaying,
        "album" to Page.Item(smokeTracks.first().album!!)
    )

    var phase by mutableStateOf(0)
    val phases = screens.size + 3 // + sort sheet, + track menu, + playlist dialog

    println("uiSmoke: $phases phases")

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Gladix UI smoke"
        ) {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    Box {
                        App(state)
                        LaunchedEffect(Unit) {
                            for ((index, screen) in screens.withIndex()) {
                                state.page = screen.second
                                phase = index
                                println("uiSmoke: ${screen.first}")
                                delay(220)
                            }
                            println("uiSmoke: sort sheet + menu + playlist dialog")
                            phase = screens.size
                            delay(400)
                            phase = screens.size + 1
                            delay(400)
                            phase = screens.size + 2
                            delay(400)
                            println("uiSmoke: done")
                            exitProcess(0)
                        }
                        when (phase) {
                            screens.size -> SortSheet(
                                SortState(ctx.settings, "smoke"),
                                onDismiss = {}
                            )

                            screens.size + 1 -> Column(Modifier.padding(24.dp)) {
                                SortControls(remember { SortState(ctx.settings, "smoke") })
                                TrackMenuButton(
                                    state = state,
                                    track = smokeTracks.first(),
                                    startExpanded = true
                                )
                            }

                            screens.size + 2 -> SaveToPlaylistDialog(
                                state = state,
                                track = smokeTracks.first(),
                                onDismiss = {}
                            )
                        }
                    }
                }
            }
        }
    }
}
