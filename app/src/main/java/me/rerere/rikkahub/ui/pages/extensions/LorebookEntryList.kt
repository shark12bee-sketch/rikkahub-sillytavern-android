package me.rerere.rikkahub.ui.pages.extensions

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.data.model.Lorebook
import me.rerere.rikkahub.data.model.PromptInjection
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
internal fun LorebookEntryList(
    book: Lorebook,
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
    var insertionId by remember { mutableStateOf<kotlin.uuid.Uuid?>(null) }
    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        val fromId = book.entries.firstOrNull { it.id.toString() == from.key }?.id
        val toId = book.entries.firstOrNull { it.id.toString() == to.key }?.id
        if (fromId != null && toId != null) {
            val reordered = moveLorebookEntry(book.entries, fromId, toId)
            if (reordered != book.entries) {
                insertionId = fromId
                onEntriesChange(reordered)
            }
        }
    }
    LazyColumn(
        state = listState,
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "book-fields") { Column(verticalArrangement = Arrangement.spacedBy(16.dp)) { header() } }
        item(key = "entry-controls") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("条目（${book.entries.size}）", modifier = Modifier.weight(1f))
                TextButton(onClick = { collapsed = !collapsed }) { Text(if (collapsed) "展开条目" else "折叠条目") }
                TextButton(onClick = { onAddEntry("") }) { Text("新增") }
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
                    onAddEntry = { onAddEntry(group) },
                )
            }
            if (expanded) items(entries, key = { it.id.toString() }) { entry ->
                ReorderableItem(reorderState, entry.id.toString(), enabled = draggingGroup == null || draggingGroup == group) { dragging ->
                    Column(Modifier.graphicsLayer { alpha = if (dragging) 0.75f else 1f }) {
                        if (insertionId == entry.id) HorizontalDivider(thickness = 3.dp, color = MaterialTheme.colorScheme.primary)
                        RegexInjectionEntryCard(
                            entry = entry,
                            collapsed = collapsed,
                            dragHandle = {
                                Text(
                                    "☰",
                                    modifier = Modifier.size(40.dp).semantics { contentDescription = "拖动排序 ${entry.name}" }
                                        .draggableHandle(
                                            onDragStarted = { draggingGroup = group },
                                            onDragStopped = { draggingGroup = null; insertionId = null },
                                        ),
                                    style = MaterialTheme.typography.titleLarge,
                                )
                            },
                            onEdit = { onEditEntry(entry) },
                            onDelete = { onEntriesChange(book.entries.filterNot { it.id == entry.id }) },
                            onUpdate = { edited -> onEntriesChange(book.entries.map { if (it.id == edited.id) edited else it }) },
                        )
                    }
                }
            }
        }
        if (book.entries.isEmpty()) item(key = "empty") { Text("暂无条目") }
    }
}
