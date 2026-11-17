package ai.sovra.remote.ui

import ai.sovra.remote.data.Appearance
import ai.sovra.remote.data.RemoteStore
import ai.sovra.remote.data.SavedDesktop
import ai.sovra.remote.data.Storage
import ai.sovra.remote.protocol.Bounds
import ai.sovra.remote.protocol.CHAT_SEARCH_LABEL
import ai.sovra.remote.protocol.FakeDesktop
import ai.sovra.remote.protocol.NEW_CHAT_LABEL
import ai.sovra.remote.protocol.UiElement
import ai.sovra.remote.protocol.desktopAddress
import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** One control in Sovra's 1280×800 window, placed where the desktop puts it. */
private fun control(
    target: String, label: String?, role: String, x: Double, y: Double, width: Double = 300.0, height: Double = 28.0,
    checked: Boolean? = null, selected: Boolean? = null,
) = UiElement(target, label, role, true, false, checked, selected, null, Bounds(x, y, width, height))

private val SIDEBAR = buildList {
    add(control("new", NEW_CHAT_LABEL, "Button", 8.0, 124.0, 239.0, 36.0))
    add(control("search", CHAT_SEARCH_LABEL, "TextInput", 8.0, 540.0, 239.0, 30.0))
    add(control("filter", "Chats", "Button", 10.0, 577.0, 116.0, 28.0))
    listOf("Chat", "Skills", "Team", "Schedules", "Models", "Artifacts", "Settings").forEachIndexed { index, page ->
        add(control("nav-${page.lowercase()}", page, "Button", 8.0, 168.0 + index * 30, 239.0, 28.0))
    }
    listOf("Quiet café for tomorrow", "Plan the launch", "Compare laptops", "Weekly report").forEachIndexed { index, title ->
        add(control("c$index", "Open conversation $title", "Button", 8.0, 639.0 + index * 51, 239.0, 49.0))
    }
}

private val COMPOSER = control("composer", "Message Sovra", "MultilineTextInput", 300.0, 700.0, 900.0, 40.0)

private val CONVERSATION = listOf(
    control("log", "Conversation", "Log", 280.0, 80.0, 976.0, 598.0),
    control("u1", "Find me a quiet café near the office for tomorrow at 10.", "Comment", 700.0, 120.0, 300.0, 40.0),
    control(
        "a1",
        "Here are two quiet spots within a five-minute walk:\n\n- **Fern & Co.** opens at 8, has booths and good Wi-Fi.\n- **Northside Roasters** is calmer before noon.\n\nWant me to book a table?",
        "Article", 400.0, 180.0, 600.0, 200.0,
    ),
    control("u2", "Book Fern & Co. for two, please.", "Comment", 700.0, 400.0, 300.0, 30.0),
    control("a2", "Done. A table for two at **Fern & Co.**, tomorrow at 10:00. I added it to your calendar.", "Article", 400.0, 450.0, 600.0, 60.0),
)

/** What `/help` opens: a page of text, buttons, a switch, choices and a text box. */
private val HELP = listOf(
    control("help-h", "Help", "Heading", 300.0, 60.0),
    control("help-p", "Sovra can search the web, read your files, make tables and run your skills.", "Label", 300.0, 90.0, 700.0),
    control("start-h", "Getting started", "Heading", 300.0, 130.0),
    control("tour", "Take a guided tour", "Button", 300.0, 160.0, 200.0, 32.0),
    control("tour-text", "Take a guided tour", "Label", 310.0, 165.0, 180.0, 20.0),
    control("keys", "Keyboard shortcuts", "Button", 300.0, 200.0, 200.0, 32.0),
    control("prefs-h", "Preferences", "Heading", 300.0, 250.0),
    control("tips", "Show tips when Sovra starts", "Switch", 300.0, 280.0, checked = true),
    control("light", "Light", "Tab", 300.0, 320.0, 90.0),
    control("dark", "Dark", "Tab", 400.0, 320.0, 90.0, selected = true),
    control("name", "Your display name", "TextInput", 300.0, 370.0),
    control("help-list", null, "List", 280.0, 40.0, 976.0, 700.0),
)

private val MENU = listOf(
    control("s1", "/files. Choose local files to read and discuss in this chat. ", "Button", 0.0, 555.0, 100.0, 20.0),
    control("s2", "/web-search. Use when current information or sourced web research is needed. <question>", "Button", 0.0, 593.0, 100.0, 20.0),
    control("s3", "/table. Use when comparing structured records in a native table. <what to present>", "Button", 0.0, 631.0, 100.0, 20.0),
    control("s4", "/summarize. Summarize a document, page or thread. <what to summarize>", "Button", 0.0, 669.0, 100.0, 20.0),
)

private sealed interface Shot {
    data class Remote(val tab: Tab) : Shot
    data object Settings : Shot
    data object Tour : Shot
    data object Pair : Shot
    data object Scan : Shot
    data class Link(val link: String) : Shot
}

