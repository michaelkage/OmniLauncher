package dev.omni.launcher.theme

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class ThemePack(
    @SerialName("schema_version") val schemaVersion: Int,
    @SerialName("pack_metadata") val metadata: ThemePackMetadata,
    val palettes: ThemePackPalettes
)

@Serializable
data class ThemePackMetadata(
    val id: String,
    val name: String,
    val author: String = "Unknown",
    val version: String = "1.0.0"
)

@Serializable
data class ThemePackPalettes(
    val light: ThemePalette,
    val dark: ThemePalette
)

@Serializable
data class ThemePalette(
    val primary: String,
    val secondary: String,
    val tertiary: String,
    val background: String,
    val surface: String
)

/**
 * Strict, data-only theme pack codec. Packs define colour tokens only; they cannot
 * supply code, fonts, images, URLs, component types, or executable behaviour.
 */
object ThemePackCodec {
    const val SUPPORTED_SCHEMA_VERSION = 1
    const val MAX_PACK_BYTES = 64 * 1024

    private val json = Json {
        ignoreUnknownKeys = false
        isLenient = false
        coerceInputValues = false
        prettyPrint = true
    }

    fun decode(source: String): ThemePack {
        require(source.toByteArray(Charsets.UTF_8).size <= MAX_PACK_BYTES) {
            "Theme pack exceeds the 64 KiB size limit."
        }
        val pack = try {
            json.decodeFromString<ThemePack>(source)
        } catch (error: SerializationException) {
            throw IllegalArgumentException("Theme pack JSON could not be parsed: ${error.message}", error)
        }
        validate(pack)
        return pack
    }

    fun encode(pack: ThemePack): String {
        validate(pack)
        return json.encodeToString(pack)
    }

    fun validate(pack: ThemePack) {
        require(pack.schemaVersion == SUPPORTED_SCHEMA_VERSION) {
            "Unsupported theme pack schema version: ${pack.schemaVersion}."
        }
        require(pack.metadata.id.matches(Regex("[A-Za-z0-9._-]{1,64}"))) {
            "Theme pack ID must use 1–64 letters, numbers, dots, underscores, or hyphens."
        }
        require(pack.metadata.name.isNotBlank() && pack.metadata.name.length <= 64) {
            "Theme pack name must contain 1–64 characters."
        }
        require(pack.metadata.author.length <= 64) { "Theme pack author must be at most 64 characters." }
        require(pack.metadata.version.matches(Regex("[0-9]+\\.[0-9]+\\.[0-9]+"))) {
            "Theme pack version must use semantic numeric form (for example, 1.0.0)."
        }
        listOf(
            "light.primary" to pack.palettes.light.primary,
            "light.secondary" to pack.palettes.light.secondary,
            "light.tertiary" to pack.palettes.light.tertiary,
            "light.background" to pack.palettes.light.background,
            "light.surface" to pack.palettes.light.surface,
            "dark.primary" to pack.palettes.dark.primary,
            "dark.secondary" to pack.palettes.dark.secondary,
            "dark.tertiary" to pack.palettes.dark.tertiary,
            "dark.background" to pack.palettes.dark.background,
            "dark.surface" to pack.palettes.dark.surface
        ).forEach { (token, value) ->
            require(value.matches(Regex("#[0-9A-Fa-f]{6}"))) {
                "Palette token '$token' must be a six-digit hex colour such as #336699."
            }
        }
    }
}
