Vydání 1.2.0 — sjednocené ovládání, oprava posuvníku přehrávání a fronty. / Release 1.2.0 — unified playback controls, seekbar & queue fixes.

## Ke stažení / Download

`Gladix-1.2.0-windows-x64.zip` — samostatná složka s vlastním JVM runtime, běží bez instalace JDK. Rozbalte a spusťte `Gladix\Gladix.exe`. / Self-contained folder with its own JVM runtime, no JDK needed. Unzip and run `Gladix\Gladix.exe`.

```
SHA-256  5bf6ded4eb0e13fbe7af3c4d6e1369edbb61c754f99d3a71c84c05df2024ccd1
```

## Česky

**Co je nového a opraveno**

- **Sjednocené ovládání a progress bar** — odstraněno duplicitní ovládání přehrávání pod obalem alba v detailu skladby. Posuvník času a ovládací tlačítka jsou nyní trvale přístupná ve spodní liště na všech obrazovkách.
- **Oprava posuvníku přehrávání (seekbar)** — opravena chyba přepočtu pozice při přetahování posuvníku, která bránila přesnému přetáčení skladeb.
- **Oprava navigace ve frontě** — opraveno chování kliknutí na skladbu ve frontě a knihovně při výskytu duplicitních skladeb.
- **Optimalizace řazení** — rychlejší řazení skladeb v knihovně i stažených skladbách pomocí optimalizovaných komparátorů.
- **Formátování času** — korektní zobrazení nulové délky jako 0:00.

---

## English

**What's new & Fixed**

- **Unified controls and progress bar** — removed duplicate playback controls under album artwork on the Now Playing screen. Progress bar and controls are now consistently located in the persistent bottom player bar across all screens.
- **Seekbar bug fix** — fixed position calculation when dragging the seekbar that prevented accurate seeking.
- **Queue navigation fix** — fixed track selection in queue and library when duplicate tracks are present.
- **Sorting optimization** — faster sorting in library and downloads using optimized comparators.
- **Duration formatting** — zero duration now properly formats as 0:00 instead of blank.
