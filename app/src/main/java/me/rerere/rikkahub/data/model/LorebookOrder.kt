package me.rerere.rikkahub.data.model

/** Ordinary editor roots only. Competitive groups and Prompt collection retain their own semantics. */
fun Lorebook.orderedNodes(): List<LorebookOrderNode> {
    val groups = categories.map { it.id }
    val singles = entries.filter { it.group.isBlank() && it.categoryId !in groups }.map { LorebookOrderNode(entryId = it.id) }
    val valid = groups.map { LorebookOrderNode(categoryId = it) } + singles
    return (rootOrder.filter { it in valid } + valid).distinct()
}

/** Editor traversal includes each entity once: exclusive members above ordinary roots. */
fun Lorebook.orderedEntries(): List<PromptInjection.RegexInjection> {
    val exclusive = (entryGroups + entries.map { it.group }).filter { it.isNotBlank() }.distinct()
    return exclusive.flatMap { name -> entries.filter { it.group == name } } + orderedOrdinaryEntries()
}

fun Lorebook.orderedOrdinaryEntries(): List<PromptInjection.RegexInjection> {
    val ordinary = entries.filter { it.group.isBlank() }
    val byGroup = ordinary.groupBy { it.categoryId }
    val byId = ordinary.associateBy { it.id }
    return orderedNodes().flatMap { node ->
        node.categoryId?.let { byGroup[it].orEmpty() } ?: listOfNotNull(byId[node.entryId])
    }.distinctBy { it.id }
}

/** Rebuild order after an entry insertion, keeping explicitly saved empty groups in their slots. */
fun Lorebook.withOrderedEntries(values: List<PromptInjection.RegexInjection>): Lorebook {
    val updated = values.map { if (it.group.isNotBlank() && it.categoryId != null) it.copy(categoryId = null) else it }
    val groups = categories.map { it.id }
    val exclusiveGroups = (entryGroups + entries.map { it.group } + updated.map { it.group }).filter { it.isNotBlank() }.distinct()
    if (entries.map { it.id to it.categoryId } == updated.map { it.id to it.categoryId })
        return copy(entries = updated, entryGroups = exclusiveGroups)
    // Deletion/settings preserve the saved root order even when storage entry order differs.
    val ids = updated.map { it.id }.toSet()
    if (updated.all { e -> entries.any { it.id == e.id && it.categoryId == e.categoryId } } &&
        entries.filter { it.id in ids }.map { it.id } == updated.map { it.id }) {
        val value = copy(entries = updated, entryGroups = exclusiveGroups)
        return value.copy(rootOrder = value.orderedNodes())
    }
    val nodes = updated.filter { it.group.isBlank() }.map { if (it.categoryId !in groups) LorebookOrderNode(entryId = it.id) else LorebookOrderNode(categoryId = it.categoryId) }.distinct().toMutableList()
    orderedNodes().forEachIndexed { index, node ->
        if (node.categoryId in groups && updated.none { it.group.isBlank() && it.categoryId == node.categoryId }) nodes.add(index.coerceAtMost(nodes.size), node)
    }
    return copy(entries = updated, entryGroups = exclusiveGroups, rootOrder = nodes.distinct())
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
