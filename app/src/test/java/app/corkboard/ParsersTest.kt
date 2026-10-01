package app.corkboard

import app.corkboard.data.ApiException
import app.corkboard.data.Filter
import app.corkboard.data.Images
import app.corkboard.data.Parsers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The fixtures are real responses, cut down to a few rows with the listing text replaced. */
class ParsersTest {
    private fun fixture(name: String): String = javaClass.classLoader!!.getResource(name)!!.readText()

    @Test
    fun `car search rows are unpacked`() {
        val page = Parsers.search(fixture("search_cta.json"))
        assertEquals(301, page.total)
        assertEquals(6, page.items.size)
        val first = page.items.first()
        // Ids and dates are offsets from the decode table's minimums.
        assertEquals(7958795223L + 17015235L, first.postingId)
        assertEquals(1788275238L + 2538498L, first.postedAt)
        assertEquals(145, first.categoryId)
        assertEquals(29000L, first.price)
        assertEquals("$29,000", first.priceText)
        assertEquals("Sample listing 0", first.title)
        assertEquals("fUrzJ5PhEyjBs9xUVCp7Ru", first.uuid)
        assertEquals("sample-listing-0", first.slug)
        assertEquals(22000L, first.odometer)
        assertEquals(13, first.imageIds.size)
        assertEquals("sfbay", first.hostname)
        assertEquals("eby", first.subarea)
        assertEquals("hood 1", first.place)
        assertEquals(37.5308, first.lat!!, 1e-6)
        assertTrue(first.hasDetails)
    }

    @Test
    fun `car filters come from the response`() {
        val page = Parsers.search(fixture("search_cta.json"))
        val names = page.filters.map { it.label }
        assertTrue("model year" in names)
        assertTrue("odometer" in names)
        assertTrue("title status" in names)
        // Search text, sort and sub-area have their own controls and stay out of the sheet.
        assertFalse(page.filters.any { it is Filter.Text && it.name == "query" })
        assertFalse(page.filters.any { it is Filter.Select && it.name == "subarea" })

        val year = page.filters.filterIsInstance<Filter.Range>().first { it.label == "model year" }
        assertEquals("min_auto_year", year.minName)
        assertEquals("max_auto_year", year.maxName)
        val price = page.filters.filterIsInstance<Filter.Range>().first { it.label == "price" }
        assertEquals("$", price.prefix)
        val transmission = page.filters.filterIsInstance<Filter.Multi>().first { it.name == "auto_transmission" }
        assertEquals(listOf("manual", "automatic", "other"), transmission.options.map { it.label })
        assertEquals("1", transmission.options.first().value)
        val makeModel = page.filters.filterIsInstance<Filter.Text>().first { it.name == "auto_make_model" }
        assertEquals("makemodel", makeModel.autocomplete)
        // Groups of checkboxes are flattened into toggles.
        val titlesOnly = page.filters.filterIsInstance<Filter.Toggle>().first { it.name == "srchType" }
        assertEquals("T", titlesOnly.value)

        assertEquals(listOf("date", "dateoldest", "priceasc", "pricedsc", "dist"), page.sortOptions.map { it.value })
        assertEquals("date", page.sort)
        assertEquals(6, page.subareas.size)
        assertEquals("eby", page.subareas.first { it.label == "east bay" }.value)
    }

    @Test
    fun `housing rows carry bedrooms`() {
        val page = Parsers.search(fixture("search_apa.json"))
        val first = page.items.first()
        assertEquals("Sample listing 0", first.title)
        assertEquals(1, first.bedrooms)
        assertNull(first.sqft)
        assertTrue(page.filters.any { it is Filter.Range && it.minName == "min_bedrooms" })
        assertTrue(page.filters.any { it is Filter.Toggle && it.name == "pets_cat" })
    }

    @Test
    fun `full result list is digests with paging keys`() {
        val page = Parsers.search(fixture("search_full.json"))
        assertNotNull(page.cacheId)
        assertTrue(page.cacheTs > 0)
        assertNotNull(page.maxPostedTs)
        val digest = page.items.first()
        assertNull(digest.title)
        assertFalse(digest.hasDetails)
        // A trailing negative number groups duplicates; it must not be mistaken for anything else.
        assertTrue(page.items.all { it.postingId > page.minPostingId })
    }

    @Test
    fun `details batch is keyed by posting id`() {
        val details = Parsers.batch(fixture("search_batch.json"), fallbackMinPostingId = 0)
        assertEquals(5, details.size)
        val first = details[7925551486L + 34357568L]!!
        assertEquals("Sample batch listing 0", first.title)
        assertEquals(10, first.imageIds.size)
        assertEquals("sample-batch-listing-0", first.slug)
        assertNotNull(first.uuid)
    }

    @Test
    fun `listing detail is read`() {
        val p = Parsers.posting(fixture("posting.json"))
        assertEquals(7975810458L, p.postingId)
        assertEquals("fUrzJ5PhEyjBs9xUVCp7Ru", p.uuid)
        assertEquals("2021 Sample Roadster", p.title)
        assertEquals("$29,000", p.priceText)
        assertEquals(13, p.imageIds.size)
        assertEquals("manual", p.attributes.first { it.label == "transmission" }.value)
        assertTrue(p.bodyHtml.contains("<br>"))
        assertEquals(37.5308, p.lat!!, 1e-6)
        assertEquals("fremont / union city / newark, east bay", p.place)
    }

    @Test
    fun `areas are read with their sub-areas`() {
        val areas = Parsers.areas(fixture("areas.json"))
        val sf = areas.first { it.hostname == "sfbay" }
        assertEquals(1, sf.id)
        assertEquals("SF bay area", sf.name)
        assertEquals("CA", sf.region)
        assertTrue(sf.subAreas.any { it.abbr == "eby" && it.name == "east bay" })
    }

    @Test
    fun `the site's own error message is surfaced`() {
        val body = """{"apiVersion":8,"data":{},"errors":[{"code":0,"links":[],"message":"suggestWeb unrecognized parameter 'cat'"}]}"""
        val e = runCatching { Parsers.suggestions(body) }.exceptionOrNull()
        assertTrue(e is ApiException)
        assertEquals("suggestWeb unrecognized parameter 'cat'", e!!.message)
    }

    @Test
    fun `a gone listing says so`() {
        val e = runCatching { Parsers.posting("""{"data":{"items":[]},"errors":[]}""") }.exceptionOrNull()
        assertTrue(e is ApiException)
    }

    @Test
    fun `garbage is an error, not a crash`() {
        assertTrue(runCatching { Parsers.search("<html>blocked</html>") }.exceptionOrNull() is ApiException)
    }

    @Test
    fun `image ids become urls`() {
        assertEquals("https://images.craigslist.org/00w0w_TqEQX1TUvp_0ny0hJ_600x450.jpg", Images.url("3:00w0w_TqEQX1TUvp_0ny0hJ", Images.MEDIUM))
    }
}
