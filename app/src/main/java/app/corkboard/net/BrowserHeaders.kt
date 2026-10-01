package app.corkboard.net

/**
 * The request details Chrome for Android produces and Cronet does not. Cronet gives the app
 * Chrome's TLS and HTTP/2 handshake; everything here is what makes the headers agree with it.
 * All of it derives from one number, the Cronet version in the APK, so the claimed browser is
 * always the one whose network stack is doing the talking.
 */
object BrowserHeaders {
    /** The page every request claims to come from: the site's own search app lives on www. */
    const val ORIGIN = "https://www.craigslist.org"

    /** Chrome's reduced user agent: the device is always "K" on Android 10 and the minor version zero. */
    fun userAgent(cronetVersion: String): String =
        "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/${major(cronetVersion)}.0.0.0 Mobile Safari/537.36"

    fun major(cronetVersion: String): Int = cronetVersion.substringBefore('.').toIntOrNull() ?: 0

    /**
     * The `Sec-CH-UA` value Chrome [major] sends. Chrome derives all of it from the major version
     * (components/embedder_support/user_agent_utils.cc): a GREASE brand whose two characters,
     * version and position in the list are picked by `major` modulo the table sizes, so each
     * release has exactly one header. 120 gives `"Not_A Brand";v="8", "Chromium";v="120",
     * "Google Chrome";v="120"`.
     */
    fun secChUa(major: Int): String {
        val chars = listOf(" ", "(", ":", "-", ".", "/", ")", ";", "=", "?", "_")
        val greaseVersions = listOf("8", "99", "24")
        val list = listOf(
            "Not${chars[major % chars.size]}A${chars[(major + 1) % chars.size]}Brand" to greaseVersions[major % greaseVersions.size],
            "Chromium" to "$major",
            "Google Chrome" to "$major",
        )
        val orders = listOf(listOf(0, 1, 2), listOf(0, 2, 1), listOf(1, 0, 2), listOf(1, 2, 0), listOf(2, 0, 1), listOf(2, 1, 0))
        val order = orders[major % orders.size]
        val out = arrayOfNulls<Pair<String, String>>(3)
        for (i in list.indices) out[order[i]] = list[i]
        return out.joinToString(", ") { "\"${it!!.first}\";v=\"${it.second}\"" }
    }

    /**
     * Chrome's `Accept-Language` for a language list, as net::HttpUtil builds it: each tag, then
     * its bare language unless the next tag shares it, no duplicates, q-values falling from 0.9.
     */
    fun acceptLanguage(tags: List<String>): String {
        val langs = tags.map { it.trim() }.filter { it.isNotEmpty() }
        if (langs.isEmpty()) return "en-US,en;q=0.9"
        val out = LinkedHashSet<String>()
        for ((i, lang) in langs.withIndex()) {
            out += lang
            val base = lang.substringBefore('-')
            if (base == lang) continue
            if (i < langs.size - 1 && langs[i + 1].substringBefore('-') == base) continue
            out += base
        }
        return out.mapIndexed { i, l -> if (i == 0) l else "$l;q=0.${(10 - i).coerceAtLeast(1)}" }.joinToString(",")
    }

    /** What kind of request this is, which decides `Accept` and the `Sec-Fetch-*` trio. */
    enum class Kind { Api, Image, Page }

    /**
     * Headers in the order Chrome writes them for a request made by the search page. The user
     * agent and `Accept-Encoding` are Cronet's own to send.
     */
    fun headers(kind: Kind, cronetVersion: String, acceptLanguage: String): List<Pair<String, String>> {
        val major = major(cronetVersion)
        return buildList {
            add("sec-ch-ua-platform" to "\"Android\"")
            add("sec-ch-ua" to secChUa(major))
            add("sec-ch-ua-mobile" to "?1")
            when (kind) {
                Kind.Api -> {
                    add("Accept" to "*/*")
                    add("Origin" to ORIGIN)
                    add("Sec-Fetch-Site" to "same-site")
                    add("Sec-Fetch-Mode" to "cors")
                    add("Sec-Fetch-Dest" to "empty")
                }
                Kind.Page -> {
                    // A page typed into the address bar: no referrer, and nothing but the navigation trio.
                    add("Upgrade-Insecure-Requests" to "1")
                    add("Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8")
                    add("Sec-Fetch-Site" to "none")
                    add("Sec-Fetch-Mode" to "navigate")
                    add("Sec-Fetch-User" to "?1")
                    add("Sec-Fetch-Dest" to "document")
                }
                Kind.Image -> {
                    add("Accept" to "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
                    add("Sec-Fetch-Site" to "same-site")
                    add("Sec-Fetch-Mode" to "no-cors")
                    add("Sec-Fetch-Dest" to "image")
                }
            }
            if (kind != Kind.Page) add("Referer" to "$ORIGIN/")
            add("Accept-Language" to acceptLanguage)
        }
    }
}
