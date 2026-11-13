package ai.sovra.remote.ui

import ai.sovra.remote.data.Appearance
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The Computer tab's picture: two fingers zoom in, and taps still reach the controls. */
@RunWith(AndroidJUnit4::class)
class ZoomBoxTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun pinchZoomsInAndTapsStillReachTheControls() {
        val taps = mutableIntStateOf(0)
        compose.setContent {
            SovraTheme(Appearance.Light) {
                ZoomBox { Box(Modifier.size(300.dp).testTag("window").clickable { taps.intValue += 1 }) }
            }
        }
        val window = compose.onNodeWithTag("window")
        // Unclipped width on screen (the zoomed picture is wider than the screen).
        fun width(): Float {
            val coordinates = window.fetchSemanticsNode().layoutInfo.coordinates
            val right = coordinates.localToRoot(Offset(coordinates.size.width.toFloat(), 0f))
            return right.x - coordinates.localToRoot(Offset.Zero).x
        }
        val before = width()

        window.performTouchInput {
            pinch(
                start0 = center - Offset(20f, 0f), end0 = center - Offset(250f, 0f),
                start1 = center + Offset(20f, 0f), end1 = center + Offset(250f, 0f),
            )
        }
        compose.waitForIdle()
        val after = width()
        assertTrue("zoomed from $before to $after", after > before * 1.5f)

        window.performClick()
        compose.waitForIdle()
        assertEquals(1, taps.intValue)
    }
}
