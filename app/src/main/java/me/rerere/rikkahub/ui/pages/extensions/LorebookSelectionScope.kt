package me.rerere.rikkahub.ui.pages.extensions

import me.rerere.rikkahub.data.model.PromptInjection
import kotlin.uuid.Uuid

/** Scope is supplied by the user's range selector, never inferred from checked entries. */
internal fun toggleLorebookScope(
    entries: List<PromptInjection.RegexInjection>,
    selected: Set<Uuid>,
    group: String,
): Set<Uuid> {
    val scoped = entries.filter { it.group.ifBlank { "" } == group.ifBlank { "" } }.map { it.id }.toSet()
    return if (selected.containsAll(scoped)) selected - scoped else selected + scoped
}
