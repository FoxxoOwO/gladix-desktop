package dev.gladix.desktop.core.platform

import com.sun.jna.Function
import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.WString
import dev.gladix.desktop.core.AppPaths
import java.io.File

/**
 * Gives the process a real identity so Windows shows "Gladix" (instead of
 * "Neznámá aplikace") above the media widget in quick settings and in the
 * taskbar.
 *
 * Two things are needed and both are easy to get wrong:
 *
 * 1. `SetCurrentProcessExplicitAppUserModelID` has to run before the first
 *    window exists, and it is a *wide* API — passing a plain [String] makes JNA
 *    marshal ANSI bytes that the shell then reads as UTF-16, producing a
 *    garbage id.
 * 2. The id only resolves to a name and an icon when a Start Menu shortcut
 *    carries it as the `System.AppUserModel.ID` property. A registry entry
 *    under `HKCU\Software\Classes\AppUserModelId` is not enough for the media
 *    flyout. The shortcut is created here on first launch because the portable
 *    build ships without an installer.
 */
object AppIdentity {

    const val APP_USER_MODEL_ID = "Gladix.Desktop"
    private const val DISPLAY_NAME = "Gladix"

    private const val CLSID_SHELL_LINK = "00021401-0000-0000-C000-000000000046"
    private const val IID_SHELL_LINK_W = "000214F9-0000-0000-C000-000000000046"
    private const val IID_PROPERTY_STORE = "886D8EEB-8CF2-4446-8D02-CDBA1DBDCF99"
    private const val IID_PERSIST_FILE = "0000010b-0000-0000-C000-000000000046"
    private const val FMTID_APP_USER_MODEL = "9F4C2855-9F79-4B39-A8D0-E1D42DE1D5F3"
    private const val PID_APP_USER_MODEL_ID = 5
    private const val CLSCTX_INPROC_SERVER = 1
    private const val VT_LPWSTR = 31

    // IShellLinkW slots, counting the three IUnknown methods.
    private const val SL_SET_DESCRIPTION = 7
    private const val SL_SET_WORKING_DIRECTORY = 9
    private const val SL_SET_ICON_LOCATION = 17
    private const val SL_SET_PATH = 20

    // IPropertyStore slots.
    private const val PS_SET_VALUE = 6
    private const val PS_COMMIT = 7

    // IPersistFile slots.
    private const val PF_SAVE = 6

    private interface Shell32 : Library {
        fun SetCurrentProcessExplicitAppUserModelID(appId: WString): Int
    }

    private interface Ole32 : Library {
        fun CoInitializeEx(reserved: Pointer?, coInit: Int): Int
        fun CoCreateInstance(
            rclsid: Pointer,
            outer: Pointer?,
            clsContext: Int,
            riid: Pointer,
            out: Pointer
        ): Int
    }

    private val shell32 by lazy { Native.load("shell32", Shell32::class.java) }
    private val ole32 by lazy { Native.load("ole32", Ole32::class.java) }

    /**
     * Must run before any window exists. Safe to call more than once, and it
     * never throws: a failure only costs the friendly name in the shell.
     */
    fun install() {
        runCatching { shell32.SetCurrentProcessExplicitAppUserModelID(WString(APP_USER_MODEL_ID)) }
        // COM work, keep it off the UI thread.
        Thread({ runCatching { ensureStartMenuShortcut() } }, "gladix-identity").apply {
            isDaemon = true
            start()
        }
    }

    /** The launcher we should point a shortcut at, if we are running packaged. */
    private fun launcher(): File? {
        val path = System.getProperty("jpackage.app-path") ?: return null
        return File(path).takeIf { it.isFile }
    }

    private fun shortcutFile(): File {
        val programs = File(
            System.getenv("APPDATA") ?: System.getProperty("user.home"),
            "Microsoft/Windows/Start Menu/Programs"
        )
        return File(programs, "$DISPLAY_NAME.lnk")
    }

