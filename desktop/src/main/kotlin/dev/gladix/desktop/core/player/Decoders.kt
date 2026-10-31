package dev.gladix.desktop.core.player

import dev.gladix.desktop.ui.text.UiStrings
import java.io.BufferedInputStream
import java.io.InputStream
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem

/**
 * Turns a Deezer audio stream (MP3 or FLAC, already decrypted by the extension)
 * into 16 bit little endian PCM.
 *
 * We deliberately do NOT use [AudioSystem.getAudioInputStream] for format
 * detection. Deezer serves MP3 files; the FLAC reader from jflac is consulted
 * first by the SPI and throws `IOException: Resetting to invalid mark` instead
 * of the `UnsupportedAudioFileException` that would make AudioSystem try the
 * next reader, so every MP3 would fail to open. Sniffing the container and
 * calling the matching reader directly avoids that entirely.
 */
object Decoders {

    /** Deeper than any reader probes, so mark/reset always succeeds. */
    private const val MARK_LIMIT = 1 shl 20
    private const val BUFFER_SIZE = 1 shl 20

    enum class Container { FLAC, MP3, WAV, UNKNOWN }

    /** Container of the last successfully opened stream, for the UI/diagnostics. */
    @Volatile
    var lastContainer: Container = Container.UNKNOWN
        private set

    /** A decoded stream together with what it turned out to be. */
    data class Decoded(val stream: AudioInputStream, val container: Container)

    /** Reads the first bytes and guesses the container. */
    fun sniff(head: ByteArray, length: Int): Container {
        if (length >= 4) {
            val magic = String(head, 0, 4, Charsets.ISO_8859_1)
            when (magic) {
                "fLaC" -> return Container.FLAC
                "RIFF" -> return Container.WAV
                "OggS" -> return Container.UNKNOWN
            }
        }
        if (length >= 3 &&
            head[0] == 'I'.code.toByte() &&
            head[1] == 'D'.code.toByte() &&
            head[2] == '3'.code.toByte()
        ) {
            return Container.MP3
        }
        // MPEG audio frame sync: 11 set bits.
        if (length >= 2 &&
            head[0] == 0xFF.toByte() &&
            (head[1].toInt() and 0xE0) == 0xE0
        ) {
            return Container.MP3
        }
        return Container.UNKNOWN
    }

    /**
     * Opens [input] and returns a stream already converted to
     * `PCM_SIGNED 16 bit little endian` at the source sample rate.
     */
    fun openPcm(input: InputStream): AudioInputStream = open(input).stream

    /**
     * Like [openPcm], but also reports which container the bytes really were.
     * The UI uses that for the quality indicator next to the player.
     */
    fun open(input: InputStream): Decoded {
        val buffered = BufferedInputStream(input, BUFFER_SIZE)
        val container = detect(buffered)
        lastContainer = container

        val decoded = openRaw(buffered, container)
        return Decoded(toPcm(decoded, container), container)
    }

    private fun detect(stream: BufferedInputStream): Container {
        stream.mark(MARK_LIMIT)
        val head = ByteArray(12)
        var read = 0
        while (read < head.size) {
            val n = stream.read(head, read, head.size - read)
            if (n < 0) break
            read += n
        }
        stream.reset()
        return sniff(head, read)
    }

    private fun openRaw(stream: BufferedInputStream, container: Container): AudioInputStream {
        val failures = mutableListOf<String>()

        suspendUnit {
            when (container) {
                Container.FLAC -> org.jflac.sound.spi.FlacAudioFileReader()
                    .getAudioInputStream(stream)

                Container.MP3 -> javazoom.spi.mpeg.sampled.file.MpegAudioFileReader()
                    .getAudioInputStream(stream)

                else -> AudioSystem.getAudioInputStream(stream)
            }
        }.let { result ->
            if (result.isSuccess) return result.getOrThrow()
            failures += "${result.exceptionOrNull()?.message}"
        }

        // The sniff can be wrong (a leading ID3 tag, a WAV wrapper around
        // something else...). Try the remaining readers before giving up.
        val alternatives = when (container) {
            Container.FLAC -> listOf(Container.MP3, Container.WAV)
            Container.MP3 -> listOf(Container.FLAC, Container.WAV)
            else -> listOf(Container.MP3, Container.FLAC)
        }
        for (alternative in alternatives) {
            stream.mark(MARK_LIMIT)
            val attempt = runCatching {
                when (alternative) {
                    Container.FLAC -> org.jflac.sound.spi.FlacAudioFileReader()
                        .getAudioInputStream(stream)

                    Container.MP3 -> javazoom.spi.mpeg.sampled.file.MpegAudioFileReader()
                        .getAudioInputStream(stream)

                    else -> AudioSystem.getAudioInputStream(stream)
                }
            }
            if (attempt.isSuccess) {
                lastContainer = alternative
                return attempt.getOrThrow()
            }
            failures += "${attempt.exceptionOrNull()?.message}"
            runCatching { stream.reset() }
        }

        throw IllegalArgumentException(
            UiStrings.s.decodeFailed(container.name, failures.joinToString("; "))
        )
    }

    private fun toPcm(stream: AudioInputStream, container: Container): AudioInputStream {
        val source = stream.format
        if (source.encoding == AudioFormat.Encoding.PCM_SIGNED &&
            source.sampleSizeInBits == 16 &&
            !source.isBigEndian
        ) {
            return stream
        }

        val pcm = AudioFormat(
            AudioFormat.Encoding.PCM_SIGNED,
            source.sampleRate,
            16,
            source.channels,
            source.channels * 2,
            source.sampleRate,
            false
        )

        // AudioSystem finds the provider registered by mp3spi/jflac.
        runCatching { return AudioSystem.getAudioInputStream(pcm, stream) }

        // If the SPI registration is missing (shaded jar, odd class loader),
        // instantiate the conversion provider ourselves.
        val provider = when (container) {
            Container.FLAC -> runCatching {
                org.jflac.sound.spi.FlacFormatConversionProvider()
            }.getOrNull()

            Container.MP3 -> runCatching {
                javazoom.spi.mpeg.sampled.convert.MpegFormatConversionProvider()
            }.getOrNull()

            else -> null
        }
        if (provider != null) {
            runCatching { return provider.getAudioInputStream(pcm, stream) }
        }

        throw IllegalArgumentException(
            UiStrings.s.convertFailed(source.encoding.toString(), container.name)
        )
    }

    /** Keeps the reader calls in one place with a uniform result type. */
    private inline fun suspendUnit(block: () -> AudioInputStream): Result<AudioInputStream> =
        runCatching(block)
}