package me.rerere.rikkahub.ui.pages.extensions

import me.rerere.rikkahub.data.model.PromptInjection
import kotlin.uuid.Uuid

/** Move only this group's slots; entries in other groups keep their absolute positions. */
internal fun moveLorebookEntry(
    entries: List<PromptInjection.RegexInjection>,
    fromId: Uuid,
    toId: Uuid,
): List<PromptInjection.RegexInjection> {
    val from = entries.firstOrNull { it.id == fromId } ?: return entries
    val to = entries.firstOrNull { it.id == toId } ?: return entries
    if (from.group != to.group || fromId == toId) return entries
    val group = entries.filter { it.group == from.group }.toMutableList()
    val target = group.indexOfFirst { it.id == toId }
    group.remove(from)
    group.add(target, from)
    val iterator = group.iterator()
    return entries.map { if (it.group == from.group) iterator.next() else it }
}

internal data class LorebookEntryPlacement(val targetId: Uuid, val after: Boolean)

/** Resolve by ID against the latest draft, never by a potentially stale lazy-list index. */
internal fun insertLorebookEntry(
    entries: List<PromptInjection.RegexInjection>,
    pending: PromptInjection.RegexInjection,
    placement: LorebookEntryPlacement?,
): List<PromptInjection.RegexInjection> {
    if (entries.any { it.id == pending.id }) return entries
    if (placement == null) {
        // Only a group with no existing entries has an unambiguous first position.
        return if (entries.none { it.group == pending.group }) entries + pending else entries
    }
    val index = entries.indexOfFirst { it.id == placement.targetId && it.group == pending.group }
    if (index < 0) return entries
    return entries.toMutableList().apply { add(index + if (placement.after) 1 else 0, pending) }
}
