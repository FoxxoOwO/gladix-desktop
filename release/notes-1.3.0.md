Gladix Desktop 1.3.0 — audio disk cache and smooth queue preloading.

## Download

`Gladix-1.3.0-windows-x64.zip` — self-contained folder with its own JVM runtime, no JDK
needed. Unzip and run `Gladix\Gladix.exe`.

```
SHA-256  98ae4c197f8e2d0cba94833d5e76683a6661ae5c78bbf106a13101fc2eb237a3
```

## What's new

**Audio disk cache**
- Tracks that play are stored in a local cache under `%APPDATA%\Gladix\audio_cache`.
- Configurable size in *Settings → Data and storage*: off, 512 MB, 1 GB, 2 GB or 4 GB.
- LRU eviction: when the cache fills up, the least recently used tracks are removed.
- Shows the space currently used and offers a button to clear the cache.

**Track preloading**
- The next tracks in the queue — following the shuffle order when shuffle is on — are
  downloaded into the cache ahead of time.
- Switching tracks in the queue is therefore instant instead of waiting on Deezer.

**Stutter-free playback**
- The sound card buffer (`SourceDataLine`) grew to 256 KB, roughly 1.5 s of audio, so CPU
  or I/O spikes no longer cause dropouts.
- Audio streams are read through a 64 KB buffer.

**Carried over from 1.1.3**
- Lyrics fall back to lrclib.net when Deezer has none.
- Playing a track from the library queues the whole tab in the order shown.
- One progress bar instead of two, and a closable queue screen.
