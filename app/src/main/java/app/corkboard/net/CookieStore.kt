package app.corkboard.net

import android.content.SharedPreferences

/**
 * Cronet's embedded build keeps no cookie jar, so this is one: small, persistent, and only as
 * clever as the site needs. craigslist sets a single long-lived browser cookie on the first API
 * call and expects it back; a client that arrives without one every time is the odd one out.
 */
class CookieStore(private val prefs: SharedPreferences?) {
    private data class Cookie(val name: String, val value: String, val domain: String, val expiresAt: Long)

    private val cookies = LinkedHashMap<String, Cookie>()

    init {
        prefs?.all?.forEach { (key, raw) ->
            val parts = (raw as? String)?.split('\t') ?: return@forEach
            if (parts.size == 4) cookies[key] = Cookie(parts[0], parts[1], parts[2], parts[3].toLongOrNull() ?: 0L)
        }
    }

    /** Records one `Set-Cookie` header received from [host]. */
    @Synchronized
    fun save(host: String, setCookie: String, now: Long = System.currentTimeMillis()) {
        val pieces = setCookie.split(';').map { it.trim() }
        val (name, value) = pieces.firstOrNull()?.split('=', limit = 2)?.takeIf { it.size == 2 } ?: return
        if (name.isEmpty()) return
        var domain = host
        var expiresAt = Long.MAX_VALUE
        for (attr in pieces.drop(1)) {
            val k = attr.substringBefore('=').lowercase()
            val v = attr.substringAfter('=', "")
            when (k) {
                "domain" -> v.trimStart('.').lowercase().takeIf { host == it || host.endsWith(".$it") }?.let { domain = it }
                "max-age" -> v.toLongOrNull()?.let { expiresAt = now + it * 1000 }
            }
        }
        val key = "$domain|$name"
        if (expiresAt <= now || value.isEmpty()) {
            cookies.remove(key)
            prefs?.edit()?.remove(key)?.apply()
        } else {
            cookies[key] = Cookie(name, value, domain, expiresAt)
            prefs?.edit()?.putString(key, "$name\t$value\t$domain\t$expiresAt")?.apply()
        }
    }

    /** The `Cookie` header for a request to [host], or null when there is nothing to send. */
    @Synchronized
    fun header(host: String, now: Long = System.currentTimeMillis()): String? =
        cookies.values
            .filter { it.expiresAt > now && (host == it.domain || host.endsWith(".${it.domain}")) }
            .joinToString("; ") { "${it.name}=${it.value}" }
            .ifEmpty { null }

    @Synchronized
    fun clear() {
        cookies.clear()
        prefs?.edit()?.clear()?.apply()
    }
}
