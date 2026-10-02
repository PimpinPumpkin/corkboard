package app.corkboard.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.corkboard.BuildConfig
import app.corkboard.data.ClUrls
import app.corkboard.data.Store
import app.corkboard.data.ThemeMode
import app.corkboard.net.Http

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(store: Store, http: Http, onBack: () -> Unit) {
    val theme by store.theme.collectAsStateWithLifecycle()
    var cleared by remember { mutableStateOf(false) }
    val context = LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Settings") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } })
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState())) {
            Heading("Theme")
            listOf(ThemeMode.System to "Follow the system", ThemeMode.Light to "Light", ThemeMode.Dark to "Dark").forEach { (mode, label) ->
                Row(Modifier.fillMaxWidth().clickable { store.setTheme(mode) }.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = theme == mode, onClick = { store.setTheme(mode) })
                    Text(label, style = MaterialTheme.typography.bodyLarge)
                }
            }

            Heading("Photos")
            val fitResults by store.fitResults.collectAsStateWithLifecycle()
            val fitListing by store.fitListing.collectAsStateWithLifecycle()
            Toggle("Whole photos in search results", "Show each photo in full instead of cropping it to a square.", fitResults) { store.setFitResults(it) }
            Spacer(Modifier.height(10.dp))
            Toggle("Whole photos on a listing", "Show each photo in full in the listing's gallery instead of cropping it to fill.", fitListing) { store.setFitListing(it) }

            Heading("Map")
            val showMap by store.showMap.collectAsStateWithLifecycle()
            val mapBelow by store.mapBelow.collectAsStateWithLifecycle()
            Toggle("Map on a listing", "Show a small map of roughly where the listing is. Off, no map is loaded at all.", showMap) { store.setShowMap(it) }
            if (showMap) {
                Spacer(Modifier.height(10.dp))
                Toggle("Map below the description", "Turn off to put the map before the listing's text instead of after it.", mapBelow) { store.setMapBelow(it) }
            }

            Heading("Listings language")
            val language by store.language.collectAsStateWithLifecycle()
            var pickLanguage by remember { mutableStateOf(false) }
            Item(
                ClUrls.languages.firstOrNull { it.first == language }?.second ?: "Follow the phone",
                "craigslist translates filters, labels and categories into these languages. The listings themselves stay as their authors wrote them.",
            ) { pickLanguage = true }
            if (pickLanguage) {
                AlertDialog(
                    onDismissRequest = { pickLanguage = false },
                    title = { Text("Listings language") },
                    text = {
                        Column(Modifier.verticalScroll(rememberScrollState())) {
                            (listOf<Pair<String?, String>>(null to "Follow the phone") + ClUrls.languages).forEach { (code, name) ->
                                Row(Modifier.fillMaxWidth().clickable { store.setLanguage(code); pickLanguage = false }, verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(selected = language == code, onClick = null, modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp))
                                    Text(name, style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                        }
                    },
                    confirmButton = { TextButton(onClick = { pickLanguage = false }) { Text("Cancel") } },
                )
            }

            Heading("Hidden listings")
            val hidden by store.hidden.collectAsStateWithLifecycle()
            Item(
                if (hidden.isEmpty()) "Nothing hidden" else if (hidden.size == 1) "Show 1 hidden listing again" else "Show ${hidden.size} hidden listings again",
                "Press and hold a listing and choose Hide to keep it out of every search.",
            ) { store.unhideAll() }

            Heading("Backup")
            var backupNote by remember { mutableStateOf<String?>(null) }
            var restoring by remember { mutableStateOf<android.net.Uri?>(null) }
            val scope = rememberCoroutineScope()
            val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
                if (uri != null) scope.launch {
                    val ok = withContext(Dispatchers.IO) { runCatching { context.contentResolver.openOutputStream(uri)!!.use { store.backupTo(it) } }.isSuccess }
                    backupNote = if (ok) "Backup saved." else "The backup could not be written."
                }
            }
            val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> restoring = uri }
            Item(
                "Save a backup",
                backupNote ?: "One file with your favorites, lists, notes, hidden listings, saved searches, settings and saved copies of listings.",
            ) { save.launch("corkboard-backup-${java.time.LocalDate.now()}.zip") }
            Spacer(Modifier.height(10.dp))
            Item("Restore from a backup", "Add a backup's contents to what is here, or replace everything with it.") { open.launch(arrayOf("application/zip", "application/octet-stream")) }
            restoring?.let { uri ->
                fun apply(merge: Boolean) {
                    restoring = null
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) {
                            runCatching { context.contentResolver.openInputStream(uri)!!.use { if (merge) store.mergeFrom(it) else store.restoreFrom(it) } }.getOrDefault(false)
                        }
                        if (!ok) backupNote = "That file is not a Corkboard backup. Nothing was changed."
                        else {
                            // What is in memory no longer matches the disk: start over from it.
                            context.packageManager.getLaunchIntentForPackage(context.packageName)?.component?.let { context.startActivity(Intent.makeRestartActivityTask(it)) }
                            Runtime.getRuntime().exit(0)
                        }
                    }
                }
                AlertDialog(
                    onDismissRequest = { restoring = null },
                    title = { Text("Restore this backup?") },
                    text = {
                        Text(
                            "Add to what is here keeps everything in the app and adds the backup's favorites, lists, notes, hidden listings and saved searches to it.\n\n" +
                                "Replace everything swaps what is in the app for what is in the file, settings included.\n\nEither way the app restarts.",
                        )
                    },
                    confirmButton = {
                        Column(horizontalAlignment = Alignment.End) {
                            TextButton(onClick = { apply(merge = true) }) { Text("Add to what is here") }
                            TextButton(onClick = { apply(merge = false) }) { Text("Replace everything") }
                            TextButton(onClick = { restoring = null }) { Text("Cancel") }
                        }
                    },
                )
            }

            Heading("Privacy")
            Item(
                if (cleared) "Site cookie cleared" else "Clear the site cookie",
                "craigslist gives every browser one cookie. Clearing it makes the app a new visitor.",
            ) { http.cookies.clear(); cleared = true }

            // CI writes the latest changes into the APK; a local build has none and shows nothing.
            val whatsNew = remember { runCatching { context.assets.open("whatsnew.txt").bufferedReader().readText().trim() }.getOrDefault("") }
            if (whatsNew.isNotEmpty()) {
                Heading("What's new")
                Text(whatsNew, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 20.dp))
            }

            Heading("About")
            Item(
                "Corkboard ${BuildConfig.VERSION_NAME}",
                "An independent client for craigslist. Not affiliated with or endorsed by craigslist. " +
                    "It talks to craigslist directly from this phone; there is no Corkboard server and nothing is collected. " +
                    "Free software under the GNU GPL v3. Includes Cronet from the Chromium project (BSD license), " +
                    "the Google Sans Flex typeface (SIL Open Font License), " +
                    "Coil, AndroidX and Kotlin libraries (Apache 2.0).",
            ) {}
        }
    }
}

@Composable
private fun Heading(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 10.dp))
}

@Composable
private fun Item(title: String, detail: String, onClick: () -> Unit) {
    Column(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Toggle(title: String, detail: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable { onChange(!on) }.padding(start = 20.dp, end = 16.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = on, onCheckedChange = onChange)
    }
}
