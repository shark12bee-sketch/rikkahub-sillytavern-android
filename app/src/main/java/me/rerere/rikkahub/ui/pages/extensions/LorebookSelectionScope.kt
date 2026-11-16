package me.rerere.rikkahub.ui.pages.extensions

import me.rerere.rikkahub.data.model.PromptInjection
import kotlin.uuid.Uuid

/** The first checked entry chooses the scope, as in the original grouped selection UI. */
internal fun lorebookSelectionGroup(entries: List<PromptInjection.RegexInjection>, selected: Set<Uuid>): String? =
    (entries.firstOrNull { it.id in selected } ?: entries.firstOrNull())?.group?.ifBlank { "" }

internal fun toggleLorebookScope(entries: List<PromptInjection.RegexInjection>, selected: Set<Uuid>): Set<Uuid> {
    val group = lorebookSelectionGroup(entries, selected) ?: return selected
    val scoped = entries.filter { it.group.ifBlank { "" } == group }.map { it.id }.toSet()
    // Never discard an existing selection in another group restored from older UI state.
    return if (selected.containsAll(scoped)) selected - scoped else selected + scoped
}
