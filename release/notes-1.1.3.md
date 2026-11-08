Gladix Desktop 1.1.3 — lyrics, queueing from the library, one progress bar and a closable queue.

## Download

`Gladix-1.1.3-windows-x64.zip` — self-contained folder with its own JVM runtime, no JDK
needed. Unzip and run `Gladix\Gladix.exe`.

```
SHA-256  b1cc6865a4323e97a8e231d5738d0ffe64ae3d73565f5fe91cce3c62099c0f1b
```

**What's new**

- **Lyrics work** — Deezer carries words for nowhere near the whole catalogue (a normal
  favourites list answers `lyrics: null` for about half of its tracks), and whenever it
  had none the *Lyrics* pane stayed empty. A missing text now falls back to the free
  **lrclib.net** database: an exact lookup first (title + artist + album + duration),
  then a search that prefers the candidate with the closest duration. Synchronized LRC
  texts are shown as plain lines.
- **Playing a track from the library fills the queue** — previously only the clicked
  song ended up in the queue. The whole tab is queued now, in the order you see it
  (including after sorting), so playback continues with the next song.
- **Closing the queue** — the *Queue* screen has a back arrow in its header that returns
  to the previous screen (usually wherever the queue was opened from).

**Fixed**

- **One progress bar instead of two** — the now playing screen showed the position
  twice: once above the transport controls and once in the player bar at the bottom. The
  player bar hides its slider on that screen.
- **Lyric parsing no longer crashes** on `"lyrics": null` — a missing text is recognised
  as "no lyrics" and the lookup continues, instead of an exception the extension
  swallowed.

**Verified:** `:desktop:selfCheck` (19 checks: queueing from flat library rows, LRC
marker stripping and the new checks) and a run with a real account:
*Library → Tracks* → click a track → *Queue* holds **1627 tracks** in list order;
*Something More* → *Lyrics* shows the full text; the now playing screen has a single
slider; the back arrow in *Queue* returns.

**Known limitation:** lyrics are line based, not synced to the music (no karaoke style
progressive highlighting). Multi-select in the library is still missing.

The MSI/EXE installers are not shipped — they need WiX Toolset and code signing; use the
portable ZIP.
