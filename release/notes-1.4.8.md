Gladix Desktop 1.4.8 — Flow, your own download folder, updates everywhere.

## Download

`Gladix-1.4.8-windows-x64.zip` — self-contained folder with its own JVM runtime, no JDK
needed. Unzip and run `Gladix\Gladix.exe`.

`Gladix-1.4.8-linux-x64.zip` — same shape for 64-bit Linux with a GTK/OpenGL desktop
(GNOME, KDE, and anything else with a current libgtk-3). Unzip and run `Gladix/bin/Gladix`.

The macOS builds are unsigned and not notarized, so Gatekeeper refuses the first launch.
Right-click the app → Open, or run `xattr -dr com.apple.quarantine Gladix.app` once.

`Gladix-1.4.8-macos-arm64.zip` — Apple Silicon (M1 and later).
`Gladix-1.4.8-macos-x64.zip` — Intel Macs.

```
SHA-256
```

## New

**Flow.** The greeting row on Home has a Flow button: Deezer's endless radio for this
account, starting from what you actually listen to. Any radio, Flow included, refills
itself when the queue runs out, so it keeps going instead of stopping after twelve
tracks — and after a restart the queue still remembers it was Flow.

**Choose where downloads go.** Settings has a download folder with a picker. Leave it
empty and everything lands in the app data folder as before; point it at a music folder
and the files appear there.

**Updates install on Linux and macOS.** The self-updater used to give up off Windows and
leave you with a ZIP in a folder. It now recognises every packaging layout (`Gladix.exe`
next to `app/`, `bin/Gladix` next to `lib/`, or the `.app` bundle), swaps the running
image from a small shell script after the app exits, and relaunches it — with a rollback
if the swap fails. On Linux it still needs write access to the folder the app lives in,
so a copy under `/opt` or `/usr/local` asks for a different install location.

**Updates are checked against their hash.** The release notes carry SHA-256 lines; a
downloaded archive that does not match is deleted and reported instead of unpacked.

## Fixed

**A failed like, save or follow says so.** The buttons used to flip and stay flipped when
Deezer refused, with nothing on screen. The change is now undone and the reason is
shown. The same goes for the "retry" button on a failed page load, which did nothing.

**A deleted download no longer breaks playback.** If a downloaded file disappeared, the
player failed instead of falling back to streaming.

## Verified

- `:desktop:selfCheck` — settings, queue, hashing, the update asset pick for all four
  platforms, and the rendered swap scripts.
- `:desktop:updateProbe` — the Windows swap script against throwaway folders: user data
  kept on success, rolled back on failure, an image without the launcher rejected.
- `:desktop:uiSmoke` — walks all nine screens.
- `:desktop:liveCheck` — now runs every morning in CI against a real account: album
  tracks, a real download, lyrics, radio and Flow.
