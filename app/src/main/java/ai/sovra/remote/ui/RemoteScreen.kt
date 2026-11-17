/*
 * The remote screen: four tabs — Chat, Chats, Skills and More — over Sovra on the paired
 * computer, as a bottom bar on phones and a side rail on tablets and in landscape. Every
 * tap becomes a real, revision-guarded action there.
 */
package ai.sovra.remote.ui

import ai.sovra.remote.data.ConnectionProgress
import ai.sovra.remote.data.Phase
import ai.sovra.remote.data.RemoteStore
import ai.sovra.remote.data.Tone
import ai.sovra.remote.data.developerLog
import ai.sovra.remote.data.routeLabel
import ai.sovra.remote.protocol.ErrorCode
import ai.sovra.remote.protocol.Intent
import ai.sovra.remote.protocol.NEW_CHAT_LABEL
import ai.sovra.remote.protocol.Outcome
import ai.sovra.remote.protocol.Skill
import ai.sovra.remote.protocol.UiElement
import ai.sovra.remote.protocol.fail
import ai.sovra.remote.protocol.findComposer
import ai.sovra.remote.protocol.moreChats
import ai.sovra.remote.protocol.openChatPage
import ai.sovra.remote.protocol.openConversation
import ai.sovra.remote.protocol.ref
import ai.sovra.remote.protocol.searchChats
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
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

/** Plain-language recovery for each state; desktop messages are shown, never parsed. Connecting and unreachable show live progress instead (`ConnectionCard`). */
private fun recovery(phase: Phase): Pair<String, String>? = when (phase) {
    Phase.Disabled -> "Turn on phone control" to
        "In Sovra on your computer, open Skills and choose “Use Sovra from your phone”. For your safety it switches off whenever Sovra restarts."
    Phase.Busy -> "Sovra is busy" to "Another phone may be in control, or a new phone is being paired. Try again in a moment."
    Phase.Incompatible -> "Update needed" to "This app and Sovra on your computer are different versions. Update both."
    else -> null
}

enum class Tab(val label: String, val icon: ImageVector) {
    Chat("Chat", SovraIcons.Chat),
    Chats("Chats", SovraIcons.Chats),
    Skills("Skills", SovraIcons.Skills),
    More("More", SovraIcons.More),
}

private const val QUIET_NOTICE_MS = 5000L
private const val SKILL_PAGE_GRACE_MS = 15_000L

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RemoteScreen(store: RemoteStore, onSettings: () -> Unit, onUnpaired: () -> Unit, startTab: Tab = Tab.Chat) {
    val state by store.state.collectAsState()
    val p = LocalPalette.current
    var tab by rememberSaveable { mutableStateOf(startTab) }
    // What the phone last opened (a More row or a `/` skill), for pages that do not name themselves.
    var pageTitle by rememberSaveable { mutableStateOf<String?>(null) }
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
    // A problem only the person can fix: show just its card, not a dimmed screen that cannot work.
    val blocked = !live && (recovery(state.phase) != null || state.phase == Phase.Unreachable)
    val hasComposer = snapshot?.let { findComposer(it) != null } == true

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

    val wide = isWide()
    val keyboard = WindowInsets.isImeVisible
    val select: (Tab) -> Unit = { tab = it }

    Row(Modifier.fillMaxSize().background(p.canvas)) {
        if (wide && !blocked) NavRail(tab, select, Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Start + WindowInsetsSides.Vertical)))
        Column(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.End + if (wide) WindowInsetsSides.Bottom else WindowInsetsSides.Start))
                .imePadding(),
        ) {
            Readable(Modifier.weight(1f)) {
                Column(
                    Modifier.fillMaxSize().padding(horizontal = Space.medium, vertical = Space.small),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TopBar(
                        name = state.desktop?.name ?: "Your computer",
                        current = when {
                            state.phase == Phase.Connected -> status(Phase.Connected).copy(label = routeLabel(state.route))
                            state.progress?.let { it.step != ConnectionProgress.Step.Waiting } == true &&
                                (state.phase == Phase.Connecting || state.phase == Phase.Unreachable) -> status(Phase.Connecting)
                            else -> status(state.phase)
                        },
                        working = state.working,
                        onNewChat = if (live && (tab == Tab.Chat || tab == Tab.Chats)) newChat else null,
                        newChatEnabled = !busy,
                        onSettings = onSettings,
                    )

                    state.notice?.let { NoticeBanner(it.tone, it.text) { store.setNotice(null) } }

                    val progress = state.progress
                    var details by remember { mutableStateOf(false) }
                    if (!state.preferences.developer && !live && (progress?.attempt ?: 0) > 1) {
                        SButton(if (details) "Hide technical details" else "Show technical details", { details = !details }, compact = true, variant = ButtonVariant.Ghost)
                    }
                    if (progress != null && !live && (state.phase == Phase.Connecting || state.phase == Phase.Unreachable)) {
                        ConnectionCard(progress, state.desktop?.name ?: "your computer", state.detail) { store.retryNow() }
                    }
                    if (state.preferences.developer || details) {
                        DeveloperPanel(state.phase.name.lowercase(), state.route?.name?.lowercase(), ::developerLog)
                    }

                    val help = recovery(state.phase)
                    if (help != null && !live) {
                        Card {
                            SText(help.first, style = Type.sectionTitle)
                            SText(help.second, tone = TextTone.Secondary)
                            state.detail?.let { SText(it, style = Type.caption, tone = TextTone.Muted) }
                            SButton("Try again", { store.startLive() }, Modifier.fillMaxWidth(), variant = ButtonVariant.Primary)
                        }
                    }

                    if (snapshot == null || blocked) {
                        if (help == null && progress == null) Waiting(state.desktop?.name ?: "your computer")
                    } else {
                        Box(Modifier.weight(1f).alpha(if (live) 1f else 0.5f)) {
                            when (tab) {
                                Tab.Chat -> ChatPanel(
                                    store = store,
                                    snapshot = snapshot,
                                    live = live,
                                    chosen = chosen,
                                    onChosenUsed = { chosen = null },
                                    onBrowseSkills = { tab = Tab.Skills },
                                    onSkillSent = { skillSentAt = System.currentTimeMillis() },
                                    onOpenedPage = { title ->
                                        // A `/` command (like /help) opened one of Sovra's pages: show it.
                                        pageTitle = title
                                        skillSentAt = 0
                                        tab = Tab.More
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
                                Tab.More -> MorePanel(
                                    store = store,
                                    snapshot = snapshot,
                                    disabled = busy,
                                    onChat = { tab = Tab.Chat },
                                    onScroll = scroll,
                                    title = pageTitle,
                                    onOpened = { pageTitle = it },
                                )
                            }
                        }
                    }
                }
            }
            // The keyboard covers the tab bar anyway; keep the chat box right above it.
            if (!wide && !keyboard && !blocked) BottomBar(tab, select)
        }
    }
}

