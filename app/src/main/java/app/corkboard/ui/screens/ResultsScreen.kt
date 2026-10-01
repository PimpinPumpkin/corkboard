package app.corkboard.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import app.corkboard.data.SearchQuery
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import app.corkboard.data.Units
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.corkboard.data.ClApi
import app.corkboard.data.Listing
import app.corkboard.data.Option
import app.corkboard.data.Store
import app.corkboard.ui.Format
import app.corkboard.ui.ResultsState
import app.corkboard.ui.components.ListingRow
import app.corkboard.ui.components.ListingTile
import app.corkboard.ui.components.SearchPill
import app.corkboard.ui.components.RefreshBox
import app.corkboard.ui.components.SoftField
import app.corkboard.work.Alerts
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultsScreen(state: ResultsState, api: ClApi, store: Store, onBack: () -> Unit, onOpen: (Listing) -> Unit) {
    val query = state.query
    val page = state.page
    val favorites by store.favorites.collectAsStateWithLifecycle()
    val hidden by store.hidden.collectAsStateWithLifecycle()
    val saved by store.saved.collectAsStateWithLifecycle()
    val grid by store.grid.collectAsStateWithLifecycle()
    val favoriteIds = remember(favorites) { favorites.map { it.postingId }.toHashSet() }
    val isSaved = saved.any { it.query == query }
    val homeOnly by store.homeCountryOnly.collectAsStateWithLifecycle()
    val gate = remember(query.areaHost, homeOnly) { store.countryGate(query) }
    val items = remember(state.items, hidden, gate) { state.items.filter { it.postingId !in hidden && (gate == null || gate(it)) } }
    val foreignHidden = gate != null && state.items.any { !gate(it) }

    var text by rememberSaveable(query.text) { mutableStateOf(query.text) }
    var showFilters by remember { mutableStateOf(false) }
    var showPlace by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val units = page?.units ?: Units()
    val focus = LocalFocusManager.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    // Asked the first time a search is saved, which is the first moment a notification makes sense.
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    val notes by store.notes.collectAsStateWithLifecycle()
    /** Long press hides a listing from every search, with a moment to take it back. */
    fun hide(l: Listing) {
        store.setHidden(l.postingId, true)
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            if (snackbar.showSnackbar("Listing hidden", actionLabel = "Undo", duration = SnackbarDuration.Long) == SnackbarResult.ActionPerformed) store.setHidden(l.postingId, false)
        }
    }

    val gridState = rememberLazyGridState()
    // Ask for the next stretch a couple of screens before the end, so scrolling never hits a wall.
    val nearEnd by remember(items.size) {
        derivedStateOf { (gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= items.size - 24 }
    }
    // Counted on what the search returned, not on what is shown: near a border most of a batch can be
    // filtered away, and the next one has to be fetched for the list to fill at all.
    LaunchedEffect(nearEnd, items.size, state.items.size, state.hasMore, state.loadingMore) {
        if (nearEnd && state.items.isNotEmpty() && state.hasMore && !state.loadingMore) state.loadMore()
    }
    // A new search starts at the top.
    LaunchedEffect(query) { gridState.scrollToItem(0) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            // One opaque surface for the whole header, so results scroll under it, not through it.
            Column(Modifier.background(MaterialTheme.colorScheme.surface)) {
                TopAppBar(
                    title = {
                        Column {
                            Text(query.categoryName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                when {
                                    page == null || state.loading -> query.areaName
                                    // A postal code the site does not know is ignored without an error; say so.
                                    query.postal != null && page.place?.postal.isNullOrEmpty() -> "${Format.count(page.total)} in ${query.areaName} · postal code not found"
                                    // The site's count includes the other side of the border; do not repeat it as ours.
                                    foreignHidden -> "Near ${page.place?.city?.takeIf { query.postal != null } ?: query.areaName} · other countries hidden"
                                    query.postal != null -> "${Format.count(page.total)} near ${page.place?.city}"
                                    else -> "${Format.count(page.total)} in ${query.areaName}"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
                    actions = {
                        IconButton(onClick = { store.setGrid(!grid) }) {
                            Icon(if (grid) Icons.AutoMirrored.Outlined.ViewList else Icons.Outlined.GridView, if (grid) "Show as list" else "Show as grid")
                        }
                        IconButton(onClick = {
                            val existing = store.findSaved(query)
                            if (existing != null) {
                                store.removeSaved(existing.id)
                                scope.launch { snackbar.showSnackbar("Search removed") }
                            } else {
                                saving = true
                            }
                        }) {
                            Icon(if (isSaved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder, if (isSaved) "Remove saved search" else "Save this search")
                        }
                    },
                )
                SearchPill(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = "Search ${query.categoryName.lowercase()}",
                    onSearch = { focus.clearFocus(); state.run(query.with("query", listOf(text.trim()))) },
                    onClear = { if (query.text.isNotEmpty()) state.run(query.with("query", emptyList())) },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = query.filterCount > 0,
                        onClick = { showFilters = true },
                        enabled = page != null,
                        label = { Text(if (query.filterCount > 0) "Filters · ${query.filterCount}" else "Filters") },
                        leadingIcon = { Icon(Icons.Outlined.Tune, null, Modifier.size(18.dp)) },
                    )
                    if (page != null && page.sortOptions.size > 1) {
                        Picker(
                            label = page.sortOptions.firstOrNull { it.value == page.sort }?.let { sortName(it) } ?: "Sort",
                            icon = { Icon(Icons.AutoMirrored.Outlined.Sort, null, Modifier.size(18.dp)) },
                            selected = query.sort != null,
                            options = page.sortOptions.map { Option(sortName(it), it.value) },
                            onPick = { state.run(query.with("sort", listOf(it))) },
                        )
                    }
                    if (page != null) {
                        val near = page.place?.takeIf { it.postal.isNotEmpty() }
                        FilterChip(
                            selected = query.subarea != null || query.postal != null,
                            onClick = { showPlace = true },
                            label = {
                                Text(
                                    when {
                                        near != null -> "${near.radius} ${page.units.distance} of ${near.postal}"
                                        query.subarea != null -> page.subareas.firstOrNull { it.value == query.subarea }?.label ?: query.subarea
                                        else -> "Whole area"
                                    },
                                )
                            },
                            leadingIcon = { Icon(Icons.Outlined.LocationOn, null, Modifier.size(18.dp)) },
                        )
                    }
                }
                if (state.loading || state.loadingMore) LinearProgressIndicator(Modifier.fillMaxWidth()) else Box(Modifier.padding(top = 4.dp))
            }
        },
    ) { pad ->
        when {
            state.error != null -> Message(pad, state.error.orEmpty()) { Button(onClick = state::retry) { Text("Try again") } }
            state.loading && items.isEmpty() -> Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            items.isEmpty() && state.hasMore -> Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            items.isEmpty() -> Message(pad, if (foreignHidden) "Nothing found in this country. Listings from other countries are hidden." else "Nothing found. Try fewer filters or a wider area.") {}
            // Pulling down asks the site again, past the fifteen minutes it lets results be reused.
            else -> RefreshBox(isRefreshing = state.refreshing, onRefresh = state::refresh, modifier = Modifier.fillMaxSize().padding(top = pad.calculateTopPadding())) {
            LazyVerticalGrid(
                columns = if (grid) GridCells.Adaptive(160.dp) else GridCells.Fixed(1),
                state = gridState,
                contentPadding = PaddingValues(
                    start = if (grid) 12.dp else 0.dp, end = if (grid) 12.dp else 0.dp,
                    top = 8.dp, bottom = pad.calculateBottomPadding() + 16.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(if (grid) 8.dp else 0.dp),
            ) {
                itemsIndexed(items, key = { _, l -> l.postingId }) { _, l ->
                    val fav = l.postingId in favoriteIds
                    if (grid) ListingTile(l, fav, onClick = { onOpen(l) }, onFavorite = { store.toggleFavorite(l) }, units = units, note = notes[l.postingId], onLongClick = { hide(l) }, modifier = Modifier.animateItem())
                    else ListingRow(l, fav, onClick = { onOpen(l) }, onFavorite = { store.toggleFavorite(l) }, units = units, note = notes[l.postingId], onLongClick = { hide(l) }, modifier = Modifier.animateItem())
                }
                if (state.hasMore) item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(28.dp)) }
                }
            }
            }
        }
    }

    if (showFilters && page != null) {
        FilterSheet(page.filters, query, api, onApply = { showFilters = false; state.run(it) }, onDismiss = { showFilters = false })
    }

    if (saving) {
        SaveSearchDialog(
            suggested = query.text.ifEmpty { query.categoryName },
            onDismiss = { saving = false },
            onSave = { name, alerts ->
                saving = false
                store.save(query, newestSeen = state.page?.items?.maxOfOrNull { it.postingId } ?: 0, name = name.takeIf { it != query.text.ifEmpty { query.categoryName } }, alerts = alerts)
                if (alerts) {
                    Alerts.schedule(context)
                    if (Build.VERSION.SDK_INT >= 33) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                scope.launch { snackbar.showSnackbar(if (alerts) "Search saved, with alerts." else "Search saved.") }
            },
        )
    }

    if (showPlace && page != null) {
        PlaceDialog(query, page.subareas, page.units.distance, homeOnly, store::setHomeCountryOnly, onApply = { showPlace = false; state.run(it) }, onDismiss = { showPlace = false })
    }
}

/** Saving a search: what to call it, and whether the phone should watch it. Alerts start off. */
@Composable
private fun SaveSearchDialog(suggested: String, onDismiss: () -> Unit, onSave: (String, Boolean) -> Unit) {
    // Opens with the suggested name selected and the keyboard up, so typing replaces it outright.
    var name by remember { mutableStateOf(TextFieldValue(suggested, TextRange(0, suggested.length))) }
    var alerts by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save this search") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.copy(text = it.text.take(60)) },
                    singleLine = true,
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
                Row(Modifier.fillMaxWidth().padding(top = 16.dp).clickable { alerts = !alerts }, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Alert me about new listings", style = MaterialTheme.typography.bodyLarge)
                        Text("Checked by the phone about every three hours.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = alerts, onCheckedChange = { alerts = it })
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name.text.trim().ifEmpty { suggested }, alerts) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * Where to search: the whole area, one of its sub-areas, or a distance around a postal code. The
 * three exclude each other, the same as on the site.
 */
@Composable
private fun PlaceDialog(query: SearchQuery, subareas: List<Option>, distanceUnit: String, homeOnly: Boolean, onHomeOnly: (Boolean) -> Unit, onApply: (SearchQuery) -> Unit, onDismiss: () -> Unit) {
    var postal by remember { mutableStateOf(query.postal.orEmpty()) }
    var distance by remember { mutableStateOf(query.distance ?: "10") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Where to look") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Near a postal code", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SoftField(value = postal, onValueChange = { v -> postal = v.filter { it.isLetterOrDigit() || it == ' ' || it == '-' }.take(10) }, modifier = Modifier.weight(1.3f), label = "Postal code", keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters))
                    SoftField(value = distance, onValueChange = { v -> distance = v.filter { it.isDigit() }.take(4) }, modifier = Modifier.weight(1f), label = "Within", suffix = distanceUnit, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                }
                Button(onClick = { onApply(query.near(postal, distance)) }, enabled = postal.isNotBlank(), modifier = Modifier.padding(top = 12.dp)) { Text("Search near here") }

                Row(Modifier.fillMaxWidth().padding(top = 12.dp).clickable { onHomeOnly(!homeOnly) }, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Hide other countries", style = MaterialTheme.typography.bodyLarge)
                        Text("A distance search near a border reaches across it.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = homeOnly, onCheckedChange = onHomeOnly)
                }

                Text("Or pick a part of ${query.areaName}", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 20.dp))
                (listOf(Option("Whole area", "")) + subareas).forEach { o ->
                    val chosen = query.postal == null && (query.subarea ?: "") == o.value
                    Row(
                        Modifier.fillMaxWidth().clickable { onApply(query.near(null, null).copy(subarea = o.value.ifEmpty { null })) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = chosen, onClick = null, modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp))
                        Text(o.label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** The site's own labels for the price sorts are arrows made of currency signs; say it in words. */
private fun sortName(o: Option): String = when (o.value) {
    "date" -> "Newest"
    "dateoldest" -> "Oldest"
    "priceasc" -> "Price: low to high"
    "pricedsc" -> "Price: high to low"
    "dist" -> "Distance"
    "rel" -> "Relevance"
    "upcoming" -> "Upcoming"
    else -> o.label.replaceFirstChar { it.uppercase() }
}

@Composable
private fun Picker(label: String, icon: @Composable () -> Unit, selected: Boolean, options: List<Option>, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        FilterChip(selected = selected, onClick = { open = true }, label = { Text(label) }, leadingIcon = icon)
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { o -> DropdownMenuItem(text = { Text(o.label) }, onClick = { open = false; onPick(o.value) }) }
        }
    }
}

@Composable
private fun Message(pad: PaddingValues, text: String, action: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().padding(pad).padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
            action()
        }
    }
}
