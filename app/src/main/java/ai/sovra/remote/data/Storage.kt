/*
 * On-device persistence. The phone's private key is encrypted with a key held in the
 * Android Keystore and never leaves this device (backup is off in the manifest). Only
 * public routing hints for the paired computer are saved; the pairing invitation itself
 * is never written anywhere.
 */
package ai.sovra.remote.data

import ai.sovra.remote.protocol.PeerAddress
import ai.sovra.remote.protocol.parseAddress
import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

data class SavedDesktop(val address: PeerAddress, val name: String, val pairedAt: Long)

enum class Appearance { System, Dark, Light }

/** How the phone may reach the computer. */
enum class Reach { Internet, Local, Relay }

data class Preferences(
    val appearance: Appearance = Appearance.System,
    /** Refresh cadence while the remote screen is open (ms). Must stay under 5 s. */
    val refreshMs: Long = 2000,
    /** Testing on one Wi-Fi only: no relays or Internet address lookup. */
    val localOnly: Boolean = false,
    /** Testing the off-network path: no direct sockets, everything through the encrypted relay. */
    val relayOnly: Boolean = false,
    /** Fetch a screenshot with each refresh, when the desktop supports it. */
    val screenshots: Boolean = false,
) {
    /** Same Wi-Fi only wins if old saved settings somehow have both. */
    val reach: Reach get() = when {
        localOnly -> Reach.Local
        relayOnly -> Reach.Relay
        else -> Reach.Internet
    }
}

class Storage(context: Context) {
    private val prefs = context.getSharedPreferences("sovra", Context.MODE_PRIVATE)

    private fun key(): SecretKey {
        val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (store.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )
        return generator.generateKey()
    }

    fun loadPhoneSecret(): String? {
        val sealed = prefs.getString(PHONE_SECRET, null) ?: return null
        return runCatching {
            val bytes = Base64.decode(sealed, Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes, 0, IV_BYTES))
            String(cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES), Charsets.UTF_8)
        }.getOrNull()
    }

    fun savePhoneSecret(secret: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val sealed = cipher.iv + cipher.doFinal(secret.toByteArray(Charsets.UTF_8))
        prefs.edit().putString(PHONE_SECRET, Base64.encodeToString(sealed, Base64.NO_WRAP)).commit()
    }

    /** Damaged storage fails closed (treated as unpaired), it is never "repaired" silently. */
    fun loadDesktop(): SavedDesktop? {
        val text = prefs.getString(DESKTOP, null) ?: return null
        return runCatching {
            val saved = Json.parseToJsonElement(text) as JsonObject
            val address = parseAddress(saved["address"]) ?: return null
            val name = (saved["name"] as JsonPrimitive).content
            val pairedAt = (saved["pairedAt"] as JsonPrimitive).content.toLong()
            SavedDesktop(address, name, pairedAt)
        }.getOrNull()
    }

    fun saveDesktop(desktop: SavedDesktop): Boolean {
        val text = buildJsonObject {
            put("address", desktop.address.toJson())
            put("name", desktop.name)
            put("pairedAt", desktop.pairedAt)
        }.toString()
        return prefs.edit().putString(DESKTOP, text).commit()
    }

    fun deleteDesktop() {
        prefs.edit().remove(DESKTOP).commit()
    }

    fun loadPreferences(): Preferences = Preferences(
        appearance = runCatching { Appearance.valueOf(prefs.getString(APPEARANCE, null)!!) }.getOrDefault(Appearance.System),
        localOnly = prefs.getBoolean(LOCAL_ONLY, false),
        relayOnly = prefs.getBoolean(RELAY_ONLY, false),
        screenshots = prefs.getBoolean(SCREENSHOTS, false),
    )

    fun savePreferences(preferences: Preferences) {
        prefs.edit()
            .putString(APPEARANCE, preferences.appearance.name)
            .putBoolean(LOCAL_ONLY, preferences.localOnly)
            .putBoolean(RELAY_ONLY, preferences.relayOnly)
            .putBoolean(SCREENSHOTS, preferences.screenshots)
            .apply()
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "sovra.phone-secret.v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val PHONE_SECRET = "sovra.phone-secret.v1"
        const val DESKTOP = "sovra.desktop.v1"
        const val APPEARANCE = "sovra.appearance"
        const val LOCAL_ONLY = "sovra.local-only"
        const val RELAY_ONLY = "sovra.relay-only"
        const val SCREENSHOTS = "sovra.screenshots"
    }
}
