package dev.gladix.desktop.core

import androidx.compose.ui.input.key.Key

/** A playback action bound to a key combination. */
enum class Shortcut {
    PlayPause,
    Next,
    Previous,
    Search
}

/**
 * The keyboard map of the main window.
 *
 * Deliberately small and free of ambiguity: a bare arrow belongs to whatever
 * list has focus, so skipping a track needs the modifier. Space is only taken
 * here when no text field consumed it (the window listens after the content),
 * which is what keeps typing a space in the search field possible.
 */
object Shortcuts {

    fun of(key: Key, ctrl: Boolean = false, shift: Boolean = false, alt: Boolean = false): Shortcut? {
        if (ctrl) return when (key) {
            Key.DirectionRight -> Shortcut.Next
            Key.DirectionLeft -> Shortcut.Previous
            Key.F -> Shortcut.Search
            else -> null
        }
        if (shift || alt) return null
        return if (key == Key.Spacebar) Shortcut.PlayPause else null
    }
}
