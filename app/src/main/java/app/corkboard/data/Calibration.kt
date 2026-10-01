package app.corkboard.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The handful of facts about craigslist's private API that the app depends on and the site could
 * change on any day. They are compiled in, and can also be corrected without an app update: the
 * app reads `calibration.json` from this project's repository once a day and adopts it when its
 * `version` is higher than the one in hand.
 *
 * It can only retune what is here. It cannot point the app at another host or run anything; a
 * file with values outside the sane ranges below is ignored whole.
 */
@Serializable
data class Calibration(
    val version: Int = 1,
    /** The API generation in every path: `/web/v8/`. */
    val api: String = "v8",
    /** How many complete rows the first search request asks for, and the size of a details batch. */
    val quick: Int = 360,
    val chunk: Int = 1080,
    /** The numbers that tag the optional pieces of a packed search row. */
    val tagImages: Int = 4,
    val tagHousing: Int = 5,
    val tagSlug: Int = 6,
    val tagOdometer: Int = 9,
    val tagPrice: Int = 10,
    val tagUuid: Int = 13,
    /** A line to show on the home screen: "craigslist changed something, please update". */
    val notice: String? = null,
) {
    private fun sane(): Boolean =
        Regex("v[0-9]{1,3}").matches(api) && quick in 1..10_000 && chunk in 1..10_000 &&
            listOf(tagImages, tagHousing, tagSlug, tagOdometer, tagPrice, tagUuid).let { t -> t.all { it in 1..99 } && t.toSet().size == t.size } &&
            (notice == null || notice.length <= 300)

    companion object {
        const val URL = "https://raw.githubusercontent.com/PimpinPumpkin/corkboard/main/calibration.json"
        private val json = Json { ignoreUnknownKeys = true }

        @Volatile var current: Calibration = Calibration()
            private set

        /** Takes [text] as the calibration in force if it parses, is sane, and is newer. Returns whether it did. */
        fun adopt(text: String): Boolean {
            val c = runCatching { json.decodeFromString(serializer(), text) }.getOrNull() ?: return false
            if (!c.sane() || c.version <= current.version) return false
            current = c
            return true
        }

        /** For tests: back to what is compiled in. */
        fun reset() { current = Calibration() }
    }
}
