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

/**
 * Resolves a component list using a path of component indexes. Every path
 * segment must point to a stack, preventing accidental edits to leaf nodes.
 */
internal fun componentsAtPath(
    components: List<LayoutComponent>,
    stackPath: List<Int>
): List<LayoutComponent> {
    var current = components
    stackPath.forEachIndexed { depth, index ->
        require(index in current.indices) { "Invalid stack path at depth $depth." }
        val stack = current[index]
        require(stack.type == "stack") { "Component at depth $depth is not a stack." }
        current = stack.children
    }
    return current
}

/**
 * Replaces the component list at a stack path without mutating the original
 * tree. An empty path applies the transform to the root component list.
 */
internal fun updateComponentsAtPath(
    components: List<LayoutComponent>,
    stackPath: List<Int>,
    transform: (List<LayoutComponent>) -> List<LayoutComponent>
): List<LayoutComponent> {
    if (stackPath.isEmpty()) return transform(components).toList()

    val index = stackPath.first()
    require(index in components.indices) { "Invalid stack path." }
    val stack = components[index]
    require(stack.type == "stack") { "Selected component is not a stack." }

    val updatedChildren = updateComponentsAtPath(
        stack.children,
        stackPath.drop(1),
        transform
    )
    return components.toMutableList().also {
        it[index] = stack.copy(children = updatedChildren)
    }
}
