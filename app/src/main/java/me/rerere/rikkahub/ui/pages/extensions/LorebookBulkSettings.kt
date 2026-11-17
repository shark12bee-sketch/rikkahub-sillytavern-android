package me.rerere.rikkahub.ui.pages.extensions

import me.rerere.rikkahub.data.model.InjectionPosition
import me.rerere.rikkahub.data.model.PromptInjection

/** Null means untouched, including when selected entries have different original values. */
internal data class LorebookSettingsPatch(
    val priority: Int? = null,
    val position: InjectionPosition? = null,
    val depth: Int? = null,
    val probability: Int? = null,
    val enabled: Boolean? = null,
) {
    val fields: List<String> get() = listOfNotNull(
        priority?.let { "优先级" }, position?.let { "注入位置" }, depth?.let { "注入深度" },
        probability?.let { "注入概率" }, enabled?.let { "启用状态" },
    )
    fun apply(entry: PromptInjection.RegexInjection): PromptInjection.RegexInjection {
        require(probability == null || probability in 0..100) { "概率必须为 0–100" }
        return entry.copy(priority = priority ?: entry.priority, position = position ?: entry.position,
            injectDepth = depth ?: entry.injectDepth, probability = probability ?: entry.probability,
            enabled = enabled ?: entry.enabled)
    }
}
