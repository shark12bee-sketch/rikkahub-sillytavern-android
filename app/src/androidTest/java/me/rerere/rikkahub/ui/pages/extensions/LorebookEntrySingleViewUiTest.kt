package me.rerere.rikkahub.ui.pages.extensions

import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.data.model.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import kotlin.uuid.Uuid

class LorebookEntrySingleViewUiTest {
    @get:Rule val compose = createComposeRule()
    private val category = LorebookCategory(name = "分类甲")
    private val member = PromptInjection.RegexInjection(name = "互斥成员", content = "exclusive", group = "竞争甲", categoryId = category.id)
    private val plain = PromptInjection.RegexInjection(name = "普通成员", content = "ordinary")
    private var book by mutableStateOf(Lorebook(entries = listOf(member, plain), categories = listOf(category)))
    private var selected by mutableStateOf(emptySet<Uuid>())
    private var selecting = false
    private var added: String? = null
    private fun show() { compose.setContent { MaterialTheme {
        LorebookEntryList(book, null, {}, {}, {}, Modifier.size(400.dp, 700.dp),
            selectedIds = if (selecting) selected else null,
            onSelectEntry = { selected = if (it in selected) selected - it else selected + it },
            header = {}, onEntriesChange = { book = book.withLorebookEntries(it) }, onGroupsChange = { book = it },
            onEditEntry = {}, onAddEntry = { added = it }, onGroupSettings = { _, _ -> })
    } } }
    @Test fun exclusiveMemberHasOnlyOneDisplayLocationEvenWithOldCategoryMetadata() {
        show()
        compose.onNodeWithText("竞争甲").performClick()
        compose.onAllNodesWithText("互斥成员").assertCountEquals(1)
        compose.onNodeWithContentDescription("拖动分类 分类甲").performClick()
        compose.onAllNodesWithText("互斥成员").assertCountEquals(1)
        compose.runOnIdle { assertEquals(listOf(plain), book.orderedOrdinaryEntries()) }
        captureLorebookRepairEvidence("round14-exclusive-single-view")
    }
    @Test fun exclusiveMembersCanBeSelectedInTheirOwnGroup() {
        selecting = true; show()
        compose.onNodeWithText("竞争甲").performClick()
        compose.onNodeWithContentDescription("选择条目 互斥成员").performClick()
        compose.runOnIdle { assertEquals(setOf(member.id), selected); assertEquals(2, book.entries.size) }
    }
    @Test fun categoryExpansionDoesNotExposeExclusiveMembers() {
        show()
        compose.onNodeWithContentDescription("拖动分类 分类甲").performClick()
        compose.onNodeWithText("互斥成员").assertDoesNotExist()
        compose.onNodeWithText("普通成员").assertIsDisplayed()
    }

    @Test fun groupAddKeepsItsExclusiveContext() {
        show()
        compose.onNodeWithContentDescription("在 竞争甲 中新增条目").performClick()
        compose.runOnIdle { assertEquals("竞争甲", added); assertEquals(2, book.entries.size) }
    }
    @Test fun expansionAndSelectionSurviveScrollingThroughLargeBooks() {
        book = book.copy(entries = book.entries + List(180) { PromptInjection.RegexInjection(name = "Long $it", group = "竞争甲") })
        selecting = true; show()
        compose.onNodeWithText("竞争甲").performClick()
        compose.onNodeWithContentDescription("选择条目 互斥成员").performClick()
        val list = compose.onNodeWithTag("lorebook-entry-list")
        list.performScrollToNode(hasContentDescription("选择条目 Long 179"))
        list.performScrollToNode(hasContentDescription("选择条目 互斥成员"))
        compose.onNodeWithContentDescription("选择条目 互斥成员").assertIsOn()
        compose.runOnIdle { assertEquals(setOf(member.id), selected) }
        captureLorebookRepairEvidence("round14-expanded-group-survives-scroll")
    }
}
