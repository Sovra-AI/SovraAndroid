package ai.sovra.remote.protocol

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ports of the Expo app's `client.test.ts`. */
class ClientTest {
    private val ticket = PairingTicket(desktopAddress, SECRET)

    private fun Outcome<*>.code() = (this as? Outcome.Failed)?.code

    @Test
    fun pairsOnceThenReconnectsWithHelloWithoutANewInvitation() = runTest {
        val desktop = FakeDesktop()
        val client = RemoteClient(desktop.transport(), null)
        assertTrue(client.inspect() is Outcome.Failed)
        assertEquals(Outcome.Ok(desktopAddress), client.pair(ticket, "Pixel"))
        assertNull(desktop.invitation)
        // Re-scanning the same (now consumed) QR still succeeds via Hello.
        assertTrue(client.pair(ticket, "Pixel") is Outcome.Ok)
        val restarted = RemoteClient(desktop.transport(), desktopAddress)
        assertTrue(restarted.inspect() is Outcome.Ok)
        assertEquals(listOf("hello", "control:inspect"), desktop.requests.takeLast(2))
    }

    @Test
    fun usesUpAFreshCodeWhenAnAlreadyPairedPhoneScansItAgain() = runTest {
        val desktop = FakeDesktop().apply { paired = true }
        assertTrue(RemoteClient(desktop.transport(), null).pair(ticket, "Pixel") is Outcome.Ok)
        assertEquals(listOf("hello", "pair"), desktop.requests)
        assertNull(desktop.invitation)
    }

    @Test
    fun staysPairedWhenTheCodeIsAlreadyUsedAfterHello() = runTest {
        val desktop = FakeDesktop().apply { paired = true; invitation = null }
        assertEquals(Outcome.Ok(desktopAddress), RemoteClient(desktop.transport(), null).pair(ticket, "Pixel"))
    }

    @Test
    fun reportsAWrongInvitationAndNeverTreatsUnreachableAsRevoked() = runTest {
        val desktop = FakeDesktop().apply { invitation = "ff".repeat(32) }
        assertEquals(ErrorCode.InvalidInvitation, RemoteClient(desktop.transport(), null).pair(ticket, "Pixel").code())
        val offline = FakeDesktop().apply { unreachable = true; paired = true }
        assertEquals(ErrorCode.Unreachable, RemoteClient(offline.transport(), desktopAddress).inspect().code())
    }

    @Test
    fun usesExactSequencesStartingAtOneAndInspectsBeforeEveryMutation() = runTest {
        val desktop = FakeDesktop()
        val send = desktop.element("send", "Send", "Button")
        val client = desktop.client()
        assertTrue(client.act(Intent.Click(send.ref())) is Outcome.Ok)
        assertTrue(client.act(Intent.Key("enter")) is Outcome.Ok)
        assertEquals(listOf(1, 2, 3, 4), desktop.sequences)
        assertEquals(listOf("hello", "control:inspect", "control:click", "control:inspect", "control:key"), desktop.requests)
        assertEquals(listOf(Input("click", "send"), Input("key", key = "enter")), desktop.delivered)
    }

    @Test
    fun retriesOnlyStaleObservationsWhichTheDesktopNeverDelivers() = runTest {
        val desktop = FakeDesktop()
        val send = desktop.element("send", "Send", "Button")
        val client = desktop.client()
        desktop.churn = 2
        assertTrue(client.act(Intent.Click(send.ref())) is Outcome.Ok)
        assertEquals(1, desktop.delivered.size)
        desktop.churn = 10
        assertEquals(ErrorCode.StaleObservation, client.act(Intent.Click(send.ref())).code())
        assertEquals(1, desktop.delivered.size)
    }

    @Test
    fun neverReplaysAMutationWhoseReplyWasLost() = runTest {
        val desktop = FakeDesktop()
        val client = desktop.client()
        assertTrue(client.inspect() is Outcome.Ok)
        val before = desktop.requests.size
        desktop.dropReplyFor = "key"
        assertEquals(ErrorCode.OutcomeUnknown, client.act(Intent.Key("enter")).code())
        assertEquals(listOf(Input("key", key = "enter")), desktop.delivered)
        assertEquals(2, desktop.requests.size - before)
        // The next mutation starts from a fresh Hello and inspection, not a replay.
        assertTrue(client.act(Intent.Key("escape")) is Outcome.Ok)
        assertEquals(listOf("enter", "escape"), desktop.delivered.map { it.key })
    }

    @Test
    fun reconnectsReadsAfterTheDesktopClosesAnIdleConnection() = runTest {
        val desktop = FakeDesktop()
        val client = desktop.client()
        assertTrue(client.inspect() is Outcome.Ok)
        desktop.closeConnections()
        assertTrue(client.inspect() is Outcome.Ok)
        assertEquals(2, desktop.requests.count { it == "hello" })
    }

