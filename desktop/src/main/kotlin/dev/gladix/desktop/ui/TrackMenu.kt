package dev.gladix.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.brahmkshatriya.echo.common.models.Playlist
import dev.brahmkshatriya.echo.common.models.Track
import dev.gladix.desktop.core.allItems
import dev.gladix.desktop.ui.text.UiStrings
import kotlinx.coroutines.launch

/**
 * The three-dot menu every track row and the player bar use.
 *
 * Mirrors the track sheet of the Android app: play, add to next, add to queue,
 * library and like toggles, save to playlist, download, jump to artist/album,
 * radio and share.
 */
@Composable
fun TrackMenuButton(
    state: AppState,
    track: Track,
    modifier: Modifier = Modifier,
    startExpanded: Boolean = false,
    onPlay: (() -> Unit)? = null
) {
    val strings = UiStrings.s
    val player = state.ctx.player
    val downloads = state.ctx.downloads
    val scope = rememberCoroutineScope()

    var open by remember { mutableStateOf(startExpanded) }
    var liked by remember(track.id) { mutableStateOf(false) }
    var saved by remember(track.id) { mutableStateOf(false) }
    var playlistDialog by remember(track.id) { mutableStateOf(false) }

    // The two flags live on Deezer, so they are read when the menu opens.
    LaunchedEffect(open, track.id) {
        if (!open) return@LaunchedEffect
        liked = runCatching { state.ctx.client.isLiked(track) }.getOrDefault(false)
        saved = runCatching { state.ctx.client.isSaved(track) }.getOrDefault(false)
    }

    @Suppress("UNUSED_EXPRESSION")
    downloads.version // recompose when a download starts or finishes

    val downloaded = downloads.entry(track.id) != null

    Box(modifier = modifier) {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Rounded.MoreVert, contentDescription = strings.moreActions)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            MenuHeader(track)
            HorizontalDivider()

            MenuItem(Icons.Rounded.PlayArrow, strings.play) {
                open = false
                if (onPlay != null) onPlay() else player.playNow(track)
            }
            MenuItem(Icons.AutoMirrored.Rounded.PlaylistPlay, strings.addToNext) {
                open = false
                player.addNext(track)
                state.showToast(strings.addedNext(track.title))
            }
            MenuItem(Icons.AutoMirrored.Rounded.QueueMusic, strings.addToQueue) {
                open = false
                player.addToQueue(listOf(track))
                state.showToast(strings.addedToQueue(track.title))
            }

            HorizontalDivider()

            MenuItem(
                if (saved) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                if (saved) strings.removeFromLibrary else strings.saveToLibrary
            ) {
                open = false
                val next = !saved
                saved = next
                scope.launch {
                    runCatching { state.ctx.client.setSaved(track, next) }
                    state.showToast(
                        if (next) strings.savedToLibrary else strings.removedFromLibrary
                    )
                }
            }
            MenuItem(
                if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                if (liked) strings.unlike else strings.like
            ) {
                open = false
                val next = !liked
                liked = next
                scope.launch { runCatching { state.ctx.client.setLiked(track, next) } }
            }
            MenuItem(Icons.AutoMirrored.Rounded.PlaylistAdd, strings.saveToPlaylist) {
                open = false
                playlistDialog = true
            }

            HorizontalDivider()

            MenuItem(
                if (downloaded) Icons.Rounded.DownloadDone else Icons.Rounded.Download,
                if (downloaded) strings.removeDownloaded else strings.download
            ) {
                open = false
                if (downloaded) {
                    scope.launch { downloads.delete(track.id) }
                } else {
                    downloads.download(track) { result ->
                        result.onFailure { state.showToast(strings.downloadFailed(it.message)) }
                    }
                }
            }

            HorizontalDivider()

            val artist = track.artists.firstOrNull()
            if (artist != null) {
                MenuItem(Icons.Rounded.Person, artist.name) {
                    open = false
                    state.open(artist)
                }
            }
            val album = track.album
            if (album != null) {
                MenuItem(Icons.Rounded.Album, album.title) {
                    open = false
                    state.open(album)
                }
            }
            MenuItem(Icons.Rounded.Podcasts, strings.radio) {
                open = false
                scope.launch {
                    val tracks = runCatching {
                        state.ctx.client.radioFor(track, null)?.allItems()
                    }.getOrNull()
                    if (tracks.isNullOrEmpty()) {
                        state.showToast(strings.radioUnavailable)
                    } else {
                        player.playList(tracks, 0, track)
                        state.showToast(strings.radioTracks(tracks.size))
                    }
                }
            }
            MenuItem(Icons.Rounded.Share, strings.share) {
                open = false
                scope.launch {
                    val url = runCatching { state.ctx.client.shareUrl(track) }.getOrNull()
                    if (url.isNullOrBlank()) {
                        state.showToast(strings.linkUnavailable)
                    } else {
                        copyToClipboard(url)
                        state.showToast(strings.linkCopied)
                    }
                }
            }
        }
    }

    if (playlistDialog) {
        SaveToPlaylistDialog(state, track) { playlistDialog = false }
    }
}

