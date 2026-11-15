package dev.gladix.desktop.core

import java.io.File
import java.io.IOException
import java.util.zip.ZipFile

/**
 * Finds the folder of the jpackage app image that is currently running.
 *
 * A packaged Gladix consists of `Gladix.exe` plus an `app` folder next to it.
 * When the code runs from Gradle or an IDE there is no such image, so every
 * lookup returns null and the update simply stays a downloaded ZIP.
 */
object InstallLocation {

    private const val PROP = "jpackage.app-path"

    val launcher: File? = detect()
    val root: File? = launcher?.parentFile?.takeIf { it.isDirectory }

    private fun detect(): File? {
        // jpackage sets this for every app image launcher; it is the most
        // reliable source because it points at the real exe, not the JVM.
        val explicit = System.getProperty(PROP)
        if (!explicit.isNullOrBlank()) {
            return File(explicit).takeIf(::looksPackaged)
        }
        // Fallback for launchers that do not set the property.
        val command = runCatching {
            ProcessHandle.current().info().command().orElse(null)
        }.getOrNull()
        if (!command.isNullOrBlank()) {
            File(command).takeIf(::looksPackaged)?.let { return it }
        }
        // Last resort: the class sits in <image>/app/some.jar.
        val image = runCatching {
            val location = InstallLocation::class.java.protectionDomain.codeSource.location
            val self = File(location.toURI())
            if (self.isFile && self.parentFile?.name == "app") self.parentFile.parentFile else null
        }.getOrNull()
        return image?.takeIf { looksPackaged(File(it, "Gladix.exe")) }
    }

    private fun looksPackaged(exe: File): Boolean =
        exe.isFile && exe.name.endsWith(".exe", ignoreCase = true) &&
            exe.parentFile?.let { File(it, "app").isDirectory } == true
}

/**
 * Turns a downloaded `Gladix-*-windows-x64.zip` into an applied update.
 *
 * The running `Gladix.exe` and the JARs it has open are locked by Windows, so
 * the swap cannot happen in-process. The ZIP is therefore unpacked next to the
 * app data, a small PowerShell script is written to `%TEMP%` and started. It
 * waits for this process to disappear, moves the old image aside, copies the
 * new one in, verifies `Gladix.exe`, removes the backup and — optionally —
 * starts the new build. If anything fails it rolls the old image back.
 */
class SelfUpdater(private val installRoot: File? = InstallLocation.root) {

    /** Folder holding downloaded ZIPs and the unpacked staging image. */
    val stagingRoot: File = File(AppPaths.root, "updates")

    val isSupported: Boolean
        get() = installRoot?.let { File(it, "Gladix.exe").isFile && File(it, "app").isDirectory } == true

    /** Extracts [zip] and returns the app image folder found inside it. */
    fun stage(zip: File): File {
        val destination = File(stagingRoot, "staging")
        destination.deleteRecursively()
        if (!destination.mkdirs()) throw IOException("Cannot create ${destination.path}")

        ZipFile(zip).use { archive ->
            val base = destination.canonicalFile
            for (entry in archive.entries()) {
                // Windows ZipFile hands out names with backslashes and without
                // the trailing separator, so a directory entry has to be
                // detected from the normalised name, not from isDirectory.
                val name = entry.name.replace('\\', '/')
                if (name.isEmpty()) continue
                val target = File(destination, name).canonicalFile
                if (target != base && !target.path.startsWith(base.path + File.separator)) {
                    throw IOException("Unsafe path in archive: ${entry.name}")
                }
                if (name.endsWith("/") || entry.isDirectory) {
                    target.mkdirs()
                } else {
                    target.parentFile?.mkdirs()
                    archive.getInputStream(entry).use { input ->
                        target.outputStream().use { output -> input.copyTo(output) }
                    }
                }
            }
        }

        val image = findImage(destination)
        if (!File(image, "Gladix.exe").isFile) {
            image.deleteRecursively()
            throw IOException("Archive does not contain Gladix.exe")
        }
        return image
    }

    /** True when the install folder can be replaced without elevation. */
    fun canWriteWithoutElevation(): Boolean = installRoot?.let { canWrite(it) } == true

