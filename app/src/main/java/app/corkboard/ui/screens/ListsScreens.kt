package app.corkboard.ui.screens

import android.Manifest
import android.content.Intent
import android.os.Build
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.ListAlt
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material3.OutlinedButton
import android.provider.OpenableColumns
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import app.corkboard.data.Export
import app.corkboard.data.Images
import coil.compose.AsyncImage
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
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

/** Which collection a list screen shows: the two built-in ones, or one the user made. */
sealed interface Shelf {
    data object Favorites : Shelf
    data object Noted : Shelf
    data class Custom(val id: Long) : Shelf
}

/** Every collection of kept listings: favorites, the ones with notes, and the user's own lists. */
@Composable
fun ListsScreen(store: Store, onBack: () -> Unit, onOpen: (Shelf) -> Unit) {
    val favorites by store.favorites.collectAsStateWithLifecycle()
    val noted by store.noted.collectAsStateWithLifecycle()
    val lists by store.lists.collectAsStateWithLifecycle()
    var creating by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var importNote by remember { mutableStateOf<String?>(null) }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val rows = runCatching { context.contentResolver.openInputStream(uri)!!.use { Export.listings(it.readBytes().toString(Charsets.UTF_8)) } }.getOrDefault(emptyList())
        importNote = if (rows.isEmpty()) "No listings found in that file. It needs a Link column with craigslist links."
        else {
            // The list takes the file's name: "trucks-under-10k.csv" becomes "trucks under 10k".
            val fileName = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
            val list = store.importList(fileName.orEmpty().substringBeforeLast('.').replace('-', ' ').replace('_', ' '), rows)
            "Imported ${list.items.size} " + (if (list.items.size == 1) "listing" else "listings") + " into \"${list.name}\"."
        }
    }
    ListScaffold("Saved listings", onBack, null) { pad ->
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = pad.calculateTopPadding(), bottom = pad.calculateBottomPadding() + 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { ShelfCard(Icons.Outlined.FavoriteBorder, "Favorites", favorites.size, favorites.firstOrNull()) { onOpen(Shelf.Favorites) } }
            item { ShelfCard(Icons.Outlined.EditNote, "With notes", noted.size, noted.firstOrNull()) { onOpen(Shelf.Noted) } }
            items(lists, key = { it.id }) { l ->
                ShelfCard(Icons.AutoMirrored.Outlined.ListAlt, l.name, l.items.size, l.items.firstOrNull(), Modifier.animateItem()) { onOpen(Shelf.Custom(l.id)) }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilledTonalButton(onClick = { creating = true }, modifier = Modifier.weight(1f).height(56.dp)) {
                        Icon(Icons.Outlined.Add, null)
                        Text("New list", modifier = Modifier.padding(start = 8.dp))
                    }
                    // A spreadsheet saved from a list here, or one laid out the same way.
                    OutlinedButton(onClick = { import.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain", "application/octet-stream")) }, modifier = Modifier.weight(1f).height(56.dp)) {
                        Icon(Icons.Outlined.FileOpen, null)
                        Text("Import", modifier = Modifier.padding(start = 8.dp))
                    }
                }
                importNote?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp, start = 4.dp)) }
            }
        }
    }
    if (creating) NameDialog("New list", "", "Create", onDismiss = { creating = false }) { store.createList(it); creating = false }
}

