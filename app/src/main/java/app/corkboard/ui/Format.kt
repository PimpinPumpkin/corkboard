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

    /** The site formats prices itself, in the area's currency; the bare number is only a fallback. */
    fun price(l: Listing): String? = l.priceText ?: l.price?.let { NumberFormat.getIntegerInstance().format(it) }

    /** The small facts under a title: "92,000 mi", "2 br", "850 sq ft", in the area's own measures. */
    fun facts(l: Listing, units: Units = Units()): String = listOfNotNull(
        l.odometer?.let { NumberFormat.getIntegerInstance().format(it) + " " + units.distance },
        l.bedrooms?.let { "$it br" },
        l.sqft?.let { "$it " + if (units.area == "m") "m²" else "sq ft" },
    ).joinToString(" · ")

    fun count(n: Int): String = NumberFormat.getIntegerInstance(Locale.US).format(n)
}
