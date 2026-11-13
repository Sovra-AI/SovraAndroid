package ai.sovra.remote.protocol

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun fixture(name: String) = checkNotNull(ParityTest::class.java.getResource("/fixtures/$name")).readText().trim()

private fun at(target: String, label: String?, role: String?, x: Double, y: Double, width: Double = 100.0, height: Double = 20.0) =
    UiElement(target, label, role, true, false, null, null, null, Bounds(x, y, width, height))

private fun view(vararg elements: UiElement) = InspectResult(elements.toList(), elements.size, false, "dark", 100, "r1")

/** The Expo app's cases not already covered one-for-one elsewhere (see docs/test-parity.md). */
class ParityTest {
    @Test
    fun explainsAMissingChatBoxInsteadOfTypingBlindly() = runTest {
        val desktop = FakeDesktop()
        val (result, partial) = sendChatMessage(desktop.client(), "hi")
        assertTrue(result is Outcome.Failed)
        assertEquals(false, partial)
        assertEquals(emptyList<Input>(), desktop.delivered)
    }

    @Test
    fun scrollsAChatOutFromUnderTheSidebarFooterBeforeOpeningIt() = runTest {
        val desktop = FakeDesktop()
        var first = desktop.element("c1", "Open conversation Plan my week", "Button")
        var second = desktop.element("c2", "Open conversation What can you do?", "Button")
        desktop.replace(first, first.copy(bounds = Bounds(8.0, 593.0, 239.0, 49.0)).also { first = it })
        desktop.replace(second, second.copy(bounds = Bounds(8.0, 674.0, 239.0, 49.0)).also { second = it })
        val tour = desktop.element("tour", "Take a guided tour", "Button")
        desktop.replace(tour, tour.copy(bounds = Bounds(8.0, 702.0, 239.0, 36.0)))
        desktop.onDeliver = {
            if (it.action == "scroll") {
                desktop.replace(first, first.copy(bounds = first.bounds.copy(y = first.bounds.y - 60)).also { moved -> first = moved })
                desktop.replace(second, second.copy(bounds = second.bounds.copy(y = second.bounds.y - 60)).also { moved -> second = moved })
            }
        }
        assertTrue(openConversation(desktop.client(), "What can you do?") is Outcome.Ok)
        assertEquals(listOf(Input("scroll", "c1", lines = -2), Input("click", "c2")), desktop.delivered)
    }

    @Test
    fun opensAVisibleChatDirectly() = runTest {
        val desktop = FakeDesktop()
        val row = desktop.element("c1", "Open conversation Plan my week", "Button", 593.0)
        desktop.replace(row, row.copy(bounds = row.bounds.copy(height = 49.0)))
        assertTrue(openConversation(desktop.client(), "Plan my week") is Outcome.Ok)
        assertEquals(listOf(Input("click", "c1")), desktop.delivered)
    }

    @Test
    fun findsTheConversationLogOrNone() {
        assertEquals("log", conversationArea(view(at("log", "Conversation", "Log", 280.0, 80.0, 976.0, 598.0)))?.target)
        assertNull(conversationArea(view(at("x", "Settings", "Button", 0.0, 0.0))))
    }

    @Test
    fun readsAllTextInReadingOrderWithoutAConversation() {
        val chat = view(
            at("log", "Conversation", "Log", 280.0, 80.0, 976.0, 598.0),
            at("today", "Today", "Heading", 8.0, 611.0, 239.0, 26.0),
            at("title", "Hello from the phone", null, 38.0, 643.0, 173.0, 21.0),
            at("reply", "First, connect an AI", null, 445.0, 338.0, 646.0, 22.0),
            at("hint", "Type / for skills · Enter to send", null, 606.0, 760.0, 324.0, 18.0),
        )
        assertEquals(listOf("reply", "today", "title", "hint"), readableText(chat).map { it.target })
    }

    @Test
    fun ranksSkillsByNameWordAndDescription() {
        val skills = readSkillMenu(
            view(
                at("s1", "/files. Choose local files to read and discuss in this chat. ", "Button", 0.0, 0.0),
                at("s2", "/web-search. Use when current information or sourced web research is needed. <question>", "Button", 0.0, 0.0),
                at("s3", "/table. Use when comparing structured records in a native table. <what to present>", "Button", 0.0, 0.0),
            ),
        )
        assertEquals(listOf("web-search"), matchSkills(skills, "/search").map { it.name })
        assertEquals(listOf("table"), matchSkills(skills, "compar").map { it.name })
        assertEquals(3, matchSkills(skills, "").size)
        assertNull(slashQuery("and/or"))
        assertTrue(explainNotice("Unknown skill /tabel. Type / to see available skills.").contains("Unknown skill"))
    }