@Composable
private fun ShelfCard(icon: ImageVector, name: String, count: Int, cover: Listing?, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surfaceContainer).clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The newest listing's photo stands in for the list; an icon when the list is empty.
        Box(Modifier.size(64.dp).clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
            val image = cover?.imageIds?.firstOrNull()
            if (image == null) Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            else AsyncImage(model = Images.url(image, Images.THUMB), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Column(Modifier.weight(1f).padding(start = 16.dp)) {
            Text(name, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(if (count == 1) "1 listing" else "$count listings", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun NameDialog(title: String, initial: String, confirm: String, onDismiss: () -> Unit, onDone: (String) -> Unit) {
    // Opens with the old name selected and the keyboard up, so typing replaces it outright.
    var name by remember { mutableStateOf(TextFieldValue(initial, TextRange(0, initial.length))) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.copy(text = it.text.take(60)) },
                singleLine = true,
                label = { Text("Name") },
                modifier = Modifier.focusRequester(focus),
            )
        },
        confirmButton = { TextButton(enabled = name.text.isNotBlank(), onClick = { onDone(name.text) }) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** One collection, with what can be done to it: share it, save it as a spreadsheet, rename, delete. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShelfScreen(shelf: Shelf, store: Store, onBack: () -> Unit, onOpen: (Listing) -> Unit) {
    val context = LocalContext.current
    val favorites by store.favorites.collectAsStateWithLifecycle()
    val noted by store.noted.collectAsStateWithLifecycle()
    val lists by store.lists.collectAsStateWithLifecycle()
    val notes by store.notes.collectAsStateWithLifecycle()
    val favoriteIds = remember(favorites) { favorites.map { it.postingId }.toHashSet() }
    val custom = (shelf as? Shelf.Custom)?.let { c -> lists.firstOrNull { it.id == c.id } }
    val name = when (shelf) {
        Shelf.Favorites -> "Favorites"
        Shelf.Noted -> "With notes"
        is Shelf.Custom -> custom?.name ?: ""
    }
    val items = when (shelf) {
        Shelf.Favorites -> favorites
        Shelf.Noted -> noted
        is Shelf.Custom -> custom?.items.orEmpty()
    }
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var moving by remember { mutableStateOf<Listing?>(null) }
    moving?.let { l -> ListPicker(l, store, onDismiss = { moving = null }) }
    val saveCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(Export.csv(items, notes).toByteArray()) } }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
                actions = {
                    IconButton(enabled = items.isNotEmpty(), onClick = {
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_SUBJECT, name).putExtra(Intent.EXTRA_TEXT, Export.text(name, items, notes)), null))
                    }) { Icon(Icons.Outlined.Share, "Share this list") }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "More") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Save as spreadsheet (CSV)") }, enabled = items.isNotEmpty(), onClick = { menu = false; saveCsv.launch(Export.fileName(name)) })
                            if (custom != null) {
                                DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; renaming = true })
                                DropdownMenuItem(text = { Text("Delete list") }, onClick = { menu = false; deleting = true })
                            }
                        }
                    }
                },
            )
        },
    ) { pad ->
        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(pad).padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    when (shelf) {
                        Shelf.Favorites -> "Tap the heart on a listing to keep it here."
                        Shelf.Noted -> "Write a note on any listing and it shows up here, hearted or not."
                        is Shelf.Custom -> "Open a listing and tap the list button at the top to add it here."
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                )
            }
        } else LazyColumn(contentPadding = pad) {
            item {
                Text(
                    "Press and hold a listing to put it in other lists or take it out of this one.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
            }
            items(items, key = { it.postingId }) { l ->
                ListingRow(
                    l, favorite = l.postingId in favoriteIds, onClick = { onOpen(l) }, onFavorite = { store.toggleFavorite(l) },
                    note = notes[l.postingId], modifier = Modifier.animateItem(),
                    // Press and hold to choose which lists it belongs in: move it, copy it, or take it out.
                    onLongClick = { moving = l },
                )
            }
        }
    }
    if (renaming && custom != null) NameDialog("Rename list", custom.name, "Rename", onDismiss = { renaming = false }) { store.renameList(custom.id, it); renaming = false }
    if (deleting && custom != null) AlertDialog(
        onDismissRequest = { deleting = false },
        title = { Text("Delete \"${custom.name}\"?") },
        text = { Text("The list goes away. The listings in it are not touched on craigslist.") },
        confirmButton = { TextButton(onClick = { deleting = false; store.deleteList(custom.id); onBack() }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { deleting = false }) { Text("Cancel") } },
    )
}

