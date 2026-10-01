package app.corkboard.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** A list of kept listings turned into something that can leave the app: a message, or a spreadsheet. */
object Export {
    private fun link(l: Listing): String = l.uuid?.let { ClUrls.web(it, l.slug) }.orEmpty()
    private fun price(l: Listing): String = l.priceText ?: l.price?.toString().orEmpty()

    /** Plain text for the share sheet: one short block per listing. */
    fun text(name: String, items: List<Listing>, notes: Map<Long, String>): String = buildString {
        append(name).append("\n")
        for (l in items) {
            append("\n").append(l.title.orEmpty())
            price(l).takeIf { it.isNotEmpty() }?.let { append(" · ").append(it) }
            if (l.place.isNotBlank()) append(" · ").append(l.place)
            append("\n")
            notes[l.postingId]?.let { append("Note: ").append(it).append("\n") }
            link(l).takeIf { it.isNotEmpty() }?.let { append(it).append("\n") }
        }
    }

    /** One row per listing. Quoted the way spreadsheets expect, so commas and line breaks survive. */
    fun csv(items: List<Listing>, notes: Map<Long, String>): String {
        fun cell(v: String) = "\"" + v.replace("\"", "\"\"") + "\""
        val date = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        val rows = items.map { l ->
            listOf(
                l.title.orEmpty(), price(l), l.place, l.odometer?.toString().orEmpty(),
                if (l.postedAt > 0) date.format(Date(l.postedAt * 1000)) else "",
                notes[l.postingId].orEmpty(), link(l), l.postingId.toString(), l.imageIds.firstOrNull().orEmpty(),
            ).joinToString(",") { cell(it) }
        }
        return (listOf(HEADER) + rows).joinToString("\r\n") + "\r\n"
    }

    private const val HEADER = "Title,Price,Place,Odometer,Posted,Note,Link,Posting ID,Image"

    /** Splits spreadsheet text into rows of cells, honoring quotes, doubled quotes and line breaks inside cells. */
    fun parseCsv(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var i = 0
        val t = text.removePrefix("\uFEFF")
        while (i < t.length) {
            val c = t[i]
            when {
                quoted && c == '"' && t.getOrNull(i + 1) == '"' -> { cell.append('"'); i++ }
                c == '"' -> quoted = !quoted
                !quoted && c == ',' -> { row.add(cell.toString()); cell.clear() }
                !quoted && (c == '\n' || c == '\r') -> {
                    if (c == '\r' && t.getOrNull(i + 1) == '\n') i++
                    row.add(cell.toString()); cell.clear()
                    if (row.any { it.isNotEmpty() }) rows.add(row)
                    row = mutableListOf()
                }
                else -> cell.append(c)
            }
            i++
        }
        row.add(cell.toString())
        if (row.any { it.isNotEmpty() }) rows.add(row)
        return rows
    }

    /**
     * Listings and their notes out of a spreadsheet this app saved, or one laid out the same way.
     * Columns are found by their headings, so they can be reordered or some left out; a row
     * needs a craigslist link to count.
     */
    fun listings(csv: String): List<Pair<Listing, String>> {
        val rows = parseCsv(csv)
        val head = rows.firstOrNull()?.map { it.trim().lowercase() } ?: return emptyList()
        fun col(name: String) = head.indexOf(name)
        val (title, price, place, odometer, posted, note, link, id, image) =
            listOf("title", "price", "place", "odometer", "posted", "note", "link", "posting id", "image").map(::col)
        if (link < 0) return emptyList()
        val date = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        return rows.drop(1).mapNotNull { r ->
            fun at(i: Int) = r.getOrNull(i)?.trim().orEmpty()
            val from = ClUrls.listingFromLink(at(link)) ?: return@mapNotNull null
            // Without the site's own id, one is made from the address, so hearts and notes still have something to hold on to.
            val postingId = at(id).toLongOrNull() ?: (from.uuid.hashCode().toLong() and 0xFFFFFFFFL) + 1
            from.copy(
                postingId = postingId,
                title = at(title).ifEmpty { "Listing" },
                priceText = at(price).ifEmpty { null },
                place = at(place),
                odometer = at(odometer).filter { it.isDigit() }.toLongOrNull(),
                postedAt = runCatching { date.parse(at(posted))!!.time / 1000 }.getOrDefault(0L),
                imageIds = listOfNotNull(at(image).ifEmpty { null }),
            ) to at(note)
        }
    }

    fun fileName(name: String): String = name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifEmpty { "list" } + ".csv"
}

private operator fun <T> List<T>.component6(): T = this[5]
private operator fun <T> List<T>.component7(): T = this[6]
private operator fun <T> List<T>.component8(): T = this[7]
private operator fun <T> List<T>.component9(): T = this[8]
