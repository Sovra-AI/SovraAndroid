/*
 * The More tab: every other part of Sovra (Team, Schedules, Models, Settings…), rebuilt
 * as phone screens. Text reads as text, buttons are rows, switches are switches and text
 * boxes get a typing bar. Every tap is a real, revision-guarded action on the computer.
 * Pages a `/` command opens (like /help) land here too.
 */
package ai.sovra.remote.ui

import ai.sovra.remote.data.RemoteStore
import ai.sovra.remote.protocol.InspectResult
import ai.sovra.remote.protocol.Intent
import ai.sovra.remote.protocol.PageItem
import ai.sovra.remote.protocol.UiElement
import ai.sovra.remote.protocol.describe
import ai.sovra.remote.protocol.findComposer
import ai.sovra.remote.protocol.openChatPage
import ai.sovra.remote.protocol.openPage
import ai.sovra.remote.protocol.pages
import ai.sovra.remote.protocol.ref
import ai.sovra.remote.protocol.tidyLabel
import ai.sovra.remote.protocol.typeInto
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** An icon for the pages people know by name; everything else gets the grid. */
private fun pageIcon(label: String) = when (label.trim().lowercase()) {
    "skills" -> SovraIcons.Skills
    "settings" -> SovraIcons.Settings
    "chat" -> SovraIcons.Chat
    "team", "groups" -> SovraIcons.Team
    "schedules" -> SovraIcons.Calendar
    "models" -> SovraIcons.Chip
    "artifacts" -> SovraIcons.Document
    "training" -> SovraIcons.Book
    "my ai's", "my ais" -> SovraIcons.Person
    "knowledge" -> SovraIcons.Stack
    "connections" -> SovraIcons.Link
    else -> SovraIcons.More
}

@Composable
fun MorePanel(
    store: RemoteStore,
    snapshot: InspectResult,
    disabled: Boolean,
    onChat: () -> Unit,
    onScroll: (UiElement, Int) -> Unit,
    title: String? = null,
    onOpened: (String) -> Unit = {},
) {
    val onChatPage = remember(snapshot) { findComposer(snapshot) != null }
    val press: (UiElement) -> Unit = { store.launch { store.act(Intent.Click(it.ref())) } }
    if (onChatPage) {
        Directory(snapshot, disabled, onOpen = { page ->
            val label = page.label?.trim().orEmpty()
            if (label.lowercase() == "chat") onChat() else {
                onOpened(label)
                press(page)
            }
        })
    } else {
        PageView(store, snapshot, disabled, press, title, onBack = { store.launch { store.perform { openChatPage(it) } } }, onScroll)
    }
}

/** Every part of Sovra, as a list. */
@Composable
private fun Directory(snapshot: InspectResult, disabled: Boolean, onOpen: (UiElement) -> Unit) {
    val shortcuts = remember(snapshot) { pages(snapshot).filter { it.label?.trim()?.lowercase() != "chat" } }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(Space.small), modifier = Modifier.fillMaxSize()) {
        item {
            Column(Modifier.padding(horizontal = Space.compact, vertical = Space.small), verticalArrangement = Arrangement.spacedBy(Space.compact)) {
                SText("Everything in Sovra", Modifier.semantics { heading() }, style = Type.pageTitle)
                SText("Open any part of Sovra. It shows up here, made for your phone.", tone = TextTone.Secondary)
            }
        }
        if (shortcuts.isEmpty()) {
            item {
                Card(subtle = true) {
                    SText("Nothing to open right now. Make sure Sovra’s main window is open on your computer.", tone = TextTone.Secondary)
                }
            }
        }
        items(shortcuts, key = { it.target }) { page ->
            val label = page.label?.trim().orEmpty()
            ListRow(label, enabled = !disabled, hint = "Open $label", icon = pageIcon(label)) { onOpen(page) }
        }
    }
}

