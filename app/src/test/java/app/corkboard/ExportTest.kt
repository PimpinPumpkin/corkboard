package app.corkboard

import app.corkboard.data.Export
import app.corkboard.data.Listing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportTest {
    private val truck = Listing(
        postingId = 7975810458, postedAt = 1790813736, categoryId = 145, priceText = "$4,200",
        title = "1999 Tacoma, \"runs great\"", place = "Oakland", odometer = 212000, uuid = "abc123", slug = "tacoma",
    )
    private val couch = Listing(postingId = 2, postedAt = 0, categoryId = 5, title = "Couch", uuid = "def456")

    @Test
    fun `a spreadsheet row survives commas, quotes and line breaks`() {
        val csv = Export.csv(listOf(truck, couch), mapOf(truck.postingId to "new clutch,\nask about title"))
        val lines = csv.split("\r\n")
        assertEquals("Title,Price,Place,Odometer,Posted,Note,Link,Posting ID", lines[0])
        assertTrue(csv.contains("\"1999 Tacoma, \"\"runs great\"\"\",\"$4,200\",\"Oakland\",\"212000\","))
        assertTrue(csv.contains("\"new clutch,\nask about title\",\"https://www.craigslist.org/view/d/tacoma/abc123\",\"7975810458\""))
        assertTrue(csv.contains("\"Couch\",\"\",\"\",\"\",\"\",\"\",\"https://www.craigslist.org/view/def456\",\"2\""))
        assertTrue(csv.endsWith("\r\n"))
    }

    @Test
    fun `shared text reads like a message`() {
        val text = Export.text("Trucks", listOf(truck), mapOf(truck.postingId to "new clutch"))
        assertEquals(
            "Trucks\n\n1999 Tacoma, \"runs great\" · $4,200 · Oakland\nNote: new clutch\nhttps://www.craigslist.org/view/d/tacoma/abc123\n",
            text,
        )
    }

    @Test
    fun `file names are plain`() {
        assertEquals("trucks-under-10k.csv", Export.fileName("Trucks under 10k!"))
        assertEquals("list.csv", Export.fileName("???"))
    }
}
