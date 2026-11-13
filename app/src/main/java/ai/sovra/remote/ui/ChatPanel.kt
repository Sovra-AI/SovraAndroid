/*
 * The Chat tab: Sovra's conversation as bubbles, and a chat box that sends through the
 * real one on the computer. Typing `/` offers Sovra's skills; choosing one shows it as a
 * chip, so the message is just the words that follow.
 */
package ai.sovra.remote.ui

import ai.sovra.remote.data.RemoteStore
import ai.sovra.remote.protocol.InspectResult
import ai.sovra.remote.protocol.Skill
import ai.sovra.remote.protocol.UiElement
import ai.sovra.remote.protocol.conversationArea
import ai.sovra.remote.protocol.explainNotice
import ai.sovra.remote.protocol.findComposer
import ai.sovra.remote.protocol.findStop
import ai.sovra.remote.protocol.hintPrompt
import ai.sovra.remote.protocol.matchSkills
import ai.sovra.remote.protocol.needsText
import ai.sovra.remote.protocol.skillNotice
import ai.sovra.remote.protocol.slashQuery
import ai.sovra.remote.protocol.transcript
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

private data class Pending(val text: String, /** `updatedAt` when sending finished. */ val doneAt: Long?)

private const val MAX_SUGGESTIONS = 6

