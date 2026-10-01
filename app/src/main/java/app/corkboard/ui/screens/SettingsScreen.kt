package app.corkboard.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
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
                Row(Modifier.fillMaxWidth().clickable { store.setTheme(mode) }.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = theme == mode, onClick = { store.setTheme(mode) })
                    Text(label, style = MaterialTheme.typography.bodyLarge)
                }
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
                    "Coil, AndroidX and Kotlin libraries (Apache 2.0).",
            ) {}
        }
    }
}

@Composable
private fun Heading(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 8.dp))
}

@Composable
private fun Item(title: String, detail: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
