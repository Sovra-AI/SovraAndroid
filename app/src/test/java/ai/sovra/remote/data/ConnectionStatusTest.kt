package ai.sovra.remote.data

import ai.sovra.remote.protocol.Route
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun at(step: ConnectionProgress.Step = ConnectionProgress.Step.Reaching, attempt: Int = 1, retryAt: Long? = null) =
    ConnectionProgress(step, attempt, since = 0, retryAt = retryAt)

/** Same cases as the Expo app's `connection-status.test.ts`. */
class ConnectionStatusTest {
    @Test
    fun showsEachStepAsItHappens() {
        val starting = describeConnection(at(ConnectionProgress.Step.Starting), "Mac", 0, null)
        assertEquals("Connecting to Mac…", starting.title)
        assertEquals(listOf(StepState.Active, StepState.Todo), starting.steps.map { it.state })

        val reaching = describeConnection(at(), "Mac", 3000, null)
        assertEquals(listOf(StepState.Done, StepState.Active), reaching.steps.map { it.state })
        assertEquals("Trying for 3 s", reaching.status)
        assertNull(reaching.hint)
        assertFalse(reaching.canRetryNow)
    }

    @Test
    fun explainsSlowFirstTries() {
        assertTrue(describeConnection(at(), "Mac", 7000, null).hint!!.contains("mobile data"))
    }

    @Test
    fun countsDownToTheNextTryAndOffersToTryNow() {
        val view = describeConnection(at(ConnectionProgress.Step.Waiting, 2, 14_500), "Mac", 10_000, "Your computer took too long to answer.")
        assertEquals("Can’t reach Mac yet", view.title)
        assertEquals("Trying again in 5 s · attempt 2", view.status)
        assertEquals("Mac didn’t answer", view.steps[1].label)
        assertEquals("Your computer took too long to answer.", view.steps[1].detail)
        assertTrue(view.canRetryNow)
    }

    @Test
    fun keepsTheLastFailureInViewWhileTryingAgain() {
        assertEquals("Last try: offline", describeConnection(at(attempt = 2), "Mac", 1000, "offline").steps[1].detail)
    }

    @Test
    fun listsWhatToCheckAfterRepeatedFailures() {
        assertTrue(describeConnection(at(attempt = 3), "Mac", 1000, null).hint!!.contains("Use Sovra from your phone"))
    }

    @Test
    fun staysQuietForAQuickReconnectThenExplains() {
        assertFalse(showConnectionCard(at(), 1000))
        assertTrue(showConnectionCard(at(), 1600))
        assertTrue(showConnectionCard(at(attempt = 2), 0))
        assertTrue(showConnectionCard(at(ConnectionProgress.Step.Waiting), 0))
    }

    @Test
    fun namesThePathInPlainWords() {
        assertEquals("Connected directly", routeLabel(Route.Direct))
        assertEquals("Connected through an encrypted relay", routeLabel(Route.Relay))
        assertEquals("Connected", routeLabel(null))
    }

    @Test
    fun keepsCountingAndExplainsASlowPairing() {
        assertEquals("Pairing securely with your computer…", pairingStatus(0))
        assertEquals("Pairing securely with your computer… 2 s", pairingStatus(2400))
        assertTrue(pairingStatus(9000).contains("9 s. Over mobile data"))
    }
}
