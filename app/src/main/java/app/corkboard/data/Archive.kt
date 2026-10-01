package app.corkboard.data

import android.content.Context
import app.corkboard.net.BrowserHeaders
import app.corkboard.net.Http
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/** A listing as it was last seen: the site's own response, kept with a few facts about it. */
@Serializable
data class Snapshot(
    val savedAt: Long,
    val firstSeenAt: Long,
    val body: String,
    val priceText: String? = null,
    /** The price before the most recent change the app noticed, and when it noticed. */
    val priceWas: String? = null,
    val priceChangedAt: Long? = null,
)

/**
 * A private copy of every listing opened, so one that is later deleted or expires can still be
 * read. The text of the last few hundred is kept; for listings the user chose to keep (a heart, a
 * list, a note) the photos are kept too, because the site removes them along with the listing.
 */
class Archive(context: Context, private val http: Http) {
    private val dir = File(context.filesDir, "archive").apply { mkdirs() }
    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun file(uuid: String) = File(dir, "${safe(uuid)}.json")
    private fun photos(uuid: String) = File(dir, safe(uuid))
    private fun safe(uuid: String) = uuid.filter { it.isLetterOrDigit() }.take(40)

    fun load(uuid: String): Snapshot? = runCatching { json.decodeFromString(Snapshot.serializer(), file(uuid).readText()) }.getOrNull()

    /** Records a fresh response, remembering the previous price if this one differs from it. */
    fun save(uuid: String, body: String, priceText: String?, now: Long = System.currentTimeMillis() / 1000): Snapshot {
        val old = load(uuid)
        val changed = old != null && old.priceText != null && priceText != null && old.priceText != priceText
        val snapshot = Snapshot(
            savedAt = now,
            firstSeenAt = old?.firstSeenAt ?: now,
            body = body,
            priceText = priceText,
            priceWas = if (changed) old!!.priceText else old?.priceWas,
            priceChangedAt = if (changed) now else old?.priceChangedAt,
        )
        runCatching {
            val tmp = File(dir, "${safe(uuid)}.tmp")
            tmp.writeText(json.encodeToString(Snapshot.serializer(), snapshot))
            tmp.renameTo(file(uuid))
        }
        return snapshot
    }

    /** The saved copy of photo [index], if there is one. */
    fun photo(uuid: String, index: Int): File? = File(photos(uuid), "$index.jpg").takeIf { it.length() > 0 }

    /**
     * Keeps a listing for good: its text if not already saved, and its photos. Photos are fetched
     * one at a time with a pause between, the way paging through the gallery would.
     */
    fun keep(listing: Listing, api: ClApi) {
        val uuid = listing.uuid ?: return
        scope.launch {
            runCatching {
                val imageIds = if (load(uuid) == null) {
                    val body = api.postingRaw(uuid)
                    val p = Parsers.posting(body)
                    save(uuid, body, p.priceText)
                    p.imageIds
                } else {
                    runCatching { Parsers.posting(load(uuid)!!.body).imageIds }.getOrDefault(listing.imageIds)
                }
                val folder = photos(uuid).apply { mkdirs() }
                imageIds.take(MAX_PHOTOS).forEachIndexed { i, id ->
                    val out = File(folder, "$i.jpg")
                    if (out.length() > 0) return@forEachIndexed
                    val r = http.get(Images.url(id, Images.MEDIUM), BrowserHeaders.Kind.Image)
                    if (r.ok) withContext(Dispatchers.IO) { out.writeBytes(r.body) }
                    delay(400)
                }
            }
        }
    }

    /** Drops the oldest text copies past [MAX_TEXT], and never one that is in [kept]. */
    fun prune(kept: Set<String>) {
        scope.launch {
            val keptNames = kept.map { safe(it) }.toSet()
            val files = dir.listFiles { f -> f.isFile && f.name.endsWith(".json") }.orEmpty().sortedByDescending { it.lastModified() }
            files.drop(MAX_TEXT).filter { it.nameWithoutExtension !in keptNames }.forEach { it.delete() }
            dir.listFiles { f -> f.isDirectory }.orEmpty().filter { it.name !in keptNames }.forEach { it.deleteRecursively() }
        }
    }

    private companion object {
        const val MAX_TEXT = 400
        const val MAX_PHOTOS = 24
    }
}