@Composable
fun ChatPanel(
    store: RemoteStore,
    snapshot: InspectResult,
    screenshot: String?,
    live: Boolean,
    chosen: Skill?,
    onChosenUsed: () -> Unit,
    onBrowseSkills: () -> Unit,
    onSkillSent: () -> Unit,
    onOpenedPage: () -> Unit,
    onScroll: (UiElement, Int) -> Unit,
) {
    val p = LocalPalette.current
    val state by store.state.collectAsState()
    var draft by remember { mutableStateOf("") }
    var skill by remember { mutableStateOf<Skill?>(null) }
    var pending by remember { mutableStateOf<Pending?>(null) }
    var focused by remember { mutableStateOf(false) }
    // Sovra keeps its last `/` notice until something is typed there; only show it after our own `/` send.
    var asked by remember { mutableStateOf(false) }
    var skillsFailed by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val list = rememberLazyListState()

    val chat = remember(snapshot) { transcript(snapshot) }
    val area = remember(snapshot) { conversationArea(snapshot) }
    val composer = remember(snapshot) { findComposer(snapshot) }
    val stop = remember(snapshot) { findStop(snapshot) }
    val notice = remember(snapshot) { skillNotice(snapshot) }

    // A skill picked on the Skills tab starts the next message.
    LaunchedEffect(chosen) {
        val next = chosen ?: return@LaunchedEffect
        skill = next
        draft = ""
        onChosenUsed()
        runCatching { focus.requestFocus() }
    }

    // The sent bubble stays until the computer shows a newer view.
    LaunchedEffect(state.updatedAt, pending) {
        val done = pending?.doneAt ?: return@LaunchedEffect
        if (state.updatedAt > done) pending = null
    }

    val query = if (skill == null) slashQuery(draft) else null
    LaunchedEffect(query != null, live, state.skills == null) {
        if (query != null && live && state.skills == null) skillsFailed = !store.fetchSkills()
    }
    val skills = state.skills
    val suggestions = if (query != null && skills != null) matchSkills(skills, query).take(MAX_SUGGESTIONS) else emptyList()

    val current = skill
    val message = if (current != null) "/${current.name}${if (draft.isNotBlank()) " ${draft.trim()}" else ""}" else draft
    // "/web-search" typed out in full counts as choosing it; "/" or an unknown name does not send.
    val typedSkill = query?.let { q -> skills?.firstOrNull { it.name == q.lowercase() } }
    val ready = when {
        current != null -> !current.needsText() || draft.isNotBlank()
        query != null -> typedSkill != null || (skills == null && query.isNotEmpty())
        else -> draft.isNotBlank()
    }
    val canSend = live && !state.working && pending == null && ready

    fun choose(next: Skill) {
        skill = next
        draft = ""
        runCatching { focus.requestFocus() }
    }

    fun send() {
        if (!canSend) return
        if (typedSkill != null && typedSkill.needsText()) {
            // Ask for the missing words here instead of letting the computer refuse it.
            choose(typedSkill)
            return
        }
        val text = message.trim()
        val sentDraft = draft
        val sentSkill = skill
        pending = Pending(text, null)
        asked = text.startsWith("/")
        if (asked) onSkillSent()
        draft = ""
        skill = null
        store.launch {
            if (store.send(message)) {
                pending = Pending(text, System.currentTimeMillis())
                if (text.startsWith("/")) {
                    store.refresh()
                    val after = store.state.value.snapshot
                    if (after != null && findComposer(after) == null) {
                        pending = null
                        onOpenedPage()
                    }
                }
            } else {
                // Nothing was lost: put the words back so they can be sent again.
                pending = null
                draft = sentDraft
                skill = sentSkill
            }
        }
    }

    // Follow the conversation while the reader is at the bottom.
    val atBottom by remember { derivedStateOf { !list.canScrollForward } }
    val itemCount = chat.messages.size + (if (pending != null) 1 else 0) + 1
    LaunchedEffect(itemCount, chat.messages.lastOrNull()?.text?.length) {
        if (atBottom || pending != null) list.scrollToItem(maxOf(0, itemCount - 1))
    }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(Space.small)) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                composer == null -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = p.ring)
                    VSpace(Space.small)
                    SText("Opening the chat on your computer…", tone = TextTone.Secondary)
                }
                chat.messages.isEmpty() && area != null -> ConversationPicture(snapshot, screenshot, area, live && !state.working, onScroll)
                chat.messages.isEmpty() && pending == null -> Column(Modifier.fillMaxSize().padding(horizontal = Space.small), verticalArrangement = Arrangement.Center) {
                    SText("What’s on your mind?", style = Type.pageTitle)
                    VSpace(Space.small)
                    SText("Ask Sovra anything. It works on your computer and the answer appears here.", tone = TextTone.Secondary)
                    VSpace(Space.medium)
                    SButton("Browse skills", onBrowseSkills)
                }
                else -> LazyColumn(state = list, verticalArrangement = Arrangement.spacedBy(Space.medium), modifier = Modifier.fillMaxSize()) {
                    if (area != null && chat.earlier) {
                        item {
                            SButton(
                                "Show earlier messages", { onScroll(area, 8) }, Modifier.fillMaxWidth(),
                                variant = ButtonVariant.Ghost, compact = true, enabled = live && !state.working,
                            )
                        }
                    }
                    items(chat.messages, key = { it.id }) { entry ->
                        if (entry.fromYou) Bubble(entry.text) else Box(Modifier.semantics(mergeDescendants = true) { contentDescription = "Sovra: ${entry.text}" }) { Markdown(entry.text) }
                    }
                    pending?.let { item { Bubble(it.text, sending = true) } }
                    item {
                        if (chat.working != null || stop != null || (pending != null && pending?.doneAt == null)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.small)) {
                                CircularProgressIndicator(Modifier.size(16.dp), color = p.ring, strokeWidth = 2.dp)
                                SText(
                                    if (pending != null && pending?.doneAt == null) "Sending to your computer…" else chat.working ?: "Sovra is working…",
                                    style = Type.caption, tone = TextTone.Secondary,
                                )
                            }
                        }
                    }
                }
            }
        }

        if (notice != null && asked) SText(explainNotice(notice), Modifier.padding(horizontal = Space.small), style = Type.caption, tone = TextTone.Warning)

        if (query != null) {
            Card(Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
                when {
                    skills == null && skillsFailed -> Row(verticalAlignment = Alignment.CenterVertically) {
                        SText("Couldn’t get the skills from your computer.", Modifier.weight(1f), tone = TextTone.Secondary)
                        SButton("Try again", { skillsFailed = false; store.launch { skillsFailed = !store.fetchSkills(force = true) } }, compact = true, variant = ButtonVariant.Ghost)
                    }
                    skills == null -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.small)) {
                        CircularProgressIndicator(Modifier.size(16.dp), color = p.ring, strokeWidth = 2.dp)
                        SText("Getting skills from your computer…", tone = TextTone.Secondary)
                    }
                    suggestions.isEmpty() -> SText("No skill matches “/$query”.", tone = TextTone.Secondary)
                    else -> Column(Modifier.verticalScroll(rememberScrollState())) {
                        suggestions.forEach { entry ->
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = Layout.control)
                                    .clip(RoundedCornerShape(Radius.button))
                                    .clickable(role = Role.Button, onClickLabel = "Use ${entry.title}") { choose(entry) }
                                    .padding(horizontal = 4.dp, vertical = Space.small),
                            ) {
                                SText(entry.title, style = Type.label)
                                if (entry.description.isNotEmpty()) SText(entry.description, style = Type.caption, tone = TextTone.Secondary, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }

        val shape = RoundedCornerShape(Radius.panel)
        Column(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(p.composerBackground)
                .border(if (focused) 1.5.dp else 1.dp, if (focused) p.composerFocusBorder else p.composerBorder, shape)
                .padding(Space.small),
            verticalArrangement = Arrangement.spacedBy(Space.small),
        ) {
            current?.let { chip ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.small)) {
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(p.selection)
                            .clickable(role = Role.Button, onClickLabel = "Remove ${chip.title}") { skill = null }
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(Space.snug),
                    ) {
                        SText(chip.title, style = Type.label, tone = TextTone.Link)
                        SText("✕", style = Type.label, tone = TextTone.Secondary)
                    }
                    SText(chip.description, Modifier.weight(1f), style = Type.caption, tone = TextTone.Muted, maxLines = 1)
                }
            }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Space.small)) {
                Box(Modifier.weight(1f).heightIn(min = Layout.control).padding(horizontal = 4.dp), contentAlignment = Alignment.CenterStart) {
                    val placeholder = current?.hintPrompt() ?: "Message Sovra  ·  type / for skills"
                    if (draft.isEmpty()) SText(placeholder, tone = TextTone.Muted, maxLines = 1)
                    BasicTextField(
                        draft, { draft = it },
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 140.dp)
                            .focusRequester(focus)
                            .onFocusChanged { focused = it.isFocused }
                            .semantics { contentDescription = "Message to Sovra" },
                        enabled = live,
                        textStyle = Type.body.copy(color = p.text),
                        cursorBrush = SolidColor(p.ring),
                    )
                }
                if (stop != null) {
                    Box(
                        Modifier
                            .heightIn(min = Layout.control)
                            .clip(RoundedCornerShape(999.dp))
                            .background(p.stopBackground)
                            .clickable(role = Role.Button, onClickLabel = "Stop what Sovra is doing") { store.launch { store.stop() } }
                            .padding(horizontal = Space.medium),
                        contentAlignment = Alignment.Center,
                    ) { SText("Stop", style = Type.label.copy(color = p.primaryForeground)) }
                } else {
                    Box(
                        Modifier
                            .heightIn(min = Layout.control)
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (canSend) p.sendReadyBackground else p.hover)
                            .clickable(enabled = canSend, role = Role.Button, onClickLabel = "Send") { send() }
                            .padding(horizontal = Space.medium),
                        contentAlignment = Alignment.Center,
                    ) { SText("Send", style = Type.label.copy(color = if (canSend) p.sendReadyForeground else p.mutedText)) }
                }
            }
        }
    }
}

@Composable
private fun Bubble(text: String, sending: Boolean = false) {
    val p = LocalPalette.current
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Box(
            Modifier
                .widthIn(max = 300.dp)
                .alpha(if (sending) 0.6f else 1f)
                .clip(RoundedCornerShape(topStart = Radius.panel, topEnd = Radius.panel, bottomStart = Radius.panel, bottomEnd = Radius.small))
                .background(p.userBubble)
                .semantics(mergeDescendants = true) { contentDescription = if (sending) "Sending: $text" else "You: $text" }
                .padding(horizontal = Space.medium, vertical = 10.dp),
        ) { SText(text) }
    }
}
