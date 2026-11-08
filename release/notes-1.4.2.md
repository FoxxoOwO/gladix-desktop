Gladix Desktop 1.4.2 — the portable build actually starts again.

## Download

`Gladix-1.4.2-windows-x64.zip` — self-contained folder with its own JVM runtime, no JDK
needed. Unzip and run `Gladix\Gladix.exe`.

```
SHA-256  7b6e79d7091de53fad2b10698701d665938285b0ffc4a04ba8ccdd01395e6d46
```

## Fixed

- **`Gladix.exe` no longer dies on startup.** The packaged runtime image was built from
  JDK 17 while the classes were compiled for JVM 21, so the launcher aborted with
  `UnsupportedClassVersionError: class file version 65.0`. The Compose plugin takes the
  runtime from the JVM running the Gradle daemon unless told otherwise, and that daemon
  runs on JDK 17 here.
- **The runtime and the code are now pinned to the same toolchain.** A `jvmToolchain(21)`
  plus an explicit `javaHome` for the packaging step keep them in lockstep, and a missing
  JDK 21 is downloaded automatically instead of silently picking an older one.

The shipped `runtime/release` now reads `JAVA_VERSION="21.0.7"`, and `Gladix.exe` was
started from the unpacked distribution to confirm it reaches the home screen.
