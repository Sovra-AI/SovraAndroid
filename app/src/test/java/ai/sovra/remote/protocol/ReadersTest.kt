package ai.sovra.remote.protocol

import ai.sovra.remote.ui.mergeTitles
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun element(target: String, label: String?, role: String?, x: Double, y: Double, width: Double = 100.0, height: Double = 20.0, visible: Boolean = true) =
    UiElement(target, label, role, visible, false, null, null, null, Bounds(x, y, width, height))

private fun snapshot(vararg elements: UiElement) = InspectResult(elements.toList(), elements.size, false, "dark", 100, "r1")

// Shapes and labels taken from live Sovra desktop inspections.
private val sidebar = arrayOf(
    element("new", "Start a fresh conversation", "Button", 8.0, 124.0, 239.0, 36.0),
    element("nav-chat", "Chat", "Button", 8.0, 168.0, 239.0, 28.0),
    element("nav-skills", "Skills", "Button", 8.0, 198.0, 239.0, 28.0),
    element("filter", "Chats", "Button", 10.0, 577.0, 116.0, 28.0),
    element("c1", "Open conversation Second message from the phone", "Button", 8.0, 639.0, 239.0, 49.0),
    element("c2", "Open conversation Plan the launch", "Button", 8.0, 690.0, 239.0, 49.0),
)

/** Ports of the Expo app's `desktop.test.ts`, `elements.test.ts` and `navigate.test.ts`. */
class ReadersTest {
    @Test
    fun readsTheConversationInOrderAndKeepsPartlyScrolledReplies() {
        val view = snapshot(
            *sidebar,
            element("log", "Conversation", "Log", 280.0, 80.0, 976.0, 598.0),
            element("a1", "Hi! How can I help?", "Article", 400.0, 300.0, 600.0, 40.0),
            element("u1", "What is on my calendar?", "Comment", 700.0, 120.0, 300.0, 30.0),
            element("writing", "Sovra is writing", "Status", 400.0, 400.0),
        )
        assertEquals(Transcript(listOf(Message("u1", true, "What is on my calendar?"), Message("a1", false, "Hi! How can I help?")), false, "Sovra is writing"), transcript(view))
        val long = snapshot(
            element("log", "Conversation", "Log", 280.0, 34.0, 976.0, 644.0),
            element("a1", "A long comparison…", "Article", 444.0, -160.0, 692.0, 423.0),
            element("off", "Elsewhere", "Article", 1300.0, 900.0),
        )
        assertEquals(listOf("a1"), transcript(long).messages.map { it.id })
        assertTrue(transcript(long).earlier)
    }

    @Test
    fun rebuildsTheOpenPageAsNativeItems() {
        val team = element("team", "Team", "Button", 8.0, 228.0, 239.0, 28.0).copy(selected = true)
        val view = snapshot(
            *sidebar, team,
            element("bar", "Phone control is on", "Label", 300.0, 8.0),
            element("h", "Team", "Heading", 300.0, 60.0),
            element("p", "People who can use Sovra.", "Label", 300.0, 90.0),
            element("invite", "Invite someone", "Button", 300.0, 120.0, 150.0, 30.0),
            element("invite-text", "Invite someone", "Label", 310.0, 125.0, 120.0, 20.0),
            element("auto", "Approve automatically", "Switch", 300.0, 170.0).copy(checked = true),
            element("name", "Team name", "TextInput", 300.0, 210.0),
            element("list", null, "List", 300.0, 250.0, 600.0, 400.0),
        )
        val page = openPage(view)
        assertEquals("Team", page.title)
        assertEquals(
            listOf(
                PageItem.Text("p", "People who can use Sovra."),
                PageItem.Action(view.elements.first { it.target == "invite" }),
                PageItem.Toggle(view.elements.first { it.target == "auto" }, true),
                PageItem.Field(view.elements.first { it.target == "name" }),
            ),
            page.items,
        )
        assertEquals("list", page.scroll?.target)
    }

