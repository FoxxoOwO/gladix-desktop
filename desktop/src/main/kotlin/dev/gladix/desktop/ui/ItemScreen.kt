package dev.gladix.desktop.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.brahmkshatriya.echo.common.models.Album
import dev.brahmkshatriya.echo.common.models.Artist
import dev.brahmkshatriya.echo.common.models.EchoMediaItem
import dev.brahmkshatriya.echo.common.models.ImageHolder
import dev.brahmkshatriya.echo.common.models.Playlist
import dev.brahmkshatriya.echo.common.models.Track
import dev.gladix.desktop.core.Images
import dev.gladix.desktop.core.allItems
import dev.gladix.desktop.ui.text.UiStrings
import kotlinx.coroutines.launch

@Composable
fun ItemScreen(state: AppState, item: EchoMediaItem) {
    when (item) {
        is Album -> CollectionScreen(
            state = state,
            item = item,
            title = item.title,
            subtitle = item.artists.joinToString(", ") { it.name },
            description = item.description,
            cover = item.cover,
            meta = buildMeta(
                item.releaseDate?.let { it.year.toString() },
                item.trackCount?.let { UiStrings.s.trackCount(it) },
                formatDuration(item.duration)
            ),
            loadDetail = { state.ctx.client.albumDetail(item) },
            loadTracks = { state.ctx.client.albumTrackPager(item).loadAll() },
            loadShelves = { state.ctx.client.albumShelves(item) },
            sortScope = "album"
        )

        is Playlist -> CollectionScreen(
            state = state,
            item = item,
            title = item.title,
            subtitle = item.authors.joinToString(", ") { it.name },
            description = item.description,
            cover = item.cover,
            meta = buildMeta(
                item.trackCount?.let { UiStrings.s.trackCount(it) },
                formatDuration(item.duration)
            ),
            loadDetail = { state.ctx.client.playlistDetail(item) },
            loadTracks = { state.ctx.client.playlistTrackPager(item).loadAll() },
            loadShelves = { state.ctx.client.playlistShelves(item) },
            sortScope = "playlist"
        )

        is Artist -> ArtistScreen(state, item)

        is Track -> {
            LaunchedEffect(item.id) { state.ctx.player.playNow(item) }
            EmptyBox(UiStrings.s.playingNow(item.title))
        }

        else -> EmptyBox(UiStrings.s.unsupportedContent)
    }
}

private fun buildMeta(vararg parts: String?): String =
    parts.filterNotNull().filter { it.isNotBlank() }.joinToString(" • ")

