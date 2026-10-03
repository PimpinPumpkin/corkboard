package app.corkboard.data

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** One of the site's discussion forums: "automotive" is 5. */
data class Forum(val id: Int, val name: String)

/**
 * One post in a forum thread. Most posts are only a title; [hasBody] says whether there is more
 * to fetch. [depth] is how far down the site's reply tree it sits (0 is the post that started the
 * thread), and the posts of a thread come in the site's order: each reply right after its parent's
 * earlier replies and everything under them. [handle] is null for an anonymous post.
 */
data class ForumPost(
    val id: Long,
    val depth: Int,
    val title: String,
    val handle: String?,
    val postedAt: Long,
    val hasBody: Boolean,
)

/** A thread as the forum's list shows it: the first post and the shape of what came after. */
data class ForumThread(val posts: List<ForumPost>) {
    val root: ForumPost get() = posts.first()
    val replies: Int get() = posts.size - 1
    val people: Int get() = posts.map { it.handle }.distinct().size
    val lastAt: Long get() = posts.maxOf { it.postedAt }
}

/** A page of a forum's threads, newest first. [older] continues the list, null at the end. */
data class ForumPage(val threads: List<ForumThread>, val older: Long?)

/** A whole thread, with the forum it belongs to when the page says. */
data class ForumThreadPage(val forum: Forum?, val posts: List<ForumPost>)

object ForumUrls {
    private const val HOST = "https://forums.craigslist.org/"

    const val INDEX = HOST

    /** The forum's thread list, the left half of its page. [older] is the site's thread number to go back from. */
    fun threads(forumId: Int, older: Long? = null): String = "$HOST?act=DF&forumID=$forumId" + (older?.let { "&last_thread=$it" } ?: "")

    /** Every post in the thread [postId] belongs to, as the site's single-thread page lists them. */
    fun thread(postId: Long): String = "$HOST?act=showThread&ID=$postId"

    /** One post, title and body. */
    fun post(postId: Long): String = "$HOST?act=Q&ID=$postId"

    /** The pages a person would open in a browser: the forum, a post in its thread, and replying. */
    fun forumWeb(forumId: Int): String = "$HOST?forumID=$forumId"
    fun web(postId: Long): String = "$HOST?ID=$postId"
    fun reply(forumId: Int, postId: Long): String = "$HOST?act=post&forumID=$forumId&ID=$postId"
    fun compose(forumId: Int): String = "$HOST?act=post&forumID=$forumId"
}

/**
 * The forums are server-rendered HTML from long before the site had an API, and they have not
 * changed shape in years. A thread is a run of `threadline` rows whose nesting is drawn with
 * dots (`: . .` once per level); a title ending in `§` has nothing more to read.
 */
object ForumParsers {
    /** The forums' times are the site's own clock, US Pacific. */
    private val SITE_ZONE: ZoneId = ZoneId.of("America/Los_Angeles")
    private val FULL = DateTimeFormatter.ofPattern("yyyy-MM-dd H:mm")

    /** The forum list, from the forum picker on the forums' front page. */
    fun forums(html: String): List<Forum> {
        val select = Regex("""<select name="forumID"[^>]*>(.*?)</select>""", RegexOption.DOT_MATCHES_ALL).find(html)?.groupValues?.get(1) ?: return emptyList()
        return Regex("""<option value="(\d+)"[^>]*>([^<]*)</option>""").findAll(select)
            .map { Forum(it.groupValues[1].toInt(), text(it.groupValues[2])) }
            // A placeholder with no name of its own sits in the list; it is not a forum anyone reads.
            .filter { it.name.isNotEmpty() && !it.name.startsWith("forum id") }
            .toList()
    }

    fun threads(html: String, now: LocalDate = LocalDate.now(SITE_ZONE)): ForumPage {
        val threads = Regex("""<article class="thread">(.*?)</article>""", RegexOption.DOT_MATCHES_ALL).findAll(html)
            .map { ForumThread(lines(it.groupValues[1], now)) }
            .filter { it.posts.isNotEmpty() }
            .toList()
        val older = Regex("""class="previous"\s+href="[^"]*last_thread=(\d+)""").find(html)?.groupValues?.get(1)?.toLongOrNull()
        return ForumPage(threads, older)
    }

    fun thread(html: String, now: LocalDate = LocalDate.now(SITE_ZONE)): ForumThreadPage {
        val forum = Regex("""class="singlethreadheader">.*?<a href="\?forumID=(\d+)"[^>]*>([^<]*)</a>""", RegexOption.DOT_MATCHES_ALL).find(html)
            ?.let { Forum(it.groupValues[1].toInt(), text(it.groupValues[2])) }
        val start = html.indexOf("<div class=\"threads\">").coerceAtLeast(0)
        return ForumThreadPage(forum, lines(html.substring(start), now))
    }

    /** A post's body as plain text with its line breaks, or null when it has none. */
    fun body(html: String): String? {
        val start = html.indexOf("<span class=\"quote\">").takeIf { it >= 0 } ?: return null
        val end = html.lastIndexOf("</span>", html.indexOf("class=\"contextlink\"").takeIf { it > start } ?: html.length).takeIf { it > start } ?: return null
        val raw = html.substring(start + "<span class=\"quote\">".length, end)
        return text(raw.replace(Regex("""\s*<br\s*/?>\s*""", RegexOption.IGNORE_CASE), "\n"), keepLines = true).ifEmpty { null }
    }

