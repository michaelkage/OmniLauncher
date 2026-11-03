package dev.omni.launcher.preset

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PresetArchiveTest {
    private val sample = PresetDocument(
        schemaVersion = 1,
        metadata = PresetMetadata(id = "test-preset", name = "Test preset"),
        layout = LayoutNode(type = "vertical_scroll_stack")
    )

    @Test
    fun writesAndReadsSingleLayoutDocument() {
        val archive = ByteArrayOutputStream()
        PresetArchive.write("""{"schema_version":1}""", archive)

        var decoded = ""
        val result = PresetArchive.read(ByteArrayInputStream(archive.toByteArray())) {
            decoded = it
            sample
        }

        assertEquals("""{"schema_version":1}""", decoded)
        assertEquals(sample, result)
    }

    @Test
    fun rejectsNonZipInput() {
        assertThrows(IllegalArgumentException::class.java) {
            PresetArchive.read(ByteArrayInputStream("not a zip".toByteArray())) { sample }
        }
    }

    @Test
    fun rejectsUnexpectedArchiveEntry() {
        val archive = zipOf("payload.txt" to "not allowed")
        assertThrows(IllegalArgumentException::class.java) {
            PresetArchive.read(ByteArrayInputStream(archive)) { sample }
        }
    }

    @Test
    fun rejectsPathBearingArchiveEntry() {
        val archive = zipOf("../layout.json" to "{}")
        assertThrows(IllegalArgumentException::class.java) {
            PresetArchive.read(ByteArrayInputStream(archive)) { sample }
        }
    }

    @Test
    fun rejectsExpandedLayoutOverOneMiB() {
        val archive = zipOf("layout.json" to "x".repeat(1024 * 1024 + 1))
        assertThrows(IllegalArgumentException::class.java) {
            PresetArchive.read(ByteArrayInputStream(archive)) { sample }
        }
    }

    private fun zipOf(vararg entries: Pair<String, String>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }
}
