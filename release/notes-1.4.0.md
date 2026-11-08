Gladix Desktop 1.4.0 — Discord Rich Presence, automatic updates and mouse-wheel volume.

## Download

`Gladix-1.4.0-windows-x64.zip` — self-contained folder with its own JVM runtime, no JDK
needed. Unzip and run `Gladix\Gladix.exe`.

```
SHA-256  d19bb289720cf7c573d9546adff8763921b78d758bc92bb00eee8aec07be02f8
```

## What's new

**Volume with the mouse wheel**
- The wheel over the volume control (icon or slider) changes the volume in 2 % steps.
- Smooth trackpad scrolling is proportional; scrolling clears mute automatically.
- A hint appears when the pointer rests on the control.

**Discord Rich Presence** (new Settings section)
- The track you are playing appears on your Discord profile: title, artist, album and a
  running timer.
- Talks to Discord directly over its IPC socket — no extra dependency — and reconnects on
  its own with exponential backoff.
- Respects Discord's rate limit of 5 activity updates per 20 s. A seek is pushed, ordinary
  progress ticks are not, because Discord derives elapsed time from the timestamps.
- Ships with the Gladix application id, so no configuration is needed. The Settings field
  only overrides it, and clearing it falls back to the built-in value.
- The settings screen shows whether the bridge is connected or still waiting for Discord,
  plus any error Discord returned.

**Automatic update check** (new Settings section)
- Checks GitHub Releases once every 6 hours; can be turned off, and *Check now* runs it on
  demand.
- Shows the available version, downloads the ZIP with a progress bar, and opens the folder
  it landed in.
- Version comparison handles `1.10.0 > 1.9.0` correctly, unlike a string compare.

## Fixed

- **Test tasks** (`selfCheck`, `uiSmoke`, `mediaProbe`) used to fail on machines where an
  older JDK came first on `PATH`. They now always run on JDK 21.
- The cache test writes to a throwaway folder instead of deleting the user's real cache.
- The version lives in one place (`AppVersion`) and the About section reads it, so the
  window, the Discord presence and the update check can no longer disagree.
