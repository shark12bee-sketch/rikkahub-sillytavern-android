package me.rerere.rikkahub.service

import kotlinx.serialization.json.JsonPrimitive
import me.rerere.ai.core.ReasoningLevel
import me.rerere.ai.provider.BuiltInTools
import me.rerere.ai.provider.CustomBody
import me.rerere.ai.provider.CustomHeader
import me.rerere.ai.provider.Model
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.ai.tools.shouldUseExternalWebSearch
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.toMessageNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.uuid.Uuid

class ChatServiceTest {
    @Test
    fun `regenerating U2 removes its old tail and appends the new reply after U2`() {
        val u1 = UIMessage.user("U1")
        val a1 = UIMessage.assistant("A1")
        val u2 = UIMessage.user("U2")
        val a2 = UIMessage.assistant("A2")
        val u3 = UIMessage.user("U3")
        val a3 = UIMessage.assistant("A3")
        val original = Conversation(
            assistantId = Uuid.random(),
            messageNodes = listOf(u1, a1, u2, a2, u3, a3).map { it.toMessageNode() },
        )

        val truncated = truncateConversationAtMessageForRegeneration(original, u2)
        val newReply = UIMessage.assistant("A2 regenerated")
        val regenerated = truncated.copy(
            messageNodes = truncated.messageNodes + newReply.toMessageNode(),
        )

        assertEquals(
            listOf("U1", "A1", "U2", "A2 regenerated"),
            regenerated.currentMessages.map { it.toText() },
        )
        assertEquals(newReply.id, regenerated.currentMessages.last().id)
        assertFalse(regenerated.currentMessages.any { it.id in setOf(a2.id, u3.id, a3.id) })
    }

    @Test
    fun `user regeneration explicitly allows the intentional history shrink`() {
        val source = findSource("src/main/java/me/rerere/rikkahub/service/ChatService.kt")
        val userRegenerationBranch = source
            .substringAfter("if (message.role == MessageRole.USER)")
            .substringBefore("} else {")

        assertTrue(
            "regenerateAtMessage 的 USER 分支必须用 allowShrink=true 保存截断后的历史",
            userRegenerationBranch.contains(
                "saveConversation(conversationId, newConversation, allowShrink = true)",
            ),
        )
    }

    @Test
    fun `fork conversation inherits folder and workspace context`() {
        val source = Conversation(
            assistantId = Uuid.random(),
            title = "Source conversation",
            messageNodes = emptyList(),
            workspaceCwd = "/workspace/project",
            folderId = Uuid.random(),
        )

        val fork = createForkConversation(source, emptyList())

        assertNotEquals(source.id, fork.id)
        assertEquals(source.assistantId, fork.assistantId)
        assertEquals(source.workspaceCwd, fork.workspaceCwd)
        assertEquals(source.folderId, fork.folderId)
        // 上游 2.5.6：fork 会从源标题派生带序号的标题
        assertEquals("Source conversation(1)", fork.title)
        assertFalse(fork.isPinned)
    }

    @Test
    fun `fork title increments existing numeric suffix instead of stacking`() {
        assertEquals("Chat(2)", forkConversationTitle("Chat(1)", emptySet()))
        assertEquals("Chat(4)", forkConversationTitle("Chat(1)", setOf("Chat(2)", "Chat(3)")))
        assertEquals("Chat(1)", forkConversationTitle("Chat", emptySet()))
        assertEquals("Chat(2)", forkConversationTitle("Chat", setOf("Chat(1)")))
        assertEquals("Chat(abc)(1)", forkConversationTitle("Chat(abc)", emptySet()))
    }

    @Test
    fun `background generation params include model custom request configuration`() {
        val headers = listOf(CustomHeader(name = "X-Gateway-Token", value = "test-token"))
        val bodies = listOf(CustomBody(key = "gateway_mode", value = JsonPrimitive("strict")))
        val model = Model(
            modelId = "custom-chat-model",
            customHeaders = headers,
            customBodies = bodies,
        )

        val conversationId = Uuid.random()
        val params = backgroundTextGenerationParams(model, conversationId)

        assertEquals(model, params.model)
        assertEquals(ReasoningLevel.AUTO, params.reasoningLevel)
        assertEquals(headers, params.customHeaders)
        assertEquals(bodies, params.customBody)
        assertEquals(conversationId.toString(), params.sessionId)
    }

    @Test
    fun `external web search is disabled when assistant preference is disabled`() {
        val assistant = Assistant(enableWebSearch = false)
        val model = Model()

        assertFalse(shouldUseExternalWebSearch(assistant, model))
    }

    @Test
    fun `external web search is enabled when assistant preference is enabled`() {
        val assistant = Assistant(enableWebSearch = true)
        val model = Model()

        assertTrue(shouldUseExternalWebSearch(assistant, model))
    }

    @Test
    fun `built-in search suppresses enabled external web search`() {
        val assistant = Assistant(enableWebSearch = true)
        val model = Model(tools = setOf(BuiltInTools.Search))

        assertFalse(shouldUseExternalWebSearch(assistant, model))
    }

    @Test
    fun `built-in search remains exclusive when external web search is disabled`() {
        val assistant = Assistant(enableWebSearch = false)
        val model = Model(tools = setOf(BuiltInTools.Search))

        assertFalse(shouldUseExternalWebSearch(assistant, model))
    }

    @Test
    fun `unrelated built-in tools do not suppress external web search`() {
        val assistant = Assistant(enableWebSearch = true)
        val model = Model(tools = setOf(BuiltInTools.UrlContext))

        assertTrue(shouldUseExternalWebSearch(assistant, model))
    }

    private fun findSource(rel: String): String {
        val cwd = File(System.getProperty("user.dir") ?: ".")
        for (root in listOf(cwd, cwd.parentFile, cwd.parentFile?.parentFile).filterNotNull()) {
            for (candidate in listOf(File(root, rel), File(root, "app/$rel"))) {
                if (candidate.exists()) return candidate.readText()
            }
        }
        return ""
    }
}
