package app.corkboard.data

import app.corkboard.net.BrowserHeaders
import app.corkboard.net.Http
import java.net.URLEncoder

/** Builds the URLs. Kept apart from the network so the shapes can be tested on a plain JVM. */
object ClUrls {
    private const val API = "https://%s.craigslist.org/web/v8/"
    /** The languages the site itself is translated into. Anything else gets English. */
    val languages: List<Pair<String, String>> = listOf(
        "en" to "English", "es" to "Español", "fr" to "Français", "de" to "Deutsch", "it" to "Italiano",
        "pt" to "Português", "da" to "Dansk", "fi" to "Suomi", "sv" to "Svenska", "tr" to "Türkçe",
        "vi" to "Tiếng Việt", "ru" to "Русский", "zh" to "中文", "ja" to "日本語", "ko" to "한국말",
    )

    /**
     * The visitor's country and language, sent with every request the way the site's page sends
     * them. The site answers in that language: filter names, attribute labels and prices all come
     * back translated, so the app has nothing to translate for them.
     */
    @Volatile var country: String = "US"
    @Volatile var language: String = "en"

    fun setLocale(country: String, language: String) {
        this.country = country.uppercase().takeIf { it.length == 2 && it.all(Char::isLetter) } ?: "US"
        this.language = language.lowercase().takeIf { l -> languages.any { it.first == l } } ?: "en"
    }

    private val LOCALE: String get() = "cc=$country&lang=$language"

    /** The site's internal number for each sort order, sent inside the `batch` parameter. */
    private val sortIds = mapOf("date" to 1, "dateoldest" to 2, "dist" to 3, "priceasc" to 4, "pricedsc" to 5, "rel" to 6, "upcoming" to 7)

    fun sortId(sort: String?): Int = sortIds[sort] ?: 0

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    const val AREAS = "https://reference.craigslist.org/Areas"

    /**
     * A search. [batch] is `0-<cacheTs or 0>-<how many rows to send with details>-<sort>-0`: the
     * leading zero says the request comes from the www site, where the area travels in the path.
     */
    fun search(q: SearchQuery, sortId: Int, cacheTs: Long = 0, detailed: Int = QUICK): String {
        val path = if (q.subarea != null) "subarea/${q.subarea}" else "area/${q.areaHost}"
        val params = (q.params + ("cat" to listOf(q.category))).toSortedMap()
        val extra = params.entries.joinToString("") { (k, vs) -> vs.filter { it.isNotEmpty() }.joinToString("") { "&${enc(k)}=${enc(it)}" } }
        return API.format("sapi") + "postings/search/full?batch=0-$cacheTs-$detailed-$sortId-0&$LOCALE&searchPath=${enc(path)}$extra"
    }

    /** Titles and images for [size] rows of the full result list, starting at row [start]. */
    fun batch(page: SearchPage, sortId: Int, start: Int, size: Int = CHUNK): String =
        API.format("sapi") + "postings/search/batch?batch=0-$start-$size-$sortId-${page.bundleDups}-${page.maxPostedTs ?: 0}-${page.cacheTs}" +
            "&cacheId=${enc(page.cacheId.orEmpty())}&$LOCALE"

    fun posting(uuid: String): String = API.format("rapi") + "postings/${enc(uuid)}?$LOCALE"

    fun locate(lat: Double, lon: Double): String = API.format("rapi") + "locations?$LOCALE&lat=$lat&lon=$lon"

    /** The front door: it answers with a redirect to the site nearest the visitor's network address. */
    const val FRONT_DOOR = "https://geo.craigslist.org/"

    /** "sfbay" out of https://www.craigslist.org/area/sfbay or https://sfbay.craigslist.org/. */
    fun hostOf(siteUrl: String): String? =
        Regex("""craigslist\.org/area/([a-z0-9]+)""").find(siteUrl)?.groupValues?.get(1)
            ?: Regex("""//([a-z0-9]+)\.craigslist\.org""").find(siteUrl)?.groupValues?.get(1)?.takeIf { it != "www" && it != "geo" }

    fun suggest(type: String, text: String): String = API.format("sapi") + "suggest/${enc(type)}?$LOCALE&query=${enc(text)}"

    /** The listing's page on the site, for the browser: replying and flagging happen there. */
    fun web(uuid: String, slug: String?): String =
        if (slug.isNullOrEmpty()) "${BrowserHeaders.ORIGIN}/view/$uuid" else "${BrowserHeaders.ORIGIN}/view/d/$slug/$uuid"

    /** The same two sizes the site's own page asks for. */
    const val QUICK = 360
    const val CHUNK = 1080
}

class ClApi(private val http: Http) {
    private suspend fun get(url: String): String {
        val r = http.get(url, BrowserHeaders.Kind.Api)
        // Errors arrive as JSON with a message worth showing; anything else is just a status.
        if (!r.ok && r.contentType?.contains("json") != true) throw ApiException(if (r.code == 403) "craigslist is refusing requests right now. Try again later." else "craigslist answered ${r.code}")
        return r.text()
    }

    suspend fun areas(): List<Area> = Parsers.areas(get(ClUrls.AREAS))
    suspend fun search(q: SearchQuery, sortId: Int, cacheTs: Long = 0, detailed: Int = ClUrls.QUICK): SearchPage =
        Parsers.search(get(ClUrls.search(q, sortId, cacheTs, detailed)))

    suspend fun batch(page: SearchPage, sortId: Int, start: Int): Map<Long, ListingDetails> =
        Parsers.batch(get(ClUrls.batch(page, sortId, start)), page.minPostingId)

    /** The site and postal code for a point. Coordinates are rounded first: a town, not a doorstep. */
    suspend fun locate(lat: Double, lon: Double): Located? =
        Parsers.located(get(ClUrls.locate(Math.round(lat * 100) / 100.0, Math.round(lon * 100) / 100.0)))

    /**
     * Which site a postal code belongs to. The only thing that resolves one is a search, so this
     * runs the lightest search there is and reads where the site says it looked.
     */
    suspend fun locatePostal(postal: String, anyHost: String): Located? {
        val probe = SearchQuery(areaHost = anyHost, areaName = "", category = "zip", categoryName = "").near(postal, "10")
        return Parsers.searchedPlace(get(ClUrls.search(probe, 0)))?.takeIf { it.postal.isNotEmpty() }
    }

    /** The hostname of the site craigslist itself would send this visitor to, or null. */
    suspend fun nearestHost(): String? = http.redirectTarget(ClUrls.FRONT_DOOR)?.let(ClUrls::hostOf)

    suspend fun posting(uuid: String): Posting = Parsers.posting(get(ClUrls.posting(uuid)))
    suspend fun suggest(type: String, text: String): List<String> = Parsers.suggestions(get(ClUrls.suggest(type, text)))
}
