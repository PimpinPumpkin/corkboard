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

    private val _near = MutableStateFlow(read("near.json", Near.serializer()))
    val near: StateFlow<Near?> = _near.asStateFlow()

    /** Picks the site to search and, optionally, a postal code and distance to narrow every search to. */
    fun setArea(area: Area, near: Near? = null) {
        write("area.json", Area.serializer(), area)
        if (near != null) write("near.json", Near.serializer(), near) else File(dir, "near.json").delete()
        _area.value = area
        _near.value = near
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

    /** The language asked of the site: a code from [ClUrls.languages], or null to follow the phone. */
    private val _language = MutableStateFlow(prefs.getString("language", null))
    val language: StateFlow<String?> = _language.asStateFlow()

    fun setLanguage(code: String?) {
        prefs.edit().putString("language", code).apply()
        _language.value = code
        applyLocale()
    }

    /** Tells the API layer who is asking: the phone's country, and the chosen or the phone's language. */
    fun applyLocale() {
        val phone = java.util.Locale.getDefault()
        ClUrls.setLocale(phone.country, _language.value ?: phone.language)
    }

    // ---- pinned categories ----

    private val _pinned = MutableStateFlow(prefs.getString("pinned", null)?.split(',')?.filter { it.isNotEmpty() } ?: Catalog.featured.map { it.abbr })

    /** The categories shown as large tiles on the home screen, in the order they were pinned. */
    val pinned: StateFlow<List<String>> = _pinned.asStateFlow()

    fun togglePinned(abbr: String) {
        val next = if (abbr in _pinned.value) _pinned.value - abbr else _pinned.value + abbr
        prefs.edit().putString("pinned", next.joinToString(",")).apply()
        _pinned.value = next
    }

    // ---- other countries ----

    /** On by default: someone who picked a site in one country is rarely shopping in the next one over. */
    private val _homeCountryOnly = MutableStateFlow(prefs.getBoolean("homeCountryOnly", true))
    val homeCountryOnly: StateFlow<Boolean> = _homeCountryOnly.asStateFlow()

    fun setHomeCountryOnly(on: Boolean) {
        prefs.edit().putBoolean("homeCountryOnly", on).apply()
        _homeCountryOnly.value = on
    }

    private var countries: Map<Int, String> = emptyMap()

    /**
     * A test for "is this listing in the same country as the site being searched", or null when
     * the gate is off or the country cannot be told. A search by distance near a border returns
     * mostly the other side, in the other side's currency and odometer units.
     */
    fun countryGate(query: SearchQuery): ((Listing) -> Boolean)? {
        if (!_homeCountryOnly.value) return null
        if (countries.isEmpty()) countries = cachedAreas().associate { it.id to it.country }
        val home = cachedAreas().firstOrNull { it.hostname == query.areaHost }?.country?.takeIf { it.isNotEmpty() } ?: return null
        val known = countries
        // A listing whose site is unknown is kept: better one stray than a missing result.
        return { l -> known[l.areaId].let { it == null || it == home } }
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

    /** Called whenever a listing is hearted, listed or noted, so its text and photos can be archived. */
    var onKept: ((Listing) -> Unit)? = null

    fun toggleFavorite(listing: Listing) {
        val now = _favorites.value
        val adding = now.none { it.postingId == listing.postingId }
        val next = if (adding) listOf(listing) + now else now.filter { it.postingId != listing.postingId }
        write("favorites.json", ListSerializer(Listing.serializer()), next)
        _favorites.value = next
        if (adding) onKept?.invoke(listing)
    }

    // ---- the user's own lists ----

    private val _lists = MutableStateFlow(read("lists.json", ListSerializer(UserList.serializer())).orEmpty())
    val lists: StateFlow<List<UserList>> = _lists.asStateFlow()

    private fun setLists(next: List<UserList>) {
        write("lists.json", ListSerializer(UserList.serializer()), next)
        _lists.value = next
    }

    fun createList(name: String): UserList {
        val list = UserList(System.currentTimeMillis(), name.trim().ifEmpty { "New list" })
        setLists(_lists.value + list)
        return list
    }

    fun renameList(id: Long, name: String) = setLists(_lists.value.map { if (it.id == id && name.isNotBlank()) it.copy(name = name.trim()) else it })
    fun deleteList(id: Long) = setLists(_lists.value.filter { it.id != id })

    /** Adds [listing] to the list, or takes it out if it is already there. */
    fun toggleInList(id: Long, listing: Listing) {
        var added = false
        setLists(_lists.value.map { l ->
            if (l.id != id) l
            else if (l.items.any { it.postingId == listing.postingId }) l.copy(items = l.items.filter { it.postingId != listing.postingId })
            else l.copy(items = listOf(listing) + l.items).also { added = true }
        })
        if (added) onKept?.invoke(listing)
    }

    /** Every listing the user chose to keep, by any means: the ones whose photos stay archived. */
    fun keptUuids(): Set<String> =
        (_favorites.value + _noted.value + _lists.value.flatMap { it.items }).mapNotNull { it.uuid }.toSet()

    /**
     * A seller who posts the same thing again gets a new listing that names the old one. Whatever
     * the user attached to the old one (a heart, a note, a place in a list) moves to the new one.
     */
    fun carryOver(oldId: Long, new: Listing) {
        if (oldId == new.postingId) return
        if (_favorites.value.any { it.postingId == oldId }) {
            val next = _favorites.value.filter { it.postingId != new.postingId }.map { if (it.postingId == oldId) new else it }
            write("favorites.json", ListSerializer(Listing.serializer()), next)
            _favorites.value = next
        }
        if (_lists.value.any { l -> l.items.any { it.postingId == oldId } }) {
            setLists(_lists.value.map { l -> l.copy(items = l.items.filter { it.postingId != new.postingId }.map { if (it.postingId == oldId) new else it }) })
        }
        val note = _notes.value[oldId]
        if (note != null) {
            if (_notes.value[new.postingId] == null) setNote(new, note)
            setNote(Listing(postingId = oldId, postedAt = 0, categoryId = 0), "")
        }
        if (oldId in _hidden.value) setHidden(new.postingId, true)
    }

    // ---- notes ----

    private val noteSerializer = kotlinx.serialization.builtins.MapSerializer(kotlinx.serialization.serializer<Long>(), kotlinx.serialization.serializer<String>())
    private val _notes = MutableStateFlow(read("notes.json", noteSerializer).orEmpty())

    /** The user's own words about a listing, by posting id. Kept on the phone like everything else. */
    val notes: StateFlow<Map<Long, String>> = _notes.asStateFlow()

    private val _noted = MutableStateFlow(read("noted.json", ListSerializer(Listing.serializer())).orEmpty())

    /** The listings that have a note, newest note first, whether or not they are favorites too. */
    val noted: StateFlow<List<Listing>> = _noted.asStateFlow()

    /** Writes or, with blank text, removes the note on [listing], and keeps the listing to find it by. */
    fun setNote(listing: Listing, text: String) {
        val id = listing.postingId
        val next = if (text.isBlank()) _notes.value - id else _notes.value + (id to text)
        write("notes.json", noteSerializer, next)
        _notes.value = next
        val others = _noted.value.filter { it.postingId != id }
        val listings = if (text.isBlank()) others else listOf(listing) + others
        write("noted.json", ListSerializer(Listing.serializer()), listings)
        _noted.value = listings
        if (text.isNotBlank()) onKept?.invoke(listing)
    }

    fun unhideAll() {
        write("hidden.json", ListSerializer(kotlinx.serialization.serializer<Long>()), emptyList())
        _hidden.value = emptySet()
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
