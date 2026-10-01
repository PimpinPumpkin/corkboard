package app.corkboard.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material3.Badge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.corkboard.data.Listing
import app.corkboard.data.SavedSearch
import app.corkboard.data.SearchQuery
import app.corkboard.data.Store
import app.corkboard.ui.components.ListingRow
import app.corkboard.work.Alerts

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ListScaffold(title: String, onBack: () -> Unit, empty: String?, content: @Composable (PaddingValues) -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text(title) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } })
        },
    ) { pad ->
        if (empty != null) {
            Box(Modifier.fillMaxSize().padding(pad).padding(32.dp), contentAlignment = Alignment.Center) {
                Text(empty, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
        } else {
            content(pad)
        }
    }
}

/** The listings the user kept: the hearted ones, or every one with a note on it. */
@Composable
fun FavoritesScreen(store: Store, onBack: () -> Unit, onOpen: (Listing) -> Unit) {
    val favorites by store.favorites.collectAsStateWithLifecycle()
    val noted by store.noted.collectAsStateWithLifecycle()
    val notes by store.notes.collectAsStateWithLifecycle()
    var showNoted by rememberSaveable { mutableStateOf(false) }
    val favoriteIds = remember(favorites) { favorites.map { it.postingId }.toHashSet() }
    val shown = if (showNoted) noted else favorites
    ListScaffold("Saved listings", onBack, null) { pad ->
        LazyColumn(contentPadding = pad) {
            item {
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !showNoted, onClick = { showNoted = false }, label = { Text("Favorites · ${favorites.size}") })
                    FilterChip(selected = showNoted, onClick = { showNoted = true }, label = { Text("With notes · ${noted.size}") })
                }
            }
            if (shown.isEmpty()) item {
                Text(
                    if (showNoted) "Write a note on any listing and it shows up here, hearted or not." else "Tap the heart on a listing to keep it here.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                )
            }
            items(shown, key = { it.postingId }) { l ->
                ListingRow(l, favorite = l.postingId in favoriteIds, onClick = { onOpen(l) }, onFavorite = { store.toggleFavorite(l) }, note = notes[l.postingId])
            }
        }
    }
}

@Composable
fun SavedScreen(store: Store, onBack: () -> Unit, onOpen: (SearchQuery) -> Unit) {
    val saved by store.saved.collectAsStateWithLifecycle()
    val context = LocalContext.current
    ListScaffold("Saved searches", onBack, if (saved.isEmpty()) "Tap the bookmark on any search to save it and hear about new listings." else null) { pad ->
        LazyColumn(contentPadding = pad) {
            items(saved, key = { it.id }) { s ->
                SavedRow(
                    s,
                    onOpen = { onOpen(s.query) },
                    onAlerts = { store.updateSaved(s.id) { it.copy(alerts = !it.alerts) }; Alerts.schedule(context) },
                    onDelete = { store.removeSaved(s.id); Alerts.schedule(context) },
                )
            }
        }
    }
}

/** "Cars & trucks · SF bay area" over a line listing what the search narrows by. */
@Composable
private fun SavedRow(s: SavedSearch, onOpen: () -> Unit, onAlerts: () -> Unit, onDelete: () -> Unit) {
    val q = s.query
    Row(Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(start = 20.dp, end = 8.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(q.text.ifEmpty { q.categoryName }, style = MaterialTheme.typography.bodyLarge)
                if (s.unseen > 0) Badge(Modifier.padding(start = 8.dp)) { Text("${s.unseen} new") }
            }
            val detail = listOfNotNull(
                q.categoryName.takeIf { q.text.isNotEmpty() },
                q.areaName,
                q.filterCount.takeIf { it > 0 }?.let { if (it == 1) "1 filter" else "$it filters" },
            ).joinToString(" · ")
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onAlerts) {
            Icon(if (s.alerts) Icons.Outlined.Notifications else Icons.Outlined.NotificationsOff, if (s.alerts) "Turn alerts off" else "Turn alerts on")
        }
        IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, "Delete saved search") }
    }
}
