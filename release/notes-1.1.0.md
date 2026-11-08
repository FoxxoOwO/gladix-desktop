Gladix Desktop 1.1.0 — track context menu, sorting, a new Now playing screen and a real quality indicator.

## Download

`Gladix-1.1.0-windows-x64.zip` — self-contained folder with its own JVM runtime, no JDK
needed. Unzip and run `Gladix\Gladix.exe`.

**New in this release**

- **Track context menu** — three dots on every track row (album, playlist, downloads,
  history, queue) and in the player bar: play, play next, add to queue, save to or
  remove from the library, like / unlike, save to a playlist (including creating a new
  one), download or delete the download, jump to the artist, jump to the album, radio,
  share.
- **Sort and filter** — a new sheet with a *Reversed* switch, *Title / Duration /
  Artist / Album* choices and a *Save* checkbox. Available on albums, playlists,
  downloads and history; a saved choice is remembered per list.
- **New Now playing screen** — large artwork, seek with elapsed and total time, the full
  transport row and the *Up next*, *Lyrics* and *Info* tabs (artist, album, year,
  duration, track number, label, quality, file size, play count).
- **Real quality indicator** — a pill in the player and in the player bar shows what the
  decoder actually received, for example `FLAC · 44.1 kHz · 2ch`. For tracks that were
  never released as FLAC on Deezer it correctly says `MP3`, even when FLAC is selected
  in the settings.
- **New `:desktop:uiSmoke` check** — walks every screen, the sheet and the dialogs in a
  real window; if a composable throws, the build fails.
- `:desktop:selfCheck` gained list sorting, the quality label and a check that the
  decoder reports the container it actually opened.

**Verified:** `:desktop:selfCheck` (all checks pass), `:desktop:uiSmoke` (exit code 0),
starting the app from the unzipped archive with a real account — the home feed loads
without errors.

**Known limitation:** the *All / Playlists / Albums / Tracks / Artists* switcher in the
library and multi-select checkboxes are not part of this release; the menu and sorting
work on individual tracks.

The MSI/EXE installers are not shipped — they need WiX Toolset and code signing; use the
portable ZIP.
