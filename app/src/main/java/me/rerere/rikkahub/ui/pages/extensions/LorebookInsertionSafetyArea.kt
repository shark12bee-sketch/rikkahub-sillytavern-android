package me.rerere.rikkahub.ui.pages.extensions

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Gesture lifetime only. Pending-entry data remains owned by the editor, outside this container. */
internal class LorebookInsertionState {
    var pointer by mutableStateOf<LorebookDragPoint?>(null)
    var safetyBounds by mutableStateOf<LorebookDragBounds?>(null)
}

/**
 * Fixed safety boundary shared by any insertion preview. The content supplies a handle location,
 * but cannot change how coordinates, pause/resume, release confirmation or opacity are evaluated.
 * A bounded, non-filling preview slot keeps actions visible and leaves room for the separate list.
 */
@Composable
internal fun LorebookInsertionSafetyArea(
    state: LorebookInsertionState,
    feedback: () -> LorebookDragFeedback,
    title: String,
    onConfirm: (LorebookEntryPlacement) -> Unit,
    onCancel: () -> Unit,
    onInsertAtEdge: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (handleModifier: Modifier) -> Unit,
) {
    var handleCoordinates by remember(state) { mutableStateOf<LayoutCoordinates?>(null) }
    val currentFeedback by rememberUpdatedState(feedback)
    val currentConfirm by rememberUpdatedState(onConfirm)

    fun updatePointer(position: Offset) {
        state.pointer = handleCoordinates?.takeIf { it.isAttached }?.localToRoot(position)?.let {
            LorebookDragPoint(it.x, it.y)
        }
    }

    val handleModifier = Modifier
        .onGloballyPositioned { handleCoordinates = it }
        .pointerInput(state) {
            detectDragGestures(
                orientationLock = null,
                onDragStart = { _, change, _ -> updatePointer(change.position) },
                onDragCancel = { state.pointer = null },
                onDragEnd = { change ->
                    updatePointer(change.position)
                    // Re-evaluate after the UP coordinate, before clearing the gesture. Reading a
                    // composited feedback value here could commit the last MOVE's stale target.
                    val target = currentFeedback().placement
                    state.pointer = null
                    if (target != null) currentConfirm(target)
                },
                onDrag = { change, _ ->
                    change.consume()
                    updatePointer(change.position)
                },
            )
        }

    val dragFeedback = feedback()
    Surface(
        modifier = modifier.fillMaxWidth().onGloballyPositioned {
            val bounds = it.boundsInRoot()
            state.safetyBounds = LorebookDragBounds(bounds.left, bounds.top, bounds.right, bounds.bottom)
        },
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val status = when (dragFeedback.phase) {
                LorebookDragPhase.IDLE -> "安全区：拖动手柄选择同组位置；快捷插入首尾会立即保存"
                LorebookDragPhase.SAFE -> "安全区内 · 插入已暂停；向下拖出安全区继续"
                LorebookDragPhase.INSERTION -> if (dragFeedback.placement != null) {
                    "正在选择插入位置 · 松手插入指示线处"
                } else "此处没有同组目标 · 请拖至所属分组"
                LorebookDragPhase.OUTSIDE -> "插入已暂停 · 请移回下方列表继续，或返回安全区"
            }
            Text(status, style = MaterialTheme.typography.bodySmall, minLines = 2, maxLines = 2)
            Box(
                Modifier.weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .graphicsLayer { alpha = if (dragFeedback.phase == LorebookDragPhase.INSERTION) 0.45f else 1f },
            ) {
                content(handleModifier)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { onInsertAtEdge(false) }) { Text("插入最前") }
                TextButton(onClick = { onInsertAtEdge(true) }) { Text("插入最后") }
                TextButton(onClick = onCancel) { Text("取消待插入") }
            }
        }
    }
}
