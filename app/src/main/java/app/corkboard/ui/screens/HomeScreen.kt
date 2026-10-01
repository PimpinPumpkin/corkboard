package app.corkboard.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.CircleShape
import app.corkboard.ui.components.Panel
import app.corkboard.ui.components.SearchPill
import app.corkboard.ui.components.SectionTitle
import app.corkboard.ui.components.TonalIcon
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

    Scaffold { pad ->
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = pad.calculateTopPadding() + 12.dp, bottom = pad.calculateBottomPadding() + 32.dp)) {
            item {
                SearchPill(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = "Search craigslist",
                    onSearch = { if (text.isNotBlank()) onSearch(query(Catalog.forSale.all, text)) },
                    trailing = { IconButton(onClick = onSettings) { Icon(Icons.Outlined.Settings, "Settings", tint = MaterialTheme.colorScheme.onSurfaceVariant) } },
                )
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Where every search from this screen looks. Tapping it changes the place.
                    Row(
                        Modifier.weight(1f).heightIn(min = 44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer).clickable(onClick = onPickArea).padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.LocationOn, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                        val n = near
                        Text(
                            if (n == null) area.name else "${n.distance} ${area.distanceUnit} of ${n.city.ifEmpty { n.postal }}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                    BadgedBox(badge = { if (unseen > 0) Badge { Text(if (unseen > 99) "99+" else "$unseen") } }) {
                        TonalIcon(Icons.Outlined.BookmarkBorder, "Saved searches", onSaved)
                    }
                    TonalIcon(Icons.Outlined.FavoriteBorder, "Favorites", onFavorites)
                }
            }
            if (saved.isNotEmpty()) item {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    saved.forEach { s ->
                        AssistChip(
                            onClick = { onSearch(s.query) },
                            label = { Text(s.query.text.ifEmpty { s.query.categoryName } + if (s.unseen > 0) " · ${s.unseen} new" else "") },
                            leadingIcon = { Icon(Icons.Outlined.BookmarkBorder, null, Modifier.size(18.dp)) },
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(20.dp)) }
            item(key = "pinned") {
                // The pinned categories. The grid grows and shrinks smoothly as pins come and go.
                Column(Modifier.animateContentSize(spring(dampingRatio = 0.8f, stiffness = 380f))) {
                    pinned.chunked(2).forEach { pair ->
                        Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            pair.forEach { c -> key(c.abbr) { FeaturedTile(c, Modifier.weight(1f), onUnpin = { store.togglePinned(c.abbr) }) { onSearch(query(c)) } } }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                    if (pinned.isEmpty()) Text(
                        "Tap the pin beside any category below to keep it up here.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(4.dp),
                    )
                }
            }
            Catalog.sections.forEach { section ->
                val collapsible = section === Catalog.forSale
                val shown = if (collapsible && !expanded) section.categories.filter { it.abbr !in pinnedAbbrs }.take(8) else section.categories
                item(key = section.name) {
                    SectionTitle(section.name) {
                        if (section !== Catalog.more) TextButton(onClick = { onSearch(query(section.all)) }) { Text("See all") }
                    }
                    Panel(Modifier.animateContentSize(spring(dampingRatio = 0.85f, stiffness = 380f))) {
                        shown.forEach { c -> key(c.abbr) { CategoryRow(c, c.abbr in pinnedAbbrs, onPin = { store.togglePinned(c.abbr) }) { onSearch(query(c)) } } }
                        if (collapsible) Row(
                            Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(horizontal = 20.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                if (expanded) "Show fewer" else "Show all ${section.categories.size} categories",
                                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f),
                            )
                            Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
            item {
                Text(
                    "The pin beside a category keeps it at the top of this screen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp, start = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun FeaturedTile(c: Category, modifier: Modifier, onUnpin: () -> Unit, onClick: () -> Unit) {
    // Tiles arrive with a little spring instead of just appearing.
    val appear = remember { Animatable(0.85f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 400f)) }
    Box(modifier.scale(appear.value)) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .combinedClickable(onClick = onClick, onLongClick = onUnpin).padding(16.dp),
        ) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                Icon(featuredIcons[c.abbr] ?: Icons.Outlined.Sell, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Spacer(Modifier.height(14.dp))
            Text(c.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onUnpin, modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)) {
            Icon(Icons.Filled.PushPin, "Unpin ${c.name}", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun CategoryRow(c: Category, pinned: Boolean, onPin: () -> Unit, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onPin).padding(start = 20.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(c.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        // The pin is a button in plain sight: filled and tilted upright when the category is pinned.
        val turn by animateFloatAsState(if (pinned) 0f else 35f, spring(dampingRatio = 0.5f, stiffness = 400f), label = "pin")
        IconButton(onClick = onPin) {
            Icon(
                if (pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                if (pinned) "Unpin ${c.name}" else "Pin ${c.name}",
                Modifier.size(20.dp).rotate(turn),
                tint = if (pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            )
        }
    }
}