@Composable
private fun MenuHeader(track: Track) {
    Row(
        modifier = Modifier.width(300.dp).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Cover(track.cover, Modifier.size(48.dp), corner = 8.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val artists = track.artistNames()
            Text(
                text = if (artists.isBlank()) "" else artists,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun MenuItem(icon: ImageVector, label: String, onClick: () -> Unit) {
    DropdownMenuItem(
        text = {
            Text(
                text = label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        leadingIcon = { Icon(icon, contentDescription = null) },
        onClick = onClick
    )
}

/** Copies to the system clipboard; fails silently in a headless environment. */
internal fun copyToClipboard(text: String) {
    runCatching {
        java.awt.Toolkit.getDefaultToolkit().systemClipboard
            .setContents(java.awt.datatransfer.StringSelection(text), null)
    }
}

/**
 * Pick one of the user's playlists, or create a new one, and append [track].
 */
@Composable
fun SaveToPlaylistDialog(state: AppState, track: Track, onDismiss: () -> Unit) {
    val strings = UiStrings.s
    val scope = rememberCoroutineScope()
    var creating by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    val playlists = rememberLoader(track.id) { state.ctx.client.editablePlaylists(track) }
    val list = playlists.data

    fun add(playlist: Playlist) {
        scope.launch {
            val result = runCatching { state.ctx.client.addToPlaylist(playlist, listOf(track)) }
            result.onFailure { state.showToast(strings.error(it.message)) }
            result.onSuccess { state.showToast(strings.addedToPlaylist(playlist.title)) }
            onDismiss()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.saveToPlaylist) },
        text = {
            Column(Modifier.width(340.dp)) {
                when {
                    playlists.loading && list == null -> LoadingBox()
                    list.isNullOrEmpty() -> Text(
                        text = strings.noEditablePlaylists,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    else -> LazyColumn(Modifier.heightIn(max = 280.dp)) {
                        items(list) { (playlist, contains) ->
                            PlaylistPickRow(
                                title = playlist.title,
                                subtitle = playlist.trackCount?.let { strings.trackCount(it) },
                                alreadyContains = contains
                            ) { add(playlist) }
                        }
                    }
                }
                Spacer(Modifier.size(8.dp))
                if (creating) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(strings.playlistName) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    TextButton(onClick = { creating = true }) {
                        Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(strings.newPlaylist)
                    }
                }
            }
        },
        confirmButton = {
            if (creating) {
                TextButton(
                    enabled = name.isNotBlank(),
                    onClick = {
                        scope.launch {
                            val result = runCatching {
                                val created = state.ctx.client.createPlaylist(name.trim())
                                state.ctx.client.addToPlaylist(created, listOf(track))
                                created
                            }
                            result.onFailure { state.showToast(strings.error(it.message)) }
                            result.onSuccess {
                                state.showToast(strings.addedToPlaylist(it.title))
                            }
                            onDismiss()
                        }
                    }
                ) { Text(strings.create) }
            } else {
                TextButton(onClick = onDismiss) { Text(strings.cancel) }
            }
        }
    )
}

@Composable
private fun PlaylistPickRow(
    title: String,
    subtitle: String?,
    alreadyContains: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = !alreadyContains, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (alreadyContains) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = "✓",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}
