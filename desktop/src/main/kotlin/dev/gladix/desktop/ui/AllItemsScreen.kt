package dev.gladix.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.brahmkshatriya.echo.common.models.EchoMediaItem
import dev.brahmkshatriya.echo.common.models.Feed
import dev.brahmkshatriya.echo.common.models.Shelf
import dev.gladix.desktop.ui.text.UiStrings
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

/** Rows from the end at which the next page is requested, so scrolling never stalls. */
private const val PREFETCH_DISTANCE = 12

/**
 * The "see everything" page behind a horizontal shelf, starting with all albums.
 *
 * A shelf ships roughly a dozen cards; the rest lives behind its
 * [Shelf.Lists.more] feed, which pages continuations. This screen keeps asking
 * for the next page as the grid approaches its end instead of pulling the whole
 * catalogue up front.
 */
@Composable
fun AllItemsScreen(state: AppState, all: AllItems) {
    Column(Modifier.fillMaxSize()) {
        BackRow(state)
        Text(
            text = all.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )
        when (all) {
            is AllItems.Albums -> AlbumsGrid(state, all.feed)
        }
    }
}

@Composable
private fun AlbumsGrid(state: AppState, feed: Feed<Shelf>) {
    var items by remember(feed) { mutableStateOf<List<EchoMediaItem>>(emptyList()) }
    var loading by remember(feed) { mutableStateOf(false) }
    var finished by remember(feed) { mutableStateOf(false) }
    var error by remember(feed) { mutableStateOf<String?>(null) }

    val grid = rememberLazyGridState()
    val sort = remember(feed) { SortState(state.ctx.settings, "all_albums") }

    // The paged data and the continuation both belong to the remember(feed) scope
    // above, so switching to another shelf's page starts from a clean state.
    val pager = remember(feed) { AllItemsPager(feed) }

    suspend fun loadNext() {
        if (loading || finished) return
        loading = true
        error = null
        runCatching { pager.next() }
            .onSuccess { page ->
                items = items + page.items
                finished = page.finished
            }
            .onFailure { t ->
                if (t is kotlinx.coroutines.CancellationException) throw t
                error = t.message ?: t::class.simpleName ?: "Failed to load"
            }
        loading = false
    }

    LaunchedEffect(feed) {
        if (items.isEmpty()) loadNext()
    }

    // Ask for the next page while the user is still a screen away from the end.
    LaunchedEffect(grid, finished, items.size) {
        snapshotFlow { grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .distinctUntilChanged()
            .filter { last ->
                val total = grid.layoutInfo.totalItemsCount
                !finished && total > 0 && last >= total - PREFETCH_DISTANCE
            }
            .collect { loadNext() }
    }

    val shown = sort.spec.applyItems(items)

    when {
        items.isEmpty() && loading -> LoadingBox()
        items.isEmpty() && error != null -> ErrorBox(error!!) { }
        items.isEmpty() && finished -> EmptyBox(UiStrings.s.nothingToShow)
        else -> LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 168.dp),
            state = grid,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(shown, key = { it.id }) { item ->
                MediaCard(
                    title = itemTitle(item),
                    subtitle = itemSubtitle(item),
                    cover = item.cover,
                    onClick = { state.open(item) },
                    modifier = Modifier.fillMaxWidth(),
                    fillWidth = true
                )
            }
            if (loading) item { LoadingBox() }
            if (error != null && !loading) item { ErrorBox(error!!) { } }
        }
    }
}

/**
 * Walks the continuations of a shelf feed, one page at a time.
 *
 * [next] returns the albums of the next page together with the "no more pages"
 * flag, so the composable only has to append.
 */
private class AllItemsPager(private val feed: Feed<Shelf>) {

    private var paged: dev.brahmkshatriya.echo.common.helpers.PagedData<Shelf>? = null
    private var continuation: String? = null
    private var started = false

    data class Page(val items: List<EchoMediaItem>, val finished: Boolean)

    suspend fun next(): Page {
        val data = paged ?: feed
            .getPagedData(feed.notSortTabs.firstOrNull())
            .pagedData
            .also { paged = it }

        val page = data.loadPage(if (started) continuation else null)
        started = true
        continuation = page.continuation

        // A page carries shelves; only wrapped items are albums/playlists/artists.
        val loaded = page.data.flatMap { shelf -> shelf.toMediaItems() }
        return Page(loaded, page.continuation == null)
    }

    private fun Shelf.toMediaItems(): List<EchoMediaItem> = when (this) {
        is Shelf.Item -> listOf(media)
        is Shelf.Lists.Items -> list
        else -> emptyList()
    }
}
