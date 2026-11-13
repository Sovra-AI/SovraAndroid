/*
 * Version 1 phone wire contract, mirrored from the desktop:
 * `SovraHarness/crates/sovra-core/src/remote_control/wire.rs` and
 * `crates/sovra-core/src/development.rs` (`UiRequest`).
 *
 * The fixtures in `src/test/resources/fixtures` are copied byte-for-byte from the
 * harness; `WireTest` fails if these types drift from them.
 */
package ai.sovra.remote.protocol

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put

const val PROTOCOL_VERSION = 1
const val MAX_REQUEST_BYTES = 32 * 1024
const val MAX_RESPONSE_BYTES = 8 * 1024 * 1024

/** `sovra_core::remote_control::MAX_SESSION_REQUESTS`: renew with Hello after this. */
const val MAX_SESSION_REQUESTS = 4096
const val MAX_TEXT_BYTES = 4096
const val MAX_TARGET_BYTES = 2048
const val MAX_SCROLL_LINES = 100
const val MAX_PHONE_NAME_BYTES = 80

private val HEX32 = Regex("^[0-9a-f]{64}$")

/** 64 lowercase hex digits (a 32-byte key, secret or nonce). */
fun isHex32(value: String?): Boolean = value != null && HEX32.matches(value)

fun utf8Length(text: String): Int = text.toByteArray(Charsets.UTF_8).size

/** `sovra_core::group::PeerAddress`: public routing information, not a credential. */
data class PeerAddress(val device: String, val relay: String?, val addrs: List<String>) {
    fun toJson(): JsonObject = buildJsonObject {
        put("device", device)
        put("relay", relay?.let(::JsonPrimitive) ?: JsonNull)
        put("addrs", JsonArray(addrs.map(::JsonPrimitive)))
    }
}

sealed interface UiRequest {
    data object Inspect : UiRequest
    data object Screenshot : UiRequest
    data class Click(val target: String) : UiRequest
    data class Type(val text: String) : UiRequest
    data class Key(val key: String) : UiRequest
    data class Scroll(val target: String, val lines: Int) : UiRequest

    val isMutation: Boolean get() = this !is Inspect && this !is Screenshot

    fun toJson(): JsonObject = buildJsonObject {
        when (val request = this@UiRequest) {
            Inspect -> put("action", "inspect")
            Screenshot -> put("action", "screenshot")
            is Click -> { put("action", "click"); put("target", request.target) }
            is Type -> { put("action", "type"); put("text", request.text) }
            is Key -> { put("action", "key"); put("key", request.key) }
            is Scroll -> { put("action", "scroll"); put("target", request.target); put("lines", request.lines) }
        }
    }
}

/** Same rules as `UiRequest::validate` on the desktop, so bad input never costs a sequence. */
fun validateUiRequest(request: UiRequest): String? = when (request) {
    UiRequest.Inspect, UiRequest.Screenshot -> null
    is UiRequest.Click -> validateTarget(request.target)
    is UiRequest.Scroll -> validateTarget(request.target)
        ?: if (kotlin.math.abs(request.lines) > MAX_SCROLL_LINES) "Scroll between -100 and 100 lines." else null
    is UiRequest.Type ->
        if (utf8Length(request.text) > MAX_TEXT_BYTES || request.text.contains('\u0000')) {
            "That text is too long to send in one go (4096 bytes)."
        } else null
    is UiRequest.Key -> if (request.key.isEmpty() || utf8Length(request.key) > 80) "That key is not supported." else null
}

private fun validateTarget(target: String): String? {
    val length = utf8Length(target)
    return if (length == 0 || length > MAX_TARGET_BYTES) "Pick a control from the latest view first." else null
}

sealed interface RemoteOperation {
    data class Pair(val secret: String, val name: String) : RemoteOperation
    data object Hello : RemoteOperation
    data class Control(
        val generation: String,
        val session: String,
        val sequence: Int,
        val revision: String?,
        val request: UiRequest,
    ) : RemoteOperation

    fun toJson(): JsonObject = buildJsonObject {
        when (val operation = this@RemoteOperation) {
            is Pair -> { put("op", "pair"); put("secret", operation.secret); put("name", operation.name) }
            Hello -> put("op", "hello")
            is Control -> {
                put("op", "control")
                put("generation", operation.generation)
                put("session", operation.session)
                put("sequence", operation.sequence)
                put("revision", operation.revision?.let(::JsonPrimitive) ?: JsonNull)
                put("request", operation.request.toJson())
            }
        }
    }
}

/** The request envelope, serialized with the desktop's key order. */
fun encodeRequest(operation: RemoteOperation): String = buildJsonObject {
    put("version", PROTOCOL_VERSION)
    put("operation", operation.toJson())
}.toString()

