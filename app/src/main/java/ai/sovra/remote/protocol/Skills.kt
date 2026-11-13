/*
 * Sovra's `/` skills, read from the desktop's own slash menu. Typing `/` in Sovra's chat
 * box lists every enabled skill as a button labelled "/name. description <hint>" — the
 * whole list, including rows scrolled out of view — so the phone can offer the same menu.
 */
package ai.sovra.remote.protocol

import kotlinx.coroutines.delay

data class Skill(
    /** Command name without the slash: "web-search". */
    val name: String,
    /** Readable name: "Web search". */
    val title: String,
    val description: String,
    /** What to write after the command ("<question>"), empty when nothing is needed. */
    val hint: String,
)

private val ENTRY = Regex("^/([a-z0-9][a-z0-9-]*)\\.\\s?([\\s\\S]*)$")
private val HINT = Regex("\\s*((?:[<\\[][^<>\\[\\]]*[>\\]]\\s*)+)$")
private val SPECIALIST = Regex("^Consult this specialist when you need help with:\\s*", RegexOption.IGNORE_CASE)

fun titleOf(name: String): String = name.split('-').filter { it.isNotEmpty() }.joinToString(" ").replaceFirstChar { it.uppercase() }

/** Every skill in the desktop's open `/` menu, in its order. */
fun readSkillMenu(snapshot: InspectResult): List<Skill> {
    val seen = mutableSetOf<String>()
    return snapshot.elements.mapNotNull { element ->
        if (element.role != "Button" || element.label == null) return@mapNotNull null
        val entry = ENTRY.matchEntire(element.label.trim()) ?: return@mapNotNull null
        val name = entry.groupValues[1]
        if (!seen.add(name)) return@mapNotNull null
        val rest = entry.groupValues[2].trim()
        val hint = HINT.find(rest)
        Skill(
            name = name,
            title = titleOf(name),
            // Specialist skills describe themselves to the AI; keep the part people need.
            description = (if (hint != null) rest.substring(0, hint.range.first) else rest).replace(SPECIALIST, "").trim(),
            hint = hint?.groupValues?.get(1)?.trim() ?: "",
        )
    }
}

/** A skill that cannot run without text after it (`<…>`; `[…]` is optional). */
fun Skill.needsText(): Boolean = hint.contains('<')

/** The hint in plain words: "<what to present>" → "what to present". */
fun Skill.hintText(): String = hint.replace(Regex("[<>\\[\\]]"), "").replace(Regex("\\s+"), " ").trim()

/** A prompt for the chat box: "<question>" → "Your question…", "<what to present>" → "What to present…". */
fun Skill.hintPrompt(): String {
    val hint = hintText()
    if (hint.isEmpty()) return "Add details, or just tap Send"
    val question = Regex("^(what|which|who|how|where|when)\\b", RegexOption.IGNORE_CASE).containsMatchIn(hint)
    val text = if (question) hint else "your $hint"
    return text.replaceFirstChar { it.uppercase() } + "…"
}

/** The same ranking as the desktop menu: exact, prefix, word, contains, description. */
fun matchSkills(skills: List<Skill>, query: String): List<Skill> {
    val wanted = query.trim().removePrefix("/").lowercase()
    if (wanted.isEmpty()) return skills
    fun score(skill: Skill): Int? {
        val title = skill.title.lowercase()
        return when {
            skill.name == wanted -> 0
            skill.name.startsWith(wanted) -> 1
            title.split(Regex("\\s+")).any { it.startsWith(wanted) } -> 3
            skill.name.contains(wanted) || title.contains(wanted) -> 4
            wanted.length >= 3 && skill.description.lowercase().contains(wanted) -> 5
            else -> null
        }
    }
    return skills.withIndex().mapNotNull { (index, skill) -> score(skill)?.let { Triple(skill, index, it) } }
        .sortedWith(compareBy({ it.third }, { it.second }))
        .map { it.first }
}

/** "/web" while still typing the command (no space yet) → "web"; otherwise null. */
fun slashQuery(draft: String): String? = Regex("^\\s*/([A-Za-z0-9-]*)$").matchEntire(draft)?.groupValues?.get(1)

/** Sovra's message about the last `/` command ("Usage: /table <what to present>"). */
fun skillNotice(snapshot: InspectResult): String? =
    snapshot.elements.firstOrNull { it.visible && it.role == "Alert" && !it.label.isNullOrBlank() }?.label?.trim()

/** Turn the desktop's terse skill notices into plain words for the phone. */
fun explainNotice(notice: String): String {
    val usage = Regex("^Usage: /([a-z0-9-]+)\\s*(.*)$").matchEntire(notice) ?: return notice
    val what = usage.groupValues[2].replace(Regex("[<>\\[\\]]"), "").trim()
    val title = titleOf(usage.groupValues[1])
    return if (what.isNotEmpty()) "$title needs a little more: tell it $what." else "$title needs a little more text."
}

private suspend fun clearComposer(client: RemoteClient): Outcome<Unit> {
    // "secondary" is Cmd on macOS and Ctrl elsewhere (GPUI keystroke syntax).
    for (key in listOf("secondary-a", "backspace")) {
        val result = client.act(Intent.Key(key))
        if (result is Outcome.Failed) return result
    }
    return ok(Unit)
}

private const val MENU_LOOKS = 4
private const val MENU_WAIT_MS = 250L

/**
 * Read the skills by opening Sovra's `/` menu on the computer, then leave the chat box
 * empty again. Opens Chat first when another page is showing.
 */
suspend fun loadSkills(client: RemoteClient): Outcome<List<Skill>> {
    val page = openChatPage(client)
    if (page is Outcome.Failed) return page
    val composer = findComposer((page as Outcome.Ok).value)
        ?: return fail(ErrorCode.InvalidRequest, "Open a chat on your computer to see the skills.")
    (client.act(Intent.Click(composer.ref())) as? Outcome.Failed)?.let { return it }
    (clearComposer(client) as? Outcome.Failed)?.let { return it }
    (client.act(Intent.Type("/")) as? Outcome.Failed)?.let { return it }
    // The menu appears on Sovra's next frame; look again briefly rather than give up.
    var skills = emptyList<Skill>()
    for (attempt in 0 until MENU_LOOKS) {
        if (skills.isNotEmpty()) break
        if (attempt > 0) delay(MENU_WAIT_MS)
        when (val menu = client.inspect()) {
            is Outcome.Failed -> {
                clearComposer(client)
                return menu
            }
            is Outcome.Ok -> skills = readSkillMenu(menu.value)
        }
    }
    (clearComposer(client) as? Outcome.Failed)?.let { return it }
    return if (skills.isEmpty()) fail(ErrorCode.InvalidRequest, "Sovra did not list its skills. Try again.") else ok(skills)
}
