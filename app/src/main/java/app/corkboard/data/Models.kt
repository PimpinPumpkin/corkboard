package app.corkboard.data

import kotlinx.serialization.Serializable

/** One craigslist site: "SF bay area" at sfbay.craigslist.org, with its optional sub-areas. */
@Serializable
data class Area(
    val id: Int,
    val hostname: String,
    val name: String,
    val region: String = "",
    val country: String = "",
    val lat: Double = 0.0,
    val lon: Double = 0.0,
    val subAreas: List<SubArea> = emptyList(),
) {
    /** Miles in the three countries whose sites measure in them, kilometers everywhere else. */
    val distanceUnit: String get() = if (country in setOf("US", "GB", "PR")) "mi" else "km"
}

@Serializable
data class SubArea(val abbr: String, val name: String)

/** A search as the user built it. Everything needed to run it again later, and nothing else. */
@Serializable
data class SearchQuery(
    val areaHost: String,
    val areaName: String,
    val category: String,
    val categoryName: String,
    val subarea: String? = null,
    /** Filter values by the site's own parameter names; multi-selects have several values. */
    val params: Map<String, List<String>> = emptyMap(),
) {
    val text: String get() = params["query"]?.firstOrNull().orEmpty()
    val sort: String? get() = params["sort"]?.firstOrNull()

    fun with(name: String, values: List<String>): SearchQuery =
        copy(params = if (values.isEmpty() || values.all { it.isEmpty() }) params - name else params + (name to values))

    val postal: String? get() = params[POSTAL]?.firstOrNull()
    val distance: String? get() = params[DISTANCE]?.firstOrNull()

    /** Searches around a postal code instead of the whole area, or the whole area again with null. */
    fun near(postal: String?, distance: String?): SearchQuery =
        // Sorting by distance means nothing without a point to measure from.
        if (postal.isNullOrBlank()) copy(params = (params - POSTAL - DISTANCE).filterNot { it.key == "sort" && it.value == listOf("dist") })
        else copy(subarea = null, params = params + (POSTAL to listOf(postal.trim())) + (DISTANCE to listOf(distance?.takeIf { it.isNotBlank() } ?: "10")))

    /** How many filters are set, not counting what has its own control outside the filter sheet. */
    val filterCount: Int get() = params.keys.count { it !in OWN_UI }

    companion object {
        const val POSTAL = "postal"
        const val DISTANCE = "search_distance"
        val OWN_UI = setOf("query", "sort", POSTAL, DISTANCE)
    }
}

/** One row of search results. Rows past the first few hundred arrive without [title] and images. */
@Serializable
data class Listing(
    val postingId: Long,
    val postedAt: Long,
    val categoryId: Int,
    val price: Long? = null,
    val priceText: String? = null,
    val title: String? = null,
    val place: String = "",
    val hostname: String = "",
    val subarea: String = "",
    val lat: Double? = null,
    val lon: Double? = null,
    val imageIds: List<String> = emptyList(),
    val uuid: String? = null,
    val slug: String? = null,
    val odometer: Long? = null,
    val bedrooms: Int? = null,
    val sqft: Int? = null,
    /** The craigslist site the listing is on; searches by distance reach into neighboring ones. */
    val areaId: Int = 0,
    /** "mi" or "km" as that site measures, which near a border is not always the searcher's. */
    val distanceUnit: String? = null,
) {
    val hasDetails: Boolean get() = title != null && uuid != null
}

data class Option(val label: String, val value: String)

/** A search control, described by the site itself in every search response. */
sealed interface Filter {
    val label: String

    data class Text(val name: String, override val label: String, val autocomplete: String?) : Filter
    data class Range(override val label: String, val minName: String, val maxName: String, val prefix: String) : Filter
    data class Select(val name: String, override val label: String, val options: List<Option>) : Filter
    data class Multi(val name: String, override val label: String, val options: List<Option>) : Filter
    data class Toggle(val name: String, override val label: String, val value: String) : Filter
}

/** Where the site says a search was run: the answer to a postal code, or just the area. */
data class Place(val city: String, val postal: String, val radius: Int, val country: String)

/** A spot resolved by the site: which craigslist site covers it, the town, and its postal code. */
data class Located(val areaId: Int, val city: String, val postal: String)

/** "Within [distance] of [postal]": the narrower place every search from the home screen starts with. */
@Serializable
data class Near(val postal: String, val distance: String, val city: String = "")

/** The measures an area uses, as the site reports them: "mi" or "km", "ft" or "m". */
data class Units(val distance: String = "mi", val area: String = "ft")

class SearchPage(
    val place: Place?,
    val units: Units,
    val total: Int,
    val items: List<Listing>,
    val filters: List<Filter>,
    val sortOptions: List<Option>,
    val sort: String,
    val subareas: List<Option>,
    val cacheTs: Long,
    val cacheId: String?,
    val maxPostedTs: Long?,
    val bundleDups: Int,
    val minPostingId: Long,
)

/** What a details batch adds to a title-less [Listing]. */
class ListingDetails(
    val title: String,
    val imageIds: List<String>,
    val uuid: String?,
    val slug: String?,
    val priceText: String?,
    val odometer: Long?,
    val bedrooms: Int?,
    val sqft: Int?,
)

data class Attribute(val label: String, val value: String)

class Posting(
    val postingId: Long,
    val uuid: String,
    val title: String,
    val priceText: String?,
    val bodyHtml: String,
    val imageIds: List<String>,
    val attributes: List<Attribute>,
    val postedAt: Long,
    val updatedAt: Long,
    val lat: Double?,
    val lon: Double?,
    val place: String,
    val area: String,
    val category: String,
    val url: String,
    val notices: List<String>,
)

class ApiException(message: String) : Exception(message)

object Images {
    const val THUMB = "300x300"
    const val MEDIUM = "600x450"
    const val LARGE = "1200x900"

    /** "3:00w0w_abc_0ny0hJ" is image 00w0w_abc_0ny0hJ on image host 3; every host is the same name. */
    fun url(imageId: String, size: String): String =
        "https://images.craigslist.org/${imageId.substringAfter(':')}_$size.jpg"
}
