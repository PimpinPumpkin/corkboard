package app.corkboard

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.ui.unit.IntOffset
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
import android.util.Log
import app.corkboard.data.Calibration
import app.corkboard.data.ClUrls
import app.corkboard.data.SelfCheck
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import app.corkboard.data.Listing
import app.corkboard.data.SearchQuery
import app.corkboard.ui.ResultsState
import app.corkboard.ui.screens.AreaScreen
import app.corkboard.ui.screens.ListsScreen
import app.corkboard.ui.screens.Shelf
import app.corkboard.ui.screens.ShelfScreen
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
    class ShelfOf(val shelf: Shelf) : Screen
}

class MainActivity : ComponentActivity() {
    /** Set when a new-listings notification was tapped; the UI opens the saved searches and clears it. */
    private val openSaved = mutableStateOf(false)

    /** A listing to open, from a craigslist link tapped or shared into the app. */
    private val openListing = mutableStateOf<Listing?>(null)

    private fun linked(intent: Intent): Listing? =
        (intent.dataString ?: intent.getStringExtra(Intent.EXTRA_TEXT))?.let(ClUrls::listingFromLink)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        linked(intent)?.let { openListing.value = it }
        if (intent.getBooleanExtra(Alerts.EXTRA_OPEN_SAVED, false)) openSaved.value = true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as CorkboardApp
        if (savedInstanceState == null && intent.getBooleanExtra(Alerts.EXTRA_OPEN_SAVED, false)) openSaved.value = true
        if (savedInstanceState == null) linked(intent)?.let { openListing.value = it }
        // Work survives reboots by itself; this covers an app update or a cleared schedule.
        Alerts.schedule(this)
        // Debug builds take `--ez self_check true`: the app tests itself against the live site and
        // prints one RESULT line to logcat, which is what the project's automation reads.
        if (BuildConfig.DEBUG && intent.getBooleanExtra("self_check", false)) {
            CoroutineScope(Dispatchers.IO).launch {
                val result = SelfCheck.run(app.api, app.http) { Log.i("CorkboardSelfCheck", it) }
                Log.i("CorkboardSelfCheck", "Cronet ${BuildConfig.CRONET_VERSION}, calibration ${Calibration.current.version}")
                Log.i(
                    "CorkboardSelfCheck",
                    when (result) {
                        SelfCheck.Result.Pass -> "RESULT PASS"
                        is SelfCheck.Result.Blocked -> "RESULT BLOCKED ${result.why}"
                        is SelfCheck.Result.Fail -> "RESULT FAIL at ${result.step}: ${result.why}"
                    },
                )
            }
        }
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
                    LaunchedEffect(openListing.value) {
                        openListing.value?.let { stack += Screen.Posting(it) }
                        openListing.value = null
                    }
                    LaunchedEffect(openSaved.value) {
                        if (openSaved.value) {
                            openSaved.value = false
                            if (stack.last() != Screen.Saved) stack += Screen.Saved
                        }
                    }

                    // Nothing can be searched without an area, so the first launch starts there.
                    val current = if (area == null) Screen.Areas else stack.last()
                    // Going deeper slides the new screen in from the right; going back, from the left.
                    AnimatedContent(
                        targetState = stack.size to current,
                        transitionSpec = {
                            val forward = targetState.first >= initialState.first
                            val motion = spring<IntOffset>(dampingRatio = 0.9f, stiffness = 420f)
                            (slideInHorizontally(motion) { w -> if (forward) w / 6 else -w / 6 } + fadeIn(tween(180))) togetherWith
                                (slideOutHorizontally(motion) { w -> if (forward) -w / 6 else w / 6 } + fadeOut(tween(120)))
                        },
                        contentKey = { it.second },
                        label = "screen",
                    ) { (_, screen) ->
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
                            Screen.Favorites -> ListsScreen(app.store, onBack = ::pop, onOpen = { stack += Screen.ShelfOf(it) })
                            is Screen.ShelfOf -> ShelfScreen(screen.shelf, app.store, app.api, app.archive, onBack = ::pop, onOpen = { stack += Screen.Posting(it) })
                            Screen.Saved -> SavedScreen(app.store, onBack = ::pop, onOpen = ::open)
                            Screen.Settings -> SettingsScreen(app.store, app.http, onBack = ::pop)
                            is Screen.Results -> ResultsScreen(screen.state, app.api, app.store, onBack = ::pop, onOpen = { stack += Screen.Posting(it) })
                            is Screen.Posting -> PostingScreen(screen.listing, app.api, app.store, app.archive, onBack = ::pop)
                        }
                    }
                }
            }
        }
    }
}
