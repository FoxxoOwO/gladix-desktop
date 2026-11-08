Gladix Desktop 1.1.1 — bug-fix release: the library now shows content in every tab.

## Download

`Gladix-1.1.1-windows-x64.zip` — self-contained folder with its own JVM runtime, no JDK
needed. Unzip and run `Gladix\Gladix.exe`.

**What was wrong**

After switching to *Playlists*, *Albums* or *Artists* the library showed an empty page.
Deezer sends every tab as a single shelf of album, playlist or artist items, but the
renderer could only draw a track out of such an item and silently dropped everything
else. That is why there was no error message and no spinner either — just blank space.

**Fixed in this release**

- **Albums, playlists and artists are listed in the library** — each row has artwork
  (round for artists), a title and a subtitle, and opens on click.
- **The Tracks tab lists the real collection** — it used to squeeze the favourites into
  one horizontal strip; it is now a full list with positions, artwork and durations.
- **Each tab loads on its own** — data or an error from the previous tab no longer
  lingers, and switching tabs only loads what is needed.
- **Translated tab titles** — *All / Playlists / Albums / Tracks / Artists* (Czech
  *Vše / Playlisty / Alba / Skladby / Interpreti*).
- **An empty tab says so** — instead of a blank area it shows "Nothing in this section
  yet."
- **New `:desktop:uiSmoke` check** — renders every shelf shape (album, playlist, artist,
  track list, whole collection, category), so a regression of this bug fails the build.

**Verified:** `:desktop:selfCheck` (all checks pass), `:desktop:uiSmoke` (exit code 0),
starting the app from the unzipped archive with a real account — the home feed loads
without errors and every library tab filled up (hundreds of albums and artists, thousands
of tracks).

**Known limitation:** multi-select checkboxes in the library are still not there; the
menu and sorting work on individual tracks.

The MSI/EXE installers are not shipped — they need WiX Toolset and code signing; use the
portable ZIP.
