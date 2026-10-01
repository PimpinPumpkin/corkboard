package app.corkboard

import app.corkboard.data.Calibration
import app.corkboard.data.ClUrls
import app.corkboard.data.Parsers
import app.corkboard.data.SearchQuery
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CalibrationTest {
    @After fun reset() = Calibration.reset()

    private val cars = SearchQuery(areaHost = "sfbay", areaName = "SF bay area", category = "cta", categoryName = "Cars & trucks")

    @Test
    fun `a newer calibration retunes the requests and the row tags`() {
        assertTrue(Calibration.adopt("""{"version":2,"api":"v9","quick":120,"chunk":500,"tagUuid":21}"""))
        assertTrue(ClUrls.search(cars, 0).startsWith("https://sapi.craigslist.org/web/v9/postings/search/full?batch=0-0-120-0-0&"))
        val body = """{"data":{"decode":{"minPostingId":100,"minPostedDate":50,"version":2,"locations":[0,[1,"sfbay"]],"locationDescriptions":[0],"neighborhoods":[0]},
            "totalResultCount":1,"filters":[],"items":[[1,2,145,10,"1:0~1~1","0CI0t2",[21,"newstyle"],[13,"oldstyle"],"A car"]]},"errors":[]}"""
        assertEquals("newstyle", Parsers.search(body).items[0].uuid)
    }

    @Test
    fun `older, broken or unreasonable calibrations are ignored`() {
        assertFalse(Calibration.adopt("""{"version":1,"api":"v9"}"""))
        assertFalse(Calibration.adopt("""{"version":5,"api":"v8/../../evil"}"""))
        assertFalse(Calibration.adopt("""{"version":5,"api":"https://evil.example"}"""))
        assertFalse(Calibration.adopt("""{"version":5,"quick":0}"""))
        assertFalse(Calibration.adopt("""{"version":5,"tagUuid":4}"""))
        assertFalse(Calibration.adopt("not json"))
        assertEquals("v8", Calibration.current.api)
        assertNull(Calibration.current.notice)
    }

    @Test
    fun `the file in the repository is the calibration compiled in`() {
        val repo = java.io.File("../calibration.json").readText()
        // Same version, so not adopted over the defaults; but it must parse to exactly them.
        assertFalse(Calibration.adopt(repo))
        assertTrue(Calibration.adopt(repo.replace("\"version\": 1", "\"version\": 2")))
        assertEquals(Calibration().copy(version = 2), Calibration.current)
    }
}
