/*
 * Live progress while the phone connects: each step, how long it has been trying, the
 * countdown to the next try, and what to check. Ticks once a second while shown.
 */
package ai.sovra.remote.ui

import ai.sovra.remote.data.ConnectionProgress
import ai.sovra.remote.data.StepState
import ai.sovra.remote.data.describeConnection
import ai.sovra.remote.data.pairingStatus
import ai.sovra.remote.data.showConnectionCard
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** The current time, updated once a second while composed. */
@Composable
private fun ticker(): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            now = System.currentTimeMillis()
        }
    }
    return now
}

@Composable
private fun StepMark(state: StepState) {
    val p = LocalPalette.current
    when (state) {
        StepState.Active -> CircularProgressIndicator(Modifier.size(20.dp), color = p.ring, strokeWidth = 2.dp)
        StepState.Done -> Box(Modifier.size(20.dp).clip(CircleShape).background(p.success), contentAlignment = Alignment.Center) {
            SText("✓", style = Type.caption.copy(color = p.primaryForeground, fontWeight = FontWeight.Bold))
        }
        StepState.Todo -> Box(Modifier.size(20.dp).clip(CircleShape).border(2.dp, p.controlBorder, CircleShape))
    }
}

@Composable
fun ConnectionCard(progress: ConnectionProgress, computer: String, detail: String?, onRetry: () -> Unit) {
    val now = ticker()
    if (!showConnectionCard(progress, now)) return
    val view = describeConnection(progress, computer, now, detail)
    Card {
        Column(Modifier.semantics(mergeDescendants = true) { heading(); liveRegion = LiveRegionMode.Polite }) {
            SText(view.title, style = Type.sectionTitle)
            SText(view.status, style = Type.caption, tone = TextTone.Secondary)
        }
        view.steps.forEach { step ->
            val state = when (step.state) {
                StepState.Done -> "Done"
                StepState.Active -> "In progress"
                StepState.Todo -> "Next"
            }
            Row(
                Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = "${step.label}. $state. ${step.detail}" },
                horizontalArrangement = Arrangement.spacedBy(Space.small + 4.dp),
            ) {
                StepMark(step.state)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.hair)) {
                    SText(step.label, tone = if (step.state == StepState.Todo) TextTone.Muted else TextTone.Primary)
                    SText(step.detail, style = Type.caption, tone = TextTone.Muted)
                }
            }
        }
        view.hint?.let { SText(it, tone = TextTone.Secondary) }
        if (view.canRetryNow) SButton("Try now", onRetry, Modifier.fillMaxWidth(), variant = ButtonVariant.Primary)
    }
}

/** Elapsed pairing time, ticking once a second while `active`. */
@Composable
fun pairingStatusText(active: Boolean): String? {
    if (!active) return null
    val start = remember { System.currentTimeMillis() }
    return pairingStatus(ticker() - start)
}
