package ai.sovra.remote.protocol

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private fun FakeDesktop.openMenu() {
    element("s1", "/files. Choose local files to read and discuss in this chat. ", "Button", 555.0)
    element("s2", "/web-search. Use when current information or sourced web research is needed. <question>", "Button", 593.0)
    element("s3", "/table. Use when comparing structured records in a native table. <what to present>", "Button", 1923.0, visible = false)
}

class ScreensTest {
    @Test
    fun readsEverySkillAndRanksLikeTheDesktop() {
        val desktop = FakeDesktop().apply { openMenu() }
        val skills = readSkillMenu(desktop.snapshot())
        assertEquals(listOf("files", "web-search", "table"), skills.map { it.name })
        assertEquals("Web search", skills[1].title)
        assertEquals("<question>", skills[1].hint)
        assertTrue(skills[2].needsText())
        assertEquals("What to present…", skills[2].hintPrompt())
        assertEquals("Your question…", skills[1].hintPrompt())
        assertEquals(listOf("table"), matchSkills(skills, "ta").map { it.name })
        assertEquals("", slashQuery("/"))
        assertEquals(null, slashQuery("/web what"))
        assertEquals("Table needs a little more: tell it what to present.", explainNotice("Usage: /table <what to present>"))
    }

    @Test
    fun loadsSkillsFromTheMenuAndLeavesTheChatBoxEmpty() = runTest {
        val desktop = FakeDesktop()
        desktop.element("composer", "Message Sovra", "MultilineTextInput", 700.0)
        desktop.onDeliver = { if (it.action == "type" && it.text == "/") desktop.openMenu() }
        val result = loadSkills(desktop.client())
        assertEquals(listOf("files", "web-search", "table"), (result as Outcome.Ok).value.map { it.name })
        assertEquals(
            listOf(
                Input("click", "composer"), Input("key", key = "secondary-a"), Input("key", key = "backspace"),
                Input("type", text = "/"), Input("key", key = "secondary-a"), Input("key", key = "backspace"),
            ),
            desktop.delivered,
        )
    }

    @Test
    fun sendsThroughTheRealChatBoxAndRunsABareSkill() = runTest {
        val desktop = FakeDesktop()
        desktop.element("search", "Search chats", "TextInput", 500.0)
        desktop.element("composer", "Message Sovra", "MultilineTextInput", 700.0)
        val (result, _) = sendChatMessage(desktop.client(), "/new")
        assertTrue(result is Outcome.Ok)
        assertEquals(Input("click", "composer"), desktop.delivered.first())
        assertEquals(listOf(Input("type", text = "/new "), Input("key", key = "enter")), desktop.delivered.takeLast(2))
    }

    @Test
    fun opensChatFirstWhenAnotherPageIsShowing() = runTest {
        val desktop = FakeDesktop()
        desktop.element("nav-chat", "Chat", "Button", 120.0)
        desktop.onDeliver = { if (it.target == "nav-chat") desktop.element("composer", "Message Sovra", "MultilineTextInput", 700.0) }
        val (result, _) = sendChatMessage(desktop.client(), "hi")
        assertTrue(result is Outcome.Ok)
        assertEquals(Input("click", "nav-chat"), desktop.delivered.first())
    }

    @Test
    fun findsAChatThatIsNotListedBySearching() = runTest {
        val desktop = FakeDesktop()
        desktop.element("search", "Search chats", "TextInput", 498.0)
        desktop.element("c1", "Open conversation Plan my week", "Button", 593.0)
        desktop.onDeliver = { if (it.action == "type" && it.text == "Holiday ideas") desktop.element("c9", "Open conversation Holiday ideas", "Button", 640.0) }
        assertTrue(openConversation(desktop.client(), "Holiday ideas") is Outcome.Ok)
        assertTrue(Input("click", "c9") in desktop.delivered)
        assertEquals(Input("key", key = "backspace"), desktop.delivered.last())
    }

    @Test
    fun readsTheConversationAndStopsOnlyRealStopButtons() {
        val desktop = FakeDesktop()
        val log = desktop.element("log", "Conversation", "Log", 80.0)
        desktop.replace(log, log.copy(bounds = Bounds(0.0, 80.0, 1000.0, 600.0)))
        desktop.element("u1", "What is on my calendar?", "Comment", 120.0)
        desktop.element("a1", "Hi! How can I help?", "Article", 300.0)
        desktop.element("phone", "Stop phone control", "Button", 10.0)
        val chat = transcript(desktop.snapshot())
        assertEquals(listOf(true, false), chat.messages.map { it.fromYou })
        assertEquals(null, findStop(desktop.snapshot()))
    }

    @Test
    fun parsesRepliesIntoBlocks() {
        val blocks = parseBlocks("## Plants\nHere are **three**.\n\n- Snake\n- Pothos\n```\ncode\n```\n| A | B |\n| --- | --- |\n| 1 | 2 |")
        assertEquals(
            listOf(
                Block.Heading(2, "Plants"), Block.Paragraph("Here are **three**."), Block.Bullets(false, listOf("Snake", "Pothos")),
                Block.Code("code"), Block.Table(listOf("A", "B"), listOf(listOf("1", "2"))),
            ),
            blocks,
        )
        assertEquals(listOf(Span("a "), Span("b", bold = true)), parseInline("a **b**"))
    }
}
