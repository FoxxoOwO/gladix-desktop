package dev.gladix.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.brahmkshatriya.echo.common.models.Track
import dev.gladix.desktop.core.player.RepeatMode
import dev.gladix.desktop.ui.text.UiStrings
import kotlinx.coroutines.launch

/** Tabs under the transport controls of the now playing screen. */
private enum class NowTab { UpNext, Lyrics, Info }

/**
 * Full screen player: big artwork, the measured stream format, transport
 * controls and the queue/lyrics/info tabs of the Android app.
 */
@Composable
fun NowPlayingScreen(state: AppState) {
    val strings = UiStrings.s
    val player = state.ctx.player
    val track = player.current
    var tab by remember { mutableStateOf(NowTab.UpNext) }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { state.back() }) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = strings.back)
            }
            Spacer(Modifier.weight(1f))
            if (track != null) {
                LikeButton(state, track)
                TrackMenuButton(state, track)
            }
        }

        if (track == null) {
            EmptyBox(strings.nothingPlaying)
            return@Column
        }

        Box(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 8.dp)) {
                // ------------------------------------------------- artwork + transport
                Column(
                    modifier = Modifier.weight(1.1f).fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(Modifier.weight(1f))
                    Cover(
                        track.cover,
                        Modifier.fillMaxWidth(0.8f).widthIn(max = 420.dp).aspectRatio(1f),
                        corner = 18.dp
                    )
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 18.dp)
                    )
                    Text(
                        text = track.artistNames(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )

                    /* TransportRow(state) */
                    Spacer(Modifier.weight(1f))
                }

                Spacer(Modifier.width(28.dp))

                // ------------------------------------------------- tabs
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TabButton(strings.upNext, tab == NowTab.UpNext) { tab = NowTab.UpNext }
                        TabButton(strings.lyrics, tab == NowTab.Lyrics) { tab = NowTab.Lyrics }
                        TabButton(strings.info, tab == NowTab.Info) { tab = NowTab.Info }
                    }
                    Spacer(Modifier.height(10.dp))
                    Box(Modifier.fillMaxSize()) {
                        when (tab) {
                            NowTab.UpNext -> UpNextPane(state)
                            NowTab.Lyrics -> LyricsPane(state, track, Modifier.fillMaxSize())
                            NowTab.Info -> InfoPane(state, track)
                        }
                    }
                }
            }
            Box(Modifier.align(Alignment.BottomCenter).padding(horizontal = 28.dp, vertical = 12.dp)) {
                /* PositionRow(state) */
            }
        }
    }
}

