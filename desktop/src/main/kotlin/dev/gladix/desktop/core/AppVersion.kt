package dev.gladix.desktop.core

/**
 * The version this build reports. Kept in one place so the about screen, the
 * update check and the Discord presence all agree.
 */
object AppVersion {
    const val NAME = "1.4.3"

    /** `owner/repo` of the GitHub project the update check asks for releases. */
    const val REPO = "FoxxoOwO/gladix-desktop"

    val tag: String get() = "v$NAME"
}