    /**
     * Writes and starts the swap script. [restart] relaunches Gladix once the
     * files are in place; false installs silently and lets the user start the
     * app himself (used when closing the window).
     */
    fun apply(stagedImage: File, restart: Boolean): Boolean {
        val root = installRoot ?: return false
        if (!File(stagedImage, "Gladix.exe").isFile) return false
        val script = writeScript(stagedImage, root, restart)
        val elevated = !canWrite(root)
        return runCatching { startPowerShell(script, elevated) }.getOrDefault(false)
    }

    /**
     * Removes leftovers of a finished update: ZIPs that are already applied and
     * backup folders from previous swaps. The staging copy is kept on purpose —
     * it is the only retry when an elevated swap was cancelled.
     */
    fun cleanUp() {
        runCatching {
            stagingRoot.listFiles()?.forEach { if (it.isFile) it.delete() }
            val root = installRoot ?: return@runCatching
            root.parentFile?.listFiles()
                ?.filter { it.isDirectory && it.name.startsWith(root.name + ".old-") }
                ?.forEach { it.deleteRecursively() }
        }
    }

    private fun findImage(root: File): File {
        if (File(root, "app").isDirectory) return root
        val children = root.listFiles()?.filter { it.isDirectory }.orEmpty()
        children.firstOrNull { File(it, "Gladix.exe").isFile }?.let { return it }
        children.firstOrNull { File(it, "app").isDirectory }?.let { return it }
        return root
    }

    private fun canWrite(dir: File): Boolean = runCatching {
        val probe = File.createTempFile(".gladix-write", ".tmp", dir)
        probe.delete()
        true
    }.getOrDefault(false)

    private fun writeScript(stagedImage: File, root: File, restart: Boolean): File {
        val script = File.createTempFile("gladix-update", ".ps1").apply { deleteOnExit() }
        script.writeText(
            renderScript(
                src = stagedImage.absolutePath,
                dst = root.absolutePath,
                restart = restart,
                pid = ProcessHandle.current().pid()
            )
        )
        return script
    }

