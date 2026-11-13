package ai.sovra.remote.ui

import ai.sovra.remote.data.Appearance
import ai.sovra.remote.data.RemoteStore
import ai.sovra.remote.data.Storage
import ai.sovra.remote.data.phoneId
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import java.text.DateFormat
import java.util.Date

private fun short(hex: String) = "${hex.take(8)}…${hex.takeLast(8)}"

@Composable
private fun SettingRow(title: String, detail: String? = null, trailing: @Composable (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().heightIn(min = Layout.control), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            SText(title, style = Type.label)
            detail?.let { SText(it, style = Type.caption, tone = TextTone.Muted) }
        }
        trailing?.invoke()
    }
}

@Composable
fun SettingsScreen(store: RemoteStore, storage: Storage, onDone: () -> Unit, onForgotten: () -> Unit) {
    val state by store.state.collectAsState()
    val p = LocalPalette.current
    var advanced by remember { mutableStateOf(false) }
    var confirming by remember { mutableStateOf(false) }
    val preferences = state.preferences
    val phone = remember { phoneId(storage) }

    // Connection options apply on the next connection.
    val toggle: (ai.sovra.remote.data.Preferences) -> Unit = {
        store.setPreferences(it)
        store.stopLive()
    }

    Column(
        Modifier.fillMaxSize().background(p.canvas).safeDrawingPadding().verticalScroll(rememberScrollState()).padding(Space.medium),
        verticalArrangement = Arrangement.spacedBy(Space.large),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SText("Settings", Modifier.weight(1f), style = Type.pageTitle)
            SButton("Done", onDone, compact = true, variant = ButtonVariant.Ghost)
        }
        Card {
            SText("Your computer", style = Type.sectionTitle)
            val desktop = state.desktop
            if (desktop != null) {
                SettingRow(desktop.name, "Paired ${DateFormat.getDateInstance().format(Date(desktop.pairedAt))}")
                SButton("Forget this computer", { confirming = true }, Modifier.fillMaxWidth(), variant = ButtonVariant.Danger)
            } else {
                SText("None yet.", tone = TextTone.Secondary)
            }
        }
        Card {
            SText("Appearance", style = Type.sectionTitle)
            Segmented(listOf("System", "Dark", "Light"), preferences.appearance.ordinal, { store.setPreferences(preferences.copy(appearance = Appearance.entries[it])) })
        }
        SButton(if (advanced) "Hide advanced settings" else "Advanced settings", { advanced = !advanced }, Modifier.fillMaxWidth(), variant = ButtonVariant.Ghost)
        if (advanced) {
            val colors = SwitchDefaults.colors(checkedTrackColor = p.primary, uncheckedTrackColor = p.hover)
            Card {
                SText("Connection", style = Type.sectionTitle)
                SettingRow("Show screenshots", "Uses more data. Not every computer can capture its window.") {
                    Switch(preferences.screenshots, { toggle(preferences.copy(screenshots = it)) }, colors = colors)
                }
                SettingRow("Same Wi-Fi only", "For testing: no relays, so mobile data will not work.") {
                    Switch(preferences.localOnly, { toggle(preferences.copy(localOnly = it, relayOnly = preferences.relayOnly && !it)) }, colors = colors)
                }
                SettingRow("Encrypted relay only", "For testing: skips direct connections, the way a phone on a strict network reaches your computer.") {
                    Switch(preferences.relayOnly, { toggle(preferences.copy(relayOnly = it, localOnly = preferences.localOnly && !it)) }, colors = colors)
                }
            }
            Card {
                SText("This phone", style = Type.sectionTitle)
                SettingRow("Phone key", phone?.let(::short) ?: "Created on first connection")
                state.desktop?.let { SettingRow("Computer key", short(it.address.device)) }
                SText(
                    "The private key never leaves this phone. Your computer recognises the phone by this key, not by its name.",
                    style = Type.caption, tone = TextTone.Muted,
                )
            }
        }
    }

    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text("Forget this computer?") },
            text = { Text("This phone stops connecting to it. To revoke access completely, also choose Forget for this phone in Sovra on your computer.") },
            confirmButton = {
                TextButton({
                    confirming = false
                    store.launch {
                        store.forget()
                        onForgotten()
                    }
                }) { Text("Forget", color = p.danger) }
            },
            dismissButton = { TextButton({ confirming = false }) { Text("Cancel") } },
        )
    }
}
