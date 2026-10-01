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
)

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

    /** How many filters are set, not counting the search text and the sort order. */
    val filterCount: Int get() = params.keys.count { it != "query" && it != "sort" }
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

class SearchPage(
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
