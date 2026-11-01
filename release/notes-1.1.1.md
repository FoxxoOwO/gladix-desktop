Opravné vydání — knihovna teď ve všech záložkách opravdu zobrazuje obsah. / Bug-fix release — the library now shows content in every tab.

## Ke stažení / Download

`Gladix-1.1.1-windows-x64.zip` — samostatná složka s vlastním JVM runtime, běží bez instalace JDK. Rozbalte a spusťte `Gladix\Gladix.exe`. / Self-contained folder with its own JVM runtime, no JDK needed. Unzip and run `Gladix\Gladix.exe`.

```
SHA-256  629dc0958d80254d2092310a1b7c74418d2c9c7a5e38802b5096a709de80997c
```

## Česky

**Co bylo špatně**

V knihovně se po přepnutí na *Playlisty*, *Alba* nebo *Interpreti* ukázala prázdná
stránka. Deezer posílá každou záložku jako jednu polici s položkami typu album,
playlist nebo interpret, ale vykreslování umělo z takové položky nakreslit jen skladbu —
všechno ostatní tiše zahodilo. Proto se neobjevila ani chybová hláška, ani spinner, jen
prázdno.

**Opraveno v tomto vydání**

- **Alba, playlisty a interpreti se v knihovně vypisují** — každá položka má obal
  (u interpreta kulatý), název a podtitulek a kliknutím se otevře.
- **Záložka Skladby vypisuje skutečný seznam** — dřív se oblíbené skladby mačkaly do
  jednoho vodorovného pruhu, teď je to plnohodnotný seznam s čísly, obaly a délkami.
- **Každá záložka má vlastní načítání** — obsah ani chyba z předchozí záložky už
  neprosvítají, při přepnutí se načte jen to, co je potřeba.
- **Přeložené názvy záložek** — *Vše / Playlisty / Alba / Skladby / Interpreti*
  (anglicky *All / Playlists / Albums / Tracks / Artists*).
- **Prázdná záložka to řekne** — místo bílé plochy se zobrazí „V této sekci nic není.“
- **Nová kontrola `:desktop:uiSmoke`** — vykreslí každý tvar police (album, playlist,
  interpret, seznam skladeb, celá kolekce, kategorie), takže návrat téhle chyby zastaví
  build.

**Ověřeno:** `:desktop:selfCheck` (všechny kontroly procházejí), `:desktop:uiSmoke`
(kód 0), spuštění z rozbaleného ZIPu s reálným účtem — domovský feed se načte bez chyb,
v knihovně se naplnily všechny záložky (řádově stovky alb a interpretů, tisíce skladeb).

**Známe omezení:** hromadný výběr skladeb (checkboxy) v knihovně stále není; menu
a řazení pracují s jednotlivými skladbami.

Instalační MSI/EXE se nedodává — vyžaduje WiX Toolset a podpis kódu; použijte portable
ZIP.

## English

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