/**
 * Renders every remote screen against an in-memory desktop and saves each as a PNG, so the
 * phone UI can be reviewed without a paired computer. Runs only when asked:
 * `adb shell am instrument -w -e gallery true -e class ai.sovra.remote.ui.ScreenGalleryTest …`,
 * then pull `/sdcard/Android/data/ai.sovra.remote.native/files/gallery/`.
 */
@RunWith(AndroidJUnit4::class)
class ScreenGalleryTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun capturesEveryScreen() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("gallery") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val out = File(context.getExternalFilesDir(null), "gallery").apply { mkdirs() }
        val storage = Storage(context)
        storage.saveDesktop(SavedDesktop(desktopAddress, "Studio Mac", System.currentTimeMillis()))
        val desktop = FakeDesktop().apply {
            paired = true
            elements += SIDEBAR + COMPOSER + CONVERSATION
            // Typing "/" opens the skills menu, as on the desktop.
            onDeliver = { input -> if (input.action == "type" && input.text == "/" && MENU[0] !in elements) elements += MENU }
        }
        val store = runBlocking(Dispatchers.Main) { RemoteStore(storage) { desktop.transport() } }

        var shot by mutableStateOf<Shot>(Shot.Remote(Tab.Chat))
        var appearance by mutableStateOf(Appearance.Light)
        compose.setContent {
            SovraTheme(appearance) {
                key(shot, appearance) {
                    when (val current = shot) {
                        is Shot.Remote -> RemoteScreen(store, onSettings = {}, onUnpaired = {}, startTab = current.tab)
                        Shot.Settings -> SettingsScreen(store, storage, onDone = {}, onForgotten = {}, onTour = {})
                        Shot.Tour -> OnboardingScreen {}
                        Shot.Pair -> PairScreen(store, PairingNames("Pixel 9"), onScan = {}, onPaired = {}, onTour = {})
                        Shot.Scan -> ScanScreen(onCode = {}, onCancel = {})
                        is Shot.Link -> PairingLink(store, PairingNames("Pixel 9"), current.link) {}
                    }
                }
            }
        }

        fun page(vararg parts: List<UiElement>) = runBlocking(Dispatchers.Main) {
            desktop.elements.clear()
            parts.forEach { desktop.elements += it }
        }

        fun capture(name: String, next: Shot, look: Appearance = Appearance.Light, settle: Long = 1500) {
            compose.runOnUiThread {
                shot = next
                appearance = look
            }
            Thread.sleep(settle)
            runBlocking(Dispatchers.Main) { store.refresh() }
            Thread.sleep(700)
            compose.waitForIdle()
            val image = compose.onRoot().captureToImage().asAndroidBitmap()
            File(out, "android-$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }

        capture("1-chat", Shot.Remote(Tab.Chat))
        capture("2-chat-dark", Shot.Remote(Tab.Chat), Appearance.Dark)
        capture("3-chats", Shot.Remote(Tab.Chats))
        capture("4-skills", Shot.Remote(Tab.Skills), settle = 3000)
        page(SIDEBAR, listOf(COMPOSER))
        capture("5-chat-empty", Shot.Remote(Tab.Chat))
        // Typing "/" offers the skills; choosing one shows it as a chip in the chat box.
        compose.onNodeWithContentDescription("Message to Sovra").performTextInput("/")
        capture("5b-chat-slash", Shot.Remote(Tab.Chat), settle = 500)
        compose.onAllNodesWithText("Web search").onFirst().performClick()
        capture("5c-chat-skill", Shot.Remote(Tab.Chat), settle = 500)
        capture("6-more", Shot.Remote(Tab.More))
        page(SIDEBAR, HELP)
        capture("7-more-help", Shot.Remote(Tab.More))
        capture("8-more-help-dark", Shot.Remote(Tab.More), Appearance.Dark)
        capture("7b-more-typing", Shot.Remote(Tab.More))
        compose.onNodeWithText("Your display name").performClick()
        capture("7b-more-typing", Shot.Remote(Tab.More), settle = 500)
        capture("9-settings", Shot.Settings)
        compose.onNodeWithText("Advanced settings").performClick()
        capture("9b-settings-advanced", Shot.Settings, settle = 500)
        capture("10-tour", Shot.Tour)
        capture("11-pair", Shot.Pair)
        capture("12-link-failed", Shot.Link("sovra://remote-control?ticket=not-a-ticket"))
        // The computer turned phone control off, then stopped answering.
        desktop.enabled = false
        capture("13-disabled", Shot.Remote(Tab.Chat))
        desktop.enabled = true
        desktop.unreachable = true
        capture("14-unreachable", Shot.Remote(Tab.Chat), settle = 4000)
        desktop.unreachable = false
        InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(context.packageName, android.Manifest.permission.CAMERA)
        capture("15-scan", Shot.Scan)
        compose.runOnUiThread { store.stopLive() }
    }
}
