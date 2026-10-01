package app.corkboard.ui

import app.corkboard.data.Listing
import app.corkboard.data.Units
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Format {
    /** "5 min ago", "3 hr ago", "2 days ago", then a plain date. [postedAt] and [now] are epoch seconds. */
    fun ago(postedAt: Long, now: Long = System.currentTimeMillis() / 1000): String {
        val s = (now - postedAt).coerceAtLeast(0)
        return when {
            postedAt <= 0 -> ""
            s < 90 -> "just now"
            s < 3600 -> "${s / 60} min ago"
            s < 86_400 -> "${s / 3600} hr ago"
            s < 2 * 86_400 -> "yesterday"
            s < 30 * 86_400 -> "${s / 86_400} days ago"
            else -> SimpleDateFormat("MMM d", Locale.US).format(Date(postedAt * 1000))
        }
    }

    /** "Sep 28, 3:14 PM", or with the year when it is not this one. [at] is epoch seconds. */
    fun date(at: Long): String {
        val d = Date(at * 1000)
        val sameYear = SimpleDateFormat("yyyy", Locale.US).let { it.format(d) == it.format(Date()) }
        return SimpleDateFormat(if (sameYear) "MMM d, h:mm a" else "MMM d yyyy, h:mm a", Locale.US).format(d)
    }

    /**
     * A listing's description, calmed down for reading: headings and oversized text become bold
     * lines at the normal size. Sellers shout in <h1>, and at one line height that overlaps itself.
     */
    fun bodyHtml(html: String): String = html
        .replace(Regex("<h[1-6][^>]*>", RegexOption.IGNORE_CASE), "<b>")
        .replace(Regex("</h[1-6]>", RegexOption.IGNORE_CASE), "</b><br>")
        .replace(Regex("</?(big|small|font)[^>]*>", RegexOption.IGNORE_CASE), "")

    /** The site formats prices itself, in the area's currency; the bare number is only a fallback. */
    fun price(l: Listing): String? = l.priceText ?: l.price?.let { NumberFormat.getIntegerInstance().format(it) }

    /** The small facts under a title: "92,000 mi", "2 br", "850 sq ft", in the area's own measures. */
    fun facts(l: Listing, units: Units = Units()): String = listOfNotNull(
        l.odometer?.let { NumberFormat.getIntegerInstance().format(it) + " " + (l.distanceUnit ?: units.distance) },
        l.bedrooms?.let { "$it br" },
        l.sqft?.let { "$it " + if (units.area == "m") "m²" else "sq ft" },
    ).joinToString(" · ")

    fun count(n: Int): String = NumberFormat.getIntegerInstance(Locale.US).format(n)
}
