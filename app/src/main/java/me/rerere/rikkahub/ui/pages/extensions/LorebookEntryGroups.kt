package me.rerere.rikkahub.ui.pages.extensions

import me.rerere.rikkahub.data.model.withOrderedEntries
import me.rerere.rikkahub.data.model.Lorebook
import me.rerere.rikkahub.data.model.PromptInjection

/** Old books derive names from entries; explicit names also retain empty groups and their order. */
internal fun lorebookEntryGroupNames(book: Lorebook): List<String> =
    (book.entryGroups + book.entries.map { it.group }).filter { it.isNotBlank() }.distinct()

internal fun Lorebook.withLorebookEntries(entries: List<PromptInjection.RegexInjection>): Lorebook = withOrderedEntries(entries)

internal fun createLorebookEntryGroup(book: Lorebook, name: String): Lorebook {
    val group = name.trim()
    require(group.isNotEmpty()) { "分组名称不能为空" }
    require(group !in lorebookEntryGroupNames(book)) { "分组已存在" }
    return book.copy(entryGroups = lorebookEntryGroupNames(book) + group)
}

internal fun groupLabel(group: String) = group.ifBlank { "未分组" }

/** Reorder metadata only: entry IDs, group membership and all per-entry values stay untouched. */
internal fun moveLorebookGroup(book: Lorebook, source: String, target: String, after: Boolean): Lorebook {
    val names = lorebookEntryGroupNames(book)
    if (source == target || source !in names || target !in names) return book
    val remaining = names.filterNot { it == source }.toMutableList()
    remaining.add(remaining.indexOf(target) + if (after) 1 else 0, source)
    return book.copy(entryGroups = remaining)
}