@Composable
private fun ArtistScreen(state: AppState, artist: Artist) {
    val detail = rememberLoader(artist.id) { state.ctx.client.artistDetail(artist) }
    val shelves = rememberLoader(artist.id) { state.ctx.client.artistShelves(artist) }
    val shown = detail.data ?: artist
    val scope = rememberCoroutineScope()
    var following by remember(artist.id) { mutableStateOf(false) }
    LaunchedEffect(artist.id) {
        following = runCatching { state.ctx.client.followed(artist) }.getOrDefault(false)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        item {
            BackRow(state)
            DetailHeader(
                title = shown.name,
                subtitle = UiStrings.s.artistLabel,
                description = shown.bio,
                cover = shown.cover,
                round = true,
                meta = null,
                actions = {
                    IconButton(onClick = {
                        scope.launch {
                            val next = !following
                            following = next
                            runCatching { state.ctx.client.setFollowed(artist, next) }
                            state.showToast(
                                if (next) UiStrings.s.nowFollowing(shown.name)
                                else UiStrings.s.stoppedFollowing(shown.name)
                            )
                        }
                    }) {
                        Icon(
                            imageVector = if (following) Icons.Rounded.Favorite
                            else Icons.Rounded.FavoriteBorder,
                            contentDescription = UiStrings.s.follow,
                            tint = if (following) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        }
        val list = shelves.data
        if (shelves.loading && list == null) {
            item { LoadingBox() }
        } else if (list != null) {
            items(list) { shelf ->
                Box(Modifier.padding(horizontal = 12.dp)) {
                    ShelfBlock(
                        shelf = shelf,
                        onOpen = { state.open(it) },
                        onPlayTrack = { track, tracks -> playTrack(state, track, tracks) },
                        onPlayAll = { tracks -> playAll(state, tracks) },
                        onOpenCategory = { state.openCategory(it) }
                    )
                }
            }
        }
    }
}

@Composable
private fun <T> CollectionScreen(
    state: AppState,
    item: EchoMediaItem,
    title: String,
    subtitle: String?,
    description: String?,
    cover: ImageHolder?,
    meta: String?,
    loadDetail: suspend () -> T,
    loadTracks: suspend () -> List<Track>,
    loadShelves: suspend () -> List<dev.brahmkshatriya.echo.common.models.Shelf>,
    sortScope: String
) {
    val detail = rememberLoader(item.id) { runCatching { loadDetail() }.getOrNull() }
    val tracks = rememberLoader(item.id) { loadTracks() }
    val shelves = rememberLoader(item.id) { runCatching { loadShelves() }.getOrDefault(emptyList()) }

    val list = tracks.data ?: emptyList()
    val scope = rememberCoroutineScope()
    val sort = remember(sortScope) { SortState(state.ctx.settings, sortScope) }
    val shown = sort.spec.apply(list)
    var liked by remember(item.id) { mutableStateOf(false) }
    var saved by remember(item.id) { mutableStateOf(false) }

    LaunchedEffect(item.id) {
        liked = runCatching { state.ctx.client.isLiked(item) }.getOrDefault(false)
        saved = runCatching { state.ctx.client.isSaved(item) }.getOrDefault(false)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        item { BackRow(state) }
        item {
            DetailHeader(
                title = title,
                subtitle = subtitle,
                description = description,
                cover = cover,
                round = false,
                meta = meta,
                actions = {
                    PlayButton(
                        onClick = { playAll(state, shown) },
                        isPlaying = state.ctx.player.isPlaying &&
                            shown.any { it.id == state.ctx.player.current?.id }
                    )
                    Spacer(Modifier.width(10.dp))
                    IconButton(onClick = {
                        if (shown.isEmpty()) return@IconButton
                        state.ctx.player.toggleShuffle()
                        playTrack(state, shown.random(), shown)
                    }) {
                        Icon(Icons.Rounded.Shuffle, contentDescription = UiStrings.s.shuffle)
                    }
                    IconButton(onClick = {
                        scope.launch {
                            val next = !liked
                            liked = next
                            runCatching { state.ctx.client.setLiked(item, next) }
                        }
                    }) {
                        Icon(
                            imageVector = if (liked) Icons.Rounded.Favorite
                            else Icons.Rounded.FavoriteBorder,
                            contentDescription = UiStrings.s.like,
                            tint = if (liked) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = {
                        scope.launch {
                            val next = !saved
                            saved = next
                            runCatching { state.ctx.client.setSaved(item, next) }
                            state.showToast(
                                if (next) UiStrings.s.savedToLibrary else UiStrings.s.removedFromLibrary
                            )
                        }
                    }) {
                        Icon(
                            imageVector = if (saved) Icons.Rounded.Bookmark
                            else Icons.Rounded.BookmarkBorder,
                            contentDescription = UiStrings.s.saveToLibrary,
                            tint = if (saved) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = {
                        list.forEach { state.ctx.downloads.download(it) }
                        state.showToast(UiStrings.s.downloading(list.size))
                    }) {
                        Icon(Icons.Rounded.Download, contentDescription = UiStrings.s.downloadAll)
                    }
                    IconButton(onClick = {
                        scope.launch {
                            val radioTracks = runCatching {
                                state.ctx.client.radioFor(item, item)?.allItems()
                            }.getOrNull()
                            if (radioTracks.isNullOrEmpty()) {
                                state.showToast(UiStrings.s.radioUnavailable)
                            } else {
                                state.ctx.player.playList(radioTracks, 0, item)
                                state.showToast(UiStrings.s.radioTracks(radioTracks.size))
                            }
                        }
                    }) {
                        Icon(Icons.Rounded.Podcasts, contentDescription = UiStrings.s.radio)
                    }
                    IconButton(onClick = {
                        scope.launch {
                            val url = runCatching { state.ctx.client.shareUrl(item) }.getOrNull()
                            if (url.isNullOrBlank()) {
                                state.showToast(UiStrings.s.linkUnavailable)
                            } else {
                                runCatching {
                                    java.awt.Toolkit.getDefaultToolkit().systemClipboard
                                        .setContents(
                                            java.awt.datatransfer.StringSelection(url),
                                            null
                                        )
                                }
                                state.showToast(UiStrings.s.linkCopied)
                            }
                        }
                    }) {
                        Icon(Icons.Rounded.Share, contentDescription = UiStrings.s.share)
                    }
                    SortControls(sort)
                }
            )
        }

        when {
            tracks.loading && tracks.data == null -> item { LoadingBox() }
            tracks.error != null -> item {
                ErrorBox(tracks.error!!) { tracks.reload() }
            }
            else -> items(shown.size) { index ->
                val track = shown[index]
                val isCurrent = state.ctx.player.current?.id == track.id
                Box(Modifier.padding(horizontal = 12.dp)) {
                    TrackRow(
                        track = track,
                        index = index,
                        isCurrent = isCurrent,
                        isPlaying = isCurrent && state.ctx.player.isPlaying,
                        onClick = { playTrack(state, track, shown) },
                        trailing = { DownloadButton(state, track) },
                        menu = { TrackMenuButton(state, track) }
                    )
                }
            }
        }

        val shelfList = shelves.data
        if (shelfList != null && shelfList.isNotEmpty()) {
            items(shelfList) { shelf ->
                Box(Modifier.padding(horizontal = 12.dp)) {
                    ShelfBlock(
                        shelf = shelf,
                        onOpen = { state.open(it) },
                        onPlayTrack = { track, tracks2 -> playTrack(state, track, tracks2) },
                        onPlayAll = { tracks2 -> playAll(state, tracks2) },
                        onOpenCategory = { state.openCategory(it) }
                    )
                }
            }
        }
    }
}

@Composable
fun DownloadButton(state: AppState, track: Track) {
    val downloads = state.ctx.downloads
    @Suppress("UNUSED_EXPRESSION")
    downloads.version // recompose when the download list changes

    val entry = downloads.entry(track.id)
    val progress = downloads.progress(track.id)
    val scope = rememberCoroutineScope()

    when {
        progress != null -> Box(
            modifier = Modifier.size(36.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp
            )
        }

        entry != null -> IconButton(onClick = {
            scope.launch { downloads.delete(track.id) }
        }) {
            Icon(
                Icons.Rounded.Download,
                contentDescription = UiStrings.s.removeDownloaded,
                tint = MaterialTheme.colorScheme.primary
            )
        }

        else -> IconButton(onClick = {
            downloads.download(track) { result ->
                result.onFailure { state.showToast(UiStrings.s.downloadFailed(it.message)) }
            }
        }) {
            Icon(
                Icons.Rounded.Download,
                contentDescription = UiStrings.s.download,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun BackRow(state: AppState) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = { state.back() }) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = UiStrings.s.back)
        }
    }
}

@Composable
fun DetailHeader(
    title: String,
    subtitle: String?,
    description: String?,
    cover: ImageHolder?,
    round: Boolean,
    meta: String?,
    actions: @Composable () -> Unit
) {
    val bitmap = rememberCover(cover)
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Row {
            androidx.compose.foundation.Image(
                bitmap = bitmap ?: androidx.compose.ui.graphics.ImageBitmap(1, 1),
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier
                    .size(180.dp)
                    .clip(if (round) CircleShape else RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
            Spacer(Modifier.width(22.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (!meta.isNullOrBlank()) {
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) { actions() }
            }
        }
        if (!description.isNullOrBlank()) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 14.dp)
            )
        }
        Spacer(Modifier.height(12.dp))
    }
}