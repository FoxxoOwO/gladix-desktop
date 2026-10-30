package dev.gladix.desktop

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dev.gladix.desktop.core.PrefsSettings
import dev.gladix.desktop.core.platform.AppIdentity
import dev.gladix.desktop.ui.App
import dev.gladix.desktop.ui.AppState
import dev.gladix.desktop.ui.GladixTheme
import dev.gladix.desktop.ui.text.Lang
import dev.gladix.desktop.ui.text.UiStrings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun main() {
    // Must happen before the first window exists, otherwise the taskbar and the
    // media flyout keep showing "Unknown app".
    AppIdentity.install()
    // The UI language is read before the first composition so the very first
    // frame already renders in the selected language.
    runCatching {
        val stored = PrefsSettings().getStringOrDefault(PrefsSettings.KEY_UI_LANG, Lang.Cs.code)
        UiStrings.load(stored)
    }
    launchUi()
}

private fun launchUi() = application {
    val context = remember { AppContext() }
    var ready by remember { androidx.compose.runtime.mutableStateOf(false) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { runCatching { context.init() } }
        ready = true
    }

    val state = remember { AppState(context) }

    Window(
        onCloseRequest = {
            runCatching { context.player.stop() }
            exitApplication()
        },
        title = "Gladix",
        icon = windowIcon(),
        state = rememberWindowState(width = 1280.dp, height = 820.dp)
    ) {
        window.minimumSize = java.awt.Dimension(980, 620)
        GladixTheme(useDark = true) {
            if (ready) {
                App(state)
            }
        }
    }
}

/**
 * Window/taskbar icon, read from the PNG that is baked into the jar by
 * `desktop/icons/IconGenerator.java`. Returns null if the resource is missing,
 * so a stripped build still starts.
 */
private fun windowIcon(): Painter? = runCatching {
    val bytes = IconResource::class.java.getResourceAsStream("/icon.png")!!.use { it.readBytes() }
    BitmapPainter(org.jetbrains.skia.Image.makeFromEncoded(bytes).toComposeImageBitmap())
}.getOrNull()

private object IconResource