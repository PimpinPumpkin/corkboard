package app.corkboard

import app.corkboard.data.ClUrls
import app.corkboard.data.Parsers
import app.corkboard.data.SearchQuery
import app.corkboard.net.BrowserHeaders
import app.corkboard.net.CookieStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RequestShapeTest {
    private val cars = SearchQuery(areaHost = "sfbay", areaName = "SF bay area", category = "cta", categoryName = "Cars & trucks")

    @Test
    fun `a plain category search`() {
        assertEquals(
            "https://sapi.craigslist.org/web/v8/postings/search/full?batch=0-0-360-0-0&cc=US&lang=en&searchPath=area%2Fsfbay&cat=cta",
            ClUrls.search(cars, sortId = 0),
        )
    }

    @Test
    fun `filters are sent sorted, multi-selects repeated`() {
        val q = cars.with("min_auto_year", listOf("2015")).with("auto_transmission", listOf("1", "2")).with("query", listOf("miata rf"))
        assertEquals(
            "https://sapi.craigslist.org/web/v8/postings/search/full?batch=0-0-360-0-0&cc=US&lang=en&searchPath=area%2Fsfbay" +
                "&auto_transmission=1&auto_transmission=2&cat=cta&min_auto_year=2015&query=miata%20rf",
            ClUrls.search(q, sortId = 0),
        )
    }

    @Test
    fun `a sub-area replaces the area in the path`() {
        assertTrue(ClUrls.search(cars.copy(subarea = "eby"), 0).contains("searchPath=subarea%2Feby&"))
    }

    @Test
    fun `sorting travels in the batch key too`() {
        val q = cars.with("sort", listOf("priceasc"))
        assertEquals(4, ClUrls.sortId(q.sort))
        assertTrue(ClUrls.search(q, ClUrls.sortId(q.sort)).contains("batch=0-0-360-4-0"))
        assertEquals(0, ClUrls.sortId(null))
    }

    @Test
    fun `clearing a filter removes its parameter`() {
        val q = cars.with("min_price", listOf("500")).with("min_price", listOf(""))
        assertTrue(q.params.isEmpty())
        assertEquals(0, q.filterCount)
        assertEquals(1, cars.with("min_price", listOf("500")).with("query", listOf("x")).with("sort", listOf("date")).filterCount)
    }

    @Test
    fun `the details batch is addressed by position and cache`() {
        val full = Parsers.search(javaClass.classLoader!!.getResource("search_full.json")!!.readText())
        val url = ClUrls.batch(full, sortId = 1, start = 1080)
        assertTrue(url.startsWith("https://sapi.craigslist.org/web/v8/postings/search/batch?batch=0-1080-1080-1-0-${full.maxPostedTs}-${full.cacheTs}&cacheId="))
        assertTrue(url.endsWith("&cc=US&lang=en"))
    }

    @Test
    fun `listing urls`() {
        assertEquals("https://rapi.craigslist.org/web/v8/postings/abc123?cc=US&lang=en", ClUrls.posting("abc123"))
        assertEquals("https://www.craigslist.org/view/d/some-slug/abc123", ClUrls.web("abc123", "some-slug"))
        assertEquals("https://www.craigslist.org/view/abc123", ClUrls.web("abc123", null))
        assertEquals("https://sapi.craigslist.org/web/v8/suggest/makemodel?cc=US&lang=en&query=mazda%20mi", ClUrls.suggest("makemodel", "mazda mi"))
    }

    @Test
    fun `a postal search replaces the sub-area and stays out of the filter count`() {
        val q = cars.copy(subarea = "eby").near("94103", "5")
        assertNull(q.subarea)
        assertEquals(0, q.filterCount)
        assertTrue(ClUrls.search(q, 0).endsWith("searchPath=area%2Fsfbay&cat=cta&postal=94103&search_distance=5"))
        assertEquals("10", cars.near("H2X 1Y4", "").distance)
        assertTrue(ClUrls.search(cars.near("H2X 1Y4", "10"), 0).contains("postal=H2X%201Y4"))
        val back = q.with("sort", listOf("dist")).near(null, null)
        assertNull(back.sort)
        assertEquals("priceasc", q.with("sort", listOf("priceasc")).near(null, null).sort)
        assertNull(back.postal)
        assertNull(back.distance)
    }

    @Test
    fun `country and language follow the visitor, within what the site speaks`() {
        try {
            ClUrls.setLocale("mx", "ES")
            assertTrue(ClUrls.search(cars, 0).contains("&cc=MX&lang=es&"))
            assertEquals("https://rapi.craigslist.org/web/v8/postings/abc?cc=MX&lang=es", ClUrls.posting("abc"))
            // A language the site has no translation for falls back to English; so does nonsense.
            ClUrls.setLocale("NL", "nl")
            assertTrue(ClUrls.search(cars, 0).contains("&cc=NL&lang=en&"))
            ClUrls.setLocale("", "x&y=1")
            assertTrue(ClUrls.search(cars, 0).contains("&cc=US&lang=en&"))
        } finally {
            ClUrls.setLocale("US", "en")
        }
    }

    @Test
    fun `the front door's redirect names the nearest site`() {
        assertEquals("chicago", ClUrls.hostOf("https://www.craigslist.org/area/chicago"))
        assertEquals("sfbay", ClUrls.hostOf("https://sfbay.craigslist.org/"))
        assertNull(ClUrls.hostOf("https://www.craigslist.org/about/sites"))
        assertNull(ClUrls.hostOf("https://example.com/area/chicago"))
    }

    @Test
    fun `a point is looked up by rounded coordinates, and read back as a site and postal code`() {
        assertEquals("https://rapi.craigslist.org/web/v8/locations?cc=US&lang=en&lat=37.77&lon=-122.41", ClUrls.locate(37.77, -122.41))
        val found = Parsers.located("""{"data":{"items":[{"areaId":1,"city":"San Francisco","country":"US","lat":37.77,"lon":-122.41,"postal":"94103","radius":15,"region":"CA","subareaId":1,"url":"sfbay.craigslist.org"}],"lang":"en"},"errors":[]}""")!!
        assertEquals(1, found.areaId)
        assertEquals("San Francisco", found.city)
        assertEquals("94103", found.postal)
        assertNull(Parsers.located("""{"data":{"items":[]},"errors":[]}"""))
        // A search answers with the place it ran for; a postal code the site ignored comes back empty.
        val searched = Parsers.searchedPlace(javaClass.classLoader!!.getResource("search_cta.json")!!.readText())!!
        assertEquals(1, searched.areaId)
        assertEquals("", searched.postal)
    }

    @Test
    fun `a page request carries no referrer`() {
        val h = BrowserHeaders.headers(BrowserHeaders.Kind.Page, "155.0.8059.16", "en-US,en;q=0.9").toMap()
        assertEquals("navigate", h["Sec-Fetch-Mode"])
        assertEquals("none", h["Sec-Fetch-Site"])
        assertNull(h["Referer"])
        assertNull(h["Origin"])
    }

    @Test
    fun `map tiles come from the site's own tile servers`() {
        // Portland, Oregon at zoom 13 is tile 1304, 2930; one zoom in doubles both.
        assertEquals(1304, app.corkboard.ui.components.Tiles.x(-122.67, 13).toInt())
        assertEquals(2930, app.corkboard.ui.components.Tiles.y(45.52, 13).toInt())
        assertEquals("https://map4.craigslist.org/t09/13/1304/2930.png", app.corkboard.ui.components.Tiles.url(1304, 2930, 13))
    }

    @Test
    fun `craigslist links open as listings, and nothing else does`() {
        val l = ClUrls.listingFromLink("https://www.craigslist.org/view/d/sample-1999-sedan-low-miles/abcDEF0123456789abcdef")!!
        assertEquals("abcDEF0123456789abcdef", l.uuid)
        assertEquals("sample-1999-sedan-low-miles", l.slug)
        assertEquals("zyxWVU9876543210zyxwvu", ClUrls.listingFromLink("Look at this https://www.craigslist.org/view/zyxWVU9876543210zyxwvu?lang=en")!!.uuid)
        assertNull(ClUrls.listingFromLink("https://www.craigslist.org/about/help"))
        assertNull(ClUrls.listingFromLink("https://evil.example/www.craigslist.org/view/abcDEF0123456789abcdef"))
        assertNull(ClUrls.listingFromLink("just some shared text"))
    }

    // ---- headers ----

    @Test
    fun `client hints match real Chrome releases`() {
        assertEquals("\"Not_A Brand\";v=\"8\", \"Chromium\";v=\"120\", \"Google Chrome\";v=\"120\"", BrowserHeaders.secChUa(120))
        assertEquals("\"Chromium\";v=\"124\", \"Google Chrome\";v=\"124\", \"Not-A.Brand\";v=\"99\"", BrowserHeaders.secChUa(124))
    }

    @Test
    fun `user agent is Chrome on Android at the Cronet version`() {
        assertEquals(
            "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/155.0.0.0 Mobile Safari/537.36",
            BrowserHeaders.userAgent("155.0.8059.16"),
        )
    }

    @Test
    fun `api requests look like the search page's own`() {
        val h = BrowserHeaders.headers(BrowserHeaders.Kind.Api, "155.0.8059.16", "en-US,en;q=0.9").toMap()
        assertEquals("\"Android\"", h["sec-ch-ua-platform"])
        assertEquals("?1", h["sec-ch-ua-mobile"])
        assertEquals("https://www.craigslist.org", h["Origin"])
        assertEquals("https://www.craigslist.org/", h["Referer"])
        assertEquals("same-site", h["Sec-Fetch-Site"])
        assertEquals("cors", h["Sec-Fetch-Mode"])
        assertEquals("empty", h["Sec-Fetch-Dest"])
        val image = BrowserHeaders.headers(BrowserHeaders.Kind.Image, "155.0.8059.16", "en-US,en;q=0.9").toMap()
        assertEquals("image", image["Sec-Fetch-Dest"])
        assertNull(image["Origin"])
    }

    @Test
    fun `accept-language is built the way Chrome builds it`() {
        assertEquals("en-US,en;q=0.9", BrowserHeaders.acceptLanguage(listOf("en-US")))
        assertEquals("en-US,en;q=0.9,es-MX;q=0.8,es;q=0.7", BrowserHeaders.acceptLanguage(listOf("en-US", "es-MX")))
        assertEquals("en-US,en;q=0.9", BrowserHeaders.acceptLanguage(emptyList()))
    }

    // ---- cookies ----

    @Test
    fun `the site cookie is kept and sent back to every subdomain`() {
        val jar = CookieStore(null)
        jar.save("sapi.craigslist.org", "cl_b=4|abc|123;path=/;domain=.craigslist.org;secure;expires=Fri, 01-Jan-2038 00:00:00 GMT")
        assertEquals("cl_b=4|abc|123", jar.header("rapi.craigslist.org"))
        assertEquals("cl_b=4|abc|123", jar.header("sapi.craigslist.org"))
        assertNull(jar.header("example.com"))
        assertNull(jar.header("notcraigslist.org"))
    }

    @Test
    fun `a cookie cannot be set for someone else's domain`() {
        val jar = CookieStore(null)
        jar.save("sapi.craigslist.org", "x=1; Domain=example.com")
        assertNull(jar.header("example.com"))
        assertEquals("x=1", jar.header("sapi.craigslist.org"))
    }

    @Test
    fun `expired and emptied cookies go away`() {
        val jar = CookieStore(null)
        jar.save("sapi.craigslist.org", "a=1; Max-Age=10", now = 1_000)
        assertEquals("a=1", jar.header("sapi.craigslist.org", now = 5_000))
        assertNull(jar.header("sapi.craigslist.org", now = 20_000))
        jar.save("sapi.craigslist.org", "b=2")
        jar.save("sapi.craigslist.org", "b=; Max-Age=0")
        assertNull(jar.header("sapi.craigslist.org", now = 20_000))
    }
}
