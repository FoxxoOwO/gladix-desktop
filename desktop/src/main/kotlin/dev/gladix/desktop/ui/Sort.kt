package dev.gladix.desktop.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.brahmkshatriya.echo.common.models.Album
import dev.brahmkshatriya.echo.common.models.EchoMediaItem
import dev.brahmkshatriya.echo.common.models.Shelf
import dev.brahmkshatriya.echo.common.models.Track
import dev.gladix.desktop.core.PrefsSettings
import java.text.Collator
import java.util.Locale

/** How a list of tracks is ordered. `DEFAULT` keeps the order the API returned. */
enum class SortField(val key: String) {
    DEFAULT("default"),
    TITLE("title"),
    DURATION("duration"),
    ARTISTS("artists"),
    ALBUMS("albums");

    companion object {
        fun fromKey(key: String?): SortField =
            entries.firstOrNull { it.key.equals(key?.trim(), ignoreCase = true) } ?: DEFAULT
    }
}

/**
 * A sort/filter choice for one track list.
 *
 * [reversed] flips whatever order [field] produces, including [SortField.DEFAULT]
 * - on a playlist that is the "play it backwards" switch.
 */
data class SortSpec(
    val field: SortField = SortField.DEFAULT,
    val reversed: Boolean = false
) {
    // `this.field`, not `field`: inside a getter `field` is the backing-field keyword.
    val isDefault: Boolean get() = this.field == SortField.DEFAULT && !reversed

    fun apply(tracks: List<Track>): List<Track> {
        if (isDefault) return tracks
        if (field == SortField.DEFAULT) {
            return if (reversed) tracks.reversed() else tracks
        }
        val collator = Collator.getInstance(Locale.getDefault()).apply {
            strength = Collator.SECONDARY
        }
        val comparator: Comparator<Track> = when (field) {
            SortField.DEFAULT -> Comparator { _, _ -> 0 }
            SortField.TITLE -> Comparator { a, b -> collator.compare(a.title, b.title) }
            SortField.DURATION -> compareBy { it.duration ?: 0L }
            SortField.ARTISTS -> Comparator { a, b ->
                collator.compare(a.artistNames(), b.artistNames())
            }

            SortField.ALBUMS -> Comparator { a, b ->
                collator.compare(a.album?.title.orEmpty(), b.album?.title.orEmpty())
            }
        }
        val sorted = tracks.sortedWith(comparator)
        return if (reversed) sorted.reversed() else sorted
    }


    /**
     * Orders albums, playlists, artists and other media items by the same fields.
     *
     * Only [SortField.TITLE], [SortField.ARTISTS] and [SortField.ALBUMS] make sense for
     * them; [SortField.DURATION] keeps the order the API returned, and reversing that
     * order is still honoured.
     */
    fun applyItems(items: List<EchoMediaItem>): List<EchoMediaItem> {
        if (isDefault) return items
        if (field == SortField.DEFAULT || field == SortField.DURATION) {
            return if (reversed) items.reversed() else items
        }
        val collator = Collator.getInstance(Locale.getDefault()).apply {
            strength = Collator.SECONDARY
        }
        val comparator: Comparator<EchoMediaItem> = when (field) {
            SortField.DEFAULT, SortField.DURATION -> Comparator { _, _ -> 0 }
            SortField.TITLE -> Comparator { a, b -> collator.compare(itemTitle(a), itemTitle(b)) }
            SortField.ARTISTS -> Comparator { a, b ->
                collator.compare(itemSubtitle(a).orEmpty(), itemSubtitle(b).orEmpty())
            }

            SortField.ALBUMS -> Comparator { a, b ->
                collator.compare(albumTitleOf(a), albumTitleOf(b))
            }
        }
        val sorted = items.sortedWith(comparator)
        return if (reversed) sorted.reversed() else sorted
    }
}

private fun albumTitleOf(item: EchoMediaItem): String = when (item) {
    is Track -> item.album?.title.orEmpty()
    is Album -> item.title
    else -> item.title
}

/**
 * Applies [spec] to every shelf of a feed, so sorting a tab orders all of its rows.
 *
 * The library delivers its tracks as one [Shelf.Item] per track, so a feed made only of
 * loose track rows is sorted as a whole list. Otherwise track shelves are reordered
 * directly, item shelves of tracks keep their wraps by ranking ids, and item shelves of
 * albums, playlists or artists are ordered by the field the user picked. Categories are
 * ordered by title.
 */
fun sortShelves(shelves: List<Shelf>, spec: SortSpec): List<Shelf> {
    if (spec.isDefault) return shelves

    // The whole feed is loose rows: sort them as one list instead of shelf by shelf.
    val looseTracks = shelves.filterIsInstance<Shelf.Item>().mapNotNull { it.media as? Track }
    if (looseTracks.size > 1 && looseTracks.size == shelves.size) {
        val byId = shelves.associateBy { it.id }
        return spec.apply(looseTracks).mapNotNull { byId[it.id] }
    }

    return shelves.map { shelf ->
        when (shelf) {
            is Shelf.Lists.Tracks -> shelf.copy(list = spec.apply(shelf.list))
            is Shelf.Lists.Items ->
                if (shelf.list.all { it is Track }) {
                    val tracks = shelf.list.filterIsInstance<Track>()
                    shelf.copy(list = spec.apply(tracks))
                } else {
                    shelf.copy(list = spec.applyItems(shelf.list))
                }

            is Shelf.Lists.Categories -> {
                val collator = Collator.getInstance(Locale.getDefault()).apply {
                    strength = Collator.SECONDARY
                }
                val sorted = shelf.list.sortedWith { a, b -> collator.compare(a.title, b.title) }
                shelf.copy(list = if (spec.reversed) sorted.reversed() else sorted)
            }

            else -> shelf
        }
    }
}

internal fun Track.artistNames(): String = artists.joinToString(", ") { it.name }

/**
 * Sort state for one list, loaded from and saved to settings.properties.
 *
 * Persisting is what the "Save" checkbox in the sheet controls: with it off the
 * sheet only affects the current screen until it is closed.
 */
class SortState(private val settings: PrefsSettings?, private val scope: String) {

    var spec by mutableStateOf(load())
        private set

    private fun fieldKey() = "${PrefsSettings.KEY_SORT_FIELD_PREFIX}$scope"
    private fun reversedKey() = "${PrefsSettings.KEY_SORT_REVERSED_PREFIX}$scope"

    private fun load(): SortSpec {
        val store = settings ?: return SortSpec()
        return SortSpec(
            field = SortField.fromKey(store.getStringOrDefault(fieldKey(), SortField.DEFAULT.key)),
            reversed = store.getStringOrDefault(reversedKey(), "false").toBoolean()
        )
    }

    /** Applies a choice; [persist] writes it for the next launch. */
    fun set(next: SortSpec, persist: Boolean) {
        spec = next
        if (!persist) return
        settings?.putString(fieldKey(), next.field.key)
        settings?.putString(reversedKey(), next.reversed.toString())
    }

    fun reset(persist: Boolean = true) = set(SortSpec(), persist)
}
