package dev.gladix.desktop.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import dev.gladix.desktop.ui.text.UiStrings
import kotlinx.coroutines.delay

@Composable
fun SearchScreen(state: AppState) {
    var query by remember { mutableStateOf(state.searchQuery) }
    var submitted by remember { mutableStateOf(state.searchQuery.ifBlank { "" }) }
    val strings = UiStrings.s

    val recent = remember(submitted) {
        state.ctx.settings.getStringSet(PrefsKeys.SEARCH_HISTORY)?.toList() ?: emptyList()
    }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text(strings.searchFieldLabel) },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(Icons.Rounded.Close, contentDescription = strings.clear)
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                submitted = query
                state.searchQuery = query
            }),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp)
        )

        if (recent.isNotEmpty() && submitted.isBlank()) {
            LazyRow(modifier = Modifier.padding(horizontal = 20.dp)) {
                items(recent) { entry ->
                    AssistChip(
                        onClick = {
                            query = entry
                            submitted = entry
                        },
                        label = { Text(entry, fontWeight = FontWeight.Normal) },
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
            }
        }

        if (submitted.isBlank()) {
            EmptyBox(strings.searchNotLoggedInHint)
            return@Column
        }

        // Debounce so typing does not hammer the API.
        LaunchedEffect(submitted) {
            delay(250)
            state.saveSearchQuery(submitted)
        }

        val feedLoader = rememberLoader(submitted) {
            state.ctx.client.searchFeed(submitted)
        }
        val feed = feedLoader.data

        when {
            feedLoader.loading && feed == null -> LoadingBox()
            feedLoader.error != null && feed == null ->
                ErrorBox(feedLoader.error!!) { feedLoader.reload() }
            feed != null -> TabbedShelves(state, feed)
            else -> Box(Modifier.fillMaxSize())
        }
    }
}

object PrefsKeys {
    const val SEARCH_HISTORY = "desktop_search_history"
}

/** Keeps the last searches around as chips. */
fun AppState.saveSearchQuery(query: String) {
    if (query.isBlank()) return
    val key = PrefsKeys.SEARCH_HISTORY
    val current = ctx.settings.getStringSet(key)?.toMutableList() ?: mutableListOf()
    current.remove(query)
    current.add(0, query)
    while (current.size > 12) current.removeAt(current.size - 1)
    ctx.settings.putStringSet(key, current.toSet())
}