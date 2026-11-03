package dev.omni.launcher.preset

import android.content.Context
import kotlinx.serialization.json.Json

class PresetRepository(context: Context) {
    private val appContext = context.applicationContext
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = false
    }

    fun loadBundledPreset(fileName: String = "presets/minimal-wave.json"): PresetDocument {
        val source = appContext.assets.open(fileName).bufferedReader().use { it.readText() }
        val preset = json.decodeFromString<PresetDocument>(source)

        require(preset.schemaVersion == SUPPORTED_SCHEMA_VERSION) {
            "Unsupported preset schema version: ${preset.schemaVersion}"
        }
        require(preset.metadata.id.isNotBlank()) { "Preset ID must not be blank" }
        require(preset.metadata.name.isNotBlank()) { "Preset name must not be blank" }
        require(preset.layout.type in SUPPORTED_ROOT_TYPES) {
            "Unsupported root layout type: ${preset.layout.type}"
        }
        return preset
    }

    companion object {
        const val SUPPORTED_SCHEMA_VERSION = 1
        private val SUPPORTED_ROOT_TYPES = setOf("vertical_scroll_stack")
    }
}
