package ai.sovra.remote

import ai.sovra.remote.data.RemoteStore
import ai.sovra.remote.data.Storage
import ai.sovra.remote.data.createNativeTransport
import ai.sovra.remote.ui.PairScreen
import ai.sovra.remote.ui.PairingLink
import ai.sovra.remote.ui.PairingNames
import ai.sovra.remote.ui.RemoteScreen
import ai.sovra.remote.ui.ScanScreen
import ai.sovra.remote.ui.SettingsScreen
import ai.sovra.remote.ui.SovraTheme
import android.app.Application
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class SovraApp : Application() {
    val storage by lazy { Storage(this) }
    val store by lazy { RemoteStore(storage) { reach -> createNativeTransport(storage, reach) } }
    val names by lazy { PairingNames(Build.MODEL ?: "My phone") }
}

private sealed interface Screen {
    data object Pair : Screen
    data object Scan : Screen
    /** A pairing link from the camera or a deep link: used once, in memory, never saved. */
    class Link(val link: String) : Screen
    data object Remote : Screen
    data object Settings : Screen
}

class MainActivity : ComponentActivity() {
    // Plain state, not saved: a pairing link must never land in saved instance state.
    private var screen by mutableStateOf<Screen>(Screen.Pair)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as SovraApp
        screen = if (app.store.state.value.desktop != null) Screen.Remote else Screen.Pair
        takeLink(intent)
        setContent {
            val state by app.store.state.collectAsState()
            SovraTheme(state.preferences.appearance) {
                val home: () -> Unit = { screen = if (app.store.state.value.desktop != null) Screen.Remote else Screen.Pair }
                when (val current = screen) {
                    Screen.Pair -> PairScreen(app.store, app.names, onScan = { screen = Screen.Scan }, onPaired = { screen = Screen.Remote })
                    Screen.Scan -> {
                        BackHandler { screen = Screen.Pair }
                        ScanScreen(onCode = { screen = Screen.Link(it) }, onCancel = { screen = Screen.Pair })
                    }
                    is Screen.Link -> PairingLink(app.store, app.names, current.link) { paired -> screen = if (paired) Screen.Remote else Screen.Pair }
                    Screen.Remote -> RemoteScreen(app.store, onSettings = { screen = Screen.Settings }, onUnpaired = { screen = Screen.Pair })
                    Screen.Settings -> {
                        BackHandler(onBack = home)
                        SettingsScreen(app.store, app.storage, onDone = home, onForgotten = { screen = Screen.Pair })
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        takeLink(intent)
    }

    /** `sovra://remote-control?ticket=…`: pair with it, then drop it from the intent. */
    private fun takeLink(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme == "sovra" && data.host == "remote-control") screen = Screen.Link(data.toString())
        intent.data = null
    }
}
