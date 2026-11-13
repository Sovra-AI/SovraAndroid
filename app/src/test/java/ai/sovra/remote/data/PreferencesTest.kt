package ai.sovra.remote.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** Same cases as SovraiOS's `preferences.test.ts`. */
class PreferencesTest {
    @Test
    fun usesEveryPathUnlessATestingOptionNarrowsIt() {
        assertEquals(Reach.Internet, Preferences().reach)
        assertEquals(Reach.Relay, Preferences(relayOnly = true).reach)
        assertEquals(Reach.Local, Preferences(localOnly = true).reach)
        assertEquals(Reach.Local, Preferences(localOnly = true, relayOnly = true).reach)
    }
}
