package dev.gladix.desktop.ui

import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.gladix.desktop.core.player.RepeatMode
import dev.gladix.desktop.core.player.PlayerController
import dev.gladix.desktop.ui.text.UiStrings
import kotlinx.coroutines.launch

@Composable
fun PlayerBar(
    state: AppState,
    onOpenQueue: () -> Unit,
    onOpenNowPlaying: () -> Unit,
    /**
     * Off while the now playing screen is open: that screen draws its own seek
     * bar, so keeping this one visible showed the same progress twice.
     */
    showProgress: Boolean = true
) {
    val player = state.ctx.player
    val track = player.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    Surface(
        tonalElevation = 3.dp,
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (showProgress) SeekBar(player)
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // ---- track info
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onOpenNowPlaying() }
                        .padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (track != null) {
                        Cover(track.cover, Modifier.size(52.dp), corner = 8.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f, fill = false)) {
                            Text(
                                text = track.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = track.artists.joinToString(", ") { it.name },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        QualityChip(state)
                    } else {
                        Text(
                            text = UiStrings.s.nothingPlayingShort,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // ---- transport
                IconButton(onClick = { player.previous() }, enabled = track != null) {
                    Icon(Icons.Rounded.SkipPrevious, contentDescription = UiStrings.s.previous)
                }
                IconButton(
                    onClick = { player.togglePlayPause() },
                    enabled = track != null,
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.primary)
                ) {
                    Icon(
                        imageVector = if (player.isPlaying) Icons.Rounded.Pause
                        else Icons.Rounded.PlayArrow,
                        contentDescription = UiStrings.s.play,
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
                IconButton(onClick = { player.next() }, enabled = track != null) {
                    Icon(Icons.Rounded.SkipNext, contentDescription = UiStrings.s.next)
                }

                // ---- modes + volume
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { player.toggleShuffle() }) {
                        Icon(
                            Icons.Rounded.Shuffle,
                            contentDescription = UiStrings.s.shuffle,
                            tint = if (player.shuffle) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { player.cycleRepeat() }) {
                        Icon(
                            imageVector = when (player.repeatMode) {
                                RepeatMode.ONE -> Icons.Rounded.RepeatOne
                                else -> Icons.Rounded.Repeat
                            },
                            contentDescription = UiStrings.s.repeat,
                            tint = if (player.repeatMode == RepeatMode.OFF)
                                MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.primary
                        )
                    }
                    if (track != null) {
                        val liked = remember(track.id) { mutableStateOf(false) }
                        androidx.compose.runtime.LaunchedEffect(track.id) {
                            liked.value = state.ctx.client.isLiked(track)
                        }
                        IconButton(onClick = {
                            scope.launch {
                                val next = !liked.value
                                liked.value = next
                                runCatching { state.ctx.client.setLiked(track, next) }
                            }
                        }) {
                            Icon(
                                imageVector = if (liked.value) Icons.Rounded.Favorite
                                else Icons.Rounded.FavoriteBorder,
                                contentDescription = UiStrings.s.like,
                                tint = if (liked.value) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(onClick = onOpenQueue) {
                        Icon(Icons.AutoMirrored.Rounded.QueueMusic, contentDescription = UiStrings.s.navQueue)
                    }
                    if (track != null) {
                        TrackMenuButton(state, track)
                    }
                    VolumeControl(player)
                }
            }
        }
    }
}

@Composable
private fun SeekBar(player: PlayerController) {
    val duration = player.durationMs
    val position = player.positionMs
    if (duration <= 0) return
    var dragging by remember { mutableStateOf<Float?>(null) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = formatDuration(if (dragging != null) (dragging!! * duration).toLong() else position),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Slider(
            value = dragging ?: (position.toFloat() / duration).coerceIn(0f, 1f),
            onValueChange = { dragging = it },
            onValueChangeFinished = {
                val target = dragging
                dragging = null
                if (target != null) player.seekTo((target * duration).toLong())
            },
            modifier = Modifier.weight(1f).padding(horizontal = 10.dp)
        )
        Text(
            text = formatDuration(duration),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun VolumeControl(player: PlayerController) {
    var muted by remember { mutableStateOf(false) }
    var before by remember { mutableStateOf(0.85f) }

    // The wheel is the quickest way to nudge the volume, so a hover over the
    // whole control (icon + slider) listens for it, not just the thin slider.
    val onScroll: (Float) -> Unit = { delta ->
        muted = false
        player.changeVolume(player.volume + delta * VOLUME_STEP)
    }

    // A plain hover tooltip; a slider is not focusable enough for TooltipBox
    // to anchor a proper popup, so this is the lightweight variant.
    TooltipArea(
        tooltip = {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = 6.dp
            ) {
                Text(
                    text = UiStrings.s.volumeScrollHint,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        },
        delayMillis = 700
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.onPointerScroll(onScroll)
        ) {
        IconButton(onClick = {
            muted = !muted
            if (muted) {
                before = player.volume
                player.changeVolume(0f)
            } else {
                player.changeVolume(before.takeIf { it > 0.01f } ?: 0.85f)
            }
        }) {
            Icon(
                imageVector = if (muted || player.volume <= 0.01f) Icons.AutoMirrored.Rounded.VolumeOff
                else Icons.AutoMirrored.Rounded.VolumeUp,
                contentDescription = UiStrings.s.volume
            )
        }
        Box(modifier = Modifier.width(96.dp)) {
            Slider(
                value = player.volume,
                onValueChange = {
                    muted = false
                    player.changeVolume(it)
                }
            )
        }
        }
    }
}

/** One wheel notch (or one step of a smooth trackpad scroll). */
private const val VOLUME_STEP = 0.02f

/**
 * Mouse-wheel volume, without losing the widget it is attached to.
 *
 * Compose Desktop reports a wheel move as a pointer event whose [PointerEventType]
 * is [PointerEventType.Scroll]; the vertical scroll distance arrives in
 * [PointerInputChange.scrollDelta]. Because a normal `onPointerEvent` handler
 * would swallow the event for the slider below, the handler consumes nothing and
 * only reacts to non-zero scroll deltas.
 */
private fun Modifier.onPointerScroll(onScroll: (Float) -> Unit): Modifier = composed {
    pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                if (event.type == PointerEventType.Scroll) {
                    var delta = 0f
                    event.changes.forEach { change ->
                        delta += change.scrollDelta.y
                    }
                    if (delta != 0f) {
                        // Scrolling up (negative in Compose) raises the volume.
                        onScroll(-delta)
                    }
                }
            }
        }
    }
}