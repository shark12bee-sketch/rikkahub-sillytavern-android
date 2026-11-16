package me.rerere.rikkahub.ui.pages.extensions

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.data.model.Lorebook

@Composable
internal fun LorebookGroupTargetSelector(groups: List<String>, selected: String, enabled: Boolean, prefix: String = "范围", tag: String = "lorebook-target-group", onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 32.dp) {
            TextButton(onClick = { expanded = true }, enabled = enabled,
                modifier = Modifier.height(32.dp).widthIn(max = 112.dp).testTag(tag),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)) {
                Text("$prefix：${groupLabel(selected)}", maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelSmall)
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            (groups + selected + "").map { it.ifBlank { "" } }.distinct().forEach { group ->
                DropdownMenuItem(text = { Text("组内：${groupLabel(group)}") },
                    onClick = { expanded = false; onSelect(group) })
            }
        }
    }
}

/** Names only: these operations never create dummy entries or change trigger configuration. */
@Composable
internal fun LorebookGroupManagement(book: Lorebook, onEdit: (Lorebook) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("管理分组") }, text = {
        Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
            OutlinedTextField(name, { name = it; error = null }, modifier = Modifier.testTag("lorebook-group-name"), label = { Text("新分组名称") }, singleLine = true)
            TextButton(onClick = {
                runCatching { createLorebookEntryGroup(book, name) }.onSuccess { onEdit(it); name = "" }
                    .onFailure { error = it.message }
            }) { Text("创建分组") }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            val groups = lorebookEntryGroupNames(book)
            groups.forEachIndexed { index, group ->
                Row(Modifier.fillMaxWidth()) {
                    val count = book.entries.count { it.group == group }
                    Text("$group · $count 条", Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    TextButton(onClick = {
                        val order = groups.toMutableList().apply { add(index - 1, removeAt(index)) }
                        onEdit(book.copy(entryGroups = order))
                    }, enabled = index > 0) { Text("上移") }
                    if (count == 0) TextButton(onClick = { onEdit(book.copy(entryGroups = groups - group)) }) { Text("删除空组") }
                }
            }
            Text("分组仅记录名称与显示顺序；空组可保存。移动条目请使用批量管理。", style = MaterialTheme.typography.bodySmall)
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("完成分组管理") } })
}
