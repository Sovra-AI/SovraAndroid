/*
 * Reads Sovra's own screens out of an Inspect snapshot so the phone can show them
 * natively: controls, the conversation, the chat list and the sidebar pages. Pure and
 * label-based; never parses target IDs. Roles are AccessKit `Role` names as the desktop
 * prints them (`Button`, `TextInput`…); messages are `Comment` (you) and `Article` (Sovra).
 */
package ai.sovra.remote.protocol

private val TEXT_ROLES = setOf("TextInput", "MultilineTextInput", "SearchInput", "EmailInput", "NumberInput", "PasswordInput", "ComboBox")
private val ACTION_ROLES = setOf(
    "Button", "DefaultButton", "Link", "CheckBox", "Switch", "RadioButton", "Tab", "MenuItem", "MenuItemCheckBox",
    "MenuItemRadio", "ListItem", "ListBoxOption", "TreeItem", "DisclosureTriangle", "ToggleButton", "PopupButton", "Slider", "ComboBox",
)
private val SCROLL_ROLES = setOf("ScrollView", "List", "ListBox", "Tree", "Grid", "Table", "Log", "Feed", "Document")

enum class ElementKind { Text, Action, Scroll, Content }

val UiElement.kind: ElementKind
    get() = when (role) {
        in TEXT_ROLES -> ElementKind.Text
        in ACTION_ROLES -> ElementKind.Action
        in SCROLL_ROLES -> ElementKind.Scroll
        else -> ElementKind.Content
    }

/** A readable name; elements with no label fall back to their role. */
fun UiElement.describe(): String =
    label?.trim()?.takeIf { it.isNotEmpty() } ?: role?.replace(Regex("([a-z])([A-Z])"), "$1 $2")?.lowercase() ?: "Unnamed control"

private val byReadingOrder = compareBy<UiElement>({ it.bounds.y }, { it.bounds.x })

private fun InspectResult.visible() = elements.filter { it.visible }

private fun overlaps(a: UiElement, b: UiElement): Boolean {
    val p = a.bounds
    val q = b.bounds
    return p.x < q.x + q.width && q.x < p.x + p.width && p.y < q.y + q.height && q.y < p.y + p.height
}

internal fun contains(outer: UiElement, inner: UiElement): Boolean {
    val a = outer.bounds
    val b = inner.bounds
    return b.x >= a.x && b.y >= a.y && b.x + b.width <= a.x + a.width && b.y + b.height <= a.y + a.height
}

/** Visible, labelled controls in reading order. */
fun actionableControls(snapshot: InspectResult): List<UiElement> = snapshot.elements
    .filter { it.visible && it.kind != ElementKind.Content && (!it.label.isNullOrBlank() || it.kind == ElementKind.Text) }
    .sortedWith(byReadingOrder)

/** Visible text that is not itself a control; with `within`, only text inside that element. */
fun readableText(snapshot: InspectResult, within: UiElement? = null): List<UiElement> = snapshot.elements
    .filter { it.visible && it.kind == ElementKind.Content && !it.label.isNullOrBlank() && (within == null || contains(within, it)) }
    .sortedWith(byReadingOrder)

/** Sovra's chat transcript: the visible `Log` region (labelled "Conversation"). */
fun conversationArea(snapshot: InspectResult): UiElement? {
    val logs = snapshot.elements.filter { it.visible && it.role == "Log" && it.bounds.width > 0 && it.bounds.height > 0 }
    return logs.firstOrNull { it.label?.trim()?.lowercase() == "conversation" } ?: logs.firstOrNull()
}

private val COMPOSER_HINT = Regex("(message|ask|prompt|reply)", RegexOption.IGNORE_CASE)

// Sidebar search ("Search chats") and filters are text inputs too; typing a message there is wrong.
private val NOT_COMPOSER = Regex("(search|filter|find)", RegexOption.IGNORE_CASE)

/**
 * The chat box. Sovra's composer is a multiline input labelled "Message Sovra"; prefer
 * multiline inputs, then composer-like labels. Any other box is never the chat box.
 */