    @Test
    fun readsAGridOfCardsCardByCard() {
        // Shapes from a live Team page: unlabelled cards side by side, each with a switch, then Ask and Edit.
        val view = snapshot(
            *sidebar,
            element("grid", null, null, 280.0, 210.0, 976.0, 430.0),
            element("card-a", null, null, 280.0, 210.0, 232.0, 214.0),
            element("card-m", null, null, 528.0, 218.0, 232.0, 200.0),
            element("on-m", "On the team", "Switch", 545.0, 336.0, 198.0, 20.0).copy(checked = true),
            element("on-a", "On the team", "Switch", 297.0, 344.0, 198.0, 20.0).copy(checked = false),
            element("ask-m", "Ask Morgan", "Button", 545.0, 364.0, 111.0, 36.0),
            element("edit-m", "Edit", "Button", 660.0, 364.0, 57.0, 36.0),
            element("ask-a", "Ask Alex", "Button", 297.0, 372.0, 90.0, 36.0),
            element("edit-a", "Edit", "Button", 391.0, 372.0, 57.0, 36.0),
            element("add", "Add a helper", "Button", 1140.0, 665.0, 116.0, 36.0),
        )
        assertEquals(
            listOf("ask-a", "edit-a", "on-a", "break-ask-m", "ask-m", "edit-m", "on-m", "break-add", "add"),
            openPage(view).items.map { it.key },
        )
    }

    @Test
    fun keepsACardsTextWithItsButtonAndDropsRepeats() {
        // Shapes from live Connections and Schedules pages.
        val view = snapshot(
            *sidebar,
            element("team", "Connections", "Button", 8.0, 228.0, 239.0, 28.0).copy(selected = true),
            element("p", "Connections", "Label", 300.0, 60.0),
            element("make", "Create schedule", "Button", 300.0, 90.0, 150.0, 30.0),
            element("card", null, null, 280.0, 130.0, 400.0, 120.0),
            element("link", "Link an online AI", "Button", 300.0, 210.0, 150.0, 30.0),
            element("name", "An online AI", "Label", 300.0, 140.0),
            element("state", "Linked", "Label", 300.0, 170.0),
            element("make-2", "Create schedule", "Button", 300.0, 300.0, 150.0, 30.0),
        )
        val page = openPage(view)
        assertEquals("Connections", page.title)
        assertEquals(listOf("break-name", "name", "state", "link"), page.items.drop(1).map { it.key })
        assertEquals("make", page.items.first().key)
    }

    @Test
    fun offersAButtonRepeatedInOneBoxOnce() {
        // A live Schedules page: the header and the empty state both offer Create schedule.
        val view = snapshot(
            *sidebar,
            element("box", null, null, 280.0, 58.0, 976.0, 408.0),
            element("make", "Create schedule", "Button", 280.0, 136.0, 140.0, 36.0),
            element("back", "Back to chat", "Button", 428.0, 136.0, 113.0, 36.0),
            element("make-2", "Create schedule", "Button", 699.0, 404.0, 138.0, 36.0),
        )
        assertEquals(listOf("make", "back"), openPage(view).items.map { it.key })
    }

    @Test
    fun listsChatsPagesAndDestinations() {
        assertEquals(listOf("Second message from the phone", "Plan the launch"), chatList(snapshot(*sidebar)).map { it.title })
        val view = snapshot(
            *sidebar,
            element("team", "Team", "Button", 8.0, 228.0, 239.0, 28.0),
            element("settings", "Settings", "Button", 8.0, 496.0, 239.0, 28.0),
            element("tour", "Take a guided tour", "Button", 8.0, 702.0, 239.0, 36.0),
        )
        assertEquals(listOf("Chat", "Skills", "Team", "Settings"), pages(view).map { it.label })
        assertEquals(emptyList<UiElement>(), pages(snapshot(element("x", "Settings", "Button", 0.0, 0.0))))
        assertEquals("nav-chat", findNavigation(view, "Chat")?.target)
        assertNull(findNavigation(snapshot(*sidebar), "Team"))
    }

