package app.corkboard.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.corkboard.data.Area
import app.corkboard.data.ClApi
import app.corkboard.data.Store
import kotlinx.coroutines.CancellationException

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AreaScreen(api: ClApi, store: Store, canGoBack: Boolean, onDone: () -> Unit) {
    var areas by remember { mutableStateOf(store.cachedAreas()) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var filter by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(attempt) {
        if (areas.isNotEmpty()) return@LaunchedEffect
        error = null
        try {
            areas = api.areas().also { store.cacheAreas(it) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message ?: "Could not load the list of areas"
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
                title = { Text("Choose your area") },
                navigationIcon = { if (canGoBack) IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
            )
        },
    ) { pad ->
        Column(Modifier.padding(top = pad.calculateTopPadding()).fillMaxSize()) {
            OutlinedTextField(
                value = filter,
                onValueChange = { filter = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                placeholder = { Text("City, region or state code") },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
            )
            when {
                error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(error.orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(onClick = { attempt++ }, modifier = Modifier.padding(top = 12.dp)) { Text("Try again") }
                    }
                }
                areas.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                else -> LazyColumn(contentPadding = PaddingValues(top = 8.dp, bottom = pad.calculateBottomPadding() + 16.dp)) {
                    items(shown, key = { it.id }) { area -> AreaRow(area) { store.setArea(area); onDone() } }
                }
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
