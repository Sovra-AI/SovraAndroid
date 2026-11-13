/*
 * The Computer tab: everything else in Sovra. Jump to a page (Team, Schedules, Models…),
 * then tap its controls on a live picture of the window, or pick them from a list. Text
 * boxes there get their own small typing bar.
 */
package ai.sovra.remote.ui

import ai.sovra.remote.data.RemoteStore
import ai.sovra.remote.protocol.ElementKind
import ai.sovra.remote.protocol.InspectResult
import ai.sovra.remote.protocol.Intent
import ai.sovra.remote.protocol.UiElement
import ai.sovra.remote.protocol.actionableControls
import ai.sovra.remote.protocol.describe
import ai.sovra.remote.protocol.kind
import ai.sovra.remote.protocol.pageFirst
import ai.sovra.remote.protocol.pages
import ai.sovra.remote.protocol.readableText
import ai.sovra.remote.protocol.ref
import ai.sovra.remote.protocol.typeInto
import ai.sovra.remote.protocol.windowSize
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

private val KEYS = listOf("Enter" to "enter", "Esc" to "escape", "Tab" to "tab", "⌫" to "backspace", "↑" to "up", "↓" to "down")

@Composable
fun rememberPicture(screenshot: String?): ImageBitmap? = remember(screenshot) {
    screenshot?.let { data ->
        runCatching {
            val bytes = Base64.decode(data, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }.getOrNull()
    }
}

@Composable
fun ComputerPanel(
    store: RemoteStore,
    snapshot: InspectResult,
    screenshot: String?,
    disabled: Boolean,
    onOpenPage: (UiElement) -> Unit,
    onChatBox: () -> Unit,
    isChatBox: (UiElement) -> Boolean,
    onScroll: (UiElement, Int) -> Unit,
) {
    var asList by remember { mutableStateOf(false) }
    var keys by remember { mutableStateOf(false) }
    var field by remember { mutableStateOf<UiElement?>(null) }
    var text by remember { mutableStateOf("") }
    val p = LocalPalette.current
    val typing = remember { FocusRequester() }
    // The typing bar takes the keyboard as soon as a box is chosen.
    LaunchedEffect(field) { if (field != null) runCatching { typing.requestFocus() } }

    val press: (UiElement) -> Unit = { element ->
        store.launch { store.act(Intent.Click(element.ref())) }
        if (element.kind == ElementKind.Text) {
            if (isChatBox(element)) onChatBox() else field = element
        }
    }
    val type: () -> Unit = {
        val target = field
        if (target != null && text.isNotEmpty()) store.launch { if (store.perform { typeInto(it, target, text) }) text = "" }
    }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(Space.small)) {
        val shortcuts = remember(snapshot) { pages(snapshot) }
        if (shortcuts.isNotEmpty()) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Space.compact)) {
                shortcuts.forEach { page ->
                    SButton(
                        page.label ?: "", { onOpenPage(page) },
                        variant = if (page.selected == true) ButtonVariant.Primary else ButtonVariant.Secondary,
                        enabled = !disabled, hint = "Open this page on your computer",
                    )
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            SText(
                if (asList) "Everything you can press on your computer right now." else "Tap anything outlined to press it. Pinch to zoom.",
                Modifier.weight(1f), style = Type.caption, tone = TextTone.Secondary,
            )
            SButton(if (asList) "Show picture" else "Show as list", { asList = !asList }, compact = true, variant = ButtonVariant.Ghost)
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (asList) ControlsList(snapshot, disabled, press, onScroll)
            else ZoomBox { MirrorView(snapshot, screenshot, disabled, press) }
        }

        field?.let { target ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                SText("Typing into “${target.label ?: "a box"}”", Modifier.weight(1f), style = Type.caption, tone = TextTone.Secondary, maxLines = 1)
                SButton("Done", { field = null }, compact = true, variant = ButtonVariant.Ghost)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Space.small), verticalAlignment = Alignment.CenterVertically) {
                val shape = RoundedCornerShape(Radius.button)
                Box(
                    Modifier.weight(1f).heightIn(min = Layout.control).clip(shape).background(p.surface).border(1.dp, p.controlBorder, shape).padding(horizontal = 12.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (text.isEmpty()) SText("Type into “${target.label ?: "this box"}”", tone = TextTone.Muted, maxLines = 1)
                    BasicTextField(
                        text, { text = it },
                        Modifier.fillMaxWidth().focusRequester(typing).semantics { contentDescription = "Text for ${target.label ?: "the box"} on your computer" },
                        singleLine = true, textStyle = Type.body.copy(color = p.text), cursorBrush = SolidColor(p.ring),
                    )
                }
                SButton("Type", type, variant = ButtonVariant.Primary, enabled = !disabled && text.isNotEmpty())
            }
        }

        if (keys) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.compact), verticalArrangement = Arrangement.spacedBy(Space.compact)) {
                KEYS.forEach { (label, key) ->
                    SButton(label, { store.launch { store.act(Intent.Key(key)) } }, enabled = !disabled)
                }
            }
        }
        Box(
            Modifier.fillMaxWidth().heightIn(min = Layout.control).clickable(role = Role.Button) { keys = !keys },
            contentAlignment = Alignment.Center,
        ) { SText(if (keys) "Hide keyboard keys" else "Keyboard keys (Enter, Esc, arrows)", style = Type.caption, tone = TextTone.Link) }
    }
}

