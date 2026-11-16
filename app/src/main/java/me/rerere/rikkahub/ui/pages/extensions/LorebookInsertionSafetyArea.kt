package me.rerere.rikkahub.ui.pages.extensions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.uuid.Uuid

/** Gesture lifetime only. Pending-entry data remains owned by the editor, outside this container. */
internal class LorebookInsertionState {
    var pointer by mutableStateOf<LorebookDragPoint?>(null)
    var safetyBounds by mutableStateOf<LorebookDragBounds?>(null)
    var previewBounds by mutableStateOf<Rect?>(null)
    var previewWindow by mutableStateOf(LorebookThumbnailWindow())
    var drag by mutableStateOf<LorebookInsertionDrag?>(null)
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
    longPressDrag: Boolean = false,
    compact: Boolean = false,
    enabled: Boolean = true,
    cancelEnabled: Boolean = true,
    onConfirmSingle: (Uuid, LorebookEntryPlacement) -> Unit = { _, _ -> },
    idleHint: String = "安全区：拖动手柄选择同组位置；快捷插入首尾会立即保存",
    content: @Composable (handleModifier: Modifier, thumbnailHandle: @Composable (Uuid, () -> Unit) -> Modifier) -> Unit,
) {
    val handleModifier = lorebookInsertionHandle(state, enabled, longPressDrag, feedback,
        onConfirm = { _, placement -> onConfirm(placement) })

    val dragFeedback = feedback()
    Surface(
        modifier = modifier.fillMaxWidth().onGloballyPositioned {
            val bounds = it.boundsInRoot()
            state.safetyBounds = LorebookDragBounds(bounds.left, bounds.top, bounds.right, bounds.bottom)
        },
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            Modifier.padding(if (compact) 6.dp else 8.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 4.dp),
        ) {
            val status = when (dragFeedback.phase) {
                LorebookDragPhase.IDLE -> idleHint
                LorebookDragPhase.SAFE -> "安全区内 · 插入已暂停；向下拖出安全区继续"
                LorebookDragPhase.INSERTION -> if (dragFeedback.placement != null) {
                    "正在选择插入位置 · 松手插入指示线处"
                } else "此处没有同组目标 · 请拖至所属分组"
                LorebookDragPhase.OUTSIDE -> "插入已暂停 · 请移回下方列表继续，或返回安全区"
            }
            if (compact) {
                val compactStatus = when (dragFeedback.phase) {
                    LorebookDragPhase.IDLE -> if (enabled) "长按拖动" else idleHint
                    LorebookDragPhase.SAFE, LorebookDragPhase.OUTSIDE -> "已暂停"
                    LorebookDragPhase.INSERTION -> if (dragFeedback.placement != null) "松手插入" else "请选择同组"
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(compactStatus, Modifier.widthIn(max = 110.dp), style = MaterialTheme.typography.labelSmall,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            } else {
                Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(status, style = MaterialTheme.typography.bodySmall, minLines = 2, maxLines = 2)
            }
            val previewModifier = if (compact) Modifier else Modifier.weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
            Box(
                previewModifier.graphicsLayer {
                        alpha = if (!compact && dragFeedback.phase == LorebookDragPhase.INSERTION) 0.45f else 1f
                    },
            ) {
                content(handleModifier) { id, onTap ->
                    if (enabled) lorebookInsertionHandle(state, true, true, feedback,
                        onConfirm = { entryId, placement -> if (entryId != null) onConfirmSingle(entryId, placement) },
                        entryId = id, onTap = onTap)
                    else Modifier.clickable(enabled = cancelEnabled, onClick = onTap)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { onInsertAtEdge(false) }, enabled = enabled) { Text("插入最前") }
                TextButton(onClick = { onInsertAtEdge(true) }, enabled = enabled) { Text("插入最后") }
                TextButton(onClick = onCancel, enabled = cancelEnabled && state.pointer == null) { Text("取消待插入") }
            }
        }
    }
}
