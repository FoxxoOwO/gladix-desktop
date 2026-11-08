Gladix Desktop 1.1.2 — bug-fix release: sorting and filtering is now in the library too, in every tab.

## Download

`Gladix-1.1.2-windows-x64.zip` — self-contained folder with its own JVM runtime, no JDK
needed. Unzip and run `Gladix\Gladix.exe`.

**What's new**

- **Sorting in the library** — the sort icon is now above the library lists too and opens
  the same *Sort and filter* sheet (Reversed, Default / Title / Duration / Artist /
  Album, Save).
- **Every tab, not just tracks** — *All*, *Playlists*, *Albums*, *Tracks* and *Artists*
  can all be sorted. Albums, playlists and artists are ordered by title or artist.
- **Each tab remembers its own choice** — sorting the tracks does not reorder the albums;
  the choice is stored separately and survives a restart.
- **The active choice is visible** — chips such as *Title* or *Reversed* appear in the
  header, so a reordered list is never a mystery.
- **The sheet no longer breaks** — five choices did not fit the narrow dialog and "Album"
  was wrapped letter by letter; the chips now wrap as whole words.

**Fixed**

- The library *Tracks* tab sends thousands of individual items rather than one track
  list. Sorting did not handle that shape, so pressing *Apply* did nothing. Such a list
  is now sorted as a whole.
- The library header was hardcoded in Czech ("Knihovna"); it is translated in the English
  UI now.

**Verified:** `:desktop:selfCheck` (17 checks, including sorting loose items),
`:desktop:uiSmoke` (exit code 0) and starting the app from the unzipped archive with a
real account: in the library it was possible to switch to *Tracks*, open the sheet, pick
*Title* and watch the list reorder (`"45"`, `"Heroes"`, `(If You're Wondering…`,
`1*15*96`, `155`, `19-2000`, `1981`…), with the choice saved to the settings.

**Known limitation:** multi-select checkboxes in the library are still not there.

The MSI/EXE installers are not shipped — they need WiX Toolset and code signing; use the
portable ZIP.
