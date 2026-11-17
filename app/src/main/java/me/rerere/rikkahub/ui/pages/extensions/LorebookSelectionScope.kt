package me.rerere.rikkahub.ui.pages.extensions

import me.rerere.rikkahub.data.model.PromptInjection
import kotlin.uuid.Uuid

/** Scope is supplied by the user's range selector, never inferred from checked entries. */
internal fun toggleLorebookScope(
    entries: List<PromptInjection.RegexInjection>,
    selected: Set<Uuid>,
    group: String?,
): Set<Uuid> {
    val scoped = lorebookScopeIds(entries, group)
    return if (selected.containsAll(scoped)) selected - scoped else selected + scoped
}

/** Null is the all-entry scope, separate from every possible user-defined group name. */
internal fun lorebookScopeIds(entries: List<PromptInjection.RegexInjection>, group: String?): Set<Uuid> =
    entries.filter { group == null || it.group.ifBlank { "" } == group.ifBlank { "" } }.map { it.id }.toSet()
