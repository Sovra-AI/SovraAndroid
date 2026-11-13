/*
 * Moves around Sovra on the computer the way a person would, and sends chat messages
 * through its real chat box. Every step is a real, revision-guarded click or keystroke;
 * a failed step stops the flow and nothing is replayed.
 */
package ai.sovra.remote.protocol

private fun overlaps(a: UiElement, b: UiElement): Boolean {
    val p = a.bounds
    val q = b.bounds
    return p.x < q.x + q.width && q.x < p.x + p.width && p.y < q.y + q.height && q.y < p.y + p.height
}

/** Another control (e.g. the sidebar footer) sits over part of this one, so its centre may not be clickable. */
private fun covered(view: InspectResult, row: UiElement): Boolean = view.elements.any {
    it !== row && it.visible && it.role == "Button" && !contains(row, it) && overlaps(row, it)
}

private const val MAX_CHAT_SCROLLS = 8

private inline fun <T> Outcome<T>.orReturn(block: (Outcome.Failed) -> Nothing): T = when (this) {
    is Outcome.Ok -> value
    is Outcome.Failed -> block(this)
}

/** Replace the text in a text box on the computer (empty clears it). */
private suspend fun fillBox(client: RemoteClient, box: UiElement, query: String): Outcome<Unit> {
    // "secondary" is Cmd on macOS and Ctrl elsewhere (GPUI keystroke syntax).
    for (step in listOf(Intent.Click(box.ref()), Intent.Key("secondary-a"), Intent.Key("backspace"))) {
        client.act(step).orReturn { return it }
    }
    val text = query.replace("\u0000", "").trim()
    if (text.isNotEmpty()) client.act(Intent.Type(text)).orReturn { return it }
    return ok(Unit)
}

private fun chatSearchBox(view: InspectResult) = view.elements.firstOrNull { it.visible && it.label == CHAT_SEARCH_LABEL }

/** Filter Sovra's chat list by words in the title (empty shows every chat again). */
suspend fun searchChats(client: RemoteClient, query: String): Outcome<InspectResult> {
    val view = client.inspect().orReturn { return it }
    val box = chatSearchBox(view) ?: return fail(ErrorCode.InvalidRequest, "The chat list is not showing on your computer.")
    fillBox(client, box, query).orReturn { return it }
    return client.inspect()
}

/** Scroll Sovra's chat list so older chats come into view. */
suspend fun moreChats(client: RemoteClient): Outcome<InspectResult> {
    val view = client.inspect().orReturn { return it }
    val anchor = chatList(view).filter { it.shown && !covered(view, it.element) }.lastOrNull()?.element
        ?: return fail(ErrorCode.InvalidRequest, "The chat list is not showing on your computer.")
    client.act(Intent.Scroll(anchor.ref(), -6)).orReturn { return it }
    return client.inspect()
}

/**
 * Open a chat from Sovra's sidebar by its title. A row outside the scrolled list, or
 * half-hidden under the footer (Inspect reports unclipped bounds), is scrolled into view
 * over a fully visible row first; a chat that is not rendered at all is found by search.
 */
suspend fun openConversation(client: RemoteClient, title: String): Outcome<Unit> {
    var searched = false
    for (attempt in 0..MAX_CHAT_SCROLLS) {
        val view = client.inspect().orReturn { return it }
        val rows = chatList(view)
        val row = rows.firstOrNull { it.title == title }
        val anchor = rows.firstOrNull { it.shown && !covered(view, it.element) }?.element
        if (row != null && row.shown && !covered(view, row.element)) {
            client.act(Intent.Click(row.element.ref())).orReturn { return it }
            if (searched) {
                // Show the whole list again; the chat stays open.
                (client.inspect() as? Outcome.Ok)?.value?.let(::chatSearchBox)?.let { fillBox(client, it, "") }
            }
            return ok(Unit)
        }
        if (row != null && anchor != null && attempt < MAX_CHAT_SCROLLS) {
            val down = row.element.bounds.y > anchor.bounds.y
            client.act(Intent.Scroll(anchor.ref(), if (down) -2 else 2)).orReturn { return it }
            continue
        }
        if (searched) break
        val box = chatSearchBox(view) ?: break
        fillBox(client, box, title).orReturn { return it }
        searched = true
    }
    return fail(ErrorCode.InvalidRequest, "Could not find that chat on your computer. It may have been deleted.")
}

