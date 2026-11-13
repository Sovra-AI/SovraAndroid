/*
 * Sovra primitives for Android: text roles, buttons, surfaces, notices, rows and the
 * reply renderer. Every colour comes from `LocalPalette`.
 */
package ai.sovra.remote.ui

import ai.sovra.remote.data.Tone
import ai.sovra.remote.protocol.Block
import ai.sovra.remote.protocol.parseBlocks
import ai.sovra.remote.protocol.parseInline
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

enum class TextTone { Primary, Secondary, Muted, Link, Danger, Success, Warning }

@Composable
fun toneColor(tone: TextTone): Color {
    val p = LocalPalette.current
    return when (tone) {
        TextTone.Primary -> p.text
        TextTone.Secondary -> p.secondaryText
        TextTone.Muted -> p.mutedText
        TextTone.Link -> p.link
        TextTone.Danger -> p.danger
        TextTone.Success -> p.success
        TextTone.Warning -> p.warning
    }
}

@Composable
fun SText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = Type.body,
    tone: TextTone = TextTone.Primary,
    maxLines: Int = Int.MAX_VALUE,
) {
    // A colour set on the style (buttons) wins over the tone.
    val color = if (style.color != Color.Unspecified) style.color else toneColor(tone)
    androidx.compose.material3.Text(text, modifier, color = color, style = style, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
}

enum class ButtonVariant { Primary, Secondary, Ghost, Danger }

@Composable
fun SButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.Secondary,
    compact: Boolean = false,
    enabled: Boolean = true,
    hint: String? = null,
) {
    val p = LocalPalette.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shape = RoundedCornerShape(Radius.button)
    val foreground = when (variant) {
        ButtonVariant.Primary -> p.primaryForeground
        ButtonVariant.Danger -> p.danger
        ButtonVariant.Ghost -> p.link
        ButtonVariant.Secondary -> p.text
    }
    var base = modifier
        .heightIn(min = if (compact) Layout.compactControl else Layout.control)
        .clip(shape)
    base = when (variant) {
        ButtonVariant.Primary -> base.background(p.primaryGradient).then(if (pressed) Modifier.background(Color.Black.copy(alpha = 0.15f)) else Modifier)
        ButtonVariant.Secondary -> base.background(if (pressed) p.hover else p.surface).border(1.dp, p.border, shape)
        ButtonVariant.Danger -> base.background(if (pressed) p.hover else p.surface).border(1.dp, p.danger, shape)
        ButtonVariant.Ghost -> base.background(if (pressed) p.selection else Color.Transparent)
    }
    Box(
        Modifier
            .alpha(if (enabled) 1f else 0.5f)
            .then(base)
            .clickable(interaction, null, enabled = enabled, role = Role.Button, onClickLabel = hint, onClick = onClick)
            .padding(horizontal = if (compact) 12.dp else Space.medium),
        contentAlignment = Alignment.Center,
    ) {
        SText(label, style = Type.label.copy(color = foreground), maxLines = 1)
    }
}

@Composable
fun Card(modifier: Modifier = Modifier, subtle: Boolean = false, content: @Composable () -> Unit) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(Radius.card)
    Column(
        modifier
            .clip(shape)
            .background(if (subtle) p.hover else p.surface)
            .border(1.dp, p.border, shape)
            .padding(Space.medium),
        verticalArrangement = Arrangement.spacedBy(Space.small),
    ) { content() }
}

@Composable
fun NoticeBanner(tone: Tone, text: String, onDismiss: (() -> Unit)? = null) {
    val p = LocalPalette.current
    val accent = when (tone) {
        Tone.Info -> p.link
        Tone.Success -> p.success
        Tone.Warning -> p.warning
        Tone.Danger -> p.danger
    }
    val shape = RoundedCornerShape(Radius.button)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(p.surface)
            .border(1.dp, p.border, shape)
            .then(if (onDismiss != null) Modifier.clickable(onClickLabel = "Dismiss", onClick = onDismiss) else Modifier),
    ) {
        Box(Modifier.width(3.dp).heightIn(min = 44.dp).background(accent))
        SText(text, Modifier.padding(12.dp).weight(1f), style = Type.caption)
    }
}

@Composable
fun StatusDot(color: Color) = Box(Modifier.size(8.dp).clip(CircleShape).background(color))

@Composable
fun SearchBox(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier, onSearch: (() -> Unit)? = null) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(Radius.button)
    Row(
        modifier
            .heightIn(min = Layout.control)
            .clip(shape)
            .background(p.surface)
            .border(1.dp, p.controlBorder, shape)
            .padding(start = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) SText(placeholder, tone = TextTone.Muted, maxLines = 1)
            BasicTextField(
                value, onChange,
                Modifier.fillMaxWidth().semantics { contentDescription = placeholder },
                singleLine = true,
                textStyle = Type.body.copy(color = p.text),
                cursorBrush = SolidColor(p.ring),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch?.invoke() }),
            )
        }
        if (value.isNotEmpty()) {
            Box(
                Modifier.size(44.dp).clickable(onClickLabel = "Clear search", role = Role.Button) { onChange("") },
                contentAlignment = Alignment.Center,
            ) { SText("✕", tone = TextTone.Secondary) }
        } else {
            Box(Modifier.width(12.dp))
        }
    }
}

