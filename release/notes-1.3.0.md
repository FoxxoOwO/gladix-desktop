# Gladix Desktop 1.3.0

Verze 1.3.0 přináší mezipaměť (audio cache) a plynulé asynchronní přednačítání skladeb.

### Novinky a vylepšení

* **Mezipaměť pro audio (Audio Cache)**:
  * Přehrávané skladby se automaticky ukládají do lokální diskové mezipaměti (`%APPDATA%/Gladix/audio_cache`).
  * Nastavitelná velikost mezipaměti v *Nastavení -> Data a úložiště*: Vypnuto, 512 MB, 1024 MB (1 GB), 2048 MB (2 GB), 4096 MB (4 GB).
  * LRU evikce: při zaplnění mezipaměti se nejstarší skladby automaticky odstraňují.
  * Zobrazení využitého místa a tlačítko pro okamžité vymazání mezipaměti.
* **Přednačítání skladeb (Preloading)**:
  * Následující skladby ve frontě (včetně shuffle režimu) se automaticky stahují do cache na pozadí předem.
  * Přepínání skladeb ve frontě je nyní okamžité bez prodlevy.
* **Plynulé přehrávání bez sekání**:
  * Zvětšen buffer zvukové karty (`SourceDataLine`) na 256 KB (~1.5 sekundy audia) pro eliminaci záseků a praskání při vysoké zátěži CPU nebo I/O.
  * 64 KB buffering při čtení audio streamů.
* **Opravy chyb z předchozí verze**:
  * Oprava posuvníku přehrávání (seek bar dragging).
  * Oprava navigace ve frontě při duplicitních skladbách.
