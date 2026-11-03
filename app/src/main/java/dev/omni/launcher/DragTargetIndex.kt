package dev.omni.launcher

import dev.omni.launcher.preset.calculateDragTargetIndex as calculatePresetDragTargetIndex

/**
 * Launcher UI entry point for the tested preset drag-target calculation.
 */
internal fun calculateDragTargetIndex(
    startIndex: Int,
    dragDistancePx: Float,
    rowHeightPx: Float,
    itemCount: Int
): Int = calculatePresetDragTargetIndex(
    startIndex = startIndex,
    dragDistancePx = dragDistancePx,
    rowHeightPx = rowHeightPx,
    itemCount = itemCount
)
