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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.data.model.PromptInjection
import java.util.Locale
import kotlin.uuid.Uuid

internal enum class LorebookBatchMode(val label: String) { MOVE("批量移动"), COPY("跨书复制"), DELETE("批量删除") }

internal data class LorebookBatchPreview(
    val id: Uuid,
    val mode: LorebookBatchMode,
    val entries: List<PromptInjection.RegexInjection>,
) {
    val group: String get() = entries.map { it.group.ifBlank { "" } }.distinct().singleOrNull().orEmpty()
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
    insertion: LorebookInsertionState? = null,
    thumbnailHandle: @Composable (Uuid, () -> Unit) -> Modifier = { _, tap -> Modifier.clickable(enabled = enabled, onClick = tap) },
) {
    val configuration = LocalViewConfiguration.current
    val removeConfiguration = remember(configuration) {
        object : ViewConfiguration by configuration {
            override val minimumTouchTargetSize = DpSize(24.dp, 24.dp)
        }
    }
    var previewId by remember(preview.id) { mutableStateOf<Uuid?>(null) }
    val thumbnails = rememberLazyListState()
    SideEffect { insertion?.previewWindow = LorebookThumbnailWindow(thumbnails.firstVisibleItemIndex, thumbnails.firstVisibleItemScrollOffset) }
    LaunchedEffect(insertion?.pointer != null) { if (insertion?.pointer != null) previewId = null }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        LazyRow(
            state = thumbnails,
            modifier = Modifier.weight(1f).testTag("lorebook-batch-thumbnails")
                .onGloballyPositioned { insertion?.previewBounds = it.boundsInRoot() },
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(preview.entries, key = { it.id }) { entry ->
                val drag = insertion?.drag
                val lifted = drag != null && !drag.released && (drag.entryId == null || drag.entryId == entry.id)
                Box {
                    Box(Modifier.size(48.dp).testTag("lorebook-thumbnail-${entry.id}")
                        .then(thumbnailHandle(entry.id) { previewId = entry.id })
                        .promptDragPlaceholder(lifted)) {
                        LorebookThumbnailFace(entry)
                        // The corner button must not expand its hit area across the preview/drag tile.
                        CompositionLocalProvider(LocalViewConfiguration provides removeConfiguration) { Box(
                            Modifier.align(Alignment.TopEnd).size(24.dp)
                                .clickable(enabled = enabled && insertion?.pointer == null) { onRemove(entry.id) }
                                .semantics { contentDescription = "移除选择 ${entry.name}" },
                            contentAlignment = Alignment.Center,
                        ) { Text("×", style = MaterialTheme.typography.titleMedium) } }
                    }
                    if (previewId == entry.id) LorebookEntryPreviewPopup(entry) { previewId = null }
                }
            }
        }
        LorebookDragHandle("长按拖动 ${preview.entries.size} 条", handleModifier)
    }
}