/** The open page, rebuilt from what Sovra shows: no pictures of the computer. */
@Composable
private fun PageView(
    store: RemoteStore,
    snapshot: InspectResult,
    disabled: Boolean,
    press: (UiElement) -> Unit,
    opened: String?,
    onBack: () -> Unit,
    onScroll: (UiElement, Int) -> Unit,
) {
    val p = LocalPalette.current
    val read = remember(snapshot) { openPage(snapshot) }
    // A page that does not name itself often starts by repeating the name it was opened by.
    val page = remember(read, opened) {
        val first = read.items.firstOrNull()
        val repeats = (first as? PageItem.Text)?.text ?: (first as? PageItem.Heading)?.text
        if (read.title == null && repeats != null && repeats == opened) {
            read.copy(items = read.items.drop(1).dropWhile { it is PageItem.Break })
        } else {
            read
        }
    }
    var field by remember { mutableStateOf<UiElement?>(null) }
    var text by remember { mutableStateOf("") }
    val typing = remember { FocusRequester() }
    // The typing bar takes the keyboard as soon as a box is chosen.
    LaunchedEffect(field) { if (field != null) runCatching { typing.requestFocus() } }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(Space.small)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.compact)) {
            IconAction(SovraIcons.Back, "Back to everything in Sovra", onBack, enabled = !disabled)
            SText(page.title ?: opened ?: "Sovra", Modifier.weight(1f).semantics { heading() }, style = Type.sectionTitle, maxLines = 1)
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.small)) {
            if (page.items.isEmpty()) {
                item { SText("Nothing to show on this page yet.", Modifier.padding(Space.small), tone = TextTone.Secondary) }
            }
            items(page.items, key = { it.key }) { item ->
                when (item) {
                    is PageItem.Heading -> SText(
                        item.text, Modifier.padding(top = Space.small, start = Space.compact).semantics { heading() }, style = Type.sectionTitle,
                    )
                    is PageItem.Text -> SText(item.text, Modifier.padding(horizontal = Space.compact), tone = TextTone.Secondary)
                    is PageItem.Action -> ControlRow(item.element.describe(), item.element, disabled, onClick = { press(item.element) }) {
                        SIcon(SovraIcons.Chevron, null, tint = p.mutedText, size = 18.dp)
                    }
                    is PageItem.Toggle -> ControlRow(item.element.describe(), item.element, disabled, onClick = { press(item.element) }) {
                        Switch(
                            item.on, { press(item.element) },
                            enabled = !disabled && item.element.disabled != true,
                            colors = SwitchDefaults.colors(checkedTrackColor = p.primary, uncheckedTrackColor = p.hover),
                        )
                    }
                    is PageItem.Choice -> ControlRow(item.element.describe(), item.element, disabled, selected = item.chosen, onClick = { press(item.element) }) {
                        if (item.chosen) SIcon(SovraIcons.Check, "Chosen", tint = p.link, size = 20.dp)
                    }
                    is PageItem.Field -> ControlRow(
                        item.element.describe(), item.element, disabled, subtitle = "Tap to type",
                        onClick = {
                            press(item.element)
                            text = ""
                            field = item.element
                        },
                    ) { SIcon(SovraIcons.Keyboard, null, tint = p.link, size = 20.dp) }
                    is PageItem.Break -> Box(Modifier.fillMaxWidth().padding(vertical = Space.small).height(1.dp).background(p.border))
                }
            }
            page.scroll?.let { region ->
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.small)) {
                        SButton("Back up", { onScroll(region, 5) }, Modifier.weight(1f), compact = true, variant = ButtonVariant.Ghost, enabled = !disabled)
                        SButton("Show more", { onScroll(region, -5) }, Modifier.weight(1f), compact = true, enabled = !disabled)
                    }
                }
            }
        }

        field?.let { target ->
            val shape = RoundedCornerShape(Radius.panel)
            Column(
                Modifier.fillMaxWidth().clip(shape).background(p.surface).border(1.dp, p.border, shape).padding(Space.small),
                verticalArrangement = Arrangement.spacedBy(Space.small),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SText("Typing into “${target.describe()}”", Modifier.weight(1f).padding(start = Space.compact), style = Type.caption, tone = TextTone.Secondary, maxLines = 1)
                    IconAction(SovraIcons.Close, "Stop typing", { field = null }, size = Layout.compactControl)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Space.small), verticalAlignment = Alignment.CenterVertically) {
                    val box = RoundedCornerShape(Radius.button)
                    Box(
                        Modifier.weight(1f).heightIn(min = Layout.control).clip(box).background(p.canvas).border(1.dp, p.controlBorder, box).padding(horizontal = 12.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (text.isEmpty()) SText("Write here", tone = TextTone.Muted, maxLines = 1)
                        BasicTextField(
                            text, { text = it },
                            Modifier.fillMaxWidth().focusRequester(typing).semantics { contentDescription = "Text for ${target.describe()} on your computer" },
                            singleLine = true, textStyle = Type.body.copy(color = p.text), cursorBrush = SolidColor(p.ring),
                        )
                    }
                    SButton("Type", {
                        val words = text
                        store.launch { if (store.perform { typeInto(it, target, words) }) text = "" }
                    }, variant = ButtonVariant.Primary, enabled = !disabled && text.isNotEmpty())
                }
                SButton("Press Enter", { store.launch { store.act(Intent.Key("enter")) } }, Modifier.fillMaxWidth(), compact = true, variant = ButtonVariant.Ghost, enabled = !disabled)
            }
        }
    }
}

/** One control on a page as a full-width, thumb-sized row. */
@Composable
private fun ControlRow(
    title: String,
    element: UiElement,
    disabled: Boolean,
    subtitle: String? = null,
    selected: Boolean = false,
    onClick: () -> Unit,
    trailing: @Composable () -> Unit,
) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(Radius.card)
    val off = element.disabled == true
    val title = tidyLabel(title)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Layout.control + 8.dp)
            .clip(shape)
            .background(if (selected) p.selection else p.surface)
            .border(1.dp, if (selected) p.ring else p.border, shape)
            .alpha(if (off) 0.5f else 1f)
            .clickable(enabled = !disabled && !off, role = Role.Button, onClickLabel = title, onClick = onClick)
            .padding(horizontal = Space.medium, vertical = Space.small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            SText(title, style = Type.label, maxLines = 3)
            val note = if (off) "Not available right now" else subtitle
            note?.let { SText(it, style = Type.caption, tone = TextTone.Muted) }
        }
        trailing()
    }
}
