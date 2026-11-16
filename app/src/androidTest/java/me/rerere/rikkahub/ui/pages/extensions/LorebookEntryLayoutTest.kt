package me.rerere.rikkahub.ui.pages.extensions

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import me.rerere.rikkahub.data.model.Lorebook
import me.rerere.rikkahub.data.model.PromptInjection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real Compose measurement and pointer integration, in addition to the pure decision unit tests. */
@RunWith(AndroidJUnit4::class)
class LorebookEntryLayoutTest {
    @get:Rule
    val compose = createComposeRule()

    private val pending = PromptInjection.RegexInjection(name = "Pending", content = "Pending content")
    private var draft by mutableStateOf(Lorebook(entries = List(30) {
        PromptInjection.RegexInjection(name = "Existing $it", content = "Content $it")
    }))
    private var staged by mutableStateOf<PromptInjection.RegexInjection?>(pending)

    private fun showEditor() {
        compose.setContent {
            MaterialTheme {
                LorebookEntryList(
                    book = draft,
                    pendingEntry = staged,
                    onCancelPending = { staged = null },
                    onInsertPending = { target ->
                        draft = draft.copy(entries = insertLorebookEntry(draft.entries, pending, target))
                        staged = null
                    },
                    onInsertPendingAtEdge = { end ->
                        draft = draft.copy(entries = insertLorebookEntryAtEdge(draft.entries, pending, end))
                        staged = null
                    },
                    onUpdatePending = { staged = it },
                    modifier = Modifier.size(width = 360.dp, height = 560.dp).testTag("editor"),
                    header = { Text("Book settings", Modifier.height(200.dp)) },
                    onEntriesChange = { draft = draft.copy(entries = it) },
                    onEditEntry = {},
                    onAddEntry = {},
                    onGroupSettings = { _, _ -> },
                )
            }
        }
    }

    @Test
    fun entryCardWrapsContentInsideBoundedParent() {
        compose.setContent {
            MaterialTheme {
                Column(Modifier.height(560.dp).fillMaxWidth().testTag("bounded-parent")) {
                    Box(Modifier.testTag("card")) {
                        RegexInjectionEntryCard(pending, onEdit = {}, onDelete = {})
                    }
                    Text("Below card")
                }
            }
        }
        val card = compose.onNodeWithTag("card").fetchSemanticsNode().boundsInRoot
        val parent = compose.onNodeWithTag("bounded-parent").fetchSemanticsNode().boundsInRoot
        assertTrue("Decoration must not consume the parent's remaining height", card.height < parent.height / 2f)
        compose.onNodeWithText("Below card").assertIsDisplayed()
    }

    @Test
    fun safetyWrapsContentLeavesListVisibleAndStaysFixedWhileListScrolls() {
        showEditor()
        val editor = compose.onNodeWithTag("editor").fetchSemanticsNode().boundsInRoot
        val safety = compose.onNodeWithTag("lorebook-insertion-safety-area").fetchSemanticsNode().boundsInRoot
        val list = compose.onNodeWithTag("lorebook-entry-list").fetchSemanticsNode().boundsInRoot
        assertTrue(safety.height <= editor.height * 0.56f)
        assertTrue(list.height > editor.height * 0.40f)
        compose.onNodeWithText("Existing 0").assertIsDisplayed()
        compose.onNodeWithText("Book settings").assertDoesNotExist()
        compose.onNodeWithText("插入最前").assertIsDisplayed()
        compose.onNodeWithText("插入最后").assertIsDisplayed()
        compose.onNodeWithTag("lorebook-entry-list").performScrollToNode(hasText("Existing 29"))
        compose.onNodeWithText("Existing 29").assertIsDisplayed()
        assertEquals(safety, compose.onNodeWithTag("lorebook-insertion-safety-area").fetchSemanticsNode().boundsInRoot)
    }

    @Test
    fun shortcutsAndCancelWorkWithoutDragging() {
        val original = draft.entries
        showEditor()
        compose.onNodeWithText("取消待插入").performClick()
        compose.onNodeWithText("Book settings").assertIsDisplayed()
        compose.runOnIdle { assertEquals(original, draft.entries); staged = pending }
        compose.onNodeWithText("插入最前").performClick()
        compose.runOnIdle {
            assertEquals(pending, draft.entries.first())
            assertEquals(original, draft.entries.drop(1))
            assertEquals(null, staged)
            draft = draft.copy(entries = original)
            staged = pending
        }
        compose.onNodeWithText("插入最后").performClick()
        compose.runOnIdle {
            assertEquals(pending, draft.entries.last())
            assertEquals(original, draft.entries.dropLast(1))
            assertEquals(null, staged)
        }
        compose.onNodeWithTag("lorebook-insertion-safety-area").assertDoesNotExist()
    }