    @Test
    fun rejectsEveryPairingLinkTheDesktopRejects() {
        val link = fixture("pairing-link.txt")
        val good = decodeTicket(link)!!
        fun payload(edit: (MutableMap<String, kotlinx.serialization.json.JsonElement>) -> Unit): String {
            val ticket = mutableMapOf<String, kotlinx.serialization.json.JsonElement>(
                "version" to JsonPrimitive(1), "address" to good.address.toJson(), "secret" to JsonPrimitive(good.secret),
            )
            edit(ticket)
            return "sovra://remote-control?ticket=" + java.net.URLEncoder.encode(JsonObject(ticket).toString(), Charsets.UTF_8)
        }
        fun address(edit: (MutableMap<String, kotlinx.serialization.json.JsonElement>) -> Unit) = payload { ticket ->
            val fields = good.address.toJson().toMutableMap()
            edit(fields)
            ticket["address"] = JsonObject(fields)
        }
        val bad = listOf(
            "$link&extra=x", "$link#fragment", link.replace("sovra:", "https:"), link.replace("remote-control", "remote-control/x"),
            link.replace("ticket=", "tickets="), "x".repeat(2049),
            payload { it["version"] = JsonPrimitive(2) },
            payload { it["extra"] = JsonPrimitive(true) },
            payload { it["secret"] = JsonPrimitive("short") },
            address { it["relay"] = JsonPrimitive("http://relay.example") },
            address { it["relay"] = JsonPrimitive("https://user@relay.example/") },
            address { it["relay"] = JsonPrimitive("https://relay.example/?q=1") },
            address { it["addrs"] = JsonArray(listOf(JsonPrimitive("not an address"))) },
            address { it["addrs"] = JsonArray(List(17) { JsonPrimitive("1.2.3.4:5") }) },
            address { it["device_id"] = JsonPrimitive("spoof") },
        )
        for (candidate in bad) assertNull(candidate, decodeTicket(candidate))
        assertEquals(good.address, decodeTicket(payload { })!!.address)
    }

    @Test
    fun acceptsOnlyRealSocketAddresses() {
        assertTrue(isSocketAddress("10.0.0.1:443"))
        assertTrue(isSocketAddress("[2001:db8::1]:443"))
        assertTrue(!isSocketAddress("256.0.0.1:443"))
        assertTrue(!isSocketAddress("10.0.0.1"))
        assertTrue(!isSocketAddress("10.0.0.1:70000"))
    }

    @Test
    fun parsesInspectPayloadsAndDropsMalformedElements() {
        val raw = buildJsonObject {
            put("elements", JsonArray(listOf(
                Json.parseToJsonElement("""{"target":"t1","label":"Send","role":"Button","visible":true,"disabled":null,"bounds":{"x":1,"y":2,"width":3,"height":4}}"""),
                Json.parseToJsonElement("""{"target":"t2","label":"broken"}"""),
            )))
            put("total", 2); put("truncated", false); put("input_values", "omitted for privacy")
            put("appearance", "light"); put("interface_scale_percent", 125); put("revision", "abc")
        }
        val parsed = parseInspect(raw)!!
        assertEquals(listOf("t1"), parsed.elements.map { it.target })
        assertEquals("light", parsed.appearance)
        assertNull(parseInspect(buildJsonObject { put("elements", JsonArray(emptyList())) }))
        assertNull(parseInspect(JsonNull))
    }

    @Test
    fun marksBoldItalicAndInlineCode() {
        assertEquals(
            listOf(Span("a "), Span("b", bold = true), Span(" and "), Span("c", italic = true), Span(" with "), Span("d", code = true)),
            parseInline("a **b** and *c* with `d`"),
        )
    }

    @Test
    fun leavesPlainTextAlone() {
        assertEquals(listOf(Block.Paragraph("Just a sentence.")), parseBlocks("Just a sentence."))
        assertEquals(listOf(Span("5 * 3 = 15")), parseInline("5 * 3 = 15"))
    }
}
