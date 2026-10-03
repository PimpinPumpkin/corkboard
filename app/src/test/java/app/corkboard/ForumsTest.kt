package app.corkboard

import app.corkboard.data.ForumParsers
import app.corkboard.data.ForumPost
import app.corkboard.data.ForumUrls
import app.corkboard.data.ThreadLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/** The fixtures are real forum pages, cut down, with the post text and handles replaced. */
class ForumsTest {
    private fun fixture(name: String): String = javaClass.classLoader!!.getResource(name)!!.readText()
    private val today = LocalDate.of(2026, 10, 2)

    @Test
    fun `the forum list comes from the front page's picker`() {
        val forums = ForumParsers.forums(fixture("forum_index.html"))
        assertTrue(forums.size > 90)
        assertEquals("automotive", forums.first { it.id == 5 }.name)
        assertEquals("death & dying", forums.first { it.id == 130 }.name)
        // The unnamed placeholder at the top is not a forum.
        assertTrue(forums.none { it.name.startsWith("forum id") })
    }

    @Test
    fun `a forum page lists threads with their reply trees`() {
        val page = ForumParsers.threads(fixture("forum_threads.html"), today)
        assertEquals(3, page.threads.size)
        assertEquals(269183L, page.older)
        val first = page.threads.first()
        assertEquals(346841824L, first.root.id)
        assertEquals("Sample post 1", first.root.title)
        assertEquals("handle1", first.root.handle)
        assertEquals(0, first.root.depth)
        assertEquals(2, first.replies)
        assertEquals(listOf(0, 1, 2), first.posts.map { it.depth })
        // A time without a date is today on the site's clock.
        assertEquals(ZonedDateTime.of(2026, 10, 2, 16, 10, 0, 0, ZoneId.of("America/Los_Angeles")).toEpochSecond(), first.root.postedAt)
        assertEquals(33, page.threads[1].posts.size)
    }

    @Test
    fun `a thread page names its forum and keeps every post`() {
        val t = ForumParsers.thread(fixture("forum_thread.html"), today)
        assertEquals(5, t.forum!!.id)
        assertEquals("Automotive", t.forum!!.name)
        assertEquals(14, t.posts.size)
        assertEquals(listOf(0, 1, 1, 2, 1, 2, 1, 2, 3, 4, 4, 2, 1, 1), t.posts.map { it.depth })
        // The post the thread was opened from is marked on the site; it parses like any other.
        assertEquals("Sample post 99", t.posts[9].title)
        // Anonymous posts have no handle, and a § means there is no body to fetch.
        assertNull(t.posts[4].handle)
        assertFalse(t.posts[12].hasBody)
        assertEquals("Sample post 12", t.posts[12].title)
        assertTrue(t.posts[11].hasBody)
    }

    @Test
    fun `a post body keeps its lines and its links as text`() {
        val body = ForumParsers.body(fixture("forum_post.html"))
        assertEquals("First line of the body & more.\n\nSee https://example.com/page?a=1&b=2 for details.", body)
    }

    private fun post(id: Long, depth: Int) = ForumPost(id, depth, "p$id", null, 0, false)

    @Test
    fun `replies only step in where a conversation splits`() {
        // 1 has two answers (2 and 5); 2 has one answer (3), which has two (4 and 6).
        val posts = listOf(post(1, 0), post(2, 1), post(3, 2), post(4, 3), post(6, 3), post(5, 1))
        val rows = ThreadLayout.rows(posts)
        assertEquals(listOf(0, 1, 1, 2, 2, 1), rows.map { it.indent })
        assertEquals(listOf(2L, 4L, 6L, 5L), rows.filter { it.startsBranch }.map { it.post.id })
        assertEquals(listOf(listOf(), listOf(2L), listOf(2L), listOf(2L, 4L), listOf(2L, 6L), listOf(5L)), rows.map { it.branches })
        // 6 answers 3, which is not the row above it; 3 answers the row above, so says nothing.
        assertEquals(3L, rows[4].replyTo!!.id)
        assertNull(rows[2].replyTo)
        assertEquals(3, rows[1].hidden)

        // Folding branch 2 leaves its first post; everything under it goes.
        assertEquals(listOf(1L, 2L, 5L), ThreadLayout.visible(rows, setOf(2L)).map { it.post.id })
        // A branch of one post has nothing under it to fold.
        assertEquals(listOf(1L, 2L, 3L, 4L, 6L, 5L), ThreadLayout.visible(rows, setOf(6L)).map { it.post.id })
    }

    @Test
    fun `a straight back and forth never indents`() {
        val rows = ThreadLayout.rows((0..6).map { post(it.toLong(), it) })
        assertTrue(rows.all { it.indent == 0 })
        assertTrue(rows.all { it.replyTo == null })
    }

    @Test
    fun `forum addresses`() {
        assertEquals("https://forums.craigslist.org/?act=DF&forumID=5", ForumUrls.threads(5))
        assertEquals("https://forums.craigslist.org/?act=DF&forumID=5&last_thread=269183", ForumUrls.threads(5, 269183))
        assertEquals("https://forums.craigslist.org/?act=showThread&ID=7", ForumUrls.thread(7))
        assertEquals("https://forums.craigslist.org/?act=Q&ID=7", ForumUrls.post(7))
    }
}
