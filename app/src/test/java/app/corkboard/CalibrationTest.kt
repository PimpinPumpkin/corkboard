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
        assertTrue(Calibration.adoptUnsigned("""{"version":2,"api":"v9","quick":120,"chunk":500,"tagUuid":21}"""))
        assertTrue(ClUrls.search(cars, 0).startsWith("https://sapi.craigslist.org/web/v9/postings/search/full?batch=0-0-120-0-0&"))
        val body = """{"data":{"decode":{"minPostingId":100,"minPostedDate":50,"version":2,"locations":[0,[1,"sfbay"]],"locationDescriptions":[0],"neighborhoods":[0]},
            "totalResultCount":1,"filters":[],"items":[[1,2,145,10,"1:0~1~1","0CI0t2",[21,"newstyle"],[13,"oldstyle"],"A car"]]},"errors":[]}"""
        assertEquals("newstyle", Parsers.search(body).items[0].uuid)
    }

    @Test
    fun `older, broken or unreasonable calibrations are ignored`() {
        assertFalse(Calibration.adoptUnsigned("""{"version":1,"api":"v9"}"""))
        assertFalse(Calibration.adoptUnsigned("""{"version":5,"api":"v8/../../evil"}"""))
        assertFalse(Calibration.adoptUnsigned("""{"version":5,"api":"https://evil.example"}"""))
        assertFalse(Calibration.adoptUnsigned("""{"version":5,"quick":0}"""))
        assertFalse(Calibration.adoptUnsigned("""{"version":5,"tagUuid":4}"""))
        assertFalse(Calibration.adoptUnsigned("not json"))
        assertEquals("v8", Calibration.current.api)
        assertNull(Calibration.current.notice)
    }

    @Test
    fun `the file in the repository is the calibration compiled in, and is signed`() {
        val repo = java.io.File("../calibration.json").readText()
        val signature = java.io.File("../calibration.json.sig").readText()
        // If this fails after an edit to calibration.json: run scripts/sign-calibration.sh.
        assertTrue(Calibration.verified(repo, signature))
        // Same version as the defaults, so it is not adopted over them; but it must parse to exactly them.
        assertFalse(Calibration.adopt(repo, signature))
        assertTrue(Calibration.adoptUnsigned(repo.replace("\"version\": 1", "\"version\": 2")))
        assertEquals(Calibration().copy(version = 2), Calibration.current)
    }

    @Test
    fun `only this project's signature is accepted`() {
        val keys = java.security.KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair()
        val public = java.util.Base64.getEncoder().encodeToString(keys.public.encoded)
        fun sign(text: String) = java.util.Base64.getEncoder().encodeToString(
            java.security.Signature.getInstance("SHA256withECDSA").run { initSign(keys.private); update(text.toByteArray()); sign() },
        )
        val newer = """{"version":7,"quick":120}"""
        // Signed by the right key: taken.
        assertTrue(Calibration.adopt(newer, sign(newer), public))
        assertEquals(120, Calibration.current.quick)
        // Changed after signing, signed by someone else, or not signed at all: refused.
        val tampered = """{"version":8,"quick":5}"""
        assertFalse(Calibration.adopt(tampered, sign(newer), public))
        assertFalse(Calibration.adopt(tampered, sign(tampered)))
        assertFalse(Calibration.adopt(tampered, null, public))
        assertFalse(Calibration.adopt(tampered, "not base64!", public))
        assertEquals(120, Calibration.current.quick)
    }
}
