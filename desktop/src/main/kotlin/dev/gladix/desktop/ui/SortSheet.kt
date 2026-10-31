package dev.gladix.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import dev.gladix.desktop.ui.text.UiStrings

/**
 * The "sort and filter" sheet from the Android app, as a modal dialog.
 *
 * The checkbox mirrors the Android behaviour: with it checked the choice is
 * written to settings.properties and reused next time, unchecked it only lasts
 * for this screen. Nothing changes until Apply is pressed.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SortSheet(state: SortState, onDismiss: () -> Unit) {
    val strings = UiStrings.s
    var draft by remember { mutableStateOf(state.spec) }
    var persist by remember { mutableStateOf(true) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 6.dp,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.width(430.dp)
        ) {
            Column(Modifier.padding(horizontal = 22.dp, vertical = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = strings.cancel)
                    }
                    Text(
                        text = strings.sortAndFilter,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    IconButton(onClick = { draft = SortSpec() }) {
                        Icon(Icons.Rounded.RestartAlt, contentDescription = strings.reset)
                    }
                }

                Spacer(Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = strings.reversed,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = draft.reversed,
                        onCheckedChange = { draft = draft.copy(reversed = it) }
                    )
                }

                Text(
                    text = strings.sortBy,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SortField.entries.forEach { field ->
                        FilterChip(
                            selected = draft.field == field,
                            onClick = { draft = draft.copy(field = field) },
                            label = { Text(sortFieldLabel(field)) }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        text = strings.save,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { persist = !persist }
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    )
                    Checkbox(checked = persist, onCheckedChange = { persist = it })
                }

                Button(
                    onClick = {
                        state.set(draft, persist)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp).height(48.dp)
                ) {
                    Text(strings.apply)
                }
            }
        }
    }
}

@Composable
fun sortFieldLabel(field: SortField): String {
    val strings = UiStrings.s
    return when (field) {
        SortField.DEFAULT -> strings.sortDefault
        SortField.TITLE -> strings.sortTitle
        SortField.DURATION -> strings.sortDuration
        SortField.ARTISTS -> strings.sortArtists
        SortField.ALBUMS -> strings.sortAlbums
    }
}

/**
 * The header button that opens [SortSheet], plus chips showing what is active,
 * so a reversed or re-sorted list is never a mystery.
 */
@Composable
fun SortControls(state: SortState, modifier: Modifier = Modifier) {
    val strings = UiStrings.s
    var open by remember { mutableStateOf(false) }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (state.spec.field != SortField.DEFAULT) {
            ActiveChip(sortFieldLabel(state.spec.field))
        }
        if (state.spec.reversed) {
            ActiveChip(strings.reversed)
        }
        IconButton(onClick = { open = true }) {
            Icon(
                Icons.AutoMirrored.Rounded.Sort,
                contentDescription = strings.sortAndFilter,
                tint = if (state.spec.isDefault) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.primary
            )
        }
    }

    if (open) SortSheet(state) { open = false }
}

@Composable
private fun ActiveChip(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Rounded.Check,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}
