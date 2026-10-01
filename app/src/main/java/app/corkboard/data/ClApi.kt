package app.corkboard.data

import app.corkboard.net.BrowserHeaders
import app.corkboard.net.Http
import java.net.URLEncoder

/** Builds the URLs. Kept apart from the network so the shapes can be tested on a plain JVM. */
object ClUrls {
    private const val API = "https://%s.craigslist.org/web/v8/"
    private const val LOCALE = "cc=US&lang=en"

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

    suspend fun posting(uuid: String): Posting = Parsers.posting(get(ClUrls.posting(uuid)))
    suspend fun suggest(type: String, text: String): List<String> = Parsers.suggestions(get(ClUrls.suggest(type, text)))
}
