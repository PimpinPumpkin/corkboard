package app.corkboard.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class SavedSearch(
    val id: Long,
    val query: SearchQuery,
    val alerts: Boolean = true,
    /** The newest posting id already seen for this search; anything above it is new. */
    val newestSeen: Long = 0,
    val unseen: Int = 0,
)

enum class ThemeMode { System, Light, Dark }

/**
 * Everything the app remembers, all of it on the phone: the chosen area, favorites, saved
 * searches, hidden listings and the cached area list. Small JSON files, rewritten whole; none of
 * these lists grows past a few hundred entries.
 */
class Store(context: Context) {
    private val dir = File(context.filesDir, "store").apply { mkdirs() }
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    private fun <T> read(name: String, serializer: kotlinx.serialization.KSerializer<T>): T? =
        runCatching { json.decodeFromString(serializer, File(dir, name).readText()) }.getOrNull()

    private fun <T> write(name: String, serializer: kotlinx.serialization.KSerializer<T>, value: T) {
        val tmp = File(dir, "$name.tmp")
        tmp.writeText(json.encodeToString(serializer, value))
        tmp.renameTo(File(dir, name))
    }

    // ---- area ----

    private val _area = MutableStateFlow(read("area.json", Area.serializer()))
    val area: StateFlow<Area?> = _area.asStateFlow()

    fun setArea(area: Area) {
        write("area.json", Area.serializer(), area)
        _area.value = area
    }

    fun cachedAreas(): List<Area> = read("areas.json", ListSerializer(Area.serializer())).orEmpty()
    fun cacheAreas(areas: List<Area>) = write("areas.json", ListSerializer(Area.serializer()), areas)

    // ---- theme ----

    private val _theme = MutableStateFlow(runCatching { ThemeMode.valueOf(prefs.getString("theme", null) ?: "System") }.getOrDefault(ThemeMode.System))
    val theme: StateFlow<ThemeMode> = _theme.asStateFlow()

    fun setTheme(mode: ThemeMode) {
        prefs.edit().putString("theme", mode.name).apply()
        _theme.value = mode
    }

    private val _grid = MutableStateFlow(prefs.getBoolean("grid", true))
    val grid: StateFlow<Boolean> = _grid.asStateFlow()

    fun setGrid(grid: Boolean) {
        prefs.edit().putBoolean("grid", grid).apply()
        _grid.value = grid
    }

    // ---- favorites and hidden listings ----

    private val _favorites = MutableStateFlow(read("favorites.json", ListSerializer(Listing.serializer())).orEmpty())
    val favorites: StateFlow<List<Listing>> = _favorites.asStateFlow()

    fun toggleFavorite(listing: Listing) {
        val now = _favorites.value
        val next = if (now.any { it.postingId == listing.postingId }) now.filter { it.postingId != listing.postingId } else listOf(listing) + now
        write("favorites.json", ListSerializer(Listing.serializer()), next)
        _favorites.value = next
    }

    private val _hidden = MutableStateFlow(read("hidden.json", ListSerializer(kotlinx.serialization.serializer<Long>())).orEmpty().toSet())
    val hidden: StateFlow<Set<Long>> = _hidden.asStateFlow()

    fun setHidden(postingId: Long, hidden: Boolean) {
        // Old listings expire by themselves; keeping the newest thousand ids is plenty.
        val next = (if (hidden) _hidden.value + postingId else _hidden.value - postingId).sortedDescending().take(1000)
        write("hidden.json", ListSerializer(kotlinx.serialization.serializer<Long>()), next)
        _hidden.value = next.toSet()
    }

    // ---- saved searches ----

    private val _saved = MutableStateFlow(read("saved.json", ListSerializer(SavedSearch.serializer())).orEmpty())
    val saved: StateFlow<List<SavedSearch>> = _saved.asStateFlow()

    private fun setSaved(next: List<SavedSearch>) {
        write("saved.json", ListSerializer(SavedSearch.serializer()), next)
        _saved.value = next
    }

    fun findSaved(query: SearchQuery): SavedSearch? = _saved.value.firstOrNull { it.query == query }

    fun save(query: SearchQuery, newestSeen: Long): SavedSearch {
        findSaved(query)?.let { return it }
        val s = SavedSearch(id = System.currentTimeMillis(), query = query, newestSeen = newestSeen)
        setSaved(listOf(s) + _saved.value)
        return s
    }

    fun removeSaved(id: Long) = setSaved(_saved.value.filter { it.id != id })

    fun updateSaved(id: Long, change: (SavedSearch) -> SavedSearch) = setSaved(_saved.value.map { if (it.id == id) change(it) else it })
}
