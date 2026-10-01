package app.corkboard

import app.corkboard.data.Backup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BackupTest {
    private fun dirs(): Pair<File, File> {
        val root = Files.createTempDirectory("corkboard").toFile()
        return File(root, "store").apply { mkdirs() } to File(root, "archive").apply { mkdirs() }
    }

    @Test
    fun `a backup restores everything it held, and replaces what was there`() {
        val (store, archive) = dirs()
        File(store, "favorites.json").writeText("[1]")
        File(store, "lists.json").writeText("[2]")
        File(archive, "abc.json").writeText("{}")
        File(archive, "abc").mkdirs()
        File(archive, "abc/0.jpg").writeBytes(byteArrayOf(1, 2, 3))
        val zip = ByteArrayOutputStream().also { Backup.write(it, store, archive, mapOf("theme" to "Dark", "pinned" to "cta,zip")) }.toByteArray()

        val (store2, archive2) = dirs()
        File(store2, "hidden.json").writeText("old")
        val settings = Backup.restore(ByteArrayInputStream(zip), store2, archive2)!!
        assertEquals(mapOf("theme" to "Dark", "pinned" to "cta,zip"), settings)
        assertEquals("[1]", File(store2, "favorites.json").readText())
        assertEquals("[2]", File(store2, "lists.json").readText())
        assertFalse(File(store2, "hidden.json").exists())
        assertEquals("{}", File(archive2, "abc.json").readText())
        assertEquals(3, File(archive2, "abc/0.jpg").length())
    }

    @Test
    fun `some other zip is refused and nothing is touched`() {
        val (store, archive) = dirs()
        File(store, "favorites.json").writeText("[mine]")
        val zip = ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { z -> z.putNextEntry(ZipEntry("store/favorites.json")); z.write("[theirs]".toByteArray()); z.closeEntry() }
        }.toByteArray()
        assertNull(Backup.restore(ByteArrayInputStream(zip), store, archive))
        assertEquals("[mine]", File(store, "favorites.json").readText())
        assertNull(Backup.restore(ByteArrayInputStream("not a zip".toByteArray()), store, archive))
        assertEquals("[mine]", File(store, "favorites.json").readText())
    }

    @Test
    fun `a backup cannot write outside the app's own folders`() {
        val (store, archive) = dirs()
        val zip = ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { z ->
                z.putNextEntry(ZipEntry("corkboard-backup.txt")); z.write("1".toByteArray()); z.closeEntry()
                for (name in listOf("../evil.txt", "store/../../evil.txt", "/etc/evil", "other/evil.txt", "store/a/b/c.txt")) {
                    z.putNextEntry(ZipEntry(name)); z.write("x".toByteArray()); z.closeEntry()
                }
                z.putNextEntry(ZipEntry("store/ok.json")); z.write("ok".toByteArray()); z.closeEntry()
            }
        }.toByteArray()
        Backup.restore(ByteArrayInputStream(zip), store, archive)
        assertTrue(File(store, "ok.json").exists())
        assertEquals(listOf("ok.json"), store.walkTopDown().filter { it.isFile }.map { it.name }.toList())
        assertFalse(File(store.parentFile, "evil.txt").exists())
        assertFalse(File(store.parentFile.parentFile, "evil.txt").exists())
    }
}
