package dev.gladix.desktop.tools

import dev.gladix.desktop.core.SelfUpdater
import java.io.File
import kotlin.system.exitProcess

/**
 * Runs the real update swap script against throwaway folders.
 *
 * The script is the part of the updater that cannot be exercised by selfCheck —
 * it only ever runs after Gladix exits. A portable install can sit in its own
 * app-data folder, so the swap must replace only the app image and must never
 * move or delete history, downloads or settings.
 */
private var failures = 0

private fun check(name: String, condition: Boolean, detail: String = "") {
    if (condition) {
        println("  ok   $name")
    } else {
        failures++
        println("  FAIL $name ${if (detail.isEmpty()) "" else "- $detail"}")
    }
}

private fun write(file: File, text: String) {
    file.parentFile?.mkdirs()
    file.writeText(text)
}

private fun layout(base: File, tag: String): Triple<File, File, File> {
    val dst = File(base, "Gladix").apply { mkdirs() }
    val src = File(base, "updates/staging/Gladix").apply { mkdirs() }
    // Sad path: an install folder that also holds user data.
    write(File(dst, "Gladix.exe"), "old-$tag")
    write(File(dst, "app/old.txt"), "old")
    write(File(dst, "runtime/legal/license.txt"), "old")
    write(File(dst, "history.json"), "[{\"id\":\"keep-me\"}]")
    write(File(dst, "settings.properties"), "desktop_volume=70\n")
    write(File(dst, "downloads/track.mp3"), "mp3")
    // The staged image of the next version.
    write(File(src, "Gladix.exe"), "new-$tag")
    write(File(src, "app/new.txt"), "new")
    write(File(src, "runtime/legal/license.txt"), "new")
    return Triple(dst, src, base)
}

private fun acl(path: String, vararg args: String) {
    ProcessBuilder(listOf("icacls.exe", path) + args).redirectErrorStream(true).start().waitFor()
}

private fun runScript(updater: SelfUpdater, src: File, dst: File): Int {
    val script = File.createTempFile("gladix-probe", ".ps1")
    script.writeText(updater.renderScript(src.absolutePath, dst.absolutePath, restart = false, pid = 999_999))
    val process = ProcessBuilder(
        "powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", script.absolutePath
    ).redirectErrorStream(true).start()
    val output = process.inputStream.bufferedReader().readText()
    val code = process.waitFor()
    if (output.isNotBlank()) println(output.trim())
    return code
}

fun main() {
    val updater = SelfUpdater(installRoot = null)
    val base = File(System.getProperty("java.io.tmpdir"), "gladix-update-probe-${System.nanoTime()}")

    println("== swap keeps user data")
    run {
        val (dst, src, root) = layout(File(base, "ok"), "ok")
        runScript(updater, src, dst)
        check("new exe in place", File(dst, "Gladix.exe").readText() == "new-ok")
        check("new app/ in place", File(dst, "app/new.txt").isFile)
        check("old app/ gone", !File(dst, "app/old.txt").exists())
        check("runtime replaced", File(dst, "runtime/legal/license.txt").readText() == "new")
        check("history survived", File(dst, "history.json").isFile)
        check("settings survived", File(dst, "settings.properties").readText().contains("desktop_volume=70"))
        check("downloads survived", File(dst, "downloads/track.mp3").isFile)
        check("staging removed", !src.exists())
        check("backup removed", root.listFiles().orEmpty().none { it.name.startsWith("Gladix.old-") })
    }

    println("== failed swap rolls back")
    run {
        val (dst, src, root) = layout(File(base, "bad"), "bad")
        // Deny delete on runtime so moving it aside fails after app/ was
        // already swapped: exactly the half-applied state that must not stay.
        val runtime = File(dst, "runtime")
        acl("$runtime", "/deny", "${System.getenv("USERNAME")}:(DE)")
        try {
            runScript(updater, src, dst)
        } finally {
            acl("$runtime", "/remove:d", System.getenv("USERNAME"))
        }
        val log = File(System.getenv("TEMP"), "gladix-update.log").takeIf { it.isFile }?.readText().orEmpty()
        check("log says rolled back", log.contains("rolled back"), log.takeLast(200))
        check("old exe kept", File(dst, "Gladix.exe").readText() == "old-bad")
        check("old app/ restored", File(dst, "app/old.txt").isFile)
        check("new app/ removed", !File(dst, "app/new.txt").exists())
        check(
            "runtime preserved",
            File(dst, "runtime/legal/license.txt").isFile ||
                File(dst, "runtime.recovered/legal/license.txt").isFile
        )
        check("history survived", File(dst, "history.json").isFile)
        check("downloads survived", File(dst, "downloads/track.mp3").isFile)
        check("staging kept for a retry", File(src, "Gladix.exe").isFile)
    }

    println("== image without exe is rejected before any swap")
    run {
        val (dst, src, root) = layout(File(base, "noexe"), "noexe")
        File(src, "Gladix.exe").delete()
        runScript(updater, src, dst)
        check("old exe kept", File(dst, "Gladix.exe").readText() == "old-noexe")
        check("old app/ kept", File(dst, "app/old.txt").isFile)
        check("new app/ not copied", !File(dst, "app/new.txt").exists())
        check("runtime untouched", File(dst, "runtime/legal/license.txt").readText() == "old")
        check("history survived", File(dst, "history.json").isFile)
        check("no backup left", root.listFiles().orEmpty().none { it.name.startsWith("Gladix.old-") })
    }

    base.deleteRecursively()
    if (failures > 0) {
        println("updateProbe FAILED ($failures)")
        exitProcess(1)
    }
    println("updateProbe ok")
}