    @Test
    fun realPointerPausesInSafetyResumesInListAndReleaseInSafetyDoesNotInsert() {
        showEditor()
        val handle = compose.onNodeWithContentDescription("拖动插入 Pending")
        val handleBounds = handle.fetchSemanticsNode().boundsInRoot
        val safety = compose.onNodeWithTag("lorebook-insertion-safety-area").fetchSemanticsNode().boundsInRoot
        val listNode = compose.onNodeWithTag("lorebook-entry-list")
        val list = listNode.fetchSemanticsNode().boundsInRoot
        fun scrollPosition() = listNode.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        val initialScroll = scrollPosition()
        compose.mainClock.autoAdvance = false
        // Cross touch slop horizontally, entirely inside the safety boundary.
        handle.performTouchInput { down(center); moveBy(Offset(-50f, 0f)) }
        compose.mainClock.advanceTimeBy(200)
        compose.onNodeWithText("安全区内 · 插入已暂停；向下拖出安全区继续").assertIsDisplayed()
        assertEquals(initialScroll, scrollPosition(), 0f)

        val inList = Offset(handleBounds.width / 2f, list.top + list.height * 0.5f - handleBounds.top)
        handle.performTouchInput { moveTo(inList) }
        compose.mainClock.advanceTimeBy(64)
        compose.onNodeWithText("正在选择插入位置 · 松手插入指示线处").assertIsDisplayed()

        val inSafety = Offset(handleBounds.width / 2f, safety.center.y - handleBounds.top)
        handle.performTouchInput { moveTo(inSafety) }
        compose.mainClock.advanceTimeBy(200)
        compose.onNodeWithText("安全区内 · 插入已暂停；向下拖出安全区继续").assertIsDisplayed()
        val pausedScroll = scrollPosition()
        compose.mainClock.advanceTimeBy(300)
        assertEquals(pausedScroll, scrollPosition(), 0f)

        handle.performTouchInput { moveTo(inList) }
        compose.mainClock.advanceTimeBy(64)
        compose.onNodeWithText("正在选择插入位置 · 松手插入指示线处").assertIsDisplayed()
        // No frame/recomposition between returning and UP: final coordinates must still win.
        handle.performTouchInput { moveTo(inSafety); up() }
        compose.mainClock.autoAdvance = true
        compose.runOnIdle { assertEquals(pending, staged); assertEquals(30, draft.entries.size) }
    }

    @Test
    fun edgeScrollRunsOnlyInListAndStopsAfterReturningToSafety() {
        showEditor()
        val handle = compose.onNodeWithContentDescription("拖动插入 Pending")
        val bounds = handle.fetchSemanticsNode().boundsInRoot
        val safety = compose.onNodeWithTag("lorebook-insertion-safety-area").fetchSemanticsNode().boundsInRoot
        val listNode = compose.onNodeWithTag("lorebook-entry-list")
        val list = listNode.fetchSemanticsNode().boundsInRoot
        fun scrollPosition() = listNode.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        compose.mainClock.autoAdvance = false
        handle.performTouchInput { down(center); moveBy(Offset(-50f, 0f)) }
        compose.mainClock.advanceTimeBy(100)
        val before = scrollPosition()
        handle.performTouchInput { moveTo(Offset(bounds.width / 2f, list.bottom - 2f - bounds.top)) }
        compose.mainClock.advanceTimeBy(500)
        assertTrue(scrollPosition() > before)
        handle.performTouchInput { moveTo(Offset(bounds.width / 2f, safety.center.y - bounds.top)) }
        compose.mainClock.advanceTimeBy(32)
        val paused = scrollPosition()
        compose.mainClock.advanceTimeBy(500)
        assertEquals(paused, scrollPosition(), 0f)
        handle.performTouchInput { up() }
        compose.mainClock.autoAdvance = true
        compose.runOnIdle { assertEquals(30, draft.entries.size); assertEquals(pending, staged) }
    }
}
