package dev.gladix.desktop.core

import androidx.compose.ui.input.key.Key

/** A playback action bound to a key combination. */
enum class Shortcut {
    PlayPause,
    Next,
    Previous,
    Search,
    VolumeUp,
    VolumeDown,
    SeekForward,
    SeekBack,
    Mute,
    Back
}

/**
 * The keyboard map of the main window.
 *
 * Deliberately small and free of ambiguity: a bare arrow belongs to whatever
 * list has focus, so skipping a track needs the modifier. Space, M and Escape
 * are only taken here when no text field consumed them (the window listens
 * after the content), which is what keeps typing in the search field possible.
 */
object Shortcuts {

    fun of(key: Key, ctrl: Boolean = false, shift: Boolean = false, alt: Boolean = false): Shortcut? {
        if (ctrl) return when (key) {
            Key.DirectionRight -> if (shift) Shortcut.SeekForward else Shortcut.Next
            Key.DirectionLeft -> if (shift) Shortcut.SeekBack else Shortcut.Previous
            Key.DirectionUp -> Shortcut.VolumeUp
            Key.DirectionDown -> Shortcut.VolumeDown
            Key.F -> Shortcut.Search
            else -> null
        }
        if (shift || alt) return null
        return when (key) {
            Key.Spacebar -> Shortcut.PlayPause
            Key.M -> Shortcut.Mute
            Key.Escape -> Shortcut.Back
            else -> null
        }
    }
}
