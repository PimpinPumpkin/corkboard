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
                notes[l.postingId].orEmpty(), link(l), l.postingId.toString(),
            ).joinToString(",") { cell(it) }
        }
        return (listOf("Title,Price,Place,Odometer,Posted,Note,Link,Posting ID") + rows).joinToString("\r\n") + "\r\n"
    }

    fun fileName(name: String): String = name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifEmpty { "list" } + ".csv"
}
