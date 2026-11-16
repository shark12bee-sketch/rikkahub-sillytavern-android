package me.rerere.rikkahub.ui.pages.extensions

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Ref
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
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
    var pointer by remember { mutableStateOf<LorebookDragPoint?>(null) }
    var safetyBounds by remember { mutableStateOf<LorebookDragBounds?>(null) }
    var listBounds by remember { mutableStateOf<LorebookDragBounds?>(null) }
    val handleCoordinates = remember { Ref<LayoutCoordinates>() }
    val currentBook by rememberUpdatedState(book)
    val currentPending by rememberUpdatedState(pendingEntry)
    val currentInsertPending by rememberUpdatedState(onInsertPending)
    val edge = with(LocalDensity.current) { 64.dp.toPx() }
    val maxScrollVelocity = with(LocalDensity.current) { 720.dp.toPx() }
    val listState = rememberLazyListState()
    val feedback by remember(edge, maxScrollVelocity) {
        derivedStateOf {
            val entries = currentBook.entries.associateBy { it.id.toString() }
            val targets = listState.layoutInfo.visibleItemsInfo.mapNotNull { item ->
                entries[item.key]?.let { entry ->
                    val top = (listBounds?.top ?: 0f) + item.offset
                    LorebookDropTarget(entry.id, entry.group, top, top + item.size)
                }
            }
            evaluateLorebookEntryDrag(
                pointer = if (currentPending != null) pointer else null,
                safety = safetyBounds,
                viewport = listBounds,
                targets = targets,
                group = currentPending?.group.orEmpty(),
                edgeSize = edge,
                maxScrollVelocity = maxScrollVelocity,
                canScrollBackward = listState.canScrollBackward,
                canScrollForward = listState.canScrollForward,
            )
        }
    }

    // Only one scroll loop, running for the gesture's lifetime. Pointer moves never restart it.
    // The same feedback drives opacity, the insertion line and the scroll velocity.
    LaunchedEffect(pendingEntry?.id, pointer != null) {
        if (pointer == null) return@LaunchedEffect
        var previousFrame = withFrameNanos { it }
        while (pointer != null) {
            val frame = withFrameNanos { it }
            val seconds = (frame - previousFrame).coerceIn(0L, 50_000_000L) / 1_000_000_000f
            previousFrame = frame
            val velocity = feedback.scrollVelocity
            if (velocity != 0f) listState.scrollBy(velocity * seconds)
        }
    }
    LaunchedEffect(pendingEntry?.id) {
        pointer = null
        pendingEntry?.let { expandedGroups = (expandedGroups + it.group).distinct() }
    }

    fun updatePointer(position: Offset) {
        val coordinates = handleCoordinates.value?.takeIf { it.isAttached }
        pointer = coordinates?.localToRoot(position)?.let { LorebookDragPoint(it.x, it.y) }
    }

    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        val fromId = book.entries.firstOrNull { it.id.toString() == from.key }?.id
        val toId = book.entries.firstOrNull { it.id.toString() == to.key }?.id
        if (pendingEntry == null && fromId != null && toId != null) {
            val reordered = moveLorebookEntry(book.entries, fromId, toId)
            if (reordered != book.entries) {
                insertionId = fromId
                onEntriesChange(reordered)
            }
        }
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // This area is outside LazyColumn: scrolling never moves the gesture's coordinate origin.
        if (pendingEntry != null) {
            PendingLorebookSafetyArea(
                entry = pendingEntry,
                feedback = feedback,
                modifier = Modifier.onGloballyPositioned { safetyBounds = it.dragBoundsInRoot() },
                handleModifier = Modifier
                    .onGloballyPositioned { handleCoordinates.value = it }
                    .pointerInput(pendingEntry.id) {
                        detectDragGestures(
                            onDragStart = { position -> updatePointer(position) },
                            onDragCancel = { pointer = null },
                            onDragEnd = {
                                val target = feedback.placement
                                pointer = null
                                if (target != null) currentInsertPending(target)
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                // Read the pointer itself; never integrate deltas from a moving card.
                                updatePointer(change.position)
                            },
                        )
                    },
                onEdit = { onEditEntry(pendingEntry) },
                onUpdate = onUpdatePending,
                onCancel = onCancelPending,
                onInsertAtEdge = onInsertPendingAtEdge,
            )
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).onGloballyPositioned { listBounds = it.dragBoundsInRoot() },
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "book-fields") {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) { header() }
            }
            item(key = "entry-controls") {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("条目（${book.entries.size}）", modifier = Modifier.weight(1f))
                    TextButton(onClick = { collapsed = !collapsed }) {
                        Text(if (collapsed) "展开条目" else "折叠条目")
                    }
                    TextButton(onClick = { onAddEntry("") }, enabled = pendingEntry == null) { Text("新增") }
                }
            }
            val grouped = book.entries.groupBy { it.group }
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
                        onAddEntry = { if (pendingEntry == null) onAddEntry(group) },
                        canAddEntry = pendingEntry == null,
                        canEditGroup = pendingEntry == null,
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
                                    dragHandle = {
                                        Text(
                                            "☰",
                                            modifier = Modifier.size(40.dp)
                                                .semantics { contentDescription = "拖动排序 ${entry.name}" }
                                                .draggableHandle(
                                                    enabled = pendingEntry == null,
                                                    onDragStarted = { draggingGroup = group },
                                                    onDragStopped = { draggingGroup = null; insertionId = null },
                                                ),
                                            style = MaterialTheme.typography.titleLarge,
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
            if (book.entries.isEmpty()) item(key = "empty") { Text("暂无条目") }
        }
    }
}

