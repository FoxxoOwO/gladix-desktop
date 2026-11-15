package dev.gladix.desktop.core.platform

import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.WString
import com.sun.jna.win32.W32APIOptions
import java.awt.Window

/**
 * Paints the native Windows title bar in the colours of the app instead of the
 * system grey/white one.
 *
 * The window keeps its ordinary decorated frame — so Aero snap, the resize
 * borders, the system menu and the taskbar thumbnail all stay native — and only
 * DWM's own colours are overridden:
 *
 * * `DWMWA_USE_IMMERSIVE_DARK_MODE` (20, 19 on pre-20H1) draws the caption,
 *   the system menu and the window buttons in the dark style on Windows 10.
 * * `DWMWA_CAPTION_COLOR` (35), `DWMWA_TEXT_COLOR` (36) and
 *   `DWMWA_BORDER_COLOR` (34) make the caption exactly the app background and
 *   the title text the app foreground colour (Windows 11 22000+; older builds
 *   simply ignore them and keep the immersive dark caption).
 *
 * Every call is best effort: on a non-Windows JVM, an old Windows build or a
 * missing window handle the method silently does nothing so the UI still
 * starts.
 */
object WindowChrome {

    // Colours are COLORREF (0x00BBGGRR), i.e. the byte order is reversed.
    /** `DarkColors.background` = #0F0D14 — identical to the sidebar/app canvas. */
    private const val CAPTION_COLOR = 0x0014_0D0F
    /** `DarkColors.onBackground` = #ECE9F2. */
    private const val TEXT_COLOR = 0x00F2_E9EC
    /** `DarkColors.surfaceVariant` = #221D2C, the hairline around the window. */
    private const val BORDER_COLOR = 0x002C_1D22

    /** The same three roles from `LightColors`: #FDFBFF, #1B1721 and #B6ACC4. */
    private const val LIGHT_CAPTION_COLOR = 0x00FF_FBFD
    private const val LIGHT_TEXT_COLOR = 0x0021_171B
    private const val LIGHT_BORDER_COLOR = 0x00C4_ACB6

    private const val DWMWA_USE_IMMERSIVE_DARK_MODE = 20
    private const val DWMWA_USE_IMMERSIVE_DARK_MODE_PRE_20H1 = 19
    private const val DWMWA_BORDER_COLOR = 34
    private const val DWMWA_CAPTION_COLOR = 35
    private const val DWMWA_TEXT_COLOR = 36

    private interface DwmApi : Library {
        fun DwmSetWindowAttribute(hwnd: Pointer, attribute: Int, value: Pointer, size: Int): Int
    }

    private interface User32 : Library {
        fun FindWindow(lpClassName: WString?, lpWindowName: WString?): Pointer?
        fun IsWindow(hwnd: Pointer?): Boolean
        fun SetWindowPos(
            hwnd: Pointer,
            insertAfter: Pointer?,
            x: Int,
            y: Int,
            width: Int,
            height: Int,
            flags: Int
        ): Boolean
    }

    private val isWindows = System.getProperty("os.name").orEmpty().lowercase().contains("win")

    /**
     * Must run on the AWT thread once the window exists — call it from a
     * `LaunchedEffect` inside the window content. [dark] selects the palette the
     * UI is drawn with; the caption follows it so the title bar never belongs to
     * the other scheme.
     */
    fun applyFrame(window: Window, dark: Boolean = true, title: String = "Gladix") {
        if (!isWindows) return
        runCatching {
            val hwnd = windowHandle(window, title) ?: return
            val dwm = Native.load("dwmapi", DwmApi::class.java) as DwmApi
            setInt(dwm, hwnd, DWMWA_USE_IMMERSIVE_DARK_MODE, if (dark) 1 else 0)
            setInt(dwm, hwnd, DWMWA_USE_IMMERSIVE_DARK_MODE_PRE_20H1, if (dark) 1 else 0)
            setInt(dwm, hwnd, DWMWA_CAPTION_COLOR, if (dark) CAPTION_COLOR else LIGHT_CAPTION_COLOR)
            setInt(dwm, hwnd, DWMWA_TEXT_COLOR, if (dark) TEXT_COLOR else LIGHT_TEXT_COLOR)
            setInt(dwm, hwnd, DWMWA_BORDER_COLOR, if (dark) BORDER_COLOR else LIGHT_BORDER_COLOR)
            // Force a non-client repaint, otherwise an already painted caption
            // keeps the previous colours until the window is resized.
            // SWP_NOSIZE | SWP_NOMOVE | SWP_NOZORDER | SWP_NOACTIVATE: repaint the
            // non-client area only.
            user32().SetWindowPos(hwnd, null, 0, 0, 0, 0, 0x0001 or 0x0002 or 0x0004 or 0x0010)
        }
    }

    /** Kept for callers that only ever want the dark caption. */
    fun applyDarkFrame(window: Window, title: String = "Gladix") = applyFrame(window, true, title)

    private fun user32(): User32 =
        Native.load("user32", User32::class.java, W32APIOptions.DEFAULT_OPTIONS) as User32

    /**
     * The HWND is not exposed by AWT, so it is looked up twice: first by the
     * AWT peer class (the frame Compose Desktop creates is a `SunAwtFrame`),
     * then by the window title alone, which also covers windows that were
     * created by a different AWT peer implementation.
     */
    private fun windowHandle(window: Window, title: String): Pointer? {
        val user32 = user32()
        val byClass = user32.FindWindow(WString("SunAwtFrame"), WString(title))
        if (byClass != null && byClass != Pointer.NULL && user32.IsWindow(byClass)) return byClass
        val byTitle = user32.FindWindow(null, WString(title))
        if (byTitle != null && byTitle != Pointer.NULL && user32.IsWindow(byTitle)) return byTitle
        // Maximised/renamed windows still answer to the AWT class name.
        return user32.FindWindow(WString("SunAwtFrame"), null)?.takeIf {
            it != Pointer.NULL && user32.IsWindow(it)
        }
    }

    private fun setInt(dwm: DwmApi, hwnd: Pointer, attribute: Int, value: Int) {
        Memory(4).use { memory ->
            memory.setInt(0, value)
            dwm.DwmSetWindowAttribute(hwnd, attribute, memory, 4)
        }
    }
}
