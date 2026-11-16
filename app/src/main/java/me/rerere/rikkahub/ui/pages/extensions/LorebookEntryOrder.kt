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