    private fun lines(html: String, now: LocalDate): List<ForumPost> =
        Regex("""<div class="threadline[^"]*">(.*?)</div>""", RegexOption.DOT_MATCHES_ALL).findAll(html).mapNotNull { m ->
            val line = m.groupValues[1]
            val link = Regex("""href="\?act=Q&(?:amp;)?ID=(\d+)"[^>]*class="title[^"]*">(.*?)</a>""", RegexOption.DOT_MATCHES_ALL).find(line) ?: return@mapNotNull null
            val title = text(link.groupValues[2])
            val handle = Regex("""<span class="handle ([a-z]+)">(.*?)</span>""").find(line)?.let { if (it.groupValues[1] == "anon") null else text(it.groupValues[2]).ifEmpty { null } }
            ForumPost(
                id = link.groupValues[1].toLong(),
                depth = Regex(""":\s*\.\s*\.""").findAll(Regex("""<span class="dotz">(.*?)</span>""").find(line)?.groupValues?.get(1).orEmpty()).count(),
                title = title.removeSuffix("§").trim(),
                handle = handle,
                postedAt = Regex("""<time>([^<]*)</time>""").find(line)?.groupValues?.get(1)?.let { time(it, now) } ?: 0L,
                hasBody = !title.endsWith("§"),
            )
        }.toList()

    /** "2026-09-26 19:31", or just "6:49" for today, on the site's clock, as epoch seconds. */
    internal fun time(s: String, today: LocalDate): Long? {
        val t = s.trim()
        val at = runCatching { LocalDateTime.parse(t, FULL) }.getOrNull()
            ?: runCatching { LocalDateTime.of(today, LocalTime.parse(t.padStart(5, '0'))) }.getOrNull()
            ?: return null
        return at.atZone(SITE_ZONE).toEpochSecond()
    }

    /** Markup to plain text. With [keepLines], line breaks survive and runs of blank lines shrink to one. */
    internal fun text(html: String, keepLines: Boolean = false): String {
        val plain = unescape(html.replace(Regex("<[^>]+>"), ""))
        return if (keepLines) plain.lines().joinToString("\n") { it.trimEnd() }.replace(Regex("\n{3,}"), "\n\n").trim()
        else plain.replace(Regex("\\s+"), " ").trim()
    }

    private val named = mapOf("amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ", "sect" to "§")

    private fun unescape(s: String): String = Regex("&(#x[0-9a-fA-F]+|#\\d+|[a-zA-Z]+);").replace(s) { m ->
        val e = m.groupValues[1]
        when {
            e.startsWith("#x") -> e.drop(2).toIntOrNull(16)?.let { String(Character.toChars(it)) }
            e.startsWith("#") -> e.drop(1).toIntOrNull()?.let { String(Character.toChars(it)) }
            else -> named[e]
        } ?: m.value
    }
}

/**
 * How a thread is laid out on a phone. The site indents every reply one step under its parent,
 * so a back-and-forth between two people walks off the right edge after a few posts. Here a reply
 * only steps in where the conversation splits: the only answer to a post stays in line with it and
 * reads like the next message, and when a post has several answers each one starts a branch, one
 * step in, that can be folded away.
 */
object ThreadLayout {
    /**
     * A row: the post, how many steps in it sits, and for each of those steps the branch it
     * belongs to (the id of the post that starts that branch), outermost first. A post that starts
     * a branch lists itself last. [replyTo] is the post it answers, when that is not the row just above.
     */
    data class Row(val post: ForumPost, val branches: List<Long>, val replyTo: ForumPost?, val hidden: Int) {
        val indent: Int get() = branches.size
        val startsBranch: Boolean get() = branches.lastOrNull() == post.id
    }

    fun rows(posts: List<ForumPost>): List<Row> {
        if (posts.isEmpty()) return emptyList()
        // The parent of each post is the nearest earlier post one level up.
        val parent = IntArray(posts.size) { -1 }
        val stack = ArrayList<Int>()
        posts.forEachIndexed { i, p ->
            while (stack.isNotEmpty() && posts[stack.last()].depth >= p.depth) stack.removeAt(stack.lastIndex)
            parent[i] = stack.lastOrNull() ?: -1
            stack += i
        }
        val children = IntArray(posts.size)
        parent.forEach { if (it >= 0) children[it]++ }
        // Everything under each post, for the count shown when its branch is folded.
        val under = IntArray(posts.size)
        for (i in posts.indices.reversed()) if (parent[i] >= 0) under[parent[i]] += under[i] + 1

        val branches = arrayOfNulls<List<Long>>(posts.size)
        return posts.mapIndexed { i, p ->
            val up = parent[i]
            val b = when {
                up < 0 -> emptyList()
                children[up] >= 2 -> branches[up]!! + p.id
                else -> branches[up]!!
            }
            branches[i] = b
            Row(p, b, replyTo = posts.getOrNull(up)?.takeIf { up != i - 1 }, hidden = under[i])
        }
    }

    /** The rows left showing when the branches in [folded] are folded: each folded branch keeps its first post. */
    fun visible(rows: List<Row>, folded: Set<Long>): List<Row> =
        if (folded.isEmpty()) rows else rows.filter { r -> r.branches.none { it in folded && it != r.post.id } }
}