/**
 * Pinch to zoom in on small controls; drag to look around once zoomed. Taps still reach
 * the controls inside, and one finger scrolls the page while not zoomed.
 */
@Composable
private fun ZoomBox(content: @Composable () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val zoom = rememberTransformableState { change, pan, _ ->
        scale = (scale * change).coerceIn(1f, 4f)
        offset = if (scale == 1f) Offset.Zero else offset + pan
    }
    Column(
        Modifier
            .fillMaxSize()
            .clipToBounds()
            .transformable(zoom, canPan = { scale > 1f })
            .verticalScroll(rememberScrollState(), enabled = scale == 1f),
    ) {
        Box(Modifier.graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y)) { content() }
    }
}

/** The Sovra window, scaled to the phone's width. Outlined boxes are real controls. */
@Composable
fun MirrorView(snapshot: InspectResult, screenshot: String?, disabled: Boolean, onPress: (UiElement) -> Unit) {
    val p = LocalPalette.current
    val picture = rememberPicture(screenshot)
    val (windowWidth, windowHeight) = remember(snapshot) { windowSize(snapshot) }
    // The picture's shape is the real window; scrolled-away controls overstate its height.
    val aspect = picture?.let { it.width.toFloat() / it.height } ?: (windowWidth / windowHeight).toFloat()
    val controls = remember(snapshot) {
        snapshot.elements
            .filter { it.visible && it.kind != ElementKind.Content && it.bounds.width > 0 && it.bounds.height > 0 }
            // Larger first so smaller, nested controls stay on top and tappable.
            .sortedByDescending { it.bounds.width * it.bounds.height }
    }
    val text = remember(snapshot) { readableText(snapshot) }
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.card))
            .background(p.surface)
            .border(1.dp, p.border, RoundedCornerShape(Radius.card))
            .semantics { contentDescription = "Picture of the Sovra window on your computer" },
    ) {
        val scale = maxWidth.value / windowWidth.toFloat()
        Box(Modifier.fillMaxWidth().height(maxWidth / aspect)) {
            if (picture != null) {
                Image(picture, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
            } else {
                text.forEach { element ->
                    SText(
                        element.label ?: "",
                        Modifier.offset((element.bounds.x * scale).dp, (element.bounds.y * scale).dp),
                        style = Type.caption.copy(fontSize = androidx.compose.ui.unit.TextUnit(7f, androidx.compose.ui.unit.TextUnitType.Sp)),
                        tone = TextTone.Secondary, maxLines = 1,
                    )
                }
            }
            controls.forEach { element ->
                val accent = when {
                    element.kind == ElementKind.Text -> p.ring
                    element.selected == true -> p.link
                    else -> p.controlBorder
                }
                Box(
                    Modifier
                        .offset((element.bounds.x * scale).dp, (element.bounds.y * scale).dp)
                        .size((element.bounds.width * scale).dp, (element.bounds.height * scale).dp)
                        .border(1.dp, accent, RoundedCornerShape(3.dp))
                        .alpha(if (element.disabled == true) 0.4f else 1f)
                        .clickable(enabled = !disabled && element.disabled != true, role = Role.Button, onClickLabel = element.describe()) { onPress(element) }
                        .semantics { contentDescription = element.describe() },
                )
            }
        }
    }
}

