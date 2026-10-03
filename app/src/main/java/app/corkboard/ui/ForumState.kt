package app.corkboard.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.corkboard.data.ClApi
import app.corkboard.data.Forum
import app.corkboard.data.ForumPost
import app.corkboard.data.ForumThread
import app.corkboard.data.ThreadLayout
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * One forum's thread list as it stands: what has loaded, how far back, and where the list was
 * scrolled to, so coming back from a thread lands where the user left.
 */
class ForumState(private val api: ClApi, val forum: Forum) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null
    val list = LazyListState()

    var threads by mutableStateOf<List<ForumThread>>(emptyList())
        private set
    var older by mutableStateOf<Long?>(null)
        private set
    var loading by mutableStateOf(false)
        private set
    var refreshing by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    init {
        load(more = false)
    }

    fun refresh() {
        refreshing = true
        load(more = false)
    }

    /** The next page back, when the end of the list comes into view. */
    fun more() {
        if (!loading && older != null && error == null) load(more = true)
    }

    private fun load(more: Boolean) {
        job?.cancel()
        loading = true
        error = null
        job = scope.launch {
            try {
                val page = api.forumThreads(forum.id, if (more) older else null)
                // Threads move between pages while people post, so a thread can come round twice.
                threads = if (more) (threads + page.threads).distinctBy { it.root.id } else page.threads
                older = page.older
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: "Could not load this forum"
            } finally {
                loading = false
                refreshing = false
            }
        }
    }

    fun close() = scope.cancel()
}

/**
 * One thread: its posts laid out, which branches are folded, and the bodies fetched so far. A
 * body is fetched when its post first comes on screen, two at a time at most, the way a person
 * opening posts one after another would.
 */
class ThreadState(private val api: ClApi, val rootId: Long, title: String, forum: Forum?) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val gate = Semaphore(2)
    private val asked = HashSet<Long>()
    val list = LazyListState()

    var title by mutableStateOf(title)
        private set
    var forum by mutableStateOf(forum)
        private set
    var rows by mutableStateOf<List<ThreadLayout.Row>>(emptyList())
        private set
    var folded by mutableStateOf<Set<Long>>(emptySet())
        private set
    var loading by mutableStateOf(false)
        private set
    var refreshing by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    /** Fetched bodies by post id. A post that turned out to have none maps to an empty string. */
    val bodies = mutableStateMapOf<Long, String>()

    val posts: List<ForumPost> get() = rows.map { it.post }

    init {
        load()
    }

    fun refresh() {
        refreshing = true
        load()
    }

    private fun load() {
        loading = true
        error = null
        scope.launch {
            try {
                val page = api.forumThread(rootId)
                page.forum?.let { forum = it }
                page.posts.firstOrNull()?.let { title = it.title }
                rows = ThreadLayout.rows(page.posts)
                // A refresh asks again for bodies that failed; ones already read do not change.
                asked.retainAll(bodies.keys)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: "Could not load this thread"
            } finally {
                loading = false
                refreshing = false
            }
        }
    }

    fun body(post: ForumPost) {
        if (!post.hasBody || !asked.add(post.id)) return
        scope.launch {
            try {
                bodies[post.id] = gate.withPermit { api.forumBody(post.id) }.orEmpty()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                asked.remove(post.id)
            }
        }
    }

    fun toggle(branch: Long) {
        folded = if (branch in folded) folded - branch else folded + branch
    }

    fun close() = scope.cancel()
}
