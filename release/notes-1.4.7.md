Gladix Desktop 1.4.7 — update check picks the right archive.

## Download

`Gladix-1.4.7-windows-x64.zip` — self-contained folder with its own JVM runtime, no JDK
needed. Unzip and run `Gladix\Gladix.exe`.

`Gladix-1.4.7-linux-x64.zip` — same shape for 64-bit Linux with a GTK/OpenGL desktop
(GNOME, KDE, and anything else with a current libgtk-3). Unzip and run `Gladix/bin/Gladix`.

The macOS builds are unsigned and not notarized, so Gatekeeper refuses the first launch.
Right-click the app → Open, or run `xattr -dr com.apple.quarantine Gladix.app` once.

`Gladix-1.4.7-macos-arm64.zip` — Apple Silicon (M1 and later).
`Gladix-1.4.7-macos-x64.zip` — Intel Macs.

```
SHA-256
```

## Fixed

**The update check no longer downloads the Windows build on Linux and macOS.** It used
to look for a `-windows-x64.zip` asset and, failing that, take whatever came first — so
on a freshly released Mac the app pulled 106 MB meant for Windows. The archive is now
chosen from the running platform: `windows`, `linux`, `macos-arm64` or `macos-x64`, and
an unknown platform picks nothing rather than the wrong thing.

The "install update" button is still Windows-only; everywhere else the check offers the
download, as before.

## Verified

- `:desktop:selfCheck` asserts the asset choice for all four platforms and that an
  unknown platform resolves to nothing.
- `:desktop:updateProbe` still passes the Windows swap script (user data kept on success,
  rolled back on failure, image without the launcher rejected).
