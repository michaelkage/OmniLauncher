package dev.omni.launcher.preset

import org.junit.Assert.assertEquals
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
}
