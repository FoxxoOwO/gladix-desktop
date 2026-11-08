Gladix Desktop 1.4.3 — "show all" opens a page with every album.

## Download

`Gladix-1.4.3-windows-x64.zip` — self-contained folder with its own JVM runtime, no JDK
needed. Unzip and run `Gladix\Gladix.exe`.

```
SHA-256  47974b77baa23ae5218449a6133bd04a33ab6b6cdf82390af7df9e384f42a112
```

## What's new

- **A "show all" button on album shelves.** A horizontal strip only carries about a dozen
  cards and the rest of the catalogue stayed hidden behind it. Shelves that keep loading
  more items now end with a *show all* link that opens a full page of albums.
- **The page pages as you scroll.** It asks Deezer for one page at a time and starts the
  next one a screen before the end, so a large discography never loads in one go.
- **Sorting works on that page too**, using the same title/duration/artist/album choices as
  the rest of the app, remembered under its own `all_albums` scope.

The button only appears when the shelf actually has more to load; a fixed strip of twelve
albums has nothing behind it and gets no link.

## Verified

`selfCheck` (21 checks) and `uiSmoke`, which now renders the new page as one of its
screens, both pass.
