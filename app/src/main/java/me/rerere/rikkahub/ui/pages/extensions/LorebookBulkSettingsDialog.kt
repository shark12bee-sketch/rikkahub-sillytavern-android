package me.rerere.rikkahub.ui.pages.extensions

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.data.model.PromptInjection
import me.rerere.rikkahub.ui.components.ui.IntTextField

@Composable
internal fun LorebookBulkSettingsDialog(entries: List<PromptInjection.RegexInjection>, onDismiss: () -> Unit,
    onApply: (LorebookSettingsPatch) -> Unit) {
    val first = entries.firstOrNull() ?: return
    var patch by remember { mutableStateOf(LorebookSettingsPatch()) }
    fun mixed(values: List<Any>) = if (values.distinct().size > 1) "（混合，未勾选时保持各自值）" else "（未勾选时保持原值）"
    AlertDialog(onDismissRequest = onDismiss, title = { Text("批量设置 · ${entries.size} 条") }, text = {
        Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("只应用勾选的字段，正文、关键词、分组等独立内容保持不变。")
            BulkField("优先级" + mixed(entries.map { it.priority }), patch.priority != null,
                { patch = patch.copy(priority = if (it) first.priority else null) })
            patch.priority?.let { IntTextField(it, { patch = patch.copy(priority = it) }, Modifier.testTag("bulk-priority"), label = { Text("优先级") }) }
            BulkField("注入位置" + mixed(entries.map { it.position }), patch.position != null,
                { patch = patch.copy(position = if (it) first.position else null) })
            patch.position?.let { InjectionPositionSelector(it) { patch = patch.copy(position = it) } }
            BulkField("注入深度" + mixed(entries.map { it.injectDepth }), patch.depth != null,
                { patch = patch.copy(depth = if (it) first.injectDepth else null) })
            patch.depth?.let { IntTextField(it, { patch = patch.copy(depth = it) }, label = { Text("注入深度") }) }
            BulkField("注入概率" + mixed(entries.map { it.probability }), patch.probability != null,
                { patch = patch.copy(probability = if (it) first.probability else null) })
            patch.probability?.let { value ->
                Text("概率 $value%")
                Slider(value.toFloat(), { patch = patch.copy(probability = it.toInt()) }, valueRange = 0f..100f)
            }
            BulkField("启用状态" + mixed(entries.map { it.enabled }), patch.enabled != null,
                { patch = patch.copy(enabled = if (it) first.enabled else null) })
            patch.enabled?.let { Switch(it, { patch = patch.copy(enabled = it) }) }
            Text("将修改：${patch.fields.joinToString().ifBlank { "尚未选择字段" }}")
        }
    }, confirmButton = { TextButton(onClick = { onApply(patch) }, enabled = patch.fields.isNotEmpty()) { Text("应用设置") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消设置") } })
}

@Composable
private fun BulkField(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(checked, onChecked); Text(label) }
}