enum class ErrorCode(val wire: String) {
    Disabled("disabled"),
    UnsupportedVersion("unsupported_version"),
    NotPaired("not_paired"),
    InvalidInvitation("invalid_invitation"),
    InvalidRequest("invalid_request"),
    StaleSession("stale_session"),
    StaleObservation("stale_observation"),
    Busy("busy"),
    StorageUnavailable("storage_unavailable"),
    OutcomeUnknown("outcome_unknown"),

    /** Phone-side only: the computer could not be reached. Never means "revoked". */
    Unreachable("unreachable");

    companion object {
        /** Desktop codes only; `unreachable` never comes over the wire. */
        fun fromWire(code: String?): ErrorCode? = entries.firstOrNull { it.wire == code && it != Unreachable }
    }
}

sealed interface RemoteResponse {
    data object Paired : RemoteResponse
    data class Ready(
        val version: Int,
        val generation: String,
        val session: String,
        val maxRequestBytes: Int,
        val maxResponseBytes: Int,
    ) : RemoteResponse

    data class Result(val value: JsonElement) : RemoteResponse
    data class Error(val code: ErrorCode, val message: String) : RemoteResponse
}

internal val json = Json { ignoreUnknownKeys = true }

private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
private fun JsonObject.number(key: String): Double? = (this[key] as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull
private fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull
private fun JsonObject.bool(key: String): Boolean? = (this[key] as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull

/** Strict decode of a desktop reply. Anything unexpected is a protocol error, not data. */
fun decodeResponse(text: String): RemoteResponse? {
    val value = runCatching { json.parseToJsonElement(text) }.getOrNull() as? JsonObject ?: return null
    return when (value.string("status")) {
        "paired" -> RemoteResponse.Paired
        "ready" -> {
            val version = value.int("version")
            val generation = value.string("generation")
            val session = value.string("session")
            val maxRequest = value.int("max_request_bytes")
            val maxResponse = value.int("max_response_bytes")
            if (version != null && isHex32(generation) && isHex32(session) && maxRequest != null && maxResponse != null) {
                RemoteResponse.Ready(version, generation!!, session!!, maxRequest, maxResponse)
            } else null
        }
        "result" -> value["value"]?.let(RemoteResponse::Result)
        "error" -> {
            val code = ErrorCode.fromWire(value.string("code"))
            val message = value.string("message")
            if (code != null && message != null) RemoteResponse.Error(code, message) else null
        }
        else -> null
    }
}

data class Bounds(val x: Double, val y: Double, val width: Double, val height: Double)

/** One native control from Inspect. Never parse or construct `target`. */
data class UiElement(
    val target: String,
    val label: String?,
    val role: String?,
    val visible: Boolean,
    val disabled: Boolean?,
    val checked: Boolean?,
    val selected: Boolean?,
    val expanded: Boolean?,
    val bounds: Bounds,
)

data class InspectResult(
    val elements: List<UiElement>,
    val total: Int,
    val truncated: Boolean,
    val appearance: String,
    val interfaceScalePercent: Int,
    val revision: String,
)

private fun bounds(value: JsonElement?): Bounds? {
    val box = value as? JsonObject ?: return null
    return Bounds(box.number("x") ?: return null, box.number("y") ?: return null, box.number("width") ?: return null, box.number("height") ?: return null)
}

/** Parse an Inspect `result.value`; drops malformed elements rather than trusting them. */
fun parseInspect(value: JsonElement): InspectResult? {
    val view = value as? JsonObject ?: return null
    val raw = view["elements"] as? JsonArray ?: return null
    val revision = view.string("revision") ?: return null
    val elements = raw.mapNotNull { item ->
        val element = item as? JsonObject ?: return@mapNotNull null
        UiElement(
            target = element.string("target") ?: return@mapNotNull null,
            label = element.string("label"),
            role = element.string("role"),
            visible = element.bool("visible") == true,
            disabled = element.bool("disabled"),
            checked = element.bool("checked"),
            selected = element.bool("selected"),
            expanded = element.bool("expanded"),
            bounds = bounds(element["bounds"]) ?: return@mapNotNull null,
        )
    }
    return InspectResult(
        elements = elements,
        total = view.int("total") ?: elements.size,
        truncated = view.bool("truncated") == true,
        appearance = if (view.string("appearance") == "light") "light" else "dark",
        interfaceScalePercent = view.int("interface_scale_percent") ?: 100,
        revision = revision,
    )
}

fun parseScreenshot(value: JsonElement): String? = (value as? JsonObject)?.string("png")
