package me.rerere.rikkahub.ui.pages.extensions

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.data.model.*
import kotlin.uuid.Uuid

@Composable
internal fun LorebookCategorySelector(book: Lorebook, selected: Uuid?, enabled: Boolean = true,
    label: String = "分类", onSelect: (Uuid?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }, enabled = enabled) {
            Text("$label：${book.categories.firstOrNull { it.id == selected }?.name ?: "未分类"}")
        }
        DropdownMenu(expanded, { expanded = false }) {
            DropdownMenuItem(text = { Text("未分类") }, onClick = { expanded = false; onSelect(null) })
            book.categories.forEach { category ->
                DropdownMenuItem(text = { Text(category.name) }, onClick = { expanded = false; onSelect(category.id) })
            }
        }
    }
}

@Composable
internal fun LorebookCategoryManagement(book: Lorebook, onEdit: (Lorebook) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<Uuid?>(null) }
    deleting?.let { id ->
        val count = book.entries.count { it.categoryId == id }
        AlertDialog(onDismissRequest = { deleting = null }, title = { Text("删除分类？") },
            text = { Text("分类中的 $count 条条目会留在原位置，变为未分类。条目及互斥组不会删除。") },
            confirmButton = { TextButton(onClick = { onEdit(deleteLorebookCategory(book, id)); deleting = null }) { Text("确认删除分类") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消") } })
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("管理分类组") }, text = {
        Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
            OutlinedTextField(name, { name = it; error = null }, label = { Text("新分类名称") }, singleLine = true)
            TextButton(onClick = {
                runCatching { createLorebookCategory(book, name) }.onSuccess { onEdit(it); name = "" }.onFailure { error = it.message }
            }) { Text("创建分类") }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            book.categories.forEach { category ->
                var edited by remember(category.id, category.name) { mutableStateOf(category.name) }
                OutlinedTextField(edited, { edited = it }, label = { Text("${book.entries.count { it.categoryId == category.id }} 条") }, singleLine = true)
                Row {
                    TextButton(onClick = {
                        runCatching { renameLorebookCategory(book, category.id, edited) }.onSuccess(onEdit).onFailure { error = it.message }
                    }) { Text("重命名") }
                    TextButton(onClick = { deleting = category.id }) { Text("删除分类") }
                }
            }
            Text("分类仅用于组织和排序。互斥组及触发设置独立保留。")
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } })
}
