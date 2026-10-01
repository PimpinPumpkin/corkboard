package app.corkboard.data

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Everything the app remembers, as one zip file the user keeps wherever they like: favorites,
 * lists, notes, hidden listings, saved searches, the chosen place, settings, and the archive of
 * saved listings with their photos. The site cookie is left out on purpose; a restored app is a
 * new visitor.
 */
object Backup {
    private const val MARKER = "corkboard-backup.txt"
    private const val SETTINGS = "settings.txt"
    private const val VERSION = "1"

    /** Only what a backup made by [write] can contain. Anything else in a zip is refused. */
    private val allowed = Regex("""^(store|archive)/[A-Za-z0-9._-]+(/[A-Za-z0-9._-]+)?$""")

    fun write(out: OutputStream, store: File, archive: File, settings: Map<String, String>) {
        ZipOutputStream(out).use { zip ->
            fun put(name: String, bytes: ByteArray) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
            put(MARKER, VERSION.toByteArray())
            put(SETTINGS, settings.entries.joinToString("\n") { "${it.key}=${it.value}" }.toByteArray())
            for ((prefix, dir) in listOf("store" to store, "archive" to archive)) {
                dir.walkTopDown().filter { it.isFile && !it.name.endsWith(".tmp") }.forEach { f ->
                    val name = prefix + "/" + f.relativeTo(dir).invariantSeparatorsPath
                    if (allowed.matches(name)) put(name, f.readBytes())
                }
            }
        }
    }

    /**
     * Unpacks a backup into [staging] (as `store/` and `archive/`) and returns its settings, or
     * returns null when the file is not a Corkboard backup. Nothing outside [staging] is touched.
     */
    fun unpack(input: InputStream, staging: File): Map<String, String>? {
        staging.deleteRecursively()
        staging.mkdirs()
        var marked = false
        var settings: Map<String, String> = emptyMap()
        return try {
            ZipInputStream(input).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val name = entry.name
                    when {
                        entry.isDirectory -> {}
                        name == MARKER -> marked = zip.readBytes().toString(Charsets.UTF_8).trim() == VERSION
                        name == SETTINGS -> settings = zip.readBytes().toString(Charsets.UTF_8).lines()
                            .mapNotNull { line -> line.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }.toMap()
                        allowed.matches(name) && !name.contains("..") -> File(staging, name).apply { parentFile?.mkdirs() }.writeBytes(zip.readBytes())
                    }
                }
            }
            if (marked) settings else null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Replaces the contents of [store] and [archive] with the backup's and returns its settings,
     * or returns null, having changed nothing, when the file is not a Corkboard backup.
     */
    fun restore(input: InputStream, store: File, archive: File): Map<String, String>? {
        // Unpacked beside the real folders first, so a bad or cut-off file cannot leave things half replaced.
        val staging = File(store.parentFile, "restore.tmp")
        try {
            val settings = unpack(input, staging) ?: return null
            for ((prefix, dir) in listOf("store" to store, "archive" to archive)) {
                dir.deleteRecursively()
                val from = File(staging, prefix)
                if (from.exists()) from.copyRecursively(dir, overwrite = true) else dir.mkdirs()
            }
            return settings
        } finally {
            staging.deleteRecursively()
        }
    }
}