/** A tappable list row with a title, optional subtitle and a chevron. */
@Composable
fun ListRow(title: String, subtitle: String? = null, enabled: Boolean = true, hint: String? = null, onClick: () -> Unit) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(Radius.card)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Layout.control + 8.dp)
            .clip(shape)
            .background(p.surface)
            .border(1.dp, p.border, shape)
            .alpha(if (enabled) 1f else 0.6f)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = hint, onClick = onClick)
            .padding(horizontal = Space.medium, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.small),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            SText(title, style = Type.label, maxLines = 2)
            if (!subtitle.isNullOrBlank()) SText(subtitle, style = Type.caption, tone = TextTone.Secondary, maxLines = 2)
        }
        SText("›", tone = TextTone.Muted)
    }
}

/** A row of segmented choices (tabs, appearance). */
@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.button + 2.dp)).background(p.hover).padding(3.dp),
    ) {
        options.forEachIndexed { index, label ->
            val chosen = index == selected
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = Layout.control - 4.dp)
                    .clip(RoundedCornerShape(Radius.button))
                    .background(if (chosen) p.surface else Color.Transparent)
                    .clickable(role = Role.Tab, onClickLabel = label) { onSelect(index) },
                contentAlignment = Alignment.Center,
            ) { SText(label, style = Type.label, tone = if (chosen) TextTone.Primary else TextTone.Secondary, maxLines = 1) }
        }
    }
}

@Composable
private fun inline(text: String, bold: Boolean = false) = buildAnnotatedString {
    val p = LocalPalette.current
    for (span in parseInline(text)) {
        withStyle(
            SpanStyle(
                fontWeight = if (span.bold || bold) FontWeight.Bold else null,
                fontStyle = if (span.italic) FontStyle.Italic else null,
                fontFamily = if (span.code) FontFamily.Monospace else null,
                background = if (span.code) p.codeBackground else Color.Unspecified,
            ),
        ) { append(span.text) }
    }
}

/** Sovra's replies (a safe Markdown subset) in the app's type roles. */
@Composable
fun Markdown(text: String) {
    val p = LocalPalette.current
    val blocks = remember(text) { parseBlocks(text) }
    SelectionContainer {
        Column(verticalArrangement = Arrangement.spacedBy(Space.small)) {
            for (block in blocks) when (block) {
                is Block.Heading -> androidx.compose.material3.Text(
                    inline(block.text), Modifier.semantics { heading() }, color = p.text,
                    style = if (block.level <= 2) Type.sectionTitle else Type.label,
                )
                is Block.Paragraph -> androidx.compose.material3.Text(inline(block.text), color = p.text, style = Type.body)
                is Block.Bullets -> Column(verticalArrangement = Arrangement.spacedBy(Space.compact)) {
                    block.items.forEachIndexed { index, item ->
                        Row(horizontalArrangement = Arrangement.spacedBy(Space.small)) {
                            SText(if (block.ordered) "${index + 1}." else "•", tone = TextTone.Secondary)
                            androidx.compose.material3.Text(inline(item), Modifier.weight(1f), color = p.text, style = Type.body)
                        }
                    }
                }
                is Block.Code -> Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.card)).background(p.codeBackground).horizontalScroll(rememberScrollState()),
                ) {
                    androidx.compose.material3.Text(block.text, Modifier.padding(Space.small), color = p.text, style = Type.body.copy(fontFamily = FontFamily.Monospace))
                }
                is Block.Table -> Box(Modifier.horizontalScroll(rememberScrollState())) {
                    Column(Modifier.border(1.dp, p.border, RoundedCornerShape(Radius.card)).clip(RoundedCornerShape(Radius.card))) {
                        (listOf(block.header) + block.rows).forEachIndexed { rowIndex, row ->
                            Row(Modifier.background(if (rowIndex == 0) p.hover else Color.Transparent)) {
                                row.forEach { cell ->
                                    androidx.compose.material3.Text(
                                        inline(cell, bold = rowIndex == 0), Modifier.width(140.dp).padding(Space.small), color = p.text, style = Type.caption,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RowScope.Spacer(weight: Float = 1f) = Box(Modifier.weight(weight))

@Composable
fun VSpace(height: androidx.compose.ui.unit.Dp) = Box(Modifier.height(height))

@Composable
fun Logo(size: androidx.compose.ui.unit.Dp) {
    androidx.compose.foundation.Image(
        androidx.compose.ui.res.painterResource(ai.sovra.remote.R.mipmap.ic_launcher_foreground),
        "Sovra",
        Modifier.size(size).clip(CircleShape).background(LocalPalette.current.dark.let { androidx.compose.ui.graphics.Color(0xFF111216) }),
    )
}
