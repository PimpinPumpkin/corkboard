package app.corkboard.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.material3.SnackbarHost
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
    val items = remember(state.items, hidden) { state.items.filter { it.postingId !in hidden } }

    var text by rememberSaveable(query.text) { mutableStateOf(query.text) }
    var showFilters by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    // Asked the first time a search is saved, which is the first moment a notification makes sense.
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    val gridState = rememberLazyGridState()
    // Ask for the next stretch a couple of screens before the end, so scrolling never hits a wall.
    val nearEnd by remember(items.size) {
        derivedStateOf { (gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= items.size - 24 }
    }
    LaunchedEffect(nearEnd, items.size, state.hasMore) { if (nearEnd && items.isNotEmpty() && state.hasMore) state.loadMore() }
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
                                if (page != null && !state.loading) "${Format.count(page.total)} in ${query.areaName}" else query.areaName,
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
                                store.save(query, newestSeen = state.page?.items?.maxOfOrNull { it.postingId } ?: 0)
                                Alerts.schedule(context)
                                if (Build.VERSION.SDK_INT >= 33) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                                scope.launch { snackbar.showSnackbar("Search saved. You will be told about new listings.") }
                            }
                        }) {
                            Icon(if (isSaved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder, if (isSaved) "Remove saved search" else "Save this search")
                        }
                    },
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    placeholder = { Text("Search ${query.categoryName.lowercase()}") },
                    leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    trailingIcon = {
                        if (text.isNotEmpty()) IconButton(onClick = { text = ""; if (query.text.isNotEmpty()) state.run(query.with("query", emptyList())) }) { Icon(Icons.Outlined.Close, "Clear") }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(28.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focus.clearFocus(); state.run(query.with("query", listOf(text.trim()))) }),
                )
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
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
                    if (page != null && page.subareas.isNotEmpty()) {
                        Picker(
                            label = page.subareas.firstOrNull { it.value == query.subarea }?.label ?: "Whole area",
                            icon = { Icon(Icons.Outlined.LocationOn, null, Modifier.size(18.dp)) },
                            selected = query.subarea != null,
                            options = listOf(Option("Whole area", "")) + page.subareas,
                            onPick = { state.run(query.copy(subarea = it.ifEmpty { null })) },
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
            items.isEmpty() -> Message(pad, "Nothing found. Try fewer filters or a wider area.") {}
            else -> LazyVerticalGrid(
                columns = if (grid) GridCells.Adaptive(160.dp) else GridCells.Fixed(1),
                state = gridState,
                contentPadding = PaddingValues(
                    start = if (grid) 12.dp else 0.dp, end = if (grid) 12.dp else 0.dp,
                    top = pad.calculateTopPadding() + 8.dp, bottom = pad.calculateBottomPadding() + 16.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(if (grid) 8.dp else 0.dp),
            ) {
                itemsIndexed(items, key = { _, l -> l.postingId }) { _, l ->
                    val fav = l.postingId in favoriteIds
                    if (grid) ListingTile(l, fav, onClick = { onOpen(l) }, onFavorite = { store.toggleFavorite(l) })
                    else ListingRow(l, fav, onClick = { onOpen(l) }, onFavorite = { store.toggleFavorite(l) })
                }
                if (state.hasMore) item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(28.dp)) }
                }
            }
        }
    }

    if (showFilters && page != null) {
        FilterSheet(page.filters, query, api, onApply = { showFilters = false; state.run(it) }, onDismiss = { showFilters = false })
    }
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
