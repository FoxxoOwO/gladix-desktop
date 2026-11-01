Druhé vydání — kontextové menu skladeb, řazení, nová obrazovka Přehrává se a ukazatel skutečné kvality. / Second release — track context menu, sorting, a new Now playing screen and a real quality indicator.

## Ke stažení / Download

`Gladix-1.1.0-windows-x64.zip` — samostatná složka s vlastním JVM runtime, běží bez instalace JDK. Rozbalte a spusťte `Gladix\Gladix.exe`. / Self-contained folder with its own JVM runtime, no JDK needed. Unzip and run `Gladix\Gladix.exe`.

```
SHA-256  55405eecfec520ee2605e297999bcba509f421f8f25a8d3171b5241947877e16
```

## Česky

**Nové v tomto vydání**

- **Kontextové menu skladby** — tři tečky u každé skladby (album, playlist, stažené,
  historie, fronta) i v přehrávacím pruhu: přehrát, přehrát jako další, přidat do
  fronty, uložit do knihovny nebo odebrat, líbí / už se mi nelíbí, uložit do playlistu
  (včetně vytvoření nového), stáhnout nebo odstranit stažené, přejít na interpreta,
  přejít na album, rádio, sdílet.
- **Řazení a filtr** — nový sheet s přepínačem *Opačně*, volbou *Název / Délka /
  Interpret / Album* a zaškrtávátkem *Uložit*. K dispozici na albu, playlistu, ve
  stažených skladbách i v historii; uložená volba se pamatuje zvlášť pro každý seznam.
- **Nová obrazovka Přehrává se** — velký obal, seek s časy, kompletní ovládání a taby
  *Následuje*, *Text písně* a *Informace* (interpret, album, rok, délka, číslo stopy,
  vydavatel, kvalita, velikost souboru, počet přehrání).
- **Ukazatel skutečné kvality** — pilulka v přehrávači i v přehrávacím pruhu ukazuje, co
  dekodér opravdu dostal, například `FLAC · 44.1 kHz · 2ch`. U skladeb, které na Deezeru
  ve FLAC nevyšly, tedy správně ukáže `MP3`, i když je v nastavení zvoleno FLAC.
- **Kontrola `:desktop:uiSmoke`** — projde všechny obrazovky, sheet i dialogy v reálném
  okně; když některý composable spadne, build skončí chybou.
- `:desktop:selfCheck` rozšířen o řazení seznamů, popisek kvality a kontrolu, že dekodér
  hlásí skutečně otevřený kontejner.

**Ověřeno:** `:desktop:selfCheck` (všechny kontroly procházejí), `:desktop:uiSmoke`
(kód 0), spuštění z rozbaleného ZIPu s reálným účtem — domovský feed se načte bez chyb.

**Známe omezení:** přepínání *Vše / Playlisty / Alba / Skladby / Interpreti* v knihovně
a hromadný výběr skladeb (checkboxy) v tomto vydání nejsou; menu i řazení pracují
s jednotlivými skladbami.

Instalační MSI/EXE se nedodává — vyžaduje WiX Toolset a podpis kódu; použijte portable
ZIP.

## English

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
