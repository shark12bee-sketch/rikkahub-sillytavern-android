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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateMapOf
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.clearAndSetSemantics
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
import kotlin.uuid.Uuid

@Composable
internal fun LorebookEntryList(
    book: Lorebook,
    pendingEntry: PromptInjection.RegexInjection?,
    onCancelPending: () -> Unit,
    onInsertPending: (LorebookEntryPlacement?) -> Unit,
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
    onManageGroups: () -> Unit = {},
) {
    var collapsed by rememberSaveable(book.id.toString()) { mutableStateOf(false) }
    var expandedGroups by rememberSaveable(book.id.toString()) { mutableStateOf(emptyList<String>()) }
    val movingIds = if (batch?.mode == LorebookBatchMode.MOVE) batch.entries.map { it.id }.toSet() else emptySet()
    val visibleEntries = book.entries.filterNot { it.id in movingIds }
    val hasPending = pendingEntry != null || batch != null
    val taskActive = hasPending || selectedIds != null
    val insertion = remember(book.id, pendingEntry?.id, batch?.id) { LorebookInsertionState() }
    var listBounds by remember { mutableStateOf<LorebookDragBounds?>(null) }
    val currentGroup by rememberUpdatedState(pendingEntry?.group ?: batch?.group.orEmpty())
    val currentVisible by rememberUpdatedState(visibleEntries)
    val groupNames = lorebookEntryGroupNames(book)
    val currentGroups by rememberUpdatedState(groupNames + "")
    var targetGroup by rememberSaveable(book.id.toString(), pendingEntry?.id?.toString(), batch?.id?.toString()) {
        mutableStateOf(pendingEntry?.group ?: batch?.group.orEmpty())
    }
    LaunchedEffect(pendingEntry?.group) { if (pendingEntry != null) targetGroup = pendingEntry.group }
    fun revealDestination(placement: LorebookEntryPlacement) {
        placement.group?.let { group -> expandedGroups = (expandedGroups + group).distinct() }
    }
    val edge = with(LocalDensity.current) { 64.dp.toPx() }
    val maxScrollVelocity = with(LocalDensity.current) { 720.dp.toPx() }
    val listState = rememberLazyListState()
    val measuredTargets = remember(book.id) { mutableStateMapOf<String, LorebookDropTarget>() }
    val switchThreshold = with(LocalDensity.current) { 8.dp.toPx() }
    val feedback by remember(insertion, edge, maxScrollVelocity) {
        derivedStateOf {
            val entries = currentVisible.associateBy { it.id.toString() }
            val targets = listState.layoutInfo.visibleItemsInfo.mapNotNull { item ->
                val key = item.key as? String ?: return@mapNotNull null
                val target = measuredTargets[key]
                when {
                    target == null -> null
                    target.id != null && target.id.toString() !in entries -> null
                    target.id == null && target.group !in currentGroups -> null
                    else -> target
                }
            }
            val drag = insertion.drag
            val scrollReady = drag == null || drag.source != LorebookDragSource.LIST || drag.hasMoved
            evaluateLorebookEntryDrag(
                pointer = insertion.pointer,
                safety = insertion.safetyBounds,
                viewport = listBounds,
                targets = targets,
                edgeSize = edge,
                maxScrollVelocity = maxScrollVelocity,
                canScrollBackward = listState.canScrollBackward && scrollReady,
                canScrollForward = listState.canScrollForward && scrollReady,
                previous = insertion.previousPlacement,
                switchThreshold = switchThreshold,
                draggedEntryId = insertion.drag?.takeIf { it.source == LorebookDragSource.LIST }?.entryId,
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
                    onConfirm = { revealDestination(it); if (batch != null) onCommitBatch(it, null) else onInsertPending(it) },
                    onConfirmSingle = { id, placement -> revealDestination(placement); onCommitSingle(id, placement) },
                    longPressDrag = batch != null,
                    compact = batch != null,
                    enabled = batchEnabled,
                    cancelEnabled = !batchBusy,
                    idleHint = batchHint ?: if (batch != null) "长按右侧手柄整组拖动，选择条目或分组落点；快捷插入首尾会立即保存"
                        else "安全区：拖动手柄选择条目或分组落点；快捷插入首尾会立即保存",
                    instructions = (batchHint?.let { "$it\n" }.orEmpty()) + lorebookInsertionInstructions(batch?.mode),
                    headerActions = {
                        LorebookGroupTargetSelector(groupNames + listOfNotNull(pendingEntry?.group, batch?.group),
                            feedback.placement?.group ?: targetGroup, batchEnabled && insertion.pointer == null) { targetGroup = it }
                    },
                    onCancel = onCancelPending,
                    onInsertAtEdge = { end ->
                        val placement = LorebookEntryPlacement.groupEdge(targetGroup, end)
                        revealDestination(placement)
                        if (batch != null) onCommitBatch(placement, null) else onInsertPending(placement)
                    },
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
                        TextButton(onClick = onManageGroups, enabled = !batchBusy && insertion.pointer == null) { Text("管理分组") }
                        TextButton(onClick = { onAddEntry("") }, enabled = !taskActive) { Text("新增") }
                    }
                }
                val grouped = visibleEntries.groupBy { it.group.ifBlank { "" } }
                val groups = groupNames + ""
                groups.forEachIndexed { index, group ->
                    val entries = grouped[group].orEmpty()
                    val expanded = group.isBlank() || group in expandedGroups
                    item(key = "group:$group") {
                        DisposableEffect(group) { onDispose { measuredTargets.remove("group:$group") } }
                        Box(Modifier.fillMaxWidth().testTag("lorebook-group:$group").onGloballyPositioned { coordinates ->
                            val top = coordinates.localToRoot(Offset.Zero).y
                            measuredTargets["group:$group"] = LorebookDropTarget(null, group, top, top + coordinates.size.height)
                        }) {
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
                            canEditGroup = !taskActive && entries.isNotEmpty(),
                        )
                        if (feedback.placement?.targetId == null && feedback.placement?.group == group) {
                            HorizontalDivider(Modifier.align(Alignment.BottomCenter), thickness = 3.dp, color = MaterialTheme.colorScheme.primary)
                        }
                        }
                    }
                    if (expanded) items(entries, key = { it.id.toString() }) { entry ->
                        var cardCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
                        DisposableEffect(entry.id) { onDispose { measuredTargets.remove(entry.id.toString()) } }
                        Box(Modifier.animateItem().onGloballyPositioned { coordinates ->
                            cardCoordinates = coordinates
                            val top = coordinates.localToRoot(Offset.Zero).y
                            measuredTargets[entry.id.toString()] = LorebookDropTarget(entry.id, group, top, top + coordinates.size.height)
                        }) {
                            Box(Modifier.promptDragPlaceholder(insertion.drag?.let {
                                it.source == LorebookDragSource.LIST && it.entryId == entry.id && !it.released
                            } == true)) {
                                RegexInjectionEntryCard(
                                    entry = entry,
                                    collapsed = collapsed,
                                    selection = {
                                        if (selectedIds != null) {
                                            val selectedGroup = book.entries.firstOrNull { it.id in selectedIds }?.group?.ifBlank { "" }
                                            LorebookEntrySelection(entry.id in selectedIds,
                                                !batchBusy && (entry.id in selectedIds || selectedGroup == null || group == selectedGroup),
                                                entry.name, { onSelectEntry(entry.id) })
                                        }
                                    },
                                    dragHandle = {
                                        LorebookDragHandle("拖动排序 ${entry.name}",
                                            lorebookInsertionHandle(insertion, !taskActive && !batchBusy, true,
                                                feedback = { feedback },
                                                onConfirm = { id, placement ->
                                                    if (id != null) {
                                                        revealDestination(placement)
                                                        onEntriesChange(moveLorebookEntry(book.entries, id, placement))
                                                    }
                                                },
                                                entryId = entry.id, source = LorebookDragSource.LIST,
                                                origin = {
                                                    cardCoordinates?.takeIf { it.isAttached }?.let { coordinates ->
                                                        val origin = coordinates.localToRoot(Offset.Zero)
                                                        androidx.compose.ui.geometry.Rect(origin, androidx.compose.ui.geometry.Size(coordinates.size.width.toFloat(), coordinates.size.height.toFloat()))
                                                    }
                                                }))
                                    },
                                    onEdit = { onEditEntry(entry) },
                                    onDelete = { onEntriesChange(book.entries.filterNot { it.id == entry.id }) },
                                    onUpdate = { edited -> onEntriesChange(book.entries.map { if (it.id == edited.id) edited else it }) },
                                )
                            }
                            // Overlay the line: its appearance must not change item geometry under the pointer.
                            if (feedback.placement?.targetId == entry.id && feedback.placement?.after == false) {
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
        if (insertion.drag?.source == LorebookDragSource.LIST) {
            val drag = checkNotNull(insertion.drag)
            val visualEntry = remember(drag.token) { book.entries.firstOrNull { it.id == drag.entryId } }
            val visualCollapsed = remember(drag.token) { collapsed }
            if (visualEntry != null) LorebookInsertionOverlay(insertion, rootOrigin) {
                val width = with(LocalDensity.current) { drag.origin.width.toDp() }
                Box(Modifier.width(width).shadow(4.dp, MaterialTheme.shapes.large).clearAndSetSemantics {}) {
                    RegexInjectionEntryCard(visualEntry, collapsed = visualCollapsed,
                        dragHandle = { LorebookDragHandle("移动中的条目") }, onEdit = {}, onDelete = {})
                }
            }
        } else if (batch != null) LorebookBatchDragOverlay(insertion, batch, rootOrigin)
        else if (pendingEntry != null) LorebookInsertionOverlay(insertion, rootOrigin) {
            val width = with(LocalDensity.current) { checkNotNull(insertion.drag).origin.width.toDp() }
            Box(Modifier.width(width).shadow(4.dp, MaterialTheme.shapes.large).clearAndSetSemantics {}) {
                RegexInjectionEntryCard(pendingEntry,
                    dragHandle = { LorebookDragHandle("移动中的新增条目") }, onEdit = {}, onDelete = {})
            }
        }
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
    PromptPendingEntrance(entry.id) {
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
