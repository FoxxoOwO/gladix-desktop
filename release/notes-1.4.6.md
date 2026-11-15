Gladix Desktop 1.4.6 — light theme and keyboard control.

## Download

`Gladix-1.4.6-windows-x64.zip` — self-contained folder with its own JVM runtime, no JDK
needed. Unzip and run `Gladix\Gladix.exe`.

```
SHA-256  <filled by CI>
```

## New

**A light theme.** Settings now has a System / Dark / Light switcher. The choice is
remembered between runs and applied to the window frame as well as to the palette, so a
light window does not keep a dark title bar. An unknown or hand-edited value falls back to
following the OS.

**Keyboard shortcuts.** The window answers without stealing keys from whatever you are
typing in — it listens after the content, so space and `M` still type into the search
field and a bare arrow still belongs to the focused list.

| Key | Action |
|-----|--------|
| `Space` | play / pause |
| `Ctrl` + `←` / `→` | previous / next track |
| `Shift` + `Ctrl` + `←` / `→` | seek back / forward |
| `Ctrl` + `↑` / `↓` | volume up / down |
| `M` | mute |
| `Ctrl` + `F` | jump to search |
| `Esc` | back |

## Verified

- `:desktop:selfCheck` asserts the key map (modifiers, the bare-arrow cases) and the theme
  resolution (stored value, first start, unknown value).
- `:desktop:uiSmoke` renders the light phase and checks that `Ctrl` + `F` switches to the
  search page.
