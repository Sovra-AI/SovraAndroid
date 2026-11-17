package ai.sovra.remote.ui

import ai.sovra.remote.data.Appearance
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The first-run tour: Next walks every page, the last one finishes it, and Skip ends it early. */
@RunWith(AndroidJUnit4::class)
class OnboardingTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun nextWalksThroughTheTourAndGetStartedFinishesIt() {
        val done = mutableIntStateOf(0)
        compose.setContent { SovraTheme(Appearance.Light) { OnboardingScreen { done.intValue += 1 } } }
        compose.onNodeWithText("Sovra, in your pocket").assertIsDisplayed()
        repeat(3) { compose.onNodeWithText("Next").performClick() }
        compose.onNodeWithText("Private by design").assertIsDisplayed()
        assertEquals(0, compose.onAllNodesWithTextCount("Skip"))
        compose.onNodeWithText("Get started").performClick()
        assertEquals(1, done.intValue)
    }

    @Test
    fun skipEndsTheTourAtOnce() {
        val done = mutableIntStateOf(0)
        compose.setContent { SovraTheme(Appearance.Light) { OnboardingScreen { done.intValue += 1 } } }
        compose.onNodeWithText("Skip").performClick()
        assertEquals(1, done.intValue)
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTextCount(text: String) =
        onAllNodes(androidx.compose.ui.test.hasText(text)).fetchSemanticsNodes().size
}
