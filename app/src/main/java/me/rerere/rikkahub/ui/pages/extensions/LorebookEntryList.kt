package me.rerere.rikkahub.ui.pages.extensions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import me.rerere.rikkahub.data.model.Lorebook
import me.rerere.rikkahub.data.model.PromptInjection
import sh.calvin.reorderable.ReorderableItem
import kotlinx.coroutines.delay
import sh.calvin.reorderable.rememberReorderableLazyListState
import kotlin.uuid.Uuid

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun LorebookEntryList(
    book: Lorebook,
    pendingEntry: PromptInjection.RegexInjection?,
    onCancelPending: () -> Unit,
    onInsertPending: (LorebookEntryPlacement?) -> Unit,
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
    var placement by remember(pendingEntry) { mutableStateOf<LorebookEntryPlacement?>(null) }
    var pendingDragging by remember { mutableStateOf(false) }
    var pointerY by remember { mutableStateOf(0f) }
    var handleTop by remember { mutableStateOf(0f) }
    var listTop by remember { mutableStateOf(0f) }
    var pendingBottom by remember { mutableStateOf(0f) }
    val currentBook by rememberUpdatedState(book)
    val currentInsertPending by rememberUpdatedState(onInsertPending)
    val edge = with(LocalDensity.current) { 64.dp.toPx() }
    val scrollStep = with(LocalDensity.current) { 12.dp.toPx() }
    val listState = rememberLazyListState()
    fun updatePlacement() {
        val pending = pendingEntry ?: return
        val y = pointerY - listTop
        if (pointerY < pendingBottom || y > listState.layoutInfo.viewportEndOffset) {
            placement = null
            return
        }
        val target = listState.layoutInfo.visibleItemsInfo
            .filter { info -> currentBook.entries.any { it.id.toString() == info.key } }
            .minByOrNull { kotlin.math.abs(y - (it.offset + it.size / 2f)) }
        placement = target?.takeIf { info ->
            currentBook.entries.any { it.id.toString() == info.key && it.group == pending.group }
        }?.let {
            LorebookEntryPlacement(Uuid.parse(it.key as String), y > it.offset + it.size / 2f)
        }
    }
    LaunchedEffect(pendingDragging) {
        while (pendingDragging) {
            val layout = listState.layoutInfo
            val y = pointerY - listTop
            val speed = when {
                pointerY < pendingBottom + edge -> -scrollStep
                y > layout.viewportEndOffset - edge -> scrollStep
                else -> 0f
            }
            if (speed != 0f) listState.scrollBy(speed)
            updatePlacement()
            delay(16)
        }
    }
    LaunchedEffect(pendingEntry?.id) {
        if (pendingEntry != null) {
            expandedGroups = (expandedGroups + pendingEntry.group).distinct()
            listState.scrollToItem(0)
        }
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
    LazyColumn(
        state = listState,
        modifier = modifier.onGloballyPositioned { listTop = it.positionInRoot().y },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "book-fields") { Column(verticalArrangement = Arrangement.spacedBy(16.dp)) { header() } }
        item(key = "entry-controls") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("条目（${book.entries.size}）", modifier = Modifier.weight(1f))
                TextButton(onClick = { collapsed = !collapsed }) { Text(if (collapsed) "展开条目" else "折叠条目") }
                TextButton(onClick = { onAddEntry("") }, enabled = pendingEntry == null) { Text("新增") }
            }
        }
        // Insertion is a drop operation, not a reorder: keep the draft outside book.entries.
        if (pendingEntry != null) stickyHeader(key = pendingEntry.id.toString()) {
                Column(Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .onGloballyPositioned { pendingBottom = it.positionInRoot().y + it.size.height }
                    .padding(8.dp).graphicsLayer { alpha = if (pendingDragging) 0.8f else 1f }) {
                    Text("待插入 · ${pendingEntry.group.ifBlank { "未分组" }}", style = MaterialTheme.typography.titleSmall)
                    Text("拖动右侧手柄选择同组插入位置；尚未写入世界书", style = MaterialTheme.typography.bodySmall)
                    RegexInjectionEntryCard(
                        entry = pendingEntry,
                        collapsed = true,
                        dragHandle = {
                            Text("☰", Modifier.size(40.dp).semantics { contentDescription = "拖动插入 ${pendingEntry.name}" }
                                .onGloballyPositioned { handleTop = it.positionInRoot().y }
                                .pointerInput(pendingEntry) {
                                    detectDragGestures(
                                        onDragStart = { offset -> pointerY = handleTop + offset.y; pendingDragging = true },
                                        onDragCancel = { pendingDragging = false; placement = null },
                                        onDragEnd = {
                                            pendingDragging = false
                                            if (placement != null) currentInsertPending(placement)
                                            placement = null
                                        },
                                        onDrag = { change, delta ->
                                            change.consume()
                                            pointerY += delta.y
                                            updatePlacement()
                                        },
                                    )
                                })
                        },
                        onEdit = { onEditEntry(pendingEntry) },
                        onDelete = onCancelPending,
                        onUpdate = onUpdatePending,
                    )
                    Row {
                        TextButton(onClick = onCancelPending) { Text("取消待插入") }
                        if (book.entries.none { it.group == pendingEntry.group }) {
                            TextButton(onClick = { onInsertPending(null) }) { Text("插入第一条") }
                        }
                    }
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
                Column {
                    if (insertionId == entry.id) HorizontalDivider(thickness = 3.dp, color = MaterialTheme.colorScheme.primary)
                ReorderableItem(reorderState, entry.id.toString(), enabled = draggingGroup == null || draggingGroup == group) { dragging ->
                    Column(Modifier.graphicsLayer { alpha = if (dragging) 0.75f else 1f }) {
                        if (placement?.targetId == entry.id && placement?.after == false) {
                            HorizontalDivider(thickness = 3.dp, color = MaterialTheme.colorScheme.primary)
                        }
                        RegexInjectionEntryCard(
                            entry = entry,
                            collapsed = collapsed,
                            dragHandle = {
                                Text(
                                    "☰",
                                    modifier = Modifier.size(40.dp).semantics { contentDescription = "拖动排序 ${entry.name}" }
                                        .draggableHandle(
                                            enabled = pendingEntry == null,
                                            onDragStarted = { draggingGroup = group },
                                            onDragStopped = {
                                                draggingGroup = null
                                                insertionId = null
                                            },
                                        ),
                                    style = MaterialTheme.typography.titleLarge,
                                )
                            },
                            onEdit = { onEditEntry(entry) },
                            onDelete = { onEntriesChange(book.entries.filterNot { it.id == entry.id }) },
                            onUpdate = { edited -> onEntriesChange(book.entries.map { if (it.id == edited.id) edited else it }) },
                        )
                        if (placement?.targetId == entry.id && placement?.after == true) {
                            HorizontalDivider(thickness = 3.dp, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                }
            }
        }
        if (book.entries.isEmpty()) item(key = "empty") { Text("暂无条目") }
    }
}