fun findComposer(snapshot: InspectResult): UiElement? {
    val inputs = snapshot.elements.filter {
        it.visible && it.disabled != true && it.kind == ElementKind.Text && it.role != "SearchInput" &&
            !(it.label != null && NOT_COMPOSER.containsMatchIn(it.label))
    }
    val multiline = inputs.filter { it.role == "MultilineTextInput" }
    val pool = multiline.ifEmpty { inputs.filter { it.label != null && COMPOSER_HINT.containsMatchIn(it.label) } }
    return pool.maxByOrNull { it.bounds.y }
}

// Whole-label match: Sovra has many "Stop …" buttons ("Stop phone control", "Stop sharing")
// that must never be pressed as "stop work". Composer: "Stop generating" / "إيقاف التوليد".
private val STOP_HINT = Regex("^(stop|stop (generating|response|working)|إيقاف التوليد)$", RegexOption.IGNORE_CASE)

/** The real "stop" control of ongoing work, if one is visible. */
fun findStop(snapshot: InspectResult): UiElement? = snapshot.elements.firstOrNull {
    it.visible && it.disabled != true && it.kind == ElementKind.Action && it.label != null && STOP_HINT.matches(it.label.trim())
}

/** Bounding box of the whole window, used to scale the picture of it. */
fun windowSize(snapshot: InspectResult): Pair<Double, Double> {
    var width = 0.0
    var height = 0.0
    for (element in snapshot.elements) {
        if (!element.visible) continue
        width = maxOf(width, element.bounds.x + element.bounds.width)
        height = maxOf(height, element.bounds.y + element.bounds.height)
    }
    return maxOf(width, 1.0) to maxOf(height, 1.0)
}

data class Message(val id: String, val fromYou: Boolean, val text: String)

data class Transcript(
    val messages: List<Message>,
    /** The conversation continues above what the computer is showing. */
    val earlier: Boolean,
    /** Sovra's own status line while it works ("Sovra is writing"), if shown. */
    val working: String?,
)

fun transcript(snapshot: InspectResult): Transcript {
    val area = conversationArea(snapshot)
    // Overlap, not containment: a long reply can be partly scrolled out of the conversation.
    val shown = snapshot.visible().filter { area == null || overlaps(area, it) }
    val bubbles = shown.filter { (it.role == "Comment" || it.role == "Article") && !it.label.isNullOrBlank() }.sortedWith(byReadingOrder)
    val earlier = area != null && bubbles.isNotEmpty() && bubbles[0].bounds.y < area.bounds.y - 1
    val status = shown.firstOrNull { it.role == "Status" && !it.label.isNullOrBlank() }
    return Transcript(bubbles.map { Message(it.target, it.role == "Comment", it.label!!.trim()) }, earlier, status?.label?.trim())
}

data class ChatEntry(val title: String, val element: UiElement, /** On screen in Sovra's sidebar. */ val shown: Boolean)

private const val OPEN_CONVERSATION = "Open conversation "
const val CHAT_SEARCH_LABEL = "Search chats"
const val NEW_CHAT_LABEL = "Start a fresh conversation"

/**
 * The chats Sovra's sidebar has rendered: the ones on screen plus the few it keeps just
 * outside its scrolled list. Older chats appear as the list scrolls, or by searching.
 */
fun chatList(snapshot: InspectResult): List<ChatEntry> = snapshot.elements
    .filter { it.role == "Button" && it.label?.startsWith(OPEN_CONVERSATION) == true }
    .sortedWith(byReadingOrder)
    .distinctBy { it.target }
    .map { ChatEntry(it.label!!.removePrefix(OPEN_CONVERSATION).trim(), it, it.visible) }
    .filter { it.title.isNotEmpty() }

/** A sidebar destination by its exact label ("Chat", "Skills"…). */
fun findNavigation(snapshot: InspectResult, label: String): UiElement? = snapshot.visible()
    .firstOrNull { it.role == "Button" && it.disabled != true && it.label?.trim()?.lowercase() == label.lowercase() }

