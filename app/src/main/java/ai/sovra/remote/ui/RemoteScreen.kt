/*
 * The remote screen: four tabs — Chat, Chats, Skills and Computer — over Sovra on the
 * paired computer. Every tap becomes a real, revision-guarded action there.
 */
package ai.sovra.remote.ui

import ai.sovra.remote.data.Phase
import ai.sovra.remote.data.RemoteStore
import ai.sovra.remote.data.Tone
import ai.sovra.remote.protocol.Intent
import ai.sovra.remote.protocol.NEW_CHAT_LABEL
import ai.sovra.remote.protocol.Outcome
import ai.sovra.remote.protocol.Skill
import ai.sovra.remote.protocol.UiElement
import ai.sovra.remote.protocol.fail
import ai.sovra.remote.protocol.ErrorCode
import ai.sovra.remote.protocol.findComposer
import ai.sovra.remote.protocol.moreChats
import ai.sovra.remote.protocol.openChatPage
import ai.sovra.remote.protocol.openConversation
import ai.sovra.remote.protocol.ref
import ai.sovra.remote.protocol.searchChats
import ai.sovra.remote.protocol.transcript
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay

private data class Status(val label: String, val color: @Composable () -> androidx.compose.ui.graphics.Color)

private fun status(phase: Phase): Status = when (phase) {
    Phase.Loading -> Status("Starting") { LocalPalette.current.mutedText }
    Phase.Unpaired -> Status("Not paired") { LocalPalette.current.mutedText }
    Phase.Connecting -> Status("Connecting…") { LocalPalette.current.link }
    Phase.Connected -> Status("Connected") { LocalPalette.current.success }
    Phase.Unreachable -> Status("Can’t reach your computer") { LocalPalette.current.warning }
    Phase.Disabled -> Status("Phone control is off") { LocalPalette.current.warning }
    Phase.Busy -> Status("Busy") { LocalPalette.current.warning }
    Phase.Revoked -> Status("Forgotten") { LocalPalette.current.danger }
    Phase.Incompatible -> Status("Update needed") { LocalPalette.current.danger }
}

/** Plain-language recovery for each state; desktop messages are shown, never parsed. */
private fun recovery(phase: Phase): Pair<String, String>? = when (phase) {
    Phase.Connecting -> "Connecting to your computer…" to "This can take a few seconds on mobile data."
    Phase.Unreachable -> "Open Sovra on your computer" to
        "Make sure your computer is awake and online, Sovra is open, and “Use Sovra from your phone” is switched on in Sovra (it switches off whenever Sovra restarts). We keep trying while this screen is open."
    Phase.Disabled -> "Turn on phone control" to
        "In Sovra on your computer, open Skills and choose “Use Sovra from your phone”. For your safety it switches off whenever Sovra restarts."
    Phase.Busy -> "Sovra is busy" to "Another phone may be in control, or a new phone is being paired. Try again in a moment."
    Phase.Incompatible -> "Update needed" to "This app and Sovra on your computer are different versions. Update both."
    else -> null
}

enum class Tab(val label: String) { Chat("Chat"), Chats("Chats"), Skills("Skills"), Computer("Computer") }

private const val QUIET_NOTICE_MS = 5000L
private const val SKILL_PAGE_GRACE_MS = 15_000L

