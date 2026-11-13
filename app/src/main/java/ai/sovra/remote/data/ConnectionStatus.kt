/*
 * Plain words for the connection: what the phone is doing right now, how long it has
 * been trying, and what to check. Pure, so the wording is tested without rendering.
 * Same wording as the Expo app's `src/components/connection-status.ts`.
 */
package ai.sovra.remote.data

import ai.sovra.remote.protocol.Route
import kotlin.math.roundToLong

/** What the phone is doing to reach the computer while it is not connected. */
data class ConnectionProgress(
    /** Starting: binding the secure endpoint. Reaching: dialling and saying Hello. Waiting: the last try failed; another is scheduled. */
    val step: Step,
    /** 1 for the first try since the screen opened or the last success. */
    val attempt: Int,
    /** When this run of tries began (ms since epoch). */
    val since: Long,
    /** When the next try starts, while waiting. */
    val retryAt: Long?,
) {
    enum class Step { Starting, Reaching, Waiting }
}

enum class StepState { Done, Active, Todo }

data class ConnectionStep(val label: String, val detail: String, val state: StepState)

data class ConnectionView(
    val title: String,
    /** "Trying for 12 s · attempt 3", or the retry countdown. */
    val status: String,
    val steps: List<ConnectionStep>,
    /** A hint for slow or repeated failures; null while things look normal. */
    val hint: String?,
    /** Offer "Try now": only while waiting between tries. */
    val canRetryNow: Boolean,
)

/** After this long without an answer, say why it may be slow. */
private const val SLOW_MS = 6000L

/** From this attempt on, list what to check on the computer. */
private const val CHECKLIST_ATTEMPT = 3

private fun seconds(ms: Long): Long = maxOf(0L, (ms / 1000.0).roundToLong())

fun describeConnection(progress: ConnectionProgress, computer: String, now: Long, lastError: String?): ConnectionView {
    val waiting = progress.step == ConnectionProgress.Step.Waiting
    val elapsed = seconds(now - progress.since)
    val attempt = if (progress.attempt > 1) " · attempt ${progress.attempt}" else ""
    val status = if (waiting && progress.retryAt != null) {
        "Trying again in ${maxOf(1L, seconds(progress.retryAt - now))} s$attempt"
    } else {
        "Trying for $elapsed s$attempt"
    }
    val started = progress.step != ConnectionProgress.Step.Starting
    val steps = listOf(
        ConnectionStep(
            if (started) "Secure connection ready" else "Starting a secure connection",
            "End-to-end encrypted with this phone’s own key.",
            if (started) StepState.Done else StepState.Active,
        ),
        ConnectionStep(
            if (waiting) "$computer didn’t answer" else "Reaching $computer",
            when {
                waiting && lastError != null -> lastError
                progress.attempt > 1 && lastError != null -> "Last try: $lastError"
                else -> "Directly on the same Wi-Fi, or over the internet through an encrypted relay."
            },
            if (started) StepState.Active else StepState.Todo,
        ),
    )
    val hint = when {
        progress.attempt >= CHECKLIST_ATTEMPT ->
            "Check that your computer is awake and online, Sovra is open, and “Use Sovra from your phone” is on in Sovra. It switches off whenever Sovra restarts."
        now - progress.since >= SLOW_MS -> "This can take a little longer on mobile data or a new network."
        else -> null
    }
    return ConnectionView(
        title = if (waiting) "Can’t reach $computer yet" else "Connecting to $computer…",
        status = status,
        steps = steps,
        hint = hint,
        canRetryNow = waiting,
    )
}

/** A quick reconnect (back from another app) should not flash the card. */
private const val QUIET_MS = 1500L

/** Show the progress card only once a try is slow, has failed, or is a retry. */
fun showConnectionCard(progress: ConnectionProgress, now: Long): Boolean =
    progress.step == ConnectionProgress.Step.Waiting || progress.attempt > 1 || now - progress.since >= QUIET_MS

/** The status line once connected. */
fun routeLabel(route: Route?): String = when (route) {
    Route.Direct -> "Connected directly"
    Route.Relay -> "Connected through an encrypted relay"
    else -> "Connected"
}

/** While pairing: one line that keeps moving, so a slow network never looks frozen. */
fun pairingStatus(elapsedMs: Long): String {
    val elapsed = seconds(elapsedMs)
    return when {
        elapsedMs >= SLOW_MS -> "Pairing securely with your computer… $elapsed s. Over mobile data or a new network this can take a little longer."
        elapsed > 0 -> "Pairing securely with your computer… $elapsed s"
        else -> "Pairing securely with your computer…"
    }
}
