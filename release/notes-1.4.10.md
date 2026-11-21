Gladix Desktop 1.4.10 — updates that say what happened.

## Download

`Gladix-1.4.10-windows-x64.zip` — self-contained folder with its own JVM runtime, no JDK
needed. Unzip and run `Gladix\Gladix.exe`.

`Gladix-1.4.10-linux-x64.zip` — same shape for 64-bit Linux with a GTK/OpenGL desktop
(GNOME, KDE, and anything else with a current libgtk-3). Unzip and run `Gladix/bin/Gladix`.

The macOS builds are unsigned and not notarized, so Gatekeeper refuses the first launch.
Right-click the app → Open, or run `xattr -dr com.apple.quarantine Gladix.app` once.

`Gladix-1.4.10-macos-arm64.zip` — Apple Silicon (M1 and later).
`Gladix-1.4.10-macos-x64.zip` — Intel Macs.

```
SHA-256
```

## Fixed

**A check no longer disturbs a running download.** Pressing "check now" or the switch in
the middle of a 100 MB download reset the state to "available" and the automatic path
started a second download into the same file, which then failed the checksum. A check now
declines to start while a download, an unpack or a swap is in flight, and the button is
disabled meanwhile.

**The auto-update switch is read every round.** It used to be read once at startup, so
turning checks off still fetched, and turning them back on waited for a restart.

**Only a real archive is picked.** The asset lookup matched a substring of the name, so a
`.zip.sha256` or a `.blockmap` could win; it now requires the platform token and a `.zip`
suffix.

**A read-only install folder is honest about it.** A system-wide `/opt` or `/Applications`
install can never be renamed by the app without elevation, and only Windows asks for it.
The update stays a manual unzip and the settings screen names the folder to unpack into,
instead of failing with "update script could not be started".

**An update that never landed is reported.** The app records the version it is about to
install before it closes; if the next launch is still the old build — a declined elevation,
a cancelled authorisation — the settings screen says so, and which version it was.

## Verified

- `:desktop:selfCheck` — including the new cases for check blocking, the unfinished-swap
  marker and the read-only fallback logic.
- `:desktop:updateProbe` — the PowerShell swap script in three scenarios.
- `:desktop:uiSmoke` — walks all nine screens.
