package dev.gladix.desktop.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
        val collator = Collator.getInstance(Locale.getDefault()).apply {
            strength = Collator.SECONDARY
        }
        val comparator: Comparator<Track> = when (field) {
            // Reversing the original order is meaningful on its own.
            SortField.DEFAULT -> Comparator { a, b -> tracks.indexOf(a) - tracks.indexOf(b) }
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
