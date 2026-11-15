Gladix Desktop 1.4.4 — updates install themselves, no more unzipping by hand.

## Download

`Gladix-1.4.4-windows-x64.zip` — self-contained folder with its own JVM runtime, no JDK
needed. Unzip and run `Gladix\Gladix.exe` (only the first install needs this step).

```
SHA-256  3f35c4bf3a04cd702bc32329e3b87116f85bd96ff94ce26dfa64047fef578ebb
```

## What's new

**The update installs itself**
- When a newer release exists, Gladix downloads the ZIP and unpacks it right away. The
  settings screen shows *Preparing update…* and then a single *Restart and update* button.
- The restart swaps the whole application folder through a small PowerShell script that
  waits for Gladix to exit, moves the old image aside, copies the new one in and starts it
  again. If the new `Gladix.exe` is missing, the backup is put back automatically.
- Closing the window with a prepared update installs it too, so the next start already
  runs the new version without any dialog.
- No elevation is needed when Gladix sits in a writable folder; in `Program Files` the
  script asks for administrator rights through the normal UAC prompt.
- Leftovers (the old backup folder, the downloaded ZIP) are cleaned up on the next start.

## Fixed

- **Extracting the release failed on Windows.** `java.util.zip` reports entry names with
  backslashes and without the trailing separator, so the `legal` folder was created as a
  zero-byte file and the unpack died with
  `FileNotFoundException: …\runtime\legal\java.base\ADDITIONAL_LICENSE_INFO`. Entry names
  are normalised before use.
- The native window caption is repainted in the app colours, so the dark UI no longer sits
  under a light system title bar.

## Notes

- Running Gladix from Gradle or an IDE keeps the old behaviour: the ZIP is downloaded and
  the folder can be opened by hand, because there is no installed image to replace.
- Updates use the release ZIP, not the MSI.
