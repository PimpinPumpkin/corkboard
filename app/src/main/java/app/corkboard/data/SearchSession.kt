package app.corkboard.data

/**
 * One search, loaded the way the site's own page loads it:
 *
 *  1. a quick request returns the first 360 rows complete,
 *  2. if there are more, a second request returns every row (up to 10,000) as bare digests,
 *  3. titles and images for the digests arrive in batches of 1080 as the user scrolls into them.
 *
 * Following the same three steps keeps the app's traffic the shape the site expects.
 */
class SearchSession(private val api: ClApi, val query: SearchQuery, private val fresh: Boolean = false) {
    var page: SearchPage? = null
        private set
    private var full: SearchPage? = null
    private var rows: List<Listing> = emptyList()
    /** Posting ids in the full list's own order: batches are addressed by position in it. */
    private var fullIds: List<Long> = emptyList()
    private var nextChunk = 0
    private var sortId = ClUrls.sortId(query.sort)

    val total: Int get() = page?.total ?: 0

    /** The rows that can be shown: everything up to the first one still waiting for its details. */
    fun visible(): List<Listing> {
        val firstDigest = rows.indexOfFirst { !it.hasDetails }
        return if (firstDigest < 0) rows else rows.subList(0, firstDigest)
    }

    val hasMore: Boolean get() = visible().size < minOf(total, MAX_ROWS) && (full == null || rows.any { !it.hasDetails })

    suspend fun start(): SearchPage {
        val p = api.search(query, sortId, fresh = fresh)
        page = p
        rows = p.items
        sortId = ClUrls.sortId(p.sort)
        return p
    }

    /** Fetches the next stretch of results. Returns false when there was nothing left to fetch. */
    suspend fun loadMore(): Boolean {
        val first = page ?: return false
        if (!hasMore) return false
        val all = full ?: api.search(query, sortId, cacheTs = first.cacheTs, detailed = 0).also { f ->
            full = f
            fullIds = f.items.map { it.postingId }
            // The quick rows already have their details; carry them over into the full list.
            val known = rows.associateBy { it.postingId }
            rows = f.items.map { known[it.postingId] ?: it }
        }
        val from = nextChunk * ClUrls.CHUNK
        if (all.cacheId == null || from >= fullIds.size) {
            // Nothing more can be filled in: show what is complete and stop there.
            rows = rows.filter { it.hasDetails }
            return false
        }
        nextChunk++
        val details = api.batch(all, sortId, from)
        val inChunk = fullIds.subList(from, minOf(fullIds.size, from + ClUrls.CHUNK)).toHashSet()
        rows = rows.mapNotNull { l ->
            if (l.hasDetails || l.postingId !in inChunk) return@mapNotNull l
            val d = details[l.postingId]
            // A row the batch does not mention was deleted between the two requests.
            if (d?.uuid == null) null
            else l.copy(title = d.title, imageIds = d.imageIds, uuid = d.uuid, slug = d.slug, priceText = d.priceText ?: l.priceText, odometer = d.odometer, bedrooms = d.bedrooms, sqft = d.sqft)
        }
        return true
    }

    private companion object {
        const val MAX_ROWS = 10_000
    }
}
