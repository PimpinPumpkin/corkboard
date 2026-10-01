package app.corkboard.data

import app.corkboard.net.BrowserHeaders
import app.corkboard.net.Http
import app.corkboard.ui.components.Tiles

/**
 * The app checking itself against the live site: every kind of request it makes, made once, and
 * the answers held to what the rest of the code assumes about them. This is what tells a change
 * on craigslist's side (the calibration needs fixing) from a change on ours (a new Cronet), and
 * it is what the project's automation runs before letting either one through.
 */
object SelfCheck {
    sealed interface Result {
        data object Pass : Result
        /** The site would not talk to this network address at all, which says nothing about the app. */
        data class Blocked(val why: String) : Result
        data class Fail(val step: String, val why: String) : Result
    }

    private class Blockage(message: String) : Exception(message)

    suspend fun run(api: ClApi, http: Http, log: (String) -> Unit): Result {
        var step = "start"
        fun check(ok: Boolean, what: () -> String) { if (!ok) throw IllegalStateException(what()) }
        return try {
            step = "areas"
            val areas = api.areas()
            check(areas.size > 400) { "only ${areas.size} areas" }
            val sf = areas.firstOrNull { it.hostname == "sfbay" } ?: throw IllegalStateException("no sfbay in the area list")
            check(sf.subAreas.isNotEmpty()) { "sfbay has no sub-areas" }
            log("areas ok: ${areas.size}")

            step = "search"
            val query = SearchQuery(areaHost = "sfbay", areaName = sf.name, category = "cta", categoryName = "cars")
            val session = SearchSession(api, query, fresh = true)
            val page = try {
                session.start()
            } catch (e: ApiException) {
                if (e.message.orEmpty().contains("refusing")) throw Blockage(e.message.orEmpty()) else throw e
            }
            val rows = session.visible()
            check(page.total > 1000) { "total ${page.total}" }
            check(rows.size >= 300) { "only ${rows.size} complete rows of ${page.items.size}" }
            check(rows.all { !it.title.isNullOrBlank() && !it.uuid.isNullOrBlank() && it.postingId > 1_000_000_000L }) { "rows missing title, uuid or id" }
            check(rows.count { it.imageIds.isNotEmpty() } > rows.size / 2) { "most rows have no images" }
            check(rows.count { it.priceText != null } > rows.size / 2) { "most rows have no price text" }
            check(rows.count { it.odometer != null } > rows.size / 4) { "car rows have no odometer" }
            check(rows.count { it.place.isNotBlank() } > rows.size / 2) { "most rows have no place" }
            val now = System.currentTimeMillis() / 1000
            check(rows.all { it.postedAt in (now - 90 * 86_400L)..(now + 86_400L) }) { "posted dates out of range" }
            check(rows.all { it.areaId > 0 && it.distanceUnit != null }) { "rows missing their site or its units" }
            log("search ok: ${rows.size} rows of ${page.total}")

            step = "filters"
            val f = page.filters
            check(f.any { it is Filter.Range && it.minName == "min_price" }) { "no price range" }
            check(f.any { it is Filter.Range && it.minName == "min_auto_year" }) { "no model year range" }
            check(f.any { it is Filter.Multi && it.name == "auto_transmission" && it.options.size >= 2 }) { "no transmission choice" }
            check(f.any { it is Filter.Text && it.autocomplete != null }) { "no make and model field" }
            check(f.any { it is Filter.Toggle }) { "no checkboxes" }
            check(page.sortOptions.any { it.value == "priceasc" }) { "no price sort" }
            check(page.subareas.isNotEmpty()) { "no sub-areas offered" }
            log("filters ok: ${f.size}")

            step = "filtered search"
            val narrowed = api.search(query.with("auto_transmission", listOf("1")).with("min_auto_year", listOf("2015")), 0, fresh = true)
            check(narrowed.total in 1 until page.total) { "a filter did not narrow the results: ${narrowed.total} of ${page.total}" }

            step = "paging"
            check(session.hasMore) { "nothing more to load" }
            check(session.loadMore()) { "the next batch did not load" }
            check(session.visible().size > rows.size) { "the next batch added no rows" }
            log("paging ok: ${session.visible().size} rows")

            step = "listing"
            val first = rows.first { it.imageIds.isNotEmpty() }
            val posting = Parsers.posting(api.postingRaw(first.uuid!!, fresh = true))
            check(posting.postingId == first.postingId) { "listing id ${posting.postingId} is not the row's ${first.postingId}" }
            check(posting.title.isNotBlank() && posting.bodyHtml.isNotBlank()) { "listing has no title or body" }
            check(posting.attributes.isNotEmpty()) { "listing has no attributes" }
            check(posting.imageIds.isNotEmpty()) { "listing has no images" }
            check(posting.postedAt > 0 && posting.updatedAt >= posting.postedAt) { "listing dates are off" }
            check(posting.lat != null && posting.lon != null) { "listing has no coordinates" }
            log("listing ok: ${posting.attributes.size} attributes")

            step = "gone listing"
            check(runCatching { api.postingRaw("zzzzzzzzzzzzzzzzzzzzzz") }.exceptionOrNull() is GoneException) { "a missing listing is not reported as gone" }

            step = "image"
            val image = http.get(Images.url(first.imageIds.first(), Images.THUMB), BrowserHeaders.Kind.Image)
            check(image.ok && image.body.size > 1000) { "image answered ${image.code}, ${image.body.size} bytes" }
            val tile = http.get(Tiles.url(Tiles.x(posting.lon!!).toInt(), Tiles.y(posting.lat!!).toInt()), BrowserHeaders.Kind.Image)
            check(tile.ok && tile.body.size > 200) { "map tile answered ${tile.code}" }

            step = "suggestions"
            check(api.suggest("makemodel", "toyota c").isNotEmpty()) { "no make and model suggestions" }

            step = "location"
            check(api.locate(37.77, -122.41)?.areaId == sf.id) { "San Francisco is not located in sfbay" }
            check(api.locatePostal("94103", "sfbay")?.areaId == sf.id) { "94103 is not located in sfbay" }
            log("everything ok")
            Result.Pass
        } catch (e: Blockage) {
            Result.Blocked(e.message.orEmpty())
        } catch (e: java.io.IOException) {
            // The request never got an answer: that is the network, and says nothing about the app.
            Result.Blocked("no answer at $step: ${e.message}")
        } catch (e: Exception) {
            Result.Fail(step, "${e.javaClass.simpleName}: ${e.message}")
        }
    }
}
