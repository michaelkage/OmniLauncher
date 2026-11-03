package dev.omni.launcher.preset

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Safe first-generation .omni archive support.
 *
 * Archives contain exactly one declarative file (layout.json). Executable
 * scripts and arbitrary archive paths are deliberately unsupported.
 */
object PresetArchive {
    private const val MAX_ARCHIVE_BYTES = 10 * 1024 * 1024
    private const val MAX_LAYOUT_BYTES = 1024 * 1024

    fun read(input: InputStream, decodeAndValidate: (String) -> PresetDocument): PresetDocument {
        val bytes = input.use { source ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var total = 0
            while (true) {
                val count = source.read(buffer)
                if (count < 0) break
                total += count
                require(total <= MAX_ARCHIVE_BYTES) { "The .omni archive exceeds the 10 MiB limit." }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
        require(bytes.size >= 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4b.toByte()) {
            "This file is not a valid .omni ZIP archive."
        }

        var layout: ByteArray? = null
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                require(!entry.isDirectory) { "Directory entries are not allowed in .omni archives." }
                require(entry.name == "layout.json") {
                    "Unsupported archive entry '${entry.name}'. Only layout.json is accepted."
                }
                require(layout == null) { "The archive contains duplicate layout.json entries." }
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(4096)
                var total = 0
                while (true) {
                    val count = zip.read(buffer)
                    if (count < 0) break
                    total += count
                    require(total <= MAX_LAYOUT_BYTES) { "layout.json exceeds the 1 MiB limit." }
                    output.write(buffer, 0, count)
                }
                layout = output.toByteArray()
                zip.closeEntry()
            }
        }
        val json = requireNotNull(layout) { "The archive does not contain layout.json." }
        return decodeAndValidate(json.toString(Charsets.UTF_8))
    }

    fun write(documentJson: String, output: OutputStream) {
        val bytes = documentJson.toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_LAYOUT_BYTES) { "layout.json exceeds the 1 MiB limit." }
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("layout.json"))
            zip.write(bytes)
            zip.closeEntry()
        }
    }
}