private fun LayoutCoordinates.dragBoundsInRoot(): LorebookDragBounds = boundsInRoot().let {
    LorebookDragBounds(it.left, it.top, it.right, it.bottom)
}

@Composable
private fun PendingLorebookSafetyArea(
    entry: PromptInjection.RegexInjection,
    feedback: LorebookDragFeedback,
    modifier: Modifier,
    handleModifier: Modifier,
    onEdit: () -> Unit,
    onUpdate: (PromptInjection.RegexInjection) -> Unit,
    onCancel: () -> Unit,
    onInsertAtEdge: (Boolean) -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "待插入 · ${entry.group.ifBlank { "未分组" }}",
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val status = when (feedback.phase) {
                LorebookDragPhase.IDLE -> "安全区：向下拖动手柄选择同组位置，或快捷插入整本列表首尾"
                LorebookDragPhase.SAFE -> "安全区内 · 插入已暂停；向下拖出安全区继续"
                LorebookDragPhase.INSERTION -> if (feedback.placement != null) {
                    "正在选择插入位置 · 松手插入指示线处"
                } else "此处没有同组目标 · 请拖至所属分组"
                LorebookDragPhase.OUTSIDE -> "插入已暂停 · 请移回下方列表继续，或返回安全区"
            }
            Text(status, style = MaterialTheme.typography.bodySmall, minLines = 2, maxLines = 2)
            Box(Modifier.graphicsLayer {
                alpha = if (feedback.phase == LorebookDragPhase.INSERTION) 0.45f else 1f
            }) {
                RegexInjectionEntryCard(
                    entry = entry,
                    collapsed = true,
                    dragHandle = {
                        Text(
                            "☰",
                            modifier = Modifier.size(40.dp)
                                .semantics { contentDescription = "拖动插入 ${entry.name}" }
                                .then(handleModifier),
                            style = MaterialTheme.typography.titleLarge,
                        )
                    },
                    onEdit = onEdit,
                    onDelete = onCancel,
                    onUpdate = onUpdate,
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { onInsertAtEdge(false) }) { Text("插入最前") }
                TextButton(onClick = { onInsertAtEdge(true) }) { Text("插入最后") }
                TextButton(onClick = onCancel) { Text("取消待插入") }
            }
        }
    }
}