@Composable
internal fun LorebookThumbnailFace(entry: PromptInjection.RegexInjection, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.size(48.dp), shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.secondaryContainer) {
        Box(contentAlignment = Alignment.Center) {
            Text(lorebookThumbnailCharacter(entry.name), Modifier.padding(top = 6.dp),
                style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Clip)
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
    groups: List<String> = emptyList(),
    onMoveToGroup: ((String) -> Unit)? = null,
    enabled: Boolean = true,
) {
    val actual = orderedLorebookSelection(entries, selected)
    val group = lorebookSelectionGroup(entries, selected)
    val inGroup = entries.filter { it.group.ifBlank { "" } == group }.map { it.id }.toSet()
    val oneGroup = actual.map { it.group.ifBlank { "" } }.distinct().size <= 1
    var destination by remember { mutableStateOf("") }
    Column {
        Text("${mode.label} · 已选 ${actual.size} 条", style = MaterialTheme.typography.labelLarge)
        Text("一次仅选择同一分组；全选范围：${group?.let(::groupLabel) ?: "暂无条目"}", style = MaterialTheme.typography.bodySmall)
        if (!oneGroup) Text("当前选择跨组，请先取消其它分组的勾选", color = MaterialTheme.colorScheme.error)
        if (mode == LorebookBatchMode.COPY) Text(
            "完成后返回世界书列表。来源书不会修改，来源草稿的未保存修改不会写入。",
            style = MaterialTheme.typography.bodySmall,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { onSelect(toggleLorebookScope(entries, selected)) }, enabled = enabled && inGroup.isNotEmpty()) {
                Text(if (inGroup.isNotEmpty() && selected.containsAll(inGroup)) "取消全选" else "全选该组")
            }
            TextButton(onClick = onCancel, enabled = enabled) { Text("取消批量操作") }
            TextButton(onClick = onDone, enabled = enabled && oneGroup && actual.isNotEmpty()) {
                Text(if (mode == LorebookBatchMode.DELETE) "删除所选" else "完成选择")
            }
        }
        if (mode == LorebookBatchMode.MOVE && onMoveToGroup != null) Row {
            LorebookGroupTargetSelector(groups, destination, enabled, prefix = "目标") { destination = it }
            TextButton(onClick = { onMoveToGroup(destination) }, enabled = enabled && oneGroup && actual.isNotEmpty()) { Text("移入分组") }
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

/** The same safety container stays above the book list while no destination sheet is open. */
@Composable
internal fun LorebookCopyShelf(task: LorebookCopyTask, busy: Boolean, onRemove: (Uuid) -> Unit, onCancel: () -> Unit) {
    val state = remember(task.id) { LorebookInsertionState() }
    LorebookInsertionSafetyArea(
        state = state,
        feedback = { LorebookDragFeedback(LorebookDragPhase.IDLE) },
        title = "待复制 · 已选 ${task.entries.size} 条",
        idleHint = "请选择另一份世界书，再确认落点；保留来源分组和触发配置",
        instructions = "请选择另一份世界书，再确认落点。\n" + lorebookInsertionInstructions(LorebookBatchMode.COPY),
        onConfirm = {}, onInsertAtEdge = {}, onCancel = onCancel,
        enabled = false, cancelEnabled = !busy, longPressDrag = true, compact = true,
        modifier = Modifier.fillMaxWidth().testTag("lorebook-copy-shelf"),
    ) { handle, thumbnail -> LorebookBatchPreviewContent(task.preview(), handle, onRemove, !busy, state, thumbnail) }
}

internal fun lorebookCopyConfirmation(request: LorebookBatchRequest): String {
    val action = request.action as LorebookBatchAction.Copy
    val task = action.task.copy(entries = orderedLorebookSelection(action.task.entries, action.ids))
    val group = request.placement?.group ?: task.entries.first().group
    val existingGroups = (request.draft.entryGroups + request.draft.entries.flatMap { entry ->
        listOf(entry.group) + entry.inclusionGroup.split(",").map { it.trim() }
    }).toSet()
    val grouping = when {
        group.isBlank() -> "目标为未分组；来源分组不写回，其他配置保留。"
        group in existingGroups -> "目标为「$group」分组；目标已有同名的普通或包含分组，按现有规则参与触发与竞争。"
        else -> "在目标中新建同名分组「$group」，保留原分组设置。"
    }
    val inclusionGroups = task.entries.flatMap { it.inclusionGroup.split(",") }
        .map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    val inclusion = if (inclusionGroups.isEmpty()) "" else {
        "\n同时保留包含分组：${inclusionGroups.joinToString("、")}。同名者仍按现有触发与竞争规则处理。"
    }
    val position = when (request.atEnd) {
        true -> "整本列表最后"
        false -> "整本列表最前"
        null -> if (request.placement?.targetId == null && request.placement?.group != null) {
            "「${groupLabel(group)}」组内${if (request.placement.after) "最后" else "最前"}"
        } else request.draft.entries.firstOrNull { it.id == request.placement?.targetId }?.let {
            "「${it.name.ifBlank { "未命名条目" }}」${if (request.placement?.after == true) "之后" else "之前"}"
        } ?: "所选位置"
    }
    return "将 ${task.entries.size} 条复制到「${request.draft.name.ifBlank { "未命名世界书" }}」的$position。\n$grouping$inclusion\n来源世界书不变；副本生成新 ID，其余条目配置完整保留。当前目标书草稿也会一并保存。"
}

internal fun lorebookInsertionInstructions(mode: LorebookBatchMode?): String = when (mode) {
    LorebookBatchMode.DELETE -> "选择同一分组的条目，确认数量后删除；取消不修改数据。"
    null -> "拖动右侧手柄拿起新增条目。拖出安全区选择插入位置；返回安全区暂停。拖到组头会插入组内最后；首尾按钮只作用于当前范围。取消不会写入条目。"
    else -> "短按方块：预览条目。\n长按方块：单条拖动。\n长按右侧三条杠：整组拖动。\n拖出安全区：选择插入位置。\n返回安全区：暂停插入判断。\n拖到组头：插入该组最后；首尾按钮只作用于当前范围。" +
        if (mode == LorebookBatchMode.COPY) "\n复制不会删除来源条目；副本生成独立 ID，确认目标后才保存。" else "\n确认落点前不修改原列表；保存成功后只移除已插入的暂存条目。"
}
