package app.corkboard

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.corkboard.data.Listing
import app.corkboard.data.SearchQuery
import app.corkboard.ui.ResultsState
import app.corkboard.ui.screens.AreaScreen
import app.corkboard.ui.screens.FavoritesScreen
import app.corkboard.ui.screens.HomeScreen
import app.corkboard.ui.screens.PostingScreen
import app.corkboard.ui.screens.ResultsScreen
import app.corkboard.ui.screens.SavedScreen
import app.corkboard.ui.screens.SettingsScreen
import app.corkboard.ui.theme.CorkboardTheme
import app.corkboard.ui.theme.isDark
import app.corkboard.work.Alerts

/** Where the user is. A results screen owns its search so coming back from a listing keeps it. */
sealed interface Screen {
    data object Home : Screen
    data object Areas : Screen
    data object Favorites : Screen
    data object Saved : Screen
    data object Settings : Screen
    class Results(val state: ResultsState) : Screen
    class Posting(val listing: Listing) : Screen
}

class MainActivity : ComponentActivity() {
    /** Set when a new-listings notification was tapped; the UI opens the saved searches and clears it. */
    private val openSaved = mutableStateOf(false)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(Alerts.EXTRA_OPEN_SAVED, false)) openSaved.value = true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as CorkboardApp
        if (savedInstanceState == null && intent.getBooleanExtra(Alerts.EXTRA_OPEN_SAVED, false)) openSaved.value = true
        // Work survives reboots by itself; this covers an app update or a cleared schedule.
        Alerts.schedule(this)
        // Debug builds take `--ez check_alerts true` to run the saved-search check immediately.
        if (BuildConfig.DEBUG && intent.getBooleanExtra("check_alerts", false)) Alerts.runNow(this)
        // Debug builds take `--es theme light|dark` so screenshots never need a system setting changed.
        val forcedTheme = if (BuildConfig.DEBUG) intent.getStringExtra("theme") else null

        setContent {
            val mode by app.store.theme.collectAsStateWithLifecycle()
            val dark = when (forcedTheme) {
                "dark" -> true
                "light" -> false
                else -> isDark(mode)
            }
            DisposableEffect(dark) {
                val clear = Color.Transparent.toArgb()
                enableEdgeToEdge(
                    statusBarStyle = if (dark) SystemBarStyle.dark(clear) else SystemBarStyle.light(clear, clear),
                    navigationBarStyle = if (dark) SystemBarStyle.dark(clear) else SystemBarStyle.light(clear, clear),
                )
                onDispose {}
            }
            CorkboardTheme(dark) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                    val area by app.store.area.collectAsStateWithLifecycle()
                    val stack = remember { mutableStateListOf<Screen>(Screen.Home) }
                    fun pop() {
                        (stack.removeAt(stack.lastIndex) as? Screen.Results)?.state?.close()
                    }
                    fun open(query: SearchQuery) {
                        stack += Screen.Results(ResultsState(app.api, app.store, query))
                    }
                    BackHandler(enabled = stack.size > 1) { pop() }
                    LaunchedEffect(openSaved.value) {
                        if (openSaved.value) {
                            openSaved.value = false
                            if (stack.last() != Screen.Saved) stack += Screen.Saved
                        }
                    }

                    // Nothing can be searched without an area, so the first launch starts there.
                    val current = if (area == null) Screen.Areas else stack.last()
                    AnimatedContent(current, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "screen") { screen ->
                        when (screen) {
                            Screen.Home -> HomeScreen(
                                area = area,
                                store = app.store,
                                onSearch = ::open,
                                onPickArea = { stack += Screen.Areas },
                                onFavorites = { stack += Screen.Favorites },
                                onSaved = { stack += Screen.Saved },
                                onSettings = { stack += Screen.Settings },
                            )
                            Screen.Areas -> AreaScreen(
                                api = app.api,
                                store = app.store,
                                canGoBack = area != null,
                                onDone = { if (stack.last() == Screen.Areas) pop() },
                            )
                            Screen.Favorites -> FavoritesScreen(app.store, onBack = ::pop, onOpen = { stack += Screen.Posting(it) })
                            Screen.Saved -> SavedScreen(app.store, onBack = ::pop, onOpen = ::open)
                            Screen.Settings -> SettingsScreen(app.store, app.http, onBack = ::pop)
                            is Screen.Results -> ResultsScreen(screen.state, app.api, app.store, onBack = ::pop, onOpen = { stack += Screen.Posting(it) })
                            is Screen.Posting -> PostingScreen(screen.listing, app.api, app.store, onBack = ::pop)
                        }
                    }
                }
            }
        }
    }
}
