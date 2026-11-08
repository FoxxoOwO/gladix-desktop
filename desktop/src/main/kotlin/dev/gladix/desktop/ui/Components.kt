package dev.gladix.desktop.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.brahmkshatriya.echo.common.models.EchoMediaItem
import dev.brahmkshatriya.echo.common.models.ImageHolder
import dev.brahmkshatriya.echo.common.models.Shelf
import dev.brahmkshatriya.echo.common.models.Track
import dev.gladix.desktop.core.Images
import dev.gladix.desktop.ui.text.UiStrings

@Composable
fun rememberCover(holder: ImageHolder?): ImageBitmap? {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, holder) {
        value = if (holder == null) null else Images.load(holder)
    }
    return bitmap
}

/** Square artwork with a music-note placeholder. */
@Composable
fun Cover(
    holder: ImageHolder?,
    modifier: Modifier = Modifier,
    corner: Dp = 10.dp
) {
    val bitmap = rememberCover(holder)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(corner))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxSize(0.35f)
            )
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    onMore: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        trailing?.invoke()
        if (onMore != null) {
            TextButton(onClick = onMore) { Text(UiStrings.s.showAll) }
        }
    }
}

@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(modifier = Modifier.size(28.dp))
    }
}

@Composable
fun ErrorBox(message: String, onRetry: (() -> Unit)? = null) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        if (onRetry != null) {
            TextButton(onClick = onRetry) {
                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(UiStrings.s.retry)
            }
        }
    }
}

