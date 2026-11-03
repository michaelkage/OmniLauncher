package dev.omni.launcher.preset

internal fun moveComponent(
    components: List<LayoutComponent>,
    index: Int,
    offset: Int
): List<LayoutComponent> {
    val destination = index + offset
    if (index !in components.indices || destination !in components.indices || offset == 0) {
        return components.toList()
    }
    return components.toMutableList().also {
        val component = it.removeAt(index)
        it.add(destination, component)
    }
}

internal fun removeComponent(
    components: List<LayoutComponent>,
    index: Int
): List<LayoutComponent> {
    if (index !in components.indices) return components.toList()
    return components.toMutableList().also { it.removeAt(index) }
}

internal fun appendComponent(
    components: List<LayoutComponent>,
    component: LayoutComponent
): List<LayoutComponent> = components + component
