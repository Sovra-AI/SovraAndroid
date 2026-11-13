/*
 * Strict parser for the desktop pairing QR, mirroring `PairingTicket::decode` in
 * `SovraHarness/crates/sovra-core/src/remote_control/wire.rs`.
 *
 * The link is a bearer invitation: never log it, persist it, or put it in analytics.
 * Only `address` (public routing hints) may be saved after pairing succeeds.
 */
package ai.sovra.remote.protocol

import java.net.URLDecoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

private const val MAX_TICKET_BYTES = 2048
private const val PREFIX = "sovra://remote-control?"

class PairingTicket(val address: PeerAddress, val secret: String) {
    // Never print the secret.
    override fun toString() = "PairingTicket(device=${address.device})"
}

/** `application/x-www-form-urlencoded` decoding, as Rust's `url::Url::query_pairs`. */
private fun formDecode(text: String): String? = runCatching { URLDecoder.decode(text, Charsets.UTF_8) }.getOrNull()

private fun isPort(text: String): Boolean = Regex("^\\d{1,5}$").matches(text) && text.toInt() in 0..65535

/** Accepts what Rust's `SocketAddr::from_str` accepts in practice: `a.b.c.d:p` or `[v6]:p`. */
fun isSocketAddress(text: String): Boolean {
    Regex("^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3}):(\\d+)$").matchEntire(text)?.let { v4 ->
        val parts = v4.groupValues.subList(1, 5)
        return parts.all { it.toInt() <= 255 && it.toInt().toString() == it } && isPort(v4.groupValues[5])
    }
    val v6 = Regex("^\\[([0-9a-fA-F:.]+)(%[0-9]+)?]:(\\d+)$").matchEntire(text) ?: return false
    return v6.groupValues[1].contains(':') && isPort(v6.groupValues[3])
}

private fun isRelay(text: String): Boolean =
    text.length <= 2048 && Regex("^https://[A-Za-z0-9.-]+(:\\d{1,5})?(/[^?#@\\s]*)?$").matches(text)

private fun hasOnlyKeys(value: JsonObject, keys: Set<String>) = value.keys == keys

fun parseAddress(value: JsonElement?): PeerAddress? {
    val address = value as? JsonObject ?: return null
    if (!hasOnlyKeys(address, setOf("device", "relay", "addrs"))) return null
    val device = (address["device"] as? JsonPrimitive)?.takeIf { it.isString }?.content
    if (!isHex32(device)) return null
    val relay = when (val raw = address["relay"]) {
        JsonNull -> null
        is JsonPrimitive -> raw.takeIf { it.isString && isRelay(it.content) }?.content ?: return null
        else -> return null
    }
    val addrs = address["addrs"] as? JsonArray ?: return null
    if (addrs.size > 16) return null
    val list = addrs.map { entry -> (entry as? JsonPrimitive)?.takeIf { it.isString && isSocketAddress(it.content) }?.content ?: return null }
    return PeerAddress(device!!, relay, list)
}

/** Returns null for anything that is not exactly a version 1 Sovra invitation. */
fun decodeTicket(link: String): PairingTicket? {
    val trimmed = link.trim()
    if (utf8Length(trimmed) > MAX_TICKET_BYTES) return null
    if (!trimmed.startsWith(PREFIX) || trimmed.contains('#')) return null
    val pairs = trimmed.removePrefix(PREFIX).split('&')
    if (pairs.size != 1) return null
    val separator = pairs[0].indexOf('=')
    if (separator < 0 || formDecode(pairs[0].substring(0, separator)) != "ticket") return null
    val payload = formDecode(pairs[0].substring(separator + 1)) ?: return null
    val ticket = runCatching { json.parseToJsonElement(payload) }.getOrNull() as? JsonObject ?: return null
    if (!hasOnlyKeys(ticket, setOf("version", "address", "secret"))) return null
    val version = (ticket["version"] as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull
    val secret = (ticket["secret"] as? JsonPrimitive)?.takeIf { it.isString }?.content
    if (version != PROTOCOL_VERSION || !isHex32(secret)) return null
    val address = parseAddress(ticket["address"]) ?: return null
    return PairingTicket(address, secret!!)
}
