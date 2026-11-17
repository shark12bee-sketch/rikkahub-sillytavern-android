package me.rerere.rikkahub.ui.pages.extensions

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import me.rerere.rikkahub.data.model.*

/** Legacy competition controls stay separate from category drag targets and root ordering. */
@Composable
internal fun LorebookExclusiveGroups(book: Lorebook, onEdit: (Lorebook) -> Unit,
    onEditEntry: (PromptInjection.RegexInjection) -> Unit, onAddEntry: (String) -> Unit,
    onGroupSettings: (String, List<PromptInjection.RegexInjection>) -> Unit, onManage: () -> Unit,
    insertion: LorebookInsertionState, feedback: () -> LorebookDragFeedback,
    allowReorder: Boolean = true, hiddenIds: Set<kotlin.uuid.Uuid> = emptySet(),
    onMeasure: (String, LorebookDropTarget?) -> Unit) {
    var expanded by remember(book.id) { mutableStateOf(emptySet<String>()) }
    var showInfo by remember { mutableStateOf(false) }
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("互斥组", style = MaterialTheme.typography.titleMedium)
            Box {
                TextButton(onClick = { showInfo = !showInfo }) { Text("ⓘ") }
                if (showInfo) LorebookReadOnlyPopup("互斥组说明", "lorebook-exclusive-help", "关闭说明", { showInfo = false }) {
                    Text("互斥组沿用原 group 规则：同组候选按照粘性、评分、覆盖及权重选择，不保证所有启用条目同时注入。分类组仅用于整理和排序；同一条目可以同时属于分类组和互斥组。")
                }
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onManage) { Text("管理互斥组") }
        }
        lorebookEntryGroupNames(book).forEachIndexed { index, group ->
            val entries = book.entries.filter { it.group == group && it.id !in hiddenIds }
            val key = "exclusive:$group"
            var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
            DisposableEffect(key) { onDispose { onMeasure(key, null) } }
            Box(Modifier.testTag("lorebook-group:$group").onGloballyPositioned { c ->
                coordinates = c
                val top = c.localToRoot(Offset.Zero).y
                onMeasure(key, LorebookDropTarget(null, key, top, top + c.size.height))
            }) {
            LorebookGroupSection(group, entries, { onGroupSettings(group, entries) }, { onAddEntry(group) },
                canAddEntry = allowReorder, canEditGroup = allowReorder && entries.isNotEmpty(), groupIndex = index, expanded = group in expanded,
                onExpandedChange = { expanded = if (group in expanded) expanded - group else expanded + group },
                headerGesture = if (!allowReorder) null else lorebookInsertionHandle(insertion, true, true, feedback,
                    onConfirm = { _, placement -> placement.group?.removePrefix("exclusive:")?.let { onEdit(moveLorebookGroup(book, group, it, placement.after)) } },
                    onTap = { expanded = if (group in expanded) expanded - group else expanded + group },
                    source = LorebookDragSource.GROUP, groupName = key,
                    origin = { coordinates?.takeIf { it.isAttached }?.let { Rect(it.localToRoot(Offset.Zero), Size(it.size.width.toFloat(), it.size.height.toFloat())) } })
                    .semantics { contentDescription = "拖动分组 $group" })
                val placement = feedback().placement
                if (placement?.categoryTarget == false && (placement.group == group || placement.group == key))
                    HorizontalDivider(Modifier.align(if (placement.after) Alignment.BottomCenter else Alignment.TopCenter), thickness = 3.dp, color = MaterialTheme.colorScheme.primary)
            }
            if (group in expanded) entries.forEach { entry ->
                RegexInjectionEntryCard(entry, onEdit = { onEditEntry(entry) },
                    onDelete = { onEdit(book.withLorebookEntries(book.entries.filterNot { it.id == entry.id })) },
                    onUpdate = { value -> onEdit(book.withLorebookEntries(book.entries.map { if (it.id == value.id) value else it })) })
            }
        }
        if (lorebookEntryGroupNames(book).isNotEmpty()) {
            DisposableEffect(book.id) { onDispose { onMeasure("exclusive:", null) } }
            Text("拖到此处移出互斥组", style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).testTag("lorebook-group:").onGloballyPositioned { c ->
                    val top = c.localToRoot(Offset.Zero).y
                    onMeasure("exclusive:", LorebookDropTarget(null, "exclusive:", top, top + c.size.height))
                })
        }

    }
}
