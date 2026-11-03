package dev.omni.launcher.preset

import android.content.Context
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

data class PresetChoice(val id: String, val title: String, val assetPath: String)

data class PresetValidationIssue(val path: String, val message: String)

class PresetValidationException(
    val issues: List<PresetValidationIssue>
) : IllegalArgumentException(
    issues.joinToString(separator = "\n") { "${it.path}: ${it.message}" }
)

class PresetRepository(context: Context) {
    private val appContext = context.applicationContext
    private val json = Json {
        ignoreUnknownKeys = false
        isLenient = false
        coerceInputValues = false
    }

    val bundledPresets = listOf(
        PresetChoice("minimal-wave", "Minimal Wave", "presets/minimal-wave.json"),
        PresetChoice("pixel-grid", "Pixel Grid", "presets/pixel-grid.json"),
        PresetChoice("smart-stack", "Smart Stack", "presets/smart-stack.json")
    )

    fun loadBundledPreset(id: String = "minimal-wave"): PresetDocument {
        val choice = bundledPresets.firstOrNull { it.id == id }
            ?: throw IllegalArgumentException("Unknown bundled preset: $id")
        val source = appContext.assets.open(choice.assetPath).bufferedReader().use { it.readText() }
        val document = try {
            json.decodeFromString<PresetDocument>(source)
        } catch (error: SerializationException) {
            throw IllegalArgumentException("Preset JSON could not be parsed: ${error.message}", error)
        }
        val issues = validate(document)
        if (issues.isNotEmpty()) throw PresetValidationException(issues)
        return document
    }

    fun validate(document: PresetDocument): List<PresetValidationIssue> {
        val issues = mutableListOf<PresetValidationIssue>()
        if (document.schemaVersion != SUPPORTED_SCHEMA_VERSION) {
            issues += PresetValidationIssue("$.schema_version", "Expected schema version $SUPPORTED_SCHEMA_VERSION.")
        }
        if (document.metadata.id.isBlank()) issues += PresetValidationIssue("$.preset_metadata.id", "ID cannot be blank.")
        if (document.metadata.name.isBlank()) issues += PresetValidationIssue("$.preset_metadata.name", "Name cannot be blank.")
        if (document.layout.type != "vertical_scroll_stack") {
            issues += PresetValidationIssue("$.layout_hierarchy.type", "Unsupported root type '${document.layout.type}'.")
        }

        val padding = document.layout.padding
        if (listOf(padding.top, padding.left, padding.right, padding.bottom).any { it !in 0..256 }) {
            issues += PresetValidationIssue("$.layout_hierarchy.padding", "Padding values must be between 0 and 256 dp.")
        }
        if (document.layout.components.size > MAX_COMPONENTS) {
            issues += PresetValidationIssue("$.layout_hierarchy.components", "A preset may contain at most $MAX_COMPONENTS root components.")
        }

        var totalComponents = 0
        fun validateComponents(components: List<LayoutComponent>, path: String, depth: Int) {
            if (depth > MAX_DEPTH) {
                issues += PresetValidationIssue(path, "Component nesting exceeds the maximum depth of $MAX_DEPTH.")
                return
            }
            components.forEachIndexed { index, component ->
                totalComponents += 1
                if (totalComponents > MAX_TOTAL_COMPONENTS) {
                    issues += PresetValidationIssue(path, "A preset may contain at most $MAX_TOTAL_COMPONENTS components in total.")
                    return
                }
                val componentPath = "$path[$index]"
                if (component.type !in SUPPORTED_COMPONENTS) {
                    issues += PresetValidationIssue("$componentPath.type", "Unknown component type '${component.type}'.")
                }
                if (component.maxVisibleItems != null && component.maxVisibleItems !in 1..500) {
                    issues += PresetValidationIssue("$componentPath.max_visible_items", "Must be between 1 and 500.")
                }
                if (component.columns !in 1..8) {
                    issues += PresetValidationIssue("$componentPath.columns", "Grid columns must be between 1 and 8.")
                }
                if (component.spacing !in 0..64) {
                    issues += PresetValidationIssue("$componentPath.spacing", "Spacing must be between 0 and 64 dp.")
                }
                if (component.children.size > MAX_COMPONENTS) {
                    issues += PresetValidationIssue("$componentPath.children", "A component may contain at most $MAX_COMPONENTS children.")
                }
                validateComponents(component.children, "$componentPath.children", depth + 1)
            }
        }

        validateComponents(document.layout.components, "$.layout_hierarchy.components", 1)
        return issues
    }

    companion object {
        const val SUPPORTED_SCHEMA_VERSION = 1
        const val MAX_COMPONENTS = 100
        const val MAX_TOTAL_COMPONENTS = 500
        const val MAX_DEPTH = 12
        val SUPPORTED_COMPONENTS = setOf(
            "clock_widget", "app_list_container", "list", "grid", "dock", "stack"
        )
    }
}
