package app.corkboard.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.corkboard.data.Area
import app.corkboard.data.ClApi
import app.corkboard.data.DeviceLocation
import app.corkboard.data.Located
import app.corkboard.data.Near
import app.corkboard.data.Store
import app.corkboard.ui.components.SearchPill
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Where to look. Three ways in, quickest first: the phone's own location, a postal code, or the
 * list of every craigslist site. On the very first launch the site craigslist itself would pick
 * for this network address is used without asking, and can be changed from the home screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AreaScreen(api: ClApi, store: Store, canGoBack: Boolean, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var areas by remember { mutableStateOf(store.cachedAreas()) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var filter by rememberSaveable { mutableStateOf("") }
    val current = store.near.value
    var postal by rememberSaveable { mutableStateOf(current?.postal.orEmpty()) }
    var distance by rememberSaveable { mutableStateOf(current?.distance ?: "25") }
    var busy by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }
    // On first launch, hold the list back for a moment while the nearest site is looked up.
    var guessing by remember { mutableStateOf(!canGoBack) }

    /** Applies a resolved spot: its site, narrowed to a distance around its postal code. */
    fun use(found: Located?, failure: String) {
        val area = found?.let { f -> areas.firstOrNull { it.id == f.areaId } }
        if (found == null || area == null) {
            note = failure
            return
        }
        store.setArea(area, if (found.postal.isEmpty()) null else Near(found.postal, distance.ifBlank { "25" }, found.city))
        onDone()
    }

    LaunchedEffect(attempt) {
        error = null
        try {
            if (areas.isEmpty()) areas = api.areas().also { store.cacheAreas(it) }
            if (guessing) {
                val host = runCatching { api.nearestHost() }.getOrNull()
                areas.firstOrNull { it.hostname == host }?.let { store.setArea(it); onDone() }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message ?: "Could not load the list of areas"
        } finally {
            guessing = false
        }
    }

    val askLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) {
            note = "Location permission was not given. A postal code works too."
            return@rememberLauncherForActivityResult
        }
        busy = true
        note = null
        scope.launch {
            try {
                val fix = DeviceLocation.get(context)
                if (fix == null) note = "The phone could not tell where it is. Is location turned on?"
                else use(api.locate(fix.latitude, fix.longitude), "craigslist has no site for this location.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                note = e.message ?: "Could not look up this location"
            } finally {
                busy = false
            }
        }
    }

    val shown = remember(areas, filter) {
        val f = filter.trim()
        areas.asSequence()
            .filter { f.isEmpty() || it.name.contains(f, true) || it.region.equals(f, true) || it.hostname.contains(f, true) }
            // Names that start with what was typed first; then the United States before everywhere
            // else, each alphabetical by state and name.
            .sortedWith(compareBy<Area>({ f.isNotEmpty() && !it.name.startsWith(f, true) && !it.hostname.startsWith(f, true) }, { it.country != "US" }, { it.country }, { it.region }, { it.name }))
            .toList()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Where to look") },
                navigationIcon = { if (canGoBack) IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
            )
        },
    ) { pad ->
        when {
            error != null -> Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(error.orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = { attempt++ }, modifier = Modifier.padding(top = 12.dp)) { Text("Try again") }
                }
            }
            areas.isEmpty() || guessing -> Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            else -> LazyColumn(contentPadding = PaddingValues(top = pad.calculateTopPadding(), bottom = pad.calculateBottomPadding() + 16.dp)) {
                item {
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        FilledTonalButton(onClick = { askLocation.launch(Manifest.permission.ACCESS_COARSE_LOCATION) }, enabled = !busy, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                            if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Icon(Icons.Outlined.MyLocation, null, Modifier.size(18.dp))
                            Spacer(Modifier.size(8.dp))
                            Text("Use my location")
                        }
                        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = postal,
                                onValueChange = { v -> postal = v.filter { it.isLetterOrDigit() || it == ' ' || it == '-' }.take(10) },
                                modifier = Modifier.weight(1.4f),
                                label = { Text("Postal code") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                            )
                            OutlinedTextField(
                                value = distance,
                                onValueChange = { v -> distance = v.filter { it.isDigit() }.take(4) },
                                modifier = Modifier.weight(1f),
                                label = { Text("Within") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            )
                            Button(
                                enabled = postal.isNotBlank() && !busy,
                                onClick = {
                                    busy = true
                                    note = null
                                    scope.launch {
                                        try {
                                            use(api.locatePostal(postal.trim(), store.area.value?.hostname ?: areas.first().hostname), "craigslist does not know that postal code.")
                                        } catch (e: CancellationException) {
                                            throw e
                                        } catch (e: Exception) {
                                            note = e.message ?: "Could not look up that postal code"
                                        } finally {
                                            busy = false
                                        }
                                    }
                                },
                            ) { Text("Go") }
                        }
                        note?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp)) }
                        Text(
                            "Or pick a whole area",
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.padding(top = 28.dp, bottom = 10.dp, start = 4.dp),
                        )
                        SearchPill(value = filter, onValueChange = { filter = it }, placeholder = "City, region or state code", onSearch = {})
                    }
                }
                items(shown, key = { it.id }) { area -> AreaRow(area) { store.setArea(area); onDone() } }
            }
        }
    }
}

@Composable
private fun AreaRow(area: Area, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp)) {
        Text(area.name, style = MaterialTheme.typography.bodyLarge)
        val where = listOf(area.region, area.country).filter { it.isNotBlank() }.joinToString(", ")
        if (where.isNotEmpty()) Text(where, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