/** What a person needs to know about a control, in plain words (nothing for an ordinary button). */
private fun plainState(element: UiElement): String = listOfNotNull(
    if (element.kind == ElementKind.Text) "Tap to type here" else null,
    if (element.kind == ElementKind.Scroll) "Scroll with Up and Down" else null,
    when {
        element.checked == true -> "On"
        element.checked == false && Regex("CheckBox|Switch|Toggle").containsMatchIn(element.role ?: "") -> "Off"
        else -> null
    },
    if (element.selected == true) "Selected" else null,
    if (element.disabled == true) "Not available right now" else null,
).joinToString(" · ")

/** Every labelled control, the open page first, at full touch size. */
@Composable
fun ControlsList(snapshot: InspectResult, disabled: Boolean, onPress: (UiElement) -> Unit, onScroll: (UiElement, Int) -> Unit) {
    val p = LocalPalette.current
    val controls = remember(snapshot) { pageFirst(snapshot, actionableControls(snapshot)) }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(Space.small)) {
        if (controls.isEmpty()) item { SText("Nothing to press on your computer right now.", tone = TextTone.Secondary) }
        items(controls, key = { it.target }) { element ->
            val shape = RoundedCornerShape(Radius.card)
            Row(
                Modifier.fillMaxWidth().clip(shape).background(p.hover).border(1.dp, p.border, shape).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    Modifier
                        .weight(1f)
                        .heightIn(min = Layout.compactControl)
                        .clickable(enabled = !disabled && element.disabled != true, role = Role.Button, onClickLabel = element.describe()) { onPress(element) },
                    verticalArrangement = Arrangement.Center,
                ) {
                    SText(element.describe(), Modifier.alpha(if (element.disabled == true) 0.5f else 1f), style = Type.label, maxLines = 2)
                    plainState(element).takeIf { it.isNotEmpty() }?.let { SText(it, style = Type.caption, tone = TextTone.Muted) }
                }
                if (element.kind == ElementKind.Scroll) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.compact)) {
                        SButton("Up", { onScroll(element, 5) }, compact = true, enabled = !disabled)
                        SButton("Down", { onScroll(element, -5) }, compact = true, enabled = !disabled)
                    }
                }
            }
        }
    }
}

/**
 * The conversation for desktops that do not label messages: the conversation region of
 * the latest screenshot, or the labelled text inside it.
 */
@Composable
fun ConversationPicture(snapshot: InspectResult, screenshot: String?, area: UiElement, enabled: Boolean, onScroll: (UiElement, Int) -> Unit) {
    val picture = rememberPicture(screenshot)
    val lines = remember(snapshot) { readableText(snapshot, area) }
    val (windowWidth, windowHeight) = remember(snapshot) { windowSize(snapshot) }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(Space.small)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (picture != null) {
                BoxWithConstraints(Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.card))) {
                    val scale = maxWidth.value / area.bounds.width.toFloat()
                    Box(Modifier.fillMaxWidth().height((area.bounds.height * scale).dp)) {
                        Image(
                            picture, "Conversation on your computer",
                            Modifier
                                .offset((-area.bounds.x * scale).dp, (-area.bounds.y * scale).dp)
                                .size((windowWidth * scale).dp, (windowHeight * scale).dp),
                            contentScale = ContentScale.FillBounds,
                        )
                    }
                }
            } else if (lines.isEmpty()) {
                SText("Nothing to read yet. Send a message below, or open a chat on your computer.", tone = TextTone.Secondary)
            } else {
                lines.forEach { SText(it.label ?: "") }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Space.small)) {
            SButton("Earlier", { onScroll(area, 8) }, Modifier.weight(1f), compact = true, enabled = enabled)
            SButton("Later", { onScroll(area, -8) }, Modifier.weight(1f), compact = true, enabled = enabled)
        }
    }
}