    /**
     * The swap script. Kept free of state so tests can render it for throwaway
     * folders and prove the swap really leaves user data alone.
     */
    internal fun renderScript(src: String, dst: String, restart: Boolean, pid: Long): String {
        fun ps(value: String) = value.replace("'", "''")
        return """
            ${'$'}ErrorActionPreference = 'Stop'
            ${'$'}src = '${ps(src)}'
            ${'$'}dst = '${ps(dst)}'
            ${'$'}restart = ${if (restart) "${'$'}true" else "${'$'}false"}
            ${'$'}waitFor = $pid
            ${'$'}log = Join-Path ${'$'}env:TEMP 'gladix-update.log'
            ${'$'}exe = Join-Path ${'$'}dst 'Gladix.exe'
            ${'$'}backup = ${'$'}dst + '.old-' + (Get-Date -Format 'yyyyMMddHHmmss')
            ${'$'}items = @()
            ${'$'}moved = @()
            ${'$'}copied = @()

            function Log([string]${'$'}message) {
                Add-Content -LiteralPath ${'$'}log -Value ((Get-Date -Format 's') + ' ' + ${'$'}message)
            }

            try {
                Log "waiting for process ${'$'}waitFor"
                for (${'$'}i = 0; ${'$'}i -lt 240; ${'$'}i++) {
                    if (-not (Get-Process -Id ${'$'}waitFor -ErrorAction SilentlyContinue)) { break }
                    Start-Sleep -Milliseconds 500
                }
                # Give Windows a moment to release the exe and JAR handles.
                Start-Sleep -Seconds 1

                # Only the entries the image owns are replaced. A portable Gladix
                # can sit in its own app-data folder, and moving the whole folder
                # would take history, downloads and settings with it.
                ${'$'}items = @(Get-ChildItem -LiteralPath ${'$'}src -Force | Select-Object -ExpandProperty Name)
                if (${'$'}items.Count -eq 0) { throw 'staged image is empty' }
                if (-not (Test-Path -LiteralPath (Join-Path ${'$'}src 'Gladix.exe'))) {
                    throw 'staged image is missing Gladix.exe'
                }
                New-Item -ItemType Directory -Path ${'$'}backup -Force | Out-Null
                foreach (${'$'}item in ${'$'}items) {
                    ${'$'}old = Join-Path ${'$'}dst ${'$'}item
                    if (Test-Path -LiteralPath ${'$'}old) {
                        Log ('moving aside: ' + ${'$'}item)
                        # Recorded before the move: a move that fails halfway
                        # leaves the old content in the backup folder.
                        ${'$'}moved += ${'$'}item
                        Move-Item -LiteralPath ${'$'}old -Destination (Join-Path ${'$'}backup ${'$'}item) -Force
                    }
                    Log ('copying in: ' + ${'$'}item)
                    Copy-Item -LiteralPath (Join-Path ${'$'}src ${'$'}item) -Destination (Join-Path ${'$'}dst ${'$'}item) -Recurse -Force
                    ${'$'}copied += ${'$'}item
                }
                if (-not (Test-Path -LiteralPath ${'$'}exe)) { throw 'new image is missing Gladix.exe' }
                Remove-Item -LiteralPath ${'$'}backup -Recurse -Force -ErrorAction SilentlyContinue
                Remove-Item -LiteralPath ${'$'}src -Recurse -Force -ErrorAction SilentlyContinue
                Log 'update installed'
                if (${'$'}restart) { Start-Process -FilePath ${'$'}exe }
            } catch {
                # A half-swapped image is worse than the old one, so every entry
                # that was already touched goes back, not just a missing exe.
                Log ('failed: ' + ${'$'}_.Exception.Message)
                Log ('moved: ' + (${'$'}moved -join ',') + ' copied: ' + (${'$'}copied -join ','))
                # Anything already copied is new, so it goes; anything already
                # moved aside is old, so it comes back. An entry whose move
                # failed is still the original and is left alone.
                foreach (${'$'}item in ${'$'}copied) {
                    # A brand-new entry with no old counterpart is simply removed.
                    if (-not (Test-Path -LiteralPath (Join-Path ${'$'}backup ${'$'}item))) {
                        ${'$'}target = Join-Path ${'$'}dst ${'$'}item
                        if (Test-Path -LiteralPath ${'$'}target) {
                            Remove-Item -LiteralPath ${'$'}target -Recurse -Force -ErrorAction SilentlyContinue
                        }
                    }
                }
                foreach (${'$'}item in ${'$'}moved) {
                    ${'$'}old = Join-Path ${'$'}backup ${'$'}item
                    if (-not (Test-Path -LiteralPath ${'$'}old)) { continue }
                    ${'$'}target = Join-Path ${'$'}dst ${'$'}item
                    if (Test-Path -LiteralPath ${'$'}target) {
                        Remove-Item -LiteralPath ${'$'}target -Recurse -Force -ErrorAction SilentlyContinue
                    }
                    if (Test-Path -LiteralPath ${'$'}target) {
                        # Locked in place: put the old copy next to it instead of
                        # destroying the only remaining copy.
                        Move-Item -LiteralPath ${'$'}old -Destination (${'$'}target + '.recovered') -Force
                        Log ('kept old copy as ' + ${'$'}target + '.recovered')
                    } else {
                        Move-Item -LiteralPath ${'$'}old -Destination ${'$'}dst -Force
                    }
                }
                if (-not (Test-Path -LiteralPath ${'$'}backup)) {
                    Log 'nothing was swapped'
                } elseif (@(Get-ChildItem -LiteralPath ${'$'}backup -Force).Count -eq 0) {
                    Remove-Item -LiteralPath ${'$'}backup -Recurse -Force -ErrorAction SilentlyContinue
                } else {
                    Log ('backup kept at ' + ${'$'}backup)
                }
                Log 'rolled back'
            } finally {
                Remove-Item -LiteralPath ${'$'}PSCommandPath -Force -ErrorAction SilentlyContinue
            }
        """.trimIndent()
    }

    private fun startPowerShell(script: File, elevated: Boolean): Boolean {
        val normal = listOf(
            "powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass",
            "-WindowStyle", "Hidden", "-File", script.absolutePath
        )
        val command = if (!elevated) {
            normal
        } else {
            // `-Verb RunAs` shows the UAC prompt and runs the script as admin,
            // which is what writing into Program Files needs.
            val quoted = script.absolutePath.replace("'", "''")
            val ps = "Start-Process -FilePath 'powershell.exe' -ArgumentList " +
                "@('-NoProfile','-ExecutionPolicy','Bypass','-WindowStyle','Hidden','-File','\"$quoted\"') " +
                "-Verb RunAs"
            listOf("powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", ps)
        }
        ProcessBuilder(command)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start()
        return true
    }
}