/**
 * Sovra's sidebar pages (Chat, Skills, Team, Artifacts, Schedules, Groups, Models, …,
 * Settings): buttons laid out exactly like the "Chat" entry — same column, width and row height.
 */
fun pages(snapshot: InspectResult): List<UiElement> {
    val chat = findNavigation(snapshot, "Chat") ?: return emptyList()
    fun same(a: Double, b: Double) = kotlin.math.abs(a - b) <= 1
    return snapshot.visible().filter {
        it.role == "Button" && !it.label.isNullOrBlank() && same(it.bounds.x, chat.bounds.x) &&
            same(it.bounds.width, chat.bounds.width) && same(it.bounds.height, chat.bounds.height)
    }.sortedWith(byReadingOrder)
}

/** Sovra's window bar (title, "Phone control is on", "Development mode") above every page. */
private const val WINDOW_BAR_HEIGHT = 34.0

/**
 * Controls in the order a person looks for them: the open page first, then Sovra's
 * sidebar and window bar (the page shortcuts are already a row of their own).
 */
fun pageFirst(snapshot: InspectResult, controls: List<UiElement>): List<UiElement> {
    val chat = findNavigation(snapshot, "Chat") ?: return controls
    val sidebarEnd = chat.bounds.x + chat.bounds.width
    val shortcuts = pages(snapshot).map { it.target }.toSet()
    val rest = controls.filter { it.target !in shortcuts }
    val (onPage, elsewhere) = rest.partition { it.bounds.x >= sidebarEnd && it.bounds.y >= WINDOW_BAR_HEIGHT }
    return onPage + elsewhere
}

private val TOGGLE_ROLES = setOf("CheckBox", "Switch", "ToggleButton", "MenuItemCheckBox")
private val CHOICE_ROLES = setOf("Tab", "RadioButton", "MenuItemRadio")

/** One thing on an open Sovra page, as the phone shows it: text, or a control in its native form. */
sealed interface PageItem {
    val key: String

    data class Heading(override val key: String, val text: String) : PageItem
    data class Text(override val key: String, val text: String) : PageItem
    /** A button, link or list entry: one tap presses it on the computer. */
    data class Action(val element: UiElement) : PageItem { override val key get() = element.target }
    /** A checkbox or switch, shown as a phone switch. */
    data class Toggle(val element: UiElement, val on: Boolean) : PageItem { override val key get() = element.target }
    /** A tab or radio choice. */
    data class Choice(val element: UiElement, val chosen: Boolean) : PageItem { override val key get() = element.target }
    /** A text box: the phone types into it. */
    data class Field(val element: UiElement) : PageItem { override val key get() = element.target }
    /** Where one of the page's cards (a teammate, a model…) ends and the next begins. */
    data class Break(override val key: String) : PageItem
}

/** The open Sovra page, rebuilt for the phone: never a picture of the window. */
data class SovraPage(
    /** The sidebar entry that is open ("Team"), or the page's first heading. */
    val title: String?,
    val items: List<PageItem>,
    /** The page's main scrolling region, for "Show more". */
    val scroll: UiElement?,
)

/**
 * Read the open page: everything right of Sovra's sidebar and below its window bar, in
 * reading order. Text inside a control is that control's label, so it is not repeated.
 */
