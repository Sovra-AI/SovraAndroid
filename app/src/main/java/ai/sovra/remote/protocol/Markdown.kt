/*
 * A small, safe Markdown subset for Sovra's replies on the phone: headings, paragraphs,
 * bullet/numbered lists, fenced code and tables; bold, italic and inline code. No links
 * or HTML are interpreted — text stays text.
 */
package ai.sovra.remote.protocol

sealed interface Block {
    data class Heading(val level: Int, val text: String) : Block
    data class Paragraph(val text: String) : Block
    data class Bullets(val ordered: Boolean, val items: List<String>) : Block
    data class Code(val text: String) : Block
    data class Table(val header: List<String>, val rows: List<List<String>>) : Block
}

data class Span(val text: String, val bold: Boolean = false, val italic: Boolean = false, val code: Boolean = false)

private val HEADING = Regex("^(#{1,6})\\s+(.*)$")
private val BULLET = Regex("^\\s*[-*•]\\s+(.*)$")
private val NUMBERED = Regex("^\\s*\\d+[.)]\\s+(.*)$")
private val TABLE_ROW = Regex("^\\s*\\|.*\\|\\s*$")
private val TABLE_RULE = Regex("^\\s*\\|?\\s*:?-{3,}:?\\s*(\\|\\s*:?-{3,}:?\\s*)*\\|?\\s*$")

private fun cells(line: String) = line.trim().removePrefix("|").removeSuffix("|").split('|').map { it.trim() }

fun parseBlocks(markdown: String): List<Block> {
    val lines = markdown.replace("\r\n", "\n").replace('\r', '\n').split('\n')
    val blocks = mutableListOf<Block>()
    val paragraph = mutableListOf<String>()
    fun flush() {
        if (paragraph.isNotEmpty()) blocks += Block.Paragraph(paragraph.joinToString("\n"))
        paragraph.clear()
    }
    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        if (line.trim().startsWith("```")) {
            flush()
            val code = mutableListOf<String>()
            i++
            while (i < lines.size && !lines[i].trim().startsWith("```")) code += lines[i++]
            blocks += Block.Code(code.joinToString("\n"))
            i++
            continue
        }
        if (TABLE_ROW.matches(line) && i + 1 < lines.size && TABLE_RULE.matches(lines[i + 1])) {
            flush()
            val header = cells(line)
            val rows = mutableListOf<List<String>>()
            i += 2
            while (i < lines.size && TABLE_ROW.matches(lines[i])) rows += cells(lines[i++])
            blocks += Block.Table(header, rows)
            continue
        }
        val heading = HEADING.matchEntire(line)
        if (heading != null) {
            flush()
            blocks += Block.Heading(heading.groupValues[1].length, heading.groupValues[2].trim())
            i++
            continue
        }
        val bullet = BULLET.matchEntire(line)
        val numbered = if (bullet == null) NUMBERED.matchEntire(line) else null
        if (bullet != null || numbered != null) {
            flush()
            val ordered = numbered != null
            val item = (bullet ?: numbered)!!.groupValues[1].trim()
            val last = blocks.lastOrNull()
            if (last is Block.Bullets && last.ordered == ordered) blocks[blocks.lastIndex] = last.copy(items = last.items + item)
            else blocks += Block.Bullets(ordered, listOf(item))
            i++
            continue
        }
        if (line.isBlank()) flush() else paragraph += line
        i++
    }
    flush()
    return blocks
}

private val INLINE = Regex("(\\*\\*[^*\\n]+\\*\\*|`[^`\\n]+`|(?<![\\w*])\\*[^*\\s][^*\\n]*\\*(?![\\w*]))")

fun parseInline(text: String): List<Span> {
    val spans = mutableListOf<Span>()
    var last = 0
    for (match in INLINE.findAll(text)) {
        if (match.range.first > last) spans += Span(text.substring(last, match.range.first))
        val token = match.value
        spans += when {
            token.startsWith("**") -> Span(token.substring(2, token.length - 2), bold = true)
            token.startsWith("`") -> Span(token.substring(1, token.length - 1), code = true)
            else -> Span(token.substring(1, token.length - 1), italic = true)
        }
        last = match.range.last + 1
    }
    if (last < text.length) spans += Span(text.substring(last))
    return spans.ifEmpty { listOf(Span(text)) }
}