@Composable
private fun TabButton(label: String, selected: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(
            text = label,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Time, the measured stream format and the total length, like the Android player. */
@Composable
private fun PositionRow(state: AppState) {
    val player = state.ctx.player
    val strings = UiStrings.s
    val duration = player.durationMs
    var dragging by remember { mutableStateOf<Float?>(null) }

    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Slider(
            value = if (duration > 0) {
                dragging ?: (player.positionMs.toFloat() / duration).coerceIn(0f, 1f)
            } else 0f,
            onValueChange = { dragging = it },
            onValueChangeFinished = {
                val target = dragging
                dragging = null
                if (target != null && duration > 0) player.seekTo((target * duration).toLong())
            },
            enabled = duration > 0,
            modifier = Modifier.fillMaxWidth()
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = formatDuration(
                    if (dragging != null && duration > 0) (dragging!! * duration).toLong()
                    else player.positionMs
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.weight(1f))
            QualityChip(state)
            Spacer(Modifier.weight(1f))
            Text(
                text = formatDuration(if (duration > 0) duration else null),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * The stream that is actually open, decoded and measured - not what the
 * quality setting asked for. Shows `FLAC · 44.1 kHz · 2ch` while playing.
 */
@Composable
fun QualityChip(state: AppState, modifier: Modifier = Modifier) {
    val strings = UiStrings.s
    val quality = state.ctx.player.streamQuality
    val label = quality?.label
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Text(
            text = label ?: strings.qualityUnknown,
            style = MaterialTheme.typography.labelMedium,
            color = if (label != null) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
    }
}

@Composable
private fun TransportRow(state: AppState) {
    val strings = UiStrings.s
    val player = state.ctx.player
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        IconButton(onClick = { player.toggleShuffle() }) {
            Icon(
                Icons.Rounded.Shuffle,
                contentDescription = strings.shuffle,
                tint = if (player.shuffle) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(14.dp))
        IconButton(onClick = { player.previous() }) {
            Icon(Icons.Rounded.SkipPrevious, contentDescription = strings.previous)
        }
        Spacer(Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .size(68.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .clickable { player.togglePlayPause() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (player.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = strings.play,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(34.dp)
            )
        }
        Spacer(Modifier.width(10.dp))
        IconButton(onClick = { player.next() }) {
            Icon(Icons.Rounded.SkipNext, contentDescription = strings.next)
        }
        Spacer(Modifier.width(14.dp))
        IconButton(onClick = { player.cycleRepeat() }) {
            Icon(
                imageVector = when (player.repeatMode) {
                    RepeatMode.ONE -> Icons.Rounded.RepeatOne
                    else -> Icons.Rounded.Repeat
                },
                contentDescription = strings.repeat,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun LikeButton(state: AppState, track: Track) {
    val strings = UiStrings.s
    val scope = rememberCoroutineScope()
    var liked by remember(track.id) { mutableStateOf(false) }
    LaunchedEffect(track.id) {
        liked = runCatching { state.ctx.client.isLiked(track) }.getOrDefault(false)
    }
    IconButton(onClick = {
        scope.launch {
            val next = !liked
            liked = next
            runCatching { state.ctx.client.setLiked(track, next) }
        }
    }) {
        Icon(
            imageVector = if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
            contentDescription = strings.like,
            tint = if (liked) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun UpNextPane(state: AppState) {
    val player = state.ctx.player
    val queue = player.queue
    val rest = if (player.index < 0) emptyList() else queue.drop(player.index + 1)
    if (rest.isEmpty()) {
        EmptyBox(UiStrings.s.queueEmpty)
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        items(rest.size) { offset ->
            val track = rest[offset]
            val position = player.index + 1 + offset
            val isCurrent = position == player.index
            TrackRow(
                track = track,
                index = null,
                isCurrent = isCurrent,
                isPlaying = isCurrent && player.isPlaying,
                onClick = { player.playAt(position) },
                menu = { TrackMenuButton(state, track) }
            )
        }
    }
}

@Composable
private fun InfoPane(state: AppState, track: Track) {
    val strings = UiStrings.s
    val album = track.album
    val quality = state.ctx.player.streamQuality
    val downloaded = state.ctx.downloads.entry(track.id)

    Column(Modifier.fillMaxSize().padding(end = 8.dp)) {
        InfoRow(strings.artistLabel, track.artistNames().ifBlank { strings.notAvailable })
        InfoRow(strings.infoAlbum, album?.title ?: strings.notAvailable)
        val year = (track.releaseDate ?: album?.releaseDate)?.year
        InfoRow(strings.infoYear, year?.toString() ?: strings.notAvailable)
        InfoRow(strings.infoDuration, formatDuration(track.duration).ifBlank { strings.notAvailable })
        val number = track.albumOrderNumber
        if (number != null && number > 0) {
            InfoRow(strings.infoTrackNumber, number.toString())
        }
        val label = album?.label
        if (!label.isNullOrBlank()) InfoRow(strings.infoLabel, label)
        InfoRow(
            text = quality?.label ?: strings.qualityUnknown,
            label = strings.streamQuality
        )
        InfoRow(
            text = if (downloaded != null) {
                "${downloaded.bytes / 1024 / 1024} MB • offline"
            } else {
                strings.notAvailable
            },
            label = strings.downloadedTracks
        )
        if (track.plays != null) {
            InfoRow("plays", formatCount(track.plays) ?: strings.notAvailable)
        }
    }
}

@Composable
private fun InfoRow(label: String, text: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(130.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
