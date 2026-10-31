package dev.gladix.desktop.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.gladix.desktop.ui.text.UiStrings
import kotlinx.coroutines.launch

@Composable
fun LibraryScreen(state: AppState) {
    val feedLoader = rememberLoader("library") { state.ctx.client.libraryFeed() }
    val feed = feedLoader.data

    Column(Modifier.fillMaxSize()) {
        ScreenTitle(UiStrings.s.navLibrary)
        when {
            feedLoader.loading && feed == null -> LoadingBox()
            feedLoader.error != null && feed == null ->
                ErrorBox(feedLoader.error!!) { feedLoader.reload() }

            feed != null -> TabbedShelves(state, feed, sortable = true)
        }
    }
}

@Composable
fun ScreenTitle(title: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun DownloadsScreen(state: AppState) {
    val downloads = state.ctx.downloads
    @Suppress("UNUSED_EXPRESSION")
    downloads.version
    val items = downloads.items
    val scope = rememberCoroutineScope()
    val player = state.ctx.player
    val sort = remember { SortState(state.ctx.settings, "downloads") }
    val shown = sort.spec.apply(items.map { it.track })

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = UiStrings.s.downloadedTracks,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            SortControls(sort)
        }
        if (items.isEmpty()) {
            EmptyBox(UiStrings.s.nothingDownloaded)
            return@Column
        }
        LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
            items(shown) { track ->
                val entry = items.first { it.track.id == track.id }
                val isCurrent = player.current?.id == entry.track.id
                TrackRow(
                    track = entry.track,
                    index = null,
                    isCurrent = isCurrent,
                    isPlaying = isCurrent && player.isPlaying,
                    onClick = {
                        player.playList(shown, shown.indexOf(track))
                    },
                    secondary = {
                        Text(
                            text = "${entry.bytes / 1024 / 1024} MB • offline",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    },
                    trailing = {
                        IconButton(onClick = { scope.launch { downloads.delete(entry.track.id) } }) {
                            Icon(Icons.Rounded.Delete, contentDescription = UiStrings.s.remove)
                        }
                    },
                    menu = { TrackMenuButton(state, entry.track) }
                )
            }
        }
    }
}

@Composable
fun HistoryScreen(state: AppState) {
    val history = state.ctx.history
    @Suppress("UNUSED_EXPRESSION")
    history.version
    val entries = history.all
    val player = state.ctx.player
    val sort = remember { SortState(state.ctx.settings, "history") }
    val shown = sort.spec.apply(entries.map { it.track })

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = UiStrings.s.navHistory,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            SortControls(sort)
            if (entries.isNotEmpty()) {
                IconButton(onClick = { history.clear() }) {
                    Icon(Icons.Rounded.Delete, contentDescription = UiStrings.s.clearHistory)
                }
            }
        }
        if (entries.isEmpty()) {
            EmptyBox(UiStrings.s.nothingPlayed)
            return@Column
        }
        LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
            items(shown) { track ->
                val isCurrent = player.current?.id == track.id
                TrackRow(
                    track = track,
                    index = null,
                    isCurrent = isCurrent,
                    isPlaying = isCurrent && player.isPlaying,
                    onClick = { player.playList(shown, shown.indexOf(track)) },
                    menu = { TrackMenuButton(state, track) }
                )
            }
        }
    }
}

@Composable
fun QueueScreen(state: AppState) {
    val player = state.ctx.player
    val queue = player.queue

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Closing the queue returns to whatever was open before it.
            IconButton(onClick = { state.back() }) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = UiStrings.s.back
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = UiStrings.s.navQueue,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = UiStrings.s.trackCount(queue.size.toLong()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (queue.isNotEmpty()) {
                IconButton(onClick = { player.clearQueue() }) {
                    Icon(Icons.Rounded.Delete, contentDescription = UiStrings.s.clearQueue)
                }
            }
        }
        if (queue.isEmpty()) {
            EmptyBox(UiStrings.s.queueEmpty)
            return@Column
        }
        LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
            items(queue) { track ->
                val position = queue.indexOf(track)
                val isCurrent = position == player.index
                TrackRow(
                    track = track,
                    index = position,
                    isCurrent = isCurrent,
                    isPlaying = isCurrent && player.isPlaying,
                    onClick = { player.playList(queue, position, player.context) },
                    trailing = {
                        IconButton(onClick = { player.removeFromQueue(position) }) {
                            Icon(Icons.Rounded.Close, contentDescription = UiStrings.s.remove)
                        }
                    },
                    menu = { TrackMenuButton(state, track) }
                )
            }
        }
    }
}

@Composable
internal fun LyricsPane(
    state: AppState,
    track: dev.brahmkshatriya.echo.common.models.Track,
    modifier: Modifier = Modifier
) {
    val loader = rememberLoader(track.id) { state.ctx.client.lyrics(track) }
    Column(modifier = modifier.fillMaxSize()) {
        val lyrics = loader.data
        when {
            loader.loading -> LoadingBox()
            lyrics.isNullOrBlank() -> Text(
                text = UiStrings.s.lyricsUnavailable,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(lyrics.split("\n")) { line ->
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        }
    }
}