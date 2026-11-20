Gladix Desktop 1.4.9 — the space bar types again.

## Download

`Gladix-1.4.9-windows-x64.zip` — self-contained folder with its own JVM runtime, no JDK
needed. Unzip and run `Gladix\Gladix.exe`.

`Gladix-1.4.9-linux-x64.zip` — same shape for 64-bit Linux with a GTK/OpenGL desktop
(GNOME, KDE, and anything else with a current libgtk-3). Unzip and run `Gladix/bin/Gladix`.

The macOS builds are unsigned and not notarized, so Gatekeeper refuses the first launch.
Right-click the app → Open, or run `xattr -dr com.apple.quarantine Gladix.app` once.

`Gladix-1.4.9-macos-arm64.zip` — Apple Silicon (M1 and later).
`Gladix-1.4.9-macos-x64.zip` — Intel Macs.

```
SHA-256
```

## Fixed

**Typing a space no longer pauses the music.** Every key the focused control did not
consume reached the window handler, and there Space means play/pause — so a search query
with a space in it stopped playback mid-word. Text fields now say when they hold the
keyboard and the window leaves the shortcuts alone until they let go. Search, the login
form, the country, language, proxy and Discord fields, and the playlist name all count;
moving between two fields does not reopen the gap.

## Verified

- `:desktop:selfCheck` — including a new case for the typing counter across a focus move
  and a stray release, plus every earlier check.
- `:desktop:uiSmoke` — walks all nine screens.
