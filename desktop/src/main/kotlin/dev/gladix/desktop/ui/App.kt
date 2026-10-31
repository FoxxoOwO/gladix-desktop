package dev.gladix.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.gladix.desktop.ui.text.Strings
import dev.gladix.desktop.ui.text.UiStrings

private data class NavEntry(val page: Page, val label: String, val icon: ImageVector)

private fun navEntries(s: Strings) = listOf(
    NavEntry(Page.Home, s.navHome, Icons.Rounded.Home),
    NavEntry(Page.Search, s.navSearch, Icons.Rounded.Search),
    NavEntry(Page.Library, s.navLibrary, Icons.Rounded.LibraryMusic),
    NavEntry(Page.Downloads, s.navDownloads, Icons.Rounded.Download),
    NavEntry(Page.History, s.navHistory, Icons.Rounded.History),
    NavEntry(Page.Queue, s.navQueue, Icons.Rounded.QueueMusic),
    NavEntry(Page.Settings, s.navSettings, Icons.Rounded.Settings)
)

@Composable
fun App(state: AppState) {
    val snackbar = remember { SnackbarHostState() }
    val strings = UiStrings.s

    LaunchedEffect(state.toast) {
        val message = state.toast
        if (message != null) {
            snackbar.showSnackbar(message)
            state.toast = null
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (state.loggedIn) {
                PlayerBar(
                    state = state,
                    onOpenQueue = { state.navigate(Page.Queue) },
                    onOpenNowPlaying = { if (state.ctx.player.current != null) state.navigate(Page.NowPlaying) },
                    showProgress = state.page !is Page.NowPlaying
                )
            }
        }
    ) { padding ->
        Surface(
            modifier = Modifier.fillMaxSize().padding(padding),
            color = MaterialTheme.colorScheme.background
        ) {
            if (!state.loggedIn) {
                LoginScreen(state)
                return@Surface
            }
            Row(Modifier.fillMaxSize()) {
                NavigationRail(containerColor = MaterialTheme.colorScheme.surface) {
                    Column(
                        modifier = Modifier.padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.LibraryMusic,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(30.dp)
                        )
                        Text(
                            text = "Gladix",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 2.dp, bottom = 6.dp)
                        )
                    }
                    navEntries(strings).forEach { entry ->
                        NavigationRailItem(
                            selected = state.page::class == entry.page::class,
                            onClick = {
                                if (state.page is Page.Item || state.page is Page.NowPlaying) {
                                    state.backStack = emptyList()
                                }
                                state.page = entry.page
                            },
                            icon = { Icon(entry.icon, contentDescription = entry.label) },
                            label = { Text(entry.label) }
                        )
                    }
                }
                HorizontalDivider(
                    modifier = Modifier.fillMaxSize(0.001f),
                    color = MaterialTheme.colorScheme.outline
                )
                Box(Modifier.fillMaxSize()) {
                    when (val page = state.page) {
                        is Page.Home -> HomeScreen(state)
                        is Page.Search -> SearchScreen(state)
                        is Page.Library -> LibraryScreen(state)
                        is Page.Downloads -> DownloadsScreen(state)
                        is Page.History -> HistoryScreen(state)
                        is Page.Queue -> QueueScreen(state)
                        is Page.Settings -> SettingsScreen(state)
                        is Page.NowPlaying -> NowPlayingScreen(state)
                        is Page.Item -> ItemScreen(state, page.item)
                        is Page.Category -> CategoryScreen(state, page.category)
                    }
                }
            }
        }
    }
}