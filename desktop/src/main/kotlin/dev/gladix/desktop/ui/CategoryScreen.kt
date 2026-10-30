package dev.gladix.desktop.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.brahmkshatriya.echo.common.models.Feed
import dev.brahmkshatriya.echo.common.models.Shelf
import dev.gladix.desktop.ui.text.UiStrings

/**
 * A Deezer home "category" (genre, mood, chart...) expands into its own feed
 * of shelves.
 */
@Composable
fun CategoryScreen(state: AppState, category: Shelf.Category) {
    val feed: Feed<Shelf>? = category.feed
    val loader = rememberLoader(category.id) {
        feed?.getPagedData(null)?.pagedData?.loadAll() ?: emptyList()
    }

    androidx.compose.foundation.layout.Column(Modifier.fillMaxSize()) {
        BackRow(state)
        Text(
            text = category.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )
        val shelves = loader.data
        val strings = UiStrings.s
        when {
            feed == null -> EmptyBox(strings.noCategoryContent)
            loader.loading && shelves == null -> LoadingBox()
            loader.error != null && shelves == null ->
                ErrorBox(loader.error!!) { loader.reload() }
            shelves != null -> ShelvesColumn(
                shelves = shelves,
                onOpen = { state.open(it) },
                onPlayTrack = { track, list -> playTrack(state, track, list) },
                onPlayAll = { list -> playAll(state, list) },
                onOpenCategory = { state.openCategory(it) },
                modifier = Modifier.padding(horizontal = 12.dp)
            )
        }
    }
}