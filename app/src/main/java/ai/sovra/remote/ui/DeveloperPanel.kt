/*
 * Developer details: the transport's own log, refreshed while shown, and a way to copy it.
 * The log holds endpoint IDs and network addresses, never keys or pairing links.
 */
package ai.sovra.remote.ui

import ai.sovra.remote.data.shortLogLine
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private const val LOG_LINES_SHOWN = 120

@Composable
fun DeveloperPanel(phase: String, route: String?, readLog: () -> List<String>) {
    val p = LocalPalette.current
    val context = LocalContext.current
    var log by remember { mutableStateOf(emptyList<String>()) }
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            log = readLog()
            delay(1500)
        }
    }
    Card(subtle = true) {
        SText("Developer details", style = Type.sectionTitle)
        SText("State: $phase" + (route?.let { " · route: $it" } ?: ""), style = Type.caption, tone = TextTone.Secondary)
        SText("Transport log (newest last, ${log.size} lines)", style = Type.caption)
        SelectionContainer {
            SText(
                log.takeLast(LOG_LINES_SHOWN).joinToString("\n", transform = ::shortLogLine).ifEmpty { "Nothing logged yet." },
                Modifier.fillMaxWidth().heightIn(max = 280.dp).background(p.codeBackground, RoundedCornerShape(Radius.button))
                    .verticalScroll(rememberScrollState()).padding(Space.small),
                style = Type.caption.copy(fontFamily = FontFamily.Monospace, fontSize = 10.sp, lineHeight = 13.sp),
                tone = TextTone.Secondary,
            )
        }
        SButton(if (copied) "Copied" else "Copy details", {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val report = "phase: $phase\nroute: ${route ?: "-"}\n\n" + readLog().joinToString("\n")
            clipboard.setPrimaryClip(ClipData.newPlainText("Sovra connection details", report))
            copied = true
        }, Modifier.fillMaxWidth(), compact = true)
    }
}
