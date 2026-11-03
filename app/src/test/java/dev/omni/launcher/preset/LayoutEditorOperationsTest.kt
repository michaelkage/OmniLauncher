package dev.omni.launcher.preset

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertThrows
import org.junit.Test

class LayoutEditorOperationsTest {
    private val clock = LayoutComponent(type = "clock_widget")
    private val grid = LayoutComponent(type = "grid")
    private val dock = LayoutComponent(type = "dock")

    @Test fun movesComponentUpWithoutMutatingOriginal() {
        val original = listOf(clock, grid, dock)
        assertEquals(listOf(grid, clock, dock), moveComponent(original, 1, -1))
        assertEquals(listOf(clock, grid, dock), original)
    }

    @Test fun movesComponentDown() {
        assertEquals(listOf(clock, dock, grid), moveComponent(listOf(clock, grid, dock), 1, 1))
    }

    @Test fun ignoresOutOfRangeMove() {
        assertEquals(listOf(clock, grid), moveComponent(listOf(clock, grid), 0, -1))
    }

    @Test fun removesOnlyRequestedComponent() {
        assertEquals(listOf(clock, dock), removeComponent(listOf(clock, grid, dock), 1))
    }

    @Test fun appendsComponent() {
        assertEquals(listOf(clock, grid), appendComponent(listOf(clock), grid))
    }

    @Test fun resolvesNestedStackPath() {
        val nestedGrid = LayoutComponent(type = "grid", columns = 6)
        val tree = listOf(LayoutComponent(type = "stack", children = listOf(
            LayoutComponent(type = "stack", children = listOf(nestedGrid))
        )))
        assertEquals(listOf(nestedGrid), componentsAtPath(tree, listOf(0, 0)))
    }

    @Test fun updatesNestedChildrenWithoutMutatingOriginalTree() {
        val original = listOf(LayoutComponent(type = "stack", children = listOf(clock)))
        val updated = updateComponentsAtPath(original, listOf(0)) { it + grid }

        assertEquals(listOf(clock, grid), updated.single().children)
        assertEquals(listOf(clock), original.single().children)
        assertNotSame(original, updated)
    }

    @Test fun updatesDeeplyNestedComponentProperties() {
        val original = listOf(LayoutComponent(type = "stack", children = listOf(
            LayoutComponent(type = "stack", children = listOf(grid))
        )))
        val updated = updateComponentsAtPath(original, listOf(0, 0)) {
            it.map { component -> component.copy(columns = 7) }
        }
        assertEquals(7, updated.single().children.single().children.single().columns)
        assertEquals(4, original.single().children.single().children.single().columns)
    }

    @Test fun rejectsPathThroughNonStackComponent() {
        assertThrows(IllegalArgumentException::class.java) {
            componentsAtPath(listOf(grid), listOf(0))
        }
    }

    @Test fun rejectsOutOfRangeStackPath() {
        assertThrows(IllegalArgumentException::class.java) {
            updateComponentsAtPath(listOf(LayoutComponent(type = "stack")), listOf(2)) { it }
        }
    }
}