    @Test
    fun refindsAControlByLabelAfterReconnectingChangesTargetIds() = runTest {
        val desktop = FakeDesktop()
        desktop.element("old", "Settings", "Button")
        val client = desktop.client()
        val settings = (client.inspect() as Outcome.Ok).value.elements[0]
        desktop.elements.clear()
        desktop.element("new", "Settings", "Button")
        assertTrue(client.act(Intent.Click(settings.ref())) is Outcome.Ok)
        assertEquals(listOf(Input("click", "new")), desktop.delivered)
        desktop.elements.clear()
        assertEquals(ErrorCode.StaleObservation, client.act(Intent.Click(settings.ref())).code())
    }

    @Test
    fun typesThroughTheRealComposerThenPressesEnter() = runTest {
        val desktop = FakeDesktop()
        desktop.element("nav", "Settings", "Button")
        desktop.element("composer", "Ask Sovra anything", "MultilineTextInput", 500.0)
        val (result, _) = sendChatMessage(desktop.client(), "Hello there\nsecond line")
        assertTrue(result is Outcome.Ok)
        assertEquals(
            listOf(
                Input("click", "composer"), Input("key", key = "secondary-a"), Input("key", key = "backspace"),
                Input("type", text = "Hello there"), Input("key", key = "shift-enter"), Input("type", text = "second line"),
                Input("key", key = "enter"),
            ),
            desktop.delivered,
        )
    }

    @Test
    fun typesASkillWithWordsAfterItUnchanged() = runTest {
        val desktop = FakeDesktop()
        desktop.element("composer", "Message Sovra", "MultilineTextInput", 500.0)
        assertTrue(sendChatMessage(desktop.client(), "/table three plants").first is Outcome.Ok)
        assertTrue(Input("type", text = "/table three plants") in desktop.delivered)
    }

    @Test
    fun typesIntoAChosenFieldWithoutPressingEnter() = runTest {
        val desktop = FakeDesktop()
        val field = desktop.element("task", "Task name", "TextInput", 372.0)
        assertTrue(typeInto(desktop.client(), field, "Morning briefing") is Outcome.Ok)
        assertEquals(listOf(Input("click", "task"), Input("type", text = "Morning briefing")), desktop.delivered)
    }

    @Test
    fun neverMistakesAFormFieldOrSearchBoxForTheChatBox() = runTest {
        val form = FakeDesktop()
        form.element("group-name", "1. What should the group be called?", "TextInput", 336.0)
        form.element("search", "Search chats", "TextInput", 500.0)
        val (result, _) = sendChatMessage(form.client(), "hi")
        assertTrue(result is Outcome.Failed)
        assertEquals(emptyList<Input>(), form.delivered)

        val both = FakeDesktop()
        both.element("search", "Search chats", "TextInput", 800.0)
        both.element("composer", "Message Sovra", "MultilineTextInput", 700.0)
        sendChatMessage(both.client(), "hi")
        assertEquals(Input("click", "composer"), both.delivered.first())
    }

    @Test
    fun stopsAfterOpeningChatIfThereIsStillNoChatBox() = runTest {
        val desktop = FakeDesktop()
        desktop.element("nav-chat", "Chat", "Button", 120.0)
        val (result, partial) = sendChatMessage(desktop.client(), "hi")
        assertTrue(result is Outcome.Failed)
        assertEquals(false, partial)
        assertEquals(listOf(Input("click", "nav-chat")), desktop.delivered)
    }

    @Test
    fun pressesOnlyTheRealStopControlOrEscape() = runTest {
        val desktop = FakeDesktop()
        desktop.element("phone-off", "Stop phone control", "Button")
        desktop.element("unshare", "Stop sharing", "Button")
        val client = desktop.client()
        assertTrue(stopWork(client) is Outcome.Ok)
        desktop.element("stop", "Stop generating", "Button")
        assertTrue(stopWork(client) is Outcome.Ok)
        assertEquals(listOf(Input("key", key = "escape"), Input("click", "stop")), desktop.delivered)
    }

    @Test
    fun splitsLongTextWithinTheLimitAndCleansPhoneNames() {
        val steps = planTyping("é".repeat(3000))
        assertEquals(2, steps.size)
        assertTrue(steps.all { it is Intent.Type && utf8Length(it.text) <= 4096 })
        assertEquals("Pixel 9", sanitizePhoneName("  Pixel\u0007 9  "))
        assertTrue(utf8Length(sanitizePhoneName("é".repeat(100))) <= 80)
        assertEquals("My phone", sanitizePhoneName("\u0001"))
    }
}
