Gladix Desktop 1.4.5 — the self-update no longer touches your data.

## Download

`Gladix-1.4.5-windows-x64.zip` — self-contained folder with its own JVM runtime, no JDK
needed. Unzip and run `Gladix\Gladix.exe`.

```
SHA-256  <filled by CI>
```

## Fixed

**An update could delete listening data.** A portable Gladix keeps everything in one
folder — `Gladix.exe` next to `history.json`, `settings.properties`, `downloads/` and the
audio cache. The swap moved that whole folder aside and removed the backup afterwards, so
an update would have taken the history and the downloaded tracks with it.

The updater now replaces only the entries the new image actually brings (`Gladix.exe`,
`app`, `runtime`). Everything else stays untouched. If anything fails halfway, every entry
that was already replaced is put back; if a locked entry cannot be cleared, the old copy is
kept next to it as `<name>.recovered` and the log says so, instead of deleting the only
remaining copy.

## Verified

- `:desktop:updateProbe` runs the real swap script against throwaway folders: user data
  survives the swap, a failed swap restores the old version, and an image without
  `Gladix.exe` is rejected before anything moves.
- `:desktop:liveCheck` uses the signed-in account: the album loads all 14 tracks, its first
  track downloads as a 40.5 MB FLAC, lyrics come back from Deezer (2130 chars) and from the
  lrclib.net fallback, and radio built from the album returns a 76-track queue.