@Composable
fun EmptyBox(text: String) {
    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** A single track line used by playlists, albums, queue, history and downloads. */
@Composable
fun TrackRow(
    track: Track,
    index: Int?,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showCover: Boolean = true,
    trailing: @Composable (() -> Unit)? = null,
    menu: @Composable (() -> Unit)? = null,
    secondary: (@Composable () -> Unit)? = null
) {
    val highlight = if (isCurrent) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.onSurface

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (index != null) {
            Box(
                modifier = Modifier.width(28.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isCurrent) {
                    Icon(
                        if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                } else {
                    Text(
                        text = (index + 1).toString(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
        }
        if (showCover) {
            Cover(track.cover, Modifier.size(40.dp), corner = 6.dp)
            Spacer(Modifier.width(10.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                color = highlight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val artist = track.artists.joinToString(", ") { it.name }
            if (artist.isNotBlank()) {
                Text(
                    text = artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            secondary?.invoke()
        }
        if (track.duration != null) {
            Text(
                text = formatDuration(track.duration),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }
        trailing?.invoke()
        menu?.invoke()
    }
}

/** Card for albums, playlists, artists and categories. */
@Composable
fun MediaCard(
    title: String,
    subtitle: String?,
    cover: ImageHolder?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    round: Boolean = false,
    /** Grids let the column stretch; horizontal strips keep the fixed card width. */
    fillWidth: Boolean = false
) {
    Column(
        modifier = modifier
            .let { if (fillWidth) it else it.width(160.dp) }
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        Cover(
            cover,
            Modifier.fillMaxWidth().aspectRatio(1f).then(
                if (round) Modifier.clip(CircleShape) else Modifier
            ),
            corner = if (round) 80.dp else 10.dp
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle!!,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** One media item (album, playlist, artist) as a list row. */
@Composable
fun MediaRow(
    item: EchoMediaItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null
) {
    val subtitle = itemSubtitle(item)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Cover(
            item.cover,
            Modifier.size(52.dp),
            corner = if (item is dev.brahmkshatriya.echo.common.models.Artist) 26.dp else 8.dp
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = itemTitle(item),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        trailing?.invoke()
    }
}

/** Renders one shelf (row of cards or a short track list). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ShelfBlock(
    shelf: Shelf,
    onOpen: (EchoMediaItem) -> Unit,
    onPlayTrack: (Track, List<Track>) -> Unit,
    onPlayAll: (List<Track>) -> Unit,
    onOpenCategory: (Shelf.Category) -> Unit = {},
    /**
     * Opens a "see everything" page. Only wired up when the shelf can keep
     * loading more items — a fixed list of 12 albums has nothing more to show.
     */
    onShowAll: ((AllItems) -> Unit)? = null
) {
    when (shelf) {
        is Shelf.Lists.Items -> {
            val items = shelf.list
            if (items.isEmpty()) return
            // The "more" feed is what makes an "all albums" page possible; without
            // it the shelf is already complete and the button would be a dead end.
            val showAll = shelf.more?.let { more ->
                onShowAll?.let { open ->
                    { open(AllItems.Albums(shelf.title, more)) }
                }
            }
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                SectionHeader(shelf.title, onMore = showAll)
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    items(items) { item ->
                        MediaCard(
                            title = itemTitle(item),
                            subtitle = itemSubtitle(item),
                            cover = item.cover,
                            round = item is dev.brahmkshatriya.echo.common.models.Artist,
                            onClick = { onOpen(item) }
                        )
                    }
                }
            }
        }

        is Shelf.Lists.Tracks -> {
            val tracks = shelf.list
            if (tracks.isEmpty()) return
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                SectionHeader(
                    shelf.title,
                    trailing = {
                        TextButton(onClick = { onPlayAll(tracks) }) { Text(UiStrings.s.play) }
                    }
                )
                tracks.take(5).forEachIndexed { index, track ->
                    TrackRow(
                        track = track,
                        index = index,
                        isCurrent = false,
                        isPlaying = false,
                        onClick = { onPlayTrack(track, tracks) }
                    )
                }
            }
        }

        is Shelf.Lists.Categories -> {
            val categories = shelf.list
            if (categories.isEmpty()) return
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                SectionHeader(shelf.title)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(categories) { category ->
                        MediaCard(
                            title = category.title,
                            subtitle = category.subtitle,
                            cover = category.image,
                            onClick = { onOpenCategory(category) }
                        )
                    }
                }
            }
        }

        is Shelf.Item -> {
            // Every tab of the library feed is a flat list of these, so an album,
            // playlist or artist shelf item has to render as a row - not be dropped.
            val item = shelf.media
            if (item is Track) {
                TrackRow(
                    track = item,
                    index = null,
                    isCurrent = false,
                    isPlaying = false,
                    onClick = { onPlayTrack(item, listOf(item)) }
                )
            } else {
                MediaRow(item = item, onClick = { onOpen(item) })
            }
        }

        is Shelf.Category -> {
            MediaCard(
                title = shelf.title,
                subtitle = shelf.subtitle,
                cover = shelf.image,
                onClick = { onOpenCategory(shelf) }
            )
        }

        else -> {
            // Unknown shelf type - ignore silently, the extension may add more.
        }
    }
}

@Composable
fun ShelvesColumn(
    shelves: List<Shelf>,
    onOpen: (EchoMediaItem) -> Unit,
    onPlayTrack: (Track, List<Track>) -> Unit,
    onPlayAll: (List<Track>) -> Unit,
    modifier: Modifier = Modifier,
    onOpenCategory: (Shelf.Category) -> Unit = {},
    onShowAll: ((AllItems) -> Unit)? = null,
    header: (@Composable () -> Unit)? = null
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        if (header != null) item { header() }
        shelfRows(shelves, onOpen, onPlayTrack, onPlayAll, onOpenCategory, onShowAll)
    }
}

/**
 * Emits [shelves] into a lazy list, expanding a shelf that holds nothing but
 * tracks into real rows.
 *
 * A library tab arrives as ONE shelf wrapping hundreds of items (the Tracks tab
 * is a single shelf with the whole favourites list), so rendering it as a card
 * strip would hide the collection behind a horizontal scroll. Everything else
 * keeps the compact [ShelfBlock] look.
 */
fun LazyListScope.shelfRows(
    shelves: List<Shelf>,
    onOpen: (EchoMediaItem) -> Unit,
    onPlayTrack: (Track, List<Track>) -> Unit,
    onPlayAll: (List<Track>) -> Unit,
    onOpenCategory: (Shelf.Category) -> Unit,
    onShowAll: ((AllItems) -> Unit)? = null
) {
    var i = 0
    while (i < shelves.size) {
        val shelf = shelves[i]
        val trackList = (shelf as? Shelf.Lists.Items)
            ?.list
            ?.takeIf { it.isNotEmpty() && it.all { item -> item is Track } }
            ?.map { it as Track }
        val flat = if (trackList == null) flatTrackRun(shelves, i) else null
        when {
            trackList != null -> {
                item {
                    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        SectionHeader(
                            shelf.title,
                            trailing = {
                                TextButton(onClick = { onPlayAll(trackList) }) {
                                    Text(UiStrings.s.play)
                                }
                            }
                        )
                    }
                }
                itemsIndexed(trackList) { index, track ->
                    TrackRow(
                        track = track,
                        index = index,
                        isCurrent = false,
                        isPlaying = false,
                        onClick = { onPlayTrack(track, trackList) }
                    )
                }
                i++
            }

            flat != null -> {
                // A library tab that is not a track shelf still arrives as one
                // Shelf.Item per track, so the whole run is emitted as rows and a
                // click queues the rest of it instead of a single song.
                itemsIndexed(flat) { _, track ->
                    TrackRow(
                        track = track,
                        index = null,
                        isCurrent = false,
                        isPlaying = false,
                        onClick = { onPlayTrack(track, flat) }
                    )
                }
                i += flat.size
            }

            else -> {
                item {
                    ShelfBlock(
                        shelf,
                        onOpen,
                        onPlayTrack,
                        onPlayAll,
                        onOpenCategory,
                        onShowAll
                    )
                }
                i++
            }
        }
    }
}

/**
 * Collects the longest run of consecutive [Shelf.Item] shelves wrapping a track,
 * starting at [index]. Returns null when [index] does not start such a run.
 */
internal fun flatTrackRun(shelves: List<Shelf>, index: Int): List<Track>? {
    val tracks = ArrayList<Track>()
    var i = index
    while (i < shelves.size) {
        val media = (shelves[i] as? Shelf.Item)?.media as? Track ?: break
        tracks += media
        i++
    }
    return tracks.takeIf { it.isNotEmpty() }
}

/** Play/pause circle button used in headers. */
@Composable
fun PlayButton(onClick: () -> Unit, isPlaying: Boolean = false, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(28.dp)
        )
    }
}