@Composable
fun RemoteScreen(store: RemoteStore, onSettings: () -> Unit, onUnpaired: () -> Unit) {
    val state by store.state.collectAsState()
    val p = LocalPalette.current
    var tab by rememberSaveable { mutableStateOf(Tab.Chat) }
    var chosen by remember { mutableStateOf<Skill?>(null) }
    var skillSentAt by remember { mutableLongStateOf(0L) }

    // Foreground only: live while resumed, stopped when the app goes to the background.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> store.startLive()
                Lifecycle.Event.ON_PAUSE -> store.stopLive()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) store.startLive()
        onDispose {
            lifecycle.removeObserver(observer)
            store.stopLive()
        }
    }

    LaunchedEffect(state.phase) {
        if (state.phase == Phase.Revoked || state.phase == Phase.Unpaired) onUnpaired()
    }

    // Good news and tips fade on their own; problems stay until dismissed.
    LaunchedEffect(state.notice) {
        val notice = state.notice ?: return@LaunchedEffect
        if (notice.tone == Tone.Danger || notice.tone == Tone.Warning) return@LaunchedEffect
        delay(QUIET_NOTICE_MS)
        store.setNotice(null)
    }

    val snapshot = state.snapshot
    val live = state.phase == Phase.Connected && snapshot != null
    val hasComposer = snapshot?.let { findComposer(it) != null } == true
    val labelled = snapshot?.let { transcript(it).messages.isNotEmpty() } == true

    // Pictures only for the Computer tab, or the Chat tab when messages are not labelled.
    DisposableEffect(tab, labelled) {
        store.setPictureWanted(tab == Tab.Computer || (tab == Tab.Chat && !labelled))
        onDispose { store.setPictureWanted(false) }
    }

    // The Chat tab always shows Sovra's chat: bring it up on the computer when another page
    // is open. Once per visit, so a failure is reported instead of retried in a loop.
    var tried by remember { mutableStateOf(false) }
    LaunchedEffect(tab, live, hasComposer, state.working) {
        if (tab != Tab.Chat || hasComposer) {
            tried = false
            return@LaunchedEffect
        }
        // A `/` command that opens a page (Models, Schedules…) leaves the chat on purpose.
        if (!live || state.working || tried || System.currentTimeMillis() - skillSentAt < SKILL_PAGE_GRACE_MS) return@LaunchedEffect
        tried = true
        store.perform { openChatPage(it) }
    }

    // The Skills tab lists Sovra's skills, read once from the computer.
    LaunchedEffect(tab, live) {
        if (tab == Tab.Skills && live && state.skills == null) store.fetchSkills()
    }

    val busy = !live || state.working
    val scroll: (UiElement, Int) -> Unit = { element, lines -> store.launch { store.act(Intent.Scroll(element.ref(), lines)) } }
    val newChat: () -> Unit = {
        tab = Tab.Chat
        store.launch {
            store.perform { client ->
                when (val page = openChatPage(client)) {
                    is Outcome.Failed -> page
                    is Outcome.Ok -> {
                        val button = page.value.elements.firstOrNull { it.visible && it.label == NEW_CHAT_LABEL }
                        if (button == null) fail(ErrorCode.InvalidRequest, "Could not start a new chat on your computer.")
                        else client.act(Intent.Click(button.ref()))
                    }
                }
            }
        }
    }

    Column(
        Modifier.fillMaxSize().background(p.canvas).safeDrawingPadding().imePadding().padding(horizontal = Space.medium, vertical = Space.small),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.small)) {
            Column(Modifier.weight(1f)) {
                SText(state.desktop?.name ?: "Your computer", style = Type.sectionTitle, maxLines = 1)
                val current = status(state.phase)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Space.snug),
                    modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = "Status: ${current.label}" },
                ) {
                    StatusDot(current.color())
                    SText(current.label, style = Type.caption, tone = TextTone.Secondary)
                    if (state.working) CircularProgressIndicator(Modifier.padding(start = 2.dp).size(12.dp), color = p.ring, strokeWidth = 2.dp)
                }
            }
            if (tab == Tab.Chat && live) SButton("New chat", newChat, compact = true, variant = ButtonVariant.Ghost, enabled = !busy)
            SButton("Settings", onSettings, compact = true, variant = ButtonVariant.Ghost)
        }

        state.notice?.let { NoticeBanner(it.tone, it.text) { store.setNotice(null) } }

        val help = recovery(state.phase)
        if (help != null && !live) {
            Card {
                SText(help.first, style = Type.sectionTitle)
                SText(help.second, tone = TextTone.Secondary)
                state.detail?.let { SText(it, style = Type.caption, tone = TextTone.Muted) }
                if (state.phase != Phase.Connecting) SButton("Try again", { store.startLive() }, Modifier.fillMaxWidth(), variant = ButtonVariant.Primary)
            }
        }

        if (snapshot != null) {
            Segmented(Tab.entries.map { it.label }, tab.ordinal, { tab = Tab.entries[it] })
            Box(Modifier.weight(1f).alpha(if (live) 1f else 0.5f)) {
                when (tab) {
                    Tab.Chat -> ChatPanel(
                        store = store,
                        snapshot = snapshot,
                        screenshot = state.screenshot,
                        live = live,
                        chosen = chosen,
                        onChosenUsed = { chosen = null },
                        onBrowseSkills = { tab = Tab.Skills },
                        onSkillSent = { skillSentAt = System.currentTimeMillis() },
                        onOpenedPage = {
                            skillSentAt = 0
                            tab = Tab.Computer
                        },
                        onScroll = scroll,
                    )
                    Tab.Chats -> ChatsView(
                        snapshot = snapshot,
                        disabled = busy,
                        onOpen = { title ->
                            tab = Tab.Chat
                            store.launch { store.perform { openConversation(it, title) } }
                        },
                        onNew = newChat,
                        onSearch = { query -> store.launch { store.perform { searchChats(it, query) } } },
                        onMore = { store.launch { store.perform { moreChats(it) } } },
                    )
                    Tab.Skills -> SkillsView(
                        skills = state.skills,
                        disabled = busy,
                        onUse = {
                            chosen = it
                            tab = Tab.Chat
                        },
                        onReload = { store.launch { store.fetchSkills(force = true) } },
                    )
                    Tab.Computer -> ComputerPanel(
                        store = store,
                        snapshot = snapshot,
                        screenshot = state.screenshot,
                        disabled = busy,
                        onOpenPage = { page ->
                            // Sovra's chat lives in the Chat tab.
                            if (page.label?.trim()?.lowercase() == "chat") tab = Tab.Chat
                            store.launch { store.act(Intent.Click(page.ref())) }
                        },
                        onChatBox = { tab = Tab.Chat },
                        isChatBox = { element -> findComposer(snapshot)?.target == element.target },
                        onScroll = scroll,
                    )
                }
            }
        }
    }
}
