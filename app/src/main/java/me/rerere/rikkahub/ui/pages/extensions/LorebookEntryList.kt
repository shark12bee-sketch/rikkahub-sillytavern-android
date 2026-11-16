package me.rerere.rikkahub.ui.pages.extensions

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.data.model.Lorebook
import me.rerere.rikkahub.data.model.PromptInjection
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import kotlin.uuid.Uuid

@Composable
internal fun LorebookEntryList(
    book: Lorebook,
    pendingEntry: PromptInjection.RegexInjection?,
    onCancelPending: () -> Unit,
    onInsertPending: (LorebookEntryPlacement?) -> Unit,
    onInsertPendingAtEdge: (Boolean) -> Unit,
    onUpdatePending: (PromptInjection.RegexInjection) -> Unit,
    modifier: Modifier = Modifier,
    batch: LorebookBatchPreview? = null,
    selectedIds: Set<Uuid>? = null,
    onSelectEntry: (Uuid) -> Unit = {},
    onRemoveBatchEntry: (Uuid) -> Unit = {},
    onCommitBatch: (LorebookEntryPlacement?, Boolean?) -> Unit = { _, _ -> },
    onCommitSingle: (Uuid, LorebookEntryPlacement) -> Unit = { _, _ -> },
    batchEnabled: Boolean = true,
    batchBusy: Boolean = false,
    batchHint: String? = null,
    header: @Composable () -> Unit,
    onEntriesChange: (List<PromptInjection.RegexInjection>) -> Unit,
    onEditEntry: (PromptInjection.RegexInjection) -> Unit,
    onAddEntry: (String) -> Unit,
    onGroupSettings: (String, List<PromptInjection.RegexInjection>) -> Unit,
) {
    var collapsed by rememberSaveable(book.id.toString()) { mutableStateOf(false) }
    var expandedGroups by rememberSaveable(book.id.toString()) { mutableStateOf(emptyList<String>()) }
    var draggingGroup by remember { mutableStateOf<String?>(null) }
    var insertionId by remember { mutableStateOf<Uuid?>(null) }
    val movingIds = if (batch?.mode == LorebookBatchMode.MOVE) batch.entries.map { it.id }.toSet() else emptySet()
    val visibleEntries = book.entries.filterNot { it.id in movingIds }
    val hasPending = pendingEntry != null || batch != null
    val taskActive = hasPending || selectedIds != null
    val insertion = remember(book.id, pendingEntry?.id, batch?.id) { LorebookInsertionState() }
    var listBounds by remember { mutableStateOf<LorebookDragBounds?>(null) }
    val currentGroup by rememberUpdatedState(pendingEntry?.group ?: batch?.group.orEmpty())
    val currentVisible by rememberUpdatedState(visibleEntries)
    val edge = with(LocalDensity.current) { 64.dp.toPx() }
    val maxScrollVelocity = with(LocalDensity.current) { 720.dp.toPx() }
    val listState = rememberLazyListState()
    val feedback by remember(insertion, edge, maxScrollVelocity) {
        derivedStateOf {
            val entries = currentVisible.associateBy { it.id.toString() }
            val targets = listState.layoutInfo.visibleItemsInfo.mapNotNull { item ->
                entries[item.key]?.let { entry ->
                    val top = (listBounds?.top ?: 0f) + item.offset
                    LorebookDropTarget(entry.id, entry.group, top, top + item.size)
                }
            }
            evaluateLorebookEntryDrag(
                pointer = insertion.pointer,
                safety = insertion.safetyBounds,
                viewport = listBounds,
                targets = targets,
                group = currentGroup,
                edgeSize = edge,
                maxScrollVelocity = maxScrollVelocity,
                canScrollBackward = listState.canScrollBackward,
                canScrollForward = listState.canScrollForward,
            )
        }
    }

    // Only one scroll loop, running for the gesture's lifetime. Pointer moves never restart it.
    // The same feedback drives opacity, the insertion line and the scroll velocity.
    LaunchedEffect(insertion, insertion.pointer != null) {
        if (insertion.pointer == null) return@LaunchedEffect
        var previousFrame = withFrameNanos { it }
        while (insertion.pointer != null) {
            val frame = withFrameNanos { it }
            val seconds = (frame - previousFrame).coerceIn(0L, 50_000_000L) / 1_000_000_000f
            previousFrame = frame
            val velocity = feedback.scrollVelocity
            if (velocity != 0f) listState.scrollBy(velocity * seconds)
        }
    }
    LaunchedEffect(pendingEntry?.id, batch?.id, currentGroup) {
        if (hasPending) expandedGroups = (expandedGroups + currentGroup).distinct()
    }

    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        val fromId = book.entries.firstOrNull { it.id.toString() == from.key }?.id
        val toId = book.entries.firstOrNull { it.id.toString() == to.key }?.id
        if (!taskActive && fromId != null && toId != null) {
            val reordered = moveLorebookEntry(book.entries, fromId, toId)
            if (reordered != book.entries) {
                insertionId = fromId
                onEntriesChange(reordered)
            }
        }
    }

    var rootOrigin by remember { mutableStateOf(Offset.Zero) }
    BoxWithConstraints(modifier = modifier.onGloballyPositioned { rootOrigin = it.positionInRoot() }) {
        val safetyHeightLimit = maxHeight * 0.55f
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(if (batch != null) 4.dp else 8.dp)) {
            // Reserve at least 45% for the independent list, even with large text or an expanded editor.
            if (hasPending) {
                LorebookInsertionSafetyArea(
                    state = insertion,
                    feedback = { feedback },
                    modifier = Modifier.heightIn(max = safetyHeightLimit).testTag("lorebook-insertion-safety-area"),
                    title = if (batch != null) "${batch.mode.label} · 已选 ${batch.entries.size} 条"
                        else "待插入 · ${pendingEntry?.group?.ifBlank { "未分组" }}",
                    onConfirm = { if (batch != null) onCommitBatch(it, null) else onInsertPending(it) },
                    onConfirmSingle = onCommitSingle,
                    longPressDrag = batch != null,
                    compact = batch != null,
                    enabled = batchEnabled,
                    cancelEnabled = !batchBusy,
                    idleHint = batchHint ?: if (batch != null) "长按右侧手柄整组拖动，选择同组落点；快捷插入首尾会立即保存"
                        else "安全区：拖动手柄选择同组位置；快捷插入首尾会立即保存",
                    onCancel = onCancelPending,
                    onInsertAtEdge = { if (batch != null) onCommitBatch(null, it) else onInsertPendingAtEdge(it) },
                ) { handleModifier, thumbnailHandle ->
                    if (batch != null) LorebookBatchPreviewContent(batch, handleModifier, onRemoveBatchEntry, !batchBusy, insertion, thumbnailHandle)
                    else if (pendingEntry != null) NewLorebookEntryPreview(
                        entry = pendingEntry,
                        handleModifier = handleModifier,
                        onEdit = { onEditEntry(pendingEntry) },
                        onUpdate = onUpdatePending,
                        onCancel = onCancelPending,
                    )
                }
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).testTag("lorebook-entry-list")
                    .onGloballyPositioned { listBounds = it.dragBoundsInRoot() },
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // During insertion, keep the existing entries in view instead of book metadata.
                // The draft is unchanged; the fields return as soon as insertion is resolved.
                if (!taskActive) item(key = "book-fields") {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) { header() }
                }
                item(key = "entry-controls") {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("条目（${book.entries.size}）", modifier = Modifier.weight(1f))
                        TextButton(onClick = { collapsed = !collapsed }) {
                            Text(if (collapsed) "展开条目" else "折叠条目")
                        }
                        TextButton(onClick = { onAddEntry("") }, enabled = !taskActive) { Text("新增") }
                    }
                }
                val grouped = visibleEntries.groupBy { it.group }
                val groups = grouped.keys.filter { it.isNotBlank() } + grouped.keys.filter { it.isBlank() }
                groups.forEachIndexed { index, group ->
                    val entries = grouped.getValue(group)
                    val expanded = group.isBlank() || group in expandedGroups
                    item(key = "group:$group") {
                        if (group.isBlank()) Text("未分组", style = MaterialTheme.typography.labelMedium)
                        else LorebookGroupSection(
                            groupName = group,
                            entries = entries,
                            groupIndex = index,
                            expanded = expanded,
                            onExpandedChange = {
                                expandedGroups = if (expanded) expandedGroups - group else expandedGroups + group
                            },
                            onGroupSettings = { onGroupSettings(group, entries) },
                            onAddEntry = { if (!taskActive) onAddEntry(group) },
                            canAddEntry = !taskActive,
                            canEditGroup = !taskActive,
                        )
                    }
                    if (expanded) items(entries, key = { it.id.toString() }) { entry ->
                        Box {
                            ReorderableItem(
                                reorderState,
                                entry.id.toString(),
                                enabled = draggingGroup == null || draggingGroup == group,
                            ) { dragging ->
                                Box(Modifier.graphicsLayer { alpha = if (dragging) 0.75f else 1f }) {
                                    RegexInjectionEntryCard(
                                        entry = entry,
                                        collapsed = collapsed,
                                        selection = {
                                            if (selectedIds != null) {
                                            val ids = selectedIds
                                            val selectedGroup = book.entries.firstOrNull { it.id in ids }?.group
                                            LorebookEntrySelection(
                                                entry.id in ids, entry.id in ids || selectedGroup == null || entry.group == selectedGroup,
                                                entry.name, { onSelectEntry(entry.id) },
                                            )
                                            }
                                        },
                                        dragHandle = {
                                            LorebookDragHandle(
                                                description = "拖动排序 ${entry.name}",
                                                modifier = Modifier.longPressDraggableHandle(
                                                    enabled = !taskActive,
                                                    onDragStarted = { draggingGroup = group },
                                                    onDragStopped = { draggingGroup = null; insertionId = null },
                                                ),
                                            )
                                        },
                                        onEdit = { onEditEntry(entry) },
                                        onDelete = { onEntriesChange(book.entries.filterNot { it.id == entry.id }) },
                                        onUpdate = { edited ->
                                            onEntriesChange(book.entries.map { if (it.id == edited.id) edited else it })
                                        },
                                    )
                                }
                            }
                            // Overlay the line: its appearance must not change item geometry under the pointer.
                            if (insertionId == entry.id ||
                                (feedback.placement?.targetId == entry.id && feedback.placement?.after == false)
                            ) {
                                HorizontalDivider(
                                    modifier = Modifier.align(Alignment.TopCenter),
                                    thickness = 3.dp,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            if (feedback.placement?.targetId == entry.id && feedback.placement?.after == true) {
                                HorizontalDivider(
                                    modifier = Modifier.align(Alignment.BottomCenter),
                                    thickness = 3.dp,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
                if (visibleEntries.isEmpty()) item(key = "empty") {
                    Text(if (hasPending) "此分组可使用快捷按钮插入首条条目" else "暂无条目")
                }
            }
        }
        if (batch != null) LorebookBatchDragOverlay(insertion, batch, rootOrigin)
    }
}

private fun LayoutCoordinates.dragBoundsInRoot(): LorebookDragBounds = boundsInRoot().let {
    LorebookDragBounds(it.left, it.top, it.right, it.bottom)
}

/** New-entry business UI only; geometry and gesture decisions belong to the safety container. */
@Composable
private fun NewLorebookEntryPreview(
    entry: PromptInjection.RegexInjection,
    handleModifier: Modifier,
    onEdit: () -> Unit,
    onUpdate: (PromptInjection.RegexInjection) -> Unit,
    onCancel: () -> Unit,
) {
    RegexInjectionEntryCard(
        entry = entry,
        dragHandle = {
            LorebookDragHandle("拖动插入 ${entry.name}", handleModifier)
        },
        onEdit = onEdit,
        onDelete = onCancel,
        onUpdate = onUpdate,
    )
}

/** The 20dp icon lives inside a real 36dp rail, without invisible horizontal hit expansion. */
@Composable
internal fun LorebookDragHandle(description: String, modifier: Modifier = Modifier) {
    val configuration = LocalViewConfiguration.current
    val railConfiguration = remember(configuration) {
        object : ViewConfiguration by configuration {
            override val minimumTouchTargetSize = DpSize(LorebookRailWidth, 0.dp)
        }
    }
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    CompositionLocalProvider(LocalViewConfiguration provides railConfiguration) {
        Box(
            Modifier.width(LorebookRailWidth).heightIn(min = 48.dp)
                .semantics { contentDescription = description }.then(modifier),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Box(Modifier.padding(end = 4.dp)) {
                Canvas(Modifier.size(20.dp).testTag("lorebook-drag-icon")) {
                    for (y in listOf(5.dp, 10.dp, 15.dp)) {
                        drawLine(color, Offset(1.dp.toPx(), y.toPx()), Offset(19.dp.toPx(), y.toPx()),
                            strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                    }
                }
            }
        }
    }
}