    @Test
    fun listsTheOpenPagesControlsFirst() {
        val browse = element("browse", "Browse all models", "Button", 900.0, 70.0)
        val phone = element("phone", "Phone control is on. Open phone controls", "Button", 976.0, 0.0)
        val view = snapshot(*sidebar, browse, phone)
        assertEquals(listOf("browse", "phone", "new"), pageFirst(view, listOf(phone, sidebar[0], sidebar[1], sidebar[2], browse)).map { it.target })
    }

    @Test
    fun readsOnlyTextInsideTheConversation() {
        val chat = snapshot(
            element("log", "Conversation", "Log", 280.0, 80.0, 976.0, 598.0),
            element("today", "Today", "Heading", 8.0, 611.0, 239.0, 26.0),
            element("reply", "First, connect an AI", null, 445.0, 338.0, 646.0, 22.0),
        )
        assertEquals("log", conversationArea(chat)?.target)
        assertEquals(listOf("reply"), readableText(chat, conversationArea(chat)).map { it.target })
    }

    @Test
    fun keepsEveryChatSeenInTheSidebarsOrder() {
        assertEquals(listOf("a", "b", "c", "d"), mergeTitles(listOf("a", "b"), listOf("b", "c", "d")))
        assertEquals(listOf("a", "x", "b"), mergeTitles(listOf("a", "b"), listOf("a", "x", "b")))
    }

    @Test
    fun scrollsDownToAChatJustOutsideTheList() = runTest {
        val desktop = FakeDesktop()
        val first = desktop.element("c1", "Open conversation Plan my week", "Button", 593.0)
        val hidden = desktop.element("c2", "Open conversation Older chat", "Button", 753.0, visible = false)
        desktop.onDeliver = {
            if (it.action == "scroll") {
                desktop.replace(first, first.copy(visible = false))
                desktop.replace(hidden, hidden.copy(visible = true, bounds = hidden.bounds.copy(y = 653.0)))
            }
        }
        assertTrue(openConversation(desktop.client(), "Older chat") is Outcome.Ok)
        assertEquals(listOf(Input("scroll", "c1", lines = -2), Input("click", "c2")), desktop.delivered)
    }

    @Test
    fun saysSoWhenAChatCannotBeFound() = runTest {
        val desktop = FakeDesktop()
        desktop.element("search", "Search chats", "TextInput", 498.0)
        assertTrue(openConversation(desktop.client(), "Gone") is Outcome.Failed)
        assertTrue(desktop.delivered.none { it.action == "click" && it.target != "search" })
    }

    @Test
    fun searchesAndScrollsTheChatList() = runTest {
        val desktop = FakeDesktop()
        desktop.element("search", "Search chats", "TextInput", 498.0)
        desktop.element("c1", "Open conversation Plan my week", "Button", 593.0)
        val client = desktop.client()
        assertTrue(searchChats(client, "plan") is Outcome.Ok)
        assertTrue(moreChats(client) is Outcome.Ok)
        assertEquals(listOf(Input("type", text = "plan"), Input("scroll", "c1", lines = -6)), desktop.delivered.takeLast(2))
    }

    @Test
    fun bringsUpChatOnlyWhenAnotherPageIsShowing() = runTest {
        val desktop = FakeDesktop()
        desktop.element("nav-chat", "Chat", "Button", 120.0)
        desktop.onDeliver = { if (it.target == "nav-chat") desktop.element("composer", "Message Sovra", "MultilineTextInput", 700.0) }
        val client = desktop.client()
        assertTrue(openChatPage(client) is Outcome.Ok)
        assertTrue(openChatPage(client) is Outcome.Ok)
        assertEquals(listOf(Input("click", "nav-chat")), desktop.delivered)
    }

    @Test
    fun keepsTheUsefulPartOfASpecialistsDescription() {
        val view = snapshot(element("s", "/coding. Consult this specialist when you need help with: Builds websites and small apps. <task>", "Button", 0.0, 0.0))
        assertEquals("Builds websites and small apps.", readSkillMenu(view)[0].description)
    }
}
