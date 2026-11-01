Opravné vydání — řazení a filtr je nově i v knihovně, ve všech záložkách. / Bug-fix release — sorting and filtering is now in the library too, in every tab.

## Ke stažení / Download

`Gladix-1.1.2-windows-x64.zip` — samostatná složka s vlastním JVM runtime, běží bez instalace JDK. Rozbalte a spusťte `Gladix\Gladix.exe`. / Self-contained folder with its own JVM runtime, no JDK needed. Unzip and run `Gladix\Gladix.exe`.

```
SHA-256  95a7639b6e5e909170f10c848863dcf5c9dc70f6a4e92a9638eccccbec41cf8e
```

## Česky

**Co je nového**

- **Řazení v knihovně** — ikona řazení je i nad seznamy v knihovně a otevírá stejný
  sheet *Řazení a filtr* (Opačně, Výchozí / Název / Délka / Interpret / Album, Uložit).
- **Všechny záložky, ne jen skladby** — řadit lze *Vše*, *Playlisty*, *Alba*, *Skladby*
  i *Interpreti*. Alba, playlisty a interpreti se řadí podle názvu nebo interpreta.
- **Každá záložka si pamatuje svou volbu** — seřazení skladeb nerozhodí pořadí alb;
  volba se ukládá zvlášť a drží i po restartu.
- **Aktivní volba je vidět** — v hlavičce se zobrazí chipy *Název*, *Opačně* apod., takže
  není záhadou, proč je seznam přerovnaný.
- **Sheet se už nerozbíjí** — pět voleb se vešlo do úzkého dialogu tak, že se „Album“
  lámalo po písmenech; chipy se teď zalamují po celých slovech.

**Opraveno**

- Záložka *Skladby* v knihovně neposílá jeden seznam skladeb, ale tisíce jednotlivých
  položek. Řazení s tímhle tvarem dřív nepočítalo, takže by se po kliknutí na *Použít*
  nestalo nic. Teď se takový seznam seřadí jako celek.
- Hlavička knihovny byla natvrdo česky („Knihovna“); v anglickém rozhraní se překládá.

**Ověřeno:** `:desktop:selfCheck` (17 kontrol, včetně řazení volných položek),
`:desktop:uiSmoke` (kód 0) a spuštění z rozbaleného ZIPu s reálným účtem: v knihovně
šlo přepnout na *Skladby*, otevřít sheet, zvolit *Název* a seznam se přerovnal
(`"45"`, `"Heroes"`, `(If You're Wondering…`, `1*15*96`, `155`, `19-2000`, `1981`…),
volba se uložila do nastavení.

**Známe omezení:** hromadný výběr skladeb (checkboxy) v knihovně stále není.

Instalační MSI/EXE se nedodává — vyžaduje WiX Toolset a podpis kódu; použijte portable
ZIP.

## English

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
