package dev.gladix.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.brahmkshatriya.echo.common.models.Feed
import dev.brahmkshatriya.echo.common.models.Shelf
import dev.brahmkshatriya.echo.common.models.Tab
import dev.brahmkshatriya.echo.common.models.Track
import dev.gladix.desktop.ui.text.UiStrings

@Composable
fun HomeScreen(state: AppState) {
    val feedLoader = rememberLoader("home") { state.ctx.client.homeFeed() }
    val feed = feedLoader.data
    val strings = UiStrings.s

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = strings.greeting(
                        state.ctx.client.user?.name.orEmpty().ifBlank { strings.listenerFallback }
                    ),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = strings.recommendedByDeezer,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        when {
            feedLoader.loading && feed == null -> LoadingBox()
            feedLoader.error != null && feed == null -> ErrorBox(feedLoader.error!!) {
                feedLoader.reload()
            }
            feed != null -> TabbedShelves(state, feed)
        }
    }
}

/**
 * Renders a [Feed] as a tab strip plus the shelves of the selected tab.
 * When the feed has no tabs everything is shown in one page.
 *
 * [sortable] adds the sort/filter control to every tab; each tab keeps its own
 * persisted choice, so sorting the library tracks does not reorder the albums.
 */
@Composable
fun TabbedShelves(
    state: AppState,
    feed: Feed<Shelf>,
    startTab: Int = 0,
    sortable: Boolean = false
) {
    val tabs = feed.notSortTabs
    var selected by remember(feed) { mutableStateOf(startTab) }
    val index = selected.coerceIn(0, (tabs.size - 1).coerceAtLeast(0))
    val selectedTab: Tab? = tabs.getOrNull(index)

    Column(Modifier.fillMaxSize()) {
        if (tabs.size > 1) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(tabs) { position, tab ->
                    FilterChip(
                        selected = position == index,
                        onClick = { selected = position },
                        label = { Text(tabTitle(tab)) }
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
        }

        // One loader per tab: reusing one across tabs kept the previous tab on
        // screen (and its stale error), which made a freshly picked tab look empty.
        key(selectedTab?.id) {
            val shelvesLoader = rememberLoader(selectedTab) {
                feed.getPagedData(selectedTab).pagedData.loadAll()
            }
            val shelves = shelvesLoader.data
            val sort = if (sortable) {
                remember(selectedTab?.id) {
                    SortState(state.ctx.settings, "tab_${selectedTab?.id ?: "all"}")
                }
            } else null
            val shown = if (shelves != null && sort != null) {
                sortShelves(shelves, sort.spec)
            } else shelves

            Column(Modifier.fillMaxSize()) {
                if (sort != null && !shelves.isNullOrEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .padding(start = 20.dp, end = 8.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Spacer(Modifier.weight(1f))
                        SortControls(sort)
                    }
                }
                when {
                    shelvesLoader.loading && shelves == null -> LoadingBox()
                    shelvesLoader.error != null -> ErrorBox(shelvesLoader.error!!) {
                        shelvesLoader.reload()
                    }

                    shown != null && shown.isEmpty() -> EmptyBox(UiStrings.s.emptyTab)
                    shown != null -> ShelvesColumn(
                        shelves = shown,
                        onOpen = { state.open(it) },
                        onPlayTrack = { track, list -> playTrack(state, track, list) },
                        onPlayAll = { list -> playAll(state, list) },
                        onOpenCategory = { state.openCategory(it) },
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )

                    else -> LoadingBox()
                }
            }
        }
    }
}

/** Tab titles come from the extension in English, so the known ids are translated. */
@Composable
fun tabTitle(tab: Tab): String {
    val strings = UiStrings.s
    return when (tab.id) {
        "all" -> strings.tabAll
        "playlists" -> strings.tabPlaylists
        "albums" -> strings.tabAlbums
        "tracks" -> strings.tabTracks
        "artists" -> strings.tabArtists
        else -> tab.title
    }
}

fun playTrack(state: AppState, track: Track, list: List<Track>) {
    val index = list.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
    state.ctx.player.playList(list, index)
}

fun playAll(state: AppState, list: List<Track>) {
    if (list.isEmpty()) return
    state.ctx.player.playList(list, 0)
}