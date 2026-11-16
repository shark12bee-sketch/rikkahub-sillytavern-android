package me.rerere.rikkahub.ui.pages.extensions

import kotlin.math.abs
import kotlin.uuid.Uuid

/** All geometry uses root coordinates, including the actual pointer, not the dragged card. */
internal data class LorebookDragPoint(val x: Float, val y: Float)

internal data class LorebookDragBounds(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    fun contains(point: LorebookDragPoint): Boolean =
        right > left && bottom > top && point.x >= left && point.x < right &&
            point.y >= top && point.y < bottom
}

internal data class LorebookDropTarget(val id: Uuid, val group: String, val top: Float, val bottom: Float)

internal enum class LorebookDragPhase { IDLE, SAFE, INSERTION, OUTSIDE }

internal data class LorebookDragFeedback(
    val phase: LorebookDragPhase,
    val placement: LorebookEntryPlacement? = null,
    val scrollVelocity: Float = 0f,
)

/** One decision controls the preview and scrolling; returning to safety cannot retain a stale target. */
internal fun evaluateLorebookEntryDrag(
    pointer: LorebookDragPoint?,
    safety: LorebookDragBounds?,
    viewport: LorebookDragBounds?,
    targets: List<LorebookDropTarget>,
    group: String,
    edgeSize: Float,
    maxScrollVelocity: Float,
    canScrollBackward: Boolean,
    canScrollForward: Boolean,
): LorebookDragFeedback {
    if (pointer == null) return LorebookDragFeedback(LorebookDragPhase.IDLE)
    if (safety?.contains(pointer) == true) return LorebookDragFeedback(LorebookDragPhase.SAFE)
    if (viewport == null || !viewport.contains(pointer)) return LorebookDragFeedback(LorebookDragPhase.OUTSIDE)

    val visible = targets.filter { it.bottom > viewport.top && it.top < viewport.bottom }
    val target = visible.firstOrNull { pointer.y >= it.top && pointer.y < it.bottom }
        ?: visible.minByOrNull { minOf(abs(pointer.y - it.top), abs(pointer.y - it.bottom)) }
    val placement = target?.takeIf { it.group == group }?.let {
        LorebookEntryPlacement(it.id, after = pointer.y > (it.top + it.bottom) / 2f)
    }
    // Prevent overlapping edge bands in a short viewport. Speed is distance-based, in px/second.
    val edge = edgeSize.coerceIn(0f, (viewport.bottom - viewport.top) / 3f)
    val velocity = if (edge > 0f) {
        val fromTop = pointer.y - viewport.top
        val fromBottom = viewport.bottom - pointer.y
        when {
            fromTop < edge && canScrollBackward -> -maxScrollVelocity * (1f - fromTop / edge)
            fromBottom < edge && canScrollForward -> maxScrollVelocity * (1f - fromBottom / edge)
            else -> 0f
        }
    } else 0f
    return LorebookDragFeedback(LorebookDragPhase.INSERTION, placement, velocity)
}
