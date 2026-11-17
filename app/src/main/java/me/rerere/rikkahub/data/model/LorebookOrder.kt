package me.rerere.rikkahub.data.model

/** One root traversal shared by the editor and prompt collection. Collapse state is UI-only. */
fun Lorebook.orderedNodes(): List<LorebookOrderNode> {
    val groups = categories.map { it.id }
    val singles = entries.filter { it.categoryId !in groups }.map { LorebookOrderNode(entryId = it.id) }
    val valid = groups.map { LorebookOrderNode(categoryId = it) } + singles
    return (rootOrder.filter { it in valid } + valid).distinct()
}

fun Lorebook.orderedEntries(): List<PromptInjection.RegexInjection> {
    val byGroup = entries.groupBy { it.categoryId }
    val byId = entries.associateBy { it.id }
    return orderedNodes().flatMap { node ->
        node.categoryId?.let { byGroup[it].orEmpty() } ?: listOfNotNull(byId[node.entryId])
    }.distinctBy { it.id }
}

/** Rebuild order after an entry insertion, keeping explicitly saved empty groups in their slots. */
fun Lorebook.withOrderedEntries(updated: List<PromptInjection.RegexInjection>): Lorebook {
    val groups = categories.map { it.id }
    if (entries.map { it.id to it.categoryId } == updated.map { it.id to it.categoryId })
        return copy(entries = updated, entryGroups = (entryGroups + updated.map { it.group }).filter { it.isNotBlank() }.distinct())
    // Deletion/settings preserve the saved root order even when storage entry order differs.
    val ids = updated.map { it.id }.toSet()
    if (updated.all { e -> entries.any { it.id == e.id && it.categoryId == e.categoryId } } &&
        entries.filter { it.id in ids }.map { it.id } == updated.map { it.id }) {
        val value = copy(entries = updated)
        return value.copy(rootOrder = value.orderedNodes())
    }
    val nodes = updated.map { if (it.categoryId !in groups) LorebookOrderNode(entryId = it.id) else LorebookOrderNode(categoryId = it.categoryId) }.distinct().toMutableList()
    orderedNodes().forEachIndexed { index, node ->
        if (node.categoryId in groups && updated.none { it.categoryId == node.categoryId }) nodes.add(index.coerceAtMost(nodes.size), node)
    }
    return copy(entries = updated, entryGroups = (entryGroups + updated.map { it.group }).filter { it.isNotBlank() }.distinct(), rootOrder = nodes.distinct())
}


/** Native import creates independent identities in both ID spaces, including root references. */
fun Lorebook.withFreshIds(): Lorebook {
    val ids = entries.associate { it.id to kotlin.uuid.Uuid.random() }
    val categoryIds = categories.associate { it.id to kotlin.uuid.Uuid.random() }
    return copy(id = kotlin.uuid.Uuid.random(),
        entries = entries.map { it.copy(id = ids.getValue(it.id), categoryId = categoryIds[it.categoryId]) },
        categories = categories.map { it.copy(id = categoryIds.getValue(it.id)) },
        rootOrder = rootOrder.map { it.copy(entryId = ids[it.entryId], categoryId = categoryIds[it.categoryId]) })
}
