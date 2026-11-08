package dev.gladix.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import dev.brahmkshatriya.echo.common.models.Album
import dev.brahmkshatriya.echo.common.models.Artist
import dev.brahmkshatriya.echo.common.models.Playlist
import dev.brahmkshatriya.echo.common.models.Shelf
import dev.brahmkshatriya.echo.common.models.Track
import dev.gladix.desktop.ui.AllItems
import dev.gladix.desktop.ui.App
import dev.gladix.desktop.ui.AppState
import dev.gladix.desktop.ui.Page
import dev.gladix.desktop.ui.SaveToPlaylistDialog
import dev.gladix.desktop.ui.ShelvesColumn
import dev.gladix.desktop.ui.SortControls
import dev.gladix.desktop.ui.SortField
import dev.gladix.desktop.ui.SortSpec
import dev.gladix.desktop.ui.SortSheet
import dev.gladix.desktop.ui.SortState
import dev.gladix.desktop.ui.TrackMenuButton
import dev.gladix.desktop.ui.sortShelves
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
        "album" to Page.Item(smokeTracks.first().album!!),
        // The "show all" page: a paged grid, exercised with one page of items.
        "all albums" to Page.All(
            AllItems.Albums("Albums", smokeAlbumFeed())
        )
    )

    var phase by mutableStateOf(0)
    val phases = screens.size + 4 // + shelf types, sort sheet, track menu, playlist dialog

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
                            // A library tab is a flat list of Shelf.Item rows, so every
                            // shelf shape has to render - an unhandled one shows as an
                            // empty page instead of an error.
                            println("uiSmoke: shelf types")
                            phase = screens.size
                            delay(700)
                            println("uiSmoke: sort sheet + menu + playlist dialog")
                            phase = screens.size + 1
                            delay(400)
                            phase = screens.size + 2
                            delay(400)
                            phase = screens.size + 3
                            delay(400)
                            println("uiSmoke: done")
                            exitProcess(0)
                        }
                        when (phase) {
                            screens.size -> Column(Modifier.fillMaxSize()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Spacer(Modifier.weight(1f))
                                    SortControls(remember { SortState(ctx.settings, "smoke") })
                                }
                                ShelvesColumn(
                                    shelves = sortShelves(
                                        smokeShelves(smokeTracks),
                                        SortSpec(SortField.TITLE, reversed = true)
                                    ),
                                    onOpen = {},
                                    onPlayTrack = { _, _ -> },
                                    onPlayAll = {},
                                    modifier = Modifier.padding(12.dp)
                                )
                            }

                            screens.size + 1 -> SortSheet(
                                SortState(ctx.settings, "smoke"),
                                onDismiss = {}
                            )

                            screens.size + 2 -> Column(Modifier.padding(24.dp)) {
                                SortControls(remember { SortState(ctx.settings, "smoke") })
                                TrackMenuButton(
                                    state = state,
                                    track = smokeTracks.first(),
                                    startExpanded = true
                                )
                            }

                            screens.size + 3 -> SaveToPlaylistDialog(
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

/**
 * A shelf feed with a single page, so the "show all" grid renders without
 * touching the network and without paging forever.
 */
internal fun smokeAlbumFeed(): dev.brahmkshatriya.echo.common.models.Feed<Shelf> {
    val albums = (1..8).map { index ->
        Shelf.Item(
            Album(
                id = "smoke-all-$index",
                title = "Album $index",
                artists = listOf(Artist(id = "smoke-artist", name = "AmaG"))
            )
        )
    }
    val data: dev.brahmkshatriya.echo.common.helpers.PagedData<Shelf> =
        dev.brahmkshatriya.echo.common.helpers.PagedData.Single { albums }
    return dev.brahmkshatriya.echo.common.models.Feed(listOf()) {
        dev.brahmkshatriya.echo.common.models.Feed.Data(data)
    }
}

/**
 * One shelf of every shape the UI renders, including the flat [Shelf.Item]
 * rows a library tab is built from.
 */
internal fun smokeShelves(tracks: List<Track>): List<Shelf> = listOf(
    Shelf.Item(
        Album(
            id = "smoke-album",
            title = "Fast Lane",
            artists = listOf(Artist(id = "smoke-artist", name = "AmaG")),
            label = "Smoke Records"
        )
    ),
    Shelf.Item(
        Playlist(
            id = "smoke-playlist",
            title = "Road Trip",
            isEditable = true,
            authors = listOf(Artist(id = "smoke-user", name = "Teresa")),
            trackCount = 42L
        )
    ),
    Shelf.Item(Artist(id = "smoke-artist", name = "AmaG")),
    Shelf.Lists.Tracks(id = "smoke-tracks", title = "Short list", list = tracks),
    Shelf.Lists.Items(id = "smoke-items", title = "Whole collection", list = tracks),
    Shelf.Lists.Categories(
        id = "smoke-categories",
        title = "Categories",
        list = listOf(Shelf.Category(id = "smoke-category", title = "Rock"))
    )
)