fun openPage(snapshot: InspectResult): SovraPage {
    val chat = findNavigation(snapshot, "Chat")
    val sidebarEnd = chat?.let { it.bounds.x + it.bounds.width } ?: 0.0
    val shortcuts = pages(snapshot)
    val shortcutTargets = shortcuts.map { it.target }.toSet()
    val onPage = snapshot.visible().filter {
        it.bounds.width > 0 && it.bounds.height > 0 && it.bounds.x >= sidebarEnd &&
            it.bounds.y >= WINDOW_BAR_HEIGHT && it.target !in shortcutTargets
    }
    val controls = onPage.filter { it.kind == ElementKind.Action || it.kind == ElementKind.Text }
    // Innermost unlabelled boxes holding a control and something else to read: the page's cards
    // (a teammate, a service…). Cards are read one by one: their text, then buttons, then
    // switches, so a status or switch stays with what it belongs to. A button's own label inside
    // it does not count, so a box around a single button is not a card.
    val labelled = onPage.filter { !it.label.isNullOrBlank() && controls.none { c -> c !== it && contains(c, it) } }
    val holders = onPage.filter { box ->
        box.role == null && box.label.isNullOrBlank() &&
            controls.any { box !== it && contains(box, it) } && labelled.count { box !== it && contains(box, it) } >= 2
    }
    val cards = holders.filter { card -> holders.none { it !== card && contains(card, it) } }
    fun cardOf(element: UiElement) = cards.filter { it !== element && contains(it, element) }.minByOrNull { it.bounds.width * it.bounds.height }
    val plainAction = { e: UiElement -> e.kind == ElementKind.Action && e.role !in TOGGLE_ROLES && e.role !in CHOICE_ROLES }
    val ordered = onPage.sortedWith(
        compareBy<UiElement>(
            { (cardOf(it) ?: it).bounds.y }, { (cardOf(it) ?: it).bounds.x },
            { if (cardOf(it) == null) 0 else if (it.kind == ElementKind.Content) 0 else if (plainAction(it)) 1 else 2 },
            { it.bounds.y }, { it.bounds.x },
        ),
    )
    val items = mutableListOf<PageItem>()
    var lastText: String? = null
    var lastCard: UiElement? = null
    val seenActions = mutableSetOf<String>()
    for (element in ordered) {
        val label = element.label?.trim().orEmpty()
        val item: PageItem? = when {
            element.kind == ElementKind.Text -> PageItem.Field(element)
            element.kind == ElementKind.Scroll -> null
            label.isEmpty() -> null
            element.role in TOGGLE_ROLES -> PageItem.Toggle(element, element.checked == true)
            element.role in CHOICE_ROLES -> PageItem.Choice(element, element.selected == true || element.checked == true)
            // A button already offered on the page or in the same card (a header and an empty
            // state) is one choice; the same button in different cards (Edit per teammate) is not.
            element.kind == ElementKind.Action -> {
                val inCard = cardOf(element)?.target?.let { "$it|$label" }
                if (label in seenActions || (inCard != null && inCard in seenActions)) null
                else PageItem.Action(element).also { seenActions += inCard ?: label }
            }
            controls.any { it !== element && contains(it, element) } -> null
            label == lastText -> null
            element.role == "Heading" -> PageItem.Heading(element.target, label)
            else -> PageItem.Text(element.target, label)
        }
        if (item != null) {
            val card = cardOf(element)
            if (card !== lastCard && items.isNotEmpty() && (card != null || lastCard != null)) items += PageItem.Break("break-${element.target}")
            lastCard = card
            items += item
            lastText = (item as? PageItem.Text)?.text ?: (item as? PageItem.Heading)?.text
        }
    }
    val scroll = onPage.filter { it.kind == ElementKind.Scroll }.maxByOrNull { it.bounds.width * it.bounds.height }
    val title = shortcuts.firstOrNull { it.selected == true }?.label?.trim()
        ?: items.firstNotNullOfOrNull { (it as? PageItem.Heading)?.text }
    // The title is shown above the page; do not repeat it as the first heading.
    val first = items.firstOrNull()
    if (title != null && ((first as? PageItem.Heading)?.text == title || (first as? PageItem.Text)?.text == title)) items.removeAt(0)
    while (items.firstOrNull() is PageItem.Break) items.removeAt(0)
    return SovraPage(title, items, scroll)
}

private val ONE_PLURAL = Regex("""(^|[^\d.,])1 ([A-Za-z]{3,}[^s\W])s\b""")

/** A label as the phone shows it: "Web search, 1 skills" reads "Web search, 1 skill". */
fun tidyLabel(label: String): String = ONE_PLURAL.replace(label) { "${it.groupValues[1]}1 ${it.groupValues[2]}" }
