Gladix Desktop 1.2.0 — unified playback controls, seekbar and queue fixes.

## Download

`Gladix-1.2.0-windows-x64.zip` — self-contained folder with its own JVM runtime, no JDK
needed. Unzip and run `Gladix\Gladix.exe`.

```
SHA-256  5bf6ded4eb0e13fbe7af3c4d6e1369edbb61c754f99d3a71c84c05df2024ccd1
```

## What's new and fixed

- **Unified controls and progress bar** — removed duplicate playback controls under album artwork on the Now Playing screen. Progress bar and controls are now consistently located in the persistent bottom player bar across all screens.
- **Seekbar bug fix** — fixed position calculation when dragging the seekbar that prevented accurate seeking.
- **Queue navigation fix** — fixed track selection in queue and library when duplicate tracks are present.
- **Sorting optimization** — faster sorting in library and downloads using optimized comparators.
- **Duration formatting** — zero duration now properly formats as 0:00 instead of blank.