    private fun ensureStartMenuShortcut() {
        val exe = launcher() ?: return
        val link = shortcutFile()
        if (link.isFile) return
        link.parentFile?.mkdirs()

        ole32.CoInitializeEx(null, 0x2) // COINIT_APARTMENTTHREADED, ignore RPC_E_CHANGED_MODE

        val clsid = guid(CLSID_SHELL_LINK)
        val iidLink = guid(IID_SHELL_LINK_W)
        val out = Memory(Native.POINTER_SIZE.toLong())
        val hr = ole32.CoCreateInstance(clsid, null, CLSCTX_INPROC_SERVER, iidLink, out)
        if (hr != 0) {
            note("CoCreateInstance failed hr=$hr")
            return
        }
        val unk = out.getPointer(0) ?: return

        try {
            call(unk, SL_SET_DESCRIPTION, WString(DISPLAY_NAME))
            call(unk, SL_SET_PATH, WString(exe.absolutePath))
            call(unk, SL_SET_WORKING_DIRECTORY, WString(exe.parentFile.absolutePath))
            call(unk, SL_SET_ICON_LOCATION, WString(exe.absolutePath), 0)

            val store = queryInterface(unk, IID_PROPERTY_STORE)
            if (store == null) {
                note("QueryInterface(IPropertyStore) failed")
            } else {
                try {
                    val key = Memory(20).apply {
                        write(0, guidBytes(FMTID_APP_USER_MODEL), 0, 16)
                        setInt(16, PID_APP_USER_MODEL_ID)
                    }
                    // PROPVARIANT with VT_LPWSTR: the string pointer sits at offset 8.
                    val value = Memory(32).apply {
                        clear()
                        setShort(0, VT_LPWSTR.toShort())
                        setPointer(8, Memory((APP_USER_MODEL_ID.length + 1) * 2L).apply {
                            setWideString(0, APP_USER_MODEL_ID)
                        })
                    }
                    val setHr = call(store, PS_SET_VALUE, key, value)
                    val commitHr = if (setHr == 0L) call(store, PS_COMMIT) else -1L
                    if (setHr != 0L || commitHr != 0L) {
                        note("SetValue hr=$setHr commit hr=$commitHr")
                    }
                } finally {
                    release(store)
                }
            }

            val persist = queryInterface(unk, IID_PERSIST_FILE)
            if (persist == null) {
                note("QueryInterface(IPersistFile) failed")
            } else {
                try {
                    val saveHr = call(persist, PF_SAVE, WString(link.absolutePath), 1)
                    if (saveHr != 0L) note("Save hr=$saveHr")
                } finally {
                    release(persist)
                }
            }
        } finally {
            release(unk)
        }
    }

    private fun note(message: String) {
        runCatching {
            File(AppPaths.root, "identity.log").appendText(message + "\n")
        }
    }

    private fun guid(value: String): Memory = Memory(16).apply { write(0, guidBytes(value), 0, 16) }

    private fun guidBytes(value: String): ByteArray {
        val parts = value.split("-")
        val bytes = ByteArray(16)
        fun put(index: Int, hex: String, littleEndian: Boolean) {
            val raw = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            val ordered = if (littleEndian) raw.reversed().toByteArray() else raw
            ordered.copyInto(bytes, index)
        }
        put(0, parts[0], true)
        put(4, parts[1], true)
        put(6, parts[2], true)
        put(8, parts[3] + parts[4], false)
        return bytes
    }

    private fun slot(ptr: Pointer, index: Int): Pointer =
        ptr.getPointer(0).getPointer(index.toLong() * Native.POINTER_SIZE)

    private fun call(ptr: Pointer, index: Int, vararg args: Any?): Long =
        Function.getFunction(slot(ptr, index), Function.ALT_CONVENTION)
            .invokeLong(arrayOf<Any?>(ptr, *args))

    private fun queryInterface(ptr: Pointer, iid: String): Pointer? {
        val iidMemory = guid(iid)
        val out = Memory(Native.POINTER_SIZE.toLong())
        val hr = call(ptr, 0, iidMemory, out)
        return if (hr == 0L) out.getPointer(0) else null
    }

    private fun release(ptr: Pointer) {
        runCatching { call(ptr, 2) }
    }
}