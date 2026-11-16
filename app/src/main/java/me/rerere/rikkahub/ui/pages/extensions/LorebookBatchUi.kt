package me.rerere.rikkahub.ui.pages.extensions

import android.icu.text.BreakIterator
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.data.model.PromptInjection
import java.util.Locale
import kotlin.uuid.Uuid

internal enum class LorebookBatchMode(val label: String) { MOVE("批量移动"), COPY("跨书复制") }

internal data class LorebookBatchPreview(
    val id: Uuid,
    val mode: LorebookBatchMode,
    val entries: List<PromptInjection.RegexInjection>,
) {
    val group: String get() = entries.firstOrNull()?.group.orEmpty()
}

/** ICU character boundaries preserve emoji sequences and combining marks, not just UTF-16 units. */
internal fun lorebookThumbnailCharacter(name: String): String {
    val text = name.trim()
    if (text.isEmpty()) return "?"
    val iterator = BreakIterator.getCharacterInstance(Locale.ROOT)
    iterator.setText(text)
    return text.substring(0, iterator.next())
}

@Composable
internal fun LorebookBatchPreviewContent(
    preview: LorebookBatchPreview,
    handleModifier: Modifier,
    onRemove: (Uuid) -> Unit,
    enabled: Boolean = true,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        LazyRow(
            modifier = Modifier.weight(1f).testTag("lorebook-batch-thumbnails"),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(preview.entries, key = { it.id }) { entry ->
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.secondaryContainer) {
                    Box(Modifier.size(64.dp)) {
                        Text(
                            lorebookThumbnailCharacter(entry.name),
                            Modifier.align(Alignment.Center),
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Clip,
                        )
                        Box(
                            Modifier.align(Alignment.TopEnd).size(28.dp)
                                .clickable(enabled = enabled) { onRemove(entry.id) }
                                .semantics { contentDescription = "移除选择 ${entry.name}" },
                            contentAlignment = Alignment.Center,
                        ) { Text("×", style = MaterialTheme.typography.titleMedium) }
                    }
                }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            LorebookDragHandle("长按拖动 ${preview.entries.size} 条", handleModifier)
            Text("${preview.entries.size} 条", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
internal fun LorebookSelectionControls(
    entries: List<PromptInjection.RegexInjection>,
    selected: Set<Uuid>,
    mode: LorebookBatchMode,
    onSelect: (Set<Uuid>) -> Unit,
    onDone: () -> Unit,
    onCancel: () -> Unit,
) {
    val actual = orderedLorebookSelection(entries, selected)
    val group = actual.firstOrNull()?.group ?: entries.firstOrNull()?.group
    val inGroup = entries.filter { it.group == group }.map { it.id }.toSet()
    Column {
        Text("${mode.label} · 已选 ${actual.size} 条", style = MaterialTheme.typography.labelLarge)
        Text("一次仅选择同一分组；全选范围：${group?.ifBlank { "未分组" } ?: "暂无条目"}",
            style = MaterialTheme.typography.bodySmall)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { onSelect(if (selected == inGroup) emptySet() else inGroup) }, enabled = inGroup.isNotEmpty()) {
                Text(if (selected == inGroup && selected.isNotEmpty()) "取消全选" else "全选该组")
            }
            TextButton(onClick = onCancel) { Text("取消批量操作") }
            TextButton(onClick = onDone, enabled = actual.isNotEmpty() && actual.map { it.group }.distinct().size == 1) {
                Text("完成选择")
            }
        }
    }
}

@Composable
internal fun LorebookEntrySelection(checked: Boolean, enabled: Boolean, name: String, onCheckedChange: () -> Unit) {
    Checkbox(
        checked = checked,
        onCheckedChange = { onCheckedChange() },
        enabled = enabled,
        modifier = Modifier.padding(top = 8.dp).size(48.dp)
            .semantics { contentDescription = "选择条目 $name" },
    )
}
