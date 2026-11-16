package me.rerere.rikkahub.ui.pages.extensions

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.data.model.Lorebook
import me.rerere.rikkahub.data.model.PromptInjection
import kotlin.uuid.Uuid

internal sealed interface LorebookBatchAction {
    data class Move(val ids: Set<Uuid>) : LorebookBatchAction
}

internal data class LorebookBatchRequest(
    val draft: Lorebook,
    val expected: Lorebook?,
    val action: LorebookBatchAction,
    val placement: LorebookEntryPlacement? = null,
    val atEnd: Boolean? = null,
)

internal fun orderedLorebookSelection(
    entries: List<PromptInjection.RegexInjection>,
    ids: Set<Uuid>,
): List<PromptInjection.RegexInjection> = entries.filter { it.id in ids }

/** Resolve the drop against the remaining entries. No lazy-list index adjustment is needed. */
internal fun insertLorebookBatch(
    remaining: List<PromptInjection.RegexInjection>,
    selected: List<PromptInjection.RegexInjection>,
    placement: LorebookEntryPlacement?,
    atEnd: Boolean?,
): List<PromptInjection.RegexInjection> {
    require(selected.isNotEmpty()) { "没有可插入的条目，请重新选择" }
    val groups = selected.map { it.group }.distinct()
    require(groups.size == 1) { "一次只能操作同一分组的条目" }
    require((remaining + selected).map { it.id }.distinct().size == remaining.size + selected.size) {
        "条目 ID 冲突，未修改世界书"
    }
    val index = when {
        atEnd != null -> if (atEnd) remaining.size else 0
        placement != null -> {
            val target = remaining.indexOfFirst { it.id == placement.targetId && it.group == groups.single() }
            require(target >= 0) { "插入目标已改变或不属于同一分组，请重新选择落点" }
            target + if (placement.after) 1 else 0
        }
        else -> {
            require(remaining.none { it.group == groups.single() }) { "请先选择插入位置" }
            remaining.size
        }
    }
    return remaining.take(index) + selected + remaining.drop(index)
}

internal fun applyLorebookBatch(books: List<Lorebook>, request: LorebookBatchRequest): List<Lorebook> {
    val current = books.firstOrNull { it.id == request.draft.id }
    require(current == request.expected) { "目标世界书已被修改，请重新打开后选择落点；暂存条目仍保留" }
    val entries = when (val action = request.action) {
        is LorebookBatchAction.Move -> {
            val selected = orderedLorebookSelection(request.draft.entries, action.ids)
            require(selected.size == action.ids.size) { "选中的条目已删除，请取消并重新选择" }
            insertLorebookBatch(
                request.draft.entries.filterNot { it.id in action.ids }, selected, request.placement, request.atEnd,
            )
        }
    }
    val edited = request.draft.copy(entries = entries)
    return if (current == null) books + edited else books.map { if (it.id == edited.id) edited else it }
}

/** Wait for persistence before consuming a task. A failed write restores only this transaction's target. */
internal class LorebookBatchWriter(
    private val update: suspend ((List<Lorebook>) -> List<Lorebook>) -> Unit,
) {
    private val mutex = Mutex()

    suspend fun commit(request: LorebookBatchRequest): Lorebook = mutex.withLock {
        var applied: Lorebook? = null
        try {
            update { latest ->
                val updated = applyLorebookBatch(latest, request)
                applied = updated.first { it.id == request.draft.id }
                updated
            }
            checkNotNull(applied)
        } catch (failure: Exception) {
            // SettingsStore publishes memory before disk I/O. Do not leave an unconfirmed insertion
            // there after an I/O error, and never overwrite a newer edit made by another caller.
            applied?.let { written ->
                withContext(NonCancellable) {
                    runCatching {
                        update { latest ->
                            if (latest.firstOrNull { it.id == written.id } != written) latest
                            else if (request.expected == null) latest.filterNot { it.id == written.id }
                            else latest.map { if (it.id == written.id) request.expected else it }
                        }
                    }.exceptionOrNull()?.let(failure::addSuppressed)
                }
            }
            throw failure
        }
    }
}
