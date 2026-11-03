package dev.omni.launcher.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ThemePackCodecTest {
    private val sample = ThemePack(
        schemaVersion = 1,
        metadata = ThemePackMetadata(id = "org.omni.theme.midnight", name = "Midnight"),
        palettes = ThemePackPalettes(
            light = ThemePalette("#336699", "#445566", "#667788", "#F7F9FC", "#FFFFFF"),
            dark = ThemePalette("#99CCFF", "#AABBCC", "#DDEEFF", "#101820", "#18232D")
        )
    )

    @Test
    fun roundTripsAValidDeclarativePack() {
        assertEquals(sample, ThemePackCodec.decode(ThemePackCodec.encode(sample)))
    }

    @Test
    fun rejectsUnsupportedSchemaVersion() {
        assertThrows(IllegalArgumentException::class.java) {
            ThemePackCodec.validate(sample.copy(schemaVersion = 2))
        }
    }

    @Test
    fun rejectsUnsafePackIdentifiers() {
        assertThrows(IllegalArgumentException::class.java) {
            ThemePackCodec.validate(sample.copy(metadata = sample.metadata.copy(id = "../outside")))
        }
    }

    @Test
    fun rejectsMalformedColourTokens() {
        val invalid = sample.copy(
            palettes = sample.palettes.copy(
                dark = sample.palettes.dark.copy(primary = "red")
            )
        )
        assertThrows(IllegalArgumentException::class.java) { ThemePackCodec.validate(invalid) }
    }

    @Test
    fun rejectsOversizedInputBeforeDecoding() {
        assertThrows(IllegalArgumentException::class.java) {
            ThemePackCodec.decode(" ".repeat(ThemePackCodec.MAX_PACK_BYTES + 1))
        }
    }

    @Test
    fun rejectsUnknownFields() {
        val source = ThemePackCodec.encode(sample).replace(
            "\"schema_version\": 1,",
            "\"schema_version\": 1,\n  \"run_script\": \"not-supported\","
        )
        assertThrows(IllegalArgumentException::class.java) { ThemePackCodec.decode(source) }
    }
}
