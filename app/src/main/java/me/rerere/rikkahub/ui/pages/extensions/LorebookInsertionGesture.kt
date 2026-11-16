package me.rerere.rikkahub.ui.pages.extensions

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import kotlin.uuid.Uuid

internal data class LorebookThumbnailWindow(val firstIndex: Int = 0, val offset: Int = 0)

internal data class LorebookInsertionDrag(
    val token: Uuid = Uuid.random(),
    val entryId: Uuid?,
    val origin: Rect,
    val start: Offset,
    val window: LorebookThumbnailWindow = LorebookThumbnailWindow(),
    val last: Offset = start,
    val released: Boolean = false,
    val submitted: Boolean = false,
)

/** One detector for both thumbnail and group handles; layout never determines the drop target. */
@Composable
internal fun lorebookInsertionHandle(
    state: LorebookInsertionState,
    enabled: Boolean,
    longPress: Boolean,
    feedback: () -> LorebookDragFeedback,
    onConfirm: (Uuid?, LorebookEntryPlacement) -> Unit,
    entryId: Uuid? = null,
    onTap: (() -> Unit)? = null,
): Modifier {
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val currentFeedback by rememberUpdatedState(feedback)
    val currentConfirm by rememberUpdatedState(onConfirm)
    val currentTap by rememberUpdatedState(onTap)
    val haptic = LocalHapticFeedback.current

    fun root(position: Offset) = coordinates?.takeIf { it.isAttached }?.localToRoot(position)
    fun update(position: Offset) {
        root(position)?.let { point ->
            state.pointer = LorebookDragPoint(point.x, point.y)
            state.drag = state.drag?.copy(last = point)
        }
    }
    fun start(position: Offset) {
        val point = root(position) ?: return
        val bounds = if (entryId == null) state.previewBounds else coordinates?.boundsInRoot()
        if (bounds != null) {
            state.drag = LorebookInsertionDrag(entryId = entryId, origin = bounds, start = point, window = state.previewWindow)
            haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
        }
        update(position)
    }
    fun finish(position: Offset?, cancelled: Boolean) {
        if (position != null) update(position)
        val target = if (cancelled) null else currentFeedback().placement
        state.pointer = null
        state.drag = state.drag?.copy(released = true, submitted = target != null)
        haptic.performHapticFeedback(HapticFeedbackType.GestureEnd)
        if (target != null) currentConfirm(entryId, target)
    }

    val tapSemantics = if (onTap == null) Modifier else Modifier.semantics {
        onClick(label = "查看条目预览") { currentTap?.invoke(); true }
    }
    return Modifier.onGloballyPositioned { coordinates = it }.then(tapSemantics)
        .pointerInput(state, longPress, enabled, entryId) {
            if (!enabled) return@pointerInput
            if (longPress) awaitEachGesture {
                try {
                    val down = awaitFirstDown()
                    val held = awaitLongPressOrCancellation(down.id)
                    if (held != null) {
                        start(held.position)
                        val released = drag(held.id) { change -> change.consume(); update(change.position) }
                        val up = currentEvent.changes.firstOrNull { !it.pressed }
                        if (released && up != null) { up.consume(); finish(up.position, false) }
                        else finish(null, true)
                    } else {
                        // A consumed move (horizontal scrolling) cancels both click and long press.
                        val up = currentEvent.changes.firstOrNull { it.id == down.id && !it.pressed && !it.isConsumed }
                        if (up != null) { currentTap?.invoke(); up.consume() }
                    }
                } finally {
                    if (state.pointer != null) finish(null, true)
                }
            } else detectDragGestures(
                orientationLock = null,
                onDragStart = { down, change, _ -> start(down.position); update(change.position) },
                onDragCancel = { finish(null, true) },
                onDragEnd = { finish(it.position, false) },
                onDrag = { change, _ -> change.consume(); update(change.position) },
            )
        }
}