@Composable
fun SavedScreen(store: Store, onBack: () -> Unit, onOpen: (SearchQuery) -> Unit) {
    val saved by store.saved.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    var renaming by remember { mutableStateOf<SavedSearch?>(null) }
    renaming?.let { s ->
        NameDialog("Name this search", s.name ?: s.title, "Save", onDismiss = { renaming = null }) { store.renameSaved(s.id, it); renaming = null }
    }
    ListScaffold("Saved searches", onBack, if (saved.isEmpty()) "Tap the bookmark on any search to save it and hear about new listings." else null) { pad ->
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = pad.calculateTopPadding(), bottom = pad.calculateBottomPadding() + 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (saved.any { it.alerts }) item { AlertsNote() }
            items(saved, key = { it.id }) { s ->
                SavedRow(
                    s,
                    onOpen = { onOpen(s.query) },
                    onAlerts = {
                        store.updateSaved(s.id) { it.copy(alerts = !it.alerts) }
                        Alerts.schedule(context)
                        // Turning the bell on is the moment a notification first makes sense.
                        if (!s.alerts && Build.VERSION.SDK_INT >= 33) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                    },
                    onDelete = { store.removeSaved(s.id); Alerts.schedule(context) },
                    onRename = { renaming = s },
                )
            }
        }
    }
}

/** "Cars & trucks · SF bay area" over a line listing what the search narrows by. */
@Composable
private fun SavedRow(s: SavedSearch, onOpen: () -> Unit, onAlerts: () -> Unit, onDelete: () -> Unit, onRename: () -> Unit) {
    val q = s.query
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surfaceContainer).clickable(onClick = onOpen).padding(start = 20.dp, end = 8.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(s.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (s.unseen > 0) Badge(Modifier.padding(start = 8.dp)) { Text("${s.unseen} new") }
            }
            val detail = listOfNotNull(
                q.text.takeIf { it.isNotEmpty() && s.title != it },
                q.categoryName.takeIf { s.title != it },
                q.areaName,
                q.filterCount.takeIf { it > 0 }?.let { if (it == 1) "1 filter" else "$it filters" },
            ).joinToString(" · ")
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onRename) { Icon(Icons.Outlined.Edit, "Rename saved search") }
        IconButton(onClick = onAlerts) {
            Icon(if (s.alerts) Icons.Outlined.Notifications else Icons.Outlined.NotificationsOff, if (s.alerts) "Turn alerts off" else "Turn alerts on")
        }
        IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, "Delete saved search") }
    }
}

/**
 * The honest small print about alerts, the same caveat any alarm or reminder app without a push
 * service has to give: the phone decides when background work runs, and battery optimization lets
 * it put that off for hours.
 */
@Composable
private fun AlertsNote() {
    val context = LocalContext.current
    val power = remember { context.getSystemService(PowerManager::class.java) }
    // Read again every time the screen comes back, so returning from system settings updates it.
    var unrestricted by remember { mutableStateOf(power?.isIgnoringBatteryOptimizations(context.packageName) == true) }
    LifecycleResumeEffect(Unit) {
        unrestricted = power?.isIgnoringBatteryOptimizations(context.packageName) == true
        onPauseOrDispose {}
    }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.secondaryContainer).padding(20.dp)) {
        Text("About alerts", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
        Text(
            "Corkboard checks saved searches itself, about every three hours, with no push service. " +
                if (unrestricted) "Battery use is unrestricted, so checks should run on time. Android can still hold them back while the phone sits idle or battery saver is on."
                else "Android may delay or skip the checks to save battery, sometimes for many hours. For alerts you can rely on, set battery use for Corkboard to Unrestricted. They can still be late while the phone sits idle or battery saver is on.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(top = 6.dp),
        )
        if (!unrestricted) FilledTonalButton(
            onClick = {
                // The app's own page in system settings, where "App battery usage" lives on every Android.
                runCatching { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))) }
            },
            modifier = Modifier.padding(top = 12.dp),
        ) { Text("Open battery settings") }
    }
}
