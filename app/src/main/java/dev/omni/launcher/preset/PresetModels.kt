package dev.omni.launcher.preset

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PresetDocument(
    @SerialName("schema_version") val schemaVersion: Int,
    @SerialName("preset_metadata") val metadata: PresetMetadata,
    @SerialName("global_styles") val globalStyles: GlobalStyles = GlobalStyles(),
    @SerialName("interaction_matrix") val interactions: List<InteractionRule> = emptyList(),
    @SerialName("layout_hierarchy") val layout: LayoutNode
)

@Serializable
data class PresetMetadata(
    val id: String,
    val name: String,
    val author: String = "Unknown",
    val version: String = "1.0.0",
    @SerialName("engine_version") val engineVersion: String = "0.1.0"
)

@Serializable
data class GlobalStyles(
    @SerialName("font_family") val fontFamily: String = "system_sans",
    @SerialName("animation_curve") val animationCurve: String = "standard"
)

@Serializable
data class InteractionRule(
    val trigger: String,
    val action: String,
    val target: String? = null
)

@Serializable
data class LayoutNode(
    val type: String,
    val alignment: String = "start",
    val padding: LayoutPadding = LayoutPadding(),
    val components: List<LayoutComponent> = emptyList()
)

@Serializable
data class LayoutPadding(
    val top: Int = 0,
    val left: Int = 0,
    val right: Int = 0,
    val bottom: Int = 0
)

@Serializable
data class LayoutComponent(
    val type: String,
    val style: String? = null,
    val behavior: String? = null,
    @SerialName("show_labels") val showLabels: Boolean = true,
    @SerialName("max_visible_items") val maxVisibleItems: Int? = null,
    val filter: AppFilter? = null
)

@Serializable
data class AppFilter(
    @SerialName("exclude_categories") val excludeCategories: List<String> = emptyList()
)
