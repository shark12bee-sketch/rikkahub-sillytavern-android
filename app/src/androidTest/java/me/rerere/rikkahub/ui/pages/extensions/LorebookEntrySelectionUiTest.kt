package me.rerere.rikkahub.ui.pages.extensions

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import me.rerere.rikkahub.data.model.PromptInjection
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import kotlin.uuid.Uuid

class LorebookEntrySelectionUiTest {
    @get:Rule val compose = createComposeRule()
    private val a = PromptInjection.RegexInjection(name = "Alpha", group = "A")
    private val b = a.copy(id = Uuid.random(), name = "Beta")
    private val c = a.copy(id = Uuid.random(), name = "Other", group = "B")
    private val u = a.copy(id = Uuid.random(), name = "Loose", group = "")
    private val entries = listOf(a, b, c, u)
    private var selected by mutableStateOf(setOf(c.id))

    private fun scope(group: String) {
        compose.onNodeWithTag("lorebook-selection-scope").performClick()
        compose.onNodeWithText("组内：${group.ifBlank { "未分组" }}").performClick()
    }

    @Test fun chosenScopeSurvivesSelectionChangesAndSavedStateRestoration() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            MaterialTheme {
                LorebookSelectionControls(entries, selected, LorebookBatchMode.MOVE,
                    onSelect = { selected = it }, onDone = {}, onCancel = {}, groups = listOf("A", "B", "Empty"))
            }
        }
        scope("B")
        compose.runOnIdle { selected = setOf(a.id) }
        compose.onNodeWithText("全选范围：B").assertIsDisplayed()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("全选范围：B").assertIsDisplayed()
        compose.runOnIdle { assertEquals(setOf(a.id), selected) }
        scope("A")
        compose.runOnIdle { assertEquals(setOf(a.id), selected) }
        compose.onNodeWithText("全选该组").performClick()
        compose.runOnIdle { assertEquals(setOf(a.id, b.id), selected) }
        compose.runOnIdle { selected = emptySet() }
        compose.onNodeWithText("全选范围：A").assertIsDisplayed()
    }

    @Test fun selectionScopeAndMoveDestinationNeverOverwriteEachOtherOrManualSelection() {
        var destination: String? = null
        compose.setContent {
            MaterialTheme {
                LorebookSelectionControls(entries, selected, LorebookBatchMode.MOVE,
                    onSelect = { selected = it }, onDone = {}, onCancel = {}, groups = listOf("A", "B", "Empty"),
                    onMoveToGroup = { destination = it })
            }
        }
        scope("A")
        compose.onNodeWithTag("lorebook-target-group").performClick()
        compose.onNodeWithText("组内：B").performClick()
        compose.onNodeWithText("全选范围：A").assertIsDisplayed()
        compose.onNodeWithText("全选该组").performClick()
        compose.runOnIdle { assertEquals(setOf(a.id, b.id, c.id), selected) }
        compose.onNodeWithText("移入分组").performClick()
        compose.runOnIdle { assertEquals("B", destination) }
        scope("Empty")
        compose.onNodeWithText("全选该组").assertIsNotEnabled()
        compose.onNodeWithText("目标：B").assertIsDisplayed()
        compose.runOnIdle { assertEquals(setOf(a.id, b.id, c.id), selected) }
    }

    @Test fun crossGroupSelectionCanCompleteInMoveCopyAndDeleteWithoutChangingItsScope() {
        var mode by mutableStateOf(LorebookBatchMode.MOVE)
        var completed = 0
        selected = setOf(a.id, c.id)
        compose.setContent {
            MaterialTheme {
                LorebookSelectionControls(entries, selected, mode, onSelect = { selected = it },
                    onDone = { completed++ }, onCancel = {}, groups = listOf("A", "B"))
            }
        }
        scope("B")
        for (next in LorebookBatchMode.entries) {
            compose.runOnIdle { mode = next }
            compose.onNodeWithText("全选范围：B").assertIsDisplayed()
            compose.onNodeWithText(if (next == LorebookBatchMode.DELETE) "删除所选" else "完成选择")
                .assertIsEnabled().performClick()
        }
        compose.runOnIdle { assertEquals(3, completed); assertEquals(setOf(a.id, c.id), selected) }
    }
}
