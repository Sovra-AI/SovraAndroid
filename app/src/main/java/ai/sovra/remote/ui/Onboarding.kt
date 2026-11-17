/*
 * The first-run tour: four short pages on what the app does, how pairing works, how to
 * ask Sovra for things and why it is private. Swipe or tap Next; Skip ends it any time.
 * Shown once, and again from Settings.
 */
package ai.sovra.remote.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private data class TourPage(val icon: ImageVector, val title: String, val body: String)

private val TOUR = listOf(
    TourPage(
        SovraIcons.Phone, "Sovra, in your pocket",
        "Ask Sovra for anything from your phone. Your computer does the work, and the answer shows up here.",
    ),
    TourPage(
        SovraIcons.Scan, "Pair once, in seconds",
        "On your computer, open Sovra, go to Skills and choose “Use Sovra from your phone”. Scan the code it shows. After that the app connects by itself.",
    ),
    TourPage(
        SovraIcons.Skills, "Just ask",
        "Type a message and send it. Tap the sparkle to use a skill, like searching the web or making a table. Your chats and the rest of Sovra are in the tabs below.",
    ),
    TourPage(
        SovraIcons.Lock, "Private by design",
        "Everything is encrypted between your phone and your computer. It works on Wi-Fi or mobile data while your computer is awake and Sovra is open.",
    ),
)

@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val p = LocalPalette.current
    val pager = rememberPagerState { TOUR.size }
    val scope = rememberCoroutineScope()
    val last = pager.currentPage == TOUR.lastIndex

    Column(Modifier.fillMaxSize().background(p.canvas).safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = Space.small, vertical = Space.small), horizontalArrangement = Arrangement.End) {
            if (!last) SButton("Skip", onDone, variant = ButtonVariant.Ghost, compact = true, hint = "Skip the tour")
            else Box(Modifier.height(Layout.compactControl))
        }
        HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth()) { index ->
            val page = TOUR[index]
            Readable(Modifier.fillMaxSize()) {
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Space.large),
                    verticalArrangement = Arrangement.spacedBy(Space.large, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier.size(112.dp).clip(RoundedCornerShape(32.dp)).background(p.primaryGradient),
                        contentAlignment = Alignment.Center,
                    ) { SIcon(page.icon, null, tint = p.primaryForeground, size = 56.dp) }
                    SText(page.title, style = Type.pageTitle.copy(textAlign = TextAlign.Center))
                    SText(page.body, style = Type.body.copy(textAlign = TextAlign.Center), tone = TextTone.Secondary)
                }
            }
        }
        Readable {
            Column(Modifier.padding(Space.large), verticalArrangement = Arrangement.spacedBy(Space.large), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Space.small),
                    modifier = Modifier.semantics { contentDescription = "Page ${pager.currentPage + 1} of ${TOUR.size}" },
                ) {
                    TOUR.indices.forEach { index ->
                        val current = index == pager.currentPage
                        Box(Modifier.height(8.dp).width(if (current) 24.dp else 8.dp).clip(CircleShape).background(if (current) p.ring else p.hover))
                    }
                }
                SButton(
                    if (last) "Get started" else "Next",
                    { if (last) onDone() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
                    Modifier.fillMaxWidth(),
                    variant = ButtonVariant.Primary,
                )
            }
        }
    }
}
