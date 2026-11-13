package ai.sovra.remote.protocol

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun fixture(name: String) = checkNotNull(ProtocolTest::class.java.getResource("/fixtures/$name")).readText().trim()

/** Fixtures are pretty-printed JSON; compare compact forms, which keep key order. */
private fun compact(text: String) = Json.parseToJsonElement(text).toString()

class ProtocolTest {
    @Test
    fun requestsMatchTheDesktopFixturesByteForByte() {
        assertEquals(compact(fixture("hello-request.json")), encodeRequest(RemoteOperation.Hello))
        assertEquals(compact(fixture("pair-request.json")), encodeRequest(RemoteOperation.Pair("02".repeat(32), "My phone")))
        assertEquals(
            compact(fixture("inspect-request.json")),
            encodeRequest(RemoteOperation.Control("01".repeat(32), "03".repeat(32), 1, null, UiRequest.Inspect)),
        )
    }

    @Test
    fun decodesDesktopRepliesStrictly() {
        assertTrue(decodeResponse(fixture("ready-response.json")) is RemoteResponse.Ready)
        assertTrue(decodeResponse(fixture("error-response.json")) is RemoteResponse.Error)
        assertEquals(RemoteResponse.Paired, decodeResponse("""{"status":"paired"}"""))
        assertNull(decodeResponse("""{"status":"error","code":"made_up","message":"x"}"""))
        assertNull(decodeResponse("""{"status":"ready","version":1}"""))
        assertNull(decodeResponse("not json"))
    }

    @Test
    fun validatesInputLikeTheDesktop() {
        assertNotNull(validateUiRequest(UiRequest.Click("")))
        assertNotNull(validateUiRequest(UiRequest.Scroll("x", 101)))
        assertNotNull(validateUiRequest(UiRequest.Type("a".repeat(4097))))
        assertNotNull(validateUiRequest(UiRequest.Type("é".repeat(2049))))
        assertNull(validateUiRequest(UiRequest.Type("hello world")))
    }

    @Test
    fun decodesAPairingLinkFromTheDesktopEncoder() {
        val ticket = decodeTicket(fixture("pairing-link.txt"))!!
        assertEquals("01".repeat(32), ticket.address.device)
        assertEquals("https://euw1-1.relay.n0.iroh.link./", ticket.address.relay)
        assertEquals(listOf("192.168.1.20:51234", "[fe80::1]:51234"), ticket.address.addrs)
        assertEquals("02".repeat(32), ticket.secret)
        val link = fixture("pairing-link.txt")
        for (bad in listOf("$link&extra=x", "$link#fragment", link.replace("sovra:", "https:"), link.replace("ticket=", "tickets="), "x".repeat(2049))) {
            assertNull(bad, decodeTicket(bad))
        }
        assertTrue(isSocketAddress("10.0.0.1:443"))
        assertTrue(isSocketAddress("[2001:db8::1]:443"))
        assertTrue(!isSocketAddress("256.0.0.1:443"))
    }

    @Test
    fun pairsThenControlsWithFreshRevisions() = runTest {
        val desktop = FakeDesktop()
        val client = RemoteClient(desktop.transport(), null)
        val paired = client.pair(decodeTicket(fixture("pairing-link.txt").replace("01".repeat(32), DESKTOP))!!, "Phone")
        assertTrue(paired.toString(), paired is Outcome.Ok)
        val button = desktop.element("send", "Send", "Button")
        assertTrue(client.act(Intent.Click(button.ref())) is Outcome.Ok)
        assertEquals(listOf(Input("click", "send")), desktop.delivered)
    }

    @Test
    fun neverReplaysInputWhoseReplyWasLost() = runTest {
        val desktop = FakeDesktop()
        val client = desktop.client()
        desktop.dropReplyFor = "type"
        val result = client.act(Intent.Type("hello"))
        assertEquals(ErrorCode.OutcomeUnknown, (result as Outcome.Failed).code)
        assertEquals(1, desktop.delivered.size)
    }

    @Test
    fun reportsADisabledComputer() = runTest {
        val desktop = FakeDesktop()
        val client = desktop.client()
        desktop.enabled = false
        assertEquals(ErrorCode.Disabled, (client.inspect() as Outcome.Failed).code)
    }
}
