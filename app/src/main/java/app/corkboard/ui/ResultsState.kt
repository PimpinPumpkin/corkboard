package app.corkboard.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.corkboard.data.ClApi
import app.corkboard.data.Listing
import app.corkboard.data.SearchPage
import app.corkboard.data.SearchQuery
import app.corkboard.data.SearchSession
import app.corkboard.data.Store
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** One results screen's search: the query as edited so far, what came back, and what is loading. */
class ResultsState(private val api: ClApi, private val store: Store, initial: SearchQuery) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null
    private var session: SearchSession? = null

    var query by mutableStateOf(initial)
        private set
    var items by mutableStateOf<List<Listing>>(emptyList())
        private set
    var page by mutableStateOf<SearchPage?>(null)
        private set
    var loading by mutableStateOf(false)
        private set
    var loadingMore by mutableStateOf(false)
        private set
    var hasMore by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    init {
        run(initial)
    }

    fun run(q: SearchQuery) {
        query = q
        job?.cancel()
        val s = SearchSession(api, q)
        session = s
        loading = true
        error = null
        job = scope.launch {
            try {
                val p = s.start()
                page = p
                items = s.visible()
                hasMore = s.hasMore
                // Looking at a saved search counts as having seen what is in it.
                store.findSaved(q)?.let { saved ->
                    val newest = p.items.maxOfOrNull { it.postingId } ?: saved.newestSeen
                    store.updateSaved(saved.id) { it.copy(newestSeen = maxOf(it.newestSeen, newest), unseen = 0) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: "Something went wrong"
                items = emptyList()
                hasMore = false
            } finally {
                if (session === s) loading = false
            }
        }
    }

    fun retry() = run(query)

    fun loadMore() {
        val s = session ?: return
        if (loading || loadingMore || !hasMore) return
        loadingMore = true
        scope.launch {
            try {
                s.loadMore()
                if (session === s) {
                    items = s.visible()
                    hasMore = s.hasMore
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The rows already on screen are still good; just stop asking for more.
                if (session === s) hasMore = false
            } finally {
                loadingMore = false
            }
        }
    }

    fun close() = scope.cancel()
}