/** Show Sovra's chat (the conversation and its chat box) if another page is open. */
suspend fun openChatPage(client: RemoteClient): Outcome<InspectResult> {
    val view = client.inspect().orReturn { return it }
    if (findComposer(view) != null) return ok(view)
    val nav = findNavigation(view, "Chat") ?: return fail(ErrorCode.InvalidRequest, "Chat is not reachable from this screen on your computer.")
    client.act(Intent.Click(nav.ref())).orReturn { return it }
    return client.inspect()
}

/** Split text into typeable chunks: newlines become Shift+Enter, chunks fit the 4 KiB limit. */
fun planTyping(text: String): List<Intent> {
    val steps = mutableListOf<Intent>()
    text.replace("\r\n", "\n").replace('\r', '\n').split('\n').forEachIndexed { index, line ->
        if (index > 0) steps += Intent.Key("shift-enter")
        val chunk = StringBuilder()
        var offset = 0
        while (offset < line.length) {
            val next = line.offsetByCodePoints(offset, 1)
            val character = line.substring(offset, next)
            if (utf8Length(chunk.toString() + character) > MAX_TEXT_BYTES) {
                steps += Intent.Type(chunk.toString())
                chunk.clear()
            }
            chunk.append(character)
            offset = next
        }
        if (chunk.isNotEmpty()) steps += Intent.Type(chunk.toString())
    }
    return steps
}

/**
 * A lone "/table" leaves Sovra's `/` menu open, where Enter only completes the name.
 * A trailing space closes the menu, so Enter runs the skill (or Sovra explains what it needs).
 */
fun bareCommand(text: String): String = if (Regex("^\\s*/[A-Za-z0-9-]+$").matches(text)) "$text " else text

/**
 * Send a chat message through Sovra's real chat box, the way a person would: open Chat
 * if needed, focus the box, clear it, type, press Enter. `partial` on failure says some
 * text may already be in the box on the computer.
 */
suspend fun sendChatMessage(client: RemoteClient, text: String): Pair<Outcome<Unit>, Boolean> {
    val message = text.replace("\u0000", "")
    if (message.isBlank()) return fail(ErrorCode.InvalidRequest, "Write a message first.") to false
    val page = openChatPage(client)
    if (page is Outcome.Failed) return page to false
    val composer = findComposer((page as Outcome.Ok).value)
        ?: return fail(ErrorCode.InvalidRequest, "There is no chat box on screen. Open a chat on your computer first.") to false
    // The phone's text is the whole message: clear anything left in the chat box there first.
    for (step in listOf(Intent.Click(composer.ref()), Intent.Key("secondary-a"), Intent.Key("backspace"))) {
        val result = client.act(step)
        if (result is Outcome.Failed) return result to false
    }
    var typedAny = false
    for (step in planTyping(bareCommand(message))) {
        val result = client.act(step)
        if (result is Outcome.Failed) return result to (typedAny || result.code == ErrorCode.OutcomeUnknown)
        typedAny = true
    }
    val sent = client.act(Intent.Key("enter"))
    if (sent is Outcome.Failed) {
        return fail(sent.code, "Your text is in the chat box on your computer but may not be sent: ${sent.message}") to true
    }
    return ok(Unit) to false
}

/** Type into one particular field on the computer (a form box, a search box), without pressing Enter. */
suspend fun typeInto(client: RemoteClient, field: UiElement, text: String): Outcome<Unit> {
    val message = text.replace("\u0000", "")
    if (message.isEmpty()) return fail(ErrorCode.InvalidRequest, "Write something first.")
    client.act(Intent.Click(field.ref())).orReturn { return it }
    for (step in planTyping(message)) client.act(step).orReturn { return it }
    return ok(Unit)
}

/** Press Sovra's visible Stop control; falls back to Escape when none is shown. */
suspend fun stopWork(client: RemoteClient): Outcome<Unit> {
    val view = client.inspect().orReturn { return it }
    val stop = findStop(view)
    val result = if (stop != null) client.act(Intent.Click(stop.ref())) else client.act(Intent.Key("escape"))
    return if (result is Outcome.Failed) result else ok(Unit)
}
