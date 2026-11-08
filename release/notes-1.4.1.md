Gladix Desktop 1.4.1 — Discord Rich Presence actually connects, and the docs are English-first.

## Download

`Gladix-1.4.1-windows-x64.zip` — self-contained folder with its own JVM runtime, no JDK
needed. Unzip and run `Gladix\Gladix.exe`.

```
SHA-256  e37818f13aa3891690ea35e20e6148764a3ceac2a2e1ed3105ee7c266c67af4e
```

## Fixed

- **Discord Rich Presence now connects.** The IPC client used to write to the Discord pipe
  without ever reading from it. The handshake reply and every activity acknowledgement
  piled up in the pipe buffer until writes blocked, so the presence stayed silent. A
  reader thread now drains those frames, recognises `READY`, and answers Discord's pings.
- **Rich Presence can no longer be configured into nothing.** The application id ships as
  the default and the Settings field only overrides it; clearing the field or leaving `0`
  in it falls back to the built-in id instead of disabling the presence.
- **Rich Presence is on by default** and the Settings section reports whether it is
  connected, still waiting for Discord, or which error Discord returned.

## Changed

- **README is English first**, with the Czech translation below it.
- **Release notes are written in English** and kept in the repository under `release/`.
- The feature tables in the README now list the audio cache, queue preloading,
  mouse-wheel volume, Discord Rich Presence and the automatic update check.
