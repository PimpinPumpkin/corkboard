package app.corkboard.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Apartment
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.Handyman
import androidx.compose.material.icons.automirrored.outlined.DirectionsBike
import androidx.compose.material.icons.outlined.TwoWheeler
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Kitchen
import androidx.compose.material.icons.outlined.Sailing
import androidx.compose.material.icons.outlined.Bed
import androidx.compose.material.icons.outlined.Work
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Chair
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Redeem
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.corkboard.data.Area
import app.corkboard.data.Catalog
import app.corkboard.data.Category
import app.corkboard.data.SearchQuery
import app.corkboard.data.Section
import app.corkboard.data.Store

private val featuredIcons: Map<String, ImageVector> = mapOf(
    "cta" to Icons.Outlined.DirectionsCar,
    "zip" to Icons.Outlined.Redeem,
    "sya" to Icons.Outlined.Computer,
    "ela" to Icons.Outlined.Headphones,
    "fua" to Icons.Outlined.Chair,
    "apa" to Icons.Outlined.Apartment,
    "moa" to Icons.Outlined.Smartphone,
    "tla" to Icons.Outlined.Handyman,
    "bia" to Icons.AutoMirrored.Outlined.DirectionsBike,
    "mca" to Icons.Outlined.TwoWheeler,
    "pta" to Icons.Outlined.Build,
    "vga" to Icons.Outlined.SportsEsports,
    "msa" to Icons.Outlined.MusicNote,
    "ppa" to Icons.Outlined.Kitchen,
    "boo" to Icons.Outlined.Sailing,
    "roo" to Icons.Outlined.Bed,
    "jjj" to Icons.Outlined.Work,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    area: Area?,
    store: Store,
    onSearch: (SearchQuery) -> Unit,
    onPickArea: () -> Unit,
    onFavorites: () -> Unit,
    onSaved: () -> Unit,
    onSettings: () -> Unit,
) {
    area ?: return
    val saved by store.saved.collectAsStateWithLifecycle()
    val near by store.near.collectAsStateWithLifecycle()
    val pinnedAbbrs by store.pinned.collectAsStateWithLifecycle()
    val all = remember { Catalog.sections.flatMap { it.categories }.distinctBy { it.abbr } }
    val pinned = remember(pinnedAbbrs) { pinnedAbbrs.mapNotNull { abbr -> all.firstOrNull { it.abbr == abbr } } }
    val unseen = saved.sumOf { it.unseen }
    var text by rememberSaveable { mutableStateOf("") }
    var expanded by rememberSaveable { mutableStateOf(false) }

    fun query(c: Category, search: String = "") = SearchQuery(
        areaHost = area.hostname, areaName = area.name, category = c.abbr, categoryName = c.name,
        params = if (search.isBlank()) emptyMap() else mapOf("query" to listOf(search.trim())),
    ).near(near?.postal, near?.distance)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Corkboard") },
                actions = {
                    IconButton(onClick = onSaved) {
                        BadgedBox(badge = { if (unseen > 0) Badge { Text(if (unseen > 99) "99+" else "$unseen") } }) {
                            Icon(Icons.Outlined.BookmarkBorder, "Saved searches")
                        }
                    }
                    IconButton(onClick = onFavorites) { Icon(Icons.Outlined.FavoriteBorder, "Favorites") }
                    IconButton(onClick = onSettings) { Icon(Icons.Outlined.Settings, "Settings") }
                },
            )
        },
    ) { pad ->
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = pad.calculateTopPadding(), bottom = pad.calculateBottomPadding() + 24.dp)) {
            item {
                AssistChip(
                    onClick = onPickArea,
                    label = {
                        val n = near
                        Text(
                            if (n == null) area.name else "${n.distance} ${area.distanceUnit} of ${n.city.ifEmpty { n.postal }} · ${area.name}",
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    },
                    leadingIcon = { Icon(Icons.Outlined.LocationOn, null, Modifier.size(18.dp)) },
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search everything for sale") },
                    leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    singleLine = true,
                    shape = RoundedCornerShape(28.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { if (text.isNotBlank()) onSearch(query(Catalog.forSale.all, text)) }),
                )
                Spacer(Modifier.height(16.dp))
            }
            if (saved.isNotEmpty()) item {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    saved.forEach { s ->
                        AssistChip(
                            onClick = { onSearch(s.query) },
                            label = { Text(s.query.text.ifEmpty { s.query.categoryName } + if (s.unseen > 0) " · ${s.unseen} new" else "") },
                            leadingIcon = { Icon(Icons.Outlined.BookmarkBorder, null, Modifier.size(18.dp)) },
                        )
                    }
                }
            }
            items(pinned.chunked(2)) { pair ->
                Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pair.forEach { c -> FeaturedTile(c, Modifier.weight(1f), onLongClick = { store.togglePinned(c.abbr) }) { onSearch(query(c)) } }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            item {
                Text(
                    "Press and hold a category to pin or unpin it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Catalog.sections.forEach { section ->
                val collapsible = section === Catalog.forSale
                val shown = if (collapsible && !expanded) section.categories.filter { it.abbr !in pinnedAbbrs }.take(8) else section.categories
                item { SectionHeader(section, onAll = if (section === Catalog.more) null else ({ onSearch(query(section.all)) })) }
                items(shown, key = { section.name + it.abbr }) { c -> CategoryRow(c, c.abbr in pinnedAbbrs, onLongClick = { store.togglePinned(c.abbr) }) { onSearch(query(c)) } }
                if (collapsible) item {
                    TextButton(onClick = { expanded = !expanded }) {
                        Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null)
                        Spacer(Modifier.width(4.dp))
                        Text(if (expanded) "Show fewer" else "Show all ${section.categories.size} categories")
                    }
                }
            }
        }
    }
}

@Composable
private fun FeaturedTile(c: Category, modifier: Modifier, onLongClick: () -> Unit, onClick: () -> Unit) {
    Card(
        modifier = modifier.clip(RoundedCornerShape(20.dp)).combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Icon(featuredIcons[c.abbr] ?: Icons.Outlined.PushPin, null, Modifier.size(28.dp))
            Spacer(Modifier.height(12.dp))
            Text(c.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SectionHeader(section: Section, onAll: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(section.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        if (onAll != null) TextButton(onClick = onAll) { Text("See all") }
    }
}

@Composable
private fun CategoryRow(c: Category, pinned: Boolean, onLongClick: () -> Unit, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick).padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(c.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (pinned) Icon(Icons.Outlined.PushPin, "Pinned", Modifier.padding(end = 8.dp).size(18.dp), tint = MaterialTheme.colorScheme.primary)
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