/** The computer's name and how it is connected, with New chat and Settings. */
@Composable
private fun TopBar(name: String, current: Status, working: Boolean, onNewChat: (() -> Unit)?, newChatEnabled: Boolean, onSettings: () -> Unit) {
    val p = LocalPalette.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.compact)) {
        Column(Modifier.weight(1f).semantics(mergeDescendants = true) { contentDescription = "$name. Status: ${current.label}" }) {
            SText(name, style = Type.sectionTitle, maxLines = 1)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.snug)) {
                StatusDot(current.color())
                SText(current.label, style = Type.caption, tone = TextTone.Secondary, maxLines = 1)
                if (working) CircularProgressIndicator(Modifier.padding(start = 2.dp).size(12.dp), color = p.ring, strokeWidth = 2.dp)
            }
        }
        if (onNewChat != null) IconAction(SovraIcons.Compose, "New chat", onNewChat, enabled = newChatEnabled)
        IconAction(SovraIcons.Settings, "Settings", onSettings)
    }
}

/** Shown before the first view arrives, so the screen is never blank. */
@Composable
private fun Waiting(name: String) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Space.medium, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(color = LocalPalette.current.ring)
        SText("Connecting to $name…", style = Type.body.copy(textAlign = TextAlign.Center), tone = TextTone.Secondary)
    }
}

@Composable
private fun TabButton(tab: Tab, chosen: Boolean, onSelect: (Tab) -> Unit, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    Column(
        modifier
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(Radius.card))
            .clickable(role = Role.Tab, onClickLabel = tab.label) { onSelect(tab) }
            .semantics { selected = chosen }
            .padding(vertical = Space.snug),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Space.hair, Alignment.CenterVertically),
    ) {
        Box(
            Modifier.width(56.dp).height(30.dp).clip(RoundedCornerShape(999.dp)).background(if (chosen) p.selection else androidx.compose.ui.graphics.Color.Transparent),
            contentAlignment = Alignment.Center,
        ) { SIcon(tab.icon, null, tint = if (chosen) p.link else p.secondaryText, size = 22.dp) }
        SText(tab.label, style = Type.caption.copy(fontWeight = if (chosen) androidx.compose.ui.text.font.FontWeight.SemiBold else null), tone = if (chosen) TextTone.Primary else TextTone.Secondary, maxLines = 1)
    }
}

/** Phones: the tabs along the bottom, within thumb reach. */
@Composable
private fun BottomBar(tab: Tab, onSelect: (Tab) -> Unit) {
    val p = LocalPalette.current
    Column(Modifier.fillMaxWidth().background(p.surface)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(p.border))
        Row(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = Space.small, vertical = Space.compact),
        ) {
            Tab.entries.forEach { TabButton(it, it == tab, onSelect, Modifier.weight(1f)) }
        }
    }
}

/** Tablets, foldables and landscape: the tabs down the side. */
@Composable
private fun NavRail(tab: Tab, onSelect: (Tab) -> Unit, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    Row(modifier.fillMaxHeight().background(p.surface)) {
        Column(
            Modifier.fillMaxHeight().width(88.dp).padding(vertical = Space.medium),
            verticalArrangement = Arrangement.spacedBy(Space.small),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Logo(40.dp)
            Box(Modifier.height(Space.small))
            Tab.entries.forEach { TabButton(it, it == tab, onSelect, Modifier.width(80.dp)) }
        }
        Box(Modifier.fillMaxHeight().width(1.dp).background(p.border))
    }
}
