package dev.gladix.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.gladix.desktop.core.AppPaths
import dev.gladix.desktop.core.PrefsSettings
import dev.gladix.desktop.core.UpdateChecker
import dev.gladix.desktop.ui.text.Lang
import dev.gladix.desktop.ui.text.UiStrings
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(state: AppState) {
    val settings = state.ctx.settings
    val strings = UiStrings.s
    val scope = rememberCoroutineScope()
    var quality by remember {
        mutableStateOf(settings.getStringOrDefault(PrefsSettings.KEY_QUALITY, PrefsSettings.QUALITY_FLAC))
    }
    var logging by remember {
        mutableStateOf(settings.getBoolean(PrefsSettings.KEY_LOGGING) ?: true)
    }
    var country by remember { mutableStateOf(settings.getStringOrDefault(PrefsSettings.KEY_COUNTRY, "")) }
    var language by remember { mutableStateOf(settings.getStringOrDefault(PrefsSettings.KEY_LANGUAGE, "")) }
    var cacheSizeMb by remember {
        mutableStateOf(settings.getInt(PrefsSettings.KEY_CACHE_SIZE_MB) ?: PrefsSettings.DEFAULT_CACHE_SIZE_MB)
    }
    var cacheUsedMb by remember {
        mutableStateOf(state.ctx.cache.currentSizeBytes() / (1024L * 1024L))
    }
    var proxy by remember { mutableStateOf(settings.getStringOrDefault(PrefsSettings.KEY_PROXY, "")) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenTitle(strings.settingsTitle)

        SettingsSection(strings.sectionAppearance) {
            Text(
                strings.uiLanguage,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Lang.entries.forEach { candidate ->
                    FilterChip(
                        selected = UiStrings.lang == candidate,
                        onClick = {
                            UiStrings.set(candidate)
                            settings.putString(PrefsSettings.KEY_UI_LANG, candidate.code)
                        },
                        label = { Text(candidate.nativeName) }
                    )
                }
            }
        }

        HorizontalDivider()

        SettingsSection(strings.sectionPlayback) {
            Text(
                strings.streamQuality,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 8.dp)
            ) {
                listOf(
                    PrefsSettings.QUALITY_FLAC to "FLAC (lossless)",
                    PrefsSettings.QUALITY_320 to "MP3 320",
                    PrefsSettings.QUALITY_128 to "MP3 128"
                ).forEach { (value, label) ->
                    FilterChip(
                        selected = quality == value,
                        onClick = {
                            quality = value
                            settings.putString(PrefsSettings.KEY_QUALITY, value)
                        },
                        label = { Text(label) }
                    )
                }
            }
            Text(
                strings.qualityNote,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        HorizontalDivider()

        val mediaSession = state.ctx.mediaSession
        var mediaSessionEnabled by remember {
            mutableStateOf(settings.getBoolean(PrefsSettings.KEY_MEDIA_SESSION) ?: true)
        }
        SettingsSection(strings.sectionWindows) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        strings.controlFromWindows,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        strings.controlFromWindowsNote,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = mediaSessionEnabled,
                    onCheckedChange = {
                        mediaSessionEnabled = it
                        mediaSession.setEnabled(it)
                    }
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                if (mediaSessionEnabled) mediaSession.status else strings.mediaDisabledNote,
                style = MaterialTheme.typography.bodySmall,
                color = if (mediaSession.available) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.error
                }
            )
        }

        HorizontalDivider()

        SettingsSection(strings.sectionAccount) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        strings.logPlays,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        strings.logPlaysNote,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = logging,
                    onCheckedChange = {
                        logging = it
                        settings.putBoolean(PrefsSettings.KEY_LOGGING, it)
                    }
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = country,
                    onValueChange = {
                        country = it
                        settings.putString(PrefsSettings.KEY_COUNTRY, it)
                    },
                    label = { Text(strings.countryLabel) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = language,
                    onValueChange = {
                        language = it
                        settings.putString(PrefsSettings.KEY_LANGUAGE, it)
                    },
                    label = { Text(strings.deezerLanguageLabel) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = proxy,
                onValueChange = {
                    proxy = it
                    settings.putString(PrefsSettings.KEY_PROXY, it)
                },
                label = { Text(strings.proxyLabel) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                strings.changesAfterRestart,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        HorizontalDivider()

        SettingsSection(strings.sectionData) {
            Text(
                strings.musicCache,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 8.dp)
            ) {
                listOf(0, 512, 1024, 2048, 4096).forEach { mb ->
                    FilterChip(
                        selected = cacheSizeMb == mb,
                        onClick = {
                            cacheSizeMb = mb
                            settings.putInt(PrefsSettings.KEY_CACHE_SIZE_MB, mb)
                            state.ctx.cache.evictIfNeeded()
                            cacheUsedMb = state.ctx.cache.currentSizeBytes() / (1024L * 1024L)
                        },
                        label = { Text(strings.cacheSizeMb(mb)) }
                    )
                }
            }
            Text(
                text = strings.cacheUsed(cacheUsedMb),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
            )

            Text(
                text = strings.dataFolder(AppPaths.root.absolutePath),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = strings.downloadsFolder(AppPaths.downloads.absolutePath),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = {
                    runCatching {
                        java.awt.Desktop.getDesktop().open(AppPaths.downloads)
                    }
                }) { Text(strings.openFolder) }
                OutlinedButton(onClick = {
                    state.ctx.cache.clear()
                    cacheUsedMb = 0L
                    state.showToast(strings.cacheCleared)
                }) { Text(strings.clearCache) }
                OutlinedButton(onClick = {
                    state.ctx.history.clear()
                    state.showToast(strings.historyCleared)
                }) { Text(strings.clearHistory) }
            }
        }

        HorizontalDivider()

        val discord = state.ctx.discord
        var discordEnabled by remember {
            mutableStateOf(settings.getBoolean(PrefsSettings.KEY_DISCORD_PRESENCE) ?: false)
        }
        var discordClientId by remember {
            mutableStateOf(
                settings.getStringOrDefault(
                    PrefsSettings.KEY_DISCORD_CLIENT_ID,
                    PrefsSettings.DEFAULT_DISCORD_CLIENT_ID
                )
            )
        }
        SettingsSection(strings.sectionDiscord) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        strings.discordPresence,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        strings.discordPresenceNote,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = discordEnabled,
                    onCheckedChange = {
                        discordEnabled = it
                        settings.putBoolean(PrefsSettings.KEY_DISCORD_PRESENCE, it)
                        discord.setEnabled(it)
                    }
                )
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = discordClientId,
                onValueChange = {
                    discordClientId = it
                    settings.putString(PrefsSettings.KEY_DISCORD_CLIENT_ID, it.trim())
                    if (discordEnabled) {
                        discord.setEnabled(false)
                        discord.setEnabled(true)
                    }
                },
                label = { Text(strings.discordClientId) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                strings.discordClientIdNote,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp)
            )
        }

        HorizontalDivider()

        val updates = state.ctx.updates
        var autoUpdate by remember {
            mutableStateOf(settings.getBoolean(PrefsSettings.KEY_UPDATE_CHECK) ?: true)
        }
        SettingsSection(strings.sectionUpdates) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        strings.autoUpdateCheck,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        strings.autoUpdateCheckNote,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = autoUpdate,
                    onCheckedChange = {
                        autoUpdate = it
                        settings.putBoolean(PrefsSettings.KEY_UPDATE_CHECK, it)
                    }
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = { scope.launch { updates.check() } },
                    enabled = updates.state != UpdateChecker.State.Checking
                ) { Text(strings.checkNow) }

                when (updates.state) {
                    UpdateChecker.State.Checking -> Text(
                        strings.checkingUpdates,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    UpdateChecker.State.UpToDate -> Text(
                        strings.upToDate,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    UpdateChecker.State.Available -> OutlinedButton(
                        onClick = { updates.download() }
                    ) { Text(strings.downloadUpdate) }

                    UpdateChecker.State.Downloading -> Text(
                        strings.downloadingUpdate,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    UpdateChecker.State.Downloaded -> OutlinedButton(
                        onClick = { updates.openDownloadFolder() }
                    ) { Text(strings.openDownloads) }

                    UpdateChecker.State.Failed -> Text(
                        "${strings.updateFailed}: ${updates.error.orEmpty()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )

                    UpdateChecker.State.Idle -> Unit
                }
            }

            when (updates.state) {
                UpdateChecker.State.Available -> {
                    val version = updates.latestVersion.orEmpty()
                    Text(
                        strings.updateAvailableVersion(version),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                UpdateChecker.State.Downloading -> {
                    LinearProgressIndicator(
                        progress = { updates.downloadProgress },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    )
                }

                UpdateChecker.State.Downloaded -> Text(
                    strings.updateDownloaded,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp)
                )

                else -> Unit
            }
        }

        HorizontalDivider()

        SettingsSection(strings.sectionUser) {
            Text(
                text = state.ctx.client.user?.name.orEmpty().ifBlank { strings.notLoggedIn },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = {
                state.ctx.player.stop()
                state.ctx.client.logout()
                state.backStack = emptyList()
                state.page = Page.Home
            }) { Text(strings.signOut) }
        }

        SettingsSection(strings.sectionAbout) {
            Text(
                text = strings.aboutLine,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "v${dev.gladix.desktop.core.AppVersion.NAME}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
            Text(
                text = strings.aboutCredits,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp)
            )
            Spacer(Modifier.width(4.dp))
        }
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 10.dp)
        )
        content()
    }
}