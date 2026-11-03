package dev.omni.launcher.theme

import android.content.Context
import android.util.AtomicFile
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/**
 * Private app-storage repository for validated, data-only theme packs.
 * Imported files are never extracted as archives or interpreted as executable content.
 */
class ThemePackRepository(context: Context) {
    private val directory = File(context.applicationContext.filesDir, "theme-packs")

    fun import(input: InputStream): ThemePack {
        val bytes = readBounded(input)
        val pack = ThemePackCodec.decode(bytes.toString(Charsets.UTF_8))
        save(pack)
        return pack
    }

    fun save(pack: ThemePack) {
        val bytes = ThemePackCodec.encode(pack).toByteArray(Charsets.UTF_8)
        require(bytes.size <= ThemePackCodec.MAX_PACK_BYTES) {
            "Theme pack exceeds the 64 KiB size limit."
        }
        ensureDirectory()
        val atomicFile = AtomicFile(fileFor(pack.metadata.id))
        val stream = atomicFile.startWrite()
        try {
            stream.write(bytes)
            stream.fd.sync()
            atomicFile.finishWrite(stream)
        } catch (error: Throwable) {
            atomicFile.failWrite(stream)
            throw error
        }
    }

    fun export(pack: ThemePack, output: OutputStream) {
        val bytes = ThemePackCodec.encode(pack).toByteArray(Charsets.UTF_8)
        require(bytes.size <= ThemePackCodec.MAX_PACK_BYTES) {
            "Theme pack exceeds the 64 KiB size limit."
        }
        output.write(bytes)
        output.flush()
    }

    fun load(id: String): ThemePack {
        val file = fileFor(id)
        require(file.isFile) { "Theme pack '$id' was not found." }
        require(file.length() <= ThemePackCodec.MAX_PACK_BYTES) {
            "Stored theme pack exceeds the 64 KiB size limit."
        }
        return ThemePackCodec.decode(file.readText(Charsets.UTF_8))
    }

    fun list(): List<ThemePackMetadata> = if (!directory.isDirectory) {
        emptyList()
    } else {
        directory.listFiles()
            .orEmpty()
            .filter { it.isFile && it.extension == "json" }
            .map { ThemePackCodec.decode(it.readText(Charsets.UTF_8)).metadata }
            .sortedBy { it.name.lowercase() }
    }

    fun remove(id: String): Boolean = fileFor(id).let { it.isFile && it.delete() }

    private fun ensureDirectory() {
        require(directory.isDirectory || directory.mkdirs()) {
            "Could not create the theme pack directory."
        }
    }

    private fun fileFor(id: String): File {
        require(id.matches(Regex("[A-Za-z0-9._-]{1,64}"))) {
            "Theme pack ID contains unsupported characters."
        }
        return File(directory, "$id.json")
    }

    private fun readBounded(input: InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            require(output.size() + count <= ThemePackCodec.MAX_PACK_BYTES) {
                "Theme pack exceeds the 64 KiB size limit."
            }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }
}
