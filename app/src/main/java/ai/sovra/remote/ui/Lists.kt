/*
 * The Chats and Skills tabs: Sovra's chat list from its sidebar, and its `/` skills.
 * Every tap becomes a real click on the computer.
 */
package ai.sovra.remote.ui

import ai.sovra.remote.protocol.InspectResult
import ai.sovra.remote.protocol.Skill
import ai.sovra.remote.protocol.chatList
import ai.sovra.remote.protocol.matchSkills
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay

/**
 * Keeps every chat title seen while browsing, in the sidebar's order. Sovra only renders
 * the rows near its visible list, so older chats appear as "Show older chats" scrolls it.
 */
internal fun mergeTitles(known: List<String>, current: List<String>): List<String> {
    val next = known.toMutableList()
    var at = -1
    for (title in current) {
        val index = next.indexOf(title)
        if (index >= 0) {
            at = index
            continue
        }
        next.add(at + 1, title)
        at += 1
    }
    return next
}

private const val SEARCH_DELAY_MS = 700L

@Composable
fun ChatsView(
    snapshot: InspectResult,
    disabled: Boolean,
    onOpen: (String) -> Unit,
    onNew: () -> Unit,
    onSearch: (String) -> Unit,
    onMore: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val current = remember(snapshot) { chatList(snapshot).map { it.title } }
    var titles by remember { mutableStateOf(current) }
    var applied by remember { mutableStateOf("") }
    val search by rememberUpdatedState(onSearch)
    val searching = query.isNotBlank()

    // While searching, the computer's filtered list is shown as is.
    LaunchedEffect(current) {
        if (!searching) titles = mergeTitles(titles, current)
    }
    // Search on the computer shortly after typing stops; clear it when leaving the tab.
    LaunchedEffect(query) {
        delay(SEARCH_DELAY_MS)
        if (applied == query.trim()) return@LaunchedEffect
        applied = query.trim()
        search(applied)
    }
    DisposableEffect(Unit) { onDispose { if (applied.isNotEmpty()) search("") } }

    val shown = if (searching) current else titles
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(Space.small)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Space.small), verticalAlignment = Alignment.CenterVertically) {
            SearchBox(query, { query = it }, "Search your chats", Modifier.weight(1f))
            SButton("New chat", onNew, variant = ButtonVariant.Primary, enabled = !disabled, hint = "Start a fresh conversation")
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(Space.small)) {
            if (shown.isEmpty()) {
                item {
                    SText(
                        if (searching) "No chats match “${query.trim()}”." else "No chats yet. Tap New chat to start one.",
                        Modifier.padding(Space.small), tone = TextTone.Secondary,
                    )
                }
            }
            items(shown, key = { it }) { title -> ListRow(title, enabled = !disabled, hint = "Open this chat") { onOpen(title) } }
            if (!searching && titles.isNotEmpty()) {
                item { SButton("Show older chats", onMore, Modifier.fillMaxWidth(), variant = ButtonVariant.Ghost, enabled = !disabled) }
            }
        }
    }
}

@Composable
fun SkillsView(skills: List<Skill>?, disabled: Boolean, onUse: (Skill) -> Unit, onReload: () -> Unit) {
    var query by remember { mutableStateOf("") }
    if (skills == null) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = LocalPalette.current.ring)
            VSpace(Space.small)
            SText("Getting skills from your computer…", tone = TextTone.Secondary)
            SButton("Try again", onReload, variant = ButtonVariant.Ghost, enabled = !disabled)
        }
        return
    }
    val shown = remember(skills, query) { matchSkills(skills, query) }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(Space.small)) {
        SearchBox(query, { query = it }, "What do you want to do?", Modifier.fillMaxWidth())
        LazyColumn(verticalArrangement = Arrangement.spacedBy(Space.small)) {
            item {
                SText(
                    "Pick a skill, then say what you need. You can also type / in the chat.",
                    Modifier.padding(horizontal = Space.compact), style = Type.caption, tone = TextTone.Secondary,
                )
            }
            if (shown.isEmpty()) item { SText("No skills match “${query.trim()}”.", Modifier.padding(Space.small), tone = TextTone.Secondary) }
            items(shown, key = { it.name }) { skill ->
                ListRow(skill.title, skill.description, enabled = !disabled, hint = "Start a message with this skill") { onUse(skill) }
            }
        }
    }
}
