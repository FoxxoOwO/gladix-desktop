# Gladix Desktop 1.4.0

Verze 1.4.0 přináší Discord Rich Presence, automatickou kontrolu aktualizací a ovládání hlasitosti kolečkem myši.

### Novinky

* **Hlasitost kolečkem myši**
  * Kolečko myši nad ovládáním hlasitosti (ikona i posuvník) mění hlasitost po 2 %.
  * Funguje i plynulý scroll na trackpadu; po scrollování se automaticky zruší ztlumení.
  * Po najetí myší se zobrazí nápověda.

* **Discord Rich Presence** (nová sekce v nastavení)
  * Aktuálně hraná skladba se zobrazuje na profilu v Discordu — název, interpret, album i ubíhající čas.
  * Komunikuje přímo přes Discord IPC (žádná externí knihovna), s automatickým připojením a obnovou spojení.
  * Respektuje rate limit Discordu (max. 5 aktualizací za 20 s).
  * Vyžaduje **Discord Application ID** z [Discord Developer Portal](https://discord.com/developers/applications) — vloží se do nastavení.

* **Automatická kontrola aktualizací** (nová sekce v nastavení)
  * Jednou za 6 hodin se ověří nové vydání na GitHubu, kontrola jde vypnout.
  * Tlačítko **Zkontrolovat teď**, zobrazení dostupné verze a stažení ZIP s ukazatelem průběhu.
  * Stažený balíček se uloží do `%APPDATA%\Gladix\updates`, tlačítko otevře složku.
  * Porovnávání verzí zvládá i `1.10.0 > 1.9.0`.

### Opraveno

* **Testovací úlohy** (`selfCheck`, `uiSmoke`, `mediaProbe`) už nepadají na systémech, kde je v `PATH` starší JDK — běží vždy na JDK 21.
* Test mezipaměti zapisuje do dočasné složky a nemaže uživatelskou cache.
* Verze aplikace je na jednom místě (`AppVersion`) a zobrazuje se v sekci *O aplikaci*.
