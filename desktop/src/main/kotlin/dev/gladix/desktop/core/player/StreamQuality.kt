package dev.gladix.desktop.core.player

import java.util.Locale

/**
 * What the currently playing stream really is, as reported by the decoder.
 *
 * Deezer hands out MP3 at 128 kbps even when FLAC was asked for (the track is
 * simply not released in lossless), so the setting in Preferences is only a
 * wish. This is the measured truth and is what the UI shows.
 */
data class StreamQuality(
    val container: Decoders.Container,
    val sampleRate: Int,
    val channels: Int,
    val bitsPerSample: Int
) {

    /** `FLAC`, `MP3`, `WAV`, or null when the container could not be identified. */
    val containerName: String? = when (container) {
        Decoders.Container.FLAC -> "FLAC"
        Decoders.Container.MP3 -> "MP3"
        Decoders.Container.WAV -> "WAV"
        Decoders.Container.UNKNOWN -> null
    }

    /** `44.1 kHz`, `48 kHz`, or null for a nonsense sample rate. */
    val sampleRateLabel: String? = when {
        sampleRate <= 0 -> null
        sampleRate % 1000 == 0 -> "${sampleRate / 1000} kHz"
        else -> String.format(Locale.ROOT, "%.1f kHz", sampleRate / 1000.0)
    }

    /** `2ch`, `1ch`, `6ch`. */
    val channelLabel: String? = when {
        channels <= 0 -> null
        else -> "${channels}ch"
    }

    /** `FLAC · 44.1 kHz · 2ch`, or null when nothing is known. */
    val label: String? = listOfNotNull(containerName, sampleRateLabel, channelLabel)
        .takeIf { it.isNotEmpty() }
        ?.joinToString(" · ")
}
