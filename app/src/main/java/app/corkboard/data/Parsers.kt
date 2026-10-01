package app.corkboard.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Readers for the JSON the site's own web app consumes. None of it is documented, so every reader
 * here is lenient: a field that is missing or has changed shape costs that field, never the page.
 * The one place this is checked against real responses is ParsersTest.
 */
object Parsers {
    private val json = Json { ignoreUnknownKeys = true }

    private fun JsonElement?.obj(): JsonObject? = this as? JsonObject
    private fun JsonElement?.arr(): JsonArray? = this as? JsonArray
    private fun JsonElement?.prim(): JsonPrimitive? = (this as? JsonPrimitive)?.takeIf { it !is JsonNull }
    private fun JsonElement?.str(): String? = prim()?.content
    /** A place name. The site writes a missing one as the number 0, which is not a name. */
    private fun JsonElement?.name(): String? = prim()?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }
    private fun JsonElement?.long(): Long? = prim()?.let { it.longOrNull ?: it.doubleOrNull?.toLong() }
    private fun JsonElement?.int(): Int? = prim()?.let { it.intOrNull ?: it.doubleOrNull?.toInt() }
    private fun JsonElement?.double(): Double? = prim()?.doubleOrNull

    /** The `data` object of an API envelope, or an [ApiException] carrying the site's own message. */
    private fun data(body: String): JsonObject {
        val root = runCatching { json.parseToJsonElement(body) }.getOrNull().obj() ?: throw ApiException("unreadable response")
        val error = root["errors"].arr()?.firstOrNull().obj()?.get("message").str()
        if (error != null) throw ApiException(error)
        return root["data"].obj() ?: throw ApiException("empty response")
    }

    fun areas(body: String): List<Area> =
        (runCatching { json.parseToJsonElement(body) }.getOrNull().arr() ?: throw ApiException("unreadable area list")).mapNotNull { e ->
            val o = e.obj() ?: return@mapNotNull null
            Area(
                id = o["AreaID"].int() ?: return@mapNotNull null,
                hostname = o["Hostname"].str() ?: return@mapNotNull null,
                name = o["Description"].str() ?: return@mapNotNull null,
                region = o["Region"].str().orEmpty(),
                country = o["Country"].str().orEmpty(),
                lat = o["Latitude"].double() ?: 0.0,
                lon = o["Longitude"].double() ?: 0.0,
                subAreas = o["SubAreas"].arr().orEmpty().mapNotNull { s ->
                    val so = s.obj() ?: return@mapNotNull null
                    SubArea(so["Abbreviation"].str() ?: return@mapNotNull null, so["ShortDescription"].str() ?: so["Description"].str().orEmpty())
                },
            )
        }

    /** The answer to "which site covers these coordinates": its id, the town, and the postal code there. */
    fun located(body: String): Located? {
        val o = data(body)["items"].arr()?.firstOrNull().obj() ?: return null
        return Located(o["areaId"].int() ?: return null, o["city"].name().orEmpty(), o["postal"].name().orEmpty())
    }

    /** The place a search says it ran for, which is how a postal code is turned into a site. */
    fun searchedPlace(body: String): Located? {
        val o = data(body)["location"].obj() ?: return null
        return Located(o["areaId"].int() ?: return null, o["city"].name().orEmpty(), o["postal"].name().orEmpty())
    }

    fun suggestions(body: String): List<String> = data(body)["items"].arr().orEmpty().mapNotNull { it.str() }

    // ---- search ----

    fun search(body: String): SearchPage {
        val d = data(body)
        val decode = d["decode"].obj()
        val minPostingId = decode?.get("minPostingId").long() ?: 0L
        val filters = d["filters"].arr().orEmpty()
        val sortFilter = filters.firstOrNull { it.obj()?.get("name").str() == "sort" }.obj()
        val subareaFilter = filters.firstOrNull { it.obj()?.get("name").str() == "subarea" }.obj()
        val loc = d["location"].obj()
        // The units belong to the area searched; a search that spills into neighbors lists several.
        val area = d["areas"].obj()?.let { it[loc?.get("areaId").str().orEmpty()] ?: it.values.firstOrNull() }.obj()
        return SearchPage(
            place = loc?.let { Place(it["city"].str().orEmpty(), it["postal"].str().orEmpty(), it["radius"].int() ?: 0, it["country"].str().orEmpty()) },
            units = Units(area?.get("distanceUnits").str() ?: "mi", area?.get("areaUnits").str() ?: "ft"),
            total = d["totalResultCount"].int() ?: 0,
            items = if (decode == null) emptyList() else items(d["items"].arr().orEmpty(), decode, d["areas"].obj()),
            filters = filters.flatMap { filter(it.obj()) },
            sortOptions = options(sortFilter),
            sort = d["detailsOrder"].str() ?: sortFilter?.get("value").str() ?: "date",
            subareas = options(subareaFilter),
            cacheTs = d["cacheTs"].long() ?: 0L,
            cacheId = d["cacheId"].str(),
            maxPostedTs = d["maxPostedTs"].long(),
            bundleDups = d["bundleDups"].int() ?: 0,
            minPostingId = minPostingId,
        )
    }

    private fun options(filter: JsonObject?): List<Option> =
        filter?.get("options").arr().orEmpty().mapNotNull { o ->
            val oo = o.obj() ?: return@mapNotNull null
            Option(oo["label"].str() ?: return@mapNotNull null, oo["value"].str().orEmpty())
        }

    /** The search text, sort order and sub-area have their own places in the UI, not the filter sheet. */
    private val ownUi = setOf("query", "sort", "subarea")

    private fun filter(f: JsonObject?): List<Filter> {
        f ?: return emptyList()
        val name = f["name"].str().orEmpty()
        val label = f["label"].str() ?: name
        if (name in ownUi) return emptyList()
        return when (f["type"].str()) {
            "text" -> listOf(Filter.Text(name, label, f["autocomplete"].str()))
            "range" -> {
                val min = f["minVal"].obj()?.get("name").str()
                val max = f["maxVal"].obj()?.get("name").str()
                if (min == null || max == null) emptyList() else listOf(Filter.Range(label, min, max, f["input_prefix"].str().orEmpty()))
            }
            "select" -> options(f).takeIf { it.size > 1 }?.let { listOf(Filter.Select(name, label, it)) }.orEmpty()
            "multi" -> options(f).takeIf { it.isNotEmpty() }?.let { listOf(Filter.Multi(name, label, it)) }.orEmpty()
            "boolean" -> listOf(Filter.Toggle(name, label, f["value"].str() ?: "1"))
            "group" -> f["form"].arr().orEmpty().flatMap { filter(it.obj()) }
            else -> emptyList()
        }
    }

    /**
     * Search results come packed. Each row is `[idOffset, dateOffset, categoryId, price, location,
     * firstImageSize, ...]` followed by optional pieces in any order: a bare string is the title,
     * a negative number groups duplicates, and an array is a tagged field whose first element says
     * which. Ids and dates are offsets from minimums in the `decode` table, and the location is
     * indexes into its tables: `area:description[:neighborhood]~lat~lon`.
     */
    private fun items(rows: List<JsonElement>, decode: JsonObject, areas: JsonObject?): List<Listing> {
        val minPostingId = decode["minPostingId"].long() ?: 0L
        val minPostedDate = decode["minPostedDate"].long() ?: 0L
        val locations = decode["locations"].arr().orEmpty()
        val descriptions = decode["locationDescriptions"].arr().orEmpty()
        val neighborhoods = decode["neighborhoods"].arr().orEmpty()
        // Since format version 2 a sixth fixed element carries the first image's encoded size.
        val fixed = if (decode["version"].int() == 1) 5 else 6
        return rows.mapNotNull { r ->
            val row = r.arr() ?: return@mapNotNull null
            if (row.size < 5) return@mapNotNull null
            val geo = row[4].str().orEmpty().split('~')
            val idx = geo[0].split(':').map { it.toIntOrNull() ?: 0 }
            val loc = locations.getOrNull(idx.getOrElse(0) { 0 }).arr()
            val hood = idx.getOrNull(2)?.let { neighborhoods.getOrNull(it).name() }
            val description = idx.getOrNull(1)?.let { descriptions.getOrNull(it).name() }
            val price = row[3].long()
            var listing = Listing(
                postingId = minPostingId + (row[0].long() ?: return@mapNotNull null),
                postedAt = minPostedDate + (row[1].long() ?: 0L),
                categoryId = row[2].int() ?: 0,
                price = price?.takeIf { it >= 0 },
                // The neighborhood is the site's own name for the place; the description is whatever
                // the poster typed there, which for dealers is often a phone number.
                place = hood ?: description.orEmpty(),
                areaId = loc?.getOrNull(0).int() ?: 0,
                distanceUnit = areas?.get(loc?.getOrNull(0).str().orEmpty()).obj()?.get("distanceUnits").str(),
                hostname = loc?.getOrNull(1).str().orEmpty(),
                subarea = loc?.getOrNull(2).str().orEmpty(),
                lat = geo.getOrNull(1)?.toDoubleOrNull(),
                lon = geo.getOrNull(2)?.toDoubleOrNull(),
            )
            for (extra in row.drop(fixed)) {
                when (extra) {
                    is JsonArray -> listing = tagged(listing, extra)
                    is JsonPrimitive -> if (extra.isString) listing = listing.copy(title = extra.content)
                    else -> {}
                }
            }
            listing
        }
    }

    private fun tagged(l: Listing, field: JsonArray): Listing = when (field.firstOrNull().int()) {
        4 -> l.copy(imageIds = field.drop(1).mapNotNull { it.str() })
        5 -> l.copy(bedrooms = field.getOrNull(1).int()?.takeIf { it > 0 }, sqft = field.getOrNull(2).int()?.takeIf { it > 0 })
        6 -> l.copy(slug = field.getOrNull(1).str())
        9 -> l.copy(odometer = field.getOrNull(1).long())
        10 -> l.copy(priceText = field.getOrNull(1).str())
        13 -> l.copy(uuid = field.getOrNull(1).str())
        else -> l
    }

    /** A details batch: `[idOffset, title, imageIds, ...tagged fields]` per row, keyed here by posting id. */
    fun batch(body: String, fallbackMinPostingId: Long): Map<Long, ListingDetails> {
        val d = data(body)
        val min = d["minPostingId"].long() ?: fallbackMinPostingId
        return d["batch"].arr().orEmpty().mapNotNull { r ->
            val row = r.arr() ?: return@mapNotNull null
            val id = min + (row.getOrNull(0).long() ?: return@mapNotNull null)
            var l = Listing(postingId = id, postedAt = 0, categoryId = 0)
            for (extra in row.drop(3)) if (extra is JsonArray) l = tagged(l, extra)
            id to ListingDetails(
                title = row.getOrNull(1).str().orEmpty(),
                imageIds = row.getOrNull(2).arr().orEmpty().mapNotNull { it.str() },
                uuid = l.uuid, slug = l.slug, priceText = l.priceText, odometer = l.odometer, bedrooms = l.bedrooms, sqft = l.sqft,
            )
        }.toMap()
    }

    // ---- one listing ----

    fun posting(body: String): Posting {
        val p = data(body)["items"].arr()?.firstOrNull().obj() ?: throw ApiException("This listing is no longer available")
        val loc = p["location"].obj()
        val place = listOfNotNull(
            loc?.get("neighborhood").name() ?: loc?.get("description").name(),
            loc?.get("subArea").name(),
        ).joinToString(", ")
        return Posting(
            postingId = p["postingId"].long() ?: 0L,
            uuid = p["postingUuid"].str().orEmpty(),
            title = p["title"].str().orEmpty(),
            priceText = p["priceString"].str(),
            bodyHtml = p["body"].str().orEmpty(),
            imageIds = p["images"].arr().orEmpty().mapNotNull { it.str() },
            attributes = p["attributes"].arr().orEmpty().mapNotNull { a ->
                val o = a.obj() ?: return@mapNotNull null
                val value = o["value"].str()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                // Flags like "cryptocurrency ok" have a label and a value that say the same thing.
                Attribute(o["label"].str().orEmpty().takeIf { !it.equals(value, true) }.orEmpty(), value)
            },
            postedAt = p["postedDate"].long() ?: 0L,
            updatedAt = p["updatedDate"].long() ?: 0L,
            lat = loc?.get("lat").double(),
            lon = loc?.get("lon").double(),
            place = place,
            area = loc?.get("area").str().orEmpty(),
            category = p["category"].str().orEmpty(),
            url = p["url"].str().orEmpty(),
            repostOf = p["repostOf"].long()?.takeIf { it > 0 },
            notices = p["notices"].arr().orEmpty().mapNotNull { it.str() ?: it.obj()?.get("text").str() },
        )
    }
}
