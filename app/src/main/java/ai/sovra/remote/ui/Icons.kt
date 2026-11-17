/*
 * The app's few icons, drawn on a 24 dp grid as 2 dp rounded strokes so they match at any
 * size and tint from the palette. No icon library: the set is small and fixed.
 */
package ai.sovra.remote.ui

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private fun icon(name: String, stroke: String, fill: String? = null): ImageVector {
    val builder = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
    if (fill != null) builder.addPath(addPathNodes(fill), fill = SolidColor(Color.Black))
    builder.addPath(
        addPathNodes(stroke),
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    )
    return builder.build()
}

object SovraIcons {
    val Chat = icon("Chat", "M7 5h10a3 3 0 0 1 3 3v6a3 3 0 0 1-3 3h-5l-4.5 3.5V17H7a3 3 0 0 1-3-3V8a3 3 0 0 1 3-3z")
    val Chats = icon("Chats", "M9 6h11M9 12h11M9 18h11M4.5 6h.01M4.5 12h.01M4.5 18h.01")
    val Skills = icon("Skills", "M11 4l1.8 6.2L19 12l-6.2 1.8L11 20l-1.8-6.2L3 12l6.2-1.8zM19 3v4M17 5h4")
    val More = icon("More", "M6 5h3a1 1 0 0 1 1 1v3a1 1 0 0 1-1 1H6a1 1 0 0 1-1-1V6a1 1 0 0 1 1-1zM15 5h3a1 1 0 0 1 1 1v3a1 1 0 0 1-1 1h-3a1 1 0 0 1-1-1V6a1 1 0 0 1 1-1zM6 14h3a1 1 0 0 1 1 1v3a1 1 0 0 1-1 1H6a1 1 0 0 1-1-1v-3a1 1 0 0 1 1-1zM15 14h3a1 1 0 0 1 1 1v3a1 1 0 0 1-1 1h-3a1 1 0 0 1-1-1v-3a1 1 0 0 1 1-1z")
    val Settings = icon("Settings", "M4 7h9M19 7h1M4 17h3M13 17h7M16 5a2 2 0 1 1 0 4a2 2 0 1 1 0-4zM10 15a2 2 0 1 1 0 4a2 2 0 1 1 0-4z")
    val Send = icon("Send", "M12 19V5M6 11l6-6 6 6")
    val Stop = icon("Stop", "M8 7h8a1 1 0 0 1 1 1v8a1 1 0 0 1-1 1H8a1 1 0 0 1-1-1V8a1 1 0 0 1 1-1z", fill = "M8 7h8a1 1 0 0 1 1 1v8a1 1 0 0 1-1 1H8a1 1 0 0 1-1-1V8a1 1 0 0 1 1-1z")
    val Add = icon("Add", "M12 5v14M5 12h14")
    val Compose = icon("Compose", "M12 5H7a2 2 0 0 0-2 2v10a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2v-5M15.5 4.5a2.1 2.1 0 0 1 3 3L12 14l-4 1 1-4z")
    val Search = icon("Search", "M11 4a7 7 0 1 1 0 14a7 7 0 1 1 0-14zM20 20l-4-4")
    val Back = icon("Back", "M15 5l-7 7 7 7")
    val Close = icon("Close", "M6 6l12 12M18 6L6 18")
    val Chevron = icon("Chevron", "M9 5l7 7-7 7")
    val Computer = icon("Computer", "M5 4h14a1 1 0 0 1 1 1v10a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V5a1 1 0 0 1 1-1zM9 20h6M12 16v4")
    val Phone = icon("Phone", "M8 3h8a1 1 0 0 1 1 1v16a1 1 0 0 1-1 1H8a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1zM11 18h2")
    val Scan = icon("Scan", "M4 9V5a1 1 0 0 1 1-1h4M15 4h4a1 1 0 0 1 1 1v4M20 15v4a1 1 0 0 1-1 1h-4M9 20H5a1 1 0 0 1-1-1v-4M8 12h8")
    val Lock = icon("Lock", "M7 11h10a1 1 0 0 1 1 1v7a1 1 0 0 1-1 1H7a1 1 0 0 1-1-1v-7a1 1 0 0 1 1-1zM8 11V8a4 4 0 0 1 8 0v3")
    val Refresh = icon("Refresh", "M20 12a8 8 0 1 1-2.3-5.6M20 4v4h-4")
    val Keyboard = icon("Keyboard", "M4 6h16a1 1 0 0 1 1 1v10a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V7a1 1 0 0 1 1-1zM7 10h.01M11 10h.01M15 10h.01M8 14h8")
    val Check = icon("Check", "M5 12l5 5 9-10")
    val Team = icon("Team", "M9 11a3 3 0 1 0 0-6a3 3 0 1 0 0 6zM3 20v-1a5 5 0 0 1 5-5h2a5 5 0 0 1 5 5v1M16 5.5a3 3 0 0 1 0 5.5M18 14a5 5 0 0 1 3 4.6V20")
    val Calendar = icon("Calendar", "M5 5h14a1 1 0 0 1 1 1v13a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V6a1 1 0 0 1 1-1zM4 10h16M8 3v4M16 3v4")
    val Chip = icon("Chip", "M8 7h8a1 1 0 0 1 1 1v8a1 1 0 0 1-1 1H8a1 1 0 0 1-1-1V8a1 1 0 0 1 1-1zM10 3v4M14 3v4M10 17v4M14 17v4M3 10h4M3 14h4M17 10h4M17 14h4")
    val Book = icon("Book", "M4 5h6a2 2 0 0 1 2 2v13a2 2 0 0 0-2-2H4zM20 5h-6a2 2 0 0 0-2 2v13a2 2 0 0 1 2-2h6z")
    val Person = icon("Person", "M12 11a4 4 0 1 0 0-8a4 4 0 1 0 0 8zM4 21v-1a6 6 0 0 1 6-6h4a6 6 0 0 1 6 6v1")
    val Stack = icon("Stack", "M12 4c4.4 0 8 1.3 8 3s-3.6 3-8 3-8-1.3-8-3 3.6-3 8-3zM4 7v10c0 1.7 3.6 3 8 3s8-1.3 8-3V7M4 12c0 1.7 3.6 3 8 3s8-1.3 8-3")
    val Link = icon("Link", "M10 14a4 4 0 0 0 5.7 0l3-3a4 4 0 0 0-5.7-5.7l-1 1M14 10a4 4 0 0 0-5.7 0l-3 3a4 4 0 0 0 5.7 5.7l1-1")
    val Document = icon("Document", "M7 3h7l5 5v12a1 1 0 0 1-1 1H7a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1zM14 3v5h5M9 13h6M9 17h6")
}

@Composable
fun SIcon(icon: ImageVector, contentDescription: String?, modifier: Modifier = Modifier, size: Dp = 22.dp, tint: Color = LocalPalette.current.text) {
    androidx.compose.material3.Icon(icon, contentDescription, modifier.size(size), tint = tint)
}
